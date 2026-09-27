import SwiftUI

extension SessionsScreen {
    private func workspaceGroupHeader(_ group: WorkspaceSessionGroup) -> some View {
        let isCollapsed = collapsedWorkspaceGroupIDs.contains(group.id)

        return Button {
            if isCollapsed {
                collapsedWorkspaceGroupIDs.remove(group.id)
            } else {
                collapsedWorkspaceGroupIDs.insert(group.id)
            }
        } label: {
            HStack(alignment: .center, spacing: 8) {
                Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                    .agentBuddyFont(size: 10, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .frame(width: 12)

                Image(systemName: "folder")
                    .agentBuddyFont(size: 11, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.accent)

                VStack(alignment: .leading, spacing: 2) {
                    Text(group.workspaceTitle)
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)

                    Text(group.serverHost)
                        .agentBuddyFont(.caption2)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .lineLimit(1)

                    Text(abbreviateHomePath(group.workspacePath))
                        .agentBuddyFont(.caption2)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(AgentBuddyTheme.border.opacity(0.75))
                    .frame(height: 1)
            }
        }
        .buttonStyle(.plain)
    }

    func sessionList(derived: SessionsDerivedData) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 3) {
                    ForEach(derived.workspaceSections) { section in
                        if let title = section.title {
                            Text(title)
                                .agentBuddyFont(.caption2)
                                .foregroundColor(AgentBuddyTheme.textMuted)
                                .padding(.horizontal, 2)
                        }

                        ForEach(section.groups) { group in
                            workspaceGroupHeader(group)

                            if !collapsedWorkspaceGroupIDs.contains(group.id) {
                                ForEach(visibleSessionRows(for: group)) { row in
                                    let thread = row.thread
                                    let isCollapsed = collapsedSessionNodeKeys.contains(thread.key)

                                    sessionRow(
                                        thread,
                                        isActive: thread.key == activeThreadKey,
                                        derived: derived,
                                        ephemeralState: ephemeralStateByThreadKey[thread.key],
                                        depth: row.depth,
                                        hasChildren: row.hasChildren,
                                        isCollapsed: isCollapsed,
                                        onToggleNode: {
                                            guard row.hasChildren else { return }
                                            if isCollapsed {
                                                collapsedSessionNodeKeys.remove(thread.key)
                                            } else {
                                                collapsedSessionNodeKeys.insert(thread.key)
                                            }
                                        },
                                        onSelectSession: {
                                            guard resumingKey == nil else { return }
                                            Task { await resumeSession(thread) }
                                        }
                                    )
                                    .id(thread.key)
                                    .contextMenu {
                                        sessionRowContextMenu(thread)
                                    }
                                    .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                        Button {
                                            Task { await forkThread(thread) }
                                        } label: {
                                            Label("Fork", systemImage: "arrow.triangle.branch")
                                        }
                                        .tint(AgentBuddyTheme.accent)
                                    }
                                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                        Button(role: .destructive) {
                                            archiveTargetKey = thread.key
                                        } label: {
                                            Label("Delete", systemImage: "trash")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.leading, 4)
                .padding(.trailing, 8)
                .padding(.vertical, 4)
            }
            .onAppear {
                scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
            }
            .onChange(of: pendingActiveSessionScroll) { _, _ in
                scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
            }
            .onChange(of: derived.filteredThreadKeys) { _, _ in
                scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
            }
            .onChange(of: collapsedWorkspaceGroupIDs) { _, _ in
                scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
            }
            .onChange(of: collapsedSessionNodeKeys) { _, _ in
                scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
            }
        }
    }

    @ViewBuilder
    private func sessionRowContextMenu(_ thread: AppSessionSummary) -> some View {
        Button {
            renamingThreadKey = thread.key
            renameCurrentTitle = thread.sessionTitle
            renameDraft = ""
        } label: {
            Label("Rename", systemImage: "pencil")
        }

        Button {
            Task { await forkThread(thread) }
        } label: {
            Label("Fork", systemImage: "arrow.triangle.branch")
        }

        Button(role: .destructive) {
            archiveTargetKey = thread.key
        } label: {
            Label("Delete", systemImage: "trash")
        }
    }

    private func visibleSessionRows(for group: WorkspaceSessionGroup) -> [SessionTreeRow] {
        var rows: [SessionTreeRow] = []

        func append(nodes: [SessionTreeNode], depth: Int) {
            for node in nodes {
                let hasChildren = !node.children.isEmpty
                rows.append(
                    SessionTreeRow(
                        thread: node.thread,
                        depth: depth,
                        hasChildren: hasChildren
                    )
                )

                if hasChildren && !collapsedSessionNodeKeys.contains(node.thread.key) {
                    append(nodes: node.children, depth: depth + 1)
                }
            }
        }

        append(nodes: group.treeRoots, depth: 0)
        return rows
    }

    func scheduleActiveSessionScrollIfNeeded() {
        guard activeThreadKey != nil else { return }
        pendingActiveSessionScroll = true
    }

    private func scrollToActiveSessionIfNeeded(derived: SessionsDerivedData, proxy: ScrollViewProxy) {
        guard pendingActiveSessionScroll, let activeKey = activeThreadKey else { return }

        guard let activeThread = derived.filteredThreads.first(where: { $0.key == activeKey }) else {
            pendingActiveSessionScroll = false
            return
        }

        let activeWorkspaceGroupID = derived.workspaceGroupIDByThreadKey[activeThread.key] ?? workspaceGroupID(for: activeThread)
        if collapsedWorkspaceGroupIDs.contains(activeWorkspaceGroupID) {
            collapsedWorkspaceGroupIDs.remove(activeWorkspaceGroupID)
            return
        }

        if let collapsedAncestor = ancestorThreadKeys(for: activeKey, derived: derived)
            .reversed()
            .first(where: { collapsedSessionNodeKeys.contains($0) }) {
            collapsedSessionNodeKeys.remove(collapsedAncestor)
            return
        }

        pendingActiveSessionScroll = false
        withAnimation(.easeInOut(duration: 0.2)) {
            proxy.scrollTo(activeKey, anchor: .center)
        }
    }

    private func ancestorThreadKeys(for key: ThreadKey, derived: SessionsDerivedData) -> [ThreadKey] {
        var ancestors: [ThreadKey] = []
        var visited: Set<ThreadKey> = []
        var cursor: AppSessionSummary? = derived.parentByKey[key]

        while let thread = cursor, !visited.contains(thread.key) {
            ancestors.append(thread.key)
            visited.insert(thread.key)
            cursor = derived.parentByKey[thread.key]
        }

        return ancestors
    }
}

private struct SessionTreeRow: Identifiable {
    let thread: AppSessionSummary
    let depth: Int
    let hasChildren: Bool

    var id: ThreadKey { thread.key }
}
