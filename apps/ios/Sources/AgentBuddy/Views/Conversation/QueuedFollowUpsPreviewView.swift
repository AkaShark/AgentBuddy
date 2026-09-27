import SwiftUI

/// Messages queued behind the running turn. Each row can be turned into a
/// steer (sent into the current turn) or removed.
struct QueuedFollowUpsPreviewView: View {
    let previews: [AppQueuedFollowUpPreview]
    let onSteer: (AppQueuedFollowUpPreview) -> Void
    let onDelete: (AppQueuedFollowUpPreview) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.link)
                    .accessibilityHidden(true)
                Text("Queued Next")
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                Spacer()
                Text(verbatim: "\(previews.count)")
                    .buddyText(.caption, weight: .semibold)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .padding(.horizontal, BuddySpacing.xs)
                    .padding(.vertical, 2)
                    .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
                    .accessibilityLabel(Text("\(previews.count) queued"))
            }
            .padding(.top, BuddySpacing.xs)

            ForEach(previews, id: \.id) { preview in
                QueuedFollowUpRow(preview: preview, onSteer: onSteer, onDelete: onDelete)
            }
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.bottom, BuddySpacing.sm)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
    }
}

private struct QueuedFollowUpRow: View {
    let preview: AppQueuedFollowUpPreview
    let onSteer: (AppQueuedFollowUpPreview) -> Void
    let onDelete: (AppQueuedFollowUpPreview) -> Void

    var body: some View {
        let style = QueuedFollowUpPreviewStyle.forKind(preview.kind)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: BuddySpacing.xxs) {
                Label {
                    Text(style.title)
                } icon: {
                    Image(systemName: style.symbol)
                }
                .buddyText(.caption, weight: .semibold)
                .foregroundStyle(style.tint)
                .lineLimit(1)

                Spacer(minLength: 0)

                if preview.kind == .message || preview.kind == .pendingSteer {
                    steerButton
                }

                BuddyIconButton(
                    systemImage: "trash",
                    accessibilityLabel: "Remove queued message",
                    tone: .plain
                ) {
                    onDelete(preview)
                }
            }

            Text(verbatim: preview.text)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.trailing, BuddySpacing.sm)
                .padding(.top, -BuddySpacing.xxs)
                .padding(.bottom, BuddySpacing.sm)
        }
        .padding(.leading, BuddySpacing.sm)
        .background(style.background, in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
    }

    private var steerButton: some View {
        let isPending = preview.kind == .pendingSteer
        return Button(action: { onSteer(preview) }) {
            HStack(spacing: BuddySpacing.xxs) {
                Image(systemName: isPending ? "checkmark" : "arrow.turn.down.right")
                    .font(.system(size: 13, weight: .semibold))
                    .accessibilityHidden(true)
                Text(isPending ? "Steering" : "Steer")
                    .buddyText(.label, weight: .semibold)
            }
            .foregroundStyle(isPending ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.textPrimary)
            .padding(.horizontal, BuddySpacing.sm)
            .frame(minHeight: BuddySize.compactPill)
            .background(AgentBuddyTheme.surface, in: Capsule())
            .overlay(Capsule().strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1))
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .fixedSize()
        .disabled(isPending)
        .accessibilityHint(Text("Sends this message into the running task now"))
    }
}

private struct QueuedFollowUpPreviewStyle {
    let title: LocalizedStringKey
    let symbol: String
    let tint: Color
    let background: Color

    static func forKind(_ kind: AppQueuedFollowUpKind) -> Self {
        switch kind {
        case .message:
            return Self(
                title: "Queued message",
                symbol: "text.bubble.fill",
                tint: AgentBuddyTheme.textSecondary,
                background: AgentBuddyTheme.surfaceSoft
            )
        case .pendingSteer:
            return Self(
                title: "Steer queued",
                symbol: "arrowshape.turn.up.right.fill",
                tint: AgentBuddyTheme.link,
                background: AgentBuddyTheme.surfaceSoft
            )
        case .retryingSteer:
            return Self(
                title: "Retrying steer",
                symbol: "arrow.clockwise",
                tint: AgentBuddyTheme.warning,
                background: AgentBuddyTheme.warningSurface
            )
        }
    }
}
