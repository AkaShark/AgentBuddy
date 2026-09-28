package com.akashark.agentbuddy.android.ui.sessions

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.ThreadKey

class SessionsPresentationTest {
    private val id = "s1/t1"

    private fun state(
        summary: AppSessionSummary,
        approval: Boolean = false,
        input: Boolean = false,
        stopping: Boolean = false,
    ) = sessionTaskState(
        summary = summary,
        approvalIds = if (approval) setOf(id) else emptySet(),
        inputIds = if (input) setOf(id) else emptySet(),
        stoppingIds = if (stopping) setOf(id) else emptySet(),
    )

    @Test
    fun `rows show the same waiting and stopping states as the home cards`() {
        assertEquals(BuddyTaskState.AWAITING_APPROVAL, state(summary(active = true), approval = true, input = true))
        assertEquals(BuddyTaskState.AWAITING_INPUT, state(summary(active = true), input = true))
        assertEquals(BuddyTaskState.STOPPING, state(summary(active = true), approval = true, stopping = true))
        assertEquals(BuddyTaskState.RUNNING, state(summary(active = true)))
    }

    @Test
    fun `plain tasks are idle before a turn and completed after one`() {
        assertEquals(BuddyTaskState.IDLE, state(summary()))
        assertEquals(BuddyTaskState.COMPLETED, state(summary(endMs = 5)))
    }

    @Test
    fun `idle sub-agents show their reported status, waiting ones still wait`() {
        assertEquals(BuddyTaskState.FAILED, state(summary(subagent = AppSubagentStatus.ERRORED, endMs = 5)))
        assertEquals(BuddyTaskState.INTERRUPTED, state(summary(subagent = AppSubagentStatus.SHUTDOWN)))
        assertEquals(BuddyTaskState.IDLE, state(summary(subagent = AppSubagentStatus.RUNNING, endMs = 5)))
        assertEquals(BuddyTaskState.AWAITING_APPROVAL, state(summary(subagent = AppSubagentStatus.RUNNING), approval = true))
        assertEquals(BuddyTaskState.RUNNING, state(summary(active = true, subagent = AppSubagentStatus.COMPLETED)))
    }

    @Test
    fun `row subtitles name the waiting state first`() {
        val summaries = listOf(summary(active = true))
        val groups = buildSessionsGroups(
            derived = SessionsDerivation.derive(summaries = summaries),
            allSummaries = summaries,
            activeKey = null,
            collapsedGroupKeys = emptySet(),
            collapsedNodeKeys = emptySet(),
            pathLabel = { _, cwd -> cwd },
            inputIds = setOf(id),
        )
        val row = groups.single().rows.single()
        assertEquals(BuddyTaskState.AWAITING_INPUT, row.state)
        assertEquals("等待你回复", row.subtitle.substringBefore(" · "))
    }

    private fun summary(
        active: Boolean = false,
        endMs: Long? = null,
        subagent: AppSubagentStatus? = null,
    ) = AppSessionSummary(
        key = ThreadKey(serverId = "s1", threadId = "t1"),
        agentRuntimeKind = "codex",
        serverDisplayName = "Mac",
        serverHost = "mac.local",
        title = "t1",
        preview = "",
        cwd = "/Users/me/AgentBuddy",
        model = "",
        modelProvider = "",
        parentThreadId = null,
        forkedFromId = null,
        agentNickname = null,
        agentRole = null,
        agentDisplayLabel = null,
        agentStatus = subagent ?: AppSubagentStatus.UNKNOWN,
        updatedAt = null,
        hasActiveTurn = active,
        isResumed = true,
        isSubagent = subagent != null,
        isFork = false,
        lastResponsePreview = null,
        lastResponseTurnId = null,
        lastUserMessage = null,
        lastToolLabel = null,
        recentToolLog = emptyList(),
        lastTurnStartMs = null,
        lastTurnEndMs = endMs,
        stats = null,
        tokenUsage = null,
        goal = null,
    )
}
