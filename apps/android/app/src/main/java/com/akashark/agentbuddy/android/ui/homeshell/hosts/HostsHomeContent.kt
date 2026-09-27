package com.akashark.agentbuddy.android.ui.homeshell.hosts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.DebugBuildLabel
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyPageHeader
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

data class HostsHomeUiState(
    val primary: HostSummary?,
    val others: List<HostSummary>,
)

class HostsHomeCallbacks(
    val onAddHost: () -> Unit,
    val onPairWithQr: () -> Unit,
)

/**
 * 主机 tab: every known host with its connection state, partners and
 * management actions, then the entry for pairing another computer. The
 * connection flows (QR, Slingshot, SSH / URL) stay in the Discovery screens.
 */
@Composable
fun HostsHomeContent(
    state: HostsHomeUiState,
    handlers: HostActionHandlers,
    callbacks: HostsHomeCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    showsBuildLabel: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gutter = BuddySpacing.pageGutter(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = BuddySpacing.lg, bottom = BuddySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        ) {
            item(key = "header") {
                BuddyPageHeader(
                    title = "你的主机",
                    eyebrow = "随时随地，保持连接",
                    subtitle = "离开电脑，工作也能继续。",
                    modifier = Modifier.padding(bottom = BuddySpacing.md),
                ) {
                    BuddyIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "添加主机",
                        onClick = callbacks.onAddHost,
                        tone = BuddyIconButtonTone.SURFACE,
                        diameter = 44.dp,
                        iconSize = 22.dp,
                    )
                }
            }
            state.primary?.let { primary ->
                item(key = "host-${primary.serverId}") { HostCard(host = primary, featured = true, handlers = handlers) }
            }
            items(state.others, key = { "host-${it.serverId}" }) { host ->
                HostCard(host = host, featured = false, handlers = handlers)
            }
            item(key = "add") {
                AddAnotherComputer(
                    isFirst = state.primary == null,
                    callbacks = callbacks,
                    modifier = Modifier.padding(top = BuddySpacing.lg),
                )
            }
            if (showsBuildLabel) {
                item(key = "build") {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        DebugBuildLabel()
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAnotherComputer(
    isFirst: Boolean,
    callbacks: HostsHomeCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        Text(
            text = if (isFirst) "连接一台电脑" else "添加另一台电脑",
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "打开电脑端搭子，扫描配对二维码即可连接。",
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textSecondary,
        )
        BuddyButton(
            text = "扫码连接",
            onClick = callbacks.onPairWithQr,
            kind = BuddyButtonKind.BRAND,
            icon = Icons.Outlined.QrCodeScanner,
            modifier = Modifier.padding(top = BuddySpacing.xxs),
        )
        BuddyButton(
            text = "其他连接方式（SSH、地址）",
            onClick = callbacks.onAddHost,
            kind = BuddyButtonKind.QUIET,
        )
    }
}
