import SwiftUI
import UIKit

struct ToolCallCardView: View {
    let model: ToolCallCardModel
    let serverId: String?
    private let externalExpanded: Bool?
    private let onExpandedChange: ((Bool) -> Void)?
    @State private var expanded: Bool
    @State var collapsedDiffSections: Set<String> = []
    /// Header row (icon + summary). A half-step smaller than body so tool
    /// calls read as secondary to assistant messages.
    private let summaryFontSize: CGFloat = 13
    /// Expanded content size — matches the bash/command output size
    /// (`ConversationCommandOutputViewport` renders at 12pt) so tool-call
    /// details, diffs, and command output share a typographic baseline.
    let contentFontSize: CGFloat = 12
    let terminalFontSize: CGFloat = 12
    let maxVisibleTextCharacters = 2_000
    @State var expandedLongTextIDs: Set<String> = []

    init(
        model: ToolCallCardModel,
        serverId: String? = nil,
        externalExpanded: Bool? = nil,
        onExpandedChange: ((Bool) -> Void)? = nil
    ) {
        self.model = model
        self.serverId = serverId
        self.externalExpanded = externalExpanded
        self.onExpandedChange = onExpandedChange
        _expanded = State(initialValue: externalExpanded ?? model.defaultExpanded)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: model.kind.iconName)
                    .agentBuddyFont(size: 12, weight: .semibold)
                    .foregroundColor(kindAccent)

                if let attributedSummary = model.attributedSummary {
                    Text(attributedSummary)
                        .agentBuddyFont(size: summaryFontSize)
                        .lineLimit(1)
                } else {
                    Text(model.summary)
                        .agentBuddyFont(size: summaryFontSize)
                        .foregroundColor(AgentBuddyTheme.textSystem)
                        .lineLimit(1)
                }

                Spacer()

                if let duration = model.duration, !duration.isEmpty {
                    Text(duration)
                        .agentBuddyFont(.caption2)
                        .foregroundColor(durationStatusColor)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(
                            Capsule(style: .continuous)
                                .fill(durationStatusColor.opacity(0.10))
                        )
                        .overlay(
                            Capsule(style: .continuous)
                                .stroke(durationStatusColor.opacity(0.22), lineWidth: 0.5)
                        )
                        .accessibilityLabel(durationAccessibilityLabel(duration))
                }

                Image(systemName: resolvedExpanded ? "chevron.up" : "chevron.down")
                    .agentBuddyFont(size: 11, weight: .medium)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.2)) {
                    setExpanded(!resolvedExpanded)
                }
            }

            if resolvedExpanded {
                VStack(alignment: .leading, spacing: 8) {
                    if let imageDescriptor {
                        ToolCallImagePreview(
                            descriptor: imageDescriptor,
                            serverId: serverId
                        )
                    }
                    ForEach(identifiedSections) { section in
                        sectionView(section)
                    }
                }
                .padding(.top, 6)
                .transition(.toolCallDetailReveal)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(AgentBuddyTheme.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(AgentBuddyTheme.border, lineWidth: 0.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .animation(.spring(duration: 0.32, bounce: 0.12), value: resolvedExpanded)
        .onChange(of: model.status) { _, newStatus in
            if newStatus == .failed {
                setExpanded(true)
            }
        }
        .onAppear {
            if let externalExpanded {
                expanded = externalExpanded
            }
        }
        .onChange(of: externalExpanded) { _, newValue in
            if let newValue, newValue != expanded {
                withAnimation(.spring(duration: 0.35, bounce: 0.15)) {
                    expanded = newValue
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func setExpanded(_ nextValue: Bool) {
        expanded = nextValue
        if let onExpandedChange {
            onExpandedChange(nextValue)
        }
    }

    private var resolvedExpanded: Bool { expanded }

    private var durationStatusColor: Color {
        switch model.status {
        case .completed:
            return AgentBuddyTheme.success
        case .inProgress:
            return AgentBuddyTheme.warning
        case .failed:
            return AgentBuddyTheme.danger
        case .unknown:
            return AgentBuddyTheme.textSecondary
        }
    }

    private func durationAccessibilityLabel(_ duration: String) -> String {
        switch model.status {
        case .completed:
            return "\(duration), completed"
        case .inProgress:
            return "\(duration), in progress"
        case .failed:
            return "\(duration), failed"
        case .unknown:
            return duration
        }
    }

    var kindAccent: Color {
        switch model.kind {
        case .commandExecution, .commandOutput:
            return AgentBuddyTheme.warning
        case .fileChange, .fileDiff, .webSearch:
            return AgentBuddyTheme.accent
        case .mcpToolCall, .widget:
            return AgentBuddyTheme.accentStrong
        case .mcpToolProgress, .imageView:
            return AgentBuddyTheme.warning
        case .collaboration:
            return AgentBuddyTheme.success
        }
    }
}

private extension AnyTransition {
    static var toolCallDetailReveal: AnyTransition { .sectionReveal }
}

#if DEBUG
#Preview("Tool Call Card") {
    ZStack {
        AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
        ToolCallCardView(model: AgentBuddyPreviewData.sampleToolCallModel)
            .padding(20)
    }
}
#endif
