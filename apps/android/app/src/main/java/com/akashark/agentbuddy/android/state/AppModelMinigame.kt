package com.akashark.agentbuddy.android.state

import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppMinigameRequest
import uniffi.codex_mobile_client.AppMinigameResult

// Thinking-indicator minigame requests behind [AppModel]'s public API.

internal fun AppModel.requestMinigameImpl(
    parentThreadId: String,
    serverId: String,
    lastUserMessage: String?,
    lastAssistantMessage: String?,
) {
    if (!com.akashark.agentbuddy.android.ui.ExperimentalFeatures.isEnabled(
            com.akashark.agentbuddy.android.ui.AgentBuddyFeature.THINKING_MINIGAME
        )) return
    if (_minigameOverlay.value !is MinigameOverlayState.Idle) return
    _minigameOverlay.value = MinigameOverlayState.Loading
    minigameJob?.cancel()
    minigameJob = scope.launch {
        try {
            val result: AppMinigameResult = client.startMinigame(
                AppMinigameRequest(
                    serverId = serverId,
                    parentThreadId = parentThreadId,
                    lastUserMessage = lastUserMessage,
                    lastAssistantMessage = lastAssistantMessage,
                )
            )
            _minigameOverlay.value = MinigameOverlayState.Shown(
                MinigameContent(
                    html = result.widgetHtml,
                    title = result.title,
                    width = result.width.toFloat(),
                    height = result.height.toFloat(),
                )
            )
        } catch (t: Throwable) {
            _minigameOverlay.value = MinigameOverlayState.Failed(t.message ?: t.toString())
        }
    }
}

internal fun AppModel.dismissMinigameImpl() {
    minigameJob?.cancel()
    minigameJob = null
    _minigameOverlay.value = MinigameOverlayState.Idle
}
