package com.akashark.agentbuddy.android.ui.discovery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BUDDY_CHROME_MAX_FONT_SCALE
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import uniffi.codex_mobile_client.AppAlleycatPairPayload

/** Render state of the QR pairing sheet ([AlleycatAddServerSheet]). */
internal data class AlleycatPairViewState(
    val params: AppAlleycatPairPayload?,
    val displayName: String,
    val agentOptions: List<DiscoveryAgentOption>,
    val hasAvailableAgents: Boolean,
    val allAgentsSelected: Boolean,
    val isLoadingAgents: Boolean,
    val parseError: String?,
    val agentError: String?,
    val connectError: String?,
    val isConnecting: Boolean,
    val canConnect: Boolean,
    val cameraDenied: Boolean,
    val showPaste: Boolean,
    val pasteJson: String,
)

/** User actions of the QR pairing sheet. */
internal class AlleycatPairCallbacks(
    val onCancel: () -> Unit,
    val onScan: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onTogglePaste: () -> Unit,
    val onPasteJsonChange: (String) -> Unit,
    val onPasteFromClipboard: () -> Unit,
    val onParse: () -> Unit,
    val onDisplayNameChange: (String) -> Unit,
    val onToggleAllAgents: () -> Unit,
    val onAgentToggle: (DiscoveryAgentOption, Boolean) -> Unit,
    val onConnect: () -> Unit,
)

/**
 * Stateless 「扫码配对」 form: scan / rescan card, paste-JSON fallback, the
 * scanned host with display name and partner multi-select, errors with their
 * next step, and the 「连接」 button.
 */
@Composable
internal fun AlleycatPairContent(
    state: AlleycatPairViewState,
    actions: AlleycatPairCallbacks,
    modifier: Modifier = Modifier,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        header = {
            DiscoverySheetHeader(
                title = "扫码配对",
                actionTitle = "取消",
                onAction = actions.onCancel,
                actionEnabled = !state.isConnecting,
            )
        },
        bottomBar = {
            BuddyButton(
                text = "连接",
                onClick = actions.onConnect,
                enabled = state.canConnect,
                isLoading = state.isConnecting,
            )
        },
    ) {
        DiscoveryFormSection(title = "配对") {
            PairScanCard(rescan = state.params != null, onScan = actions.onScan)
            if (state.cameraDenied) {
                BuddyBanner(
                    tone = BuddyBannerTone.WARNING,
                    message = "扫描配对二维码需要相机权限。请在系统设置中授予权限，或在下方粘贴 JSON。",
                    actionTitle = "打开设置",
                    onAction = actions.onOpenSettings,
                )
            }
            AlleycatPastePairJsonSection(
                showPaste = state.showPaste,
                onTogglePaste = actions.onTogglePaste,
                pasteJson = state.pasteJson,
                onPasteJsonChange = actions.onPasteJsonChange,
                onPasteFromClipboard = actions.onPasteFromClipboard,
                parsedParams = state.params,
                onParse = actions.onParse,
            )
        }

        state.parseError?.let { message ->
            BuddyBanner(
                tone = BuddyBannerTone.WARNING,
                message = "$message\n请重新扫描电脑上显示的二维码，或粘贴完整的配对 JSON。",
            )
        }

        val params = state.params
        if (params == null) {
            DiscoveryFormSection(title = "没有装桌面端？在要连接的主机上运行：") {
                AlleycatPairCommandRow()
            }
        } else {
            AlleycatScannedHostSection(
                params = params,
                displayName = state.displayName,
                onDisplayNameChange = actions.onDisplayNameChange,
            )
            AlleycatAgentSection(state = state, actions = actions)
        }

        state.agentError?.let { message ->
            BuddyBanner(
                tone = BuddyBannerTone.WARNING,
                message = "$message\n确认电脑上的搭子正在运行，然后重新扫描二维码。",
            )
        }
        state.connectError?.let { message ->
            BuddyBanner(
                tone = BuddyBannerTone.DANGER,
                message = "$message\n确认电脑在线并已打开搭子，然后再点「连接」。",
            )
        }
    }
}

@Composable
private fun PairScanCard(rescan: Boolean, onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.BRAND),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md), verticalAlignment = Alignment.Top) {
            BuddyIconTile(
                content = BuddyTileContent.Symbol(Icons.Outlined.QrCodeScanner),
                fill = AgentBuddyTheme.brandChipFill,
                foreground = AgentBuddyTheme.onBrand,
            )
            Text(
                text = "配对二维码来自电脑上的搭子。先在电脑上打开搭子，再用这台手机扫码。",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.onBrand,
                modifier = Modifier.weight(1f),
            )
        }
        BuddyButton(
            text = if (rescan) "重新扫描二维码" else "扫描配对二维码",
            onClick = onScan,
            kind = if (rescan) BuddyButtonKind.SECONDARY else BuddyButtonKind.PRIMARY,
            icon = Icons.Outlined.QrCodeScanner,
        )
    }
}

/** Collapsible paste-JSON fallback of [AlleycatAddServerSheet]. */
@Composable
internal fun AlleycatPastePairJsonSection(
    showPaste: Boolean,
    onTogglePaste: () -> Unit,
    pasteJson: String,
    onPasteJsonChange: (String) -> Unit,
    onPasteFromClipboard: () -> Unit,
    parsedParams: AppAlleycatPairPayload?,
    onParse: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BuddyShapes.control)
            .clickable(role = Role.Button, onClick = onTogglePaste)
            .semantics { stateDescription = if (showPaste) "已展开" else "已折叠" }
            .heightIn(min = BuddySize.minHitTarget),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "粘贴配对 JSON",
            style = buddyTextStyle(BuddyTextStyle.LABEL),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (showPaste) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = AgentBuddyTheme.textSecondary,
            modifier = Modifier.size(BuddySize.icon),
        )
    }
    if (showPaste) {
        MintTextField(
            value = pasteJson,
            onValueChange = onPasteJsonChange,
            placeholder = "{\"v\":1,\"node_id\":\"...\",\"token\":\"...\",\"relay\":\"https://...\"}",
            label = "配对 JSON",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            monospaced = true,
        )
        MintButtonPair(
            first = { buttonModifier ->
                BuddyButton(
                    text = "从剪贴板粘贴",
                    onClick = onPasteFromClipboard,
                    kind = BuddyButtonKind.SOFT,
                    icon = Icons.Outlined.ContentPaste,
                    modifier = buttonModifier,
                )
            },
            second = { buttonModifier ->
                BuddyButton(
                    text = if (parsedParams == null) "解析 JSON" else "重新解析 JSON",
                    onClick = onParse,
                    kind = BuddyButtonKind.SECONDARY,
                    enabled = pasteJson.trim().isNotEmpty(),
                    modifier = buttonModifier,
                )
            },
        )
    }
}

/** Scanned-host preview and display name of [AlleycatAddServerSheet]. */
@Composable
internal fun AlleycatScannedHostSection(
    params: AppAlleycatPairPayload,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
) {
    DiscoveryFormSection(title = "扫描到的主机") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .buddyCard(BuddySurfaceTone.SURFACE, shape = BuddyShapes.detailCard, padding = null)
                .padding(horizontal = BuddySpacing.md),
        ) {
            PreviewRow("node", shortNodeId(params.nodeId))
            BuddyDivider()
            PreviewRow("protocol", "v${params.v.toInt()}")
            params.relay?.takeIf { it.isNotBlank() }?.let {
                BuddyDivider()
                PreviewRow("relay", it)
            }
            params.hostName?.takeIf { it.isNotBlank() }?.let {
                BuddyDivider()
                PreviewRow("host", it)
            }
        }
        MintTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            placeholder = "显示名称（可选）",
            modifier = Modifier.padding(top = BuddySpacing.xxs),
        )
    }
}

@Composable
private fun AlleycatAgentSection(state: AlleycatPairViewState, actions: AlleycatPairCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        DiscoveryAgentListHeader(
            showsToggle = state.hasAvailableAgents,
            allSelected = state.allAgentsSelected,
            onToggleAll = actions.onToggleAllAgents,
        )
        when {
            state.isLoadingAgents -> Row(
                modifier = Modifier
                    .heightIn(min = BuddySize.control)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AgentBuddyTheme.textSecondary,
                )
                Text(
                    text = "正在加载智能体",
                    style = buddyTextStyle(BuddyTextStyle.BODY),
                    color = AgentBuddyTheme.textSecondary,
                )
            }

            state.agentOptions.isEmpty() -> Text(
                text = "此主机上没有可用的智能体。",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
            )

            else -> DiscoveryAgentList(options = state.agentOptions, onToggle = actions.onAgentToggle)
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    // Side by side at normal sizes; label above value once text is enlarged.
    val stacked = LocalDensity.current.fontScale * LocalTextScale.current > BUDDY_CHROME_MAX_FONT_SCALE
    val labelText: @Composable () -> Unit = {
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
        )
    }
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.minHitTarget)
                .padding(vertical = BuddySpacing.xs)
                .semantics(mergeDescendants = true) {},
        ) {
            labelText()
            Text(
                text = value,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = AgentBuddyTheme.textPrimary,
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BuddySize.minHitTarget)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelText()
            Text(
                text = value,
                style = buddyTextStyle(BuddyTextStyle.CODE),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 2,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}
