package com.akashark.agentbuddy.android.state

import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AppLoginAccountRequest

// Local server restart and stored local-account auth restore (ChatGPT tokens
// or OpenAI API key) behind [AppModel]'s public API.

internal suspend fun AppModel.restartLocalServerImpl() {
    val currentLocal = snapshot.value?.servers?.firstOrNull { it.isLocal }
    val serverId = currentLocal?.serverId ?: "local"
    val displayName = currentLocal?.displayName ?: "This Device"
    runCatching { serverBridge.disconnectServer(serverId) }
    serverBridge.connectLocalServer(
        serverId = serverId,
        displayName = displayName,
        host = "127.0.0.1",
        port = 0u,
    )
    restoreStoredLocalAuthState(serverId)
    try {
        refreshSessions(listOf(serverId))
    } catch (_: Exception) {
    }
    refreshSnapshot()
}

internal suspend fun AppModel.restoreStoredLocalAuthStateImpl(serverId: String) {
    val apiKeyStore = OpenAIApiKeyStore(appContext)
    val storedApiKey = apiKeyStore.load()
    if (restoreStoredLocalChatGptAuth(serverId)) {
        return
    }
    apiKeyStore.applyToEnvironment()
    if (!storedApiKey.isNullOrBlank() && loginStoredLocalApiKeyAuth(serverId, storedApiKey)) {
        return
    }
}

internal suspend fun AppModel.ensureLocalAuthForThreadStartImpl(serverId: String): Boolean {
    val server = snapshot.value?.servers?.firstOrNull { it.serverId == serverId } ?: return true
    if (!server.isLocal) return true
    if (server.account != null) return true

    if (restoreStoredLocalAuthIfNeeded(serverId, reason = "startThread")) {
        return true
    }

    return false
}

internal suspend fun AppModel.restoreStoredLocalAuthIfNeeded(serverId: String, reason: String): Boolean {
    val server = snapshot.value?.servers?.firstOrNull { it.serverId == serverId } ?: return false
    if (!server.isLocal || server.account != null) return false

    val apiKeyStore = OpenAIApiKeyStore(appContext)
    val storedApiKey = apiKeyStore.load()?.trim().orEmpty()
    val hasStoredChatGptTokens = ChatGPTOAuthTokenStore(appContext).load() != null
    if (!hasStoredChatGptTokens && storedApiKey.isBlank()) return false

    LLog.i(
        "AppModel",
        "restoring stored local auth before local session operation",
        fields = mapOf(
            "serverId" to serverId,
            "reason" to reason,
        ),
    )
    restoreStoredLocalAuthState(serverId)
    refreshSnapshot()
    return snapshot.value?.servers?.firstOrNull { it.serverId == serverId }?.account != null
}

internal suspend fun AppModel.restoreStoredLocalChatGptAuthImpl(serverId: String): Boolean {
    val storedTokens = ChatGPTOAuthTokenStore(appContext).load() ?: return false
    val refreshedTokens = runCatching {
        ChatGPTOAuth.refreshStoredTokens(
            context = appContext,
            previousAccountId = null,
        )
    }.getOrNull()
    if (refreshedTokens != null &&
        loginStoredLocalChatGptAuth(serverId, refreshedTokens)
    ) {
        return true
    }
    if (loginStoredLocalChatGptAuth(serverId, storedTokens)) {
        return true
    }
    if (refreshedTokens != null) {
        return false
    }
    delay(2_000)
    return runCatching {
        ChatGPTOAuth.refreshStoredTokens(
            context = appContext,
            previousAccountId = null,
        )
    }.getOrNull()?.let { retriedRefresh ->
        loginStoredLocalChatGptAuth(serverId, retriedRefresh)
    } == true
}

private suspend fun AppModel.loginStoredLocalChatGptAuth(
    serverId: String,
    tokens: ChatGPTOAuthTokenBundle,
): Boolean {
    return runCatching {
        client.loginAccount(
            serverId,
            uniffi.codex_mobile_client.AppLoginAccountRequest.ChatgptAuthTokens(
                accessToken = tokens.accessToken,
                chatgptAccountId = tokens.accountId,
                chatgptPlanType = tokens.planType,
            ),
        )
        true
    }.getOrElse { error ->
        _lastError.value = error.message
        false
    }
}

private suspend fun AppModel.loginStoredLocalApiKeyAuth(serverId: String, apiKey: String): Boolean {
    return runCatching {
        client.loginAccount(
            serverId,
            AppLoginAccountRequest.ApiKey(apiKey.trim()),
        )
        _lastError.value = null
        true
    }.getOrElse { error ->
        LLog.w(
            "AppModel",
            "restoring stored local API key auth failed",
            fields = mapOf(
                "serverId" to serverId,
                "error" to (error.localizedMessage ?: error.message ?: error.toString()),
            ),
        )
        false
    }
}
