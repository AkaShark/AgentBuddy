package com.akashark.agentbuddy.android.state

import kotlinx.coroutines.sync.withLock
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.AppSortDirection
import uniffi.codex_mobile_client.AppThreadSortKey
import uniffi.codex_mobile_client.AppThreadSourceKind
import uniffi.codex_mobile_client.AppListThreadsRequest
import uniffi.codex_mobile_client.AppRefreshModelsRequest
import com.akashark.agentbuddy.android.state.AppModel.Companion.SESSION_LIST_PAGE_LIMIT

// Session-list, thread-search and per-server conversation metadata (models,
// rate limits) refreshes behind [AppModel]'s public API.

internal suspend fun AppModel.refreshSessionsImpl(serverIds: Collection<String>?) {
    val targetServerIds = (serverIds?.toList() ?: snapshot.value?.servers
        ?.filter { it.isConnected }
        ?.map { it.serverId }
        .orEmpty())
        .distinct()

    if (targetServerIds.isEmpty()) {
        return
    }

    sessionListMutex.withLock {
        try {
            for (serverId in targetServerIds) {
                client.listThreads(
                    serverId,
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
            }
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message
            throw e
        }
    }
}

internal suspend fun AppModel.refreshThreadSearchSessionsImpl(
    query: String,
    runtimeKind: AgentRuntimeKind?,
    forceRepair: Boolean,
) {
    val trimmedQuery = query.trim()
    val servers = snapshot.value?.servers
        ?.filter { it.isConnected }
        .orEmpty()
    val targetServerIds = servers
        .filter { server ->
            runtimeKind == null || server.agentRuntimes.any {
                it.available && it.kind == runtimeKind
            }
        }
        .map { it.serverId }
        .distinct()

    if (targetServerIds.isEmpty()) {
        return
    }

    sessionListMutex.withLock {
        try {
            for (serverId in targetServerIds) {
                client.listThreads(
                    serverId,
                    AppListThreadsRequest(
                        cursor = null,
                        limit = 80u,
                        sortKey = AppThreadSortKey.UPDATED_AT,
                        sortDirection = AppSortDirection.DESC,
                        modelProviders = null,
                        sourceKinds = listOf(
                            AppThreadSourceKind.CLI,
                            AppThreadSourceKind.VS_CODE,
                            AppThreadSourceKind.APP_SERVER,
                        ),
                        archived = false,
                        cwd = null,
                        searchTerm = trimmedQuery.ifEmpty { null },
                        useStateDbOnly = !forceRepair,
                        runtimeKinds = runtimeKind?.let { listOf(it) },
                    ),
                )
            }
            _lastError.value = null
        } catch (e: Exception) {
            _lastError.value = e.message
            throw e
        }
    }
}

internal suspend fun AppModel.loadConversationMetadataIfNeededImpl(serverId: String) {
    if (hasFreshConversationMetadata(serverId)) return
    loadAvailableModelsIfNeeded(serverId)
    loadRateLimitsIfNeeded(serverId)
    recentConversationMetadataLoads[serverId] = System.currentTimeMillis()
}

internal suspend fun AppModel.loadAvailableModelsIfNeededImpl(serverId: String) {
    val server = snapshot.value?.servers?.firstOrNull { it.serverId == serverId } ?: return
    if (!server.isConnected) return
    if (server.availableModels != null) return
    if (!loadingModelServerIds.add(serverId)) return
    try {
        client.refreshModels(
            serverId,
            AppRefreshModelsRequest(cursor = null, limit = null, includeHidden = false),
        )
        refreshSnapshot()
    } catch (e: Exception) {
        _lastError.value = e.message
    } finally {
        loadingModelServerIds.remove(serverId)
    }
}

internal suspend fun AppModel.loadRateLimitsIfNeededImpl(serverId: String) {
    val server = snapshot.value?.servers?.firstOrNull { it.serverId == serverId } ?: return
    if (!server.isConnected) return
    if (server.account == null) return
    if (server.rateLimits != null) return
    if (!loadingRateLimitServerIds.add(serverId)) return
    try {
        client.refreshRateLimits(serverId)
        refreshSnapshot()
    } catch (e: Exception) {
        _lastError.value = e.message
    } finally {
        loadingRateLimitServerIds.remove(serverId)
    }
}

private fun AppModel.hasFreshConversationMetadata(serverId: String): Boolean {
    val server = snapshot.value?.servers?.firstOrNull { it.serverId == serverId } ?: return false
    val hasModels = server.availableModels != null
    val hasRateLimits = server.account == null || server.rateLimits != null
    if (hasModels && hasRateLimits) return true

    val lastLoad = recentConversationMetadataLoads[serverId] ?: return false
    return System.currentTimeMillis() - lastLoad < 10_000L
}
