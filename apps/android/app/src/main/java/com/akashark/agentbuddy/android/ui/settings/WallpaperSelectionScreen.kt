package com.akashark.agentbuddy.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.ui.VideoWallpaperProcessor
import com.akashark.agentbuddy.android.ui.VideoWallpaperPlayer
import com.akashark.agentbuddy.android.ui.WallpaperConfig
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperScope
import com.akashark.agentbuddy.android.ui.WallpaperType
import com.akashark.agentbuddy.android.ui.colorFromHex
import com.akashark.agentbuddy.android.ui.rememberWallpaperMotionTransform
import com.akashark.agentbuddy.android.ui.wallpaperBlurRadius
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
    val previewConfigState = remember {
        mutableStateOf(
            WallpaperManager.pendingConfig ?: if (threadKey != null) WallpaperManager.resolvedConfig(threadKey)
            else resolvedServerId?.let { WallpaperManager.resolvedConfigForServer(it) }
        )
    }
    var previewConfig by previewConfigState
    val isProcessingVideoState = remember { mutableStateOf(false) }
    var isProcessingVideo by isProcessingVideoState

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
                    isProcessingVideo = false
                } else {
                    isProcessingVideo = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AgentBuddyTheme.background)) {
        // Full-screen preview
        val previewBitmap = remember(previewConfig) {
            previewConfig?.let { WallpaperManager.previewBitmapForConfig(it, threadKey = threadKey, serverId = resolvedServerId) }
        }
        val previewVideoPath = previewConfig?.let {
            WallpaperManager.previewVideoPathForConfig(it, threadKey = threadKey, serverId = resolvedServerId)
        }
        val blurState = remember(previewConfig) { mutableFloatStateOf(previewConfig?.blur ?: 0f) }
        val brightnessState = remember(previewConfig) { mutableFloatStateOf(previewConfig?.brightness ?: 1f) }
        val motionEnabledState = remember(previewConfig) { mutableStateOf(previewConfig?.motionEnabled ?: false) }
        val blur by blurState
        val brightness by brightnessState
        val motionEnabled by motionEnabledState
        val blurRadius = wallpaperBlurRadius(blur)
        val brightnessAlpha = brightness.coerceIn(0f, 1f)
        val motion = rememberWallpaperMotionTransform(motionEnabled)
        val selectedLabel = when (previewConfig?.type) {
            WallpaperType.CUSTOM_IMAGE -> "照片"
            WallpaperType.CUSTOM_VIDEO -> "视频"
            WallpaperType.VIDEO_URL -> "视频 URL"
            WallpaperType.SOLID_COLOR -> "颜色"
            WallpaperType.THEME -> themes.firstOrNull { it.slug == previewConfig?.themeSlug }?.name ?: "主题"
            WallpaperType.NONE, null -> "无壁纸"
        }

        if (previewConfig?.type in setOf(WallpaperType.CUSTOM_VIDEO, WallpaperType.VIDEO_URL) && previewVideoPath != null) {
            VideoWallpaperPlayer(
                filePath = previewVideoPath,
                blurAmount = blur,
                brightnessAlpha = brightnessAlpha,
                motionTransform = motion,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
                    .graphicsLayer {
                        alpha = brightnessAlpha
                        scaleX = motion.scale
                        scaleY = motion.scale
                        translationX = motion.translationX
                        translationY = motion.translationY
                    },
            )
        } else if (previewConfig?.type == WallpaperType.SOLID_COLOR) {
            val color = previewConfig?.colorHex?.let { colorFromHex(it) } ?: AgentBuddyTheme.background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .graphicsLayer { alpha = brightnessAlpha },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(AgentBuddyTheme.background))
        }

        // Sample bubbles overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 32.dp, end = 32.dp, top = 104.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SampleBubble(
                text = "你能帮我重构这个模块吗？",
                isUser = true,
            )
            SampleBubble(
                text = "当然！我来分析一下代码结构，给出改进建议。",
                isUser = false,
            )
        }

        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(AgentBuddyTheme.surface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "选择壁纸",
                color = AgentBuddyTheme.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Bottom card
        WallpaperControlsPanel(
            threadKey = threadKey,
            isServerOnly = isServerOnly,
            resolvedServerId = resolvedServerId,
            wallpaperScope = wallpaperScope,
            sourceScope = sourceScope,
            themes = themes,
            selectedLabel = selectedLabel,
            previewConfigState = previewConfigState,
            blurState = blurState,
            brightnessState = brightnessState,
            motionEnabledState = motionEnabledState,
            isProcessingVideoState = isProcessingVideoState,
            photoPicker = photoPicker,
            videoPicker = videoPicker,
            scope = scope,
            context = context,
            onApplied = onApplied,
        )

        // Processing overlay
        if (isProcessingVideo) {
            Box(
                modifier = Modifier.fillMaxSize().background(AgentBuddyTheme.background.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AgentBuddyTheme.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("正在处理视频...", color = AgentBuddyTheme.textPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SampleBubble(text: String, isUser: Boolean) {
    val bgColor = if (isUser) AgentBuddyTheme.accent.copy(alpha = 0.15f) else AgentBuddyTheme.surface.copy(alpha = 0.85f)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = AgentBuddyTheme.textPrimary,
            fontSize = 13.sp,
            modifier = Modifier
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
