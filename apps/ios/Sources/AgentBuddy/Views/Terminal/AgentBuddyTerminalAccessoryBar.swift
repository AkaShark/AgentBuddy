import UIKit

/// Compact row of common terminal keys docked above the system keyboard.
/// Each chip sends a fixed escape sequence (or fires a callback) so the
/// user doesn't need to know how to escape control chars from the IME.
final class AgentBuddyTerminalAccessoryBar: UIView {
    /// Send a raw UTF-8 string straight to the PTY (Esc/Tab/Ctrl-C, etc).
    var onSendRaw: ((String) -> Void)?
    /// Paste the current `UIPasteboard.string` (bracket-pasted by Rust).
    var onPaste: (() -> Void)?
    /// Wipe local scrollback + screen.
    var onClear: (() -> Void)?
    /// Forward selected output to the assistant thread.
    var onSendToAssistant: (() -> Void)?

    private let scrollView = UIScrollView()
    private let stack = UIStackView()
    /// Buttons that need to enable/disable based on pasteboard state.
    private weak var pasteButton: UIButton?

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
        rebuildKeys()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
        rebuildKeys()
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: 44)
    }

    private func configure() {
        backgroundColor = UIColor.black.withAlphaComponent(0.96)
        translatesAutoresizingMaskIntoConstraints = false
        autoresizingMask = [.flexibleWidth]
        frame = CGRect(x: 0, y: 0, width: 320, height: 44)

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.alwaysBounceHorizontal = true
        addSubview(scrollView)

        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .horizontal
        stack.spacing = 6
        stack.alignment = .center
        stack.isLayoutMarginsRelativeArrangement = true
        stack.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 5, leading: 10, bottom: 5, trailing: 10)
        scrollView.addSubview(stack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stack.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),
        ])

        let topHairline = UIView()
        topHairline.translatesAutoresizingMaskIntoConstraints = false
        topHairline.backgroundColor = UIColor.white.withAlphaComponent(0.12)
        addSubview(topHairline)
        NSLayoutConstraint.activate([
            topHairline.topAnchor.constraint(equalTo: topAnchor),
            topHairline.leadingAnchor.constraint(equalTo: leadingAnchor),
            topHairline.trailingAnchor.constraint(equalTo: trailingAnchor),
            topHairline.heightAnchor.constraint(equalToConstant: 1.0 / UIScreen.main.scale),
        ])

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(pasteboardChanged),
            name: UIPasteboard.changedNotification,
            object: nil
        )
    }

    private func rebuildKeys() {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        addRawKey(title: "Esc", payload: "\u{1B}")
        addRawKey(title: "Tab", payload: "\t")
        addRawKey(title: "Ctrl-C", payload: "\u{03}")
        addRawKey(title: "Ctrl-D", payload: "\u{04}")
        addRawKey(title: "Ctrl-Z", payload: "\u{1A}")
        addRawKey(title: "↑", payload: "\u{1B}[A")
        addRawKey(title: "↓", payload: "\u{1B}[B")
        addRawKey(title: "←", payload: "\u{1B}[D")
        addRawKey(title: "→", payload: "\u{1B}[C")
        pasteButton = addActionKey(title: "Paste") { [weak self] in
            self?.onPaste?()
        }
        updatePasteState()
        _ = addActionKey(title: "Clear") { [weak self] in
            self?.onClear?()
        }
        _ = addActionKey(title: "Send to AI") { [weak self] in
            self?.onSendToAssistant?()
        }
    }

    private func addRawKey(title: String, payload: String) {
        let button = makeKey(title: title)
        button.addAction(UIAction { [weak self] _ in
            self?.onSendRaw?(payload)
        }, for: .touchUpInside)
        stack.addArrangedSubview(button)
    }

    @discardableResult
    private func addActionKey(title: String, action: @escaping () -> Void) -> UIButton {
        let button = makeKey(title: title)
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
        stack.addArrangedSubview(button)
        return button
    }

    private func makeKey(title: String) -> UIButton {
        var config = UIButton.Configuration.gray()
        config.title = title
        config.baseForegroundColor = .white.withAlphaComponent(0.86)
        config.baseBackgroundColor = UIColor.white.withAlphaComponent(0.10)
        config.background.cornerRadius = 8
        config.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
        config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attrs in
            var out = attrs
            out.font = UIFont(name: "SFMono-Regular", size: 13) ?? .monospacedSystemFont(ofSize: 13, weight: .regular)
            return out
        }
        let button = UIButton(configuration: config)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.heightAnchor.constraint(equalToConstant: 34).isActive = true
        return button
    }

    @objc private func pasteboardChanged() {
        updatePasteState()
    }

    private func updatePasteState() {
        guard let button = pasteButton else { return }
        button.isEnabled = UIPasteboard.general.hasStrings
        button.alpha = button.isEnabled ? 1.0 : 0.45
    }
}
