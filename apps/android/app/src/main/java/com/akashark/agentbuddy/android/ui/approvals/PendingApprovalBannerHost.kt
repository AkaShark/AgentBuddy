package com.akashark.agentbuddy.android.ui.approvals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.ThreadKey

/**
 * App-level approval banner. Shows requests whose approval stack is not on
 * screen; tapping opens that conversation (where the card lives). The card
 * expands inside the banner instead when opening would not help or would cost
 * something ([approvalBannerExpandsInPlace]): no thread reported, the thread
 * is already on screen without its stack (realtime voice, minigame open), or
 * [keepCurrentScreen] (an active voice call must not be torn down). The layout
 * never blocks touches outside the banner; it stays below a header registered
 * with [KeepApprovalBannerBelow].
 */
@Composable
internal fun PendingApprovalBannerHost(
    appModel: AppModel,
    approvals: List<PendingApproval>,
    onScreenThread: ThreadKey?,
    keepCurrentScreen: Boolean,
) {
    val coordinator = rememberApprovalCoordinator(appModel)
    val snapshot by appModel.snapshot.collectAsState()
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val pendingIds = approvals.map { it.id }
    LaunchedEffect(pendingIds) {
        // A closed banner comes back for new requests only.
        hiddenIds = hiddenIds.intersect(pendingIds.toSet())
        if (expandedId !in pendingIds) expandedId = null
    }
    val visible = approvals.filter { approval ->
        approval.isAnswerableOnPhone &&
            approval.id !in hiddenIds &&
            approval.threadKeyOrNull?.let(ApprovalStackVisibility::isShowing) != true
    }
    val first = visible.firstOrNull()
    // Keeps the last request so the banner can fade out after it is gone.
    val lastShown = remember { arrayOfNulls<Pair<PendingApproval, Int>>(1) }
    if (first != null) lastShown[0] = first to visible.size

    val reduceMotion = buddyReduceMotion
    val headerInsetPx = ApprovalBannerTopInset.px
    val topPlacement = if (headerInsetPx > 0) {
        Modifier.padding(top = with(LocalDensity.current) { headerInsetPx.toDp() })
    } else {
        Modifier.windowInsetsPadding(WindowInsets.statusBars)
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = first != null,
            enter = fadeIn(BuddyMotion.STATE.spec(reduceMotion)),
            exit = fadeOut(BuddyMotion.STATE.spec(reduceMotion)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topPlacement)
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        ) {
            val (approval, count) = lastShown[0] ?: return@AnimatedVisibility
            val server = snapshot?.servers?.firstOrNull { it.serverId == approval.serverId }
            val hostName = hostDisplayName(server?.displayName)
            val threadKey = approval.threadKeyOrNull
            val inPlace = approvalBannerExpandsInPlace(threadKey, onScreenThread, keepCurrentScreen)
            val expanded = inPlace && expandedId == approval.id
            val taskTitle = threadKey?.let { key ->
                appModel.threadSnapshot(key)?.info?.let { info ->
                    info.title?.takeIf { it.isNotBlank() } ?: info.preview?.takeIf { it.isNotBlank() }
                }
            }
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                PendingApprovalBannerContent(
                    count = count,
                    detail = approvalBannerDetail(hostName, taskTitle ?: ApprovalCopy.title(approval.kind)),
                    actionTitle = if (expanded) "收起" else "去处理",
                    onOpen = {
                        if (!inPlace && threadKey != null) {
                            ApprovalCoordinatorHolder.scope.launch { openApprovalThread(appModel, threadKey) }
                        } else {
                            expandedId = if (expanded) null else approval.id
                        }
                    },
                    onClose = {
                        hiddenIds = hiddenIds + visible.map { it.id }
                        expandedId = null
                    },
                )
                if (expanded) {
                    Box(
                        Modifier
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45f).dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ApprovalCard(
                            approval = approval,
                            hostName = hostName,
                            submittingDecision = coordinator.lockedDecisions[approval.id],
                            failure = coordinator.failures[approval.id],
                            onDecision = { decision -> submitApprovalDecision(appModel, coordinator, approval, decision) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether 「去处理」 expands the card inside the banner instead of opening the
 * request's thread: the thread is unknown, already on screen (its stack is
 * not, or the banner would not show), or the current screen must stay.
 */
internal fun approvalBannerExpandsInPlace(
    approvalThread: ThreadKey?,
    onScreenThread: ThreadKey?,
    keepCurrentScreen: Boolean,
): Boolean = approvalThread == null || keepCurrentScreen || approvalThread == onScreenThread

/**
 * Heights (px) of headers the banner must stay below, e.g. the conversation
 * header whose back and 「…」 buttons it would otherwise cover.
 */
internal object ApprovalBannerTopInset {
    private val insets = mutableStateMapOf<Any, Int>()

    val px: Int get() = insets.values.maxOrNull() ?: 0

    fun set(owner: Any, heightPx: Int) {
        insets[owner] = heightPx
    }

    fun clear(owner: Any) {
        insets.remove(owner)
    }
}

/**
 * Keeps the app-level approval banner below a header that is [heightPx] tall,
 * measured from the top of the window, while this is composed.
 */
@Composable
internal fun KeepApprovalBannerBelow(heightPx: Int) {
    val owner = remember { Any() }
    DisposableEffect(owner, heightPx) {
        ApprovalBannerTopInset.set(owner, heightPx)
        onDispose { ApprovalBannerTopInset.clear(owner) }
    }
}

/**
 * Opens [key] the way a notification tap does (load, then make it active; the
 * app navigates when the active thread changes). Going back from a
 * conversation keeps it active, so re-selecting the same thread first clears
 * the active key and gives the app a few frames to see the change.
 */
internal suspend fun openApprovalThread(appModel: AppModel, key: ThreadKey) {
    val resolved = appModel.ensureThreadLoaded(key) ?: key
    if (appModel.snapshot.value?.activeThread == resolved) {
        appModel.store.setActiveThread(null)
        withTimeoutOrNull(1_000) { appModel.snapshot.first { it?.activeThread != resolved } }
        delay(64)
    }
    appModel.activateThread(resolved)
}
