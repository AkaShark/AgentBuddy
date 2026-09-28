package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.settings.SettingsTextField

/** Card over a scrim that asks for the local OpenAI API key before realtime can start. */
@Composable
internal fun RealtimeApiKeyPrompt(
    apiKey: String,
    apiKeyError: String?,
    isSavingKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
            modifier = modifier
                .padding(horizontal = BuddySpacing.lg)
                .widthIn(max = 420.dp)
                .clip(BuddyShapes.confirmCard)
                .background(AgentBuddyTheme.surface)
                .border(1.dp, AgentBuddyTheme.border, BuddyShapes.confirmCard)
                .padding(BuddySpacing.lg),
        ) {
            Text(
                text = "Realtime 需要 API 密钥",
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = "为此设备输入你的 OpenAI API 密钥。搭子会将其作为 OPENAI_API_KEY 存储在本地 Codex 环境中。",
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
            )

            SettingsTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = "API 密钥",
                placeholder = "sk-...",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            if (!apiKeyError.isNullOrBlank()) {
                Text(
                    text = apiKeyError,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
                    color = AgentBuddyTheme.danger,
                )
            }

            BuddyButton(
                text = "保存 API 密钥",
                onClick = onSave,
                enabled = apiKey.isNotBlank(),
                isLoading = isSavingKey,
            )
        }
    }
}
