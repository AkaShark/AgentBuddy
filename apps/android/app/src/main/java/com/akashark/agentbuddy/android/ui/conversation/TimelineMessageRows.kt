package com.akashark.agentbuddy.android.ui.conversation

import android.util.Base64
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBrandMark
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
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

    // Right-aligned Mint user bubble matching iOS `UserBubble`: surfaceSoft
    // fill with the 19/19/5/19 corners pointing at the sender. It hugs the
    // content width, keeps a 48dp start gutter so long messages wrap before
    // that edge, and caps its width on wide screens.
    //
    // Long-press opens an action menu (Edit / Fork / Copy). Text selection is
    // disabled on user bubbles because Compose's SelectionContainer would
    // consume the long-press gesture before our handler sees it; copy is
    // exposed via the menu instead.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BuddySpacing.xs, bottom = BuddySpacing.sm),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(modifier = Modifier.padding(start = 48.dp)) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                modifier = Modifier
                    .widthIn(max = UserBubbleMaxWidth)
                    .clip(BuddyShapes.userBubble)
                    .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.userBubble)
                    .combinedClickable(
                        onLongClickLabel = "消息操作",
                        onClick = {},
                        onLongClick = { showMenu = true },
                    )
                    .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
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
                                .heightIn(max = 200.dp)
                                .clip(TimelineImageShape),
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

    val bodyStyle = buddyTextStyle(BuddyTextStyle.BODY)
    ProvideTextStyle(bodyStyle) {
        com.akashark.agentbuddy.android.ui.common.FormattedText(
            text = display,
            color = AgentBuddyTheme.textPrimary,
            fontSize = bodyStyle.fontSize,
        )
    }

    if (isLong) {
        TimelineLinkButton(
            text = if (expanded) "收起" else "展开",
            onClick = { expanded = !expanded },
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

    AssistantMessageLayout(
        agentNickname = data.agentNickname,
        agentRole = data.agentRole,
    ) {
        if (isStreamingMessage) {
            StreamingMarkdownView(
                text = renderedText,
                itemId = itemId,
                onRendered = onStreamingSnapshotRendered,
                bodySize = BuddyTextStyle.BODY.size,
                mintTypography = true,
            )
        } else {
            AssistantRenderBlocks(
                blocks = renderBlocks,
                fallbackText = renderedText,
            )
        }
    }
}

/**
 * Assistant reply on the page (no bubble): an optional agent label for
 * subagent replies, then the Markdown body.
 */
@Composable
internal fun AssistantMessageLayout(
    agentNickname: String?,
    agentRole: String?,
    body: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BuddySpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        // Agent badge
        if (agentNickname != null || agentRole != null) {
            val label = buildString {
                agentNickname?.let { append(it) }
                agentRole?.let {
                    if (isNotEmpty()) append(" ")
                    append("[$it]")
                }
            }
            Text(
                text = label,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        body()
    }
}

/**
 * "搭子 · Codex" line that opens each assistant reply (iOS
 * `AssistantSpeakerHeader`), so a turn reads as a conversation with a named
 * partner instead of an anonymous log.
 */
@Composable
internal fun AssistantSpeakerHeader(
    partnerLabel: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = BuddySpacing.xxs)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyBrandMark(size = 26.dp)
        Text(
            text = "搭子",
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
            color = AgentBuddyTheme.textPrimary,
        )
        partnerLabel?.takeIf { it.isNotBlank() }?.let { partner ->
            Text(
                text = "· $partner",
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AssistantRenderBlocks(
    blocks: List<AppMessageRenderBlock>,
    fallbackText: String,
) {
    if (blocks.isEmpty()) {
        MarkdownText(text = fallbackText)
        return
    }

    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
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
                            .clip(TimelineImageShape),
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

    // Secondary tone with a quiet leading rule instead of an italic mono wall.
    val ruleColor = AgentBuddyTheme.border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BuddySpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "思考过程",
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        SelectableConversationText(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = reasoningText,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(color = ruleColor, size = Size(2.dp.toPx(), size.height))
                    }
                    .padding(start = BuddySpacing.sm),
            )
        }
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
                style = buddyTextStyle(BuddyTextStyle.BODY).copy(fontFamily = FontFamily.Monospace),
                color = AgentBuddyTheme.textBody,
            )
        }
        return
    }

    SelectableMarkdownText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        bodySize = BuddyTextStyle.BODY.size,
        lineHeightRatio = MintBodyLineHeightRatio,
    )
}


/** BODY 16 / 26 for Markdown rendered by the platform TextView. */
internal const val MintBodyLineHeightRatio = 26f / 16f

private val UserBubbleMaxWidth = 560.dp
