package com.akashark.agentbuddy.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.ModelInfo

@Composable
internal fun RuntimeFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = leadingIcon,
        label = {
            Text(
                text = "$label $count",
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                maxLines = 1,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AgentBuddyTheme.accent,
            selectedLabelColor = Color.Black,
        ),
    )
}

@Composable
internal fun ModelOptionRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = if (selected) {
        AgentBuddyTheme.accent.copy(alpha = 0.14f)
    } else {
        AgentBuddyTheme.surface.copy(alpha = 0.55f)
    }
    val borderColor = if (selected) {
        AgentBuddyTheme.accent
    } else {
        AgentBuddyTheme.textMuted.copy(alpha = 0.32f)
    }
    val title = model.modelPickerDisplayName()
    val detail = model.description
        .takeIf { it.isNotBlank() }
        ?: model.model.takeIf { it.isNotBlank() && it != title && it != model.id }
    val runtimeLabel = model.agentRuntimeKind.runtimeLabel
        .takeUnless { model.agentRuntimeKind == "amp" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(0.8.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ModelRuntimeIcon(model.agentRuntimeKind)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (runtimeLabel != null) {
                    Text(
                        text = runtimeLabel,
                        color = AgentBuddyTheme.textSecondary,
                        fontSize = AgentBuddyTextStyle.caption2.scaled,
                        maxLines = 1,
                    )
                }
            }
            if (detail != null) {
                Text(
                    text = detail,
                    color = AgentBuddyTheme.textMuted,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选模型",
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun ModelRuntimeIcon(kind: AgentRuntimeKind) {
    AgentIconView(kind = kind, sizeDp = 16)
}
