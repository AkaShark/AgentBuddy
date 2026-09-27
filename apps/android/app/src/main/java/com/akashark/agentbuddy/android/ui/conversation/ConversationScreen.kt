package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.akashark.agentbuddy.android.push.PUSH_PREFS
import com.akashark.agentbuddy.android.push.PushNotifications
import com.akashark.agentbuddy.android.push.shouldShowUnsupportedHostHint
import com.akashark.agentbuddy.android.push.unsupportedHostHintDismissedKey
import com.akashark.agentbuddy.android.state.isActiveStatus
import com.akashark.agentbuddy.android.ui.ChatWallpaperBackground
import com.akashark.agentbuddy.android.ui.ConversationPrefs
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperType
import com.akashark.agentbuddy.android.ui.isNearListBottom
import com.akashark.agentbuddy.android.ui.rememberStickyFollowTail
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppHostPushSupport
import uniffi.codex_mobile_client.AppRenameThreadRequest
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.ThreadKey

/**
 * Main conversation screen with turn grouping, scroll-to-bottom FAB,
 * pinned context strip, gradient fade, and inline user input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    threadKey: ThreadKey,
    onBack: () -> Unit,
    onInfo: (() -> Unit)? = null,
    onNavigateToSessions: (() -> Unit)? = null,
    onShowDirectoryPicker: (() -> Unit)? = null,
    onOpenSavedApp: ((String) -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    ConversationRenderPrewarm(appModel = appModel, context = context)

    val thread = remember(snapshot, threadKey) {
        appModel.threadSnapshot(threadKey)
    }
    val server = remember(snapshot, threadKey) {
        snapshot?.servers?.find { it.serverId == threadKey.serverId }
    }
    // Host push design §9: a host without push.v1 cannot report completions;
    // say so (dismissible per server) instead of a silent keep-alive. Re-probed
    // when the server connects (capability known) or the app resumes
    // (notification permission may have changed).
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var showUnsupportedHostHint by remember(threadKey.serverId) { mutableStateOf(false) }
    LaunchedEffect(threadKey.serverId, server?.health, isResumed) {
        val support = runCatching { appModel.client.hostPushSupport(threadKey.serverId) }
            .getOrDefault(AppHostPushSupport.NOT_APPLICABLE)
        val dismissed = context.getSharedPreferences(PUSH_PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(unsupportedHostHintDismissedKey(threadKey.serverId), false)
        showUnsupportedHostHint = shouldShowUnsupportedHostHint(
            support = support,
            notificationsEnabled = PushNotifications.areEnabled(context),
            dismissed = dismissed,
        )
    }
    val items = thread?.hydratedConversationItems ?: emptyList()
    val normalizedActiveTurnId = thread?.activeTurnId?.trim()?.takeIf { it.isNotEmpty() }
    val isThinking = thread?.info?.status?.isActiveStatus == true
    val minigameOverlay by appModel.minigameOverlay.collectAsState()
    val isMinigameActive = minigameOverlay !is com.akashark.agentbuddy.android.state.MinigameOverlayState.Idle
    val collapseTurns = ConversationPrefs.areTurnsCollapsed
    val agentDirectoryVersion = snapshot?.agentDirectoryVersion ?: 0uL
    val transcriptTurns = remember(items, thread?.info?.status, isThinking, collapseTurns) {
        buildTranscriptTurns(
            items = items,
            isStreaming = isThinking,
            expandedRecentTurnCount = if (collapseTurns) 1 else Int.MAX_VALUE,
        )
    }
    val transcriptTailSignature = remember(items, normalizedActiveTurnId, isThinking) {
        conversationTranscriptTailSignature(items, normalizedActiveTurnId, isThinking)
    }
    // Server-paginated windowing: the Rust reducer owns which turns are
    // currently loaded for this thread. Kotlin just renders whatever is in
    // `hydratedConversationItems` and exposes a "Load earlier" button gated
    // on `olderTurnsCursor`. On legacy v0.124 servers this cursor stays null
    // (all turns arrive in the resume response), so the button stays hidden.
    val displayedTurns = transcriptTurns
    val hasMoreTurnsAbove = thread?.olderTurnsCursor != null
    val supportsTurnPagination = server?.capabilities?.supportsTurnPagination == true
    val isInitialTurnsLoading = thread != null &&
        !thread.initialTurnsLoaded &&
        supportsTurnPagination &&
        hasMoreTurnsAbove &&
        displayedTurns.isNotEmpty()
    var isLoadingOlderTurns by remember(threadKey) { mutableStateOf(false) }
    var expandedTurnIds by remember(threadKey, collapseTurns) { mutableStateOf(setOf<String>()) }
    var streamingRenderTick by remember(threadKey) { mutableStateOf(0) }
    var followScrollToken by remember(threadKey) { mutableStateOf(0) }
    var hasPositionedInitialTail by remember(threadKey) { mutableStateOf(false) }
    var waitingForDataExpired by remember(threadKey) { mutableStateOf(false) }
    LaunchedEffect(threadKey) {
        waitingForDataExpired = false
        kotlinx.coroutines.delay(1000)
        waitingForDataExpired = true
    }
    val threadHasServerData = thread?.let {
        !it.info.preview.isNullOrBlank() || !it.info.title.isNullOrBlank()
    } == true
    val isWaitingForData = items.isEmpty() && threadHasServerData && !waitingForDataExpired
    var lastObservedUpdatedAt by remember(threadKey) { mutableStateOf<Long?>(null) }
    LaunchedEffect(transcriptTurns.map { it.id to it.isCollapsedByDefault }) {
        val validIds = transcriptTurns.mapTo(mutableSetOf()) { it.id }
        expandedTurnIds = expandedTurnIds.intersect(validIds)
    }
    LaunchedEffect(thread?.info?.updatedAt, isThinking) {
        val updatedAt = thread?.info?.updatedAt
        if (updatedAt != null && updatedAt != lastObservedUpdatedAt && isThinking) {
            followScrollToken += 1
        }
        lastObservedUpdatedAt = updatedAt
    }

    ConversationThreadEffects(
        appModel = appModel,
        threadKey = threadKey,
        thread = thread,
    )

    var showModelSelector by remember { mutableStateOf(false) }
    var showCollaborationModeSelector by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember(threadKey) { mutableStateOf("") }
    var showPermissionsSheet by remember { mutableStateOf(false) }
    var showExperimentalSheet by remember { mutableStateOf(false) }
    var showSkillsSheet by remember { mutableStateOf(false) }
    var showSessionDiffSheet by remember { mutableStateOf(false) }
    var slashErrorMessage by remember { mutableStateOf<String?>(null) }
    var reloadErrorMessage by remember { mutableStateOf<String?>(null) }
    var collaborationModesLoading by remember { mutableStateOf(false) }
    var collaborationModePresets by remember {
        mutableStateOf<List<uniffi.codex_mobile_client.AppCollaborationModePreset>>(emptyList())
    }
    LaunchedEffect(showModelSelector, server?.health, server?.account, server?.availableModels, server?.rateLimits) {
        if (showModelSelector || (server?.account != null && server.rateLimits == null)) {
            appModel.loadConversationMetadataIfNeeded(threadKey.serverId)
        }
    }
    LaunchedEffect(showCollaborationModeSelector) {
        if (!showCollaborationModeSelector || collaborationModesLoading) return@LaunchedEffect
        collaborationModesLoading = true
        collaborationModePresets = try {
            appModel.client.listCollaborationModes(threadKey.serverId)
        } catch (_: Exception) {
            fallbackCollaborationModePresets()
        }
        collaborationModesLoading = false
    }

    // Pending user input for this thread. The dismissal ledger is shared with
    // the global ApprovalOverlay via [LocalDismissedUserInputs] so dismissing
    // from either surface hides the request everywhere.
    val dismissedUserInputs = com.akashark.agentbuddy.android.ui.LocalDismissedUserInputs.current
    val pendingInput = remember(snapshot, threadKey, dismissedUserInputs.ids) {
        snapshot?.pendingUserInputs?.firstOrNull {
            it.isRelevantToThread(threadKey) &&
                !dismissedUserInputs.isDismissed(it.id)
        }
    }

    val activeTaskSummary = remember(items) { conversationActiveTaskSummary(items) }

    // Pinned context: latest TODO progress + combined session diff summary
    val pinnedContext = remember(items) { conversationPinnedContext(items) }

    // Auto-scroll state
    val listState = rememberLazyListState()
    val shouldFollowTail = rememberStickyFollowTail(
        listState = listState,
        resetKey = threadKey,
    )
    val isAtBottom by remember {
        derivedStateOf {
            listState.isNearListBottom()
        }
    }

    val displayedTurnCount = displayedTurns.size + (if (hasMoreTurnsAbove) 1 else 0)
    LaunchedEffect(threadKey, displayedTurnCount, transcriptTailSignature, followScrollToken, streamingRenderTick) {
        if (shouldFollowTail && displayedTurns.isNotEmpty()) {
            val bottomAnchorIndex = conversationBottomAnchorIndex(displayedTurnCount)
            if (hasPositionedInitialTail) {
                listState.animateScrollToItem(bottomAnchorIndex)
            } else {
                listState.scrollToItem(bottomAnchorIndex)
                hasPositionedInitialTail = true
            }
        }
    }

    val wallpaperVersion = WallpaperManager.version
    val hasWallpaper = remember(threadKey, wallpaperVersion) {
        WallpaperManager.resolvedConfig(threadKey)?.type?.let { it != WallpaperType.NONE } == true
    }
    val headerScrimColor = if (hasWallpaper) AgentBuddyTheme.background.copy(alpha = 0.75f) else AgentBuddyTheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper fills the entire screen edge-to-edge (behind status + nav bars)
        ChatWallpaperBackground(threadKey = threadKey)

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header with status bar inset built-in — extends behind status bar with scrim
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerScrimColor),
            ) {
                Spacer(Modifier.statusBarsPadding())
                HeaderBar(
                    thread = thread,
                    onBack = onBack,
                    onInfo = onInfo,
                    showModelSelector = showModelSelector,
                    onToggleModelSelector = { showModelSelector = !showModelSelector },
                    onShowPermissions = { showPermissionsSheet = true },
                    onShowCollaborationMode = { showCollaborationModeSelector = true },
                    onReloadError = { reloadErrorMessage = it },
                    transparentBackground = hasWallpaper,
                )
            }

            // Message list with gradient fade and scroll FAB
            Box(modifier = Modifier.weight(1f)) {
                if (thread == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AgentBuddyTheme.accent)
                    }
                } else {
                    ConversationTranscriptList(
                        appModel = appModel,
                        scope = scope,
                        threadKey = threadKey,
                        thread = thread,
                        items = items,
                        listState = listState,
                        hasWallpaper = hasWallpaper,
                        isWaitingForData = isWaitingForData,
                        isInitialTurnsLoading = isInitialTurnsLoading,
                        hasMoreTurnsAbove = hasMoreTurnsAbove,
                        isLoadingOlderTurns = isLoadingOlderTurns,
                        onLoadOlderTurns = loadOlderTurns@{
                            if (isLoadingOlderTurns) return@loadOlderTurns
                            isLoadingOlderTurns = true
                            scope.launch {
                                try {
                                    appModel.loadOlderTurns(threadKey).join()
                                } finally {
                                    isLoadingOlderTurns = false
                                }
                            }
                        },
                        displayedTurns = displayedTurns,
                        expandedTurnIds = expandedTurnIds,
                        onExpandTurn = { turnId -> expandedTurnIds = expandedTurnIds + turnId },
                        onCollapseTurn = { turnId -> expandedTurnIds = expandedTurnIds - turnId },
                        agentDirectoryVersion = agentDirectoryVersion,
                        onStreamingSnapshotRendered = { streamingRenderTick += 1 },
                        onOpenSavedApp = onOpenSavedApp,
                    )
                }

                // Scroll-to-bottom FAB
                if (!isAtBottom && displayedTurns.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(conversationBottomAnchorIndex(displayedTurnCount))
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        containerColor = AgentBuddyTheme.surface,
                        contentColor = AgentBuddyTheme.textPrimary,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "滚动到底部", modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Bottom area: gradient fade + pinned context + composer + nav bar inset
            // Hidden while the thinking-minigame overlay is up.
            if (!isMinigameActive) ConversationBottomArea(
                appModel = appModel,
                threadKey = threadKey,
                thread = thread,
                server = server,
                items = items,
                hasWallpaper = hasWallpaper,
                headerScrimColor = headerScrimColor,
                pinnedContext = pinnedContext,
                showUnsupportedHostHint = showUnsupportedHostHint,
                onDismissUnsupportedHostHint = {
                    context.getSharedPreferences(PUSH_PREFS, android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(unsupportedHostHintDismissedKey(threadKey.serverId), true)
                        .apply()
                    showUnsupportedHostHint = false
                },
                activeTaskSummary = activeTaskSummary,
                pendingInput = pendingInput,
                onShowSessionDiffSheet = { showSessionDiffSheet = true },
                onShowCollaborationModeSelector = { showCollaborationModeSelector = true },
                onToggleModelSelector = { showModelSelector = !showModelSelector },
                onNavigateToSessions = onNavigateToSessions,
                onShowDirectoryPicker = onShowDirectoryPicker,
                onShowRenameDialog = { initialName ->
                    val trimmed = initialName?.trim().orEmpty()
                    if (trimmed.isNotEmpty()) {
                        scope.launch {
                            try {
                                appModel.client.renameThread(
                                    threadKey.serverId,
                                    AppRenameThreadRequest(
                                        threadId = threadKey.threadId,
                                        name = trimmed,
                                    ),
                                )
                                appModel.refreshThreadSnapshot(threadKey)
                            } catch (e: Exception) {
                                slashErrorMessage = e.message ?: "重命名会话失败"
                            }
                        }
                    } else {
                        renameDraft = thread?.info?.title?.takeIf { it.isNotBlank() }.orEmpty()
                        showRenameDialog = true
                    }
                },
                onShowPermissionsSheet = { showPermissionsSheet = true },
                onShowExperimentalSheet = { showExperimentalSheet = true },
                onShowSkillsSheet = { showSkillsSheet = true },
                onSlashError = { slashErrorMessage = it },
                onDismissPendingUserInput = {
                    pendingInput?.let { dismissedUserInputs.dismiss(it.id) }
                },
            )
        }

        ConversationMinigameOverlay(
            appModel = appModel,
            threadKey = threadKey,
            items = items,
            isMinigameActive = isMinigameActive,
            minigameOverlay = minigameOverlay,
        )

        ConversationSheets(
            appModel = appModel,
            scope = scope,
            threadKey = threadKey,
            thread = thread,
            showPermissionsSheet = showPermissionsSheet,
            onDismissPermissionsSheet = { showPermissionsSheet = false },
            showCollaborationModeSelector = showCollaborationModeSelector,
            onDismissCollaborationModeSelector = { showCollaborationModeSelector = false },
            collaborationModePresets = collaborationModePresets,
            collaborationModesLoading = collaborationModesLoading,
            showExperimentalSheet = showExperimentalSheet,
            onDismissExperimentalSheet = { showExperimentalSheet = false },
            showSkillsSheet = showSkillsSheet,
            onDismissSkillsSheet = { showSkillsSheet = false },
            showSessionDiffSheet = showSessionDiffSheet,
            onDismissSessionDiffSheet = { showSessionDiffSheet = false },
            pinnedContext = pinnedContext,
            onSlashError = { slashErrorMessage = it },
        )

        ConversationDialogs(
            appModel = appModel,
            scope = scope,
            threadKey = threadKey,
            thread = thread,
            showRenameDialog = showRenameDialog,
            onDismissRenameDialog = { showRenameDialog = false },
            renameDraft = renameDraft,
            onRenameDraftChange = { renameDraft = it },
            slashErrorMessage = slashErrorMessage,
            onDismissSlashError = { slashErrorMessage = null },
            reloadErrorMessage = reloadErrorMessage,
            onDismissReloadError = { reloadErrorMessage = null },
            onSlashError = { slashErrorMessage = it },
        )
    }
}

private fun PendingUserInputRequest.isRelevantToThread(threadKey: ThreadKey): Boolean {
    if (serverId != threadKey.serverId) return false

    val requestThreadId = threadId.trim()
    return requestThreadId.isEmpty() || requestThreadId == threadKey.threadId
}

private fun conversationBottomAnchorIndex(turnCount: Int): Int = turnCount + 1
