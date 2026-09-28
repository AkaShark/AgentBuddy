package com.akashark.agentbuddy.android.ui.conversation

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.currentConnectionStep
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.approvals.hostDisplayName
import com.akashark.agentbuddy.android.ui.common.reportsEffectiveThreadPermissions
import com.akashark.agentbuddy.android.ui.common.runtimeLabel
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppConnectionStepState
import uniffi.codex_mobile_client.AppModeKind
import uniffi.codex_mobile_client.AppThreadPermissionPreset
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.threadPermissionPreset

/**
 * Conversation header: task title and 「搭档 · 主机」 with connection state,
 * plan marker and full-access warning; model, permissions, plan mode, reload
 * and task info live in the 「…」 menu. The partner / model panel still opens
 * inline under the header.
 */
@Composable
fun HeaderBar(
    thread: AppThreadSnapshot?,
    onBack: () -> Unit,
    onInfo: (() -> Unit)? = null,
    showModelSelector: Boolean,
    onToggleModelSelector: () -> Unit,
    onShowPermissions: () -> Unit = {},
    onShowCollaborationMode: () -> Unit = {},
    onReloadError: ((String) -> Unit)? = null,
    transparentBackground: Boolean = false,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    // Recompose when the pending launch selection (permissions) changes.
    val launchState by appModel.launchState.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val server = remember(snapshot, thread) {
        thread?.let { t -> snapshot?.servers?.find { it.serverId == t.key.serverId } }
    }
    val connection = conversationConnectionState(
        transportState = server?.transportState,
        connectionFailed = server?.currentConnectionStep?.state == AppConnectionStepState.FAILED,
    )
    // Full access = approval never + sandbox danger-full-access (iOS HeaderView).
    val isFullAccess = remember(thread, launchState) {
        thread
            ?.takeIf { it.agentRuntimeKind.reportsEffectiveThreadPermissions }
            ?.let { t ->
                val approval = appModel.launchState.approvalPolicyValue(t.key) ?: t.effectiveApprovalPolicy
                val sandbox = appModel.launchState.turnSandboxPolicy(t.key) ?: t.effectiveSandboxPolicy
                threadPermissionPreset(approval, sandbox) == AppThreadPermissionPreset.FULL_ACCESS
            } == true
    }
    var isReloading by remember { mutableStateOf(false) }
    val reload: () -> Unit = reload@{
        if (thread == null || isReloading) return@reload
        scope.launch {
            isReloading = true
            try {
                if (server != null && !server.isLocal && server.account == null) {
                    val authUrl = appModel.client.startRemoteSshOauthLogin(thread.key.serverId)
                    CustomTabsIntent.Builder()
                        .setShowTitle(true)
                        .build()
                        .launchUrl(context, Uri.parse(authUrl))
                    return@launch
                }
                val nextKey = appModel.refreshThreadIncludingTurns(thread.key)
                appModel.store.setActiveThread(nextKey)
            } catch (e: Exception) {
                onReloadError?.invoke(e.message ?: "重新加载会话失败")
            } finally {
                isReloading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!transparentBackground) Modifier.background(AgentBuddyTheme.background) else Modifier),
    ) {
        ConversationHeaderContent(
            state = ConversationHeaderState(
                title = conversationHeaderTitle(thread?.info?.title, thread?.info?.preview),
                subtitle = conversationHeaderSubtitle(
                    partner = thread?.agentRuntimeKind?.runtimeLabel.orEmpty(),
                    hostName = hostDisplayName(server?.displayName),
                    connection = connection,
                ),
                connection = connection,
                isPlanMode = thread?.collaborationMode == AppModeKind.PLAN,
                isFullAccess = isFullAccess,
                isFastMode = HeaderOverrides.pendingFastMode,
                isReloading = isReloading,
                canShowInfo = onInfo != null,
            ),
            actions = ConversationHeaderActions(
                onBack = onBack,
                onToggleModelPanel = onToggleModelSelector,
                onOpenModelPanel = { if (!showModelSelector) onToggleModelSelector() },
                onOpenPermissions = onShowPermissions,
                onOpenPlanMode = onShowCollaborationMode,
                onReload = reload,
                onOpenInfo = { onInfo?.invoke() },
            ),
        )

        // Inline model selector — shared panel lives in
        // `com.akashark.agentbuddy.android.ui.common.ModelSelectorPanel`; the home
        // composer's HomeModelChip uses the same panel inside a
        // ModalBottomSheet.
        val reduceMotion = buddyReduceMotion
        AnimatedVisibility(
            visible = showModelSelector,
            enter = expandVertically(BuddyMotion.SHEET.spec(reduceMotion)),
            exit = shrinkVertically(BuddyMotion.STATE.spec(reduceMotion)),
        ) {
            com.akashark.agentbuddy.android.ui.common.ModelSelectorPanel(
                thread = thread,
                availableModels = server?.availableModels ?: emptyList(),
                onToggleMode = { mode ->
                    thread?.let { t ->
                        scope.launch {
                            try {
                                appModel.store.setThreadCollaborationMode(t.key, mode)
                            } catch (_: Exception) {}
                        }
                    }
                },
                fastMode = HeaderOverrides.pendingFastMode,
                onFastModeChange = { HeaderOverrides.pendingFastMode = it },
                showBackground = false,
            )
        }
    }
}

/**
 * Holds the fast-mode override selected in the header.
 * Launch model/effort state lives in [AppLaunchState].
 */
object HeaderOverrides {
    var pendingFastMode by mutableStateOf(false)
}
