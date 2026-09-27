package com.akashark.agentbuddy.android.ui.homeshell

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskList
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

class HomeTaskPresentationTest {
    private val id = "s1/t1"

    private fun state(
        active: Boolean = false,
        endMs: Long? = null,
        approval: Boolean = false,
        input: Boolean = false,
        cancelling: Boolean = false,
    ) = HomeTaskPresentation.state(
        taskId = id,
        hasActiveTurn = active,
        lastTurnEndMs = endMs,
        approvalIds = if (approval) setOf(id) else emptySet(),
        inputIds = if (input) setOf(id) else emptySet(),
        cancellingIds = if (cancelling) setOf(id) else emptySet(),
    )

    @Test
    fun `stopping wins over every other state while the turn runs`() {
        assertEquals(BuddyTaskState.STOPPING, state(active = true, approval = true, input = true, cancelling = true))
    }

    @Test
    fun `a stop marker without an active turn does not mean stopping`() {
        assertEquals(BuddyTaskState.AWAITING_APPROVAL, state(active = false, approval = true, cancelling = true))
        assertEquals(BuddyTaskState.COMPLETED, state(active = false, endMs = 5, cancelling = true))
    }

    @Test
    fun `awaiting approval wins over awaiting input and running`() {
        assertEquals(BuddyTaskState.AWAITING_APPROVAL, state(active = true, approval = true, input = true))
    }

    @Test
    fun `awaiting input wins over running`() {
        assertEquals(BuddyTaskState.AWAITING_INPUT, state(active = true, input = true))
    }

    @Test
    fun `running when the turn is active and nothing waits on the user`() {
        assertEquals(BuddyTaskState.RUNNING, state(active = true))
    }

    @Test
    fun `completed after a finished turn, idle before any turn`() {
        assertEquals(BuddyTaskState.COMPLETED, state(endMs = 1_000))
        assertEquals(BuddyTaskState.IDLE, state())
    }

    @Test
    fun `items join approvals and inputs by thread key and skip MCP elicitations`() {
        val sessions = listOf(session("t1", active = true), session("t2", active = true), session("t3"))
        val approvals = listOf(
            approval("a1", "t1", ApprovalKind.COMMAND),
            approval("a2", "t1", ApprovalKind.FILE_CHANGE),
            approval("a3", "t3", ApprovalKind.MCP_ELICITATION),
            approval("a4", null, ApprovalKind.COMMAND),
        )
        val inputs = listOf(input("t2"))
        val items = HomeTaskPresentation.items(
            sessions = sessions,
            pendingApprovals = approvals,
            pendingInputs = inputs,
            pinnedKeys = listOf(PinnedThreadKey(serverId = "s1", threadId = "t3")),
            cancellingIds = emptySet(),
        )
        assertEquals(BuddyTaskState.AWAITING_APPROVAL, items[0].state)
        assertEquals(2, items[0].pendingApprovalCount)
        assertEquals(BuddyTaskState.AWAITING_INPUT, items[1].state)
        assertEquals(BuddyTaskState.IDLE, items[2].state)
        assertEquals(0, items[2].pendingApprovalCount)
        assertEquals(listOf(false, false, true), items.map { it.isPinned })
    }

    @Test
    fun `approvals on another host do not leak onto a same-named thread`() {
        val items = HomeTaskPresentation.items(
            sessions = listOf(session("t1", active = true)),
            pendingApprovals = listOf(approval("a1", "t1", ApprovalKind.COMMAND, serverId = "s2")),
            pendingInputs = emptyList(),
            pinnedKeys = emptyList(),
            cancellingIds = emptySet(),
        )
        assertEquals(BuddyTaskState.RUNNING, items.single().state)
    }

    @Test
    fun `sections partition attention, active and recent keeping order`() {
        val items = HomeTaskPresentation.items(
            sessions = listOf(
                session("done", endMs = 10),
                session("run", active = true),
                session("ask", active = true),
                session("stop", active = true),
                session("idle"),
                session("reply", active = true),
            ),
            pendingApprovals = listOf(approval("a", "ask", ApprovalKind.COMMAND)),
            pendingInputs = listOf(input("reply")),
            pinnedKeys = emptyList(),
            cancellingIds = setOf("s1/stop"),
        )
        val sections = HomeTaskPresentation.sections(items)
        assertEquals(listOf("ask", "reply"), sections.attention.map { it.key.threadId })
        assertEquals(listOf("run", "stop"), sections.active.map { it.key.threadId })
        assertEquals(listOf("done", "idle"), sections.recent.map { it.key.threadId })
        assertEquals(BuddyTaskState.STOPPING, sections.active[1].state)
    }

    @Test
    fun `stop markers are pruned once the turn ends`() {
        val sessions = listOf(session("t1", active = true), session("t2", active = false))
        val pruned = HomeTaskPresentation.pruneCancelling(setOf("s1/t1", "s1/t2", "s1/gone"), sessions)
        assertEquals(setOf("s1/t1"), pruned)
    }

    @Test
    fun `hero summary counts running and waiting tasks`() {
        assertEquals("当前没有进行中的任务。", HomeTaskPresentation.summary(0, 0))
        assertEquals("2 个任务进行中。", HomeTaskPresentation.summary(2, 0))
        assertEquals("1 个等待你确认。", HomeTaskPresentation.summary(0, 1))
        assertEquals("1 个任务进行中，1 个等待你确认。", HomeTaskPresentation.summary(1, 1))
    }

    @Test
    fun `relative time is localized`() {
        val now = 10_000_000_000L
        assertEquals("刚刚", HomeTaskPresentation.relativeTime(now - 30_000, now))
        assertEquals("18 分钟前", HomeTaskPresentation.relativeTime(now - 18 * 60_000, now))
        assertEquals("3 小时前", HomeTaskPresentation.relativeTime(now - 3 * 3_600_000, now))
        assertEquals("昨天", HomeTaskPresentation.relativeTime(now - 30 * 3_600_000, now))
        assertEquals("2 天前", HomeTaskPresentation.relativeTime(now - 2 * 86_400_000, now))
        assertEquals("刚刚", HomeTaskPresentation.relativeTime(now + 5_000, now))
    }

    @Test
    fun `home list keeps pins in order with placeholders, else ten most recent, never hidden`() {
        val all = (1..12).map { session("t$it") }
        val hidden = listOf(PinnedThreadKey(serverId = "s1", threadId = "t1"))
        val recent = HomeTaskList.merge(emptyList(), hidden, emptyList(), all)
        assertEquals(10, recent.size)
        assertTrue(recent.none { it.key.threadId == "t1" })

        val pins = listOf(
            PinnedThreadKey(serverId = "s1", threadId = "t5"),
            PinnedThreadKey(serverId = "s1", threadId = "t1"),
            PinnedThreadKey(serverId = "s1", threadId = "t3"),
        )
        val pinned = HomeTaskList.merge(pins, hidden, emptyList(), all)
        assertEquals(listOf("t5", "t3"), pinned.map { it.key.threadId })
    }

    @Test
    fun `title and project name fall back sensibly`() {
        assertEquals("未命名任务", HomeTaskPresentation.title("未命名会话"))
        assertEquals("修复登录", HomeTaskPresentation.title(" 修复登录 "))
        assertEquals("AgentBuddy", HomeTaskPresentation.projectName("/Users/me/Projects/AgentBuddy/"))
        assertEquals(null, HomeTaskPresentation.projectName("  "))
    }

    private fun session(
        threadId: String,
        active: Boolean = false,
        endMs: Long? = null,
    ) = AppSessionSummary(
        key = ThreadKey(serverId = "s1", threadId = threadId),
        agentRuntimeKind = "codex",
        serverDisplayName = "Mac",
        serverHost = "mac.local",
        title = threadId,
        preview = "",
        cwd = "/Users/me/AgentBuddy",
        model = "",
        modelProvider = "",
        parentThreadId = null,
        forkedFromId = null,
        agentNickname = null,
        agentRole = null,
        agentDisplayLabel = null,
        agentStatus = AppSubagentStatus.UNKNOWN,
        updatedAt = null,
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
        lastTurnEndMs = endMs,
        stats = null,
        tokenUsage = null,
        goal = null,
    )

    private fun approval(
        id: String,
        threadId: String?,
        kind: ApprovalKind,
        serverId: String = "s1",
    ) = PendingApproval(
        id = id,
        serverId = serverId,
        kind = kind,
        threadId = threadId,
        turnId = null,
        itemId = null,
        command = null,
        path = null,
        grantRoot = null,
        cwd = null,
        reason = null,
    )

    private fun input(threadId: String) = PendingUserInputRequest(
        id = "in-$threadId",
        serverId = "s1",
        threadId = threadId,
        turnId = "turn",
        itemId = "item",
        questions = emptyList(),
        requesterAgentNickname = null,
        requesterAgentRole = null,
    )
}
