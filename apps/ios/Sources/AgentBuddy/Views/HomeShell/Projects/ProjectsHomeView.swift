import SwiftUI

/// 项目 tab: the current project with its latest tasks and a direct "new
/// task" action, then the other known projects. Projects are host + working
/// directory pairs derived in Rust (`AppProject`); selecting one keeps any
/// draft in the composer.
struct ProjectsHomeView: View {
    let model: HomeDashboardModel
    let actions: HomeShellActions
    var openingKey: ThreadKey?
    let onManageHosts: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let topAnchor = "projects.top"

    private var summaries: [ProjectSummary] {
        // A folder just picked in the directory picker has no task yet; keep
        // it listed while it is the current project.
        var projects = model.projects
        if let selected = model.selectedProject, !projects.contains(where: { $0.id == selected.id }) {
            projects.insert(selected, at: 0)
        }
        return ProjectSummary.build(projects: projects, sessions: model.allSessions, servers: model.connectedServers)
    }

    var body: some View {
        let summaries = summaries
        let current = ProjectSummary.current(in: summaries, selectedId: model.selectedProject?.id)
        let others = summaries.filter { $0.id != current?.id }

        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                    BuddyPageHeader(
                        eyebrow: "Room for every idea",
                        title: "Projects",
                        subtitle: Text("Keep the context. Pick it up next time.")
                    ) {
                        BuddyIconButton(
                            systemImage: "plus",
                            accessibilityLabel: "New project",
                            tone: .surface,
                            diameter: 44,
                            isEnabled: !model.connectedServers.isEmpty,
                            action: actions.createProject
                        )
                    }
                    .id(Self.topAnchor)

                    if let current {
                        hero(for: current)
                    } else {
                        emptyState
                    }

                    if !others.isEmpty {
                        VStack(alignment: .leading, spacing: 0) {
                            BuddySectionHeader("Other projects")
                            ForEach(Array(others.enumerated()), id: \.element.id) { index, summary in
                                if index > 0 { BuddyDivider() }
                                ProjectRow(summary: summary, showsHost: showsHostNames) {
                                    makeCurrent(summary.project, proxy: proxy)
                                } onNewTask: {
                                    actions.newTask(summary.project)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.lg)
                .padding(.bottom, BuddySpacing.xl)
            }
            .scrollIndicators(.hidden)
        }
        .buddyPageBackground()
    }

    /// Host names only add information when projects span several hosts.
    private var showsHostNames: Bool {
        Set(model.projects.map(\.serverId)).count > 1
    }

    private func hero(for summary: ProjectSummary) -> some View {
        ProjectHeroCard(
            summary: summary,
            showsHost: showsHostNames,
            recentTasks: ProjectSummary.recentSessions(
                for: summary.project,
                in: model.allSessions,
                hiddenKeys: model.hiddenKeys
            ),
            isHostOnline: model.canLaunchSessions(on: summary.project.serverId),
            openingKey: openingKey,
            onOpenTask: { session in
                guard openingKey == nil else { return }
                Task { await actions.openSession(session) }
            },
            onSeeAllTasks: { actions.showProjectTasks(summary.project) },
            onNewTask: { actions.newTask(summary.project) }
        )
    }

    /// Makes a listed project current and brings the card back into view so
    /// the change is visible.
    private func makeCurrent(_ project: AppProject, proxy: ScrollViewProxy) {
        withAnimation(BuddyMotion.animation(.page, reduceMotion: reduceMotion)) {
            actions.selectProject(project)
            proxy.scrollTo(Self.topAnchor, anchor: .top)
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        if model.connectedServers.isEmpty {
            BuddyEmptyState(
                systemImage: "folder.badge.questionmark",
                title: "Connect a host first",
                message: "Projects are folders on a connected computer. Pair a computer, then pick a folder to work in.",
                actionTitle: "View hosts",
                actionSystemImage: "laptopcomputer",
                actionKind: .secondary,
                action: onManageHosts
            )
        } else {
            BuddyEmptyState(
                systemImage: "folder.badge.plus",
                title: "No projects yet",
                message: "Pick a folder on one of your hosts. Tasks you start there are grouped here.",
                actionTitle: "New project",
                actionSystemImage: "plus",
                action: actions.createProject
            )
        }
    }
}

// MARK: - Hero card

struct ProjectHeroCard: View {
    let summary: ProjectSummary
    let showsHost: Bool
    let recentTasks: [HomeDashboardRecentSession]
    let isHostOnline: Bool
    let openingKey: ThreadKey?
    let onOpenTask: (HomeDashboardRecentSession) -> Void
    let onSeeAllTasks: () -> Void
    let onNewTask: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            HStack(alignment: .top) {
                BuddyIconTile(content: .brandMark, fill: AgentBuddyTheme.surface, size: 48)
                Spacer()
                if summary.runningCount > 0 {
                    Text("\(summary.runningCount) running")
                        .buddyText(.label, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .padding(.horizontal, BuddySpacing.sm)
                        .frame(minHeight: BuddySize.compactPill)
                        .background(AgentBuddyTheme.surface, in: Capsule())
                }
            }
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text(verbatim: summary.name)
                    .buddyText(.title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                Text(verbatim: [summary.hostName, summary.displayPath].compactMap { $0 }.joined(separator: " · "))
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            recentTasksSection
            BuddyButton("New task", systemImage: "plus", kind: .primary, action: onNewTask)
                // An offline host can't start the task; the note above says why.
                .disabled(!isHostOnline)
        }
        .buddyCard(.soft, radius: BuddyRadius.card, padding: BuddySpacing.lg)
        .accessibilityElement(children: .contain)
    }

    private var recentTasksSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !isHostOnline {
                sectionLabel
                note("Host offline. Reconnect it to continue this project.")
            } else if summary.taskCount == 0 {
                sectionLabel
                note("No tasks yet. Start one with New task.")
            } else {
                // Tasks hidden from home are left out, so every row may be
                // gone; then only the link below remains.
                if !recentTasks.isEmpty {
                    sectionLabel
                    ForEach(Array(recentTasks.enumerated()), id: \.element.id) { index, session in
                        if index > 0 { BuddyDivider() }
                        ProjectRecentTaskRow(session: session, isOpening: openingKey == session.key) {
                            onOpenTask(session)
                        }
                    }
                }
                if summary.taskCount > recentTasks.count {
                    BuddyButton(
                        "See all \(summary.taskCount) tasks",
                        trailingSystemImage: "chevron.right",
                        kind: .quiet,
                        fullWidth: false,
                        action: onSeeAllTasks
                    )
                    // Line the link text up with the task titles.
                    .padding(.leading, -BuddySpacing.xs)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var sectionLabel: some View {
        Text("Recent tasks")
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .accessibilityAddTraits(.isHeader)
            .padding(.bottom, BuddySpacing.xxs)
    }

    private func note(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, BuddySpacing.xxs)
    }
}

/// One of the current project's latest tasks: title, last update, chevron.
struct ProjectRecentTaskRow: View {
    let session: HomeDashboardRecentSession
    let isOpening: Bool
    let onOpen: () -> Void

    var body: some View {
        let title = HomeTaskPresentation.title(for: session)
        let updated = String(localized: "Updated \(HomeTaskPresentation.relativeTime(session.updatedAt))")
        Button(action: onOpen) {
            HStack(spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                    Text(verbatim: title)
                        .buddyText(.body, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)
                    Text(verbatim: updated)
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isOpening {
                    ProgressView().controlSize(.small)
                } else {
                    Image(systemName: "chevron.right")
                        .imageScale(.small)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                }
            }
            .padding(.vertical, BuddySpacing.xs)
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: title))
        .accessibilityValue(Text(verbatim: updated))
    }
}

// MARK: - Row

struct ProjectRow: View {
    let summary: ProjectSummary
    let showsHost: Bool
    let onSelect: () -> Void
    let onNewTask: () -> Void

    var body: some View {
        Button(action: onSelect) {
            BuddyListRow(title: Text(verbatim: summary.name), subtitle: Text(verbatim: subtitle)) {
                BuddyIconTile(content: .initial(summary.initial))
            }
            .foregroundStyle(AgentBuddyTheme.textSecondary)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(action: onNewTask) { Label("New task here", systemImage: "plus") }
            Button(action: onSelect) { Label("Make current project", systemImage: "checkmark.circle") }
        }
        .accessibilityHint(Text("Makes this the current project"))
    }

    private var subtitle: String {
        var parts = [String(localized: "\(summary.taskCount) tasks")]
        if let lastUsed = summary.lastUsed {
            parts.append(String(localized: "Updated \(HomeTaskPresentation.relativeTime(lastUsed))"))
        }
        if showsHost, let host = summary.hostName {
            parts.append(host)
        }
        return parts.joined(separator: " · ")
    }
}
