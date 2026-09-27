import SwiftUI

// MARK: - Empty / error state

/// Explains what happened and offers exactly one next step. Every state gets its
/// own specific action instead of a generic "Retry".
struct BuddyEmptyState: View {
    let systemImage: String
    let title: LocalizedStringKey
    let message: Text
    var actionTitle: LocalizedStringKey?
    var actionSystemImage: String?
    var actionKind: BuddyButtonKind = .primary
    var action: (() -> Void)?

    init(
        systemImage: String,
        title: LocalizedStringKey,
        message: LocalizedStringKey,
        actionTitle: LocalizedStringKey? = nil,
        actionSystemImage: String? = nil,
        actionKind: BuddyButtonKind = .primary,
        action: (() -> Void)? = nil
    ) {
        self.systemImage = systemImage
        self.title = title
        self.message = Text(message)
        self.actionTitle = actionTitle
        self.actionSystemImage = actionSystemImage
        self.actionKind = actionKind
        self.action = action
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            BuddyIconTile(content: .symbol(systemImage), size: 48)
            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                Text(title)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                message
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let actionTitle, let action {
                BuddyButton(actionTitle, systemImage: actionSystemImage, kind: actionKind, action: action)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }
}

// MARK: - Inline banner

/// Persistent inline notice for connection problems, failures and pending
/// sync. Critical states never rely on a transient toast.
struct BuddyBanner: View {
    enum Tone {
        case info, warning, danger, success

        var fill: Color {
            switch self {
            case .info: return AgentBuddyTheme.surfaceSoft
            case .warning: return AgentBuddyTheme.warningSurface
            case .danger: return AgentBuddyTheme.dangerSurface
            case .success: return AgentBuddyTheme.successSurface
            }
        }

        var foreground: Color {
            switch self {
            case .info: return AgentBuddyTheme.textPrimary
            case .warning: return AgentBuddyTheme.warning
            case .danger: return AgentBuddyTheme.danger
            case .success: return AgentBuddyTheme.success
            }
        }

        var defaultSymbol: String {
            switch self {
            case .info: return "info.circle"
            case .warning: return "exclamationmark.triangle"
            case .danger: return "exclamationmark.octagon"
            case .success: return "checkmark.circle"
            }
        }
    }

    let tone: Tone
    let message: Text
    var systemImage: String?
    var actionTitle: LocalizedStringKey?
    var action: (() -> Void)?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
            Image(systemName: systemImage ?? tone.defaultSymbol)
                .foregroundStyle(tone.foreground)
                .accessibilityHidden(true)
            message
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                        .frame(minHeight: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xs)
        .background(tone.fill, in: RoundedRectangle(cornerRadius: BuddyRadius.detailCard, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Sheets

extension View {
    /// Bottom sheet chrome: 30pt top corners (the platform keeps the bottom
    /// square and handles the safe area) on the Mint page background.
    func buddySheetStyle() -> some View {
        presentationCornerRadius(BuddyRadius.sheet)
            .presentationBackground(AgentBuddyTheme.background)
    }
}
