import SwiftUI
import UIKit

struct ConversationExplorationGroupRow: View {
    @Environment(\.textScale) private var textScale

    let id: String
    let items: [ConversationItem]
    let showsCollapsedPreview: Bool
    let displayMode: ConversationDetailDisplayMode

    @State private var expanded = false

    var body: some View {
        let entries = explorationEntries

        VStack(alignment: .leading, spacing: 6) {
            Button(action: toggleExpanded) {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .agentBuddyFont(size: 12, weight: .semibold)
                        .foregroundColor(isActive ? AgentBuddyTheme.warning : AgentBuddyTheme.textSecondary)
                    Text(verbatim: summaryText)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSystem)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .agentBuddyFont(size: 11, weight: .medium)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(entries) { entry in
                        HStack(alignment: .top, spacing: 8) {
                            Circle()
                                .fill(entry.isInProgress ? AgentBuddyTheme.warning : AgentBuddyTheme.textMuted)
                                .frame(width: explorationBulletSize, height: explorationBulletSize)
                                .padding(.top, explorationBulletTopPadding)
                            Text(verbatim: entry.label)
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            } else if showsCollapsedPreview && !entries.isEmpty {
                ScrollViewReader { proxy in
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(entries) { entry in
                                HStack(alignment: .top, spacing: 8) {
                                    Circle()
                                        .fill(entry.isInProgress ? AgentBuddyTheme.warning : AgentBuddyTheme.textMuted)
                                        .frame(width: explorationBulletSize, height: explorationBulletSize)
                                        .padding(.top, explorationBulletTopPadding)
                                    Text(verbatim: displayedCollapsedLabel(for: entry))
                                        .agentBuddyFont(.caption)
                                        .foregroundColor(AgentBuddyTheme.textSecondary)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }

                            Color.clear
                                .frame(height: 1)
                                .id(bottomAnchorId)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                    }
                    .frame(maxHeight: collapsedPreviewHeight)
                    .background(AgentBuddyTheme.surface.opacity(0.6))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    .overlay(alignment: .top) {
                        LinearGradient(
                            colors: [AgentBuddyTheme.surface.opacity(0.92), AgentBuddyTheme.surface.opacity(0)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 16)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .allowsHitTesting(false)
                    }
                    .onAppear {
                        scrollToBottom(proxy)
                    }
                    .onChange(of: collapsedPreviewScrollSignature) { _, _ in
                        scrollToBottom(proxy, animated: true)
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .opacity(displayMode.rendersRows ? 1 : 0)
        .frame(height: displayMode.rendersRows ? nil : 0)
        .clipped()
        .onChange(of: showsCollapsedPreview) { _, newValue in
            guard !newValue else { return }
            expanded = false
        }
    }

    private var summaryText: String {
        let prefix = isActive ? "Exploring" : "Explored"
        return explorationSummaryText(prefix: prefix)
    }

    private var explorationBulletSize: CGFloat {
        6 * textScale
    }

    private var explorationBulletTopPadding: CGFloat {
        5 * textScale
    }

    private var collapsedPreviewHeight: CGFloat {
        (AgentBuddyFont.uiMonoFont(size: 12 * textScale).lineHeight * 3) + 18
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

    private func explorationSummaryText(prefix: String) -> String {
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
            parts.append("\(readCount) \(readCount == 1 ? "file" : "files")")
        }
        if searchCount > 0 {
            parts.append("\(searchCount) \(searchCount == 1 ? "search" : "searches")")
        }
        if listingCount > 0 {
            parts.append("\(listingCount) \(listingCount == 1 ? "listing" : "listings")")
        }
        if fallbackCount > 0 {
            parts.append("\(fallbackCount) \(fallbackCount == 1 ? "step" : "steps")")
        }
        if parts.isEmpty {
            let count = explorationEntries.count
            return count == 1 ? "\(prefix) 1 exploration step" : "\(prefix) \(count) exploration steps"
        }
        return "\(prefix) \(parts.joined(separator: ", "))"
    }

    private func explorationLabel(for action: ConversationCommandAction, fallback: String) -> String {
        let suffix = explorationCommandSuffix(for: action)
        switch action.kind {
        case .read:
            return action.path.map { "Read \(workspaceTitle(for: $0))\(suffix)" } ?? fallback
        case .search:
            if let query = action.query, let path = action.path {
                return "Searched for \(query) in \(workspaceTitle(for: path))\(suffix)"
            }
            if let query = action.query {
                return "Searched for \(query)\(suffix)"
            }
            return fallback
        case .listFiles:
            return action.path.map { "Listed files in \(workspaceTitle(for: $0))\(suffix)" } ?? fallback
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
