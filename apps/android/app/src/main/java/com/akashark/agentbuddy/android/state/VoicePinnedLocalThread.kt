package com.akashark.agentbuddy.android.state

import android.content.Context
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey
import com.akashark.agentbuddy.android.state.VoiceRuntimeController.Companion.LOCAL_SERVER_ID

// Pinned local voice thread for [VoiceRuntimeController]: local server
// connect, thread reuse/creation and the persisted thread id preference.

private const val VOICE_PREFS_NAME = "agentbuddy.voice"
private const val PERSISTED_LOCAL_VOICE_THREAD_ID_KEY = "agentbuddy.voice.local.thread_id"

internal suspend fun VoiceRuntimeController.ensurePinnedLocalVoiceThread(
    appModel: AppModel,
    cwd: String,
    model: String? = null,
): ThreadKey? {
    val serverId = ensureLocalServerConnected(appModel) ?: return null
    val launchConfig = appModel.launchState.launchConfig(modelOverride = model)

    persistedLocalVoiceThreadId(appModel)?.let { storedThreadId ->
        val key = ThreadKey(serverId = serverId, threadId = storedThreadId)
        val knownThread = appModel.snapshot.value?.let { snapshot ->
            snapshot.threads.any { it.key == key } || snapshot.sessionSummaries.any { it.key == key }
        } == true

        if (knownThread) {
            appModel.store.setActiveThread(key)
            return key
        }

        val loadedKey = appModel.ensureThreadLoaded(key)
        if (loadedKey != null) {
            appModel.store.setActiveThread(loadedKey)
            setPersistedLocalVoiceThreadId(appModel, loadedKey.threadId)
            appModel.refreshSnapshot()
            return loadedKey
        }

        setPersistedLocalVoiceThreadId(appModel, null)
    }

    return try {
        val key = appModel.startThread(
            serverId,
            launchConfig.toAppStartThreadRequest(
                preferredVoiceThreadCwd(appModel, key = null, fallback = cwd),
            ),
        )
        SavedThreadsStore.add(
            appModel.appContext,
            PinnedThreadKey(serverId = key.serverId, threadId = key.threadId),
        )
        appModel.store.setActiveThread(key)
        setPersistedLocalVoiceThreadId(appModel, key.threadId)
        appModel.refreshSnapshot()
        key
    } catch (_: Exception) {
        null
    }
}

private suspend fun ensureLocalServerConnected(appModel: AppModel): String? {
    appModel.snapshot.value?.servers?.firstOrNull { it.isLocal && it.isConnected }?.let { server ->
        return server.serverId
    }

    val currentLocal = appModel.snapshot.value?.servers?.firstOrNull { it.isLocal }
    val serverId = currentLocal?.serverId ?: LOCAL_SERVER_ID
    val displayName = currentLocal?.displayName ?: "本地"
    return try {
        appModel.serverBridge.connectLocalServer(serverId, displayName, "127.0.0.1", 0u)
        appModel.restoreStoredLocalAuthState(serverId)
        appModel.refreshSnapshot()
        serverId
    } catch (_: Exception) {
        null
    }
}

internal fun VoiceRuntimeController.persistedLocalVoiceThreadId(appModel: AppModel): String? {
    val stored = voicePrefs(appModel)
        .getString(PERSISTED_LOCAL_VOICE_THREAD_ID_KEY, null)
        ?.trim()
        .orEmpty()
    return stored.ifEmpty { null }
}

internal fun VoiceRuntimeController.setPersistedLocalVoiceThreadId(appModel: AppModel, threadId: String?) {
    val trimmed = threadId?.trim().orEmpty()
    val editor = voicePrefs(appModel).edit()
    if (trimmed.isEmpty()) {
        editor.remove(PERSISTED_LOCAL_VOICE_THREAD_ID_KEY)
    } else {
        editor.putString(PERSISTED_LOCAL_VOICE_THREAD_ID_KEY, trimmed)
    }
    editor.apply()
}

private fun voicePrefs(appModel: AppModel) =
    appModel.appContext.getSharedPreferences(VOICE_PREFS_NAME, Context.MODE_PRIVATE)

private fun preferredVoiceThreadCwd(
    appModel: AppModel,
    key: ThreadKey?,
    fallback: String,
): String {
    val existingCwd = key
        ?.let { threadKey ->
            appModel.snapshot.value
                ?.threads
                ?.firstOrNull { it.key == threadKey }
                ?.info
                ?.cwd
                ?.trim()
        }
        .orEmpty()
    if (existingCwd.isNotEmpty()) {
        return existingCwd
    }

    val trimmedFallback = fallback.trim()
    if (trimmedFallback.isNotEmpty()) {
        return trimmedFallback
    }

    return appModel.launchState.snapshot.value.currentCwd.trim().ifEmpty { "/" }
}
