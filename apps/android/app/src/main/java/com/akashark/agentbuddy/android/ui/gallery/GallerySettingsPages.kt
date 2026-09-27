package com.akashark.agentbuddy.android.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.settings.AccountSheetActions
import com.akashark.agentbuddy.android.ui.settings.AccountSheetContent
import com.akashark.agentbuddy.android.ui.settings.AppearanceActions
import com.akashark.agentbuddy.android.ui.settings.AppearanceContent
import com.akashark.agentbuddy.android.ui.settings.ServerEditContent
import com.akashark.agentbuddy.android.ui.settings.ServerEditFormActions
import com.akashark.agentbuddy.android.ui.settings.SettingsTopLevelActions
import com.akashark.agentbuddy.android.ui.settings.SettingsTopLevelContent
import com.akashark.agentbuddy.android.ui.settings.ThemePickerContent
import com.akashark.agentbuddy.android.ui.settings.WallpaperApplyTargets
import com.akashark.agentbuddy.android.ui.settings.WallpaperControlsPanel
import com.akashark.agentbuddy.android.ui.settings.WallpaperEffects
import com.akashark.agentbuddy.android.ui.settings.WallpaperScreenLayout
import com.akashark.agentbuddy.android.ui.settings.WallpaperSourcePicker

/** `settings`: top-level settings list; switches and font choice toggle locally. */
@Composable
internal fun GallerySettingsPage() {
    var state by remember { mutableStateOf(GallerySettingsFixtures.topLevel) }
    val noop = GallerySettingsFixtures.noopTopLevelActions
    SettingsTopLevelContent(
        state = state,
        actions =
            SettingsTopLevelActions(
                onDone = noop.onDone,
                onOpenAppearance = noop.onOpenAppearance,
                onSelectFont = { state = state.copy(monoFontEnabled = it) },
                onCollapseTurnsChange = { state = state.copy(collapseTurns = it) },
                onShowHomeTaskDetailsChange = { state = state.copy(showHomeTaskDetails = it) },
                onPetVisibleChange = { state = state.copy(petVisible = it) },
                onOpenPets = noop.onOpenPets,
                onOpenApps = noop.onOpenApps,
                onOpenExperimental = noop.onOpenExperimental,
                onOpenDebug = noop.onOpenDebug,
                onOpenAccount = noop.onOpenAccount,
                onEditServer = noop.onEditServer,
                onRenameServer = noop.onRenameServer,
                onRemoveServer = noop.onRemoveServer,
            ),
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/** `account`: local server signed in with ChatGPT and a saved API key. */
@Composable
internal fun GalleryAccountPage() {
    var state by remember { mutableStateOf(GallerySettingsFixtures.account) }
    AccountSheetContent(
        state = state,
        actions =
            AccountSheetActions(
                onDismiss = {},
                onLogin = {},
                onLogout = {},
                onApiKeyChange = { state = state.copy(apiKey = it) },
                onSaveApiKey = {},
                onBaseUrlChange = { state = state.copy(baseUrl = it) },
                onSaveBaseUrl = {},
                onClearBaseUrl = {},
            ),
        modifier = galleryPageModifier(),
    )
}

/** `server-edit`: manually added SSH host; the mode switch and fields edit locally. */
@Composable
internal fun GalleryServerEditPage() {
    var state by remember { mutableStateOf(GallerySettingsFixtures.serverEdit) }
    ServerEditContent(
        state = state,
        actions =
            ServerEditFormActions(
                onDismiss = {},
                onDisplayNameChange = { state = state.copy(displayName = it) },
                onModeChange = { state = state.copy(mode = it) },
                onHostChange = { state = state.copy(host = it) },
                onSshPortChange = { state = state.copy(sshPort = it) },
                onWakeMacChange = { state = state.copy(wakeMac = it) },
                onCodexPortChange = { state = state.copy(codexPort = it) },
                onWebsocketUrlChange = { state = state.copy(websocketUrl = it) },
                onSave = {},
                onSaveAndReconnect = { state = state.copy(isReconnecting = !state.isReconnecting) },
            ),
        modifier = galleryPageModifier(),
    )
}

@Composable
private fun galleryPageModifier(): Modifier =
    Modifier
        .fillMaxSize()
        .background(AgentBuddyTheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)

/** `appearance`: mode, text size with the live preview, wallpaper and theme rows. */
@Composable
internal fun GalleryAppearancePage() {
    var state by remember { mutableStateOf(GallerySettingsFixtures.appearance()) }
    AppearanceContent(
        state = state,
        actions =
            AppearanceActions(
                onBack = {},
                onModeChange = { state = state.copy(mode = it) },
                onTextSizeStepChange = { state = state.copy(textSizeStep = it) },
                onPickWallpaper = {},
                onRemoveWallpaper = { state = state.copy(hasWallpaper = false) },
                onOpenLightThemes = {},
                onOpenDarkThemes = {},
            ),
        wallpaper = {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(AgentBuddyTheme.surfaceSoft, AgentBuddyTheme.background, AgentBuddyTheme.brand.copy(alpha = 0.35f)),
                        ),
                    ),
            )
        },
        modifier = galleryPageModifier(),
    )
}

/** `themes`: light theme picker from the bundled theme index (推荐 + 全部主题). */
@Composable
internal fun GalleryThemesPage() {
    var selected by remember { mutableStateOf(AgentBuddyThemeManager.lightTheme.slug) }
    ThemePickerContent(
        title = "浅色主题",
        themes = AgentBuddyThemeManager.lightThemes,
        selectedSlug = selected,
        onSelect = { selected = it },
        onDismiss = {},
        modifier = galleryPageModifier(),
    )
}

/** `wallpaper`: wallpaper picker chrome over a sample preview, sources expanded. */
@Composable
internal fun GalleryWallpaperPage() {
    var effects by remember { mutableStateOf(WallpaperEffects(blur = 0f, motionEnabled = false, brightness = 0.8f)) }
    var selectedSlug by remember { mutableStateOf<String?>(AgentBuddyThemeManager.themeIndex.firstOrNull()?.slug) }
    var videoUrl by remember { mutableStateOf("") }
    WallpaperScreenLayout(
        title = "选择壁纸",
        onBack = {},
        isProcessing = false,
        preview = {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(AgentBuddyTheme.brand, AgentBuddyTheme.surfaceSoft, AgentBuddyTheme.background),
                        ),
                    ),
            )
        },
        panel = {
            WallpaperControlsPanel(
                title = "壁纸控件",
                collapsedSubtitle = "已选择「主题」。点击展开控件。",
                expandedSubtitle = "已选择「主题」。可在此调整，或展开下方挑选新的壁纸。",
                effects = effects,
                onBlurChange = { effects = effects.copy(blur = if (it) 0.75f else 0f) },
                onMotionChange = { effects = effects.copy(motionEnabled = it) },
                onBrightnessChange = { effects = effects.copy(brightness = it) },
                targets = WallpaperApplyTargets(thread = true, server = true, serverIsPrimary = false),
                onApplyThread = {},
                onApplyServer = {},
                initiallySourcesExpanded = true,
                sourcePicker = {
                    WallpaperSourcePicker(
                        themes = AgentBuddyThemeManager.themeIndex,
                        selectedThemeSlug = selectedSlug,
                        noneSelected = selectedSlug == null,
                        isProcessingVideo = false,
                        videoUrl = videoUrl,
                        onVideoUrlChange = { videoUrl = it },
                        onSelectNone = { selectedSlug = null },
                        onSelectTheme = { selectedSlug = it.slug },
                        onPickPhoto = {},
                        onPickVideo = {},
                        onSetColor = {},
                        onSubmitVideoUrl = {},
                    )
                },
            )
        },
    )
}
