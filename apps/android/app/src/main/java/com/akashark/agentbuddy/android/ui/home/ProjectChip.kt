package com.akashark.agentbuddy.android.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyContextChip
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.projectDefaultLabel

/** Project context chip of the new-task composer; opens the project picker. */
@Composable
fun ProjectChip(
    project: AppProject?,
    disabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when {
        project != null -> projectDefaultLabel(project.cwd)
        disabled -> "未连接主机"
        else -> "选择项目"
    }
    BuddyContextChip(
        text = label,
        onClick = onTap,
        icon = Icons.Outlined.Folder,
        trailingIcon = Icons.Outlined.UnfoldMore,
        enabled = !disabled,
        modifier = modifier.semantics { contentDescription = "项目：$label" },
    )
}
