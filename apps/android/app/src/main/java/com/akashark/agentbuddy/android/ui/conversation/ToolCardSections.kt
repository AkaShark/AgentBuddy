package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

private const val ToolCallTextPreviewLimit = 2_000

/** Small caption heading inside expanded detail content ("参数", "结果"). */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = AgentBuddyTheme.textSecondary,
    )
}

/** Nested code surface inside a detail card (surfaceSoft, radius 12). */
private fun Modifier.nestedCodeSurface(): Modifier =
    fillMaxWidth()
        .clip(timelineCodeShape(nested = true))
        .background(timelineCodeFill(nested = true))

@Composable
internal fun CodeSection(
    label: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel(label)
        LimitedToolTextBlock(content) { display ->
            Box(
                modifier = Modifier
                    .nestedCodeSurface()
                    .padding(BuddySpacing.sm),
            ) {
                // Only the code area scrolls sideways; the page never does.
                Text(
                    text = display,
                    style = buddyTextStyle(BuddyTextStyle.CODE),
                    color = AgentBuddyTheme.textBody,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

@Composable
internal fun InlineTextSection(
    label: String,
    content: String,
    tone: Color = AgentBuddyTheme.textBody,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel(label)
        LimitedToolTextBlock(content) { display ->
            Text(
                text = display,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = tone,
                modifier = Modifier
                    .nestedCodeSurface()
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
            )
        }
    }
}

@Composable
internal fun KeyValueSection(
    label: String,
    entries: List<Pair<String, String>>,
) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .nestedCodeSurface()
                .padding(BuddySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            entries.forEach { (key, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                    Text(
                        text = "$key：",
                        style = buddyTextStyle(BuddyTextStyle.LABEL),
                        color = AgentBuddyTheme.textSecondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(value) { display ->
                            Text(
                                text = display,
                                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                                color = AgentBuddyTheme.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ListSection(
    label: String,
    items: List<String>,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .nestedCodeSurface()
                .padding(BuddySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                    Text(
                        text = "•",
                        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                        color = AgentBuddyTheme.textSecondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(item) { display ->
                            Text(
                                text = display,
                                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                                color = AgentBuddyTheme.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgressSection(
    label: String,
    items: List<String>,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .nestedCodeSurface()
                .padding(BuddySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                    Text(
                        text = "•",
                        style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                        color = if (index == items.lastIndex) AgentBuddyTheme.warning else AgentBuddyTheme.textSecondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(item) { display ->
                            Text(
                                text = display,
                                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                                color = AgentBuddyTheme.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiffSection(
    label: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        if (label.isNotEmpty()) {
            SectionLabel(label)
        }
        LimitedToolTextBlock(content) { display ->
            SyntaxHighlightedDiffBlock(
                diff = display,
                titleHint = label.ifEmpty { null },
                fontSize = BuddyTextStyle.CODE.size.sp,
                modifier = Modifier
                    .nestedCodeSurface()
                    .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
            )
        }
    }
}

@Composable
internal fun LimitedToolTextBlock(
    content: String,
    previewFromTail: Boolean = false,
    body: @Composable (String) -> Unit,
) {
    val isLong = content.length > ToolCallTextPreviewLimit
    var expanded by remember(content, previewFromTail) { mutableStateOf(false) }
    val display = remember(content, expanded, previewFromTail) {
        if (isLong && !expanded) {
            if (previewFromTail) content.takeLast(ToolCallTextPreviewLimit) else content.take(ToolCallTextPreviewLimit)
        } else {
            content
        }
    }

    body(display)

    if (isLong) {
        TimelineLinkButton(
            text = if (expanded) "收起" else "展开",
            onClick = { expanded = !expanded },
        )
    }
}
