import SwiftUI
import UIKit

struct ConversationExplorationGroupRow: View {
    @Environment(\.textScale) private var textScale
    @ScaledMetric(relativeTo: .subheadline) private var labelLineHeight: CGFloat = BuddyTextStyle.label.lineHeight

    let id: String
    let items: [ConversationItem]
    let showsCollapsedPreview: Bool
    let displayMode: ConversationDetailDisplayMode

    @State private var expanded = false

    var body: some View {
        let entries = explorationEntries

        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggleExpanded) {
                HStack(spacing: BuddySpacing.sm) {
                    TimelineStatusGlyph(status: isActive ? .inProgress : .completed)
                    Text(verbatim: summaryText)
                        .buddyText(.label)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    TimelineDisclosureChevron(expanded: expanded)
                }
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    ForEach(entries) { entry in
                        HStack(alignment: .top, spacing: BuddySpacing.xs) {
                            Circle()
                                .fill(entry.isInProgress ? AgentBuddyTheme.warning : AgentBuddyTheme.textSecondary)
                                .frame(width: explorationBulletSize, height: explorationBulletSize)
                                .padding(.top, explorationBulletTopPadding)
                                .accessibilityHidden(true)
                            Text(verbatim: entry.label)
                                .buddyText(.label, weight: .regular)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
                .padding(.bottom, BuddySpacing.sm)
            } else if showsCollapsedPreview && !entries.isEmpty {
                ScrollViewReader { proxy in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                            ForEach(entries) { entry in
                                HStack(alignment: .top, spacing: BuddySpacing.xs) {
                                    Circle()
                                        .fill(entry.isInProgress ? AgentBuddyTheme.warning : AgentBuddyTheme.textSecondary)
                                        .frame(width: explorationBulletSize, height: explorationBulletSize)
                                        .padding(.top, explorationBulletTopPadding)
                                        .accessibilityHidden(true)
                                    Text(verbatim: displayedCollapsedLabel(for: entry))
                                        .buddyText(.label, weight: .regular)
                                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }

                            Color.clear
                                .frame(height: 1)
                                .id(bottomAnchorId)
                        }
                        .padding(.horizontal, BuddySpacing.sm)
                        .padding(.vertical, BuddySpacing.xs)
                    }
                    .frame(maxHeight: collapsedPreviewHeight)
                    .timelineCodeSurface()
                    .overlay(alignment: .top) {
                        LinearGradient(
                            colors: [TimelineCodeStyle.fill(nested: true), TimelineCodeStyle.fill(nested: true).opacity(0)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: BuddySpacing.md)
                        .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                        .allowsHitTesting(false)
                    }
                    .padding(.bottom, BuddySpacing.sm)
                    .onAppear {
                        scrollToBottom(proxy)
                    }
                    .onChange(of: collapsedPreviewScrollSignature) { _, _ in
                        scrollToBottom(proxy, animated: true)
                    }
                }
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .timelineDetailCard()
        .opacity(displayMode.rendersRows ? 1 : 0)
        .frame(height: displayMode.rendersRows ? nil : 0)
        .clipped()
        .onChange(of: showsCollapsedPreview) { _, newValue in
            guard !newValue else { return }
            expanded = false
        }
    }

    private var summaryText: String {
        explorationSummaryText(isActive: isActive)
    }

    private var explorationBulletSize: CGFloat {
        6 * textScale
    }

    private var explorationBulletTopPadding: CGFloat {
        6 * textScale
    }

    private var collapsedPreviewHeight: CGFloat {
        (labelLineHeight * textScale * 3) + 18
    }

    private var bottomAnchorId: String {
        "\(id)-exploration-bottom"
    }

    private var collapsedPreviewScrollSignature: String {
        explorationEntries
            .map { "\($0.id)|\($0.label)|\($0.isInProgress)" }
            .joined(separator: "\n")
    }

    private var isActive: Bool {
        explorationEntries.contains(where: \.isInProgress)
    }

    private func toggleExpanded() {
        withAnimation(.easeInOut(duration: 0.2)) {
            expanded.toggle()
        }
    }

    private func displayedCollapsedLabel(for entry: ExplorationDisplayEntry) -> String {
        let collapsed = entry.label
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if collapsed.count <= 140 {
            return collapsed
        }
        let cutoff = collapsed.index(collapsed.startIndex, offsetBy: 140)
        return "\(collapsed[..<cutoff])..."
    }

    private var explorationEntries: [ExplorationDisplayEntry] {
        items.flatMap { item -> [ExplorationDisplayEntry] in
            guard case .commandExecution(let data) = item.content else { return [] }
            if data.actions.isEmpty {
                return [
                    ExplorationDisplayEntry(
                        id: "\(item.id)-command",
                        label: data.command,
                        isInProgress: data.isInProgress
                    )
                ]
            }
            return data.actions.enumerated().map { index, action in
                ExplorationDisplayEntry(
                    id: "\(item.id)-\(index)",
                    label: explorationLabel(for: action, fallback: data.command),
                    isInProgress: data.isInProgress
                )
            }
        }
    }

    private func explorationSummaryText(isActive: Bool) -> String {
        var readCount = 0
        var searchCount = 0
        var listingCount = 0
        var fallbackCount = 0

        for item in items {
            guard case .commandExecution(let data) = item.content else { continue }
            if data.actions.isEmpty {
                fallbackCount += 1
                continue
            }
            for action in data.actions {
                switch action.kind {
                case .read:
                    readCount += 1
                case .search:
                    searchCount += 1
                case .listFiles:
                    listingCount += 1
                case .unknown:
                    fallbackCount += 1
                }
            }
        }

        var parts: [String] = []
        if readCount > 0 {
            parts.append(readCount == 1 ? String(localized: "1 file") : String(localized: "\(readCount) files"))
        }
        if searchCount > 0 {
            parts.append(searchCount == 1 ? String(localized: "1 search") : String(localized: "\(searchCount) searches"))
        }
        if listingCount > 0 {
            parts.append(listingCount == 1 ? String(localized: "1 listing") : String(localized: "\(listingCount) listings"))
        }
        if fallbackCount > 0 {
            parts.append(fallbackCount == 1 ? String(localized: "1 step") : String(localized: "\(fallbackCount) steps"))
        }
        if parts.isEmpty {
            let count = explorationEntries.count
            parts.append(count == 1 ? String(localized: "1 step") : String(localized: "\(count) steps"))
        }
        let joined = parts.joined(separator: " · ")
        return isActive ? String(localized: "Exploring \(joined)") : String(localized: "Explored \(joined)")
    }

    private func explorationLabel(for action: ConversationCommandAction, fallback: String) -> String {
        let suffix = explorationCommandSuffix(for: action)
        switch action.kind {
        case .read:
            return action.path.map { String(localized: "Read \(workspaceTitle(for: $0))") + suffix } ?? fallback
        case .search:
            if let query = action.query, let path = action.path {
                return String(localized: "Searched for \(query) in \(workspaceTitle(for: path))") + suffix
            }
            if let query = action.query {
                return String(localized: "Searched for \(query)") + suffix
            }
            return fallback
        case .listFiles:
            return action.path.map { String(localized: "Listed files in \(workspaceTitle(for: $0))") + suffix } ?? fallback
        case .unknown:
            return fallback
        }
    }

    private func explorationCommandSuffix(for action: ConversationCommandAction) -> String {
        let command = action.command.trimmingCharacters(in: .whitespacesAndNewlines)
        guard command.hasSuffix(")"),
              let start = command.range(of: " (", options: .backwards)?.lowerBound else {
            return ""
        }
        return String(command[start...])
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

private struct ExplorationDisplayEntry: Identifiable {
    let id: String
    let label: String
    let isInProgress: Bool
}
