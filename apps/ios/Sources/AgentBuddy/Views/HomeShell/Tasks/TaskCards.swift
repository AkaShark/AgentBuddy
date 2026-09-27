import SwiftUI

// MARK: - Active task card

/// Card for a task that is running, stopping or waiting on the user. Running
/// tasks use the brand surface; tasks that need a decision use the warning
/// surface so they read as "your turn" at a glance.
struct ActiveTaskCard: View {
    let item: HomeTaskItem
    let isOpening: Bool
    var showsDetail = false
    let handlers: TaskActionHandlers
    let onOpen: () -> Void

    private var tone: BuddySurfaceTone {
        item.state.needsAttention ? .warning : .brand
    }

    private var primaryText: Color {
        item.state.needsAttention ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.onBrand
    }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                HStack(alignment: .center, spacing: BuddySpacing.xxs) {
                    BuddyStatusLabel(state: item.state, tint: item.state.needsAttention ? nil : primaryText)
                    Spacer(minLength: BuddySpacing.xs)
                    if isOpening {
                        ProgressView().controlSize(.small).tint(primaryText)
                    } else {
                        Text(verbatim: HomeTaskPresentation.relativeTime(item.session.updatedAt))
                            .buddyText(.caption, weight: .medium)
                            .foregroundStyle(primaryText.opacity(0.8))
                    }
                    TaskActionsMenuButton(item: item, handlers: handlers, tint: primaryText.opacity(0.8))
                        .padding(.trailing, -BuddySpacing.sm)
                }
                .padding(.vertical, -BuddySpacing.xs)

                Text(verbatim: item.title)
                    .buddyText(.title)
                    .foregroundStyle(primaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                if let step = item.latestStep {
                    Text(verbatim: step)
                        .buddyText(.body)
                        .foregroundStyle(primaryText.opacity(0.85))
                        .lineLimit(showsDetail ? 4 : 2)
                        .multilineTextAlignment(.leading)
                }

                chips

                if item.activitySummary != nil || item.pendingApprovalCount > 0 {
                    Rectangle()
                        .fill(primaryText.opacity(0.14))
                        .frame(height: 1)
                    footer
                }
            }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(tone, radius: BuddyRadius.card, padding: BuddySpacing.lg)
        .contentShape(RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous))
        .onTapGesture(perform: onOpen)
        .contextMenu { TaskActionsMenuContent(item: item, handlers: handlers) }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(.default) { onOpen() }
    }

    private var chips: some View {
        HStack(spacing: BuddySpacing.xs) {
            if let project = item.projectName {
                BuddyChip(project, tone: item.state.needsAttention ? .outline : .onBrand)
            }
            BuddyChip(item.partnerName, tone: item.state.needsAttention ? .outline : .onBrand)
        }
    }

    @ViewBuilder
    private var footer: some View {
        HStack(spacing: BuddySpacing.xs) {
            if item.pendingApprovalCount > 0 {
                Image(systemName: "checkmark.shield")
                    .accessibilityHidden(true)
                Text("\(item.pendingApprovalCount) waiting for your OK")
            } else if let summary = item.activitySummary {
                Image(systemName: "arrow.triangle.branch")
                    .accessibilityHidden(true)
                Text(verbatim: summary)
            }
            Spacer(minLength: BuddySpacing.xs)
            Image(systemName: "arrow.up.right")
                .accessibilityHidden(true)
        }
        .buddyText(.label)
        .foregroundStyle(item.state.needsAttention ? AgentBuddyTheme.warning : primaryText)
        .lineLimit(1)
    }
}

// MARK: - Task row

/// Compact row for tasks that are not running ("接着上次").
struct TaskRow: View {
    let item: HomeTaskItem
    let isOpening: Bool
    var showsDetail = false
    let handlers: TaskActionHandlers
    let onOpen: () -> Void

    var body: some View {
            BuddyListRow(
                title: Text(verbatim: item.title),
                subtitle: subtitle,
                tile: {
                    BuddyIconTile(
                        content: .symbol(item.state.systemImage),
                        fill: item.state == .completed || item.state == .idle ? AgentBuddyTheme.surface : item.state.fill,
                        foreground: item.state == .completed || item.state == .idle ? AgentBuddyTheme.textSecondary : item.state.foreground
                    )
                    .overlay {
                        RoundedRectangle(cornerRadius: BuddyRadius.tile, style: .continuous)
                            .strokeBorder(AgentBuddyTheme.border, lineWidth: item.state == .completed || item.state == .idle ? 1 : 0)
                    }
                },
                accessory: {
                    if isOpening {
                        ProgressView().controlSize(.small)
                            .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                    } else {
                        TaskActionsMenuButton(item: item, handlers: handlers)
                    }
                }
            )
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            .contextMenu { TaskActionsMenuContent(item: item, handlers: handlers) }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction(.default) { onOpen() }
            .accessibilityHint(Text(item.state.title))
    }

    private var subtitle: Text {
        var parts: [String] = []
        if let project = item.projectName { parts.append(project) }
        switch item.state {
        case .awaitingApproval: parts.append(String(localized: "Needs your OK"))
        case .awaitingInput: parts.append(String(localized: "Waiting for your reply"))
        case .running: parts.append(String(localized: "Running"))
        case .stopping: parts.append(String(localized: "Stopping…"))
        default: parts.append(String(localized: "Updated \(HomeTaskPresentation.relativeTime(item.session.updatedAt))"))
        }
        var text = parts.joined(separator: " · ")
        if showsDetail, let step = item.latestStep {
            text += "\n" + step
        }
        return Text(verbatim: text)
    }
}
