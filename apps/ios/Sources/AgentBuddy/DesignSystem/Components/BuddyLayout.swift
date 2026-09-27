import SwiftUI

// MARK: - Section header

/// "正在进行 01" style header: heading text, optional count, optional trailing action.
struct BuddySectionHeader<Trailing: View>: View {
    let title: Text
    var count: Int?
    @ViewBuilder var trailing: () -> Trailing

    init(_ title: LocalizedStringKey, count: Int? = nil, @ViewBuilder trailing: @escaping () -> Trailing) {
        self.title = Text(title)
        self.count = count
        self.trailing = trailing
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
            title
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            if let count {
                Text(verbatim: String(format: "%02d", count))
                    .buddyText(.caption)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            }
            Spacer(minLength: BuddySpacing.xs)
            trailing()
        }
        .frame(minHeight: BuddySize.minHitTarget)
    }
}

extension BuddySectionHeader where Trailing == EmptyView {
    init(_ title: LocalizedStringKey, count: Int? = nil) {
        self.init(title, count: count) { EmptyView() }
    }
}

// MARK: - Page hero

/// Page title block: optional eyebrow, title, optional subtitle, optional
/// trailing action aligned with the title.
struct BuddyPageHeader<Trailing: View>: View {
    var eyebrow: LocalizedStringKey?
    let title: LocalizedStringKey
    var subtitle: Text?
    var titleStyle: BuddyTextStyle = .display
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        HStack(alignment: .top, spacing: BuddySpacing.md) {
            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                if let eyebrow {
                    Text(eyebrow)
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                Text(title)
                    .buddyText(titleStyle)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    subtitle
                        .buddyText(.body)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
            trailing()
        }
    }
}

extension BuddyPageHeader where Trailing == EmptyView {
    init(eyebrow: LocalizedStringKey? = nil, title: LocalizedStringKey, subtitle: Text? = nil, titleStyle: BuddyTextStyle = .display) {
        self.init(eyebrow: eyebrow, title: title, subtitle: subtitle, titleStyle: titleStyle) { EmptyView() }
    }
}

// MARK: - List row

/// Tile + title + subtitle + chevron row used for recent tasks, projects and
/// settings-like lists. At least 64pt tall; title up to two lines.
struct BuddyListRow<Tile: View, Accessory: View>: View {
    let title: Text
    var subtitle: Text?
    @ViewBuilder var tile: () -> Tile
    @ViewBuilder var accessory: () -> Accessory

    var body: some View {
        HStack(spacing: BuddySpacing.md) {
            tile()
            VStack(alignment: .leading, spacing: 2) {
                title
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                if let subtitle {
                    subtitle
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            accessory()
        }
        .padding(.vertical, BuddySpacing.sm)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
    }
}

extension BuddyListRow where Accessory == Image {
    init(title: Text, subtitle: Text? = nil, @ViewBuilder tile: @escaping () -> Tile) {
        self.title = title
        self.subtitle = subtitle
        self.tile = tile
        self.accessory = {
            Image(systemName: "chevron.right")
        }
    }
}

/// Hairline divider in the decorative border colour.
struct BuddyDivider: View {
    var body: some View {
        Rectangle()
            .fill(AgentBuddyTheme.border)
            .frame(height: 1)
            .accessibilityHidden(true)
    }
}
