package com.akashark.agentbuddy.android.ui.homeshell

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostPresentation
import com.akashark.agentbuddy.android.ui.homeshell.hosts.HostSummary
import com.akashark.agentbuddy.android.ui.homeshell.projects.ProjectSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.AppServerCapabilities
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppServerTransportState
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.ThreadKey

class HomeShellSummariesTest {
    private val idFor = { serverId: String, cwd: String -> "$serverId::${cwd.trimEnd('/')}" }

    @Test
    fun `project counts are keyed by project id and include running tasks`() {
        val projects = listOf(project("mbp", "/p/AgentBuddy", 2_000), project("mbp", "/p/Site", 0))
        val sessions = listOf(
            session("mbp", "/p/AgentBuddy/", active = true),
            session("mbp", "/p/AgentBuddy", active = false),
            session("mini", "/p/AgentBuddy", active = true),
            session("mbp", "", active = true),
        )
        val summaries = ProjectSummaries.build(
            projects = projects,
            sessions = sessions,
            serverNames = mapOf("mbp" to "MacBook Pro"),
            idFor = idFor,
            nameFor = { it.substringAfterLast('/') },
            pathFor = { it.cwd },
        )
        assertEquals(2, summaries[0].taskCount)
        assertEquals(1, summaries[0].runningCount)
        assertEquals("MacBook Pro", summaries[0].hostName)
        assertEquals(0, summaries[1].taskCount)
        // Upstream reports 0 for "unknown": never render it as 1970.
        assertNull(summaries[1].lastUsedMs)
    }

    @Test
    fun `recent tasks are the project's newest three`() {
        val project = project("mbp", "/p/A", 0)
        val sessions = listOf(
            session("mbp", "/p/A", active = false, threadId = "a1", updatedAt = 1),
            session("mbp", "/p/A/", active = false, threadId = "a5", updatedAt = 5),
            session("mbp", "/p/B", active = false, threadId = "b9", updatedAt = 9),
            session("mini", "/p/A", active = false, threadId = "m8", updatedAt = 8),
            session("mbp", "/p/A", active = true, threadId = "a3", updatedAt = 3),
            session("mbp", "/p/A", active = false, threadId = "a4", updatedAt = 4),
        )
        val recent = ProjectSummaries.recentTasks(project, sessions, idFor)
        assertEquals(listOf("a5", "a4", "a3"), recent.map { it.key.threadId })
    }

    @Test
    fun `project tasks use the project id rule and stay on the project's host`() {
        val sessions = listOf(
            session("mbp", "/p/A/", active = false, threadId = "a1"),
            session("mbp", "/p/a", active = false, threadId = "lower"),
            session("mbp", "/p/B", active = false, threadId = "b1"),
            session("mini", "/p/A", active = false, threadId = "m1"),
            session("mbp", "", active = false, threadId = "blank"),
        )
        val tasks = ProjectSummaries.tasksOf(idFor("mbp", "/p/A"), "mbp", sessions, idFor)
        assertEquals(listOf("a1"), tasks.map { it.key.threadId })
    }

    @Test
    fun `hero is the selected project, else the most recently used`() {
        val summaries = ProjectSummaries.build(
            projects = listOf(project("mbp", "/p/A", 1_000), project("mbp", "/p/B", 5_000)),
            sessions = emptyList(),
            serverNames = emptyMap(),
            idFor = idFor,
            nameFor = { it },
            pathFor = { it.cwd },
        )
        assertEquals("mbp::/p/B", ProjectSummaries.hero(summaries, selectedId = null)?.id)
        assertEquals("mbp::/p/A", ProjectSummaries.hero(summaries, selectedId = "mbp::/p/A")?.id)
    }

    @Test
    fun `a stale disconnected entry never hides the connected host on the same address`() {
        val servers = listOf(
            server("stale", "Mac", "mac.local", AppServerHealth.DISCONNECTED),
            server("live", "Mac", "MAC.local", AppServerHealth.CONNECTED),
            server("mini", "Mini", "mini.local", AppServerHealth.DISCONNECTED),
            server("air", "Air", "air.local", AppServerHealth.CONNECTED),
        )
        assertEquals(listOf("air", "live", "mini"), hostsInDisplayOrder(servers).map { it.serverId })
    }

    @Test
    fun `offline hosts are a connection state, never a failure of tasks`() {
        assertEquals(BuddyConnectionState.CONNECTED, HostPresentation.connectionState(AppServerHealth.CONNECTED))
        assertEquals(BuddyConnectionState.CONNECTING, HostPresentation.connectionState(AppServerHealth.CONNECTING))
        assertEquals(BuddyConnectionState.DISCONNECTED, HostPresentation.connectionState(AppServerHealth.DISCONNECTED))
        assertEquals(BuddyConnectionState.FAILED, HostPresentation.connectionState(AppServerHealth.UNRESPONSIVE))
    }

    @Test
    fun `source titles map saved connection modes`() {
        assertEquals("点对点连接", HostPresentation.sourceTitle(false, "manual", hasAlleycatNode = true, preferredConnectionMode = null, hasWebsocketUrl = false))
        assertEquals("SSH", HostPresentation.sourceTitle(false, "manual", false, preferredConnectionMode = "ssh", hasWebsocketUrl = false))
        assertEquals("局域网", HostPresentation.sourceTitle(false, "bonjour", false, null, false))
        assertEquals("在这台手机上运行", HostPresentation.sourceTitle(true, null, false, null, false))
    }

    @Test
    fun `primary host is the selected one, else the first connected`() {
        val hosts = listOf(host("a", BuddyConnectionState.DISCONNECTED), host("b", BuddyConnectionState.CONNECTED))
        assertEquals("b", HostPresentation.primary(hosts, selectedServerId = null)?.serverId)
        assertEquals("a", HostPresentation.primary(hosts, selectedServerId = "a")?.serverId)
    }

    @Test
    fun `the This Device sentinel is mapped only for display`() {
        assertEquals("本机", hostDisplayName("This Device"))
        assertEquals("MacBook Pro", hostDisplayName("MacBook Pro"))
    }

    private fun host(id: String, connection: BuddyConnectionState) = HostSummary(
        serverId = id,
        name = id,
        isLocal = false,
        connection = connection,
        sourceTitle = "SSH",
        partners = emptyList(),
        runningCount = 0,
        hasTerminal = false,
    )

    private fun project(serverId: String, cwd: String, lastUsedAtMs: Long) =
        AppProject(id = idFor(serverId, cwd), serverId = serverId, cwd = cwd, lastUsedAtMs = lastUsedAtMs)

    private fun session(
        serverId: String,
        cwd: String,
        active: Boolean,
        threadId: String = "$serverId$cwd$active",
        updatedAt: Long? = null,
    ) = AppSessionSummary(
        key = ThreadKey(serverId = serverId, threadId = threadId),
        agentRuntimeKind = "codex",
        serverDisplayName = serverId,
        serverHost = serverId,
        title = "t",
        preview = "",
        cwd = cwd,
        model = "",
        modelProvider = "",
        parentThreadId = null,
        forkedFromId = null,
        agentNickname = null,
        agentRole = null,
        agentDisplayLabel = null,
        agentStatus = AppSubagentStatus.UNKNOWN,
        updatedAt = updatedAt,
        hasActiveTurn = active,
        isResumed = true,
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

    private fun server(id: String, name: String, host: String, health: AppServerHealth) = AppServerSnapshot(
        serverId = id,
        displayName = name,
        host = host,
        port = 8390u,
        wakeMac = null,
        isLocal = false,
        health = health,
        transportState = if (health == AppServerHealth.CONNECTED) AppServerTransportState.CONNECTED else AppServerTransportState.DISCONNECTED,
        capabilities = AppServerCapabilities(
            canUseTransportActions = true,
            canBrowseDirectories = true,
            canStartThreads = true,
            canResumeThreads = true,
            supportsTurnPagination = true,
        ),
        account = null,
        requiresOpenaiAuth = false,
        rateLimits = null,
        rateLimitsByRuntime = emptyList(),
        availableModels = null,
        agentRuntimes = emptyList(),
        connectionProgress = null,
        usageStats = null,
        codexVersion = null,
    )
}
