package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.SavedServer
import com.akashark.agentbuddy.android.state.SavedServerStore
import com.akashark.agentbuddy.android.state.ChatGPTOAuth
import com.akashark.agentbuddy.android.auth.ChatGPTOAuthActivity
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSlingshotEnvironment
import kotlinx.coroutines.CoroutineScope

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
        ConnectedComputersDialog(
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

@Composable
private fun ConnectedComputersDialog(
    environments: List<AppSlingshotEnvironment>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (AppSlingshotEnvironment) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已连接电脑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "这些电脑来自使用你已登录账号的 ChatGPT。请先在电脑上启动 Codex，它才会显示在此处。",
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = 12.sp,
                )
                when {
                    loading && environments.isEmpty() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AgentBuddyTheme.accent,
                            )
                            Text(
                                text = "正在加载已连接电脑...",
                                color = AgentBuddyTheme.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    error != null -> {
                        Text(
                            text = error,
                            color = AgentBuddyTheme.danger,
                            fontSize = 12.sp,
                        )
                    }

                    environments.isEmpty() -> {
                        Text(
                            text = "未找到此账号下已连接的电脑。",
                            color = AgentBuddyTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    else -> {
                        if (loading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AgentBuddyTheme.accent,
                                trackColor = AgentBuddyTheme.border,
                            )
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(340.dp),
                        ) {
                            items(environments, key = { it.id }) { environment ->
                                ConnectedComputerRow(
                                    environment = environment,
                                    onClick = { onSelect(environment) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRefresh,
                enabled = !loading,
            ) {
                Text("刷新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ConnectedComputerRow(
    environment: AppSlingshotEnvironment,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(10.dp))
            .clickable(enabled = environment.online, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = slingshotEnvironmentIcon(environment),
            contentDescription = null,
            tint = if (environment.online) AgentBuddyTheme.accent else AgentBuddyTheme.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = environment.displayName,
                color = if (environment.online) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = slingshotEnvironmentSubtitle(environment),
                color = AgentBuddyTheme.textSecondary,
                fontSize = 11.sp,
            )
        }
        Text(
            text = slingshotEnvironmentStatus(environment),
            color = if (environment.online && !environment.busy) AgentBuddyTheme.accent else AgentBuddyTheme.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
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

private fun slingshotEnvironmentSubtitle(environment: AppSlingshotEnvironment): String {
    val parts = buildList {
        environment.hostName?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        listOfNotNull(
            environment.operatingSystem.trim().takeIf { it.isNotEmpty() },
            environment.architecture?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").takeIf { it.isNotEmpty() }?.let(::add)
        environment.appServerVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Codex $it") }
    }
    return parts.ifEmpty { listOf(environment.id) }.joinToString(" - ")
}

private fun slingshotEnvironmentStatus(environment: AppSlingshotEnvironment): String =
    when {
        !environment.online -> "离线"
        environment.busy -> "忙碌"
        else -> "在线"
    }

private fun slingshotEnvironmentIcon(
    environment: AppSlingshotEnvironment,
): androidx.compose.ui.graphics.vector.ImageVector =
    when (environment.operatingSystem.lowercase()) {
        "linux" -> Icons.Outlined.Dns
        "windows" -> Icons.Outlined.DesktopWindows
        "macos", "darwin" -> Icons.Outlined.DesktopWindows
        else -> Icons.Outlined.Laptop
    }

internal suspend fun loadSlingshotTokens(context: Context) =
    ChatGPTOAuth.requireStoredOrRefreshedTokens(
        context,
        "使用 Slingshot 连接前请先用 ChatGPT 登录。",
    )
