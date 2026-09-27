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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
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
        accent = AgentBuddyTheme.warning,
        status = AppOperationStatus.COMPLETED,
        defaultExpanded = true,
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
        accent = AgentBuddyTheme.accent,
        status = data.status,
        defaultExpanded = true,
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("图片")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                data.status == AppOperationStatus.IN_PROGRESS ||
                    data.status == AppOperationStatus.PENDING -> {
                    GeneratedImageLoadingTile()
                }
                else -> {
                    Text(
                        text = "图片不可用",
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedImageLoadingTile() {
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .scale(0.98f + pulse * 0.05f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AgentBuddyTheme.accent.copy(alpha = 0.16f + pulse * 0.08f),
                            AgentBuddyTheme.warning.copy(alpha = 0.10f),
                        ),
                    ),
                    RoundedCornerShape(12.dp),
                )
                .border(
                    0.5.dp,
                    AgentBuddyTheme.accent.copy(alpha = 0.28f + pulse * 0.12f),
                    RoundedCornerShape(12.dp),
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = "正在生成图片",
            color = AgentBuddyTheme.textPrimary,
            fontSize = AgentBuddyTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
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
                        .background(
                            AgentBuddyTheme.accent.copy(alpha = 0.42f),
                            RoundedCornerShape(999.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun RevisedPromptSection(prompt: String) {
    var expanded by remember(prompt) { mutableStateOf(false) }
    val isLong = prompt.length > 220 || prompt.count { it == '\n' } >= 4
    val display = if (expanded || !isLong) prompt else prompt.take(220).trimEnd() + "…"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("修订后的提示词")
            Spacer(Modifier.weight(1f))
            if (isLong) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    color = AgentBuddyTheme.accent,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        Text(
            text = display,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.body.scaled,
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                .padding(10.dp),
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("图片")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = loadState) {
                ToolImageLoadState.Loading -> {
                    CircularProgressIndicator(
                        color = AgentBuddyTheme.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                is ToolImageLoadState.Loaded -> {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = toolCardWorkspaceTitle(path),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                is ToolImageLoadState.Failed -> {
                    Text(
                        text = state.message,
                        color = AgentBuddyTheme.danger,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
                        modifier = Modifier.padding(vertical = 20.dp),
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
