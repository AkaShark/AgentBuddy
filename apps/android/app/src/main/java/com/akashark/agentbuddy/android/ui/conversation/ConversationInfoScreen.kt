package com.akashark.agentbuddy.android.ui.conversation

import android.content.Context
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.PathDisplay
import com.akashark.agentbuddy.android.state.currentConnectionStep
import com.akashark.agentbuddy.android.state.displayModelLabel
import com.akashark.agentbuddy.android.state.displayTitle
import com.akashark.agentbuddy.android.state.hasActiveTurn
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.common.effortDisplayName
import com.akashark.agentbuddy.android.ui.common.titleDisplayLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTaskState
import com.akashark.agentbuddy.android.ui.settings.SettingsAlertDialog
import com.akashark.agentbuddy.android.ui.settings.SettingsTextField
import com.akashark.agentbuddy.android.ui.settings.mintConnectionStatus
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppConnectionStepState
import uniffi.codex_mobile_client.AppRenameThreadRequest
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.PlanType
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.ThreadSummaryStatus

/**
 * Task info (`threadKey != null`) or server info (`threadKey == null`).
 * Reads the Rust snapshot, maps it to [TaskInfoState] and owns the fork /
 * rename calls plus their UI-only progress and failure state.
 */
@Composable
fun ConversationInfoScreen(
    threadKey: ThreadKey? = null,
    serverId: String? = null,
    onBack: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onOpenShell: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var showRenameDialog by remember(threadKey) { mutableStateOf(false) }
    var renameText by remember(threadKey) { mutableStateOf("") }
    var isForking by remember(threadKey) { mutableStateOf(false) }
    var actionError by remember(threadKey) { mutableStateOf<String?>(null) }

    val resolvedServerId = threadKey?.serverId ?: serverId
    val thread = remember(snapshot, threadKey) {
        threadKey?.let { key -> snapshot?.threads?.find { it.key == key } }
    }
    val server = remember(snapshot, resolvedServerId) {
        snapshot?.servers?.find { it.serverId == resolvedServerId }
    }

    val state = TaskInfoState(
        isServerOnly = threadKey == null,
        hero = thread?.let { taskInfoHero(it, server, context) },
        context = thread?.let { t ->
            val window = t.modelContextWindow?.toLong() ?: 0L
            if (window > 0L) TaskContextUsage(usedTokens = t.contextTokensUsed?.toLong() ?: 0L, windowTokens = window) else null
        },
        stats = thread?.stats,
        usage = server?.usageStats,
        rateLimits = server?.rateLimits,
        server = server?.let(::taskInfoServer),
        canOpenShell = onOpenShell != null,
        isForking = isForking,
        actionError = actionError,
    )

    ConversationInfoContent(
        state = state,
        actions = TaskInfoActions(
            onBack = onBack,
            onChangeWallpaper = onChangeWallpaper,
            onFork = fork@{
                val t = thread ?: return@fork
                val tk = threadKey ?: return@fork
                if (isForking) return@fork
                isForking = true
                actionError = null
                scope.launch {
                    try {
                        val newKey = appModel.client.forkThread(
                            tk.serverId,
                            appModel.launchState.threadForkRequest(
                                sourceThreadId = tk.threadId,
                                cwdOverride = t.info.cwd,
                                threadKey = tk,
                            ),
                        )
                        appModel.store.setActiveThread(newKey)
                        appModel.refreshThreadSnapshot(newKey)
                    } catch (e: Exception) {
                        actionError = "分叉失败：${e.message?.trim().orEmpty().ifEmpty { "未知错误" }}"
                    } finally {
                        isForking = false
                    }
                }
            },
            onRename = {
                renameText = thread?.info?.title.orEmpty()
                showRenameDialog = true
            },
            onOpenShell = { onOpenShell?.invoke() },
            onDismissError = { actionError = null },
        ),
        modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
    )

    if (showRenameDialog && threadKey != null) {
        SettingsAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = "重命名任务",
            confirmText = "重命名",
            dismissText = "取消",
            onConfirm = {
                val trimmed = renameText.trim()
                if (trimmed.isNotEmpty()) {
                    showRenameDialog = false
                    actionError = null
                    scope.launch {
                        try {
                            appModel.client.renameThread(
                                threadKey.serverId,
                                AppRenameThreadRequest(threadId = threadKey.threadId, name = trimmed),
                            )
                            appModel.refreshThreadSnapshot(threadKey)
                        } catch (e: Exception) {
                            actionError = "重命名失败：${e.message?.trim().orEmpty().ifEmpty { "未知错误" }}"
                        }
                    }
                }
            },
            text = {
                SettingsTextField(value = renameText, onValueChange = { renameText = it }, label = "名称")
            },
        )
    }
}

private fun taskInfoHero(
    thread: AppThreadSnapshot,
    server: AppServerSnapshot?,
    context: Context,
): TaskInfoHero {
    val (status, statusTitle) = when {
        thread.hasActiveTurn -> BuddyTaskState.RUNNING to BuddyTaskState.RUNNING.title
        thread.info.status == ThreadSummaryStatus.SYSTEM_ERROR -> BuddyTaskState.FAILED to "出错"
        thread.info.status == ThreadSummaryStatus.NOT_LOADED -> BuddyTaskState.IDLE to "未加载"
        else -> BuddyTaskState.IDLE to BuddyTaskState.IDLE.title
    }
    val partner = thread.agentRuntimeKind.titleDisplayLabel
    val host = server?.let { serverDisplayName(it.displayName) }?.trim()?.takeIf { it.isNotEmpty() }
    val cwd = thread.info.cwd?.takeIf { it.isNotBlank() }
    val cwdDisplay = cwd?.let {
        if (server?.isLocal == true) {
            PathDisplay.display(it, true, context)
        } else {
            it.replace(Regex("^/home/[^/]+"), "~").replace(Regex("^/Users/[^/]+"), "~")
        }
    }
    return TaskInfoHero(
        title = thread.displayTitle,
        status = status,
        statusTitle = statusTitle,
        partnerAndHost = if (host != null) "$partner · $host" else partner,
        model = thread.displayModelLabel.trim().ifEmpty { null },
        effort = thread.reasoningEffort?.trim()?.takeIf { it.isNotEmpty() }?.let(::effortDisplayName),
        cwdDisplay = cwdDisplay,
        cwdFull = cwd,
        threadId = thread.key.threadId,
        createdAt = thread.info.createdAt,
        updatedAt = thread.info.updatedAt,
    )
}

/**
 * Server status for task info. The text is the host's status label with its
 * connection step (installing, tunnelling …, as in Settings); the tone follows
 * the conversation header, so an unresponsive host reads 连接中 in both. A
 * connected host that still needs something (需要登录, a pending step) keeps
 * the warning dot.
 */
internal fun taskInfoConnection(server: AppServerSnapshot): Pair<BuddyConnectionState, String> {
    val (statusTone, title) = server.mintConnectionStatus()
    val headerTone = conversationConnectionState(
        transportState = server.transportState,
        connectionFailed = server.currentConnectionStep?.state == AppConnectionStepState.FAILED,
    )
    val pending = headerTone == BuddyConnectionState.CONNECTED && statusTone == BuddyConnectionState.CONNECTING
    return (if (pending) BuddyConnectionState.CONNECTING else headerTone) to title
}

private fun taskInfoServer(server: AppServerSnapshot): TaskInfoServer {
    val (connection, connectionTitle) = taskInfoConnection(server)
    val account = server.account
    return TaskInfoServer(
        name = serverDisplayName(server.displayName),
        address = "${server.host}:${server.port}",
        mode = if (server.isLocal) "本地" else "远程",
        connection = connection,
        connectionTitle = connectionTitle,
        accountEmail = (account as? Account.Chatgpt)?.email,
        planLabel = (account as? Account.Chatgpt)?.planType?.let(::planTypeLabel),
        usesApiKey = account is Account.ApiKey,
        models = server.availableModels.orEmpty().map { it.displayName.ifBlank { it.id } },
    )
}

/** The shared "This Device" sentinel is mapped at render time only. */
private fun serverDisplayName(name: String): String = if (name == "This Device") "本设备" else name

private fun planTypeLabel(planType: PlanType): String = when (planType) {
    PlanType.FREE -> "Free"
    PlanType.GO -> "Go"
    PlanType.PLUS -> "Plus"
    PlanType.PRO -> "Pro"
    PlanType.TEAM -> "Team"
    PlanType.BUSINESS -> "Business"
    PlanType.ENTERPRISE -> "Enterprise"
    PlanType.EDU -> "Edu"
    PlanType.UNKNOWN -> "未知计划"
}
