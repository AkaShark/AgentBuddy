import SwiftUI

// MARK: - Mint grouped-list styling for Settings screens

/// Icon + title + optional subtitle used as the label of settings rows
/// (navigation links, toggles, pickers). Icons are 17pt in a 24pt column so
/// titles line up across rows.
struct SettingsMintRowLabel: View {
    private let title: Text
    private let subtitle: Text?
    private let systemImage: String?
    private let iconTint: Color

    init(
        _ title: LocalizedStringKey,
        subtitle: LocalizedStringKey? = nil,
        systemImage: String? = nil,
        iconTint: Color = AgentBuddyTheme.textSecondary
    ) {
        self.title = Text(title)
        self.subtitle = subtitle.map { Text($0) }
        self.systemImage = systemImage
        self.iconTint = iconTint
    }

    init(
        title: Text,
        subtitle: Text? = nil,
        systemImage: String? = nil,
        iconTint: Color = AgentBuddyTheme.textSecondary
    ) {
        self.title = title
        self.subtitle = subtitle
        self.systemImage = systemImage
        self.iconTint = iconTint
    }

    var body: some View {
        HStack(spacing: BuddySpacing.sm) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(iconTint)
                    .frame(width: 24)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 2) {
                title
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                if let subtitle {
                    subtitle
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(.vertical, BuddySpacing.xxs)
    }
}

/// Trailing checkmark for the selected row of a single-choice list. The
/// selection is also exposed to VoiceOver, so it never relies on the icon alone.
struct SettingsMintCheckmark: View {
    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(AgentBuddyTheme.link)
            .accessibilityHidden(true)
    }
}

extension View {
    /// Page background for a settings `Form` / `List`.
    func settingsMintList() -> some View {
        scrollContentBackground(.hidden)
            .buddyPageBackground()
    }

    /// Surface fill for a settings row.
    func settingsMintRow() -> some View {
        listRowBackground(AgentBuddyTheme.surface)
            .listRowSeparatorTint(AgentBuddyTheme.border)
    }

    /// Caption section header in textSecondary, sentence case.
    func settingsMintHeader() -> some View {
        buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .textCase(nil)
    }

    /// Caption section footer in textSecondary.
    func settingsMintFooter() -> some View {
        buddyText(.caption)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
    }

    /// Text field that is a whole settings row: the row is the field, so it
    /// takes body text and a 44pt minimum height without its own outline.
    func settingsMintFormField() -> some View {
        buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .frame(minHeight: BuddySize.minHitTarget)
    }

    /// Bordered input inside a settings row that also holds other content:
    /// body text, 48pt tall, required-control outline.
    func settingsMintInputField() -> some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
        return buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.control)
            .background(AgentBuddyTheme.surface, in: shape)
            .overlay { shape.strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1) }
    }

    /// Selected-row trait for single-choice settings lists.
    func settingsMintSelected(_ isSelected: Bool) -> some View {
        accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
