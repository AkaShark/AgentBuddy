import SwiftUI

struct QueuedFollowUpsPreviewView: View {
    let previews: [AppQueuedFollowUpPreview]
    let onSteer: (AppQueuedFollowUpPreview) -> Void
    let onDelete: (AppQueuedFollowUpPreview) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AgentBuddyTheme.accent)
                Text("Queued Next")
                    .agentBuddyFont(.caption, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                Spacer()
                Text("\(previews.count)")
                    .agentBuddyFont(.caption2, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AgentBuddyTheme.surface.opacity(0.9))
                    .clipShape(Capsule())
            }

            ForEach(previews, id: \.id) { preview in
                let style = QueuedFollowUpPreviewStyle.forKind(preview.kind)

                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 6) {
                            Image(systemName: style.symbol)
                                .font(.system(size: 11, weight: .semibold))
                            Text(style.title)
                                .agentBuddyFont(.caption2, weight: .semibold)
                        }
                        .foregroundColor(style.tint)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(style.tint.opacity(0.14))
                        .clipShape(Capsule())

                        Text(preview.text)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .lineLimit(4)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if preview.kind == .message || preview.kind == .pendingSteer {
                        Button(action: { onSteer(preview) }) {
                            HStack(spacing: 6) {
                                if preview.kind == .pendingSteer {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12, weight: .semibold))
                                    Text("Steering")
                                        .agentBuddyFont(.caption, weight: .semibold)
                                } else {
                                    Image(systemName: "arrow.turn.down.right")
                                        .font(.system(size: 12, weight: .semibold))
                                    Text("Steer")
                                        .agentBuddyFont(.caption, weight: .semibold)
                                }
                            }
                            .foregroundColor(preview.kind == .pendingSteer ? AgentBuddyTheme.accent : AgentBuddyTheme.textPrimary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(AgentBuddyTheme.surface.opacity(0.96))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .disabled(preview.kind == .pendingSteer)
                    }

                    Button(action: { onDelete(preview) }) {
                        Image(systemName: "trash")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .frame(width: 30, height: 30)
                    }
                    .buttonStyle(.plain)
                }
                .padding(12)
                .background(style.background)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(style.border, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
        .padding(12)
        .background(AgentBuddyTheme.codeBackground.opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct QueuedFollowUpPreviewStyle {
    let title: String
    let symbol: String
    let tint: Color
    let background: Color
    let border: Color

    static func forKind(_ kind: AppQueuedFollowUpKind) -> Self {
        switch kind {
        case .message:
            let tint = AgentBuddyTheme.accent
            return Self(
                title: "Queued message",
                symbol: "text.bubble.fill",
                tint: tint,
                background: tint.opacity(0.08),
                border: tint.opacity(0.24)
            )
        case .pendingSteer:
            let tint = AgentBuddyTheme.accentStrong
            return Self(
                title: "Steer queued",
                symbol: "arrowshape.turn.up.right.fill",
                tint: tint,
                background: tint.opacity(0.10),
                border: tint.opacity(0.28)
            )
        case .retryingSteer:
            let tint = AgentBuddyTheme.warning
            return Self(
                title: "Retrying steer",
                symbol: "arrow.clockwise",
                tint: tint,
                background: tint.opacity(0.10),
                border: tint.opacity(0.28)
            )
        }
    }
}
