import Foundation

/// Render-only summary of a project (host + working directory) with task
/// counts taken from the same session list the Tasks tab shows.
struct ProjectSummary: Identifiable, Equatable {
    /// How many of a project's tasks the current-project card lists.
    static let recentTaskLimit = 3

    let project: AppProject
    let name: String
    let hostName: String?
    let displayPath: String
    let taskCount: Int
    let runningCount: Int
    let lastUsed: Date?

    var id: String { project.id }

    var initial: String {
        name.first.map { String($0).uppercased() } ?? "#"
    }

    static func build(
        projects: [AppProject],
        sessions: [HomeDashboardRecentSession],
        servers: [HomeDashboardServer]
    ) -> [ProjectSummary] {
        let serverNames = Dictionary(servers.map { ($0.id, $0.displayName) }, uniquingKeysWith: { first, _ in first })
        // Only tasks on a host with a listed project can match; skip the rest
        // before asking Rust for the canonical id.
        let listedServerIds = Set(projects.map(\.serverId))
        var resolver = ProjectIdResolver()
        var counts: [String: (total: Int, running: Int)] = [:]
        for session in sessions where listedServerIds.contains(session.serverId) {
            let id = resolver.id(serverId: session.serverId, cwd: session.cwd)
            var entry = counts[id] ?? (0, 0)
            entry.total += 1
            if session.hasTurnActive { entry.running += 1 }
            counts[id] = entry
        }
        return projects.map { project in
            let entry = counts[project.id] ?? (0, 0)
            return ProjectSummary(
                project: project,
                name: HomeTaskPresentation.projectName(forCwd: project.cwd) ?? project.cwd,
                hostName: serverNames[project.serverId],
                displayPath: abbreviateHomePath(project.cwd),
                taskCount: entry.total,
                runningCount: entry.running,
                // 0 means "unknown" upstream; don't render it as 1970.
                lastUsed: project.lastUsedAtMs.flatMap { $0 > 0 ? Date(timeIntervalSince1970: TimeInterval($0) / 1000) : nil }
            )
        }
    }

    /// The current project: the user's pick while it is listed, otherwise the
    /// most recently used one (Rust returns projects newest first).
    static func current(in summaries: [ProjectSummary], selectedId: String?) -> ProjectSummary? {
        if let selectedId, let selected = summaries.first(where: { $0.id == selectedId }) {
            return selected
        }
        return summaries.first
    }

    /// A project's newest tasks, matched the same way `build` counts them.
    /// Tasks hidden from home stay out, as they do on the Tasks tab.
    static func recentSessions(
        for project: AppProject,
        in sessions: [HomeDashboardRecentSession],
        hiddenKeys: [PinnedThreadKey] = [],
        limit: Int = ProjectSummary.recentTaskLimit
    ) -> [HomeDashboardRecentSession] {
        let hidden = Set(hiddenKeys)
        var resolver = ProjectIdResolver()
        return Array(
            sessions
                .filter { session in
                    session.serverId == project.serverId
                        && !hidden.contains(PinnedThreadKey(threadKey: session.key))
                        && resolver.id(serverId: session.serverId, cwd: session.cwd) == project.id
                }
                .sorted { $0.updatedAt > $1.updatedAt }
                .prefix(limit)
        )
    }
}

/// Rust's canonical project id for a task's folder (`projectIdFor`), so
/// "/app/" and "/app" land in the same project. Caches per folder so each
/// distinct cwd crosses the FFI once per pass.
struct ProjectIdResolver {
    private var ids: [String: [String: String]] = [:]

    mutating func id(serverId: String, cwd: String) -> String {
        if let id = ids[serverId]?[cwd] { return id }
        let id = projectIdFor(serverId: serverId, cwd: cwd)
        ids[serverId, default: [:]][cwd] = id
        return id
    }
}
