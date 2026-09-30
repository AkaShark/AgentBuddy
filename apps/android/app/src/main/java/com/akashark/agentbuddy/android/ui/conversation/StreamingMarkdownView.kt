package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * Composable that renders streaming assistant messages. Uses
 * [StreamingTextCoordinator] to split text into a stable cached prefix and a
 * re-parsed frontier. The frontier is drawn fully opaque: fading it in again on
 * every delta made the paragraph being written blink.
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

    // Compute streaming state — stable prefix blocks are cached, the frontier is re-parsed
    val streamState = remember(itemId, text) {
        StreamingTextCoordinator.update(
            itemId = itemId,
            text = text,
            parser = appModel.parser,
        )
    }

    LaunchedEffect(text) {
        onRendered?.invoke()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (mintTypography) BuddySpacing.sm else 8.dp),
    ) {
        // Stable prefix blocks (cached)
        if (streamState.stableBlocks.isNotEmpty()) {
            StreamingRenderBlocks(
                blocks = streamState.stableBlocks,
                bodySize = bodySize,
                mintTypography = mintTypography,
            )
        }

        // Frontier blocks (re-parsed as text arrives)
        if (streamState.frontierBlocks.isNotEmpty()) {
            StreamingRenderBlocks(
                blocks = streamState.frontierBlocks,
                bodySize = bodySize,
                mintTypography = mintTypography,
            )
        }
    }
}

@Composable
private fun StreamingRenderBlocks(
    blocks: List<AppMessageRenderBlock>,
    bodySize: Float,
    mintTypography: Boolean,
) {
    blocks.forEachIndexed { index, block ->
        when (block) {
            is AppMessageRenderBlock.Markdown -> {
                if (block.markdown.isNotEmpty()) {
                    StreamingMarkdownText(
                        text = block.markdown,
                        bodySize = bodySize,
                        mintTypography = mintTypography,
                    )
                }
            }
            is AppMessageRenderBlock.CodeBlock -> {
                if (isMathLanguage(block.language)) {
                    StreamingMarkdownText(
                        text = mathMarkdownBlock(block.code),
                        bodySize = bodySize,
                        mintTypography = mintTypography,
                    )
                } else {
                    CodeBlockSegment(
                        language = block.language,
                        code = block.code,
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
