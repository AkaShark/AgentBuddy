import SwiftUI

// MARK: - Partner label environment

private struct ConversationPartnerLabelKey: EnvironmentKey {
    static let defaultValue: String? = nil
}

extension EnvironmentValues {
    /// Runtime shown next to AgentBuddy's name in a conversation ("Codex").
    var conversationPartnerLabel: String? {
        get { self[ConversationPartnerLabelKey.self] }
        set { self[ConversationPartnerLabelKey.self] = newValue }
    }
}

// MARK: - Header

/// "✦ 搭子 · Codex" line that opens each assistant reply, so a turn reads as
/// a conversation with a named partner instead of an anonymous log.
struct AssistantSpeakerHeader: View {
    @Environment(\.conversationPartnerLabel) private var partnerLabel

    var body: some View {
        HStack(spacing: BuddySpacing.xs) {
            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(AgentBuddyTheme.action)
                Image(systemName: "sparkles")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.onAction)
            }
            .frame(width: 26, height: 26)
            .accessibilityHidden(true)
            Text("AgentBuddy")
                .buddyText(.label, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            if let partnerLabel, !partnerLabel.isEmpty {
                Text(verbatim: "· \(partnerLabel)")
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}
