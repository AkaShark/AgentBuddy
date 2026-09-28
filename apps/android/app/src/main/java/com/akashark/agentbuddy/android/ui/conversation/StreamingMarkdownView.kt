package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppMessageRenderBlock

/**
 * Composable that renders streaming assistant messages with a fade-in reveal
 * effect on newly appended tokens. Uses [StreamingTextCoordinator] to split
 * text into a stable cached prefix and an animated frontier.
 *
 * [mintTypography] renders with the Mint BODY / CODE metrics that follow the
 * system font size (the conversation timeline); false keeps the fixed-dp
 * metrics other callers (home previews) size against.
 */
@Composable
fun StreamingMarkdownView(
    text: String,
    itemId: String,
    onRendered: (() -> Unit)? = null,
    bodySize: Float = AgentBuddyTextStyle.body,
    mintTypography: Boolean = false,
) {
    val appModel = LocalAppModel.current

    // Compute streaming state — stable prefix blocks are cached, frontier blocks animate
    val streamState = remember(itemId, text) {
        StreamingTextCoordinator.update(
            itemId = itemId,
            text = text,
            parser = appModel.parser,
        )
    }

    // Animate frontier alpha: snap to 0 on new text, then animate to 1
    val frontierAlpha = remember(itemId) { Animatable(1f) }

    LaunchedEffect(text) {
        frontierAlpha.snapTo(0f)
        frontierAlpha.animateTo(1f, animationSpec = tween(durationMillis = 150))
        onRendered?.invoke()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (mintTypography) BuddySpacing.sm else 8.dp),
    ) {
        // Render stable prefix blocks (fully opaque, cached)
        if (streamState.stableBlocks.isNotEmpty()) {
            StreamingRenderBlocks(
                blocks = streamState.stableBlocks,
                alpha = 1f,
                bodySize = bodySize,
                mintTypography = mintTypography,
            )
        }

        // Render frontier blocks with fade-in
        if (streamState.frontierBlocks.isNotEmpty()) {
            StreamingRenderBlocks(
                blocks = streamState.frontierBlocks,
                alpha = frontierAlpha.value,
                bodySize = bodySize,
                mintTypography = mintTypography,
            )
        }
    }
}

@Composable
private fun StreamingRenderBlocks(
    blocks: List<AppMessageRenderBlock>,
    alpha: Float,
    bodySize: Float,
    mintTypography: Boolean,
) {
    blocks.forEachIndexed { index, block ->
        when (block) {
            is AppMessageRenderBlock.Markdown -> {
                if (block.markdown.isNotEmpty()) {
                    StreamingMarkdownText(
                        text = block.markdown,
                        modifier = Modifier.alpha(alpha),
                        bodySize = bodySize,
                        mintTypography = mintTypography,
                    )
                }
            }
            is AppMessageRenderBlock.CodeBlock -> {
                if (isMathLanguage(block.language)) {
                    StreamingMarkdownText(
                        text = mathMarkdownBlock(block.code),
                        modifier = Modifier.alpha(alpha),
                        bodySize = bodySize,
                        mintTypography = mintTypography,
                    )
                } else {
                    CodeBlockSegment(
                        language = block.language,
                        code = block.code,
                        modifier = Modifier.alpha(alpha),
                        codeStyle = if (mintTypography) {
                            buddyTextStyle(BuddyTextStyle.CODE)
                        } else {
                            TextStyle(fontFamily = AgentBuddyTheme.monoFont, fontSize = bodySize.scaled)
                        },
                    )
                }
            }
            is AppMessageRenderBlock.InlineImage -> {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(block.data)
                        .crossfade(false)
                        .build(),
                    contentDescription = "助手图片",
                    modifier = Modifier
                        .alpha(alpha)
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(TimelineImageShape),
                )
            }
        }
    }
}

@Composable
private fun StreamingMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    bodySize: Float = AgentBuddyTextStyle.body,
    mintTypography: Boolean = false,
) {
    SelectableMarkdownText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        bodySize = bodySize,
        usePhysicalDpTextSize = !mintTypography,
        lineHeightRatio = if (mintTypography) MintBodyLineHeightRatio else null,
    )
}
