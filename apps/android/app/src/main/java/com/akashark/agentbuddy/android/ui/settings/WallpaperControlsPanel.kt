package com.akashark.agentbuddy.android.ui.settings

import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import androidx.compose.material3.HorizontalDivider
import com.akashark.agentbuddy.android.ui.WallpaperConfig
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.WallpaperScope
import com.akashark.agentbuddy.android.ui.WallpaperType
import uniffi.codex_mobile_client.ThreadKey
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.CoroutineScope

/**
 * Bottom card of [WallpaperSelectionScreen]: blur, motion and brightness controls,
 * the apply buttons, and the toggle that expands [WallpaperSourcePicker].
 */
@Composable
internal fun BoxScope.WallpaperControlsPanel(
    threadKey: ThreadKey?,
    isServerOnly: Boolean,
    resolvedServerId: String?,
    wallpaperScope: WallpaperScope?,
    sourceScope: WallpaperScope?,
    themes: List<AgentBuddyThemeIndexEntry>,
    selectedLabel: String,
    previewConfigState: MutableState<WallpaperConfig?>,
    blurState: MutableFloatState,
    brightnessState: MutableFloatState,
    motionEnabledState: MutableState<Boolean>,
    isProcessingVideoState: MutableState<Boolean>,
    photoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    videoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    scope: CoroutineScope,
    context: Context,
    onApplied: () -> Unit,
) {
    var previewConfig by previewConfigState
    var blur by blurState
    var brightness by brightnessState
    var motionEnabled by motionEnabledState
    var sourceOptionsExpanded by remember { mutableStateOf(false) }
    var sheetMinimized by remember { mutableStateOf(false) }
    val videoUrlTextState = remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(
                AgentBuddyTheme.surface.copy(alpha = 0.95f),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AgentBuddyTheme.textMuted.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { sheetMinimized = !sheetMinimized }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (sheetMinimized) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (sheetMinimized) "展开控件" else "收起控件",
                    tint = AgentBuddyTheme.textPrimary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { sheetMinimized = !sheetMinimized },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "壁纸控件",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (sheetMinimized) {
                        "已选择「$selectedLabel」。点击展开控件。"
                    } else {
                        "已选择「$selectedLabel」。可在此调整，或展开下方挑选新的壁纸。"
                    },
                    color = AgentBuddyTheme.textMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (sheetMinimized) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (sheetMinimized) "展开控件" else "收起控件",
                tint = AgentBuddyTheme.textPrimary,
            )
        }

        if (!sheetMinimized) {
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = blur > 0.01f,
                        onCheckedChange = { checked ->
                            blur = if (checked) 0.75f else 0f
                            previewConfig = (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
                                blur = blur,
                                brightness = brightness,
                                motionEnabled = motionEnabled,
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AgentBuddyTheme.accent,
                            uncheckedColor = AgentBuddyTheme.textMuted,
                        ),
                    )
                    Text("模糊", color = AgentBuddyTheme.textPrimary, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = motionEnabled,
                        onCheckedChange = {
                            motionEnabled = it
                            previewConfig = (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
                                blur = blur,
                                brightness = brightness,
                                motionEnabled = motionEnabled,
                            )
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AgentBuddyTheme.accent,
                            uncheckedColor = AgentBuddyTheme.textMuted,
                        ),
                    )
                    Text("动效", color = AgentBuddyTheme.textPrimary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u2600", fontSize = 14.sp, color = AgentBuddyTheme.textMuted)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        previewConfig = (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
                            blur = blur,
                            brightness = brightness,
                            motionEnabled = motionEnabled,
                        )
                    },
                    valueRange = 0.2f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AgentBuddyTheme.accent,
                        activeTrackColor = AgentBuddyTheme.accent,
                        inactiveTrackColor = AgentBuddyTheme.border,
                    ),
                )
                Text("\u2600", fontSize = 20.sp, color = AgentBuddyTheme.textPrimary)
            }

            Spacer(Modifier.height(16.dp))

            if (!isServerOnly && threadKey != null) {
                Button(
                    onClick = {
                        val config = (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
                            blur = blur,
                            brightness = brightness,
                            motionEnabled = motionEnabled,
                        )
                        if (config.type == WallpaperType.NONE) {
                            WallpaperManager.clearPendingWallpaper()
                            WallpaperManager.clearWallpaper(WallpaperScope.Thread(threadKey))
                            onApplied()
                        } else if (WallpaperManager.applyWallpaper(
                                config,
                                WallpaperScope.Thread(threadKey),
                                sourceScope,
                            )
                        ) {
                            onApplied()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AgentBuddyTheme.accent,
                        contentColor = AgentBuddyTheme.onAccentStrong,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("申请此会话(线程)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))
            }

            if (resolvedServerId != null) {
                Button(
                    onClick = {
                        val config = (previewConfig ?: WallpaperConfig(type = WallpaperType.NONE)).copy(
                            blur = blur,
                            brightness = brightness,
                            motionEnabled = motionEnabled,
                        )
                        if (config.type == WallpaperType.NONE) {
                            WallpaperManager.clearPendingWallpaper()
                            WallpaperManager.clearWallpaper(WallpaperScope.Server(resolvedServerId))
                            onApplied()
                        } else if (WallpaperManager.applyWallpaper(
                                config,
                                WallpaperScope.Server(resolvedServerId),
                                sourceScope,
                            )
                        ) {
                            onApplied()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServerOnly) AgentBuddyTheme.accent else AgentBuddyTheme.surface,
                        contentColor = if (isServerOnly) AgentBuddyTheme.onAccentStrong else AgentBuddyTheme.textPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "申请此服务器",
                        fontSize = 13.sp,
                        fontWeight = if (isServerOnly) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = AgentBuddyTheme.border.copy(alpha = 0.3f))
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { sourceOptionsExpanded = !sourceOptionsExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择其他壁纸",
                        color = AgentBuddyTheme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "主题、照片、颜色、视频以及 URL 来源",
                        color = AgentBuddyTheme.textMuted,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    imageVector = if (sourceOptionsExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = if (sourceOptionsExpanded) "折叠壁纸来源" else "展开壁纸来源",
                    tint = AgentBuddyTheme.textPrimary,
                )
            }

            if (sourceOptionsExpanded) {
                Spacer(Modifier.height(10.dp))
                WallpaperSourcePicker(
                    themes = themes,
                    wallpaperScope = wallpaperScope,
                    previewConfigState = previewConfigState,
                    blurState = blurState,
                    brightnessState = brightnessState,
                    motionEnabledState = motionEnabledState,
                    isProcessingVideoState = isProcessingVideoState,
                    videoUrlTextState = videoUrlTextState,
                    photoPicker = photoPicker,
                    videoPicker = videoPicker,
                    scope = scope,
                    context = context,
                )
            }
        }
    }
}
