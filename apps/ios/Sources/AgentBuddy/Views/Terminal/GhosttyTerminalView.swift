import SwiftUI
import UIKit

// MARK: - SwiftUI bridge

struct GhosttyTerminalView: UIViewRepresentable {
    let renderer: GhosttyTerminalRenderer
    let onNativeOutputVisibilityChanged: (Bool) -> Void
    let onInput: (Data) -> Void
    /// Tapped Clear in the accessory bar.
    var onClearTapped: (() -> Void)?
    /// Tapped "Send to AI" in the accessory bar.
    var onSendToAssistant: (() -> Void)?
    /// Pinch ended at this point size (clamped 10–24). The owner persists
    /// it back and re-applies the config so the terminal re-grids.
    var onFontSizePinched: ((Double) -> Void)?
    /// Initial font size for the pinch base. Re-read on every update so
    /// the SwiftUI source-of-truth and the host view stay in lockstep.
    var fontSize: Double = 13.0

    func makeUIView(context: Context) -> GhosttyHostView {
        let view = GhosttyHostView()
        view.backgroundColor = .black
        view.isOpaque = true
        view.renderer = renderer
        view.onClearTapped = onClearTapped
        view.onSendToAssistant = onSendToAssistant
        view.onFontSizePinched = onFontSizePinched
        view.currentFontSize = fontSize
        renderer.onInput = onInput
        renderer.onNativeOutputVisibilityChanged = onNativeOutputVisibilityChanged
        renderer.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: GhosttyHostView, context: Context) {
        renderer.onInput = onInput
        renderer.onNativeOutputVisibilityChanged = onNativeOutputVisibilityChanged
        uiView.renderer = renderer
        uiView.onClearTapped = onClearTapped
        uiView.onSendToAssistant = onSendToAssistant
        uiView.onFontSizePinched = onFontSizePinched
        uiView.currentFontSize = fontSize
        renderer.resize(
            width: uiView.bounds.width,
            height: uiView.bounds.height,
            scale: uiView.window?.screen.scale ?? UIScreen.main.scale
        )
    }

    static func dismantleUIView(_ uiView: GhosttyHostView, coordinator: ()) {
        let renderer = uiView.renderer
        uiView.teardownForDismissal()
        uiView.renderer = nil
        renderer?.invalidate()
    }
}
