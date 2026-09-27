import SwiftUI

/// Selectable capsule chip used by the task filters (All tasks screen and the
/// home task search). Unselected it matches `.buddyContextChip()`; selected it
/// uses the action fill, onAction content and a checkmark, so the state never
/// depends on colour alone. The hit area is at least 44×44pt.
struct SessionsFilterChip: View {
    let title: Text
    var systemImage: String?
    /// Partner icon shown instead of `systemImage` while unselected.
    var agentKind: AgentRuntimeKind?
    let isSelected: Bool
    /// Adds a trailing chevron for chips that open a menu.
    var opensMenu = false

    var body: some View {
        Group {
            if isSelected {
                content
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.onAction)
                    .lineLimit(1)
                    .padding(.horizontal, BuddySpacing.sm)
                    .frame(minHeight: BuddySize.compactPill)
                    .background(AgentBuddyTheme.action, in: Capsule())
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
            } else {
                content.buddyContextChip()
            }
        }
        .frame(minWidth: BuddySize.minHitTarget)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var content: some View {
        HStack(spacing: 6) {
            if isSelected {
                Image(systemName: "checkmark")
                    .font(.system(size: 12, weight: .bold))
                    .accessibilityHidden(true)
            } else if let agentKind {
                AgentIconView(kind: agentKind, size: 16)
                    .accessibilityHidden(true)
            } else if let systemImage {
                Image(systemName: systemImage)
                    .imageScale(.small)
                    .accessibilityHidden(true)
            }
            title
            if opensMenu {
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .accessibilityHidden(true)
            }
        }
    }
}

/// Small non-interactive tag inside a task row ("Fork", sub-agent name,
/// "Current task"). Text (plus an optional icon), never colour alone.
struct SessionsRowTag: View {
    let title: Text
    var systemImage: String?

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.system(size: 11, weight: .semibold))
                    .accessibilityHidden(true)
            }
            title
                .lineLimit(1)
        }
        .buddyText(.caption, weight: .medium)
        .foregroundStyle(AgentBuddyTheme.textSecondary)
        .padding(.horizontal, BuddySpacing.xs)
        .padding(.vertical, 2)
        .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
    }
}

extension View {
    /// Plain list row on the page background with explicit insets and no
    /// separator. Task rows use a 12pt inset plus 12pt inner padding so their
    /// text lines up with the 24pt page gutter.
    func sessionsListRow(horizontal: CGFloat = BuddySpacing.xl, vertical: CGFloat = 0) -> some View {
        listRowInsets(EdgeInsets(top: vertical, leading: horizontal, bottom: vertical, trailing: horizontal))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
    }
}
