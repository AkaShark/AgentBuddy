package com.akashark.agentbuddy.android.state

import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.ThreadStreamingDeltaKind
import uniffi.codex_mobile_client.AppStoreUpdateRecord
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.ThreadKey

// Internal event handling for [AppModel]: applies Rust `AppStore` updates
// (thread upserts, metadata, item changes, streaming deltas) to the snapshot.

internal suspend fun AppModel.handleUpdate(update: AppStoreUpdateRecord) {
    when (update) {
        is AppStoreUpdateRecord.ThreadUpserted ->
            applyThreadUpsert(update.thread, update.sessionSummary, update.agentDirectoryVersion)
        is AppStoreUpdateRecord.ThreadMetadataChanged ->
            applyThreadStateUpdated(update.state, update.sessionSummary, update.agentDirectoryVersion)
        is AppStoreUpdateRecord.ThreadItemChanged -> {
            if (!applyThreadItemChanged(update.key, update.item)) {
                recoverThreadDeltaApplication(update.key)
            }
            // Reducer piggybacks the refreshed per-thread summary on
            // every item change; patch our local session-summary cache
            // so home-list derived fields (stats, last tool label, etc.)
            // stay in sync with streaming items without waiting for a
            // full snapshot rebuild.
            applySessionSummary(update.sessionSummary)
        }
        is AppStoreUpdateRecord.ThreadStreamingDelta -> {
            if (!applyThreadStreamingDelta(update.key, update.itemId, update.kind, update.text)) {
                recoverThreadDeltaApplication(update.key)
            }
        }
        is AppStoreUpdateRecord.ThreadRemoved ->
            removeThreadSnapshot(update.key, update.agentDirectoryVersion)
        is AppStoreUpdateRecord.ActiveThreadChanged -> {
            updateActiveThread(update.key)
            if (update.key != null && threadSnapshot(update.key) == null) {
                refreshThreadSnapshot(update.key)
            }
            scheduleDeferredActiveThreadHydrationIfNeeded(update.key)
        }
        is AppStoreUpdateRecord.PendingApprovalsChanged -> refreshSnapshot()
        is AppStoreUpdateRecord.PendingUserInputsChanged -> refreshSnapshot()
        is AppStoreUpdateRecord.ServerChanged -> refreshSnapshot()
        is AppStoreUpdateRecord.ServerRemoved -> refreshSnapshot()
        is AppStoreUpdateRecord.FullResync -> refreshSnapshot()
        is AppStoreUpdateRecord.VoiceSessionChanged -> refreshSnapshot()
        is AppStoreUpdateRecord.RealtimeTranscriptUpdated -> Unit
        is AppStoreUpdateRecord.RealtimeHandoffRequested -> Unit
        is AppStoreUpdateRecord.RealtimeSpeechStarted -> Unit
        is AppStoreUpdateRecord.RealtimeStarted -> refreshSnapshot()
        is AppStoreUpdateRecord.RealtimeSdp -> Unit
        is AppStoreUpdateRecord.RealtimeOutputAudioDelta -> Unit
        is AppStoreUpdateRecord.RealtimeError -> refreshSnapshot()
        is AppStoreUpdateRecord.RealtimeClosed -> refreshSnapshot()
        is AppStoreUpdateRecord.SavedAppsChanged -> {
            // R3: Rust broadcasts this whenever the saved-apps index/HTML/
            // state changes (show_widget finalize, update, delete). Reload
            // the Kotlin mirror so home-row takeover and Apps list can
            // react without a full snapshot churn.
            try {
                SavedAppsStore.reload(appContext)
            } catch (_: Exception) {}
        }
        is AppStoreUpdateRecord.DynamicWidgetStreaming ->
            applyStreamingWidget(update.key, update.itemId, update.widget)
        is AppStoreUpdateRecord.TerminalSessionsChanged -> refreshSnapshot()
    }
}

/// Mutate an in-flight widget bubble's data so the timeline WebView
/// picks up the growing HTML via its existing pushWidgetContent path.
/// The reducer guarantees `isFinalized == false` on these; the
/// finalized update arrives separately as ThreadItemChanged and must
/// win.
private fun AppModel.applyStreamingWidget(
    key: ThreadKey,
    itemId: String,
    widget: uniffi.codex_mobile_client.HydratedWidgetData,
) {
    synchronized(snapshotLock) {
        val current = _snapshot.value ?: return
        val threadIndex = current.threads.indexOfFirst { it.key == key }
        if (threadIndex < 0) return
        val thread = current.threads[threadIndex]
        val itemIndex = thread.hydratedConversationItems.indexOfFirst { it.id == itemId }
        val updatedItems = thread.hydratedConversationItems.toMutableList()
        if (itemIndex >= 0) {
            val item = updatedItems[itemIndex]
            val content = item.content
            // Before the first delta the item is a generic DynamicToolCall
            // (no args → hydration returns None → item stays as tool-call).
            // Replace its content unconditionally with the hydrated widget,
            // except when it's already a finalized widget (stale delta).
            if (content is HydratedConversationItemContent.Widget) {
                if (content.v1.isFinalized) return
                if (content.v1 == widget) return
            }
            updatedItems[itemIndex] = item.copy(
                content = HydratedConversationItemContent.Widget(widget),
            )
        } else {
            // First delta raced ThreadItemStarted. Synthesize a placeholder
            // so the bubble appears now; the later ThreadItemStarted/Changed
            // will overwrite with the canonical hydrated item.
            updatedItems.add(
                HydratedConversationItem(
                    id = itemId,
                    content = HydratedConversationItemContent.Widget(widget),
                    sourceTurnId = thread.activeTurnId,
                    sourceTurnIndex = null,
                    timestamp = null,
                    isFromUserTurnBoundary = false,
                ),
            )
        }
        applyThreadSnapshot(thread.copy(hydratedConversationItems = updatedItems))
    }
}

private suspend fun AppModel.recoverThreadDeltaApplication(key: ThreadKey) {
    val current = _snapshot.value
    val threadMissing = current?.threads?.any { it.key == key } != true
    val summaryMissing = current?.sessionSummaries?.any { it.key == key } != true
    if (threadMissing && summaryMissing) {
        refreshSnapshot()
    } else {
        refreshThreadSnapshot(key)
    }
}

private fun AppModel.applyThreadUpsert(
    thread: AppThreadSnapshot,
    sessionSummary: AppSessionSummary,
    agentDirectoryVersion: ULong,
) {
    val nameByServerId = loadSavedServerNames()
    synchronized(snapshotLock) {
        val mergedThread = mergedThreadSnapshotPreservingHydratedItems(thread)
        val current = _snapshot.value ?: return
        val existingThreadIndex = current.threads.indexOfFirst { it.key == thread.key }

        // Race condition guard: during active streaming, if the old thread has
        // longer assistant text that starts with the new text, preserve the old
        // (more complete) text to avoid flickering backwards.
        val finalThread = if (existingThreadIndex >= 0) {
            val oldThread = current.threads[existingThreadIndex]
            if (oldThread.hasActiveTurn) {
                preserveStreamingText(oldThread, mergedThread)
            } else {
                mergedThread
            }
        } else {
            mergedThread
        }

        val updatedThreads = current.threads.toMutableList().apply {
            if (existingThreadIndex >= 0) {
                this[existingThreadIndex] = finalThread
            } else {
                add(finalThread)
            }
        }

        val adjustedSummary = applySavedServerName(sessionSummary, nameByServerId)
        val existingSummaryIndex = current.sessionSummaries.indexOfFirst { it.key == adjustedSummary.key }
        val updatedSummaries = current.sessionSummaries.toMutableList().apply {
            if (existingSummaryIndex >= 0) {
                this[existingSummaryIndex] = adjustedSummary
            } else {
                add(adjustedSummary)
            }
            sortWith(compareByDescending<AppSessionSummary> { it.updatedAt ?: Long.MIN_VALUE }
                .thenBy { it.key.serverId }
                .thenBy { it.key.threadId })
        }

        _snapshot.value = current.copy(
            threads = updatedThreads,
            sessionSummaries = updatedSummaries,
            agentDirectoryVersion = agentDirectoryVersion,
        )
        cacheThreadSnapshot(finalThread)
        _lastError.value = null
    }
}

private fun preserveStreamingText(
    oldThread: AppThreadSnapshot,
    newThread: AppThreadSnapshot,
): AppThreadSnapshot {
    if (newThread.hydratedConversationItems.isEmpty()) return newThread
    val oldItemsById = oldThread.hydratedConversationItems.associateBy { it.id }
    var changed = false
    val mergedItems = newThread.hydratedConversationItems.map { newItem ->
        val oldItem = oldItemsById[newItem.id]
        if (oldItem != null) {
            val oldText = assistantText(oldItem.content)
            val newText = assistantText(newItem.content)
            if (oldText != null && newText != null &&
                oldText.length > newText.length &&
                oldText.startsWith(newText)
            ) {
                changed = true
                oldItem
            } else {
                newItem
            }
        } else {
            newItem
        }
    }
    return if (changed) newThread.copy(hydratedConversationItems = mergedItems) else newThread
}

private fun assistantText(content: HydratedConversationItemContent): String? =
    when (content) {
        is HydratedConversationItemContent.Assistant -> content.v1.text
        else -> null
    }

private fun AppModel.applyThreadStateUpdated(
    state: uniffi.codex_mobile_client.AppThreadStateRecord,
    sessionSummary: AppSessionSummary,
    agentDirectoryVersion: ULong,
) {
    val nameByServerId = loadSavedServerNames()
    synchronized(snapshotLock) {
        val current = _snapshot.value ?: return
        val existingThreadIndex = current.threads.indexOfFirst { it.key == state.key }
        if (existingThreadIndex < 0) return

        val existingThread = current.threads[existingThreadIndex]
        val updatedThread = existingThread.copy(
            info = state.info,
            collaborationMode = state.collaborationMode,
            model = state.model,
            reasoningEffort = state.reasoningEffort,
            effectiveApprovalPolicy = state.effectiveApprovalPolicy,
            effectiveSandboxPolicy = state.effectiveSandboxPolicy,
            queuedFollowUps = state.queuedFollowUps,
            activeTurnId = state.activeTurnId,
            activePlanProgress = state.activePlanProgress,
            pendingPlanImplementationPrompt = state.pendingPlanImplementationPrompt,
            contextTokensUsed = state.contextTokensUsed,
            modelContextWindow = state.modelContextWindow,
            rateLimits = state.rateLimits,
            realtimeSessionId = state.realtimeSessionId,
            goal = state.goal,
            olderTurnsCursor = state.olderTurnsCursor,
            initialTurnsLoaded = state.initialTurnsLoaded,
        )
        val updatedThreads = current.threads.toMutableList().apply {
            this[existingThreadIndex] = updatedThread
        }

        val adjustedSummary = applySavedServerName(sessionSummary, nameByServerId)
        val existingSummaryIndex = current.sessionSummaries.indexOfFirst { it.key == adjustedSummary.key }
        val updatedSummaries = current.sessionSummaries.toMutableList().apply {
            if (existingSummaryIndex >= 0) {
                this[existingSummaryIndex] = adjustedSummary
            } else {
                add(adjustedSummary)
            }
            sortWith(compareByDescending<AppSessionSummary> { it.updatedAt ?: Long.MIN_VALUE }
                .thenBy { it.key.serverId }
                .thenBy { it.key.threadId })
        }

        _snapshot.value = current.copy(
            threads = updatedThreads,
            sessionSummaries = updatedSummaries,
            agentDirectoryVersion = agentDirectoryVersion,
        )
        cacheThreadSnapshot(updatedThread)
        _lastError.value = null
    }
}

private fun AppModel.applyThreadItemChanged(
    key: ThreadKey,
    item: HydratedConversationItem,
): Boolean {
    return synchronized(snapshotLock) {
        val current = _snapshot.value ?: return false
        val threadIndex = current.threads.indexOfFirst { it.key == key }
        if (threadIndex < 0) return false

        val thread = current.threads[threadIndex]
        val updatedItems = thread.hydratedConversationItems.toMutableList()
        val existingItemIndex = updatedItems.indexOfFirst { it.id == item.id }
        if (existingItemIndex >= 0) {
            updatedItems[existingItemIndex] = item
        } else {
            val insertionIndex = insertionIndexForItem(updatedItems, item)
            updatedItems.add(insertionIndex, item)
        }
        applyThreadSnapshot(thread.copy(hydratedConversationItems = updatedItems))
        true
    }
}

private fun AppModel.applyThreadStreamingDelta(
    key: ThreadKey,
    itemId: String,
    kind: ThreadStreamingDeltaKind,
    text: String,
): Boolean {
    return synchronized(snapshotLock) {
        val current = _snapshot.value ?: return false
        val threadIndex = current.threads.indexOfFirst { it.key == key }
        if (threadIndex < 0) return false

        val thread = current.threads[threadIndex]
        val itemIndex = thread.hydratedConversationItems.indexOfFirst { it.id == itemId }
        if (itemIndex < 0) return false

        val updatedContent = applyStreamingDelta(kind, text, thread.hydratedConversationItems[itemIndex].content)
            ?: return false
        val updatedItems = thread.hydratedConversationItems.toMutableList().apply {
            this[itemIndex] = this[itemIndex].copy(content = updatedContent)
        }
        applyThreadSnapshot(thread.copy(hydratedConversationItems = updatedItems))
        true
    }
}

private fun applyStreamingDelta(
    kind: ThreadStreamingDeltaKind,
    text: String,
    content: HydratedConversationItemContent,
): HydratedConversationItemContent? = when (kind) {
    ThreadStreamingDeltaKind.ASSISTANT_TEXT -> when (content) {
        is HydratedConversationItemContent.Assistant ->
            HydratedConversationItemContent.Assistant(content.v1.copy(text = content.v1.text + text))
        else -> null
    }
    ThreadStreamingDeltaKind.REASONING_TEXT -> when (content) {
        is HydratedConversationItemContent.Reasoning -> {
            val updatedContent = content.v1.content.toMutableList().apply {
                if (isEmpty()) {
                    add(text)
                } else {
                    this[lastIndex] = this[lastIndex] + text
                }
            }
            HydratedConversationItemContent.Reasoning(content.v1.copy(content = updatedContent))
        }
        else -> null
    }
    ThreadStreamingDeltaKind.PLAN_TEXT -> when (content) {
        is HydratedConversationItemContent.ProposedPlan ->
            HydratedConversationItemContent.ProposedPlan(content.v1.copy(content = content.v1.content + text))
        else -> null
    }
    ThreadStreamingDeltaKind.COMMAND_OUTPUT -> when (content) {
        is HydratedConversationItemContent.CommandExecution ->
            HydratedConversationItemContent.CommandExecution(
                content.v1.copy(output = (content.v1.output ?: "") + text)
            )
        else -> null
    }
    ThreadStreamingDeltaKind.MCP_PROGRESS -> when (content) {
        is HydratedConversationItemContent.McpToolCall -> {
            val updatedProgress = content.v1.progressMessages.toMutableList().apply {
                if (text.isNotBlank()) {
                    add(text)
                }
            }
            HydratedConversationItemContent.McpToolCall(
                content.v1.copy(progressMessages = updatedProgress)
            )
        }
        else -> null
    }
}

private fun insertionIndexForItem(
    items: List<HydratedConversationItem>,
    item: HydratedConversationItem,
): Int {
    val targetTurn = item.sourceTurnIndex?.toInt() ?: return items.size
    val lastSameTurn = items.indexOfLast { it.sourceTurnIndex?.toInt() == targetTurn }
    if (lastSameTurn >= 0) return lastSameTurn + 1

    val nextTurn = items.indexOfFirst {
        val sourceTurn = it.sourceTurnIndex?.toInt()
        sourceTurn != null && sourceTurn > targetTurn
    }
    return if (nextTurn >= 0) nextTurn else items.size
}
