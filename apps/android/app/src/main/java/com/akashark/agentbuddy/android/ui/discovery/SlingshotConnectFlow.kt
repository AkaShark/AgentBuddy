package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.auth.ChatGPTOAuthActivity
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSlingshotEnvironment

private const val SLINGSHOT_BASE_URL = "https://chatgpt.com/backend-api"

/**
 * Connected-computers (Slingshot) flow of [DiscoveryScreen]: the computer list,
 * the connect with cached credentials, and the ChatGPT remote-control step-up.
 */
@Composable
internal fun SlingshotConnectFlow(
    showComputers: Boolean,
    scope: CoroutineScope,
    logTag: String,
    reloadSavedServers: suspend () -> Unit,
    onCloseComputers: () -> Unit,
    onConnectError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    var slingshotEnvironments by remember { mutableStateOf<List<AppSlingshotEnvironment>>(emptyList()) }
    var slingshotIsLoading by remember { mutableStateOf(false) }
    var slingshotError by remember { mutableStateOf<String?>(null) }
    var pendingSlingshotEnvironment by remember { mutableStateOf<AppSlingshotEnvironment?>(null) }
    var authorizedSlingshotConnect by remember { mutableStateOf<Pair<AppSlingshotEnvironment, String>?>(null) }
    val slingshotStepUpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val environment = pendingSlingshotEnvironment
        pendingSlingshotEnvironment = null
        if (environment == null) {
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            onConnectError(
                result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
                    ?: "远程控制授权已取消。",
            )
            return@rememberLauncherForActivityResult
        }
        val stepUpToken = ChatGPTOAuthActivity.parseRemoteControlStepUpToken(result.data)
        if (stepUpToken == null) {
            onConnectError("远程控制授权返回的凭据不完整。")
            return@rememberLauncherForActivityResult
        }
        authorizedSlingshotConnect = environment to stepUpToken
    }

    suspend fun loadSlingshotEnvironments() {
        if (slingshotIsLoading) {
            return
        }
        slingshotIsLoading = true
        slingshotError = null
        try {
            val tokens = loadSlingshotTokens(context)
            slingshotEnvironments = appModel.serverBridge
                .listSlingshotEnvironments(
                    baseUrl = SLINGSHOT_BASE_URL,
                    accessToken = tokens.accessToken,
                    accountId = tokens.accountId,
                )
                .sortedWith(
                    compareByDescending<AppSlingshotEnvironment> { it.online }
                        .thenBy { it.busy }
                        .thenBy { it.displayName.lowercase() },
                )
        } catch (e: Exception) {
            LLog.e(logTag, "slingshot environment load failed", e)
            slingshotError = e.message ?: "无法加载已连接电脑。"
        } finally {
            slingshotIsLoading = false
        }
    }

    suspend fun connectSlingshotEnvironmentOrThrow(environment: AppSlingshotEnvironment, stepUpToken: String) {
        if (!environment.online) {
            throw IllegalStateException("${environment.displayName} 已离线。")
        }
        val server = slingshotSavedServer(environment)
        val tokens = loadSlingshotTokens(context)
        appModel.serverBridge.connectRemoteSlingshotUrlServer(
            server.id,
            server.name,
            environment.connectionUrl,
            tokens.accessToken,
            tokens.accountId,
            stepUpToken,
        )
        SavedServerStore.remember(context, server.normalizedForPersistence())
        reloadSavedServers()
        appModel.refreshSnapshot()
    }

    fun finishSuccessfulSlingshotConnect() {
        onCloseComputers()
        onDismiss()
    }

    fun startSlingshotConnect(environment: AppSlingshotEnvironment) {
        if (!environment.online) {
            onConnectError("${environment.displayName} 已离线。")
            return
        }
        scope.launch {
            var needsAuthorization = false
            try {
                connectSlingshotEnvironmentOrThrow(environment, "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!ChatGPTOAuth.isRemoteControlAuthorizationRequired(e)) {
                    LLog.e(
                        logTag,
                        "slingshot cached connect failed",
                        e,
                        fields = mapOf("environmentId" to environment.id),
                    )
                    onConnectError(e.message ?: "无法连接到此电脑。")
                    return@launch
                }
                needsAuthorization = true
            }

            if (!needsAuthorization) {
                finishSuccessfulSlingshotConnect()
                return@launch
            }

            try {
                pendingSlingshotEnvironment = environment
                slingshotStepUpLauncher.launch(
                    ChatGPTOAuthActivity.createIntent(
                        context,
                        ChatGPTOAuth.createRemoteControlEnrollmentAttempt(),
                    ),
                )
            } catch (e: Exception) {
                pendingSlingshotEnvironment = null
                onConnectError(e.localizedMessage ?: e.message ?: "无法授权远程控制。")
            }
        }
    }

    suspend fun connectSlingshotEnvironment(environment: AppSlingshotEnvironment, stepUpToken: String) {
        try {
            connectSlingshotEnvironmentOrThrow(environment, stepUpToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LLog.e(
                logTag,
                "slingshot connect failed",
                e,
                fields = mapOf("environmentId" to environment.id),
            )
            onConnectError(e.message ?: "无法连接到此电脑。")
            return
        }
        finishSuccessfulSlingshotConnect()
    }

    LaunchedEffect(authorizedSlingshotConnect) {
        val pending = authorizedSlingshotConnect ?: return@LaunchedEffect
        authorizedSlingshotConnect = null
        connectSlingshotEnvironment(pending.first, pending.second)
    }

    if (showComputers) {
        SlingshotComputersSheet(
            environments = slingshotEnvironments,
            loading = slingshotIsLoading,
            error = slingshotError,
            onDismiss = onCloseComputers,
            onRefresh = { scope.launch { loadSlingshotEnvironments() } },
            onSelect = { environment ->
                startSlingshotConnect(environment)
            },
        )
        LaunchedEffect(Unit) {
            if (slingshotEnvironments.isEmpty() && !slingshotIsLoading) {
                loadSlingshotEnvironments()
            }
        }
    }
}

private fun slingshotSavedServer(environment: AppSlingshotEnvironment): SavedServer =
    SavedServer(
        id = "slingshot-${environment.id}",
        name = environment.displayName,
        hostname = environment.id,
        port = 0,
        codexPorts = emptyList(),
        source = "manual",
        hasCodexServer = true,
        preferredConnectionMode = "directCodex",
        websocketURL = environment.connectionUrl,
        os = environment.operatingSystem,
        rememberedByUser = true,
    )

internal suspend fun loadSlingshotTokens(context: Context) =
    ChatGPTOAuth.requireStoredOrRefreshedTokens(
        context,
        "使用 Slingshot 连接前请先用 ChatGPT 登录。",
    )
