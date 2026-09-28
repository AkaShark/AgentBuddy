package com.akashark.agentbuddy.android.ui.conversation

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppConnectionProgressSnapshot
import uniffi.codex_mobile_client.AppConnectionStepKind
import uniffi.codex_mobile_client.AppConnectionStepSnapshot
import uniffi.codex_mobile_client.AppConnectionStepState
import uniffi.codex_mobile_client.AppServerCapabilities
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppServerTransportState

class TaskInfoConnectionTest {
    @Test
    fun `an unresponsive host reads connecting, like the header`() {
        assertEquals(
            BuddyConnectionState.CONNECTING to "无响应",
            taskInfoConnection(server(AppServerTransportState.UNRESPONSIVE)),
        )
    }

    @Test
    fun `the connection step shows while the host is being set up`() {
        val progress = AppConnectionProgressSnapshot(
            steps = listOf(
                AppConnectionStepSnapshot(kind = AppConnectionStepKind.CONNECTING_TO_SSH, state = AppConnectionStepState.COMPLETED, detail = null),
                AppConnectionStepSnapshot(kind = AppConnectionStepKind.INSTALLING_CODEX, state = AppConnectionStepState.IN_PROGRESS, detail = null),
            ),
            pendingInstall = false,
            terminalMessage = null,
            hostKeyMismatch = null,
        )
        assertEquals(
            BuddyConnectionState.CONNECTING to "正在安装 Codex…",
            taskInfoConnection(server(AppServerTransportState.CONNECTING, progress = progress)),
        )
    }

    @Test
    fun `a connected host that needs a sign-in keeps the warning dot`() {
        assertEquals(
            BuddyConnectionState.CONNECTING to "需要登录",
            taskInfoConnection(server(AppServerTransportState.CONNECTED, account = null)),
        )
        assertEquals(
            BuddyConnectionState.CONNECTED to "已连接",
            taskInfoConnection(server(AppServerTransportState.CONNECTED, account = Account.ApiKey)),
        )
    }

    private fun server(
        transport: AppServerTransportState,
        account: Account? = Account.ApiKey,
        progress: AppConnectionProgressSnapshot? = null,
    ) = AppServerSnapshot(
        serverId = "mac",
        displayName = "Mac",
        host = "mac.local",
        port = 8390u,
        wakeMac = null,
        isLocal = false,
        health = AppServerHealth.UNKNOWN,
        transportState = transport,
        capabilities = AppServerCapabilities(
            canUseTransportActions = true,
            canBrowseDirectories = true,
            canStartThreads = true,
            canResumeThreads = true,
            supportsTurnPagination = true,
        ),
        account = account,
        requiresOpenaiAuth = false,
        rateLimits = null,
        rateLimitsByRuntime = emptyList(),
        availableModels = null,
        agentRuntimes = emptyList(),
        connectionProgress = progress,
        usageStats = null,
        codexVersion = null,
    )
}
