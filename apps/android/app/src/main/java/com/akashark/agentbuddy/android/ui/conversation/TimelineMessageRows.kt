package com.akashark.agentbuddy.android.ui.conversation

import android.util.Base64
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppMessageRenderBlock
import kotlinx.coroutines.delay

private const val UserMessageTextPreviewLimit = 1_000

// ── User Message ─────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun UserMessageRow(
    data: uniffi.codex_mobile_client.HydratedUserMessageData,
    itemId: String,
    onEdit: ((String) -> Unit)?,
    onFork: ((String) -> Unit)?,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Right-aligned user bubble matching iOS `UserBubble`: accent-tinted
    // rounded rect that hugs content width, with a 60dp minimum gutter on
    // the left so long messages wrap before reaching that edge.
    //
    // Long-press opens an action menu (Edit / Fork / Copy). Text selection is
    // disabled on user bubbles because Compose's SelectionContainer would
    // consume the long-press gesture before our handler sees it; copy is
    // exposed via the menu instead.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .padding(start = 60.dp)
                    .background(
                        AgentBuddyTheme.accent.copy(alpha = 0.3f),
                        RoundedCornerShape(18.dp),
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true },
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                LimitedUserMessageText(data.text)
                // Inline images from data URIs
                for (uri in data.imageDataUris) {
                    val bytes = remember(uri) {
                        try {
                            val base64Part = uri.substringAfter("base64,", "")
                            if (base64Part.isNotEmpty()) Base64.decode(base64Part, Base64.DEFAULT) else null
                        } catch (_: Exception) { null }
                    }
                    bytes?.let {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(it)
                                .crossfade(false)
                                .build(),
                            contentDescription = "附带的图片",
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("编辑消息") },
                        onClick = { showMenu = false; onEdit(itemId) },
                    )
                }
                if (onFork != null) {
                    DropdownMenuItem(
                        text = { Text("从此处分叉") },
                        onClick = { showMenu = false; onFork(itemId) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        showMenu = false
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("message", data.text))
                    },
                )
            }
        }
    }
}

@Composable
private fun LimitedUserMessageText(text: String) {
    val isLong = text.length > UserMessageTextPreviewLimit
    var expanded by remember(text) { mutableStateOf(false) }
    val display = remember(text, expanded) {
        if (isLong && !expanded) text.take(UserMessageTextPreviewLimit) else text
    }

    com.akashark.agentbuddy.android.ui.common.FormattedText(
        text = display,
        color = AgentBuddyTheme.textPrimary,
        fontSize = AgentBuddyTextStyle.callout.scaled,
    )

    if (isLong) {
        Text(
            text = if (expanded) "收起" else "展开",
            color = AgentBuddyTheme.accent,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { expanded = !expanded },
        )
    }
}

// ── Assistant Message ────────────────────────────────────────────────────────

@Composable
internal fun AssistantMessageRow(
    itemId: String,
    data: uniffi.codex_mobile_client.HydratedAssistantMessageData,
    serverId: String,
    agentDirectoryVersion: ULong,
    isStreamingMessage: Boolean,
    onStreamingSnapshotRendered: (() -> Unit)?,
) {
    val appModel = LocalAppModel.current
    val renderBlocks = remember(itemId, data.text, serverId, agentDirectoryVersion, isStreamingMessage) {
        if (isStreamingMessage) {
            emptyList()
        } else {
            MessageRenderCache.getRenderBlocks(
                key = MessageRenderCache.CacheKey(
                    itemId = itemId,
                    revisionToken = data.text.hashCode(),
                    serverId = serverId,
                    agentDirectoryVersion = agentDirectoryVersion,
                ),
                parser = appModel.parser,
                text = data.text,
            )
        }
    }
    var renderedText by remember(itemId) { mutableStateOf(data.text) }
    var pendingText by remember(itemId) { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        renderedText = data.text
        pendingText = null
        if (isStreamingMessage) {
            onStreamingSnapshotRendered?.invoke()
        }
    }

    LaunchedEffect(data.text, isStreamingMessage) {
        if (!isStreamingMessage) {
            renderedText = data.text
            pendingText = null
            StreamingTextCoordinator.evict(itemId)
            return@LaunchedEffect
        }
        if (data.text == renderedText) return@LaunchedEffect
        if (renderedText.isEmpty()) {
            renderedText = data.text
            onStreamingSnapshotRendered?.invoke()
        } else {
            pendingText = data.text
        }
    }

    LaunchedEffect(pendingText, isStreamingMessage) {
        val nextText = pendingText ?: return@LaunchedEffect
        if (!isStreamingMessage) return@LaunchedEffect
        delay(60)
        renderedText = nextText
        pendingText = null
        onStreamingSnapshotRendered?.invoke()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Agent badge
        if (data.agentNickname != null || data.agentRole != null) {
            val label = buildString {
                data.agentNickname?.let { append(it) }
                data.agentRole?.let {
                    if (isNotEmpty()) append(" ")
                    append("[$it]")
                }
            }
            Text(
                text = label,
                color = AgentBuddyTheme.accent,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
        }

        if (isStreamingMessage) {
            StreamingMarkdownView(
                text = renderedText,
                itemId = itemId,
                onRendered = onStreamingSnapshotRendered,
            )
        } else {
            AssistantRenderBlocks(
                blocks = renderBlocks,
                fallbackText = renderedText,
            )
        }
    }
}

@Composable
private fun AssistantRenderBlocks(
    blocks: List<AppMessageRenderBlock>,
    fallbackText: String,
) {
    if (blocks.isEmpty()) {
        MarkdownText(text = fallbackText)
        return
    }

    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is AppMessageRenderBlock.Markdown -> MarkdownText(text = block.markdown)
                is AppMessageRenderBlock.CodeBlock -> {
                    if (isMathLanguage(block.language)) {
                        MarkdownText(text = mathMarkdownBlock(block.code))
                    } else {
                        CodeBlockSegment(
                            language = block.language,
                            code = block.code,
                        )
                    }
                }
                is AppMessageRenderBlock.InlineImage -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(block.data)
                            .crossfade(false)
                            .build(),
                        contentDescription = "助手图片 ${index + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
    }
}

// ── Reasoning ────────────────────────────────────────────────────────────────

@Composable
internal fun ReasoningRow(
    data: uniffi.codex_mobile_client.HydratedReasoningData,
) {
    val reasoningText = remember(data.summary, data.content) {
        (data.summary + data.content)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n\n")
    }

    if (reasoningText.isBlank()) return

    SelectableConversationText(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = reasoningText,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.body.scaled,
            fontFamily = AgentBuddyTheme.monoFont,
            fontStyle = FontStyle.Italic,
        )
    }
}

// ── Markdown Rendering ───────────────────────────────────────────────────

@Composable
internal fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (com.akashark.agentbuddy.android.state.DebugSettings.enabled && com.akashark.agentbuddy.android.state.DebugSettings.disableMarkdown) {
        SelectableConversationText(modifier = modifier.fillMaxWidth()) {
            Text(
                text = text,
                color = AgentBuddyTheme.textBody,
                fontFamily = FontFamily.Monospace,
                fontSize = AgentBuddyTextStyle.body.scaled,
            )
        }
        return
    }

    SelectableMarkdownText(
        text = text,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeBlockSegment(
    language: String?,
    code: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        language?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.uppercase(),
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            if (isDiffLanguage(language)) {
                SyntaxHighlightedDiffBlock(
                    diff = code,
                    titleHint = language,
                    fontSize = AgentBuddyTextStyle.caption.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SelectableConversationText {
                    Text(
                        text = code,
                        color = AgentBuddyTheme.textBody,
                        fontFamily = AgentBuddyTheme.monoFont,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}
