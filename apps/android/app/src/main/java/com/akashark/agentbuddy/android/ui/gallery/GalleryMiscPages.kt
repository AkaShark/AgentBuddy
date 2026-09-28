package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.apps.AppsListContent
import com.akashark.agentbuddy.android.ui.apps.BrokenPlaceholder
import com.akashark.agentbuddy.android.ui.apps.SavedAppUpdateOverlay
import com.akashark.agentbuddy.android.ui.apps.TopBar
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.terminal.TerminalChrome
import com.akashark.agentbuddy.android.ui.terminal.TerminalConfigContent
import com.akashark.agentbuddy.android.ui.terminal.TerminalHeader
import com.akashark.agentbuddy.android.ui.terminal.TerminalKeyBar
import com.akashark.agentbuddy.android.ui.terminal.TerminalThemeChoice
import com.akashark.agentbuddy.android.ui.voice.RealtimeVoiceContent
import com.akashark.agentbuddy.android.ui.voice.RealtimeVoiceUiState

/** `apps`: saved apps list; swipes rename (no-op) and delete locally. */
@Composable
internal fun GalleryAppsPage() {
    var apps by remember { mutableStateOf(GalleryMiscFixtures.apps) }
    AppsListContent(
        apps = apps,
        onBack = {},
        onOpenApp = {},
        onRename = {},
        onDelete = { app -> apps = apps.filterNot { it.id == app.id } },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/** `apps-empty`: no saved apps yet. */
@Composable
internal fun GalleryAppsEmptyPage() {
    AppsListContent(
        apps = emptyList(),
        onBack = {},
        onOpenApp = {},
        onRename = {},
        onDelete = {},
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/** `app-update`: saved app header over a sample widget with the update card open. */
@Composable
internal fun GalleryAppUpdatePage() {
    var updating by remember { mutableStateOf(false) }
    Column(galleryInsetsModifier()) {
        TopBar(
            title = "番茄钟",
            onBack = {},
            onRename = {},
            onUpdate = {},
            onDelete = {},
            onViewConversation = {},
            isUpdating = updating,
        )
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(BuddySpacing.xl).buddyCard(BuddySurfaceTone.SOFT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            ) {
                Text("25:00", style = buddyTextStyle(BuddyTextStyle.DISPLAY), color = AgentBuddyTheme.textPrimary)
                Text("专注一下", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textSecondary)
            }
            SavedAppUpdateOverlay(
                currentTitle = "番茄钟",
                onDismiss = {},
                onSubmit = { updating = true },
                isSubmitting = updating,
            )
        }
    }
}

/** `app-broken`: the app's files are missing. */
@Composable
internal fun GalleryAppBrokenPage() {
    Column(galleryInsetsModifier()) {
        TopBar(title = "", onBack = {}, onRename = {}, onUpdate = {}, onDelete = {}, onViewConversation = null)
        BrokenPlaceholder(onDelete = {})
    }
}

/** `voice`: listening with a short transcript; the speaker toggle flips locally. */
@Composable
internal fun GalleryVoicePage() {
    GalleryVoice(GalleryMiscFixtures.voice)
}

/** `voice-connecting`: connecting without microphone permission. */
@Composable
internal fun GalleryVoiceConnectingPage() {
    GalleryVoice(GalleryMiscFixtures.voiceConnecting)
}

@Composable
private fun GalleryVoice(initial: RealtimeVoiceUiState) {
    var state by remember { mutableStateOf(initial) }
    RealtimeVoiceContent(
        state = state,
        listState = rememberLazyListState(),
        onToggleSpeaker = { state = state.copy(isSpeakerOn = !state.isSpeakerOn) },
        onEnd = {},
        onRequestMicPermission = { state = state.copy(errorMessage = null, needsMicPermission = false) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/** `terminal-chrome`: header, backend bar and key bar around sample output (renderer not shown). */
@Composable
internal fun GalleryTerminalChromePage() {
    var backend by remember { mutableStateOf(GalleryMiscFixtures.terminalBackends.first()) }
    Column(
        Modifier
            .fillMaxSize()
            .background(TerminalChrome.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TerminalHeader(
            phase = TerminalSessionController.Phase.RUNNING,
            exitCode = null,
            selectedBackend = backend,
            backendOptions = GalleryMiscFixtures.terminalBackends,
            onSelectBackend = { backend = it },
            onBack = {},
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(BuddySpacing.md)) {
            Text(
                text = GalleryMiscFixtures.terminalOutput,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = TerminalChrome.textPrimary,
            )
        }
        TerminalKeyBar(
            canSendInput = true,
            canPaste = false,
            canClear = true,
            canSendToAssistant = true,
            onSend = {},
            onPaste = {},
            onClear = {},
            onSendToAssistant = {},
        )
    }
}

/** `terminal-config`: the terminal settings sheet content. */
@Composable
internal fun GalleryTerminalConfigPage() {
    var fontSize by remember { mutableStateOf(13f) }
    var theme by remember { mutableStateOf(TerminalThemeChoice.AGENTBUDDY_DARK) }
    var blink by remember { mutableStateOf(true) }
    TerminalConfigContent(
        fontSize = fontSize,
        onFontSizeCommit = { fontSize = it },
        theme = theme,
        onThemeChange = { theme = it },
        cursorBlink = blink,
        onCursorBlinkChange = { blink = it },
        modifier = galleryInsetsModifier().padding(top = BuddySpacing.lg),
    )
}
