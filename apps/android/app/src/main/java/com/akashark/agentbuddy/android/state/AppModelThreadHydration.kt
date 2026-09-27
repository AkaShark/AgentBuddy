package com.akashark.agentbuddy.android.state

import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppReadThreadRequest

// Turn paging and background/deferred thread hydration for [AppModel]. Jobs
// run on the AppModel-owned `scope` so they survive recomposition.

internal fun AppModel.loadInitialTurnsImpl(key: ThreadKey, limit: UInt) {
    if (!initialTurnsLoadingKeys.add(key)) return
    scope.launch {
        try {
            val outcome = store.loadThreadTurnsPage(key, null, limit)
            LLog.i(
                "Pagination",
                "loadInitialTurns",
                fields = mapOf(
                    "threadId" to key.threadId,
                    "limit" to limit.toString(),
                    "loaded" to outcome.loaded.toString(),
                    "hasMore" to outcome.hasMore.toString(),
                ),
            )
            _lastError.value = null
        } catch (e: Exception) {
            LLog.w(
                "Pagination",
                "loadInitialTurns failed",
                fields = mapOf(
                    "threadId" to key.threadId,
                    "error" to (e.message ?: e.toString()),
                ),
            )
            _lastError.value = e.message
        } finally {
            initialTurnsLoadingKeys.remove(key)
        }
    }
}

internal fun AppModel.loadInitialTurnsIfNeededImpl(key: ThreadKey, limit: UInt) {
    _snapshot.value ?: return
    if (threadSnapshot(key)?.initialTurnsLoaded == true) return
    loadInitialTurns(key, limit)
}

internal fun AppModel.loadOlderTurnsImpl(key: ThreadKey, limit: UInt): Job {
    val cursor = threadSnapshot(key)?.olderTurnsCursor
    if (cursor == null || !olderTurnsLoadingKeys.add(key)) {
        return scope.launch { /* no-op */ }
    }
    return scope.launch {
        try {
            val outcome = store.loadThreadTurnsPage(key, cursor, limit)
            LLog.i(
                "Pagination",
                "loadOlderTurns",
                fields = mapOf(
                    "threadId" to key.threadId,
                    "cursor" to cursor,
                    "limit" to limit.toString(),
                    "loaded" to outcome.loaded.toString(),
                    "hasMore" to outcome.hasMore.toString(),
                ),
            )
            _lastError.value = null
        } catch (e: Exception) {
            LLog.w(
                "Pagination",
                "loadOlderTurns failed",
                fields = mapOf(
                    "threadId" to key.threadId,
                    "error" to (e.message ?: e.toString()),
                ),
            )
            _lastError.value = e.message
        } finally {
            olderTurnsLoadingKeys.remove(key)
        }
    }
}

internal suspend fun AppModel.refreshThreadSnapshotImpl(key: ThreadKey) {
    if (_snapshot.value == null) {
        refreshSnapshot()
        return
    }

    try {
        val threadSnapshot = store.threadSnapshot(key)
        if (threadSnapshot == null) {
            synchronized(snapshotLock) {
                if (cachedThreadSnapshots[key] == null) {
                    removeThreadSnapshot(key, clearCache = false)
                }
            }
            return
        }
        applyThreadSnapshot(threadSnapshot)
    } catch (e: Exception) {
        _lastError.value = e.message
        refreshSnapshot()
    }
}

internal fun AppModel.scheduleBackgroundThreadPermissionHydration(key: ThreadKey) {
    scope.launch {
        try {
            val nextKey = client.readThread(
                key.serverId,
                AppReadThreadRequest(
                    threadId = key.threadId,
                    includeTurns = false,
                ),
            )
            val threadSnapshot = store.threadSnapshot(nextKey)
            if (threadSnapshot != null) {
                applyThreadSnapshot(threadSnapshot)
                launchState.syncFromThread(threadSnapshot)
            } else {
                refreshSnapshot()
                launchState.syncFromThread(snapshot.value?.threads?.firstOrNull { it.key == nextKey })
            }
        } catch (e: Exception) {
            _lastError.value = e.message
        }
    }
}

internal fun AppModel.scheduleDeferredActiveThreadHydrationIfNeeded(key: ThreadKey?) {
    if (key == null) {
        pendingActiveThreadHydrationJob?.cancel()
        pendingActiveThreadHydrationJob = null
        pendingActiveThreadHydrationKey = null
        return
    }

    val thread = threadSnapshot(key)
    if (thread == null || !shouldAttemptDeferredHydration(thread)) {
        if (pendingActiveThreadHydrationKey == key) {
            pendingActiveThreadHydrationJob?.cancel()
            pendingActiveThreadHydrationJob = null
            pendingActiveThreadHydrationKey = null
        }
        return
    }

    if (pendingActiveThreadHydrationKey == key && pendingActiveThreadHydrationJob != null) {
        return
    }

    pendingActiveThreadHydrationJob?.cancel()
    pendingActiveThreadHydrationKey = key
    pendingActiveThreadHydrationJob = scope.launch {
        delay(300)
        hydrateActiveThreadIfNeeded(key)
    }
}

private suspend fun AppModel.hydrateActiveThreadIfNeeded(key: ThreadKey) {
    try {
        val current = snapshot.value
        val thread = threadSnapshot(key)
        if (current?.activeThread != key || thread == null || !shouldAttemptDeferredHydration(thread)) {
            return
        }

        val nextKey = client.readThread(
            key.serverId,
            AppReadThreadRequest(
                threadId = key.threadId,
                includeTurns = false,
            ),
        )
        val threadSnapshot = store.threadSnapshot(nextKey)
        if (threadSnapshot != null) {
            applyThreadSnapshot(threadSnapshot)
        } else {
            refreshThreadSnapshot(nextKey)
        }
    } catch (e: Exception) {
        _lastError.value = e.message
    } finally {
        if (pendingActiveThreadHydrationKey == key) {
            pendingActiveThreadHydrationJob = null
            pendingActiveThreadHydrationKey = null
        }
    }
}

private fun shouldAttemptDeferredHydration(thread: AppThreadSnapshot): Boolean {
    if (thread.hydratedConversationItems.isNotEmpty()) return false
    val preview = thread.info.preview?.trim().orEmpty()
    val title = thread.info.title?.trim().orEmpty()
    return preview.isNotEmpty() || title.isNotEmpty() || thread.hasActiveTurn
}
