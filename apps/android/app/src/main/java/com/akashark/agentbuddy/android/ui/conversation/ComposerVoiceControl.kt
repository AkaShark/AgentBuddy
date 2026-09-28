package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.VoiceRuntimeController
import com.akashark.agentbuddy.android.ui.AgentBuddyFeature
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.ExperimentalFeatures
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.voice.InlineVoiceButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

/**
 * Dictation / realtime voice slot of the composer: stop while recording, a
 * spinner while transcribing, the realtime voice button (feature flag) when
 * the composer is empty, otherwise the dictation mic.
 */
@Composable
internal fun ComposerVoiceControl(
    appModel: AppModel,
    threadKey: ThreadKey,
    scope: CoroutineScope,
    isRecording: Boolean,
    isTranscribing: Boolean,
    hasContent: Boolean,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
) {
    val realtimeAvailable = remember { ExperimentalFeatures.isEnabled(AgentBuddyFeature.REALTIME_VOICE) }
    when {
        isRecording -> BuddyIconButton(
            icon = Icons.Filled.Stop,
            contentDescription = "停止录音",
            onClick = onStopDictation,
            tone = BuddyIconButtonTone.SOFT,
            iconSize = 16.dp,
            tint = AgentBuddyTheme.danger,
        )
        isTranscribing -> Box(
            modifier = Modifier
                .size(BuddySize.minHitTarget)
                .semantics { contentDescription = "正在转写" },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = AgentBuddyTheme.textSecondary,
                strokeWidth = 2.dp,
            )
        }
        realtimeAvailable && !hasContent -> {
            val voiceController = remember { VoiceRuntimeController.shared }
            val voiceSession by voiceController.activeVoiceSession.collectAsState()
            val snapshot by appModel.snapshot.collectAsState()
            InlineVoiceButton(
                phase = snapshot?.voiceSession?.phase,
                inputLevel = voiceSession?.inputLevel ?: 0f,
                isAvailable = true,
                onStart = { scope.launch { voiceController.startVoiceOnThread(appModel, threadKey) } },
                onStop = { scope.launch { voiceController.stopActiveVoiceSession(appModel) } },
            )
        }
        else -> ComposerDictationButton(onClick = onStartDictation)
    }
}

/** Dictation mic (also used by the DEBUG gallery). */
@Composable
internal fun ComposerDictationButton(onClick: () -> Unit, enabled: Boolean = true) {
    BuddyIconButton(
        icon = Icons.Outlined.Mic,
        contentDescription = "语音输入",
        onClick = onClick,
        enabled = enabled,
        tint = AgentBuddyTheme.textSecondary,
    )
}
