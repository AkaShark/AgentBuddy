package com.akashark.agentbuddy.android.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

@Composable
internal fun BottomControls(
    isSpeakerOn: Boolean,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = onToggleSpeaker,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "扬声器",
                    tint = Color.White,
                )
            }

            Text(
                text = if (isSpeakerOn) "扬声器" else "听筒",
                color = Color.White.copy(alpha = if (isSpeakerOn) 1f else 0.4f),
                fontSize = 11.sp,
                fontFamily = AgentBuddyTheme.monoFont,
            )
        }

        IconButton(
            onClick = onEnd,
            modifier = Modifier
                .size(64.dp)
                .background(AgentBuddyTheme.danger, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "结束通话",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.34f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Text(
                text = "Realtime 需要 API 密钥",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "为此设备输入你的 OpenAI API 密钥。搭子 会将其作为 OPENAI_API_KEY 存储在本地 Codex 环境中。",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                placeholder = {
                    Text(
                        text = "sk-...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontFamily = AgentBuddyTheme.monoFont,
                    )
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedBorderColor = Color.White.copy(alpha = 0.14f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!apiKeyError.isNullOrBlank()) {
                Text(
                    text = apiKeyError,
                    color = Color(0xFFFF8A8A),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }

            Button(
                onClick = onSave,
                enabled = apiKey.isNotBlank() && !isSavingKey,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.06f),
                    disabledContentColor = Color.White.copy(alpha = 0.55f),
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
            ) {
                if (isSavingKey) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = "保存 API 密钥",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
