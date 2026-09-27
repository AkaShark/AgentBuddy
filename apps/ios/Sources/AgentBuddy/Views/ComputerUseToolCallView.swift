import SwiftUI
import UIKit

struct ComputerUseToolCallView: View {
    let data: ConversationMcpToolCallData
    let view: ComputerUseView
    private let externalExpanded: Bool?
    private let onExpandedChange: ((Bool) -> Void)?
    @State private var expanded: Bool
    @State private var a11yExpanded = false
    @State private var errorExpanded = false
    private let maxVisibleTextCharacters = 2_000

    init(
        data: ConversationMcpToolCallData,
        view: ComputerUseView,
        externalExpanded: Bool? = nil,
        onExpandedChange: ((Bool) -> Void)? = nil
    ) {
        self.data = data
        self.view = view
        self.externalExpanded = externalExpanded
        self.onExpandedChange = onExpandedChange
        _expanded = State(initialValue: externalExpanded ?? (data.status == .failed))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            if resolvedExpanded {
                VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                    if let screenshot = view.screenshotPng {
                        screenshotPreview(screenshot)
                    }
                    if let error = data.errorMessage, !error.isEmpty {
                        errorBlock(error)
                    }
                    if let text = view.accessibilityText, !text.isEmpty {
                        accessibilityBlock(text)
                    }
                }
                .padding(.top, BuddySpacing.xxs)
                .padding(.bottom, BuddySpacing.sm)
                .transition(.sectionReveal)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .timelineDetailCard()
        .animation(.spring(duration: 0.32, bounce: 0.12), value: resolvedExpanded)
        .onChange(of: data.status) { _, newStatus in
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
    }

    private var header: some View {
        HStack(spacing: BuddySpacing.sm) {
            TimelineStatusGlyph(status: data.status.toolCallStatus, fallbackSystemImage: toolIcon)

            Text(view.summary)
                .buddyText(.label)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: BuddySpacing.xs)

            if let duration = formatDuration(data.durationMs), !duration.isEmpty {
                TimelineDurationText(text: duration)
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
    }

    @ViewBuilder
    private func screenshotPreview(_ data: Data) -> some View {
        if let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                        .strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                )
        } else {
            placeholderTile("Screenshot unavailable", tone: AgentBuddyTheme.textSecondary)
        }
    }

    private func errorBlock(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
            Label("Error", systemImage: "exclamationmark.triangle")
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.danger)
            Text(errorExpanded ? message : limitedText(message))
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.danger)
                .fixedSize(horizontal: false, vertical: true)
            if shouldLimitText(message) {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        errorExpanded.toggle()
                    }
                } label: {
                    Text(errorExpanded ? "Show less" : "Show more")
                        .timelineLinkAction()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(errorExpanded ? "Show less error text" : "Show more error text")
            }
        }
    }

    @ViewBuilder
    private func accessibilityBlock(_ text: String) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
            HStack(spacing: BuddySpacing.xs) {
                TimelineSectionLabel("Accessibility tree")
                Spacer()
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        a11yExpanded.toggle()
                    }
                } label: {
                    Text(a11yExpanded ? "Show less" : "Show more")
                        .timelineLinkAction()
                }
                .buttonStyle(.plain)
            }

            Text(a11yExpanded ? text : collapsedPreview(text))
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(BuddySpacing.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .timelineCodeSurface()
        }
    }

    private func placeholderTile(_ message: LocalizedStringKey, tone: Color) -> some View {
        Label(message, systemImage: "photo")
            .buddyText(.label, weight: .regular)
            .foregroundStyle(tone)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, BuddySpacing.xl)
            .timelineCodeSurface()
    }

    private func collapsedPreview(_ text: String) -> String {
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false)
        if lines.count <= 6 { return limitedText(text) }
        let head = lines.prefix(6).joined(separator: "\n")
        return "\(limitedText(head))\n… (\(lines.count - 6) more lines)"
    }

    private func limitedText(_ text: String) -> String {
        guard shouldLimitText(text) else {
            return text
        }
        return String(text.prefix(maxVisibleTextCharacters))
    }

    private func shouldLimitText(_ text: String) -> Bool {
        text.count > maxVisibleTextCharacters
    }

    private var resolvedExpanded: Bool { expanded }

    private func setExpanded(_ newValue: Bool) {
        expanded = newValue
        onExpandedChange?(newValue)
    }

    private var toolIcon: String {
        switch view.tool {
        case .listApps: return "square.grid.2x2"
        case .getAppState: return "rectangle.on.rectangle"
        case .click: return "cursorarrow.click"
        case .performSecondaryAction: return "ellipsis.circle"
        case .scroll: return "arrow.up.arrow.down"
        case .drag: return "hand.draw"
        case .typeText: return "keyboard"
        case .pressKey: return "command"
        case .setValue: return "textformat.abc"
        case .unknown: return "wand.and.stars"
        }
    }

    private func formatDuration(_ ms: Int?) -> String? {
        guard let ms, ms >= 0 else { return nil }
        if ms < 1000 { return "\(ms)ms" }
        let seconds = Double(ms) / 1000.0
        if seconds < 60 { return String(format: "%.1fs", seconds) }
        let mins = Int(seconds / 60)
        let remain = Int(seconds) % 60
        return "\(mins)m \(remain)s"
    }
}
