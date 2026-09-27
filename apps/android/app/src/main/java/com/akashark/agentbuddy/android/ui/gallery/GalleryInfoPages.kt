package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.ModelPanelActions
import com.akashark.agentbuddy.android.ui.common.ModelPanelState
import com.akashark.agentbuddy.android.ui.common.ModelSelectorPanelContent
import com.akashark.agentbuddy.android.ui.common.defaultReasoningEffortSelection
import com.akashark.agentbuddy.android.ui.conversation.ConversationInfoContent
import com.akashark.agentbuddy.android.ui.conversation.TaskInfoActions
import com.akashark.agentbuddy.android.ui.conversation.TaskInfoState

@Composable
internal fun galleryInsetsModifier(): Modifier =
    Modifier
        .fillMaxSize()
        .background(AgentBuddyTheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)

/** `info`: task info for a running task; 分叉 toggles the loading state, 重命名 shows the error banner. */
@Composable
internal fun GalleryInfoPage() {
    GalleryInfo(initial = GalleryInfoFixtures.task())
}

/** `info-server`: server info (wallpaper + terminal actions, usage, server details). */
@Composable
internal fun GalleryServerInfoPage() {
    GalleryInfo(initial = GalleryInfoFixtures.serverOnly())
}

@Composable
private fun GalleryInfo(initial: TaskInfoState) {
    var state by remember { mutableStateOf(initial) }
    ConversationInfoContent(
        state = state,
        actions =
            TaskInfoActions(
                onBack = {},
                onChangeWallpaper = {},
                onFork = { state = state.copy(isForking = !state.isForking) },
                onRename = { state = state.copy(actionError = "重命名失败：连接已断开，等搭子重连后再试。") },
                onOpenShell = {},
                onDismissError = { state = state.copy(actionError = null) },
            ),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/** `models`: model panel with three partners; every control edits local state. */
@Composable
internal fun GalleryModelsPage() {
    GalleryModels(initial = GalleryInfoFixtures.modelPanel)
}

/** `models-locked`: Amp after the first message (effort locked, partner-managed access). */
@Composable
internal fun GalleryModelsLockedPage() {
    GalleryModels(initial = GalleryInfoFixtures.lockedModelPanel)
}

@Composable
private fun GalleryModels(initial: ModelPanelState) {
    var state by remember { mutableStateOf(initial) }
    ModelSelectorPanelContent(
        state = state,
        actions =
            ModelPanelActions(
                onSelectModel = { model ->
                    state =
                        state.copy(
                            selectedModel = model.id,
                            selectedRuntime = model.agentRuntimeKind,
                            efforts = if (state.effortLocked) emptyList() else model.supportedReasoningEfforts,
                            selectedEffort = model.defaultReasoningEffortSelection(),
                        )
                },
                onSelectEffort = { state = state.copy(selectedEffort = it) },
                onPlanModeChange = { state = state.copy(planMode = it) },
                onFullAccessChange = { state = state.copy(fullAccess = it) },
                onFastModeChange = { state = state.copy(fastMode = it) },
            ),
        modifier = galleryInsetsModifier(),
        maxHeight = LocalConfiguration.current.screenHeightDp.dp,
    )
}
