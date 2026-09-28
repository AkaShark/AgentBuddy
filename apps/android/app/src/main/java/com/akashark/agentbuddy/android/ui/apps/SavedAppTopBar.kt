package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.settings.SettingsAlertDialog
import com.akashark.agentbuddy.android.ui.settings.SettingsTextField

/**
 * Saved app header: back, the title (tap to rename), 「更新」 as the one
 * emphasised action, and a 「…」 menu with 重命名 / 请求更新 / 查看会话 / 删除.
 */
@Composable
internal fun TopBar(
    title: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onViewConversation: (() -> Unit)?,
    isUpdating: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    BuddyChromeTypeLimit {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = BuddySpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            BuddyIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                iconSize = BuddySize.iconLarge,
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = BuddySize.minHitTarget)
                        .clip(BuddyShapes.control)
                        .clickable(onClickLabel = "重命名", role = Role.Button, onClick = onRename)
                        .padding(horizontal = BuddySpacing.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title.ifBlank { "应用" },
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            UpdatePill(isUpdating = isUpdating, onClick = onUpdate)
            Box {
                BuddyIconButton(
                    icon = Icons.Outlined.MoreHoriz,
                    contentDescription = "应用选项",
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AppMenuItem("重命名", Icons.Outlined.Edit) {
                        menuOpen = false
                        onRename()
                    }
                    AppMenuItem("请求更新", Icons.Outlined.Refresh, enabled = !isUpdating) {
                        menuOpen = false
                        onUpdate()
                    }
                    if (onViewConversation != null) {
                        AppMenuItem("查看会话", Icons.AutoMirrored.Outlined.Chat) {
                            menuOpen = false
                            onViewConversation()
                        }
                    }
                    AppMenuItem("删除", Icons.Outlined.Delete, color = AgentBuddyTheme.danger) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    color: Color = AgentBuddyTheme.textPrimary,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, style = buddyTextStyle(BuddyTextStyle.BODY), color = if (enabled) color else AgentBuddyTheme.onDisabled) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = if (enabled) color else AgentBuddyTheme.onDisabled)
        },
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.heightIn(min = BuddySize.minHitTarget),
    )
}

/** Brand capsule (34dp inside a 48dp hit area); shows 「更新中」 with a spinner while busy. */
@Composable
private fun UpdatePill(
    isUpdating: Boolean,
    onClick: () -> Unit,
) {
    val fill = if (isUpdating) AgentBuddyTheme.disabled else AgentBuddyTheme.brand
    val content = if (isUpdating) AgentBuddyTheme.onDisabled else AgentBuddyTheme.onBrand
    Box(
        modifier =
            Modifier
                .heightIn(min = BuddySize.minHitTarget)
                .clip(CircleShape)
                .clickable(enabled = !isUpdating, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = BuddySize.compactPill)
                    .background(fill, CircleShape)
                    .padding(horizontal = BuddySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isUpdating) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = content, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            }
            Text(
                text = if (isUpdating) "更新中" else "更新",
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = content,
                maxLines = 1,
            )
        }
    }
}

/** Mint rename dialog shared by the list and the detail screen; a blank title keeps the old one. */
@Composable
internal fun RenameAppDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var newTitle by remember(currentTitle) { mutableStateOf(currentTitle) }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = "重命名 app",
        confirmText = "保存",
        dismissText = "取消",
        onConfirm = {
            val trimmed = newTitle.trim()
            if (trimmed.isEmpty()) onDismiss() else onRename(trimmed)
        },
        text = {
            SettingsTextField(value = newTitle, onValueChange = { newTitle = it }, label = "标题")
        },
    )
}
