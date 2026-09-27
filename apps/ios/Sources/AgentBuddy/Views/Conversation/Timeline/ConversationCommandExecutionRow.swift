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
        VStack(alignment: .leading, spacing: expanded ? 8 : 0) {
            shellHeader
            if expanded {
                ConversationCommandOutputViewport(
                    output: renderedOutput,
                    status: data.status.toolCallStatus,
                    durationText: nil
                )
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
        .animation(.spring(duration: 0.35, bounce: 0.15), value: expanded)
        .onChange(of: isPreferredExpanded) { _, newValue in
            expanded = newValue
        }
        .onChange(of: displayMode) { _, newValue in
            expanded = newValue == .expanded || data.isInProgress || data.status == .failed
        }
    }

    private var shellHeader: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text("$")
                .agentBuddyMonoFont(size: 12, weight: .semibold)
                .foregroundColor(AgentBuddyTheme.warning)

            Text(expanded ? displayedCommand : collapsedCommand)
                .agentBuddyMonoFont(size: 12)
                .foregroundColor(AgentBuddyTheme.textSystem)
                .textSelection(.enabled)
                .lineLimit(expanded ? nil : 1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)

            if let durationText = timelineFormatDuration(data.durationMs), !durationText.isEmpty {
                Text(durationText)
                    .agentBuddyFont(.caption2)
                    .foregroundColor(statusColor)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(
                        Capsule(style: .continuous)
                            .fill(statusColor.opacity(0.10))
                    )
                    .overlay(
                        Capsule(style: .continuous)
                            .stroke(statusColor.opacity(0.22), lineWidth: 0.5)
                    )
                    .accessibilityLabel(durationAccessibilityLabel(durationText))
            }

            Image(systemName: expanded ? "chevron.up" : "chevron.down")
                .agentBuddyFont(size: 11, weight: .medium)
                .foregroundColor(AgentBuddyTheme.textMuted)
        }
        .contentShape(Rectangle())
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

    private var statusColor: Color { data.status.toolCallStatus.themeColor }

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
    @State private var expandedLongOutput = false

    private let bottomAnchorId = "command-output-bottom"
    private let maxVisibleTextCharacters = 2_000

    private var lineFontSize: CGFloat {
        11 * textScale
    }

    private var maxViewportHeight: CGFloat {
        (AgentBuddyFont.uiMonoFont(size: lineFontSize).lineHeight * 3) + 16
    }

    private var viewportHeight: CGFloat {
        let lh = AgentBuddyFont.uiMonoFont(size: lineFontSize).lineHeight
        let lines = max(1, visibleOutput.split(separator: "\n", omittingEmptySubsequences: false).count)
        let natural = (lh * CGFloat(min(lines, 3))) + 16
        return min(natural, maxViewportHeight)
    }

    var body: some View {
        ScrollViewReader { proxy in
            VStack(alignment: .leading, spacing: 6) {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(verbatim: visibleOutput)
                            .agentBuddyMonoFont(size: 12)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Color.clear
                            .frame(height: 1)
                            .id(bottomAnchorId)
                    }
                    .padding(.horizontal, 10)
                    .padding(.top, 8)
                    .padding(.bottom, 12)
                }
                .frame(height: viewportHeight)
                .background(AgentBuddyTheme.codeBackground.opacity(0.78))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(alignment: .top) {
                    LinearGradient(
                        colors: [AgentBuddyTheme.codeBackground.opacity(0.96), AgentBuddyTheme.codeBackground.opacity(0)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 18)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .allowsHitTesting(false)
                }
                .overlay(alignment: .bottomTrailing) {
                    if let durationText, !durationText.isEmpty {
                        Text(durationText)
                            .foregroundColor(statusColor)
                            .accessibilityLabel(durationAccessibilityLabel(durationText))
                            .agentBuddyFont(.caption2)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(alignment: .bottom) {
                                LinearGradient(
                                    colors: [.clear, AgentBuddyTheme.codeBackground.opacity(0.94)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                            }
                        }
                    }
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(AgentBuddyTheme.border.opacity(0.35), lineWidth: 1)
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
                            .agentBuddyFont(.caption2, weight: .semibold)
                            .foregroundColor(AgentBuddyTheme.accent)
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

    private var statusColor: Color { status.themeColor }

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
