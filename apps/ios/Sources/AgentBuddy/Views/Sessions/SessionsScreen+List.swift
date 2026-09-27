import SwiftUI

extension SessionsScreen {
    /// Collapsible workspace header in the `BuddySectionHeader` style:
    /// heading + task count, then host and path on a quieter second line.
    private func workspaceGroupHeader(_ group: WorkspaceSessionGroup) -> some View {
        let isCollapsed = collapsedWorkspaceGroupIDs.contains(group.id)

        return Button {
            if isCollapsed {
                collapsedWorkspaceGroupIDs.remove(group.id)
            } else {
                collapsedWorkspaceGroupIDs.insert(group.id)
            }
        } label: {
            HStack(alignment: .center, spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                        Text(verbatim: group.workspaceTitle)
                            .buddyText(.heading)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .lineLimit(1)
                        Text(verbatim: String(format: "%02d", group.threads.count))
                            .buddyText(.caption)
                            .monospacedDigit()
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .accessibilityHidden(true)
                    }

                    HStack(spacing: BuddySpacing.xxs) {
                        Text(verbatim: group.serverHost)
                            .buddyText(.label, weight: .regular)
                            .lineLimit(1)
                            .layoutPriority(1)
                        Text(verbatim: "·")
                            .buddyText(.label, weight: .regular)
                            .accessibilityHidden(true)
                        Text(verbatim: abbreviateHomePath(group.workspacePath))
                            .buddyText(.code)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                }

                Spacer(minLength: 0)

                Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isHeader)
        .accessibilityValue(Text(isCollapsed ? "Collapsed" : "Expanded"))
        .accessibilityHint(Text(isCollapsed ? "Expand" : "Collapse"))
    }

    /// The whole screen is one plain `List` so rows get native swipe actions
    /// and the page scrolls as a unit, like the Tasks tab. `header` supplies
    /// the rows above the task groups.
    func sessionList<Header: View>(
        derived: SessionsDerivedData,
        @ViewBuilder header: () -> Header
    ) -> some View {
        let headerRows = header()
        return ScrollViewReader { proxy in
            List {
                headerRows

                ForEach(derived.workspaceSections) { section in
                    if let title = section.title {
                        Text(LocalizedStringKey(title))
                            .buddyText(.caption, weight: .medium)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .accessibilityAddTraits(.isHeader)
                            .padding(.top, BuddySpacing.md)
                            .sessionsListRow()
                    }

                    ForEach(section.groups) { group in
                        workspaceGroupHeader(group)
                            .padding(.top, BuddySpacing.sm)
                            .sessionsListRow()

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
                                    .tint(AgentBuddyTheme.link)
                                }
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    // No `.destructive` role: that role makes the list
                                    // drop the row before the confirmation is answered.
                                    Button {
                                        archiveTargetKey = thread.key
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                    .tint(AgentBuddyTheme.danger)
                                }
                                .sessionsListRow(horizontal: BuddySpacing.sm, vertical: 1)
                            }
                        }
                    }
                }

                Color.clear
                    .frame(height: BuddySpacing.xl)
                    .sessionsListRow()
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .environment(\.defaultMinListRowHeight, 1)
            .scrollDismissesKeyboard(.interactively)
            // Task rows only exist once filtered threads do; until then keep
            // the pending scroll so it runs when they arrive.
            .onAppear {
                scrollToActiveSessionIfListed(derived: derived, proxy: proxy)
            }
            .onChange(of: pendingActiveSessionScroll) { _, _ in
                scrollToActiveSessionIfListed(derived: derived, proxy: proxy)
            }
            .onChange(of: derived.filteredThreadKeys) { _, _ in
                scrollToActiveSessionIfListed(derived: derived, proxy: proxy)
            }
            .onChange(of: collapsedWorkspaceGroupIDs) { _, _ in
                scrollToActiveSessionIfListed(derived: derived, proxy: proxy)
            }
            .onChange(of: collapsedSessionNodeKeys) { _, _ in
                scrollToActiveSessionIfListed(derived: derived, proxy: proxy)
            }
        }
    }

    @ViewBuilder
    func sessionRowContextMenu(_ thread: AppSessionSummary) -> some View {
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

    private func scrollToActiveSessionIfListed(derived: SessionsDerivedData, proxy: ScrollViewProxy) {
        guard !derived.filteredThreads.isEmpty else { return }
        scrollToActiveSessionIfNeeded(derived: derived, proxy: proxy)
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
