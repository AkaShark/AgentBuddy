package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import uniffi.codex_mobile_client.AppVoiceTranscriptEntry

/** Render-only input of the realtime voice screen. */
internal data class RealtimeVoiceUiState(
    val phase: AppVoiceSessionPhase,
    val transcript: List<AppVoiceTranscriptEntry>,
    val inputLevel: Float,
    val outputLevel: Float,
    /** Last session error, or the missing microphone permission. */
    val errorMessage: String?,
    val needsMicPermission: Boolean,
    val isSpeakerOn: Boolean,
)

private val VoiceMaxContentWidth = 640.dp

/**
 * Mint realtime voice page: phase status (dot + text) on top, the transcript
 * styled like the conversation, an optional handoff card, an error banner
 * and two large labelled controls. The edge glow follows the audio level
 * unless reduced motion is on, then it holds a fixed level per phase.
 */
@Composable
internal fun RealtimeVoiceContent(
    state: RealtimeVoiceUiState,
    listState: LazyListState,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
    handoff: (@Composable () -> Unit)? = null,
) {
    val reduceMotion = buddyReduceMotion
    val glow =
        if (reduceMotion) {
            voiceGlowIntensity(state.phase, inputLevel = 0f, outputLevel = 0f)
        } else {
            voiceGlowIntensity(state.phase, state.inputLevel, state.outputLevel)
        }
    Box(modifier = modifier.fillMaxSize().background(AgentBuddyTheme.background)) {
        VoiceEdgeGlow(intensity = glow, phase = state.phase)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = BuddySpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoicePhasePill(phase = state.phase, modifier = Modifier.padding(top = BuddySpacing.xl))

            TranscriptContent(
                entries = state.transcript,
                phase = state.phase,
                inputLevel = state.inputLevel,
                outputLevel = state.outputLevel,
                listState = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(max = VoiceMaxContentWidth)
                        .fillMaxWidth(),
            )

            if (handoff != null) {
                Box(
                    Modifier
                        .widthIn(max = VoiceMaxContentWidth)
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(bottom = BuddySpacing.md),
                ) {
                    handoff()
                }
            }

            val error = state.errorMessage
            if (!error.isNullOrBlank()) {
                BuddyBanner(
                    tone = BuddyBannerTone.DANGER,
                    message = error,
                    actionTitle = if (state.needsMicPermission) "授予权限" else null,
                    onAction = if (state.needsMicPermission) onRequestMicPermission else null,
                    modifier = Modifier.widthIn(max = VoiceMaxContentWidth).padding(bottom = BuddySpacing.md),
                )
            }

            BottomControls(
                isSpeakerOn = state.isSpeakerOn,
                onToggleSpeaker = onToggleSpeaker,
                onEnd = onEnd,
                modifier = Modifier.padding(bottom = BuddySpacing.xxl),
            )
        }
    }
}

/** Phase as a pill: coloured dot (pulses while listening or speaking) plus text, so it never relies on colour. */
@Composable
private fun VoicePhasePill(
    phase: AppVoiceSessionPhase,
    modifier: Modifier = Modifier,
) {
    val label = voicePhaseLabel(phase)
    BuddyChromeTypeLimit {
        Row(
            modifier =
                modifier
                    .heightIn(min = 34.dp)
                    .background(AgentBuddyTheme.surface, CircleShape)
                    .border(1.dp, AgentBuddyTheme.border, CircleShape)
                    .padding(horizontal = BuddySpacing.sm)
                    .clearAndSetSemantics { contentDescription = "语音状态：$label" },
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VoiceScreenPulsingDot(
                color = voicePhaseColor(phase),
                isActive = phase == AppVoiceSessionPhase.LISTENING || phase == AppVoiceSessionPhase.SPEAKING,
            )
            Text(text = label, style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.textPrimary)
        }
    }
}

/** Speaker toggle and end call: 64dp circles with a caption under each. */
@Composable
internal fun BottomControls(
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxl),
            verticalAlignment = Alignment.Top,
            modifier = modifier,
        ) {
            VoiceControl(
                icon = if (isSpeakerOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.Outlined.PhoneInTalk,
                label = if (isSpeakerOn) "扬声器" else "听筒",
                fill = AgentBuddyTheme.surface,
                tint = AgentBuddyTheme.textPrimary,
                bordered = true,
                modifier =
                    Modifier
                        .toggleable(value = isSpeakerOn, role = Role.Switch, onValueChange = { onToggleSpeaker() })
                        .semantics {
                            contentDescription = "扬声器"
                            stateDescription = if (isSpeakerOn) "已开启，声音从扬声器播放" else "已关闭，声音从听筒播放"
                        },
            )
            VoiceControl(
                icon = Icons.Outlined.CallEnd,
                label = "结束",
                fill = AgentBuddyTheme.dangerSurface,
                tint = AgentBuddyTheme.danger,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onEnd)
                        .semantics { contentDescription = "结束语音对话" },
            )
        }
    }
}

@Composable
private fun VoiceControl(
    icon: ImageVector,
    label: String,
    fill: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).then(modifier).padding(BuddySpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .background(fill, CircleShape)
                    .then(if (bordered) Modifier.border(1.dp, AgentBuddyTheme.borderControl, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
