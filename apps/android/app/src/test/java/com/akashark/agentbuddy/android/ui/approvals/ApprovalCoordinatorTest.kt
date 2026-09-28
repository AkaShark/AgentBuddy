package com.akashark.agentbuddy.android.ui.approvals

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.ThreadKey

class ApprovalCoordinatorTest {
    private val threadA = ThreadKey(serverId = "srv", threadId = "thread-a")

    private fun approval(
        id: String,
        threadId: String? = threadA.threadId,
        kind: ApprovalKind = ApprovalKind.COMMAND,
    ) = PendingApproval(
        id = id,
        serverId = "srv",
        kind = kind,
        threadId = threadId,
        turnId = "turn-1",
        itemId = null,
        command = "make test",
        path = null,
        grantRoot = null,
        cwd = "/Users/me/AgentBuddy",
        reason = null,
    )

    @Test
    fun `request that leaves without a local decision is resolved elsewhere`() {
        val coordinator = ApprovalCoordinator(clock = { 42L })
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        assertNull(coordinator.outcome(threadA))

        coordinator.reconcile(emptyList())

        val outcome = coordinator.outcome(threadA)
        assertEquals(ApprovalOutcomeKind.RESOLVED_ELSEWHERE, outcome?.kind)
        assertEquals("1", outcome?.approvalId)
        assertEquals(42L, outcome?.timestampMillis)
    }

    @Test
    fun `local decision records its outcome and is not reported as elsewhere`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        val sent = mutableListOf<Pair<String, ApprovalDecisionValue>>()

        val accepted = coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { id, decision -> sent += id to decision }
        assertTrue(accepted)
        assertEquals(listOf("1" to ApprovalDecisionValue.ACCEPT), sent)
        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, coordinator.outcome(threadA)?.kind)

        coordinator.reconcile(emptyList())
        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, coordinator.outcome(threadA)?.kind)
        assertTrue(coordinator.submitting.isEmpty())
    }

    @Test
    fun `repeat taps while a decision is in flight are ignored`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val first = async {
            coordinator.submit(request, ApprovalDecisionValue.DECLINE) { _, _ ->
                calls += 1
                gate.await()
            }
        }
        yield()
        assertEquals(ApprovalDecisionValue.DECLINE, coordinator.submitting["1"])
        val second = coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ -> calls += 1 }
        assertFalse(second)

        gate.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, calls)
        assertEquals(ApprovalOutcomeKind.DENIED, coordinator.outcome(threadA)?.kind)
        assertFalse(coordinator.isSubmitting("1"))
    }

    @Test
    fun `answered request stays locked until the snapshot drops it`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        var calls = 0

        assertTrue(coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ -> calls += 1 })
        assertTrue(coordinator.submitting.isEmpty())
        assertEquals(mapOf("1" to ApprovalDecisionValue.ACCEPT), coordinator.lockedDecisions)

        // The snapshot still lists it: a second tap sends nothing.
        coordinator.reconcile(listOf(request))
        assertFalse(coordinator.submit(request, ApprovalDecisionValue.DECLINE) { _, _ -> calls += 1 })
        assertEquals(1, calls)
        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, coordinator.outcome(threadA)?.kind)

        coordinator.reconcile(emptyList())
        assertTrue(coordinator.lockedDecisions.isEmpty())
        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, coordinator.outcome(threadA)?.kind)
    }

    @Test
    fun `in-flight decision is locked too`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        val gate = CompletableDeferred<Unit>()

        val first = async { coordinator.submit(request, ApprovalDecisionValue.DECLINE) { _, _ -> gate.await() } }
        yield()
        assertEquals(mapOf("1" to ApprovalDecisionValue.DECLINE), coordinator.lockedDecisions)

        gate.complete(Unit)
        assertTrue(first.await())
        assertEquals(mapOf("1" to ApprovalDecisionValue.DECLINE), coordinator.lockedDecisions)
    }

    @Test
    fun `failed submission keeps the request with a retryable failure`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))

        val ok = coordinator.submit(request, ApprovalDecisionValue.ACCEPT_FOR_SESSION) { _, _ ->
            throw IllegalStateException("host said no")
        }

        assertFalse(ok)
        assertEquals(ApprovalFailure("host said no", ApprovalDecisionValue.ACCEPT_FOR_SESSION), coordinator.failures["1"])
        assertNull(coordinator.outcome(threadA))
        assertTrue(coordinator.submitting.isEmpty())
        assertTrue(coordinator.lockedDecisions.isEmpty())

        // A successful retry clears the failure.
        assertTrue(coordinator.submit(request, ApprovalDecisionValue.ACCEPT_FOR_SESSION) { _, _ -> })
        assertNull(coordinator.failures["1"])
        assertEquals(ApprovalOutcomeKind.ALLOWED_FOR_SESSION, coordinator.outcome(threadA)?.kind)
    }

    @Test
    fun `disconnect errors use the reconnect wording`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))

        coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ ->
            throw RuntimeException("transport error: not connected")
        }

        assertEquals("连接已断开，等搭子重连后再试。", coordinator.failures["1"]?.message)
    }

    @Test
    fun `request leaving while its decision is in flight takes the local outcome`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))

        coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ ->
            // The snapshot drops the request before the call returns.
            coordinator.reconcile(emptyList())
            assertNull(coordinator.outcome(threadA))
        }

        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, coordinator.outcome(threadA)?.kind)
        // A later, unrelated request that disappears is still reported.
        val next = approval("2")
        coordinator.reconcile(listOf(next))
        coordinator.reconcile(emptyList())
        assertEquals(ApprovalOutcomeKind.RESOLVED_ELSEWHERE, coordinator.outcome(threadA)?.kind)
    }

    @Test
    fun `failure after the request is gone reports resolved elsewhere`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))

        coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ ->
            coordinator.reconcile(emptyList())
            throw IllegalStateException("approval no longer pending")
        }

        assertNull(coordinator.failures["1"])
        assertEquals(ApprovalOutcomeKind.RESOLVED_ELSEWHERE, coordinator.outcome(threadA)?.kind)
    }

    @Test
    fun `failures are cleared when the request leaves the queue`() = runBlocking {
        val coordinator = ApprovalCoordinator()
        val request = approval("1")
        coordinator.reconcile(listOf(request))
        coordinator.submit(request, ApprovalDecisionValue.ACCEPT) { _, _ -> throw IllegalStateException("boom") }
        assertEquals("boom", coordinator.failures["1"]?.message)

        coordinator.reconcile(emptyList())

        assertTrue(coordinator.failures.isEmpty())
        assertEquals(ApprovalOutcomeKind.RESOLVED_ELSEWHERE, coordinator.outcome(threadA)?.kind)
    }

    @Test
    fun `outcomes are kept per thread and can be dismissed`() {
        val coordinator = ApprovalCoordinator()
        val threadB = ThreadKey(serverId = "srv", threadId = "thread-b")
        coordinator.reconcile(listOf(approval("1"), approval("2", threadId = threadB.threadId)))
        coordinator.reconcile(listOf(approval("2", threadId = threadB.threadId)))

        assertEquals(ApprovalOutcomeKind.RESOLVED_ELSEWHERE, coordinator.outcome(threadA)?.kind)
        assertNull(coordinator.outcome(threadB))

        coordinator.dismissOutcome(threadA)
        assertNull(coordinator.outcome(threadA))
    }

    @Test
    fun `requests without a thread never produce an outcome`() {
        val coordinator = ApprovalCoordinator()
        coordinator.reconcile(listOf(approval("1", threadId = null)))
        coordinator.reconcile(emptyList())
        assertTrue(coordinator.outcomes.isEmpty())
    }

    @Test
    fun `decision values map to outcome kinds`() {
        assertEquals(ApprovalOutcomeKind.ALLOWED_ONCE, ApprovalCoordinator.outcomeKindFor(ApprovalDecisionValue.ACCEPT))
        assertEquals(
            ApprovalOutcomeKind.ALLOWED_FOR_SESSION,
            ApprovalCoordinator.outcomeKindFor(ApprovalDecisionValue.ACCEPT_FOR_SESSION),
        )
        assertEquals(ApprovalOutcomeKind.DENIED, ApprovalCoordinator.outcomeKindFor(ApprovalDecisionValue.DECLINE))
        assertEquals(ApprovalOutcomeKind.ABORTED, ApprovalCoordinator.outcomeKindFor(ApprovalDecisionValue.CANCEL))
    }

    @Test
    fun `stop task is offered only where cancel really stops the task`() {
        assertTrue(ApprovalKind.COMMAND.offersStopTask)
        assertTrue(ApprovalKind.FILE_CHANGE.offersStopTask)
        // Rust answers CANCEL on a permissions request with an empty grant (same as DECLINE).
        assertFalse(ApprovalKind.PERMISSIONS.offersStopTask)
        assertFalse(ApprovalKind.MCP_ELICITATION.offersStopTask)
    }

    @Test
    fun `thread membership and phone answerability`() {
        assertTrue(approval("1").belongsTo(threadA))
        assertFalse(approval("1", threadId = "other").belongsTo(threadA))
        assertFalse(approval("1", threadId = null).belongsTo(threadA))
        assertTrue(approval("1").isAnswerableOnPhone)
        assertFalse(approval("1", kind = ApprovalKind.MCP_ELICITATION).isAnswerableOnPhone)
        assertEquals(threadA, approval("1").threadKeyOrNull)
        assertNull(approval("1", threadId = " ").threadKeyOrNull)
    }
}
