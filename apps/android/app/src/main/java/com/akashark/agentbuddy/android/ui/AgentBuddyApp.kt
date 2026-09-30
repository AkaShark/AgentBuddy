package com.akashark.agentbuddy.android.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.DebugSettings
import com.akashark.agentbuddy.android.state.NetworkDiscovery
import com.akashark.agentbuddy.android.state.PetOverlayController
import com.akashark.agentbuddy.android.state.SavedProjectStore
import com.akashark.agentbuddy.android.state.VisibleThreadTracker
import com.akashark.agentbuddy.android.ui.approvals.PendingApprovalBannerHost
import com.akashark.agentbuddy.android.ui.home.DashboardZoomPrefs
import com.akashark.agentbuddy.android.ui.home.HomeDashboardSupport
import com.akashark.agentbuddy.android.ui.pets.PetOverlayView
import com.akashark.agentbuddy.android.ui.sessions.SessionsUiState
import com.akashark.agentbuddy.android.ui.settings.SettingsStartDestination
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.deriveProjects

/**
 * CompositionLocal for accessing [AppModel] from any composable.
 */
val LocalAppModel = staticCompositionLocalOf<AppModel> {
    error("AppModel not provided")
}

/**
 * UI-only ledger of pending-user-input request IDs the user has manually dismissed.
 * Lives at the app shell so the inline composer prompt and the global approval
 * overlay agree on what's been hidden.
 */
class DismissedUserInputState {
    var ids by mutableStateOf(setOf<String>())
        private set

    fun dismiss(id: String) {
        ids = ids + id
    }

    fun isDismissed(id: String): Boolean = ids.contains(id)
}

val LocalDismissedUserInputs = staticCompositionLocalOf<DismissedUserInputState> {
    error("DismissedUserInputState not provided")
}

/**
 * Root composable for the app: navigation stack ([AppShellState]), routes
 * ([AppRouteContent]), root sheets ([AppRootSheets]) and global overlays.
 */
@Composable
fun AgentBuddyApp(
    appModel: AppModel,
    openPetSettingsRequest: Int = 0,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        TextSizePrefs.initialize(context)
        ConversationPrefs.initialize(context)
        DashboardZoomPrefs.initialize(context)
        ExperimentalFeatures.initialize(context)
        DebugSettings.initialize(context)
        PetOverlayController.initialize(context)
    }

    // Read currentStep so Compose tracks it as a dependency and recomposes on change.
    val textScale = ConversationTextSize.fromStep(TextSizePrefs.currentStep).scale
    val dismissedUserInputs = remember { DismissedUserInputState() }
    CompositionLocalProvider(
        LocalAppModel provides appModel,
        LocalTextScale provides textScale,
        LocalDismissedUserInputs provides dismissedUserInputs,
    ) {
        val snapshot by appModel.snapshot.collectAsState()
        val scope = rememberCoroutineScope()
        val shell = remember {
            AppShellState(SavedProjectStore.selectedServerId(context)).also { it.homeMemory.reload(context) }
        }
        val currentRoute = shell.currentRoute
        val sessionsUiState = remember { SessionsUiState() }
        val homeStateHolder = rememberSaveableStateHolder()
        val networkDiscovery = remember { NetworkDiscovery(appModel.discovery) }
        val navActions = remember { AppNavigationActions(appModel, context, scope, shell) }
        val terminalEnabled = ExperimentalFeatures.isEnabled(AgentBuddyFeature.TERMINAL)
        val homeActions = remember(terminalEnabled, ExperimentalFeatures.isEnabled(AgentBuddyFeature.REALTIME_VOICE)) {
            navActions.homeShellActions(
                voiceEnabled = ExperimentalFeatures.isEnabled(AgentBuddyFeature.REALTIME_VOICE),
                terminalEnabled = terminalEnabled,
            )
        }

        HomeSelectionEffects(shell, snapshot)
        val projects = remember(snapshot) { snapshot?.let { deriveProjects(it.sessionSummaries) } ?: emptyList() }
        HomeProjectReconcileEffect(shell, projects, snapshot?.servers?.map { it.serverId }?.toSet())

        LaunchedEffect(openPetSettingsRequest) {
            if (openPetSettingsRequest > 0) shell.openSettings(SettingsStartDestination.Pets)
        }

        BackHandler(enabled = shell.interceptsBack) {
            shell.handleBack(onCloseDiscovery = { networkDiscovery.stopScanning() })
        }

        // Auto-navigate to the active thread when it changes. Home-composer
        // sends don't call setActiveThread, so this only triggers for real
        // "open a thread" actions (notifications, voice handoff, fork, new session).
        LaunchedEffect(snapshot?.activeThread) {
            val activeKey = snapshot?.activeThread ?: return@LaunchedEffect
            val alreadyShowing = when (val route = currentRoute) {
                is Route.Conversation -> route.key == activeKey
                is Route.RealtimeVoice -> route.key == activeKey
                else -> false
            }
            if (!alreadyShowing) shell.navigateToConversation(activeKey)
        }

        // Lets the FCM service skip a completion notification for the
        // conversation already on screen.
        val visibleThreadKey = when (val route = currentRoute) {
            is Route.Conversation -> route.key
            is Route.RealtimeVoice -> route.key
            else -> null
        }
        DisposableEffect(visibleThreadKey) {
            VisibleThreadTracker.visibleThread = visibleThreadKey
            onDispose { VisibleThreadTracker.visibleThread = null }
        }

        val rootModifier = if (currentRoute is Route.Conversation || currentRoute is Route.Terminal) {
            Modifier.fillMaxSize().background(AgentBuddyTheme.background)
        } else {
            Modifier.fillMaxSize().background(AgentBuddyTheme.background).systemBarsPadding()
        }

        Box(modifier = rootModifier) {
            AppRouteContent(
                route = currentRoute,
                shell = shell,
                navActions = navActions,
                homeActions = homeActions,
                projects = projects,
                sessionsUiState = sessionsUiState,
                homeStateHolder = homeStateHolder,
                terminalEnabled = terminalEnabled,
            )

            val pet = PetOverlayController.selectedPet
            if (pet != null && PetOverlayController.shouldShowInAppOverlay(context)) {
                PetOverlayView(
                    pet = pet,
                    state = PetOverlayController.avatarState(snapshot),
                    message = PetOverlayController.avatarMessage(snapshot),
                    reducedMotion = context.animationsDisabled(),
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }

            // Approvals whose inline stack is not on screen: a compact top
            // banner. The open conversation shows its own inline approval stack;
            // the voice screen answers in the banner so the call keeps running.
            val approvals = snapshot?.pendingApprovals.orEmpty().filter {
                it.kind != ApprovalKind.MCP_ELICITATION
            }
            if (approvals.isNotEmpty()) {
                PendingApprovalBannerHost(
                    appModel = appModel,
                    approvals = approvals,
                    onScreenThread = visibleThreadKey,
                    keepCurrentScreen = currentRoute is Route.RealtimeVoice,
                )
            }
        }

        AppRootSheets(
            shell = shell,
            appModel = appModel,
            snapshot = snapshot,
            projects = projects,
            networkDiscovery = networkDiscovery,
            navActions = navActions,
        )
    }
}

/** Persists the host selection and clears it when that host is no longer connected. */
@Composable
private fun HomeSelectionEffects(shell: AppShellState, snapshot: uniffi.codex_mobile_client.AppSnapshotRecord?) {
    val context = LocalContext.current
    LaunchedEffect(shell.selectedServerId) {
        SavedProjectStore.setSelectedServerId(context, shell.selectedServerId)
    }
    LaunchedEffect(shell.selectedProject?.id) {
        SavedProjectStore.setSelectedProjectId(context, shell.selectedProject?.id)
    }
    // Default is no filter: if the persisted host isn't connected, clear it.
    LaunchedEffect(snapshot) {
        val connected = snapshot?.let { snap -> HomeDashboardSupport.sortedConnectedServers(snap).map { it.serverId } }.orEmpty()
        if (shell.selectedServerId != null && shell.selectedServerId !in connected) {
            shell.selectedServerId = null
        }
    }
}

/**
 * Keeps the selected project valid for the selected host (persisted id first,
 * else the first). Without a host filter a picked project stays: the filter is
 * also dropped automatically while its host is offline or reconnecting, and
 * clearing the project then undid the user's pick. It is dropped once its host
 * is removed ([serverIds] is null until the first snapshot). Choosing "all
 * hosts" clears the project explicitly in [AppNavigationActions].
 */
@Composable
private fun HomeProjectReconcileEffect(
    shell: AppShellState,
    projects: List<uniffi.codex_mobile_client.AppProject>,
    serverIds: Set<String>?,
) {
    val context = LocalContext.current
    LaunchedEffect(shell.selectedServerId, projects, serverIds) {
        val currentServerId = shell.selectedServerId ?: run {
            shell.selectedProject?.let { picked ->
                if (serverIds != null && picked.serverId !in serverIds) {
                    shell.selectedProject = null
                } else {
                    projects.firstOrNull { it.id == picked.id }?.let { shell.selectedProject = it }
                }
            }
            return@LaunchedEffect
        }
        val serverProjects = projects.filter { it.serverId == currentServerId }
        val current = shell.selectedProject
        if (current != null && current.serverId == currentServerId) {
            serverProjects.firstOrNull { it.id == current.id }?.let { shell.selectedProject = it }
            return@LaunchedEffect
        }
        val persistedId = SavedProjectStore.selectedProjectId(context)
        shell.selectedProject = serverProjects.firstOrNull { it.id == persistedId } ?: serverProjects.firstOrNull()
    }
}

private fun android.content.Context.animationsDisabled(): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
    }.getOrDefault(1f)
    return scale == 0f
}
