package com.akashark.agentbuddy.android.ui.homeshell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The three primary destinations of the home shell. Conversations are pushed
 * on top of the shell, so the bottom navigation never competes with the
 * conversation composer.
 */
enum class HomeShellTab(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TASKS("任务", Icons.Outlined.ViewAgenda, Icons.Filled.ViewAgenda),
    PROJECTS("项目", Icons.Outlined.Folder, Icons.Filled.Folder),
    HOSTS("主机", Icons.Outlined.Laptop, Icons.Filled.Laptop),
    ;

    /** Whether the 「有个想法？」 composer pill sits above the navigation bar. */
    val showsComposerPill: Boolean
        get() = this != HOSTS
}
