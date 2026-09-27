package com.akashark.agentbuddy.android.ui.homeshell.projects

import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppSessionSummary

/**
 * Render-only summary of a project (host + working directory) with task
 * counts taken from the same session list the 任务 tab uses.
 */
data class ProjectSummary(
    val project: AppProject,
    val name: String,
    val hostName: String?,
    val displayPath: String,
    val taskCount: Int,
    val runningCount: Int,
    /** Milliseconds; null when unknown (upstream reports 0 for "unknown"). */
    val lastUsedMs: Long?,
) {
    val id: String get() = project.id

    val initial: String get() = name.firstOrNull()?.uppercase() ?: "#"
}

object ProjectSummaries {
    /**
     * [idFor], [nameFor] and [pathFor] are injected so this stays pure: the
     * app passes the Rust `projectIdFor` / `projectDefaultLabel` and the
     * platform path abbreviation.
     */
    fun build(
        projects: List<AppProject>,
        sessions: List<AppSessionSummary>,
        serverNames: Map<String, String>,
        idFor: (serverId: String, cwd: String) -> String,
        nameFor: (cwd: String) -> String,
        pathFor: (AppProject) -> String,
    ): List<ProjectSummary> {
        val totals = HashMap<String, Int>()
        val running = HashMap<String, Int>()
        for (session in sessions) {
            if (session.cwd.isBlank()) continue
            val id = idFor(session.key.serverId, session.cwd)
            totals[id] = (totals[id] ?: 0) + 1
            if (session.hasActiveTurn) running[id] = (running[id] ?: 0) + 1
        }
        return projects.map { project ->
            ProjectSummary(
                project = project,
                name = nameFor(project.cwd).ifBlank { project.cwd },
                hostName = serverNames[project.serverId],
                displayPath = pathFor(project),
                taskCount = totals[project.id] ?: 0,
                runningCount = running[project.id] ?: 0,
                lastUsedMs = project.lastUsedAtMs?.takeIf { it > 0 },
            )
        }
    }

    /** The current project, else the most recently used one. */
    fun hero(summaries: List<ProjectSummary>, selectedId: String?): ProjectSummary? =
        summaries.firstOrNull { it.id == selectedId } ?: summaries.maxByOrNull { it.lastUsedMs ?: 0L }

    /** 「3 个任务 · 昨天更新 · MacBook Pro」 */
    fun rowSubtitle(summary: ProjectSummary, nowMs: Long = System.currentTimeMillis()): String =
        buildList {
            add("${summary.taskCount} 个任务")
            summary.lastUsedMs?.let { add("${HomeTaskPresentation.relativeTime(it, nowMs)}更新") }
            summary.hostName?.let(::add)
        }.joinToString(" · ")
}
