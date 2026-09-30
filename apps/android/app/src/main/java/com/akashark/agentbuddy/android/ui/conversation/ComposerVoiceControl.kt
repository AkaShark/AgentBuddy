package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize

/**
 * Dictation slot of the composer: stop while recording, a spinner while
 * transcribing, otherwise the dictation mic (iOS parity; realtime voice starts
 * from the home composer instead).
 */
@Composable
internal fun ComposerVoiceControl(
    isRecording: Boolean,
    isTranscribing: Boolean,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
) {
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
