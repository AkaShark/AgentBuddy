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
        VStack(alignment: .leading, spacing: expanded ? BuddySpacing.xxs : 0) {
            Button(action: toggleExpanded) {
                HStack(spacing: BuddySpacing.xs) {
                    Image(systemName: "brain.head.profile")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                    Text("Thinking")
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                    if !expanded {
                        Text(verbatim: collapsedSummary)
                            .buddyText(.caption)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    Spacer(minLength: BuddySpacing.xs)
                    TimelineDisclosureChevron(expanded: expanded)
                }
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                Text(reasoningText)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, BuddySpacing.sm)
                    .overlay(alignment: .leading) {
                        Rectangle()
                            .fill(AgentBuddyTheme.border)
                            .frame(width: 2)
                            .accessibilityHidden(true)
                    }
                    .padding(.bottom, BuddySpacing.xs)
                    .transition(.sectionReveal)
            }
        }
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
        return itemCount == 1 ? String(localized: "Internal reasoning") : String(localized: "\(itemCount) reasoning notes")
    }

    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.2)) {
            expanded.toggle()
        }
    }
}
