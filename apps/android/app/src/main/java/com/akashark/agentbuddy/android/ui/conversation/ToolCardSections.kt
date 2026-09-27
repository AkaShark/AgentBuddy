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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

private const val ToolCallTextPreviewLimit = 2_000

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = AgentBuddyTheme.textSecondary,
        fontSize = AgentBuddyTextStyle.caption2.scaled,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun CodeSection(
    label: String,
    content: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        LimitedToolTextBlock(content) { display ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text(
                    text = display,
                    color = AgentBuddyTheme.textBody,
                    fontFamily = AgentBuddyTheme.monoFont,
                    fontSize = AgentBuddyTextStyle.body.scaled,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        LimitedToolTextBlock(content) { display ->
            Text(
                text = display,
                color = tone,
                fontFamily = AgentBuddyTheme.monoFont,
                fontSize = AgentBuddyTextStyle.body.scaled,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEach { (key, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$key:",
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(value) { display ->
                            Text(
                                text = display,
                                color = AgentBuddyTheme.textSystem,
                                fontSize = AgentBuddyTextStyle.body.scaled,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.body.scaled)
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(item) { display ->
                            Text(
                                text = display,
                                color = AgentBuddyTheme.textSystem,
                                fontSize = AgentBuddyTextStyle.body.scaled,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "•",
                        color = if (index == items.lastIndex) AgentBuddyTheme.accentStrong else AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.body.scaled,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        LimitedToolTextBlock(item) { display ->
                            Text(
                                text = display,
                                color = AgentBuddyTheme.textSystem,
                                fontSize = AgentBuddyTextStyle.body.scaled,
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) {
            SectionLabel(label)
        }
        LimitedToolTextBlock(content) { display ->
            SyntaxHighlightedDiffBlock(
                diff = display,
                titleHint = label.ifEmpty { null },
                fontSize = AgentBuddyTextStyle.caption.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AgentBuddyTheme.codeBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                text = if (expanded) "收起" else "展开",
                color = AgentBuddyTheme.accent,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
