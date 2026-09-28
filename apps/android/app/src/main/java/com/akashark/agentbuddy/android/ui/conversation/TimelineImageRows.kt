package com.akashark.agentbuddy.android.ui.conversation

import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyReduceMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.state.AppModel
import uniffi.codex_mobile_client.AppOperationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImageViewRow(
    data: uniffi.codex_mobile_client.HydratedImageViewData,
    serverId: String,
) {
    ToolCardShell(
        summary = toolCardWorkspaceTitle(data.path),
        status = AppOperationStatus.COMPLETED,
        defaultExpanded = true,
        fallbackIcon = Icons.Outlined.Image,
    ) {
        ImageResultSection(path = data.path, serverId = serverId)
        KeyValueSection("元数据", listOf("路径" to data.path))
    }
}

@Composable
internal fun ImageGenerationRow(
    data: uniffi.codex_mobile_client.HydratedImageGenerationData,
) {
    val summary = when (data.status) {
        AppOperationStatus.COMPLETED -> "已生成图片"
        AppOperationStatus.FAILED -> "图片生成失败"
        else -> "正在生成图片…"
    }
    ToolCardShell(
        summary = summary,
        status = data.status,
        defaultExpanded = true,
        fallbackIcon = Icons.Outlined.Image,
    ) {
        GeneratedImageSection(data = data)
        data.revisedPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            RevisedPromptSection(prompt)
        }
        data.savedPath?.takeIf { it.isNotBlank() }?.let { path ->
            KeyValueSection("元数据", listOf("保存至" to path))
        }
    }
}

@Composable
private fun GeneratedImageSection(
    data: uniffi.codex_mobile_client.HydratedImageGenerationData,
) {
    val context = LocalContext.current
    val pngBytes = data.imagePng

    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel("图片")
        Box(
            modifier = Modifier.timelineImageFrame(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                pngBytes != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pngBytes)
                            .crossfade(false)
                            .build(),
                        contentDescription = "生成的图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(TimelineImageShape),
                    )
                }
                data.status == AppOperationStatus.IN_PROGRESS ||
                    data.status == AppOperationStatus.PENDING -> {
                    GeneratedImageLoadingTile()
                }
                else -> {
                    TimelineImagePlaceholder(
                        icon = Icons.Outlined.BrokenImage,
                        text = "图片不可用",
                        tint = AgentBuddyTheme.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedImageLoadingTile() {
    // Reduced motion: a static tile instead of the pulse loop.
    val pulse = if (buddyReduceMotion) 0.6f else rememberImageGenerationPulse()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BuddySpacing.lg),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .scale(0.98f + pulse * 0.05f)
                .background(
                    AgentBuddyTheme.brand.copy(alpha = 0.55f + pulse * 0.45f),
                    BuddyShapes.control,
                ),
        ) {
            Icon(
                imageVector = Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = AgentBuddyTheme.onBrand,
                modifier = Modifier.size(BuddySize.iconLarge),
            )
        }

        Text(
            text = "正在生成图片",
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = AgentBuddyTheme.textPrimary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(28.dp, 42.dp, 28.dp).forEachIndexed { index, width ->
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(4.dp)
                        .alpha((0.38f + pulse * 0.62f - index * 0.14f).coerceIn(0.24f, 1f))
                        .background(AgentBuddyTheme.textSecondary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun rememberImageGenerationPulse(): Float {
    val transition = rememberInfiniteTransition(label = "image-generation-loading")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "image-generation-pulse",
    )
    return pulse
}

/** Placeholder / failure state inside an image frame: icon plus text. */
@Composable
internal fun TimelineImagePlaceholder(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BuddySpacing.lg)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(BuddySize.iconLarge))
        Text(
            text = text,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = tint,
        )
    }
}

/** Images in the timeline: radius 16. */
internal val TimelineImageShape = BuddyShapes.detailCard

/** Frame behind an image inside a detail card. */
private fun Modifier.timelineImageFrame(): Modifier =
    fillMaxWidth()
        .clip(TimelineImageShape)
        .background(timelineCodeFill(nested = true))
        .padding(BuddySpacing.xs)

@Composable
private fun RevisedPromptSection(prompt: String) {
    var expanded by remember(prompt) { mutableStateOf(false) }
    val isLong = prompt.length > 220 || prompt.count { it == '\n' } >= 4
    val display = if (expanded || !isLong) prompt else prompt.take(220).trimEnd() + "…"

    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("修订后的提示词") }
            if (isLong) {
                TimelineLinkButton(
                    text = if (expanded) "收起" else "展开",
                    onClick = { expanded = !expanded },
                )
            }
        }
        Text(
            text = display,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(timelineCodeShape(nested = true))
                .background(timelineCodeFill(nested = true))
                .padding(BuddySpacing.sm),
        )
    }
}

private sealed interface ToolImageLoadState {
    data object Loading : ToolImageLoadState
    data class Loaded(val bitmap: android.graphics.Bitmap) : ToolImageLoadState
    data class Failed(val message: String) : ToolImageLoadState
}

@Composable
private fun ImageResultSection(
    path: String,
    serverId: String,
) {
    val appModel = LocalAppModel.current
    val loadState by produceState<ToolImageLoadState>(
        initialValue = ToolImageLoadState.Loading,
        path,
        serverId,
    ) {
        value = ToolImageLoadState.Loading
        value = loadToolImage(appModel, path, serverId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel("图片")
        Box(
            modifier = Modifier.timelineImageFrame(),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = loadState) {
                ToolImageLoadState.Loading -> {
                    Row(
                        modifier = Modifier.padding(vertical = BuddySpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!buddyReduceMotion) {
                            CircularProgressIndicator(
                                color = AgentBuddyTheme.textSecondary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = "正在加载图片…",
                            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                            color = AgentBuddyTheme.textSecondary,
                        )
                    }
                }

                is ToolImageLoadState.Loaded -> {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = toolCardWorkspaceTitle(path),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(TimelineImageShape),
                    )
                }

                is ToolImageLoadState.Failed -> {
                    TimelineImagePlaceholder(
                        icon = Icons.Outlined.BrokenImage,
                        text = state.message,
                        tint = AgentBuddyTheme.danger,
                    )
                }
            }
        }
    }
}

private suspend fun loadToolImage(
    appModel: AppModel,
    path: String,
    serverId: String,
): ToolImageLoadState {
    return try {
        val resolved = withContext(Dispatchers.IO) {
            appModel.client.resolveImageView(serverId, path)
        }
        val bitmap = BitmapFactory.decodeByteArray(resolved.bytes, 0, resolved.bytes.size)
        if (bitmap != null) {
            ToolImageLoadState.Loaded(bitmap)
        } else {
            ToolImageLoadState.Failed("无法解码图片。")
        }
    } catch (error: Exception) {
        val message = error.message?.trim().orEmpty()
        ToolImageLoadState.Failed(
            if (message.isNotEmpty()) message else "图片不可用",
        )
    }
}
