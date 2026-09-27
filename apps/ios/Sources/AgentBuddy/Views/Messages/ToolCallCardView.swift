import SwiftUI
import UIKit

struct ToolCallCardView: View {
    let model: ToolCallCardModel
    let serverId: String?
    private let externalExpanded: Bool?
    private let onExpandedChange: ((Bool) -> Void)?
    @State private var expanded: Bool
    @State var collapsedDiffSections: Set<String> = []
    /// Expanded content size — the Mint code size (14) shared with command
    /// output, so tool-call details, diffs, and output share a baseline.
    let contentFontSize: CGFloat = BuddyTextStyle.code.size
    let terminalFontSize: CGFloat = BuddyTextStyle.code.size
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
            HStack(spacing: BuddySpacing.sm) {
                TimelineStatusGlyph(status: model.status, fallbackSystemImage: model.kind.iconName)

                if let attributedSummary = model.attributedSummary {
                    Text(attributedSummary)
                        .buddyText(.label)
                        .lineLimit(1)
                } else {
                    Text(model.summary)
                        .buddyText(.label)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)
                }

                Spacer(minLength: BuddySpacing.xs)

                if let duration = model.duration, !duration.isEmpty {
                    TimelineDurationText(text: duration)
                        .accessibilityLabel(durationAccessibilityLabel(duration))
                }

                TimelineDisclosureChevron(expanded: resolvedExpanded)
            }
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
            .accessibilityAddTraits(.isButton)
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.2)) {
                    setExpanded(!resolvedExpanded)
                }
            }

            if resolvedExpanded {
                VStack(alignment: .leading, spacing: BuddySpacing.sm) {
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
                .padding(.top, BuddySpacing.xxs)
                .padding(.bottom, BuddySpacing.sm)
                .transition(.toolCallDetailReveal)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .timelineDetailCard()
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
            return AgentBuddyTheme.textSecondary
        case .fileChange, .fileDiff, .webSearch, .mcpToolCall, .widget:
            return AgentBuddyTheme.link
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
        AgentBuddyTheme.background.ignoresSafeArea()
        ToolCallCardView(model: AgentBuddyPreviewData.sampleToolCallModel)
            .padding(20)
    }
}
#endif
