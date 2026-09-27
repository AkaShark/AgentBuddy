import SwiftUI

/// 项目 tab: the current project with a direct "new task" action, then the
/// other known projects. Projects are host + working directory pairs derived
/// in Rust (`AppProject`); selecting one keeps any draft in the composer.
struct ProjectsHomeView: View {
    let model: HomeDashboardModel
    let actions: HomeShellActions
    let onManageHosts: () -> Void

    private var summaries: [ProjectSummary] {
        ProjectSummary.build(projects: model.projects, sessions: model.allSessions, servers: model.connectedServers)
    }

    var body: some View {
        let summaries = summaries
        let current = model.selectedProject.flatMap { selected in summaries.first { $0.id == selected.id } }
        let others = summaries.filter { $0.id != current?.id }

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

                if let current {
                    ProjectHeroCard(summary: current, showsHost: showsHostNames) {
                        actions.newTask(current.project)
                    }
                } else {
                    emptyState(hasProjects: !summaries.isEmpty)
                }

                if !others.isEmpty {
                    VStack(alignment: .leading, spacing: 0) {
                        BuddySectionHeader(current == nil ? "Your projects" : "Other projects")
                        ForEach(Array(others.enumerated()), id: \.element.id) { index, summary in
                            if index > 0 { BuddyDivider() }
                            ProjectRow(summary: summary, showsHost: showsHostNames) {
                                actions.selectProject(summary.project)
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
        .buddyPageBackground()
    }

    /// Host names only add information when projects span several hosts.
    private var showsHostNames: Bool {
        Set(model.projects.map(\.serverId)).count > 1
    }

    @ViewBuilder
    private func emptyState(hasProjects: Bool) -> some View {
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
        } else if hasProjects {
            BuddyEmptyState(
                systemImage: "folder",
                title: "Choose a project",
                message: "Pick one of your projects below, or open a new folder on a host.",
                actionTitle: "New project",
                actionSystemImage: "plus",
                actionKind: .secondary,
                action: actions.createProject
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
            BuddyButton("New task", systemImage: "plus", kind: .primary, action: onNewTask)
        }
        .buddyCard(.soft, radius: BuddyRadius.card, padding: BuddySpacing.lg)
        .accessibilityElement(children: .contain)
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
