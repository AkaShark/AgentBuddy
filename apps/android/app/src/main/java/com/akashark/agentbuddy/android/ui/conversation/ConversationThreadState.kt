package com.akashark.agentbuddy.android.ui.conversation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.akashark.agentbuddy.android.state.AppModel
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.ThreadKey

@Composable
internal fun ConversationRenderPrewarm(
    appModel: AppModel,
    context: Context,
) {
    // Pre-warm Markwon and MessageParser on conversation open
    val warmMarkwon = remember(context) {
        try {
            val prism4j = io.noties.prism4j.Prism4j(com.akashark.agentbuddy.android.ui.Prism4jGrammarLocator())
            io.noties.markwon.Markwon.builder(context)
                .usePlugin(io.noties.markwon.syntax.SyntaxHighlightPlugin.create(prism4j, io.noties.markwon.syntax.Prism4jThemeDarkula.create()))
                .build()
        } catch (_: Exception) {
            io.noties.markwon.Markwon.create(context)
        }
    }
    LaunchedEffect(Unit) {
        // Trigger a lightweight parse to JIT-warm the Rust MessageParser
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            appModel.parser.extractRenderBlocksTyped("")
        }
    }
}

@Composable
internal fun ConversationThreadEffects(
    appModel: AppModel,
    threadKey: ThreadKey,
    thread: AppThreadSnapshot?,
) {
    // Reuse already-loaded thread content on re-entry, and only fall back to
    // resume/read flows when the conversation isn't available locally yet.
    LaunchedEffect(threadKey) {
        appModel.dismissMinigame()
        try {
            val resolvedThreadKey = appModel.hydrateThreadPermissions(threadKey) ?: threadKey
            appModel.activateThread(resolvedThreadKey)
            // Always call externalResumeThread so the server attaches a
            // streaming listener for this connection. Rust skips the RPC when
            // state is already fresh.
            try {
                appModel.externalResumeThread(resolvedThreadKey)
            } catch (_: Exception) {
                // Fall back to client.resumeThread for servers that need
                // launch config overrides.
                val cwdOverride = appModel.threadSnapshot(resolvedThreadKey)?.info?.cwd
                appModel.client.resumeThread(
                    resolvedThreadKey.serverId,
                    appModel.launchState.threadResumeRequest(
                        resolvedThreadKey.threadId,
                        cwdOverride = cwdOverride,
                        threadKey = resolvedThreadKey,
                    ),
                )
                appModel.refreshThreadSnapshot(resolvedThreadKey)
            }
            if (appModel.threadSnapshot(resolvedThreadKey) == null) {
                appModel.ensureThreadLoaded(resolvedThreadKey)
            }
            appModel.loadConversationMetadataIfNeeded(resolvedThreadKey.serverId)
        } catch (_: Exception) {}
    }

    LaunchedEffect(
        thread?.info?.cwd,
        thread?.effectiveApprovalPolicy,
        thread?.effectiveSandboxPolicy,
    ) {
        appModel.launchState.syncFromThread(thread)
    }

    // Initial turn load. Runs on the AppModel scope so it
    // survives recomposition — when Rust applies the page it flips
    // `initialTurnsLoaded` to true, which recomposes this view and would
    // otherwise cancel a LaunchedEffect mid-RPC. Rust owns the pagination
    // capability decision and falls back to embedded resume turns when needed.
    LaunchedEffect(threadKey, thread?.initialTurnsLoaded) {
        if (thread != null && !thread.initialTurnsLoaded) {
            appModel.loadInitialTurnsIfNeeded(threadKey)
        }
    }
}

internal fun conversationTranscriptTailSignature(
    items: List<HydratedConversationItem>,
    normalizedActiveTurnId: String?,
    isThinking: Boolean,
): Int {
    var hash = 17
    items.takeLast(4).forEach { item ->
        hash = 31 * hash + item.hashCode()
    }
    hash = 31 * hash + items.size
    hash = 31 * hash + (normalizedActiveTurnId?.hashCode() ?: 0)
    hash = 31 * hash + if (isThinking) 1 else 0
    return hash
}

internal fun conversationActiveTaskSummary(
    items: List<HydratedConversationItem>,
): ActiveTaskSummary? =
    items.asReversed().firstNotNullOfOrNull { item ->
        val content = item.content as? HydratedConversationItemContent.TodoList ?: return@firstNotNullOfOrNull null
        val steps = content.v1.steps
        if (steps.isEmpty()) return@firstNotNullOfOrNull null

        val activeSteps = steps.filter {
            it.status != uniffi.codex_mobile_client.HydratedPlanStepStatus.COMPLETED
        }
        if (activeSteps.isEmpty()) return@firstNotNullOfOrNull null

        val completed = steps.count {
            it.status == uniffi.codex_mobile_client.HydratedPlanStepStatus.COMPLETED
        }
        val focusStep = steps.firstOrNull {
            it.status == uniffi.codex_mobile_client.HydratedPlanStepStatus.IN_PROGRESS
        } ?: steps.firstOrNull {
            it.status == uniffi.codex_mobile_client.HydratedPlanStepStatus.PENDING
        } ?: activeSteps.firstOrNull()
        val detail = focusStep?.step?.trim().orEmpty()

        ActiveTaskSummary(
            progress = "$completed/${steps.size}",
            label = detail.ifBlank {
                if (activeSteps.size == 1) "1 个进行中的任务" else "${activeSteps.size} 个进行中的任务"
            },
        )
    }
