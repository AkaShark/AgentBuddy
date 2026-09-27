import SwiftUI
import UIKit

struct ConversationCommandExecutionRow: View {
    let data: ConversationCommandExecutionData
    let isPreferredExpanded: Bool
    let displayMode: ConversationDetailDisplayMode

    @State private var expanded: Bool

    init(
        data: ConversationCommandExecutionData,
        isPreferredExpanded: Bool,
        displayMode: ConversationDetailDisplayMode
    ) {
        self.data = data
        self.isPreferredExpanded = isPreferredExpanded
        self.displayMode = displayMode
        _expanded = State(initialValue: isPreferredExpanded)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: expanded ? BuddySpacing.xxs : 0) {
            shellHeader
            if expanded {
                ConversationCommandOutputViewport(
                    output: renderedOutput,
                    status: data.status.toolCallStatus,
                    durationText: nil
                )
                .padding(.bottom, BuddySpacing.sm)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .timelineDetailCard()
        .animation(.spring(duration: 0.35, bounce: 0.15), value: expanded)
        .onChange(of: isPreferredExpanded) { _, newValue in
            expanded = newValue
        }
        .onChange(of: displayMode) { _, newValue in
            expanded = newValue == .expanded || data.isInProgress || data.status == .failed
        }
    }

    private var shellHeader: some View {
        HStack(alignment: .center, spacing: BuddySpacing.sm) {
            TimelineStatusGlyph(status: data.status.toolCallStatus, fallbackSystemImage: "terminal")

            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                Text(verbatim: "$")
                    .buddyText(.code, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)

                Text(expanded ? displayedCommand : collapsedCommand)
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .textSelection(.enabled)
                    .lineLimit(expanded ? nil : 1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, expanded ? BuddySpacing.xs : 0)

            if let durationText = timelineFormatDuration(data.durationMs), !durationText.isEmpty {
                TimelineDurationText(text: durationText)
                    .accessibilityLabel(durationAccessibilityLabel(durationText))
            }

            TimelineDisclosureChevron(expanded: expanded)
        }
        .frame(minHeight: BuddySize.minHitTarget)
        .contentShape(Rectangle())
        .accessibilityAddTraits(.isButton)
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                expanded.toggle()
            }
        }
    }

    private var renderedOutput: String {
        let trimmed = data.output?.trimmingCharacters(in: .newlines) ?? ""
        if !trimmed.isEmpty {
            return trimmed
        }
        return data.isInProgress ? "Waiting for output…" : "No output"
    }

    private var displayedCommand: String {
        let trimmed = data.command.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "command" : trimmed
    }

    private var collapsedCommand: String {
        let collapsed = displayedCommand
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return collapsed.isEmpty ? "command" : collapsed
    }

    private func durationAccessibilityLabel(_ duration: String) -> String {
        switch data.status.toolCallStatus {
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
}

private struct ConversationCommandOutputViewport: View {
    let output: String
    let status: ToolCallStatus
    let durationText: String?
    @Environment(\.textScale) private var textScale
    @ScaledMetric(relativeTo: .callout) private var codeLineHeight: CGFloat = BuddyTextStyle.code.lineHeight
    @State private var expandedLongOutput = false

    private let bottomAnchorId = "command-output-bottom"
    private let maxVisibleTextCharacters = 2_000
    /// Top + bottom text padding inside the viewport.
    private let verticalTextPadding: CGFloat = BuddySpacing.xs * 2

    private var lineHeight: CGFloat {
        codeLineHeight * textScale
    }

    private var maxViewportHeight: CGFloat {
        (lineHeight * 3) + verticalTextPadding
    }

    private var viewportHeight: CGFloat {
        let lines = max(1, visibleOutput.split(separator: "\n", omittingEmptySubsequences: false).count)
        let natural = (lineHeight * CGFloat(min(lines, 3))) + verticalTextPadding
        return min(natural, maxViewportHeight)
    }

    private var codeFill: Color { TimelineCodeStyle.fill(nested: true) }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(alignment: .leading, spacing: 0) {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(verbatim: visibleOutput)
                            .buddyText(.code)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Color.clear
                            .frame(height: 1)
                            .id(bottomAnchorId)
                    }
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.vertical, BuddySpacing.xs)
                }
                .frame(height: viewportHeight)
                .timelineCodeSurface()
                .overlay(alignment: .top) {
                    LinearGradient(
                        colors: [codeFill, codeFill.opacity(0)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: BuddySpacing.md)
                    .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                    .allowsHitTesting(false)
                }
                .overlay(alignment: .bottomTrailing) {
                    if let durationText, !durationText.isEmpty {
                        TimelineDurationText(text: durationText)
                            .accessibilityLabel(durationAccessibilityLabel(durationText))
                            .padding(.horizontal, BuddySpacing.sm)
                            .padding(.vertical, BuddySpacing.xs)
                            .background(alignment: .bottom) {
                                LinearGradient(
                                    colors: [codeFill.opacity(0), codeFill],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            }
                        }
                    }
                .onAppear {
                    scrollToBottom(proxy)
                }
                .onChange(of: output) { _, _ in
                    expandedLongOutput = false
                    scrollToBottom(proxy, animated: true)
                }
                .onChange(of: expandedLongOutput) { _, _ in
                    scrollToBottom(proxy, animated: true)
                }

                if shouldLimitOutput {
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            expandedLongOutput.toggle()
                        }
                    } label: {
                        Text(expandedLongOutput ? "Show less" : "Show more")
                            .timelineLinkAction()
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(expandedLongOutput ? "Show less command output" : "Show more command output")
                }
            }
        }
    }

    private var visibleOutput: String {
        guard shouldLimitOutput, !expandedLongOutput else {
            return output
        }
        if usesTailPreview {
            return String(output.suffix(maxVisibleTextCharacters))
        }
        return String(output.prefix(maxVisibleTextCharacters))
    }

    private var shouldLimitOutput: Bool {
        output.count > maxVisibleTextCharacters
    }

    private var usesTailPreview: Bool {
        switch status {
        case .inProgress:
            return true
        case .completed, .failed, .unknown:
            return false
        }
    }

    private func durationAccessibilityLabel(_ duration: String) -> String {
        switch status {
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

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool = false) {
        DispatchQueue.main.async {
            if animated {
                withAnimation(.easeOut(duration: 0.16)) {
                    proxy.scrollTo(bottomAnchorId, anchor: .bottom)
                }
            } else {
                proxy.scrollTo(bottomAnchorId, anchor: .bottom)
            }
        }
    }
}
