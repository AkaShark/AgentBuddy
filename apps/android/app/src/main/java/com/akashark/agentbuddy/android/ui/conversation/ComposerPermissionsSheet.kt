package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.common.reportsEffectiveThreadPermissions
import com.akashark.agentbuddy.android.ui.common.supportsThreadPermissionOverrides
import com.akashark.agentbuddy.android.ui.scaled
import uniffi.codex_mobile_client.AppAskForApproval
import uniffi.codex_mobile_client.AppSandboxPolicy
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.threadPermissionsAreAuthoritative

private data class ComposerSelectionOption(
    val title: String,
    val description: String,
    val wireValue: String,
)

private val composerApprovalOptions = listOf(
    ComposerSelectionOption(
        title = "默认",
        description = "使用会话或服务器默认设置",
        wireValue = "inherit",
    ),
    ComposerSelectionOption(
        title = "不信任",
        description = "执行操作前始终询问",
        wireValue = "untrusted",
    ),
    ComposerSelectionOption(
        title = "失败时",
        description = "仅在命令失败时询问",
        wireValue = "on-failure",
    ),
    ComposerSelectionOption(
        title = "请求时",
        description = "请求提权时询问",
        wireValue = "on-request",
    ),
    ComposerSelectionOption(
        title = "从不",
        description = "无需批准直接运行",
        wireValue = "never",
    ),
)

private val composerSandboxOptions = listOf(
    ComposerSelectionOption(
        title = "默认",
        description = "使用会话或服务器默认设置",
        wireValue = "inherit",
    ),
    ComposerSelectionOption(
        title = "只读",
        description = "可读取文件，但不能编辑",
        wireValue = "read-only",
    ),
    ComposerSelectionOption(
        title = "工作区写入",
        description = "可编辑文件，但仅限本工作区",
        wireValue = "workspace-write",
    ),
    ComposerSelectionOption(
        title = "完全访问",
        description = "可编辑本工作区以外的文件",
        wireValue = "danger-full-access",
    ),
)

private fun selectedApprovalLabel(approvalPolicy: String): String =
    composerApprovalOptions.firstOrNull { it.wireValue == approvalPolicy }?.title ?: "自定义"

private fun selectedSandboxLabel(sandboxMode: String): String =
    composerSandboxOptions.firstOrNull { it.wireValue == sandboxMode }?.title ?: "自定义"

private fun AppAskForApproval.displayTitle(): String =
    when (this) {
        AppAskForApproval.UnlessTrusted -> "不信任"
        AppAskForApproval.OnFailure -> "失败时"
        AppAskForApproval.OnRequest -> "请求时"
        is AppAskForApproval.Granular -> "细粒度"
        AppAskForApproval.Never -> "从不"
    }

private fun AppSandboxPolicy.displayTitle(): String =
    when (this) {
        AppSandboxPolicy.DangerFullAccess -> "完全访问"
        is AppSandboxPolicy.ReadOnly -> "只读"
        is AppSandboxPolicy.WorkspaceWrite -> "工作区写入"
        is AppSandboxPolicy.ExternalSandbox -> "外部沙箱"
    }

@Composable
fun ComposerPermissionsSheet(threadKey: ThreadKey? = null, onDismiss: () -> Unit) {
    val appModel = LocalAppModel.current
    val launchState by appModel.launchState.snapshot.collectAsState()
    val selectedApproval = appModel.launchState.selectedApprovalPolicy(threadKey)
    val selectedSandbox = appModel.launchState.selectedSandboxMode(threadKey)
    val effectiveThread = appModel.snapshot.value?.threads?.firstOrNull { it.key == threadKey }
    val selectedRuntime = effectiveThread?.agentRuntimeKind ?: launchState.selectedAgentRuntimeKind
    val currentRuntimeSupportsPermissionOverrides =
        selectedRuntime?.supportsThreadPermissionOverrides ?: true
    val hasAuthoritativeThreadPermissions = if (
        (selectedRuntime?.reportsEffectiveThreadPermissions ?: true) &&
        threadPermissionsAreAuthoritative(
            approvalPolicy = effectiveThread?.effectiveApprovalPolicy,
            sandboxPolicy = effectiveThread?.effectiveSandboxPolicy,
        )
    ) true else false
    val currentApprovalLabel =
        if (hasAuthoritativeThreadPermissions) effectiveThread?.effectiveApprovalPolicy?.displayTitle() ?: "同步中..."
        else "同步中..."
    val currentSandboxLabel =
        if (hasAuthoritativeThreadPermissions) effectiveThread?.effectiveSandboxPolicy?.displayTitle() ?: "同步中..."
        else "同步中..."
    val usesThreadDefaults = selectedApproval == "inherit" && selectedSandbox == "inherit"
    val scrollState = rememberScrollState()

    LaunchedEffect(threadKey) {
        threadKey ?: return@LaunchedEffect
        appModel.hydrateThreadPermissions(threadKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.92f)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(title = "权限", onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surface.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
                .border(1.dp, AgentBuddyTheme.border.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "会话(线程)权限",
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = 18f.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (currentRuntimeSupportsPermissionOverrides) {
                            "更改在你下一轮及之后的对话中生效。"
                        } else {
                            "该运行时自行管理其权限。"
                        },
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption2.scaled,
                    )
                }
                Text(
                    text = if (!currentRuntimeSupportsPermissionOverrides) {
                        "运行时管理"
                    } else if (usesThreadDefaults) {
                        "使用默认"
                    } else {
                        "自定义覆盖"
                    },
                    color = if (!currentRuntimeSupportsPermissionOverrides || usesThreadDefaults) {
                        AgentBuddyTheme.textSecondary
                    } else {
                        AgentBuddyTheme.accentStrong
                    },
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            color = (
                                if (!currentRuntimeSupportsPermissionOverrides || usesThreadDefaults) {
                                    AgentBuddyTheme.surfaceLight
                                } else {
                                    AgentBuddyTheme.accentStrong
                                }
                            ).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PermissionSummaryTile(
                    title = "下一轮",
                    approval = selectedApprovalLabel(selectedApproval),
                    sandbox = selectedSandboxLabel(selectedSandbox),
                    accent = AgentBuddyTheme.accentStrong,
                    modifier = Modifier.weight(1f),
                )
                if (threadKey != null) {
                    PermissionSummaryTile(
                        title = "当前会话",
                        approval = currentApprovalLabel,
                        sandbox = currentSandboxLabel,
                        accent = if (hasAuthoritativeThreadPermissions) AgentBuddyTheme.textSecondary else AgentBuddyTheme.warning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (currentRuntimeSupportsPermissionOverrides) {
            PermissionSettingsSection(
                title = "批准策略",
                subtitle = "选择 Codex 何时请求批准",
            ) {
                PermissionDropdownField(
                    options = composerApprovalOptions,
                    selectedValue = selectedApproval,
                    onSelect = { value ->
                        appModel.launchState.updateThreadPermissions(threadKey, value, selectedSandbox)
                    },
                )
            }

            PermissionSettingsSection(
                title = "沙箱设置",
                subtitle = "选择 Codex 运行命令时的权限范围",
            ) {
                PermissionDropdownField(
                    options = composerSandboxOptions,
                    selectedValue = selectedSandbox,
                    onSelect = { value ->
                        appModel.launchState.updateThreadPermissions(threadKey, selectedApproval, value)
                    },
                )
            }
        } else {
            PermissionSettingsSection(
                title = "运行时管理",
                subtitle = "该运行时不接受客户端侧的权限覆盖。",
            ) {
                Text(
                    text = "请使用该运行时自带的控制项来设置批准和沙箱行为。",
                    color = AgentBuddyTheme.textSecondary,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
                )
            }
        }
    }
}

@Composable
private fun PermissionSummaryTile(
    title: String,
    approval: String,
    sandbox: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AgentBuddyTheme.surfaceLight.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .border(1.dp, AgentBuddyTheme.border.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        PermissionSummaryRow(label = "批准", value = approval, accent = accent)
        PermissionSummaryRow(label = "沙箱", value = sandbox, accent = accent)
    }
}

@Composable
private fun PermissionSummaryRow(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = AgentBuddyTheme.textSecondary,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
        )
        Text(
            text = value,
            color = accent,
            fontSize = AgentBuddyTextStyle.code.scaled,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PermissionSettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface.copy(alpha = 0.74f), RoundedCornerShape(20.dp))
            .border(1.dp, AgentBuddyTheme.border.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = AgentBuddyTheme.textPrimary,
                fontSize = 18f.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
            )
        }
        content()
    }
}

@Composable
private fun PermissionDropdownField(
    options: List<ComposerSelectionOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.wireValue == selectedValue }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AgentBuddyTheme.surfaceLight.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                .border(1.dp, AgentBuddyTheme.border.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = selectedOption?.title ?: "自定义",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = AgentBuddyTextStyle.body.scaled,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = selectedOption?.description ?: "此设置由服务器管理。",
                    color = AgentBuddyTheme.textMuted,
                    fontSize = AgentBuddyTextStyle.caption2.scaled,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = option.title,
                                color = AgentBuddyTheme.textPrimary,
                                fontSize = AgentBuddyTextStyle.body.scaled,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = option.description,
                                color = AgentBuddyTheme.textMuted,
                                fontSize = AgentBuddyTextStyle.caption2.scaled,
                            )
                        }
                    },
                    trailingIcon = {
                        if (selectedValue == option.wireValue) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AgentBuddyTheme.accentStrong,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.wireValue)
                    },
                )
            }
        }
    }
}
