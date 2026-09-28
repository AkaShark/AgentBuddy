package com.akashark.agentbuddy.android.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.ui.AgentBuddyAppearanceMode
import com.akashark.agentbuddy.android.ui.AgentBuddyColorThemeType
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeIndexEntry
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.ConversationTextSize
import com.akashark.agentbuddy.android.ui.TextSizePrefs
import com.akashark.agentbuddy.android.ui.WallpaperBackdrop
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBanner
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBannerTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyChevron
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// Appearance Sub-Screen (matches iOS AppearanceSettingsView)
// ═══════════════════════════════════════════════════════════════════════════════

/** Everything the Appearance screen shows, as plain data. */
internal data class AppearanceState(
    val mode: AgentBuddyAppearanceMode,
    val textSizeStep: Int,
    val hasWallpaper: Boolean,
    val wallpaperError: String?,
    val lightTheme: AgentBuddyThemeIndexEntry?,
    val darkTheme: AgentBuddyThemeIndexEntry?,
)

internal class AppearanceActions(
    val onBack: () -> Unit,
    val onModeChange: (AgentBuddyAppearanceMode) -> Unit,
    val onTextSizeStepChange: (Int) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onRemoveWallpaper: () -> Unit,
    val onOpenLightThemes: () -> Unit,
    val onOpenDarkThemes: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showThemePicker by remember { mutableStateOf<AgentBuddyColorThemeType?>(null) }
    var wallpaperError by remember { mutableStateOf<String?>(null) }
    val wallpaperPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                wallpaperError =
                    if (WallpaperManager.setCustomFromUri(uri)) {
                        null
                    } else {
                        "无法从所选图片保存壁纸。"
                    }
            }
        }

    AppearanceContent(
        state = AppearanceState(
            mode = AgentBuddyThemeManager.appearanceMode,
            textSizeStep = TextSizePrefs.currentStep,
            hasWallpaper = WallpaperManager.isWallpaperSet,
            wallpaperError = wallpaperError,
            lightTheme = AgentBuddyThemeManager.lightThemes.firstOrNull { it.slug == AgentBuddyThemeManager.lightTheme.slug }
                ?: AgentBuddyThemeManager.lightThemes.firstOrNull(),
            darkTheme = AgentBuddyThemeManager.darkThemes.firstOrNull { it.slug == AgentBuddyThemeManager.darkTheme.slug }
                ?: AgentBuddyThemeManager.darkThemes.firstOrNull(),
        ),
        actions = AppearanceActions(
            onBack = onBack,
            onModeChange = AgentBuddyThemeManager::applyAppearanceMode,
            onTextSizeStepChange = { TextSizePrefs.setStep(context, it) },
            onPickWallpaper = { wallpaperPicker.launch("image/*") },
            onRemoveWallpaper = {
                WallpaperManager.clear()
                wallpaperError = null
            },
            onOpenLightThemes = { showThemePicker = AgentBuddyColorThemeType.LIGHT },
            onOpenDarkThemes = { showThemePicker = AgentBuddyColorThemeType.DARK },
        ),
        wallpaper = { WallpaperBackdrop(modifier = Modifier.matchParentSize()) },
    )

    showThemePicker?.let { type ->
        val isDark = type == AgentBuddyColorThemeType.DARK
        BuddyBottomSheet(onDismissRequest = { showThemePicker = null }) {
            ThemePickerContent(
                title = if (isDark) "深色主题" else "浅色主题",
                themes = if (isDark) AgentBuddyThemeManager.darkThemes else AgentBuddyThemeManager.lightThemes,
                selectedSlug = if (isDark) AgentBuddyThemeManager.darkTheme.slug else AgentBuddyThemeManager.lightTheme.slug,
                onSelect = { slug ->
                    if (isDark) {
                        AgentBuddyThemeManager.selectDarkTheme(slug)
                    } else {
                        AgentBuddyThemeManager.selectLightTheme(slug)
                    }
                    showThemePicker = null
                },
                onDismiss = { showThemePicker = null },
            )
        }
    }
}

/**
 * Appearance screen body: mode, text size with a live preview, chat
 * wallpaper and the light / dark theme choices. [wallpaper] draws the current
 * chat wallpaper behind the preview and in the wallpaper thumbnail.
 */
@Composable
internal fun AppearanceContent(
    state: AppearanceState,
    actions: AppearanceActions,
    modifier: Modifier = Modifier,
    wallpaper: @Composable BoxScope.() -> Unit = {},
) {
    val textSize = ConversationTextSize.fromStep(state.textSizeStep)
    SettingsPage(title = "外观", onBack = actions.onBack, modifier = modifier) {
        settingsSection(
            title = "模式",
            key = "mode",
            footer = { SettingsFooter("跟随设备设置，或将 搭子 固定为浅色或深色模式。") },
        ) {
            val modes = AgentBuddyAppearanceMode.entries
            SettingsSegmentedControl(
                options = modes.map { it.displayName },
                selectedIndex = modes.indexOf(state.mode),
                onSelect = { actions.onModeChange(modes[it]) },
                modifier = Modifier.padding(BuddySpacing.sm),
            )
        }

        settingsSection(
            title = "字号",
            key = "textSize",
            footer = { SettingsFooter("在对话中捏合调整，或使用此滑块。") },
        ) {
            TextSizeSlider(step = state.textSizeStep, label = textSize.label, onStepChange = actions.onTextSizeStepChange)
        }

        item(key = "preview") {
            Column {
                SettingsSectionHeader("预览")
                AppearanceConversationPreview(textScale = textSize.scale, wallpaper = wallpaper)
            }
        }

        settingsSection(
            title = "聊天壁纸",
            key = "wallpaper",
            footer = state.wallpaperError?.let { error ->
                { BuddyBanner(BuddyBannerTone.DANGER, error, modifier = Modifier.padding(top = BuddySpacing.xs)) }
            },
        ) {
            SettingsRow(
                title = "从相册选择",
                onClick = actions.onPickWallpaper,
                leading = { WallpaperThumbnail(wallpaper) },
                trailing = { BuddyChevron() },
            )
            if (state.hasWallpaper) {
                SettingsRowDivider(startIndent = WallpaperRowIndent)
                SettingsRow(
                    title = "移除壁纸",
                    titleColor = AgentBuddyTheme.danger,
                    onClick = actions.onRemoveWallpaper,
                    leading = {
                        Box(Modifier.width(WallpaperThumbnailWidth), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = AgentBuddyTheme.danger,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
            }
        }

        settingsSection("浅色主题", key = "lightTheme") {
            ThemePickerButton(state.lightTheme, onClick = actions.onOpenLightThemes, onClickLabel = "选择浅色主题")
        }
        settingsSection("深色主题", key = "darkTheme") {
            ThemePickerButton(state.darkTheme, onClick = actions.onOpenDarkThemes, onClickLabel = "选择深色主题")
        }
    }
}

@Composable
private fun TextSizeSlider(
    step: Int,
    label: String,
    onStepChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字号", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(label, style = buddyTextStyle(BuddyTextStyle.LABEL), color = AgentBuddyTheme.textSecondary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            // Fixed sizes on purpose: the two glyphs show the ends of the scale.
            Text("A", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AgentBuddyTheme.textSecondary, modifier = Modifier.clearAndSetSemantics {})
            Slider(
                value = step.toFloat(),
                onValueChange = { value ->
                    val next = value.roundToInt()
                    if (next != step) onStepChange(next)
                },
                valueRange = 0f..(ConversationTextSize.entries.size - 1).toFloat(),
                steps = ConversationTextSize.entries.size - 2,
                colors =
                    SliderDefaults.colors(
                        thumbColor = AgentBuddyTheme.action,
                        activeTrackColor = AgentBuddyTheme.action,
                        inactiveTrackColor = AgentBuddyTheme.surfaceSoft,
                        activeTickColor = AgentBuddyTheme.onAction,
                        inactiveTickColor = AgentBuddyTheme.borderControl,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "字号"
                            stateDescription = label
                        },
            )
            Text("A", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = AgentBuddyTheme.textSecondary, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

private val WallpaperThumbnailWidth = 32.dp

/** Row padding + thumbnail + gap, so the separator starts under the row titles. */
private val WallpaperRowIndent = BuddySpacing.md + WallpaperThumbnailWidth + BuddySpacing.sm

@Composable
private fun WallpaperThumbnail(wallpaper: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            Modifier
                .size(width = WallpaperThumbnailWidth, height = 48.dp)
                .clip(BuddyShapes.control)
                .border(1.dp, AgentBuddyTheme.border, BuddyShapes.control)
                .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxSize()) { wallpaper() }
    }
}
