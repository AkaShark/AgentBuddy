package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** How the server is signed in, as shown on the status card. */
internal enum class AccountSignIn { CHATGPT, API_KEY, NONE }

/** Everything the account sheet shows, as plain data. */
internal data class AccountSheetState(
    val serverName: String,
    /** null while the server is not in the snapshot (neither local nor remote actions apply). */
    val isLocal: Boolean?,
    val signIn: AccountSignIn,
    val email: String?,
    val hasStoredApiKey: Boolean,
    val hasStoredBaseUrl: Boolean,
    val apiKey: String,
    val baseUrl: String,
    val isAuthWorking: Boolean,
    val error: String?,
)

internal class AccountSheetActions(
    val onDismiss: () -> Unit,
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onApiKeyChange: (String) -> Unit,
    val onSaveApiKey: () -> Unit,
    val onBaseUrlChange: (String) -> Unit,
    val onSaveBaseUrl: () -> Unit,
    val onClearBaseUrl: () -> Unit,
)

/** Account login / logout, API key and base URL for one server; stateless. */
@Composable
internal fun AccountSheetContent(
    state: AccountSheetState,
    actions: AccountSheetActions,
    modifier: Modifier = Modifier,
) {
    val isLocal = state.isLocal == true
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsPageHeader(title = "账户", onDone = actions.onDismiss)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = settingsGutter())
                        .padding(top = BuddySpacing.xs, bottom = BuddySpacing.xxl),
            ) {
                AccountStatusCard(state)

                val showLogin = isLocal && state.signIn != AccountSignIn.CHATGPT
                val showLogout = isLocal && state.signIn != AccountSignIn.NONE
                if (showLogin || showLogout) {
                    Column(
                        modifier = Modifier.padding(top = BuddySpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                    ) {
                        if (showLogin) {
                            BuddyButton(text = "使用 ChatGPT 登录", onClick = actions.onLogin, enabled = !state.isAuthWorking)
                        }
                        if (showLogout) {
                            BuddyButton(text = "退出登录", onClick = actions.onLogout, kind = BuddyButtonKind.DESTRUCTIVE)
                        }
                    }
                }

                if (isLocal) {
                    AccountApiKeySection(state, actions)
                    AccountBaseUrlSection(state, actions)
                } else if (state.isLocal == false) {
                    BuddyBanner(
                        tone = BuddyBannerTone.INFO,
                        message = "远程服务器会在需要时发起自己的 OAuth 登录。账户登录和 API 密钥仅在本机设置。",
                        modifier = Modifier.padding(top = BuddySpacing.sm),
                    )
                }

                state.error?.let {
                    BuddyBanner(tone = BuddyBannerTone.DANGER, message = it, modifier = Modifier.padding(top = BuddySpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun AccountStatusCard(state: AccountSheetState) {
    val signedIn = state.signIn != AccountSignIn.NONE
    val title =
        when (state.signIn) {
            AccountSignIn.CHATGPT -> "已登录"
            AccountSignIn.API_KEY -> "API 密钥已配置"
            AccountSignIn.NONE -> "未登录"
        }
    val icon =
        when (state.signIn) {
            AccountSignIn.CHATGPT -> Icons.Outlined.CheckCircle
            AccountSignIn.API_KEY -> Icons.Outlined.Key
            AccountSignIn.NONE -> Icons.Outlined.AccountCircle
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .buddyCard(
                    tone = if (signedIn) BuddySurfaceTone.SUCCESS else BuddySurfaceTone.SOFT,
                    shape = BuddyShapes.resultCard,
                    padding = BuddySpacing.md,
                ),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BuddyIconTile(
                content = BuddyTileContent.Symbol(icon),
                fill = AgentBuddyTheme.surface,
                foreground = if (signedIn) AgentBuddyTheme.success else AgentBuddyTheme.textSecondary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.serverName,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
                    color = AgentBuddyTheme.textSecondary,
                )
                Text(
                    text = title,
                    style = buddyTextStyle(BuddyTextStyle.HEADING),
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                if (state.signIn == AccountSignIn.CHATGPT) {
                    Text(
                        text = state.email?.takeIf { it.isNotEmpty() } ?: "ChatGPT 账户",
                        style = buddyTextStyle(BuddyTextStyle.BODY),
                        color = AgentBuddyTheme.textPrimary,
                    )
                }
            }
        }
        if (state.isLocal == true && state.hasStoredApiKey) AccountSavedLine("本地 OpenAI API 密钥已保存。")
        if (state.isLocal == true && state.hasStoredBaseUrl) AccountSavedLine("OpenAI 兼容的基础 URL 已保存。")
    }
}

@Composable
private fun AccountSavedLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AgentBuddyTheme.success, modifier = Modifier.size(16.dp))
        Text(text, style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal), color = AgentBuddyTheme.textPrimary)
    }
}

@Composable
private fun AccountApiKeySection(
    state: AccountSheetState,
    actions: AccountSheetActions,
) {
    SettingsSectionHeader("API 密钥")
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        AccountHint(
            when {
                state.hasStoredApiKey -> "OpenAI API 密钥已保存在本地环境中。"
                state.signIn == AccountSignIn.CHATGPT -> "在本地 Codex 环境中保存 OpenAI API 密钥。"
                else -> "或为本地环境保存一个 API 密钥："
            },
        )
        SettingsTextField(
            value = state.apiKey,
            onValueChange = actions.onApiKeyChange,
            label = "API 密钥",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        BuddyButton(
            text = if (state.hasStoredApiKey) "更新 API 密钥" else "保存 API 密钥",
            onClick = actions.onSaveApiKey,
            kind = BuddyButtonKind.SECONDARY,
            enabled = state.apiKey.isNotBlank(),
        )
    }
}

@Composable
private fun AccountBaseUrlSection(
    state: AccountSheetState,
    actions: AccountSheetActions,
) {
    SettingsSectionHeader("Base URL")
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        AccountHint(
            if (state.hasStoredBaseUrl) {
                "已为本地 Codex 服务器保存自定义的 OpenAI 兼容端点。"
            } else {
                "用于本地模型的可选 OpenAI 兼容端点。"
            },
        )
        SettingsTextField(
            value = state.baseUrl,
            onValueChange = actions.onBaseUrlChange,
            label = "Base URL",
            placeholder = "http://host:port/v1",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        BuddyButton(
            text = if (state.hasStoredBaseUrl) "更新 Base URL" else "保存 Base URL",
            onClick = actions.onSaveBaseUrl,
            kind = BuddyButtonKind.SECONDARY,
            enabled = state.baseUrl.isNotBlank() && !state.isAuthWorking,
        )
        if (state.hasStoredBaseUrl) {
            BuddyButton(
                text = "清除 Base URL",
                onClick = actions.onClearBaseUrl,
                kind = BuddyButtonKind.QUIET,
                enabled = !state.isAuthWorking,
            )
        }
    }
}

@Composable
private fun AccountHint(text: String) {
    Text(
        text = text,
        style = buddyTextStyle(BuddyTextStyle.CAPTION),
        color = AgentBuddyTheme.textSecondary,
        modifier = Modifier.padding(start = BuddySpacing.xxs, bottom = BuddySpacing.xxs),
    )
}
