package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.ui.conversation.responseSubmissionErrorMessage
import kotlinx.coroutines.CancellationException
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.ThreadKey

/** What the user last saw happen to an approval on a thread (UI-only; task state stays in Rust). */
enum class ApprovalOutcomeKind {
    ALLOWED_ONCE,
    ALLOWED_FOR_SESSION,
    DENIED,
    ABORTED,

    /** The request left the queue without a decision from this phone (answered elsewhere or expired). */
    RESOLVED_ELSEWHERE,
}

data class ApprovalOutcome(
    val approvalId: String,
    val kind: ApprovalOutcomeKind,
    val approvalKind: ApprovalKind,
    val timestampMillis: Long,
)

/** A decision that could not be sent; [decision] is what "重试" sends again. */
data class ApprovalFailure(
    val message: String,
    val decision: ApprovalDecisionValue,
)

/**
 * UI-side bookkeeping for approval decisions, ported from iOS
 * `ApprovalCoordinator`: one submission in flight per request id (repeat taps
 * are ignored), visible failures with retry, and the latest outcome per
 * thread. The decision itself always goes to the shared Rust store through
 * the responder; this type never marks a request resolved on its own — a
 * request only disappears when the snapshot stops listing it.
 */
class ApprovalCoordinator(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Request ids with a decision in flight, and the decision being sent. */
    var submitting: Map<String, ApprovalDecisionValue> by mutableStateOf(emptyMap())
        private set

    /** Last submission error per request id. */
    var failures: Map<String, ApprovalFailure> by mutableStateOf(emptyMap())
        private set

    /** Most recent outcome per thread, keyed by [outcomeKey]. */
    var outcomes: Map<String, ApprovalOutcome> by mutableStateOf(emptyMap())
        private set

    private var lastPending: Map<String, PendingApproval> = emptyMap()
    private val decidedLocally = mutableSetOf<String>()

    fun isSubmitting(approvalId: String): Boolean = submitting.containsKey(approvalId)

    /**
     * Sends [decision] through [respond]. Returns false without calling
     * [respond] when a decision for this request is already in flight.
     */
    suspend fun submit(
        approval: PendingApproval,
        decision: ApprovalDecisionValue,
        respond: suspend (requestId: String, decision: ApprovalDecisionValue) -> Unit,
    ): Boolean {
        val id = approval.id
        if (submitting.containsKey(id)) return false
        submitting = submitting + (id to decision)
        failures = failures - id
        try {
            respond(id, decision)
            recordOutcome(approval, outcomeKindFor(decision))
            // If the snapshot already dropped the request while we were
            // waiting, reconcile has run and there is nothing left to match.
            if (lastPending.containsKey(id)) decidedLocally += id
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (lastPending.containsKey(id)) {
                failures = failures + (id to ApprovalFailure(responseSubmissionErrorMessage(error), decision))
            } else {
                // Gone upstream and our answer did not land: someone else handled it.
                recordOutcome(approval, ApprovalOutcomeKind.RESOLVED_ELSEWHERE)
            }
            return false
        } finally {
            submitting = submitting - id
        }
    }

    /**
     * Call on every snapshot change with the phone-answerable pending
     * approvals. A request that leaves the queue without a local decision
     * becomes [ApprovalOutcomeKind.RESOLVED_ELSEWHERE] for its thread.
     */
    fun reconcile(pending: List<PendingApproval>) {
        val current = LinkedHashMap<String, PendingApproval>()
        pending.forEach { current.putIfAbsent(it.id, it) }
        var nextFailures = failures
        var nextOutcomes = outcomes
        for ((id, approval) in lastPending) {
            if (current.containsKey(id)) continue
            nextFailures = nextFailures - id
            val decided = decidedLocally.remove(id)
            // A decision still in flight records its own outcome when it returns.
            if (!decided && !submitting.containsKey(id)) {
                approval.outcomeKey()?.let { key ->
                    nextOutcomes = nextOutcomes +
                        (key to ApprovalOutcome(id, ApprovalOutcomeKind.RESOLVED_ELSEWHERE, approval.kind, clock()))
                }
            }
        }
        if (nextFailures != failures) failures = nextFailures
        if (nextOutcomes != outcomes) outcomes = nextOutcomes
        lastPending = current
    }

    fun outcome(threadKey: ThreadKey): ApprovalOutcome? = outcomes[outcomeKey(threadKey)]

    fun dismissOutcome(threadKey: ThreadKey) {
        val key = outcomeKey(threadKey)
        if (outcomes.containsKey(key)) outcomes = outcomes - key
    }

    private fun recordOutcome(approval: PendingApproval, kind: ApprovalOutcomeKind) {
        val key = approval.outcomeKey() ?: return
        outcomes = outcomes + (key to ApprovalOutcome(approval.id, kind, approval.kind, clock()))
    }

    companion object {
        fun outcomeKey(threadKey: ThreadKey): String = "${threadKey.serverId}/${threadKey.threadId}"

        fun outcomeKindFor(decision: ApprovalDecisionValue): ApprovalOutcomeKind =
            when (decision) {
                ApprovalDecisionValue.ACCEPT -> ApprovalOutcomeKind.ALLOWED_ONCE
                ApprovalDecisionValue.ACCEPT_FOR_SESSION -> ApprovalOutcomeKind.ALLOWED_FOR_SESSION
                ApprovalDecisionValue.DECLINE -> ApprovalOutcomeKind.DENIED
                ApprovalDecisionValue.CANCEL -> ApprovalOutcomeKind.ABORTED
            }
    }
}

private fun PendingApproval.outcomeKey(): String? =
    threadId?.trim()?.takeIf { it.isNotEmpty() }?.let { "$serverId/$it" }

/**
 * Requests the phone can render and answer. MCP elicitations carry no form
 * schema in [PendingApproval], so they stay with the host UI (same as iOS).
 */
val PendingApproval.isAnswerableOnPhone: Boolean
    get() = kind != ApprovalKind.MCP_ELICITATION

fun PendingApproval.belongsTo(key: ThreadKey): Boolean =
    serverId == key.serverId && threadId?.trim() == key.threadId

/** The thread this request belongs to, when the host reported one. */
val PendingApproval.threadKeyOrNull: ThreadKey?
    get() = threadId?.trim()?.takeIf { it.isNotEmpty() }?.let { ThreadKey(serverId = serverId, threadId = it) }
