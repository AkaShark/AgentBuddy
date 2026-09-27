package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperScope
import com.akashark.agentbuddy.android.ui.WallpaperType
import uniffi.codex_mobile_client.ThreadKey

@Composable
fun WallpaperAdjustScreen(
    threadKey: ThreadKey? = null,
    serverId: String? = null,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val isServerOnly = threadKey == null
    val resolvedServerId = threadKey?.serverId ?: serverId
    val sourceScope: WallpaperScope? = if (threadKey != null) {
        WallpaperManager.resolvedScope(threadKey)
    } else {
        resolvedServerId?.let { WallpaperManager.resolvedScopeForServer(it) }
    }
    val currentConfig = WallpaperManager.pendingConfig ?: if (threadKey != null) WallpaperManager.resolvedConfig(threadKey)
        else resolvedServerId?.let { WallpaperManager.resolvedConfigForServer(it) }
    var blur by remember(currentConfig) { mutableFloatStateOf(currentConfig?.blur ?: 0f) }
    var brightness by remember(currentConfig) { mutableFloatStateOf(currentConfig?.brightness ?: 1f) }
    var motionEnabled by remember(currentConfig) { mutableStateOf(currentConfig?.motionEnabled ?: false) }

    val previewBitmap = remember(currentConfig) {
        currentConfig?.let { WallpaperManager.previewBitmapForConfig(it, threadKey = threadKey, serverId = resolvedServerId) }
    }
    val isVideoType = currentConfig?.type == WallpaperType.CUSTOM_VIDEO || currentConfig?.type == WallpaperType.VIDEO_URL
    val videoPath = if (isVideoType) {
        currentConfig?.let { WallpaperManager.previewVideoPathForConfig(it, threadKey = threadKey, serverId = resolvedServerId) }
    } else {
        null
    }

    fun applyTo(target: WallpaperScope) {
        val config = currentConfig?.copy(
            blur = blur,
            brightness = brightness,
            motionEnabled = motionEnabled,
        ) ?: return
        if (WallpaperManager.applyWallpaper(config, target, sourceScope)) {
            onApplied()
        }
    }

    WallpaperScreenLayout(
        title = "调整壁纸",
        onBack = onBack,
        isProcessing = false,
        preview = {
            WallpaperPreviewLayer(
                config = currentConfig,
                // A video wallpaper never falls back to its still frame here.
                bitmap = if (isVideoType) null else previewBitmap,
                videoPath = videoPath,
                blur = blur,
                brightness = brightness,
                motionEnabled = motionEnabled,
            )
        },
        panel = {
            WallpaperControlsPanel(
                title = "壁纸控制",
                collapsedSubtitle = "点击展开模糊、动效、亮度和应用控制项。",
                expandedSubtitle = "调整当前壁纸，或收起此面板以全屏查看效果。",
                effects = WallpaperEffects(blur = blur, motionEnabled = motionEnabled, brightness = brightness),
                onBlurChange = { checked -> blur = if (checked) 0.75f else 0f },
                onMotionChange = { motionEnabled = it },
                onBrightnessChange = { brightness = it },
                targets = WallpaperApplyTargets(
                    thread = !isServerOnly,
                    server = resolvedServerId != null,
                    serverIsPrimary = isServerOnly,
                ),
                onApplyThread = { threadKey?.let { applyTo(WallpaperScope.Thread(it)) } },
                onApplyServer = { resolvedServerId?.let { applyTo(WallpaperScope.Server(it)) } },
            )
        },
    )
}
