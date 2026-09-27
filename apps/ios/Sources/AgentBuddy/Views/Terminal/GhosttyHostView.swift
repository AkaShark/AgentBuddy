import UIKit
import QuartzCore
import AudioToolbox

// MARK: - Host view

/// Terminal canvas. Hosts the Metal-backed Ghostty surface, the
/// selection-handle overlay, and the hidden first-responder text field
/// that owns the keyboard + accessory bar. Coordinates every gesture the
/// touch UX needs: long-press to select, drag handles to extend, tap to
/// focus keyboard / open links, pinch to resize, two-finger pan to
/// scroll, and one-finger pan when an in-terminal mouse-tracking app
/// captures the mouse.
final class GhosttyHostView: UIView, UIGestureRecognizerDelegate, UIEditMenuInteractionDelegate {
    private static let bellHapticThrottle: TimeInterval = 0.25
    private static let selectionDragSlop: CGFloat = 8.0

    weak var renderer: GhosttyTerminalRenderer? {
        didSet {
            keyboardOverlay.renderer = renderer
            attachRendererCallbacks()
        }
    }

    /// Tapped Clear in the accessory bar.
    var onClearTapped: (() -> Void)?
    /// Tapped Send-to-AI in the accessory bar.
    var onSendToAssistant: (() -> Void)?
    /// Pinch ended at this point size.
    var onFontSizePinched: ((Double) -> Void)?

    /// The owner's authoritative font size; we apply pinch deltas against
    /// this so the gesture is stable across multiple pinches in a row.
    var currentFontSize: Double = 13.0

    var scrollPan: UIPanGestureRecognizer?
    var dragPan: UIPanGestureRecognizer?
    var longPress: UILongPressGestureRecognizer?
    var pinch: UIPinchGestureRecognizer?
    var tap: UITapGestureRecognizer?
    var lastScrollTranslation: CGPoint = .zero
    var dragInProgress = false
    var selectionDragInProgress = false
    var selectionAnchorPos: TerminalCellPosition?
    var pinchStartFontSize: Double = 13.0
    private var lastBellTimestamp: TimeInterval = 0
    private var keyboardIsVisible = false
    private var isDismantled = false

    private let keyboardOverlay = AgentBuddyGhosttyInputView()
    private let accessoryBar = AgentBuddyTerminalAccessoryBar()
    let selectionOverlay = TerminalSelectionOverlayView()
    lazy var editMenu = UIEditMenuInteraction(delegate: self)

    override class var layerClass: AnyClass { CAMetalLayer.self }

    override var canBecomeFirstResponder: Bool { true }

    override init(frame: CGRect) {
        super.init(frame: frame)
        installSubviews()
        installGestureRecognizers()
        installAccessoryActions()
        addInteraction(editMenu)
        addKeyboardObservers()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        installSubviews()
        installGestureRecognizers()
        installAccessoryActions()
        addInteraction(editMenu)
        addKeyboardObservers()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    private func installSubviews() {
        keyboardOverlay.translatesAutoresizingMaskIntoConstraints = false
        addSubview(keyboardOverlay)
        NSLayoutConstraint.activate([
            keyboardOverlay.topAnchor.constraint(equalTo: topAnchor, constant: -9999),
            keyboardOverlay.leadingAnchor.constraint(equalTo: leadingAnchor, constant: -9999),
            keyboardOverlay.widthAnchor.constraint(equalToConstant: 1),
            keyboardOverlay.heightAnchor.constraint(equalToConstant: 1),
        ])
        // The input view only needs to be in the responder chain. Keeping
        // it tiny and far offscreen prevents UIKit's text input adornments
        // from drawing a caret over the terminal surface.
        keyboardOverlay.accessoryBar = accessoryBar

        selectionOverlay.translatesAutoresizingMaskIntoConstraints = false
        addSubview(selectionOverlay)
        NSLayoutConstraint.activate([
            selectionOverlay.topAnchor.constraint(equalTo: topAnchor),
            selectionOverlay.leadingAnchor.constraint(equalTo: leadingAnchor),
            selectionOverlay.trailingAnchor.constraint(equalTo: trailingAnchor),
            selectionOverlay.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        selectionOverlay.onHandleDrag = { [weak self] handle, location, state in
            self?.handleSelectionHandleDrag(handle: handle, location: location, state: state)
        }
    }

    private func installAccessoryActions() {
        accessoryBar.onSendRaw = { [weak self] payload in
            guard let renderer = self?.renderer else { return }
            // Raw control sequences (Esc, Tab, Ctrl-C, arrows) go straight
            // to the PTY input direction without the bracketed-paste
            // wrapper. iSH / busybox shells don't enable paste mode by
            // default, so the wrapper would print as literal text and
            // break the keystroke.
            if let data = payload.data(using: .utf8) {
                renderer.sendRawBytes(data)
            }
        }
        accessoryBar.onPaste = { [weak self] in
            guard let renderer = self?.renderer else { return }
            if let text = UIPasteboard.general.string {
                renderer.sendPaste(text)
            }
        }
        accessoryBar.onClear = { [weak self] in
            self?.onClearTapped?()
        }
        accessoryBar.onSendToAssistant = { [weak self] in
            self?.onSendToAssistant?()
        }
    }

    override func becomeFirstResponder() -> Bool {
        let hostResult = super.becomeFirstResponder()
        let inputResult = focusTerminalInput()
        return hostResult || inputResult
    }

    private func addKeyboardObservers() {
        let nc = NotificationCenter.default
        nc.addObserver(
            self,
            selector: #selector(keyboardFrameWillChange(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification,
            object: nil
        )
        nc.addObserver(
            self,
            selector: #selector(keyboardWillHide(_:)),
            name: UIResponder.keyboardWillHideNotification,
            object: nil
        )
    }

    // MARK: - Renderer wiring

    private func attachRendererCallbacks() {
        guard let renderer else { return }
        renderer.onSelectionRangeChanged = { [weak self] range in
            self?.selectionOverlay.range = range
            self?.selectionOverlay.metrics = self?.renderer?.cellMetrics()
        }
        renderer.onBell = { [weak self] in
            self?.fireBellHaptic()
        }
    }

    private func fireBellHaptic() {
        let now = CACurrentMediaTime()
        if now - lastBellTimestamp < Self.bellHapticThrottle { return }
        lastBellTimestamp = now
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
        // Audible bell when system sound is enabled; respects the silent
        // switch via AudioServices.
        AudioServicesPlaySystemSound(1057)
    }

    // MARK: - Lifecycle

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            guard !isDismantled else { return }
            isUserInteractionEnabled = true
            gestureRecognizers?.forEach { $0.isEnabled = true }
            if keyboardOverlay.accessoryBar == nil {
                keyboardOverlay.accessoryBar = accessoryBar
            }
            keyboardOverlay.renderer = renderer
            selectionOverlay.setContentScale(contentScale)
            renderer?.attach(to: self)
            renderer?.setOccluded(false)
            // Re-attach can happen after `parkForTemporaryDetach()` left
            // focus = false. Restore it so the Ghostty surface accepts
            // text input again.
            renderer?.setFocused(true)
            _ = focusTerminalInput()
        } else {
            if !isDismantled {
                parkForTemporaryDetach()
            }
        }
    }

    /// UIKit can temporarily remove a representable view from a window
    /// during navigation transitions. Park focus, but keep recognizers and
    /// the accessory bar intact in case the same view is reattached.
    private func parkForTemporaryDetach() {
        keyboardIsVisible = false
        keyboardOverlay.resignFirstResponder()
        _ = resignFirstResponder()
        renderer?.setFocused(false)
        renderer?.setOccluded(true)
    }

    /// Final teardown from `UIViewRepresentable.dismantleUIView`.
    func teardownForDismissal() {
        isDismantled = true
        isUserInteractionEnabled = false
        gestureRecognizers?.forEach { $0.isEnabled = false }
        keyboardOverlay.renderer = nil
        // Clear the accessory bar reference; the overlay's
        // `inputAccessoryView` override reads from `accessoryBar` so
        // setting it to nil drops the docked bar above the keyboard.
        keyboardOverlay.accessoryBar = nil
        keyboardOverlay.resignFirstResponder()
        endEditing(true)
        _ = resignFirstResponder()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard !isDismantled else { return }
        let scale = contentScale
        renderer?.resize(width: bounds.width, height: bounds.height, scale: scale)
        selectionOverlay.setContentScale(scale)
        selectionOverlay.metrics = renderer?.cellMetrics()
    }

    @objc private func keyboardFrameWillChange(_ notification: Notification) {
        updateKeyboardVisibility(from: notification)
        // SwiftUI's GeometryReader receives a new size when SwiftUI's
        // safe-area path adjusts for the keyboard; that re-fires
        // `resizeTerminal(for:)` on the SwiftUI side. We re-trigger
        // layoutSubviews here as a belt-and-suspenders to recompute the
        // grid promptly in case SwiftUI doesn't push a new size (e.g.
        // when the keyboard slides on a screen that already uses
        // ignoresSafeArea on its content area).
        setNeedsLayout()
    }

    @objc private func keyboardWillHide(_ notification: Notification) {
        keyboardIsVisible = false
        setNeedsLayout()
    }

    // MARK: - Helpers

    @discardableResult
    func focusTerminalInput(forceRefresh: Bool = false) -> Bool {
        guard !isDismantled, window != nil else { return false }
        if keyboardOverlay.accessoryBar == nil {
            keyboardOverlay.accessoryBar = accessoryBar
        }
        keyboardOverlay.renderer = renderer
        renderer?.setFocused(true)

        if keyboardOverlay.isFirstResponder {
            keyboardOverlay.reloadInputViews()
            if forceRefresh && !keyboardIsVisible {
                keyboardOverlay.resignFirstResponder()
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.window != nil, !self.isDismantled else { return }
                    self.keyboardOverlay.becomeFirstResponder()
                    self.renderer?.setFocused(true)
                }
            }
            return true
        }

        return keyboardOverlay.becomeFirstResponder()
    }

    private func updateKeyboardVisibility(from notification: Notification) {
        guard let window,
              let screenFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else {
            return
        }
        let keyboardFrame = window.convert(screenFrame, from: nil)
        keyboardIsVisible = keyboardFrame.intersects(window.bounds) && keyboardFrame.minY < window.bounds.maxY
    }

    var contentScale: CGFloat {
        window?.screen.scale ?? UIScreen.main.scale
    }
}
