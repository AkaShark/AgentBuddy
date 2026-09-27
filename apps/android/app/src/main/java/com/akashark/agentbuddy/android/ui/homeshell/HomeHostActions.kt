package com.akashark.agentbuddy.android.ui.homeshell

import android.content.Context
import com.akashark.agentbuddy.android.state.AlleycatCredentialStore
import com.akashark.agentbuddy.android.state.AppLifecycleController
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.SavedServerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Host management for the 主机 tab (formerly the home host pill's long-press
 * menu). Connection policy stays in Rust; this only calls the existing
 * lifecycle / bridge entry points.
 */
class HomeHostActions(
    private val appModel: AppModel,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (title: String, message: String) -> Unit,
) {
    private val lifecycleController = AppLifecycleController()

    fun reconnect(serverId: String) {
        scope.launch { lifecycleController.reconnectServer(context, appModel, serverId) }
    }

    /** Local: restart the in-process server. Remote: restart the app server, then reconnect. */
    fun restart(serverId: String, isLocal: Boolean) {
        scope.launch {
            try {
                if (isLocal) {
                    appModel.restartLocalServer()
                } else {
                    appModel.serverBridge.restartAppServer(serverId)
                    lifecycleController.reconnectServer(context, appModel, serverId)
                }
                appModel.refreshSnapshot()
            } catch (error: Exception) {
                onError("重启失败", error.message ?: "无法重启 App Server。")
            }
        }
    }

    fun rename(serverId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            SavedServerStore.rename(context, serverId, trimmed)
            appModel.refreshSnapshot()
        }
    }

    /** Forgets the saved connection; tasks already running on the host keep running. */
    fun remove(serverId: String) {
        scope.launch {
            SavedServerStore.remove(context, serverId)
            appModel.sshSessionStore.close(serverId)
            appModel.serverBridge.disconnectServer(serverId)
            appModel.refreshSnapshot()
        }
    }

    companion object {
        /**
         * The alleycat node id to open a remote shell on, when the host has a
         * saved alleycat token. Null means no terminal entry for this host.
         */
        fun terminalNodeId(context: Context, serverId: String): String? {
            val saved = SavedServerStore.remembered(context).firstOrNull { it.id == serverId } ?: return null
            val nodeId = saved.alleycatNodeId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            AlleycatCredentialStore(context.applicationContext)
                .loadToken(nodeId)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            return nodeId
        }
    }
}
