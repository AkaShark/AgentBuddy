import SwiftUI

struct ConversationReasoningRow: View {
    let data: ConversationReasoningData
    let displayMode: ConversationDetailDisplayMode

    @State private var expanded: Bool

    init(data: ConversationReasoningData, displayMode: ConversationDetailDisplayMode) {
        self.data = data
        self.displayMode = displayMode
        _expanded = State(initialValue: displayMode.defaultExpanded())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: expanded ? 8 : 0) {
            Button(action: toggleExpanded) {
                HStack(spacing: 8) {
                    Image(systemName: "brain.head.profile")
                        .agentBuddyFont(size: 12, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                    Text("Thinking")
                        .agentBuddyFont(.caption, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                    if !expanded {
                        Text(collapsedSummary)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .agentBuddyFont(size: 11, weight: .medium)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }
            }
            .buttonStyle(.plain)

            if expanded {
                Text(reasoningText)
                    .agentBuddyFont(.footnote)
                    .italic()
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .transition(.sectionReveal)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .animation(.spring(duration: 0.32, bounce: 0.12), value: expanded)
        .onChange(of: displayMode) { _, newValue in
            expanded = newValue.defaultExpanded()
        }
    }

    private var reasoningText: String {
        (data.summary + data.content)
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: "\n\n")
    }

    private var collapsedSummary: String {
        let itemCount = (data.summary + data.content).filter {
            !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }.count
        return itemCount == 1 ? "Internal reasoning" : "\(itemCount) reasoning notes"
    }

    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.2)) {
            expanded.toggle()
        }
    }
}
