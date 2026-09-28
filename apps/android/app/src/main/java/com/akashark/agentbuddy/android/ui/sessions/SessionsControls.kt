package com.akashark.agentbuddy.android.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.discovery.MintTextField

/**
 * Mint search field (surface fill, control outline, radius 16, 48dp) with a
 * leading magnifier and a clear button once there is text. Shared by 全部任务,
 * the folder picker and the project picker.
 */
@Composable
internal fun SessionsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索任务",
) {
    val focusManager = LocalFocusManager.current
    MintTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = placeholder,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        leading = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = AgentBuddyTheme.textSecondary,
                modifier = Modifier.size(BuddySize.icon),
            )
        },
        trailing = if (query.isNotEmpty()) {
            {
                BuddyIconButton(
                    icon = Icons.Outlined.Cancel,
                    contentDescription = "清除搜索",
                    onClick = { onQueryChange("") },
                    tint = AgentBuddyTheme.textSecondary,
                )
            }
        } else {
            null
        },
    )
}

/**
 * Selectable capsule filter chip. Unselected it is a surfaceSoft context chip;
 * selected it uses the action fill, onAction content and a check mark, so the
 * state never depends on colour alone. The hit area is at least 48dp.
 */
@Composable
internal fun SessionsFilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    opensMenu: Boolean = false,
    onClickLabel: String? = null,
) {
    val content = if (selected) AgentBuddyTheme.onAction else AgentBuddyTheme.textPrimary
    Box(
        modifier = modifier
            .heightIn(min = BuddySize.minHitTarget)
            .widthIn(min = BuddySize.minHitTarget)
            .clip(CircleShape)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = BuddySize.compactPill)
                .background(if (selected) AgentBuddyTheme.action else AgentBuddyTheme.surfaceSoft, CircleShape)
                .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leading = if (selected) Icons.Outlined.Check else icon
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.LABEL, if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = content,
                maxLines = 1,
            )
            if (opensMenu) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
        }
    }
}
