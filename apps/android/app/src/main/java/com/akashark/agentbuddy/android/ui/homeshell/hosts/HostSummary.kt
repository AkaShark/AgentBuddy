package com.akashark.agentbuddy.android.ui.homeshell.hosts

import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import uniffi.codex_mobile_client.AppServerHealth

/** Render-only projection of one saved / connected host for the 主机 tab. */
data class HostSummary(
    val serverId: String,
    val name: String,
    val isLocal: Boolean,
    val connection: BuddyConnectionState,
    /** How this phone reaches the host: 「点对点连接」, 「SSH」, 「本机」… */
    val sourceTitle: String,
    /** Available partner names, e.g. Codex / Claude Code. */
    val partners: List<String>,
    val runningCount: Int,
    /** Offered only when the host has a saved alleycat token and the terminal flag is on. */
    val hasTerminal: Boolean,
) {
    val canStartTask: Boolean get() = connection == BuddyConnectionState.CONNECTED

    /** Remote hosts can be renamed; the in-process local server cannot. */
    val canRename: Boolean get() = !isLocal

    /** The in-process local server has no saved connection to edit. */
    val canEditConnection: Boolean get() = !isLocal

    val runningSummary: String
        get() = if (runningCount > 0) "$runningCount 个任务进行中" else "当前没有运行中的任务"
}

object HostPresentation {
    /** A lost connection is shown as a connection state, never as a task failure. */
    fun connectionState(health: AppServerHealth): BuddyConnectionState =
        when (health) {
            AppServerHealth.CONNECTED -> BuddyConnectionState.CONNECTED
            AppServerHealth.CONNECTING, AppServerHealth.UNKNOWN -> BuddyConnectionState.CONNECTING
            AppServerHealth.UNRESPONSIVE -> BuddyConnectionState.FAILED
            AppServerHealth.DISCONNECTED -> BuddyConnectionState.DISCONNECTED
        }

    /**
     * Display name for the saved connection mode. The stored keys stay
     * untranslated because other code compares them.
     */
    fun sourceTitle(
        isLocal: Boolean,
        source: String?,
        hasAlleycatNode: Boolean,
        preferredConnectionMode: String?,
        hasWebsocketUrl: Boolean,
    ): String =
        when {
            isLocal -> "在这台手机上运行"
            hasAlleycatNode -> "点对点连接"
            hasWebsocketUrl -> "远程连接"
            preferredConnectionMode == "ssh" || source == "ssh" -> "SSH"
            source == "bonjour" || source == "lanProbe" || source == "arpScan" -> "局域网"
            source == "tailscale" -> "Tailscale"
            source == "manual" -> "手动地址"
            else -> "远程连接"
        }

    /** The selected host, else the first connected one, else the first host. */
    fun primary(hosts: List<HostSummary>, selectedServerId: String?): HostSummary? =
        hosts.firstOrNull { it.serverId == selectedServerId }
            ?: hosts.firstOrNull { it.connection == BuddyConnectionState.CONNECTED }
            ?: hosts.firstOrNull()
}
