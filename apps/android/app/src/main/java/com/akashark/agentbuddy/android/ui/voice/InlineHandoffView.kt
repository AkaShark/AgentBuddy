package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.rememberStickyFollowTail
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.ThreadKey

/**
 * Compact transcript view of a handoff subagent thread.
 * Shown inline during voice session when handoff is active.
 */
@Composable
fun InlineHandoffView(
    threadKey: ThreadKey,
    modifier: Modifier = Modifier,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()

    val items = remember(snapshot, threadKey) {
        snapshot?.threads?.find { it.key == threadKey }
            ?.hydratedConversationItems
            ?: emptyList()
    }

    val listState = rememberLazyListState()
    val shouldFollowTail = rememberStickyFollowTail(
        listState = listState,
        resetKey = threadKey,
        bufferItems = 1,
    )
    val tailContentSignature = remember(items) {
        var hash = 17
        items.takeLast(4).forEach { item ->
            hash = 31 * hash + item.hashCode()
        }
        hash = 31 * hash + items.size
        hash
    }

    LaunchedEffect(threadKey, tailContentSignature) {
        if (shouldFollowTail && items.isNotEmpty()) {
            listState.scrollToItem(items.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .clip(BuddyShapes.detailCard)
            .background(AgentBuddyTheme.surface)
            .border(1.dp, AgentBuddyTheme.border, BuddyShapes.detailCard),
        contentPadding = PaddingValues(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        items(items, key = { it.id }) { item ->
            when (val content = item.content) {
                is HydratedConversationItemContent.User ->
                    HandoffLine(content.v1.text, BuddyTextStyle.LABEL, AgentBuddyTheme.textSecondary)

                is HydratedConversationItemContent.Assistant ->
                    HandoffLine(content.v1.text, BuddyTextStyle.LABEL, AgentBuddyTheme.textPrimary, FontWeight.Medium)

                is HydratedConversationItemContent.CodeReview -> {
                    val text = content.v1.findings.firstOrNull()?.title ?: "代码评审"
                    HandoffLine("评审：$text", BuddyTextStyle.LABEL, AgentBuddyTheme.textPrimary, FontWeight.Medium)
                }

                is HydratedConversationItemContent.Reasoning ->
                    HandoffLine(
                        text = content.v1.summary.joinToString(" "),
                        style = BuddyTextStyle.CAPTION,
                        color = AgentBuddyTheme.textSecondary,
                        italic = true,
                    )

                is HydratedConversationItemContent.CommandExecution ->
                    HandoffLine("$ ${content.v1.command}", BuddyTextStyle.CODE, AgentBuddyTheme.textSecondary)

                is HydratedConversationItemContent.ImageView ->
                    HandoffLine(
                        "已查看图片：${content.v1.path.substringAfterLast('/')}",
                        BuddyTextStyle.CAPTION,
                        AgentBuddyTheme.textSecondary,
                    )

                is HydratedConversationItemContent.Note ->
                    HandoffLine(content.v1.body, BuddyTextStyle.CAPTION, AgentBuddyTheme.danger)

                else -> {} // Skip other types in compact view
            }
        }
    }
}

@Composable
private fun HandoffLine(
    text: String,
    style: BuddyTextStyle,
    color: Color,
    weight: FontWeight? = null,
    italic: Boolean = false,
) {
    Text(
        text = text,
        style = buddyTextStyle(style, weight),
        color = color,
        fontStyle = if (italic) FontStyle.Italic else null,
    )
}
