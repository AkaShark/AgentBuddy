import SwiftUI

// MARK: - Mint styling for timeline detail rows

/// Presentation-only helpers shared by the conversation timeline's tool,
/// command and preview rows: the collapsible "detail summary" card, the code
/// surface, the status glyph and the disclosure chevron. They hold no state and
/// shape no data.
enum TimelineCodeStyle {
    /// Fill behind code, commands and output. Inside a surface card it is
    /// `surfaceSoft`; directly on the page it is `surfaceSoft` in light mode
    /// and `codeBackground` in dark mode.
    static func fill(nested: Bool) -> Color {
        if nested || ThemeStore.shared.colorScheme != .dark {
            return AgentBuddyTheme.surfaceSoft
        }
        return AgentBuddyTheme.codeBackground
    }
}

extension View {
    /// Collapsible detail summary card: surface, 1pt border, radius 16.
    func timelineDetailCard() -> some View {
        buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
    }

    /// Code / command / output surface with the small-control radius (12).
    func timelineCodeSurface(nested: Bool = true) -> some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
        return background(TimelineCodeStyle.fill(nested: nested), in: shape)
            .clipShape(shape)
    }

    /// Text action inside a detail card ("Show more"): link colour, label type,
    /// 44pt tall hit area.
    func timelineLinkAction() -> some View {
        buddyText(.label, weight: .semibold)
            .foregroundStyle(AgentBuddyTheme.link)
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
    }
}

/// Leading status icon of a detail summary: checkmark, spinner or exclamation
/// mark, so the state never depends on colour alone. With Reduce Motion the
/// spinner becomes a static dotted circle.
struct TimelineStatusGlyph: View {
    let status: ToolCallStatus
    /// Icon shown when the status is unknown (usually the tool kind).
    var fallbackSystemImage: String = "wrench.and.screwdriver"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        glyph
            .font(.system(size: 17, weight: .semibold))
            .foregroundStyle(status.themeColor)
            .frame(width: BuddySize.icon, height: BuddySize.icon)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityText)
            .accessibilityHidden(status == .unknown)
    }

    @ViewBuilder
    private var glyph: some View {
        switch status {
        case .inProgress:
            if reduceMotion {
                Image(systemName: "circle.dotted")
            } else {
                ProgressView()
                    .controlSize(.small)
                    .tint(status.themeColor)
            }
        case .completed:
            Image(systemName: "checkmark")
        case .failed:
            Image(systemName: "exclamationmark.circle")
        case .unknown:
            Image(systemName: fallbackSystemImage)
        }
    }

    private var accessibilityText: Text {
        switch status {
        case .completed: return Text("Completed")
        case .inProgress: return Text("Running")
        case .failed: return Text("Failed")
        case .unknown: return Text(verbatim: "")
        }
    }
}

/// Decorative disclosure chevron for collapsible rows.
struct TimelineDisclosureChevron: View {
    let expanded: Bool

    var body: some View {
        Image(systemName: expanded ? "chevron.up" : "chevron.down")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .accessibilityHidden(true)
    }
}

/// Duration next to a summary ("1.2s"). The status itself is carried by the
/// leading glyph, so this stays neutral.
struct TimelineDurationText: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .buddyText(.caption)
            .monospacedDigit()
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .lineLimit(1)
            .fixedSize()
    }
}

/// Small caption heading inside expanded detail content ("Arguments", "Output").
struct TimelineSectionLabel: View {
    let text: Text

    init(verbatim text: String) {
        self.text = Text(verbatim: text)
    }

    init(_ key: LocalizedStringKey) {
        self.text = Text(key)
    }

    var body: some View {
        text
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
    }
}
