package com.akashark.agentbuddy.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.VideoWallpaperProcessor
import com.akashark.agentbuddy.android.ui.WallpaperConfig
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperScope
import com.akashark.agentbuddy.android.ui.WallpaperType
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

@Composable
fun WallpaperSelectionScreen(
    threadKey: ThreadKey? = null,
    serverId: String? = null,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val isServerOnly = threadKey == null
    val resolvedServerId = threadKey?.serverId ?: serverId
    val wallpaperScope: WallpaperScope? = if (threadKey != null) WallpaperScope.Thread(threadKey)
        else resolvedServerId?.let { WallpaperScope.Server(it) }
    val sourceScope: WallpaperScope? = if (threadKey != null) {
        WallpaperManager.resolvedScope(threadKey)
    } else {
        resolvedServerId?.let { WallpaperManager.resolvedScopeForServer(it) }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themes = AgentBuddyThemeManager.themeIndex
    var previewConfig by remember {
        mutableStateOf(
            WallpaperManager.pendingConfig ?: if (threadKey != null) WallpaperManager.resolvedConfig(threadKey)
            else resolvedServerId?.let { WallpaperManager.resolvedConfigForServer(it) }
        )
    }
    var isProcessingVideo by remember { mutableStateOf(false) }
    var videoUrlText by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = WallpaperManager.stagePendingImageFromUri(uri)
                if (success) {
                    val config = WallpaperConfig(type = WallpaperType.CUSTOM_IMAGE)
                    WallpaperManager.pendingConfig = config
                    previewConfig = config
                }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingVideo = true
            scope.launch {
                val result = VideoWallpaperProcessor.processLocalVideo(context, uri, WallpaperScope.Pending)
                if (result != null) {
                    val config = WallpaperConfig(type = WallpaperType.CUSTOM_VIDEO, videoDuration = result.durationSeconds)
                    WallpaperManager.pendingConfig = config
                    previewConfig = config
                }
                isProcessingVideo = false
            }
        }
    }

    val previewBitmap = remember(previewConfig) {
        previewConfig?.let { WallpaperManager.previewBitmapForConfig(it, threadKey = threadKey, serverId = resolvedServerId) }
    }
    val previewVideoPath = previewConfig?.let {
        WallpaperManager.previewVideoPathForConfig(it, threadKey = threadKey, serverId = resolvedServerId)
    }
    var blur by remember(previewConfig) { mutableFloatStateOf(previewConfig?.blur ?: 0f) }
    var brightness by remember(previewConfig) { mutableFloatStateOf(previewConfig?.brightness ?: 1f) }
    var motionEnabled by remember(previewConfig) { mutableStateOf(previewConfig?.motionEnabled ?: false) }
    val selectedLabel = when (previewConfig?.type) {
        WallpaperType.CUSTOM_IMAGE -> "照片"
        WallpaperType.CUSTOM_VIDEO -> "视频"
        WallpaperType.VIDEO_URL -> "视频 URL"
        WallpaperType.SOLID_COLOR -> "颜色"
        WallpaperType.THEME -> themes.firstOrNull { it.slug == previewConfig?.themeSlug }?.name ?: "主题"
        WallpaperType.NONE, null -> "无壁纸"
    }

    /** The previewed wallpaper with the current effect values. */
    fun editedConfig(): WallpaperConfig =
        (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
            blur = blur,
            brightness = brightness,
            motionEnabled = motionEnabled,
        )

    fun applyTo(target: WallpaperScope) {
        val config = editedConfig()
        if (config.type == WallpaperType.NONE) {
            WallpaperManager.clearPendingWallpaper()
            WallpaperManager.clearWallpaper(target)
            onApplied()
        } else if (WallpaperManager.applyWallpaper(config, target, sourceScope)) {
            onApplied()
        }
    }

    WallpaperScreenLayout(
        title = "选择壁纸",
        onBack = onBack,
        isProcessing = isProcessingVideo,
        preview = {
            WallpaperPreviewLayer(
                config = previewConfig,
                bitmap = previewBitmap,
                videoPath = previewVideoPath,
                blur = blur,
                brightness = brightness,
                motionEnabled = motionEnabled,
            )
        },
        panel = {
            WallpaperControlsPanel(
                title = "壁纸控件",
                collapsedSubtitle = "已选择「$selectedLabel」。点击展开控件。",
                expandedSubtitle = "已选择「$selectedLabel」。可在此调整，或展开下方挑选新的壁纸。",
                effects = WallpaperEffects(blur = blur, motionEnabled = motionEnabled, brightness = brightness),
                onBlurChange = { checked ->
                    blur = if (checked) 0.75f else 0f
                    previewConfig = editedConfig()
                },
                onMotionChange = {
                    motionEnabled = it
                    previewConfig = editedConfig()
                },
                onBrightnessChange = {
                    brightness = it
                    previewConfig = editedConfig()
                },
                targets = WallpaperApplyTargets(
                    thread = threadKey != null,
                    server = resolvedServerId != null,
                    serverIsPrimary = isServerOnly,
                ),
                onApplyThread = { threadKey?.let { applyTo(WallpaperScope.Thread(it)) } },
                onApplyServer = { resolvedServerId?.let { applyTo(WallpaperScope.Server(it)) } },
                sourcePicker = {
                    WallpaperSourcePicker(
                        themes = themes,
                        selectedThemeSlug = previewConfig?.themeSlug,
                        noneSelected = previewConfig == null || previewConfig?.type == WallpaperType.NONE,
                        isProcessingVideo = isProcessingVideo,
                        videoUrl = videoUrlText,
                        onVideoUrlChange = { videoUrlText = it },
                        onSelectNone = {
                            WallpaperManager.clearPendingWallpaper()
                            previewConfig = null
                            wallpaperScope?.let { WallpaperManager.clearWallpaper(it) }
                        },
                        onSelectTheme = { theme ->
                            val config = WallpaperConfig(
                                type = WallpaperType.THEME,
                                themeSlug = theme.slug,
                                blur = blur,
                                brightness = brightness,
                                motionEnabled = motionEnabled,
                            )
                            previewConfig = config
                            WallpaperManager.pendingConfig = config
                        },
                        onPickPhoto = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickVideo = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onSetColor = {
                            val hex = String.format("#%06X", 0xFFFFFF and AgentBuddyTheme.accent.toArgb())
                            val config = WallpaperConfig(
                                type = WallpaperType.SOLID_COLOR,
                                colorHex = hex,
                                blur = blur,
                                brightness = brightness,
                                motionEnabled = motionEnabled,
                            )
                            previewConfig = config
                            WallpaperManager.pendingConfig = config
                        },
                        onSubmitVideoUrl = submit@{
                            val url = videoUrlText.trim()
                            if (url.isEmpty() || isProcessingVideo) return@submit
                            isProcessingVideo = true
                            scope.launch {
                                val result = VideoWallpaperProcessor.processRemoteUrl(context, url, WallpaperScope.Pending)
                                if (result != null) {
                                    val config = WallpaperConfig(
                                        type = WallpaperType.VIDEO_URL,
                                        videoURL = url,
                                        videoDuration = result.durationSeconds,
                                        blur = blur,
                                        brightness = brightness,
                                        motionEnabled = motionEnabled,
                                    )
                                    WallpaperManager.pendingConfig = config
                                    previewConfig = config
                                }
                                isProcessingVideo = false
                            }
                        },
                    )
                },
            )
        },
    )
}
