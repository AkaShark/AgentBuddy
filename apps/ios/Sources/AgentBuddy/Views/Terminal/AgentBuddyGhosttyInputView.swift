import UIKit

// MARK: - Hidden first-responder + accessory bar

/// Invisible first-responder overlaid on the Ghostty surface. We use a
/// bare `UIView` (not `UITextField`) because UITextField routes the
/// software keyboard through the `UITextInput` pipeline
/// (`replaceRange:withText:`, marked-text APIs, etc.), and overrides of
/// `insertText:` on UITextField are not invoked for every keystroke.
/// `UIView + UIKeyInput` guarantees the system calls `insertText:` /
/// `deleteBackward` for every character so we can forward each one to
/// Ghostty's encoder.
///
/// We also own the input accessory view so the row of Esc/Tab/Ctrl-…
/// keys floats above the system keyboard automatically.
final class AgentBuddyGhosttyInputView: UIView, UIKeyInput, UITextInputTraits {
    weak var renderer: GhosttyTerminalRenderer?

    // UITextInputTraits — terminal input is verbatim, no IME assistance.
    var autocorrectionType: UITextAutocorrectionType = .no
    var autocapitalizationType: UITextAutocapitalizationType = .none
    var spellCheckingType: UITextSpellCheckingType = .no
    var smartDashesType: UITextSmartDashesType = .no
    var smartQuotesType: UITextSmartQuotesType = .no
    var smartInsertDeleteType: UITextSmartInsertDeleteType = .no
    var keyboardAppearance: UIKeyboardAppearance = .dark
    var keyboardType: UIKeyboardType = .asciiCapable
    var returnKeyType: UIReturnKeyType = .default

    /// Custom accessory bar. Setting this swaps the docked input
    /// accessory view above the keyboard.
    var accessoryBar: AgentBuddyTerminalAccessoryBar? {
        didSet {
            // If the keyboard is already up, ask UIKit to swap the
            // accessory in-place.
            if isFirstResponder {
                reloadInputViews()
            }
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        isOpaque = false
        backgroundColor = .clear
        tintColor = .clear
        clipsToBounds = true
        accessibilityLabel = "Terminal input"
    }

    // MARK: - UIKeyInput

    var hasText: Bool { true }

    func insertText(_ text: String) {
        sendCommittedText(text)
    }

    func deleteBackward() {
        sendRawBytes([0x7F])
    }

    override var canBecomeFirstResponder: Bool { true }

    override var inputAccessoryView: UIView? { accessoryBar }

    /// Touches must reach the host view's gesture recognizers (tap to
    /// focus keyboard, long-press to select, scroll pan, pinch). Drop the
    /// overlay out of hit-testing entirely so it never swallows touches
    /// even though it covers the full host area.
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        nil
    }

    override func pressesBegan(_ presses: Set<UIPress>, with event: UIPressesEvent?) {
        var handled = false
        for press in presses {
            guard let key = press.key else { continue }
            if let raw = Self.rawBytes(for: key) {
                renderer?.sendRawBytes(raw)
                handled = true
                continue
            }
            if let event = Self.terminalEvent(for: key, action: .press, repeated: false) {
                renderer?.sendKeyEvent(event)
                handled = true
            }
        }
        if !handled {
            super.pressesBegan(presses, with: event)
        }
    }

    private func sendCommittedText(_ text: String) {
        guard !text.isEmpty else { return }
        // UIKeyInput delivers Return as "\n", but PTYs expect carriage
        // return for Enter. Keep other committed text byte-for-byte.
        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\n", with: "\r")
        guard let data = normalized.data(using: .utf8), !data.isEmpty else { return }
        renderer?.sendRawBytes(data)
    }

    private func sendRawBytes(_ bytes: [UInt8]) {
        renderer?.sendRawBytes(Data(bytes))
    }

    private static func rawBytes(for key: UIKey) -> Data? {
        if key.modifierFlags.contains(.control),
           let scalar = key.charactersIgnoringModifiers.lowercased().unicodeScalars.first,
           scalar.value >= 97,
           scalar.value <= 122 {
            return Data([UInt8(scalar.value - 96)])
        }

        switch key.keyCode {
        case .keyboardReturnOrEnter: return Data([0x0D])
        case .keyboardTab: return Data([0x09])
        case .keyboardDeleteOrBackspace: return Data([0x7F])
        case .keyboardEscape: return Data([0x1B])
        case .keyboardSpacebar where key.modifierFlags.contains(.control): return Data([0x00])
        case .keyboardUpArrow: return Data([0x1B, 0x5B, 0x41])
        case .keyboardDownArrow: return Data([0x1B, 0x5B, 0x42])
        case .keyboardRightArrow: return Data([0x1B, 0x5B, 0x43])
        case .keyboardLeftArrow: return Data([0x1B, 0x5B, 0x44])
        case .keyboardHome: return Data([0x1B, 0x5B, 0x48])
        case .keyboardEnd: return Data([0x1B, 0x5B, 0x46])
        case .keyboardPageUp: return Data([0x1B, 0x5B, 0x35, 0x7E])
        case .keyboardPageDown: return Data([0x1B, 0x5B, 0x36, 0x7E])
        case .keyboardDeleteForward: return Data([0x1B, 0x5B, 0x33, 0x7E])
        case .keyboardInsert: return Data([0x1B, 0x5B, 0x32, 0x7E])
        default: return nil
        }
    }

    private static func terminalEvent(
        for key: UIKey,
        action: TerminalKeyAction,
        repeated: Bool
    ) -> TerminalKeyEvent? {
        let code = mapHIDUsage(key.keyCode)
        // Only forward keys we have a code for; printable characters arrive
        // separately via `insertText` so the IME decision (e.g. dead keys)
        // stays in UIKit.
        if code == .unidentified { return nil }
        let mods = TerminalKeyMods(
            shift: key.modifierFlags.contains(.shift),
            ctrl: key.modifierFlags.contains(.control),
            alt: key.modifierFlags.contains(.alternate),
            meta: key.modifierFlags.contains(.command)
        )
        return TerminalKeyEvent(
            action: action,
            code: code,
            mods: mods,
            text: key.characters,
            repeat: repeated
        )
    }

    private static func mapHIDUsage(_ keyCode: UIKeyboardHIDUsage) -> TerminalKeyCode {
        switch keyCode {
        case .keyboardReturnOrEnter: return .enter
        case .keyboardTab: return .tab
        case .keyboardDeleteOrBackspace: return .backspace
        case .keyboardEscape: return .escape
        case .keyboardSpacebar: return .space
        case .keyboardUpArrow: return .arrowUp
        case .keyboardDownArrow: return .arrowDown
        case .keyboardLeftArrow: return .arrowLeft
        case .keyboardRightArrow: return .arrowRight
        case .keyboardPageUp: return .pageUp
        case .keyboardPageDown: return .pageDown
        case .keyboardHome: return .home
        case .keyboardEnd: return .end
        case .keyboardDeleteForward: return .delete
        case .keyboardInsert: return .insert
        case .keyboardF1: return .f1
        case .keyboardF2: return .f2
        case .keyboardF3: return .f3
        case .keyboardF4: return .f4
        case .keyboardF5: return .f5
        case .keyboardF6: return .f6
        case .keyboardF7: return .f7
        case .keyboardF8: return .f8
        case .keyboardF9: return .f9
        case .keyboardF10: return .f10
        case .keyboardF11: return .f11
        case .keyboardF12: return .f12
        default: return .unidentified
        }
    }
}
