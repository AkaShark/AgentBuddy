import UIKit

extension GhosttyHostView {
    // MARK: - Edit menu (Copy / Paste / Select All)

    func presentEditMenu() {
        guard renderer?.currentSelectionRange() != nil else { return }
        guard window != nil else { return }
        // Anchor at the union of the painted selection rectangles so the
        // menu lands above the highlight instead of the finger.
        let anchor = selectionOverlay.selectionUnionRect()
        let center = anchor?.origin ?? bounds.center
        let configuration = UIEditMenuConfiguration(identifier: "agentbuddy.terminal.edit-menu" as NSString, sourcePoint: center)
        if let rect = anchor {
            // Provide a custom target rect via the delegate callback below.
            editMenu.presentEditMenu(with: configuration)
            _ = rect // captured by editMenuInteraction(_:targetRectFor:)
        } else {
            editMenu.presentEditMenu(with: configuration)
        }
    }

    func editMenuInteraction(
        _ interaction: UIEditMenuInteraction,
        targetRectFor configuration: UIEditMenuConfiguration
    ) -> CGRect {
        selectionOverlay.selectionUnionRect() ?? CGRect(origin: bounds.center, size: .zero)
    }

    func editMenuInteraction(
        _ interaction: UIEditMenuInteraction,
        menuFor configuration: UIEditMenuConfiguration,
        suggestedActions: [UIMenuElement]
    ) -> UIMenu? {
        let copy = UIAction(title: "Copy") { [weak self] _ in
            self?.copySelection()
        }
        let paste = UIAction(title: "Paste") { [weak self] _ in
            self?.pasteFromClipboard()
        }
        paste.attributes = UIPasteboard.general.hasStrings ? [] : .disabled
        let selectAll = UIAction(title: "Select All") { [weak self] _ in
            self?.selectAll()
        }
        return UIMenu(children: [copy, paste, selectAll])
    }

    private func copySelection() {
        guard let renderer else { return }
        if let text = renderer.readSelection(), !text.isEmpty {
            UIPasteboard.general.string = text
        }
        renderer.selectionClear()
    }

    private func pasteFromClipboard() {
        guard let renderer, let text = UIPasteboard.general.string, !text.isEmpty else { return }
        renderer.selectionClear()
        renderer.sendPaste(text)
    }

    private func selectAll() {
        _ = renderer?.selectionAll()
        // Re-present the menu over the new range so the user gets a
        // chance to immediately Copy without a second long-press.
        DispatchQueue.main.async { [weak self] in
            self?.presentEditMenu()
        }
    }
}

private extension CGRect {
    var center: CGPoint { CGPoint(x: midX, y: midY) }
}
