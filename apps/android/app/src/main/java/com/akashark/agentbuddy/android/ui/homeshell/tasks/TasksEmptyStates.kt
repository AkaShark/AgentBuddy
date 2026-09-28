package com.akashark.agentbuddy.android.ui.homeshell.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyEmptyState
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

/** One precise next step per state; an offline host never reads as a failed task. */
@Composable
fun TasksEmptyContent(
    availability: TasksHostAvailability,
    callbacks: TasksHomeCallbacks,
    modifier: Modifier = Modifier,
) {
    when (availability) {
        TasksHostAvailability.NO_HOSTS ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                BuddyEmptyState(
                    icon = Icons.Outlined.Devices,
                    title = "连接你的第一台电脑",
                    message = "在 Mac 上打开搭子并扫描配对二维码。之后任务在那台电脑上运行，你在这里随时跟进。",
                    actionTitle = "扫码连接",
                    actionIcon = Icons.Outlined.QrCodeScanner,
                    onAction = callbacks.onPairWithQr,
                )
                BuddyButton(
                    text = "其他连接方式",
                    onClick = callbacks.onOtherConnections,
                    kind = BuddyButtonKind.QUIET,
                )
            }
        TasksHostAvailability.CONNECTING ->
            BuddyEmptyState(
                icon = Icons.Outlined.Sync,
                title = "正在连接主机…",
                message = "连上后会同步任务状态，电脑上的任务不会因此停止。",
                actionTitle = "查看主机",
                actionIcon = Icons.Outlined.Laptop,
                actionKind = BuddyButtonKind.SECONDARY,
                onAction = callbacks.onManageHosts,
                modifier = modifier,
            )
        TasksHostAvailability.OFFLINE ->
            BuddyEmptyState(
                icon = Icons.Outlined.WifiOff,
                title = "主机均已离线",
                message = "主机重新连接后会同步任务状态，电脑上的任务不会因此停止。",
                actionTitle = "查看主机",
                actionIcon = Icons.Outlined.Laptop,
                actionKind = BuddyButtonKind.SECONDARY,
                onAction = callbacks.onManageHosts,
                modifier = modifier,
            )
        TasksHostAvailability.ONLINE ->
            BuddyEmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = "还没有任务",
                message = "说说你想完成什么。搭子会在你的电脑上开始工作，并在这里随时告诉你进展。",
                actionTitle = "开始任务",
                actionIcon = Icons.Outlined.Add,
                onAction = callbacks.onNewTask,
                modifier = modifier,
            )
    }
}
