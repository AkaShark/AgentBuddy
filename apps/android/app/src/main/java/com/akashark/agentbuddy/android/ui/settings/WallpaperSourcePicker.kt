package com.akashark.agentbuddy.android.ui.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import com.akashark.agentbuddy.android.ui.VideoWallpaperProcessor
import com.akashark.agentbuddy.android.ui.WallpaperConfig
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperScope
import com.akashark.agentbuddy.android.ui.WallpaperType
import com.akashark.agentbuddy.android.ui.colorFromHex
import kotlinx.coroutines.launch
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope

/** Expanded wallpaper source list: themes, photo, colour, video file and video URL. */
@Composable
internal fun WallpaperSourcePicker(
    themes: List<AgentBuddyThemeIndexEntry>,
    wallpaperScope: WallpaperScope?,
    previewConfigState: MutableState<WallpaperConfig?>,
    blurState: MutableFloatState,
    brightnessState: MutableFloatState,
    motionEnabledState: MutableState<Boolean>,
    isProcessingVideoState: MutableState<Boolean>,
    videoUrlTextState: MutableState<String>,
    photoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    videoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    scope: CoroutineScope,
    context: Context,
) {
    var previewConfig by previewConfigState
    val blur by blurState
    val brightness by brightnessState
    val motionEnabled by motionEnabledState
    var isProcessingVideo by isProcessingVideoState
    var videoUrlText by videoUrlTextState

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "主题",
            color = AgentBuddyTheme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            item {
                ThemeThumbnail(
                    label = "无",
                    backgroundColor = AgentBuddyTheme.background,
                    accentColor = null,
                    isSelected = previewConfig == null || previewConfig?.type == WallpaperType.NONE,
                    isNone = true,
                    onClick = {
                        WallpaperManager.clearPendingWallpaper()
                        previewConfig = null
                        wallpaperScope?.let { WallpaperManager.clearWallpaper(it) }
                    },
                )
            }

            items(themes) { theme ->
                val bg = colorFromHex(theme.backgroundHex)
                val accent = colorFromHex(theme.accentHex)
                ThemeThumbnail(
                    label = theme.name,
                    backgroundColor = bg,
                    accentColor = accent,
                    isSelected = previewConfig?.themeSlug == theme.slug,
                    onClick = {
                        val config = WallpaperConfig(
                            type = WallpaperType.THEME,
                            themeSlug = theme.slug,
                            blur = blur,
                            brightness = brightness,
                            motionEnabled = motionEnabled,
                        )
                        previewConfig = config
                        WallpaperManager.pendingConfig = config
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = AgentBuddyTheme.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "选择照片",
                    color = AgentBuddyTheme.accent,
                    fontSize = 13.sp,
                )
            }

            TextButton(
                onClick = {
                    val hex = String.format("#%06X", 0xFFFFFF and AgentBuddyTheme.accent.toArgb())
                    val config = WallpaperConfig(
                        type = WallpaperType.SOLID_COLOR,
                        colorHex = hex,
                        blur = blur,
                        brightness = brightness,
                        motionEnabled = motionEnabled,
                    )
                    previewConfig = config
                    WallpaperManager.pendingConfig = config
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = AgentBuddyTheme.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "设置颜色",
                    color = AgentBuddyTheme.accent,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = AgentBuddyTheme.border.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = AgentBuddyTheme.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("选择视频", color = AgentBuddyTheme.accent, fontSize = 13.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = videoUrlText,
                onValueChange = { videoUrlText = it },
                placeholder = { Text("粘贴视频 URL", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 44.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = AgentBuddyTheme.textPrimary,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgentBuddyTheme.accent,
                    unfocusedBorderColor = AgentBuddyTheme.border,
                    cursorColor = AgentBuddyTheme.accent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    val url = videoUrlText.trim()
                    if (url.isNotEmpty()) {
                        isProcessingVideo = true
                        scope.launch {
                            val result = VideoWallpaperProcessor.processRemoteUrl(
                                context,
                                url,
                                WallpaperScope.Pending,
                            )
                            if (result != null) {
                                val config = WallpaperConfig(
                                    type = WallpaperType.VIDEO_URL,
                                    videoURL = url,
                                    videoDuration = result.durationSeconds,
                                    blur = blur,
                                    brightness = brightness,
                                    motionEnabled = motionEnabled,
                                )
                                WallpaperManager.pendingConfig = config
                                previewConfig = config
                                isProcessingVideo = false
                            } else {
                                isProcessingVideo = false
                            }
                        }
                    }
                }),
            )
        }
    }
}

@Composable
private fun ThemeThumbnail(
    label: String,
    backgroundColor: Color,
    accentColor: Color?,
    isSelected: Boolean,
    isNone: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(backgroundColor)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, AgentBuddyTheme.accent, RoundedCornerShape(10.dp))
                    } else {
                        Modifier.border(1.dp, AgentBuddyTheme.border, RoundedCornerShape(10.dp))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isNone) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "无壁纸",
                    tint = AgentBuddyTheme.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            } else if (accentColor != null) {
                // Mini pattern preview — draw dots
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.5f)),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) AgentBuddyTheme.accent else AgentBuddyTheme.textMuted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
