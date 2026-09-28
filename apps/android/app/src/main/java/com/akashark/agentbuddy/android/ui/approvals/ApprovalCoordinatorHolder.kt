package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.akashark.agentbuddy.android.state.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.ThreadKey

/**
 * One [ApprovalCoordinator] for the whole process, keyed to the [AppModel]
 * that owns the snapshot. It reconciles against every snapshot so a request
 * answered on another device is reported even while no approval UI is on
 * screen, and decisions run in a process scope so leaving the screen never
 * cancels a decision halfway.
 */
internal object ApprovalCoordinatorHolder {
    val scope: CoroutineScope = MainScope()

    private var owner: AppModel? = null
    private var coordinator: ApprovalCoordinator? = null
    private var reconcileJob: Job? = null

    /** Main thread only. */
    fun coordinatorFor(appModel: AppModel): ApprovalCoordinator {
        coordinator?.takeIf { owner === appModel }?.let { return it }
        reconcileJob?.cancel()
        val created = ApprovalCoordinator()
        owner = appModel
        coordinator = created
        reconcileJob = scope.launch {
            appModel.snapshot.collect { snapshot ->
                created.reconcile(snapshot?.pendingApprovals.orEmpty().filter { it.isAnswerableOnPhone })
            }
        }
        return created
    }
}

/** The shared approval coordinator (one per process / [AppModel]). */
@Composable
fun rememberApprovalCoordinator(appModel: AppModel): ApprovalCoordinator =
    remember(appModel) { ApprovalCoordinatorHolder.coordinatorFor(appModel) }

/** Sends [decision] for [approval] to the Rust store; repeat taps are ignored by the coordinator. */
internal fun submitApprovalDecision(
    appModel: AppModel,
    coordinator: ApprovalCoordinator,
    approval: PendingApproval,
    decision: ApprovalDecisionValue,
) {
    ApprovalCoordinatorHolder.scope.launch {
        coordinator.submit(approval, decision) { requestId, value ->
            appModel.store.respondToApproval(requestId, value)
        }
    }
}

/**
 * Threads whose in-conversation approval stack is on screen right now. The
 * app-level banner skips their requests, so a request is never shown twice.
 * Snapshot state (unlike `VisibleThreadTracker`) so the banner updates as
 * soon as a conversation appears or leaves.
 */
internal object ApprovalStackVisibility {
    private val visible = mutableStateMapOf<ThreadKey, Int>()

    fun isShowing(key: ThreadKey): Boolean = (visible[key] ?: 0) > 0

    fun register(key: ThreadKey) {
        visible[key] = (visible[key] ?: 0) + 1
    }

    fun unregister(key: ThreadKey) {
        val next = (visible[key] ?: 0) - 1
        if (next > 0) visible[key] = next else visible.remove(key)
    }
}

/** Marks [threadKey]'s approval stack as on screen while this is composed. */
@Composable
internal fun RegisterApprovalStackVisible(threadKey: ThreadKey) {
    DisposableEffect(threadKey) {
        ApprovalStackVisibility.register(threadKey)
        onDispose { ApprovalStackVisibility.unregister(threadKey) }
    }
}
