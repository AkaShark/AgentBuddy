package com.akashark.agentbuddy.android.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.akashark.agentbuddy.android.auth.ChatGPTOAuthActivity
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.state.ChatGPTOAuthTokenStore
import com.akashark.agentbuddy.android.state.OpenAIApiKeyStore
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppRefreshAccountRequest
import uniffi.codex_mobile_client.AppLoginAccountRequest

/**
 * Account login/logout management for a specific server.
 */
@Composable
fun AccountSheet(
    serverId: String,
    onDismiss: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    val server = remember(snapshot, serverId) {
        snapshot?.servers?.find { it.serverId == serverId }
    }
    val account = server?.account
    val apiKeyStore = remember(context) { OpenAIApiKeyStore(context.applicationContext) }
    var apiKey by remember { mutableStateOf("") }
    var openAIBaseUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isAuthWorking by remember { mutableStateOf(false) }
    var hasStoredApiKey by remember { mutableStateOf(apiKeyStore.hasStoredKey()) }
    var hasStoredBaseUrl by remember { mutableStateOf(apiKeyStore.hasStoredBaseUrl()) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isAuthWorking = false
        LLog.d("ChatGPTOAuth", "account sheet auth result", fields = mapOf("resultCode" to result.resultCode))
        if (result.resultCode == Activity.RESULT_OK) {
            val tokens = ChatGPTOAuthActivity.parseResult(result.data)
            if (tokens == null) {
                error = "ChatGPT 登录返回的凭据不完整。"
                LLog.w("ChatGPTOAuth", "account sheet auth result missing tokens")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                try {
                    LLog.d("ChatGPTOAuth", "account sheet loginAccount starting")
                    appModel.client.loginAccount(
                        serverId,
                        AppLoginAccountRequest.ChatgptAuthTokens(
                            accessToken = tokens.accessToken,
                            chatgptAccountId = tokens.accountId,
                            chatgptPlanType = tokens.planType,
                        ),
                    )
                    appModel.refreshSnapshot()
                    error = null
                    LLog.i("ChatGPTOAuth", "account sheet loginAccount succeeded")
                } catch (e: Exception) {
                    error = e.localizedMessage ?: e.message
                    LLog.e("ChatGPTOAuth", "account sheet loginAccount failed", e)
                }
            }
        } else {
            error = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
            error?.let { LLog.w("ChatGPTOAuth", "account sheet auth canceled", fields = mapOf("error" to it)) }
        }
    }

    LaunchedEffect(serverId, account) {
        hasStoredApiKey = apiKeyStore.hasStoredKey()
        hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
    }

    LaunchedEffect(serverId) {
        runCatching {
            appModel.client.refreshAccount(
                serverId,
                AppRefreshAccountRequest(refreshToken = false),
            )
            appModel.refreshSnapshot()
            error = null
        }.onFailure { throwable ->
            error = throwable.localizedMessage ?: throwable.message
        }
    }

    AccountSheetContent(
        state = AccountSheetState(
            serverName = server?.let(::settingsServerDisplayName) ?: serverId,
            isLocal = server?.isLocal,
            signIn = when (account) {
                is Account.Chatgpt -> AccountSignIn.CHATGPT
                is Account.ApiKey -> AccountSignIn.API_KEY
                null -> AccountSignIn.NONE
            },
            email = (account as? Account.Chatgpt)?.email,
            hasStoredApiKey = hasStoredApiKey,
            hasStoredBaseUrl = hasStoredBaseUrl,
            apiKey = apiKey,
            baseUrl = openAIBaseUrl,
            isAuthWorking = isAuthWorking,
            error = error,
        ),
        actions = AccountSheetActions(
            onDismiss = onDismiss,
            onLogin = {
                try {
                    error = null
                    isAuthWorking = true
                    authLauncher.launch(
                        ChatGPTOAuthActivity.createIntent(
                            context,
                            ChatGPTOAuth.createLoginAttempt(),
                        ),
                    )
                } catch (e: Exception) {
                    isAuthWorking = false
                    error = e.localizedMessage ?: e.message
                }
            },
            onLogout = {
                scope.launch {
                    ChatGPTOAuthTokenStore(context).clear()
                    apiKeyStore.clear()
                    appModel.client.logoutAccount(serverId)
                    appModel.restartLocalServer()
                }
            },
            onApiKeyChange = { apiKey = it },
            onSaveApiKey = {
                scope.launch {
                    try {
                        apiKeyStore.save(apiKey.trim())
                        if (account is Account.ApiKey) {
                            appModel.client.logoutAccount(serverId)
                        }
                        appModel.restartLocalServer()
                        hasStoredApiKey = apiKeyStore.hasStoredKey()
                        if (hasStoredApiKey) {
                            apiKey = ""
                        } else {
                            error = "API 密钥未能本地保存。"
                            return@launch
                        }
                        error = null
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onBaseUrlChange = { openAIBaseUrl = it },
            onSaveBaseUrl = {
                val normalized = normalizeOpenAIBaseUrl(openAIBaseUrl)
                if (normalized == null) {
                    error = "请输入有效的 http 或 https Base URL。"
                } else {
                    scope.launch {
                        isAuthWorking = true
                        try {
                            apiKeyStore.saveBaseUrl(normalized)
                            appModel.restartLocalServer()
                            hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                            if (hasStoredBaseUrl) {
                                openAIBaseUrl = ""
                                error = null
                            } else {
                                error = "Base URL 未能本地保存。"
                            }
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            isAuthWorking = false
                        }
                    }
                }
            },
            onClearBaseUrl = {
                scope.launch {
                    isAuthWorking = true
                    try {
                        apiKeyStore.clearBaseUrl()
                        appModel.restartLocalServer()
                        hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                        openAIBaseUrl = ""
                        error = null
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        isAuthWorking = false
                    }
                }
            },
        ),
        modifier = Modifier.imePadding(),
    )
}

private fun normalizeOpenAIBaseUrl(rawValue: String): String? {
    val trimmed = rawValue.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return trimmed
}
