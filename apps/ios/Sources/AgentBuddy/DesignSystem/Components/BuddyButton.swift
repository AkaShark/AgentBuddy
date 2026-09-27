import SwiftUI

// MARK: - Button kinds

/// Button hierarchy from the Mint design system. A screen keeps one strongest
/// action: use `.primary` (or `.brand` on neutral pages) once per view.
enum BuddyButtonKind {
    /// action / onAction — the main decision ("允许一次", "开始任务").
    case primary
    /// brand / onBrand — emphasis without being the destructive-or-final step ("扫码连接").
    case brand
    /// surface / textPrimary with a hairline border ("拒绝", "稍后处理").
    case secondary
    /// surfaceSoft / textPrimary, no border ("在这台主机开始任务").
    case soft
    /// Text-only link action.
    case quiet
    /// errorSurface / error.
    case destructive
}

// MARK: - Style

/// 48pt minimum height, 20pt horizontal padding, radius 16, Label type.
/// Grows with Dynamic Type instead of clipping.
struct BuddyButtonStyle: ButtonStyle {
    var kind: BuddyButtonKind = .primary
    var isLoading = false
    var fullWidth = true

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        HStack(spacing: BuddySpacing.xs) {
            if isLoading {
                ProgressView()
                    .controlSize(.small)
                    .tint(foreground)
            }
            configuration.label
                .lineLimit(2)
                .multilineTextAlignment(.center)
        }
        .buddyText(.label, weight: .semibold)
        .foregroundStyle(foreground)
        .padding(.horizontal, kind == .quiet ? BuddySpacing.xs : BuddySpacing.lg)
        .padding(.vertical, BuddySpacing.xs)
        .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: BuddySize.control)
        .background(background, in: shape)
        .overlay {
            if kind == .secondary {
                shape.strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
            }
        }
        .contentShape(shape)
        .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
        .opacity(configuration.isPressed ? 0.9 : 1)
        .animation(BuddyMotion.animation(.press, reduceMotion: reduceMotion), value: configuration.isPressed)
    }

    private var foreground: Color {
        guard isEnabled || isLoading else { return AgentBuddyTheme.onDisabled }
        switch kind {
        case .primary: return AgentBuddyTheme.onAction
        case .brand: return AgentBuddyTheme.onBrand
        case .secondary, .soft: return AgentBuddyTheme.textPrimary
        case .quiet: return AgentBuddyTheme.link
        case .destructive: return AgentBuddyTheme.danger
        }
    }

    private var background: Color {
        guard isEnabled || isLoading else {
            return kind == .quiet ? .clear : AgentBuddyTheme.disabled
        }
        switch kind {
        case .primary: return AgentBuddyTheme.action
        case .brand: return AgentBuddyTheme.brand
        case .secondary: return AgentBuddyTheme.surface
        case .soft: return AgentBuddyTheme.surfaceSoft
        case .quiet: return .clear
        case .destructive: return AgentBuddyTheme.dangerSurface
        }
    }
}

// MARK: - Convenience view

/// A button that shows a spinner while `isLoading` and blocks repeat taps,
/// keeping its label so the width does not jump.
struct BuddyButton: View {
    private let title: Text
    private let systemImage: String?
    private let trailingSystemImage: String?
    private let kind: BuddyButtonKind
    private let isLoading: Bool
    private let fullWidth: Bool
    private let action: () -> Void

    init(
        _ title: LocalizedStringKey,
        systemImage: String? = nil,
        trailingSystemImage: String? = nil,
        kind: BuddyButtonKind = .primary,
        isLoading: Bool = false,
        fullWidth: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = Text(title)
        self.systemImage = systemImage
        self.trailingSystemImage = trailingSystemImage
        self.kind = kind
        self.isLoading = isLoading
        self.fullWidth = fullWidth
        self.action = action
    }

    init(
        verbatim title: String,
        systemImage: String? = nil,
        trailingSystemImage: String? = nil,
        kind: BuddyButtonKind = .primary,
        isLoading: Bool = false,
        fullWidth: Bool = true,
        action: @escaping () -> Void
    ) {
        self.title = Text(verbatim: title)
        self.systemImage = systemImage
        self.trailingSystemImage = trailingSystemImage
        self.kind = kind
        self.isLoading = isLoading
        self.fullWidth = fullWidth
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: BuddySpacing.xs) {
                if let systemImage, !isLoading {
                    Image(systemName: systemImage)
                        .imageScale(.medium)
                        .accessibilityHidden(true)
                }
                title
                if let trailingSystemImage {
                    Image(systemName: trailingSystemImage)
                        .imageScale(.small)
                        .accessibilityHidden(true)
                }
            }
        }
        .buttonStyle(BuddyButtonStyle(kind: kind, isLoading: isLoading, fullWidth: fullWidth))
        .allowsHitTesting(!isLoading)
        .accessibilityAddTraits(isLoading ? .updatesFrequently : [])
    }
}
