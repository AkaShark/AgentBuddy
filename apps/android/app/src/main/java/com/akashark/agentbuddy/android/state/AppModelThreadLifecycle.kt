package com.akashark.agentbuddy.android.state

import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AppSortDirection
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.AppThreadSortKey
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppListThreadsRequest
import uniffi.codex_mobile_client.AppReadThreadRequest
import uniffi.codex_mobile_client.AppStartThreadRequest
import uniffi.codex_mobile_client.threadPermissionsAreAuthoritative
import com.akashark.agentbuddy.android.state.AppModel.Companion.INITIAL_TURN_PAGE_LIMIT
import com.akashark.agentbuddy.android.state.AppModel.Companion.SESSION_LIST_PAGE_LIMIT

// Thread lifecycle operations behind [AppModel]'s public API: permission
// hydration, start/resume, authoritative refresh and ensure-loaded retries.

internal suspend fun AppModel.hydrateThreadPermissionsImpl(key: ThreadKey): ThreadKey? {
    val existing = threadSnapshot(key)
    if (existing != null && hasAuthoritativePermissions(existing)) {
        launchState.syncFromThread(existing)
        return key
    }

    if (existing != null) {
        launchState.syncFromThread(existing)
        scheduleBackgroundThreadPermissionHydration(key)
        return key
    }

    if (snapshot.value?.sessionSummaries?.any { it.key == key } == true) {
        scheduleBackgroundThreadPermissionHydration(key)
        return key
    }

    return try {
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
        nextKey
    } catch (e: Exception) {
        _lastError.value = e.message
        null
    }
}

internal suspend fun AppModel.startThreadImpl(
    serverId: String,
    params: AppStartThreadRequest,
): ThreadKey {
    if (!ensureLocalAuthForThreadStart(serverId)) {
        throw LocalAccountLoginRequiredException(serverId)
    }
    return client.startThread(serverId, params)
}

internal suspend fun AppModel.startTurnImpl(
    key: ThreadKey,
    payload: AppComposerPayload,
) {
    restoreStoredLocalAuthIfNeeded(key.serverId, reason = "startTurn")

    try {
        store.startTurn(key, payload.toAppStartTurnRequest(key.threadId))
        _lastError.value = null
    } catch (e: Exception) {
        _lastError.value = e.message
        throw e
    }
}

internal suspend fun AppModel.externalResumeThreadImpl(
    key: ThreadKey,
    hostId: String?,
) {
    restoreStoredLocalAuthIfNeeded(key.serverId, reason = "resumeThread")

    try {
        store.externalResumeThread(key, hostId)
        _lastError.value = null
    } catch (e: Exception) {
        _lastError.value = e.message
        throw e
    }
}

internal suspend fun AppModel.forceRefreshThreadAuthoritativeImpl(key: ThreadKey) {
    restoreStoredLocalAuthIfNeeded(key.serverId, reason = "forceRefreshAuthoritative")
    try {
        store.forceRefreshThreadAuthoritative(key)
        _lastError.value = null
    } catch (e: Exception) {
        _lastError.value = e.message
        throw e
    }
}

internal suspend fun AppModel.refreshThreadIncludingTurnsImpl(key: ThreadKey): ThreadKey {
    try {
        // 1. Refresh thread metadata only — title, status, model,
        //    active_turn_id, etc. The full historical turn list is
        //    append-only on the server, so we don't need to re-pull it
        //    here. Sending `includeTurns = true` would have the server
        //    reconstruct the entire rollout in one response, which is
        //    unbounded and OOMs the device on long threads.
        val nextKey = client.readThread(
            key.serverId,
            AppReadThreadRequest(
                threadId = key.threadId,
                includeTurns = false,
            ),
        )
        // 2. Reload the most-recent N turns via the paginated path. On
        //    v0.125+ remotes this hits `thread/turns/list`; on older
        //    remotes that don't implement it, the Rust client falls back
        //    to `thread/resume(excludeTurns: false)` and pulls the
        //    embedded turn list — preserving the prior reload behavior
        //    for legacy servers.
        try {
            store.loadThreadTurnsPage(nextKey, null, INITIAL_TURN_PAGE_LIMIT)
        } catch (e: Exception) {
            LLog.w(
                "Pagination",
                "refreshThreadIncludingTurns: initial-turn page load failed",
                fields = mapOf(
                    "threadId" to nextKey.threadId,
                    "error" to (e.message ?: e.toString()),
                ),
            )
        }
        val threadSnapshot = store.threadSnapshot(nextKey)
        if (threadSnapshot != null) {
            applyThreadSnapshot(threadSnapshot)
        } else {
            refreshThreadSnapshot(nextKey)
        }
        _lastError.value = null
        return nextKey
    } catch (e: Exception) {
        _lastError.value = e.message
        throw e
    }
}

internal suspend fun AppModel.ensureThreadLoadedImpl(
    key: ThreadKey,
    maxAttempts: Int,
): ThreadKey? {
    if (threadSnapshot(key) != null) {
        return key
    }

    var currentKey = key
    repeat(maxAttempts) { attempt ->
        var readSucceeded = false
        try {
            externalResumeThread(currentKey, null)
            store.setActiveThread(currentKey)
            readSucceeded = true
        } catch (e: Exception) {
            _lastError.value = e.message
        }

        if (readSucceeded) {
            refreshLoadedThreadSnapshot(currentKey)
            if (threadSnapshot(currentKey) != null) {
                return currentKey
            }
        }

        if (!readSucceeded) {
            try {
                client.listThreads(
                    currentKey.serverId,
                    AppListThreadsRequest(
                        cursor = null,
                        limit = SESSION_LIST_PAGE_LIMIT,
                        sortKey = AppThreadSortKey.UPDATED_AT,
                        sortDirection = AppSortDirection.DESC,
                        archived = null,
                        cwd = null,
                        searchTerm = null,
                        useStateDbOnly = false,
                        runtimeKinds = null,
                    ),
                )
            } catch (e: Exception) {
                _lastError.value = e.message
            }

            refreshLoadedThreadSnapshot(currentKey)
            if (threadSnapshot(currentKey) != null) {
                return currentKey
            }
        }

        if (attempt + 1 < maxAttempts) {
            delay(250)
        }
    }

    val activeKey = _snapshot.value?.activeThread
    if (activeKey != null &&
        activeKey.serverId == currentKey.serverId &&
        threadSnapshot(activeKey) != null
    ) {
        return activeKey
    }

    return null
}

private suspend fun AppModel.refreshLoadedThreadSnapshot(key: ThreadKey) {
    try {
        val thread = store.threadSnapshot(key)
        if (thread != null) {
            applyThreadSnapshot(thread)
        } else {
            refreshSnapshot()
        }
    } catch (e: Exception) {
        _lastError.value = e.message
        refreshSnapshot()
    }
}

private fun hasAuthoritativePermissions(thread: AppThreadSnapshot): Boolean =
    threadPermissionsAreAuthoritative(
        approvalPolicy = thread.effectiveApprovalPolicy,
        sandboxPolicy = thread.effectiveSandboxPolicy,
    )
