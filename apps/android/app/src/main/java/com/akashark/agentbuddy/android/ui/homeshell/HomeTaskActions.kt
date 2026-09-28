package com.akashark.agentbuddy.android.ui.homeshell

import android.content.Context
import com.akashark.agentbuddy.android.state.AppComposerPayload
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.SavedThreadsStore
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.state.isConnected
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskList
import com.akashark.agentbuddy.android.ui.homeshell.tasks.HomeTaskPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppArchiveThreadRequest
import uniffi.codex_mobile_client.AppInterruptTurnRequest
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.ThreadKey

/**
 * Task actions of the home shell. Every server call goes through the Rust
 * `AppClient` / `AppStore`; this class only sequences them and keeps the
 * UI-only [HomeTaskMemory] in step. Errors are reported through [onError].
 */
class HomeTaskActions(
    private val appModel: AppModel,
    private val context: Context,
    private val scope: CoroutineScope,
    private val memory: HomeTaskMemory,
    private val onError: (title: String, message: String) -> Unit,
) {
    /** Opening keeps today's behaviour: remember the cwd, then navigate. */
    fun open(session: AppSessionSummary, navigate: (ThreadKey) -> Unit) {
        appModel.launchState.updateCurrentCwd(session.cwd)
        navigate(session.key)
    }

    /**
     * Pins a thread. The first pin replaces the automatic "recent" list, so
     * the recents it displaces are unsubscribed like before.
     */
    fun pin(key: ThreadKey, visible: List<AppSessionSummary>) {
        val displaced = if (memory.pinned.isEmpty()) visible.map { it.key }.filter { it != key } else emptyList()
        val pin = HomeTaskList.pinKey(key)
        SavedThreadsStore.add(context, pin)
        if (pin in memory.hidden) SavedThreadsStore.unhide(context, pin)
        memory.reload(context)
        if (displaced.isNotEmpty()) {
            scope.launch {
                displaced.distinct().forEach { runCatching { appModel.store.unsubscribeThread(it) } }
            }
        }
    }

    fun unpin(key: ThreadKey) {
        SavedThreadsStore.remove(context, HomeTaskList.pinKey(key))
        memory.reload(context)
    }

    fun hide(key: ThreadKey) {
        SavedThreadsStore.hide(context, HomeTaskList.pinKey(key))
        memory.reload(context)
        scope.launch { runCatching { appModel.store.unsubscribeThread(key) } }
    }

    /** Marks the task as stopping, then interrupts the active turn. */
    fun stop(key: ThreadKey) {
        val id = HomeTaskPresentation.taskId(key)
        memory.cancelling = memory.cancelling + id
        scope.launch {
            val turnId = appModel.threadSnapshot(key)?.activeTurnId
            if (turnId == null) {
                memory.cancelling = memory.cancelling - id
                return@launch
            }
            runCatching {
                appModel.client.interruptTurn(key.serverId, AppInterruptTurnRequest(threadId = key.threadId, turnId = turnId))
            }.onFailure { error ->
                memory.cancelling = memory.cancelling - id
                onError("停止失败", error.message ?: "无法停止这个任务。")
            }
        }
    }

    /** Head-of-thread fork: duplicates the thread server-side and opens the copy. */
    fun fork(session: AppSessionSummary, navigate: (ThreadKey) -> Unit) {
        scope.launch {
            try {
                navigate(forkSessionThread(appModel, session))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError("分叉失败", e.message ?: "分叉任务失败")
            }
        }
    }

    /** Deletes (archives) a thread after the user confirmed. */
    fun delete(key: ThreadKey) {
        scope.launch {
            val voice = VoiceRuntimeController.shared
            voice.stopVoiceSessionIfActive(appModel, key)
            voice.clearPinnedLocalVoiceThreadIfMatches(appModel, key)
            if (appModel.snapshot.value?.activeThread == key) appModel.store.setActiveThread(null)
            runCatching { appModel.client.archiveThread(key.serverId, AppArchiveThreadRequest(threadId = key.threadId)) }
            delay(400L)
            appModel.refreshSnapshot()
        }
    }

    suspend fun sendQuickReply(key: ThreadKey, text: String): Result<Unit> =
        runCatching { sendQuickReplyTurn(appModel, key, text) }

    /**
     * Attaches a live listener to every pinned thread that is not resumed yet
     * so its card streams without being opened (mirrors iOS `hydrateThread`).
     * The store short-circuits when a listener already exists.
     */
    fun hydratePinned(visible: List<AppSessionSummary>, servers: List<AppServerSnapshot>) {
        val byPin = visible.associateBy { HomeTaskList.pinKey(it.key) }
        val serversById = servers.associateBy { it.serverId }
        for (pin in memory.pinned) {
            val session = byPin[pin]
            if (session?.isResumed == true) continue
            val key = session?.key ?: ThreadKey(serverId = pin.serverId, threadId = pin.threadId)
            val id = HomeTaskPresentation.taskId(key)
            if (id in memory.hydrating) continue
            if (serversById[key.serverId]?.isConnected != true) continue
            memory.hydrating = memory.hydrating + id
            scope.launch {
                try {
                    var resumed = runCatching { appModel.externalResumeThread(key) }.isSuccess
                    if (!resumed) {
                        runCatching { appModel.refreshSessions(listOf(key.serverId)) }
                        resumed = runCatching { appModel.externalResumeThread(key) }.isSuccess
                    }
                    if (resumed) appModel.loadInitialTurnsIfNeeded(key)
                    appModel.refreshThreadSnapshot(key)
                } finally {
                    memory.hydrating = memory.hydrating - id
                }
            }
        }
    }
}

/**
 * Head-of-thread fork shared by home and 全部任务: duplicates the thread on its
 * server, makes the copy active and returns its key. Throws when the host
 * refuses or drops; callers report the error.
 */
suspend fun forkSessionThread(appModel: AppModel, session: AppSessionSummary): ThreadKey {
    val sourceKey = appModel.hydrateThreadPermissions(session.key) ?: session.key
    val newKey = appModel.client.forkThread(
        sourceKey.serverId,
        appModel.launchState.threadForkRequest(
            sourceThreadId = sourceKey.threadId,
            cwdOverride = session.cwd,
            threadKey = sourceKey,
        ),
    )
    appModel.store.setActiveThread(newKey)
    appModel.refreshThreadSnapshot(newKey)
    appModel.launchState.updateCurrentCwd(session.cwd)
    return newKey
}

/**
 * Quick-reply send path shared by the swipe and the 「回复」 menu. The thread
 * is resumed first so the server can find it when the home list came from a
 * cold-launch snapshot. Mirrors iOS `AgentBuddyApp.swift` quick reply.
 */
suspend fun sendQuickReplyTurn(
    appModel: AppModel,
    threadKey: ThreadKey,
    text: String,
) {
    val resumeKey = appModel.hydrateThreadPermissions(threadKey) ?: threadKey
    try {
        appModel.externalResumeThread(resumeKey)
    } catch (_: Exception) {
        val cwdOverride = appModel.threadSnapshot(resumeKey)?.info?.cwd
        appModel.client.resumeThread(
            resumeKey.serverId,
            appModel.launchState.threadResumeRequest(resumeKey.threadId, cwdOverride = cwdOverride, threadKey = resumeKey),
        )
    }
    val payload = AppComposerPayload(
        text = text,
        additionalInputs = emptyList(),
        approvalPolicy = appModel.launchState.approvalPolicyValue(resumeKey),
        sandboxPolicy = appModel.launchState.turnSandboxPolicy(resumeKey),
        model = appModel.launchState.snapshot.value.selectedModel.trim().ifEmpty { null },
        reasoningEffort = null,
        serviceTier = null,
    )
    appModel.startTurn(resumeKey, payload)
    appModel.refreshThreadSnapshot(resumeKey)
}
