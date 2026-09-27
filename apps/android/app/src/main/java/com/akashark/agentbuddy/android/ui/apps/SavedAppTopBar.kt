package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

@Composable
internal fun TopBar(
    title: String,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    onUpdate: () -> Unit,
    onOpenMenu: () -> Unit,
    onViewConversation: (() -> Unit)?,
    isUpdating: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = AgentBuddyTheme.textPrimary,
            )
        }
        Text(
            text = title.ifBlank { "应用" },
            color = AgentBuddyTheme.textPrimary,
            fontSize = AgentBuddyTextStyle.headline.scaled,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 4.dp),
        )
        if (onViewConversation != null) {
            IconButton(onClick = onViewConversation) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "查看对话",
                    tint = AgentBuddyTheme.textSecondary,
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .border(
                    width = 0.8.dp,
                    color = AgentBuddyTheme.accent.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(999.dp),
                )
                .then(
                    if (isUpdating) Modifier else Modifier.clickable(onClick = onUpdate),
                )
                .alpha(if (isUpdating) 0.6f else 1f)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "更新",
                color = AgentBuddyTheme.accent,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(onClick = onOpenMenu) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = AgentBuddyTheme.textSecondary,
            )
        }
    }
}

@Composable
internal fun RenameAppDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var newTitle by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名 app") },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                singleLine = true,
                label = { Text("标题") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = newTitle.trim().ifBlank { currentTitle }
                onRename(trimmed)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
