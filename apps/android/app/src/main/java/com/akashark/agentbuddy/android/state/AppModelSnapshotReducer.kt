package com.akashark.agentbuddy.android.state

import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.ThreadKey

// Snapshot application for [AppModel]: saved-server-name overlay, wake-MAC
// persistence, per-thread snapshot patches and the thread snapshot cache.

internal fun AppModel.applySnapshot(snapshot: AppSnapshotRecord?) {
    // Compute/refresh the saved-name cache before taking snapshotLock so a
    // cache miss (SharedPreferences read + JSON parse) never happens while
    // main-thread writers are blocked on the lock.
    val nameByServerId = loadSavedServerNames()
    val merged = synchronized(snapshotLock) {
        val merged = snapshot
            ?.let { applySavedServerNames(it, nameByServerId) }
            ?.let(::mergeCachedThreadSnapshots)
        _snapshot.value = merged
        merged?.threads?.forEach(::cacheThreadSnapshot)
        merged
    }
    if (merged != null) {
        persistWakeMacs(merged)
        _lastError.value = null
    }
}

private fun AppModel.persistWakeMacs(snapshot: AppSnapshotRecord) {
    val entries = snapshot.servers.map { Triple(it.serverId, it.host, it.wakeMac) }
    // Skip the SharedPreferences read/parse entirely when neither the
    // servers' wake MACs nor the saved-server store changed since the
    // last pass.
    if (lastPersistedWakeMacs == SavedServerStore.version to entries) return
    entries.forEach { (serverId, host, wakeMac) ->
        SavedServerStore.updateWakeMac(
            context = appContext,
            serverId = serverId,
            host = host,
            wakeMac = wakeMac,
        )
    }
    lastPersistedWakeMacs = SavedServerStore.version to entries
}

/** Saved server names by id, re-parsed only when [SavedServerStore] changes. */
internal fun AppModel.loadSavedServerNames(): Map<String, String> {
    val version = SavedServerStore.version
    savedServerNamesCache?.let { (cachedVersion, names) ->
        if (cachedVersion == version) return names
    }
    val names = SavedServerStore.load(appContext)
        .mapNotNull { server ->
            val trimmed = server.name.trim()
            if (trimmed.isEmpty()) null else server.id to trimmed
        }
        .toMap()
    savedServerNamesCache = version to names
    return names
}

private fun applySavedServerNames(
    snapshot: AppSnapshotRecord,
    nameByServerId: Map<String, String>,
): AppSnapshotRecord {
    if (nameByServerId.isEmpty()) return snapshot

    return snapshot.copy(
        servers = snapshot.servers.map { server ->
            val savedName = nameByServerId[server.serverId]
            if (savedName != null && savedName != server.displayName) {
                server.copy(displayName = savedName)
            } else {
                server
            }
        },
        sessionSummaries = snapshot.sessionSummaries.map { summary ->
            val savedName = nameByServerId[summary.key.serverId]
            if (savedName != null && savedName != summary.serverDisplayName) {
                summary.copy(serverDisplayName = savedName)
            } else {
                summary
            }
        },
    )
}

internal fun AppModel.applySavedServerName(
    summary: AppSessionSummary,
    nameByServerId: Map<String, String>,
): AppSessionSummary {
    val savedName = nameByServerId[summary.key.serverId] ?: return summary
    return if (savedName != summary.serverDisplayName) {
        summary.copy(serverDisplayName = savedName)
    } else {
        summary
    }
}

/// Patch a single `AppSessionSummary` in the snapshot. Called whenever
/// the reducer emits a per-item summary update on `threadItemChanged`,
/// so home-list derived fields track streaming items without waiting
/// for a full snapshot rebuild.
internal fun AppModel.applySessionSummary(summary: AppSessionSummary) {
    val nameByServerId = loadSavedServerNames()
    synchronized(snapshotLock) {
        val current = _snapshot.value ?: return
        val adjusted = applySavedServerName(summary, nameByServerId)
        val existingIndex = current.sessionSummaries.indexOfFirst { it.key == adjusted.key }
        val updatedSummaries = current.sessionSummaries.toMutableList().apply {
            if (existingIndex >= 0) {
                this[existingIndex] = adjusted
            } else {
                add(adjusted)
            }
        }
        _snapshot.value = current.copy(sessionSummaries = updatedSummaries)
    }
}

internal fun AppModel.applyThreadSnapshot(thread: AppThreadSnapshot) {
    synchronized(snapshotLock) {
        val mergedThread = mergedThreadSnapshotPreservingHydratedItems(thread)
        val current = _snapshot.value
        if (current == null) {
            cacheThreadSnapshot(mergedThread)
            return
        }
        val existingIndex = current.threads.indexOfFirst { it.key == thread.key }
        val updatedThreads = current.threads.toMutableList().apply {
            if (existingIndex >= 0) {
                this[existingIndex] = mergedThread
            } else {
                add(mergedThread)
            }
        }
        _snapshot.value = current.copy(threads = updatedThreads)
        cacheThreadSnapshot(mergedThread)
        _lastError.value = null
    }
}

internal fun AppModel.removeThreadSnapshot(
    key: ThreadKey,
    agentDirectoryVersion: ULong? = null,
    clearCache: Boolean = true,
) {
    synchronized(snapshotLock) {
        val current = _snapshot.value ?: return
        _snapshot.value = current.copy(
            threads = current.threads.filterNot { it.key == key },
            sessionSummaries = current.sessionSummaries.filterNot { it.key == key },
            agentDirectoryVersion = agentDirectoryVersion ?: current.agentDirectoryVersion,
            activeThread = if (current.activeThread == key) null else current.activeThread,
        )
        if (clearCache) {
            cachedThreadSnapshots.remove(key)
        }
    }
}

internal fun AppModel.updateActiveThread(key: ThreadKey?) {
    synchronized(snapshotLock) {
        val current = _snapshot.value ?: return
        _snapshot.value = current.copy(activeThread = key)
    }
}

internal fun AppModel.restoreCachedThreadSnapshotIfNeeded(key: ThreadKey?) {
    synchronized(snapshotLock) {
        if (key == null) return
        if (_snapshot.value?.threads?.any { it.key == key } == true) return
        val cached = cachedThreadSnapshots[key] ?: return
        applyThreadSnapshot(cached)
    }
}

internal fun AppModel.cacheThreadSnapshot(thread: AppThreadSnapshot) {
    cachedThreadSnapshots[thread.key] = thread
}

internal fun AppModel.mergedThreadSnapshotPreservingHydratedItems(thread: AppThreadSnapshot): AppThreadSnapshot {
    if (thread.hydratedConversationItems.isNotEmpty()) return thread
    val cached = cachedThreadSnapshots[thread.key] ?: return thread
    if (cached.hydratedConversationItems.isEmpty()) return thread
    return thread.copy(hydratedConversationItems = cached.hydratedConversationItems)
}

private fun AppModel.mergeCachedThreadSnapshots(snapshot: AppSnapshotRecord): AppSnapshotRecord {
    val mergedThreads = snapshot.threads
        .map(::mergedThreadSnapshotPreservingHydratedItems)
        .toMutableList()

    cachedThreadSnapshots.forEach { (key, cached) ->
        val alreadyPresent = mergedThreads.any { it.key == key }
        val shouldInclude = snapshot.activeThread == key || snapshot.sessionSummaries.any { it.key == key }
        if (!alreadyPresent && shouldInclude) {
            mergedThreads += cached
        }
    }

    return snapshot.copy(threads = mergedThreads)
}
