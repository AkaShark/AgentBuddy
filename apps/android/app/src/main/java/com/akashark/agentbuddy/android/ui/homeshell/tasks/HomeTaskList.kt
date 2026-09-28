package com.akashark.agentbuddy.android.ui.homeshell.tasks

import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

/** Which sessions the 任务 tab lists. Pure list shaping over Rust snapshot data. */
object HomeTaskList {
    const val RECENT_LIMIT = 10

    /**
     * - If the user pinned anything, the list is exactly their pins in pin
     *   order; a pin whose thread is not loaded yet shows as a placeholder
     *   while its host is connected.
     * - Otherwise the [RECENT_LIMIT] most recent sessions.
     * - Hidden threads are always excluded.
     */
    fun merge(
        pinned: List<PinnedThreadKey>,
        hidden: List<PinnedThreadKey>,
        servers: List<AppServerSnapshot>,
        allSessions: List<AppSessionSummary>,
    ): List<AppSessionSummary> {
        val hiddenSet = hidden.toSet()
        val candidates = allSessions.filter { pinKey(it.key) !in hiddenSet }
        if (pinned.isEmpty()) return candidates.take(RECENT_LIMIT)
        val byKey = candidates.associateBy { pinKey(it.key) }
        val serversById = servers.associateBy { it.serverId }
        return pinned.mapNotNull { key ->
            if (key in hiddenSet) return@mapNotNull null
            byKey[key] ?: serversById[key.serverId]?.let { placeholder(key, it) }
        }
    }

    /** Host filter: the selected project's host wins over the host pill. */
    fun scoped(sessions: List<AppSessionSummary>, serverId: String?): List<AppSessionSummary> =
        if (serverId.isNullOrEmpty()) sessions else sessions.filter { it.key.serverId == serverId }

    fun pinKey(key: ThreadKey): PinnedThreadKey = PinnedThreadKey(serverId = key.serverId, threadId = key.threadId)

    fun placeholder(
        pinned: PinnedThreadKey,
        server: AppServerSnapshot,
    ): AppSessionSummary =
        AppSessionSummary(
            key = ThreadKey(serverId = pinned.serverId, threadId = pinned.threadId),
            agentRuntimeKind = "codex",
            serverDisplayName = server.displayName,
            serverHost = server.host,
            title = "正在加载任务",
            preview = "",
            cwd = "",
            model = "",
            modelProvider = "",
            parentThreadId = null,
            forkedFromId = null,
            agentNickname = null,
            agentRole = null,
            agentDisplayLabel = null,
            agentStatus = AppSubagentStatus.UNKNOWN,
            updatedAt = null,
            hasActiveTurn = false,
            isResumed = false,
            isSubagent = false,
            isFork = false,
            lastResponsePreview = null,
            lastResponseTurnId = null,
            lastUserMessage = null,
            lastToolLabel = null,
            recentToolLog = emptyList(),
            lastTurnStartMs = null,
            lastTurnEndMs = null,
            stats = null,
            tokenUsage = null,
            goal = null,
        )
}
