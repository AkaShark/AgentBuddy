import Foundation

/// Render-only summary of a project (host + working directory) with task
/// counts taken from the same session list the Tasks tab shows.
struct ProjectSummary: Identifiable, Equatable {
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
        var counts: [String: (total: Int, running: Int)] = [:]
        for session in sessions {
            let key = "\(session.serverId)|\(session.cwd)"
            var entry = counts[key] ?? (0, 0)
            entry.total += 1
            if session.hasTurnActive { entry.running += 1 }
            counts[key] = entry
        }
        return projects.map { project in
            let entry = counts["\(project.serverId)|\(project.cwd)"] ?? (0, 0)
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
}
