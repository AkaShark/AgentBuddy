import SwiftUI

extension TasksHomeView {
    // MARK: - Hero

    /// Full brand copy only on the first-run empty state; once tasks exist the
    /// slogan shrinks to a one-line summary so the task that needs attention is
    /// visible without scrolling.
    @ViewBuilder
    func hero(running: Int, waiting: Int, hasTasks: Bool) -> some View {
        if hasTasks {
            Text(summaryText(running: running, waiting: waiting))
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .padding(.top, BuddySpacing.xs)
        } else {
            BuddyPageHeader(
                eyebrow: "Your ideas, in motion",
                title: "Let ideas take a step forward.",
                subtitle: Text("A coding partner in your pocket.")
            )
            .padding(.top, BuddySpacing.md)
        }
    }

    private func summaryText(running: Int, waiting: Int) -> LocalizedStringKey {
        switch (running, waiting) {
        case (0, 0): return "Nothing running right now."
        case (_, 0): return "\(running) running."
        case (0, _): return "\(waiting) waiting for your OK."
        default: return "\(running) running, \(waiting) waiting for your OK."
        }
    }

    // MARK: - Sections

    @ViewBuilder
    func attentionSection(_ items: [HomeTaskItem], showsSearch: Bool) -> some View {
        if !items.isEmpty {
            BuddySectionHeader("Needs you", count: items.count) {
                if showsSearch { searchButton }
            }
            .padding(.top, BuddySpacing.md)
            .buddyListRow(vertical: 0)
            ForEach(items) { item in
                activeCard(item)
            }
        }
    }

    @ViewBuilder
    func activeSection(_ items: [HomeTaskItem], showsSearch: Bool) -> some View {
        if !items.isEmpty {
            BuddySectionHeader("In progress", count: items.count) {
                if showsSearch { searchButton }
            }
            .padding(.top, BuddySpacing.md)
            .buddyListRow(vertical: 0)
            ForEach(items) { item in
                activeCard(item)
            }
        }
    }

    @ViewBuilder
    func recentSection(_ items: [HomeTaskItem], showsSearch: Bool) -> some View {
        if !items.isEmpty {
            BuddySectionHeader("Pick up where you left off", count: items.count) {
                HStack(spacing: 0) {
                    if showsSearch { searchButton }
                    if let showAllTasks = actions.showAllTasks {
                        Button(action: showAllTasks) {
                            HStack(spacing: 4) {
                                Text("All tasks")
                                Image(systemName: "arrow.up.right").imageScale(.small)
                            }
                            .buddyText(.label)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .frame(minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.top, BuddySpacing.lg)
            .buddyListRow(vertical: 0)
            ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                VStack(spacing: 0) {
                    if index > 0 { BuddyDivider() }
                    TaskRow(
                        item: item,
                        isOpening: openingKey == item.key,
                        showsDetail: showsDetail,
                        handlers: handlers,
                        onOpen: { open(item) }
                    )
                }
                .buddyListRow(vertical: 0)
                .swipeActions(edge: .leading) { replySwipe(item) }
                .swipeActions(edge: .trailing) { hideSwipe(item) }
            }
        }
    }

    private func activeCard(_ item: HomeTaskItem) -> some View {
        ActiveTaskCard(
            item: item,
            isOpening: openingKey == item.key,
            showsDetail: showsDetail,
            handlers: handlers,
            onOpen: { open(item) }
        )
        .buddyListRow(vertical: BuddySpacing.xs)
        .swipeActions(edge: .leading) { replySwipe(item) }
        .swipeActions(edge: .trailing) { hideSwipe(item) }
    }

    private func replySwipe(_ item: HomeTaskItem) -> some View {
        Button {
            replyTarget = item.session
        } label: {
            Label("Reply", systemImage: "arrowshape.turn.up.left")
        }
        .tint(AgentBuddyTheme.link)
    }

    private func hideSwipe(_ item: HomeTaskItem) -> some View {
        Button {
            actions.hideThread(item.key)
        } label: {
            Label("Hide", systemImage: "eye.slash")
        }
        .tint(AgentBuddyTheme.textSecondary)
    }

    private var searchButton: some View {
        BuddyIconButton(
            systemImage: "magnifyingglass",
            accessibilityLabel: "Search tasks",
            tone: .plain,
            iconSize: 19
        ) {
            isSearching = true
        }
    }

    // MARK: - Empty states

    @ViewBuilder
    var emptyContent: some View {
        if model.connectedServers.isEmpty {
            BuddyEmptyState(
                systemImage: "laptopcomputer.and.iphone",
                title: "Connect your first computer",
                message: "Open AgentBuddy on your Mac and scan its pairing QR code. Tasks then run on that computer while you follow along here.",
                actionTitle: "Scan to connect",
                actionSystemImage: "qrcode.viewfinder",
                actionKind: .primary,
                action: actions.pairWithQRCode
            )
        } else if !model.connectedServers.contains(where: \.canLaunchSessions) {
            BuddyEmptyState(
                systemImage: "wifi.slash",
                title: "Your hosts are offline",
                message: "Task status will sync once a host reconnects. Nothing on the computer was stopped.",
                actionTitle: "View hosts",
                actionSystemImage: "laptopcomputer",
                actionKind: .secondary,
                action: onManageHosts
            )
        } else {
            BuddyEmptyState(
                systemImage: "sparkles",
                title: "No tasks yet",
                message: "Describe what you want done. AgentBuddy starts working on your computer and keeps you posted here.",
                actionTitle: "Start a task",
                actionSystemImage: "plus",
                actionKind: .primary,
                action: { actions.newTask(nil) }
            )
        }
    }
}
