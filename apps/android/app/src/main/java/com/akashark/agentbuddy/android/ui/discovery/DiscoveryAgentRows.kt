package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.AgentIconView
import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** One partner (agent) in a selectable list: pairing and SSH-bridge pickers. */
internal data class DiscoveryAgentOption(
    val key: String,
    val kind: AgentRuntimeKind,
    val title: String,
    val detail: String,
    val isBeta: Boolean,
    val selectable: Boolean,
    val selected: Boolean,
    /** Shown instead of the check mark when [selectable] is false. */
    val unavailableNote: String? = null,
)

/** Caption header of a partner list with an 「全部」/「无」 text action. */
@Composable
internal fun DiscoveryAgentListHeader(
    showsToggle: Boolean,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "智能体",
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showsToggle) {
            BuddyButton(
                text = if (allSelected) "无" else "全部",
                onClick = onToggleAll,
                kind = BuddyButtonKind.QUIET,
                enabled = enabled,
                fullWidth = false,
            )
        }
    }
}

/** Surface card with one [DiscoveryAgentRow] per option, divided by hairlines. */
@Composable
internal fun DiscoveryAgentList(
    options: List<DiscoveryAgentOption>,
    onToggle: (DiscoveryAgentOption, Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = null),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) BuddyDivider(startIndent = BuddySpacing.md)
            DiscoveryAgentRow(
                option = option,
                enabled = enabled,
                onCheckedChange = { checked -> onToggle(option, checked) },
            )
        }
    }
}

/**
 * Icon, name (+ 「BETA」 tag), detail line and a check circle. Selected rows
 * show a filled check, unselected an empty circle, unavailable rows say why in
 * text, so selection never depends on colour alone.
 */
@Composable
internal fun DiscoveryAgentRow(
    option: DiscoveryAgentOption,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val interactive = option.selectable && enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = option.selected,
                enabled = interactive,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = BuddySize.listRow)
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .alpha(if (option.selectable) 1f else 0.45f)
                .clearAndSetSemantics {},
        ) {
            AgentIconView(kind = option.kind, sizeDp = 28)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.title,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = if (option.selectable) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (option.isBeta) DiscoveryBetaTag()
            }
            Text(
                text = option.detail,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        when {
            !option.selectable -> option.unavailableNote?.let {
                Text(
                    text = it,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                )
            }
            option.selected -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = AgentBuddyTheme.action,
                modifier = Modifier.size(24.dp),
            )
            else -> Icon(
                imageVector = Icons.Outlined.Circle,
                contentDescription = null,
                tint = AgentBuddyTheme.borderControl,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Small outlined 「BETA」 tag for beta partners (caption size, never smaller). */
@Composable
internal fun DiscoveryBetaTag(modifier: Modifier = Modifier) {
    Text(
        text = "BETA",
        style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        color = AgentBuddyTheme.textSecondary,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, AgentBuddyTheme.borderControl, CircleShape)
            .padding(horizontal = BuddySpacing.xs, vertical = 1.dp),
    )
}
