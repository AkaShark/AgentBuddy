package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Other wallpaper sources: theme thumbnails (with 「无」), photo, video,
 * colour and a video URL. Stateless; the screen owns the processing.
 */
@Composable
internal fun WallpaperSourcePicker(
    themes: List<AgentBuddyThemeIndexEntry>,
    selectedThemeSlug: String?,
    noneSelected: Boolean,
    isProcessingVideo: Boolean,
    videoUrl: String,
    onVideoUrlChange: (String) -> Unit,
    onSelectNone: () -> Unit,
    onSelectTheme: (AgentBuddyThemeIndexEntry) -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onSetColor: () -> Unit,
    onSubmitVideoUrl: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
        Text(
            text = "主题",
            style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.Medium),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier.padding(start = BuddySpacing.xxs, top = BuddySpacing.xs).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm)) {
            item(key = "none") {
                WallpaperThumbnailOption(
                    label = "无",
                    selected = noneSelected,
                    onClick = onSelectNone,
                    fill = AgentBuddyTheme.surface,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            items(themes, key = { it.slug }) { theme ->
                val accent = themeSwatchColor(theme.accentHex, fallback = AgentBuddyTheme.link)
                WallpaperThumbnailOption(
                    label = theme.name,
                    selected = selectedThemeSlug == theme.slug,
                    onClick = { onSelectTheme(theme) },
                    fill = themeSwatchColor(theme.backgroundHex, fallback = AgentBuddyTheme.surface),
                ) {
                    // Pattern hint in the theme's own accent (data colour, not interface colour).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(6.dp).background(accent.copy(alpha = 0.55f), CircleShape)) }
                    }
                }
            }
        }

        SettingsGroup {
            SettingsRow(
                title = "从相册选择照片",
                icon = Icons.Outlined.PhotoLibrary,
                onClick = onPickPhoto,
                trailing = { BuddyChevron() },
            )
            SettingsRowDivider()
            SettingsRow(
                title = "从相册选择视频",
                icon = Icons.Outlined.VideoLibrary,
                onClick = onPickVideo,
                enabled = !isProcessingVideo,
                trailing = { if (isProcessingVideo) SettingsSpinner() else BuddyChevron() },
            )
            SettingsRowDivider()
            SettingsRow(
                title = "设置颜色",
                icon = Icons.Outlined.Palette,
                onClick = onSetColor,
                trailing = {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(AgentBuddyTheme.accent, CircleShape)
                            .border(1.dp, AgentBuddyTheme.border, CircleShape),
                    )
                },
            )
        }

        SettingsTextField(
            value = videoUrl,
            onValueChange = onVideoUrlChange,
            label = "视频 URL",
            placeholder = "粘贴视频 URL",
            enabled = !isProcessingVideo,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmitVideoUrl() }),
            trailingIcon =
                if (videoUrl.isNotBlank()) {
                    { SettingsTextAction("前往", onClick = onSubmitVideoUrl, enabled = !isProcessingVideo) }
                } else {
                    null
                },
        )
    }
}

/**
 * 60×88 thumbnail with its name below. Selection shows a 2dp action outline
 * and a check badge, so it does not depend on colour alone.
 */
@Composable
private fun WallpaperThumbnailOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    fill: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(64.dp)
                .clip(BuddyShapes.control)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = BuddySpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 60.dp, height = 88.dp)
                    .clip(BuddyShapes.control)
                    .background(fill)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) AgentBuddyTheme.action else AgentBuddyTheme.border,
                        shape = BuddyShapes.control,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(BuddySpacing.xxs)
                        .background(AgentBuddyTheme.onAction, CircleShape),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AgentBuddyTheme.action, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(
            text = label,
            style = buddyTextStyle(BuddyTextStyle.CAPTION, if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) AgentBuddyTheme.textPrimary else AgentBuddyTheme.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
