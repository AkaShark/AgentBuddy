import UIKit

/// Swift facade over the Rust `TerminalRenderer`. The platform owns the
/// Ghostty surface (via `AgentBuddyGhosttyTerminal`); the renderer mediates the
/// non-UI-thread tick task, OSC parsing, selection plumbing, and bell
/// detection. This wrapper exists so SwiftUI consumers don't have to thread
/// UniFFI handles through every call.
@MainActor
final class GhosttyTerminalRenderer {
    var onInput: ((Data) -> Void)?
    var onNativeOutputVisibilityChanged: ((Bool) -> Void)?
    /// Fires on main when Rust pushes a new (or cleared) selection range.
    /// The host view uses this to repaint the selection-handle overlay.
    var onSelectionRangeChanged: ((TerminalCellRange?) -> Void)?
    /// Fires on main when the PTY stream emits a literal BEL. The host
    /// view drives haptic feedback from here.
    var onBell: (() -> Void)?

    private var terminal: AgentBuddyGhosttyTerminal?
    private var renderer: TerminalRenderer?
    private var backendBridge: GhosttyRendererBackendBridge?
    private var bellListener: TerminalRendererBellListener?
    private var pendingOutput: [Data] = []
    private var pendingWriteBuffer = Data()
    private var outputFlushScheduled = false
    private weak var attachedView: UIView?
    private var hasNativeVisibleOutput = false
    private var didSetConfigDir = false
    private var isInvalidated = false
    private var currentConfig: TerminalConfig?

    func attach(to view: UIView) {
        guard !isInvalidated else { return }
        guard terminal == nil else {
            attachedView = view
            resize(
                width: view.bounds.width,
                height: view.bounds.height,
                scale: view.window?.screen.scale ?? UIScreen.main.scale
            )
            return
        }

        attachedView = view
        do {
            let terminal = try AgentBuddyGhosttyTerminal(view: view)
            terminal.inputHandler = { [weak self] data in
                Task { @MainActor [weak self] in
                    self?.onInput?(data)
                }
            }
            self.terminal = terminal
            let bridge = GhosttyRendererBackendBridge(terminal: terminal)
            bridge.onSelectionRangeChanged = { [weak self] range in
                self?.onSelectionRangeChanged?(range)
            }
            self.backendBridge = bridge
            let renderer = TerminalRenderer(backend: bridge)
            self.renderer = renderer
            let listener = TerminalRendererBellListener { [weak self] in
                Task { @MainActor [weak self] in
                    self?.onBell?()
                }
            }
            renderer.subscribeBell(listener: listener)
            self.bellListener = listener
            if let currentConfig {
                applyConfigToRenderer(currentConfig, renderer: renderer)
            }
            flushPendingOutput()
            updateNativeOutputVisibility(terminal: terminal)
        } catch {
            assertionFailure("Ghostty renderer failed: \(error.localizedDescription)")
        }
    }

    func resize(width: CGFloat, height: CGFloat, scale: CGFloat) {
        terminal?.resize(toWidth: width, height: height, scale: scale)
    }

    func setGridSize(cols: UInt16, rows: UInt16) {
        renderer?.setTerminalGridSize(cols: UInt32(cols), rows: UInt32(rows))
    }

    func write(_ data: Data) {
        guard !isInvalidated else { return }
        guard !data.isEmpty else { return }
        guard terminal != nil else {
            pendingOutput.append(data)
            if pendingOutput.count > 256 {
                pendingOutput.removeFirst(pendingOutput.count - 256)
            }
            return
        }
        // Tee bytes through the Rust OSC parser + bell detector before
        // handing them to Ghostty. `feed_output` runs the OSC state
        // machine and notifies any subscribed listeners (semantic state,
        // bell).
        enqueueOutput(data)
    }

    func makeOutputSink() -> (Data) -> Void {
        let batcher = GhosttyTerminalOutputBatcher { [weak self] data in
            self?.write(data)
        }
        return { data in
            batcher.append(data)
        }
    }

    func setOccluded(_ occluded: Bool) {
        renderer?.setOccluded(occluded: occluded)
    }

    func setFocused(_ focused: Bool) {
        renderer?.setFocused(focused: focused)
    }

    func sendKeyEvent(_ event: TerminalKeyEvent) {
        renderer?.sendKeyEvent(event: event)
    }

    func sendText(_ text: String, composing: Bool = false) {
        renderer?.sendText(text: text, composing: composing)
    }

    func sendPaste(_ text: String) {
        renderer?.sendPaste(text: text)
    }

    /// Send raw, unwrapped bytes straight to the PTY (used by accessory
    /// bar control keys so Esc/Tab/Ctrl-C don't accidentally enter
    /// bracketed-paste mode).
    func sendRawBytes(_ data: Data) {
        renderer?.sendRawBytes(bytes: data)
    }

    /// Send `selection` to the assistant on `threadKey`. Pulls cwd + last
    /// shell command from the renderer's OSC semantic state.
    func sendTextToAssistant(
        store: AppStore,
        threadKey: ThreadKey,
        selection: String
    ) async throws {
        guard let renderer else { return }
        try await renderer.sendTextToAssistant(
            store: store,
            payload: TerminalSendToAssistantPayload(
                threadKey: threadKey,
                includeCwd: true,
                includeLastCommand: true
            ),
            selection: selection
        )
    }

    var mouseCaptured: Bool {
        terminal?.mouseCaptured() ?? false
    }

    func sendMousePos(x: Double, y: Double, mods: Int32 = 0) {
        terminal?.mousePosX(x, y: y, mods: mods)
    }

    @discardableResult
    func sendMouseButton(pressed: Bool, button: Int32, mods: Int32 = 0) -> Bool {
        terminal?.mouseButtonPressed(pressed, button: button, mods: mods) ?? false
    }

    func sendMouseScroll(x: Double, y: Double, precise: Bool, mods: Int32 = 0) {
        terminal?.mouseScrollX(x, y: y, precise: precise, mods: mods)
    }

    func applyConfig(_ config: TerminalConfig) {
        currentConfig = config
        guard let renderer else { return }
        applyConfigToRenderer(config, renderer: renderer)
    }

    private func applyConfigToRenderer(_ config: TerminalConfig, renderer: TerminalRenderer) {
        if !didSetConfigDir {
            let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            if let dir = cachesDir?.appendingPathComponent("agentbuddy/terminal", isDirectory: true) {
                renderer.setConfigDir(path: dir.path)
                didSetConfigDir = true
            }
        }
        do {
            try renderer.applyConfig(config: config)
            if let view = attachedView {
                resize(
                    width: view.bounds.width,
                    height: view.bounds.height,
                    scale: view.window?.screen.scale ?? UIScreen.main.scale
                )
            }
        } catch {
            // Surface lifecycle race or invalid path; let user retry from sheet.
        }
    }

    // MARK: - Selection bridge

    func hitTest(x: CGFloat, y: CGFloat) -> TerminalCellPosition? {
        renderer?.hitTest(xPx: Float(x), yPx: Float(y))
    }

    func wordRange(at pos: TerminalCellPosition) -> TerminalCellRange? {
        renderer?.wordRangeAt(pos: pos)
    }

    func lineRange(at pos: TerminalCellPosition) -> TerminalCellRange? {
        renderer?.lineRangeAt(pos: pos)
    }

    func selectionSet(_ range: TerminalCellRange) {
        renderer?.selectionSet(range: range)
    }

    func selectionClear() {
        renderer?.selectionClear()
    }

    @discardableResult
    func selectionAll() -> TerminalCellRange? {
        renderer?.selectionAll()
    }

    func readSelection() -> String? {
        renderer?.readSelection()
    }

    func currentSelectionRange() -> TerminalCellRange? {
        backendBridge?.currentSelectionRange()
    }

    func cellMetrics() -> TerminalCellMetrics? {
        renderer?.cellMetrics()
    }

    func surfaceMetrics() -> AgentBuddyGhosttySurfaceMetrics? {
        guard let terminal else { return nil }
        let metrics = terminal.surfaceMetrics()
        guard metrics.cellWidthPx > 0, metrics.cellHeightPx > 0 else { return nil }
        return metrics
    }

    func linkAtPoint(x: CGFloat, y: CGFloat) -> TerminalLink? {
        renderer?.linkAtPoint(xPx: Float(x), yPx: Float(y))
    }

    /// Feed the renderer the most recent viewport rows so plain-text URL
    /// detection has fresh content. The host view calls this on a
    /// debounce after writes.
    func updateViewportLinks() {
        guard let renderer, let terminal else { return }
        let text = terminal.visibleText()
        if text.isEmpty { return }
        let rows: [String] = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        renderer.setViewportText(startRow: 0, rows: rows)
    }

    func invalidate() {
        guard !isInvalidated else { return }
        isInvalidated = true
        onInput = nil
        onNativeOutputVisibilityChanged = nil
        onSelectionRangeChanged = nil
        onBell = nil
        renderer?.detach()
        renderer = nil
        backendBridge = nil
        bellListener = nil
        terminal?.invalidate()
        terminal = nil
        attachedView = nil
        pendingOutput.removeAll()
        pendingWriteBuffer.removeAll(keepingCapacity: false)
        outputFlushScheduled = false
        didSetConfigDir = false
        setNativeOutputVisible(false)
    }

    func clearScreen() {
        guard let terminal else {
            pendingOutput.removeAll()
            setNativeOutputVisible(false)
            return
        }
        terminal.writeOutput(Data([0x1B, 0x63]))
        pendingWriteBuffer.removeAll(keepingCapacity: true)
        outputFlushScheduled = false
        setNativeOutputVisible(false)
    }

    private func flushPendingOutput() {
        guard !isInvalidated else { return }
        guard let terminal else { return }
        for data in pendingOutput {
            enqueueOutput(data)
        }
        pendingOutput.removeAll()
        flushOutputBuffer()
        updateNativeOutputVisibility(terminal: terminal)
    }

    private func enqueueOutput(_ data: Data) {
        guard !isInvalidated else { return }
        pendingWriteBuffer.append(data)
        scheduleOutputFlush()
    }

    private func scheduleOutputFlush() {
        guard !outputFlushScheduled else { return }
        outputFlushScheduled = true
        DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(8)) { [weak self] in
            self?.flushOutputBuffer()
        }
    }

    private func flushOutputBuffer() {
        outputFlushScheduled = false
        guard !isInvalidated else {
            pendingWriteBuffer.removeAll(keepingCapacity: false)
            return
        }
        guard !pendingWriteBuffer.isEmpty else { return }
        guard let terminal else {
            pendingOutput.append(pendingWriteBuffer)
            pendingWriteBuffer.removeAll(keepingCapacity: true)
            return
        }

        let data = pendingWriteBuffer
        pendingWriteBuffer.removeAll(keepingCapacity: true)
        renderer?.feedOutput(bytes: data)
        terminal.writeOutput(data)
        updateNativeOutputVisibility(terminal: terminal)
    }

    private func updateNativeOutputVisibility(terminal: AgentBuddyGhosttyTerminal) {
        guard !hasNativeVisibleOutput else { return }
        let text = terminal.visibleText()
        if text.contains(where: { !$0.isWhitespace }) {
            setNativeOutputVisible(true)
        }
    }

    private func setNativeOutputVisible(_ value: Bool) {
        guard hasNativeVisibleOutput != value else { return }
        hasNativeVisibleOutput = value
        onNativeOutputVisibilityChanged?(value)
    }
}

private final class GhosttyTerminalOutputBatcher: @unchecked Sendable {
    private let lock = NSLock()
    private var buffer = Data()
    private var scheduled = false
    private let flush: @MainActor (Data) -> Void

    init(flush: @escaping @MainActor (Data) -> Void) {
        self.flush = flush
    }

    func append(_ data: Data) {
        guard !data.isEmpty else { return }
        var shouldSchedule = false
        lock.lock()
        buffer.append(data)
        if !scheduled {
            scheduled = true
            shouldSchedule = true
        }
        lock.unlock()

        if shouldSchedule {
            DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(8)) { [weak self] in
                self?.flushNow()
            }
        }
    }

    private func flushNow() {
        lock.lock()
        let data = buffer
        buffer.removeAll(keepingCapacity: true)
        scheduled = false
        lock.unlock()

        guard !data.isEmpty else { return }
        Task { @MainActor [flush] in
            flush(data)
        }
    }
}

/// Closure-backed adapter implementing the Rust `TerminalBellListener`
/// callback interface. Rust holds an `Arc` to the listener for as long
/// as it's subscribed; we keep a strong reference from the renderer so it
/// outlives the renderer itself.
private final class TerminalRendererBellListener: TerminalBellListener, @unchecked Sendable {
    private let block: () -> Void

    init(_ block: @escaping () -> Void) {
        self.block = block
    }

    func onBell() {
        block()
    }
}
