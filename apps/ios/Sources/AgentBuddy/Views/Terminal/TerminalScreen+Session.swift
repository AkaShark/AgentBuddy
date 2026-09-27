import SwiftUI
import UIKit

extension TerminalScreen {
    func attachOutputSink() {
        let renderer = ghosttyRenderer
        let outputSink = renderer.makeOutputSink()
        controller.setOutputSink { data in
            outputSink(data)
        }
    }

    func updateTerminalContentSize(_ size: CGSize) {
        guard size.width > 0, size.height > 0 else { return }
        DispatchQueue.main.async {
            if terminalSurfaceSize != size {
                terminalSurfaceSize = size
            }
            scheduleTerminalRegrid(for: size)
        }
    }

    /// Forward terminal text to the assistant on the current active
    /// thread. If a painted selection is active, prefer that text; else
    /// send the visible viewport.
    func sendOutputToAssistant() {
        guard let threadKey = AppModel.shared.snapshot?.activeThread else { return }
        let selection = ghosttyRenderer.readSelection()?.trimmingCharacters(in: .whitespacesAndNewlines)
        let fallback = controller.output.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload = (selection?.isEmpty == false ? selection : nil) ?? fallback
        guard !payload.isEmpty else { return }
        Task {
            try? await ghosttyRenderer.sendTextToAssistant(
                store: AppModel.shared.store,
                threadKey: threadKey,
                selection: payload
            )
        }
    }

    /// Recompute PTY cols/rows. Prefer the renderer's live cell metrics —
    /// they're driven by Ghostty's actual font measurement so font-size
    /// changes, rotation, and keyboard show/hide all yield correct grids.
    /// Falls back to a font-size-aware estimate only when the renderer
    /// hasn't yet reported metrics (first frame of attach).
    private func resizeTerminal(for size: CGSize) {
        let scale = UIScreen.main.scale
        let grid: TerminalGridSize
        if let metrics = ghosttyRenderer.surfaceMetrics(),
           TerminalGridSize.metricsAreCurrent(metrics, for: size, contentScale: scale) {
            grid = TerminalGridSize(metrics: metrics)
        } else {
            grid = TerminalGridSize(estimatedFor: size, fontSize: storedFontSize)
        }
        ghosttyRenderer.setGridSize(cols: grid.cols, rows: grid.rows)
        guard grid != terminalGridSize else { return }
        terminalGridSize = grid
        let notifyBackend = selectedBackend?.supportsResize == true
        Task {
            await controller.resize(cols: grid.cols, rows: grid.rows, notifyBackend: notifyBackend)
        }
    }

    func applyConfigSettings() {
        applyConfigSettings(
            fontSize: storedFontSize,
            themeId: storedThemeId,
            cursorBlink: storedCursorBlink
        )
    }

    func applyConfigSettings(
        fontSize: Double,
        themeId: String,
        cursorBlink: Bool,
        regrid: Bool = false
    ) {
        let config = TerminalConfig(
            theme: TerminalThemeChoice.preset(forId: themeId),
            fontFamily: "SFMono-Regular",
            fontSizePt: Float(fontSize),
            cursorStyle: .bar,
            cursorBlink: cursorBlink,
            scrollbackLines: 10_000
        )
        ghosttyRenderer.applyConfig(config)
        if regrid {
            scheduleTerminalRegrid()
        }
    }

    private func scheduleTerminalRegrid(for explicitSize: CGSize? = nil) {
        let size = explicitSize ?? terminalSurfaceSize
        guard size.width > 0, size.height > 0 else { return }
        for delay in [0.0, 0.05, 0.16, 0.35] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                let latestSize = terminalSurfaceSize
                guard latestSize.width > 0, latestSize.height > 0 else { return }
                resizeTerminal(for: latestSize)
            }
        }
    }
}
