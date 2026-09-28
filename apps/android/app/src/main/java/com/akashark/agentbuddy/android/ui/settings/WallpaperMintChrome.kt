package com.akashark.agentbuddy.android.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.VideoWallpaperPlayer
import com.akashark.agentbuddy.android.ui.WallpaperConfig
import com.akashark.agentbuddy.android.ui.WallpaperType
import com.akashark.agentbuddy.android.ui.colorFromHex
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.rememberWallpaperMotionTransform
import com.akashark.agentbuddy.android.ui.wallpaperBlurRadius

// Shared Mint chrome for the wallpaper picker and adjust screens (iOS
// WallpaperMintChrome parity): floating pill header over a full-screen
// preview, sample bubbles, and a bottom panel with 30dp top corners.

/**
 * Full-screen wallpaper editor: [preview] fills the screen, sample bubbles
 * sit on top, the floating header at the top and [panel] at the bottom.
 */
@Composable
internal fun WallpaperScreenLayout(
    title: String,
    onBack: () -> Unit,
    isProcessing: Boolean,
    preview: @Composable BoxScope.() -> Unit,
    panel: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AgentBuddyTheme.background)) {
        preview()
        WallpaperSampleBubbles(Modifier.align(Alignment.TopCenter))
        WallpaperFloatingHeader(title = title, onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        panel()
        if (isProcessing) WallpaperProcessingOverlay()
    }
}

/** Back button and title pill floating over the preview. */
@Composable
private fun WallpaperFloatingHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        Row(
            modifier =
                modifier
                    .statusBarsPadding()
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuddyIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tone = BuddyIconButtonTone.SURFACE,
                diameter = 40.dp,
                iconSize = BuddySize.icon,
            )
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = AgentBuddyTheme.textPrimary,
                modifier =
                    Modifier
                        .shadow(8.dp, CircleShape, ambientColor = AgentBuddyTheme.floatingShadow, spotColor = AgentBuddyTheme.floatingShadow)
                        .background(AgentBuddyTheme.surface, CircleShape)
                        .border(1.dp, AgentBuddyTheme.border, CircleShape)
                        .heightIn(min = 40.dp)
                        .padding(horizontal = BuddySpacing.md, vertical = 10.dp)
                        .semantics { heading() },
            )
        }
    }
}

/** A sample user bubble (19/19/5/19) and assistant card over the wallpaper. */
@Composable
private fun WallpaperSampleBubbles(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = BuddySpacing.md, end = BuddySpacing.md, top = 96.dp),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Box(Modifier.fillMaxWidth().padding(start = BuddySpacing.xxxl), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "你能帮我重构这个模块吗？",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textPrimary,
                modifier =
                    Modifier
                        .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.userBubble)
                        .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
            )
        }
        Box(Modifier.fillMaxWidth().padding(end = BuddySpacing.xxxl), contentAlignment = Alignment.CenterStart) {
            Text(
                text = "当然！我来分析一下代码结构，给出改进建议。",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textBody,
                modifier =
                    Modifier
                        .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.resultCard, padding = null)
                        .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
            )
        }
    }
}

/** Bottom control panel: page background, 30dp top corners, floating shadow. */
internal fun Modifier.wallpaperBottomPanel(): Modifier =
    shadow(24.dp, BuddyShapes.sheet, ambientColor = AgentBuddyTheme.floatingShadow, spotColor = AgentBuddyTheme.floatingShadow)
        .background(AgentBuddyTheme.background, BuddyShapes.sheet)
        .border(1.dp, AgentBuddyTheme.border, BuddyShapes.sheet)

/** 36×5 drag handle at the top of the bottom panel. */
@Composable
internal fun WallpaperPanelGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 36.dp, height = 5.dp)
            .background(AgentBuddyTheme.borderControl, CircleShape),
    )
}

@Composable
private fun WallpaperProcessingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(AgentBuddyTheme.background.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 280.dp)
                    .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.resultCard)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            CircularProgressIndicator(color = AgentBuddyTheme.action, strokeWidth = 3.dp)
            Text("正在处理视频…", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textPrimary)
        }
    }
}

/**
 * The wallpaper being edited, with blur, brightness and (unless reduced
 * motion is on) the motion effect applied.
 */
@Composable
internal fun BoxScope.WallpaperPreviewLayer(
    config: WallpaperConfig?,
    bitmap: Bitmap?,
    videoPath: String?,
    blur: Float,
    brightness: Float,
    motionEnabled: Boolean,
) {
    val brightnessAlpha = brightness.coerceIn(0f, 1f)
    val motion = rememberWallpaperMotionTransform(motionEnabled && !buddyReduceMotion)
    val isVideo = config?.type == WallpaperType.CUSTOM_VIDEO || config?.type == WallpaperType.VIDEO_URL
    when {
        isVideo && videoPath != null ->
            VideoWallpaperPlayer(
                filePath = videoPath,
                blurAmount = blur,
                brightnessAlpha = brightnessAlpha,
                motionTransform = motion,
                modifier = Modifier.matchParentSize(),
            )
        bitmap != null ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .blur(wallpaperBlurRadius(blur))
                        .graphicsLayer {
                            alpha = brightnessAlpha
                            scaleX = motion.scale
                            scaleY = motion.scale
                            translationX = motion.translationX
                            translationY = motion.translationY
                        },
            )
        config?.type == WallpaperType.SOLID_COLOR -> {
            val color = config.colorHex?.let { colorFromHex(it) } ?: AgentBuddyTheme.background
            Box(Modifier.matchParentSize().background(color).graphicsLayer { alpha = brightnessAlpha })
        }
        else -> Unit
    }
}
