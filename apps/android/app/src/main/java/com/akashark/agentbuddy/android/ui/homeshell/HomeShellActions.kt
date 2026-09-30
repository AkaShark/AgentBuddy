package com.akashark.agentbuddy.android.ui.homeshell

import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.ThreadKey

/**
 * Navigation and sheet entry points the app root hands to the home shell, so
 * the tabs depend on one narrow interface. The root keeps owning navigation,
 * the root sheets and the home host / project selection.
 */
class HomeShellActions(
    val openConversation: (ThreadKey) -> Unit,
    /** 全部任务 (the sessions screen across every host). */
    val showAllTasks: () -> Unit,
    /** The sessions screen limited to one project, titled with its name. */
    val showProjectTasks: (project: AppProject, title: String) -> Unit,
    /** The root project picker (also used by the new-task sheet's project chip). */
    val openProjectPicker: () -> Unit,
    /** Local account sign-in, when creating a task needs it. */
    val openAccount: (serverId: String) -> Unit,
    /** Realtime voice, or null when the realtime_voice flag is off. */
    val startVoice: (() -> Unit)?,
    /** Scopes the task list to a host; null shows every host. */
    val selectServer: (serverId: String?) -> Unit,
    val selectProject: (AppProject) -> Unit,
    /** Opens the directory picker in project mode. */
    val createProject: () -> Unit,
    /** Opens the QR pairing sheet directly. */
    val pairWithQr: () -> Unit,
    /** Opens the full Discovery sheet (QR / Slingshot / SSH-URL chooser). */
    val showDiscovery: () -> Unit,
    val showSettings: () -> Unit,
    val showApps: () -> Unit,
    /** The local terminal, or null when the terminal flag is off. */
    val showTerminal: (() -> Unit)?,
    /** Remote shell on a paired alleycat host. */
    val openHostTerminal: (nodeId: String) -> Unit,
    /** Edit a host's saved connection (the Settings host list). */
    val editHost: (serverId: String) -> Unit,
)
