package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyButtonKind
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import kotlin.math.roundToInt

/** Where the edited wallpaper can be applied. */
internal data class WallpaperApplyTargets(
    /** 「应用到此任务」 is offered (a conversation is open). */
    val thread: Boolean,
    /** 「应用到此主机」 is offered (the host is known). */
    val server: Boolean,
    /** Server-only editing: the host button becomes the primary action. */
    val serverIsPrimary: Boolean,
)

/** Blur / motion / brightness values being previewed. */
internal data class WallpaperEffects(
    val blur: Float,
    val motionEnabled: Boolean,
    val brightness: Float,
)

/**
 * Bottom panel of the wallpaper screens: collapsible header, 模糊 / 动效 /
 * 亮度 controls, the apply buttons and (when [sourcePicker] is set) the
 * expandable list of other wallpaper sources.
 */
@Composable
internal fun BoxScope.WallpaperControlsPanel(
    title: String,
    collapsedSubtitle: String,
    expandedSubtitle: String,
    effects: WallpaperEffects,
    onBlurChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    targets: WallpaperApplyTargets,
    onApplyThread: () -> Unit,
    onApplyServer: () -> Unit,
    sourcePicker: (@Composable () -> Unit)? = null,
    initiallySourcesExpanded: Boolean = false,
) {
    var minimized by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(initiallySourcesExpanded) }
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp

    BuddyChromeTypeLimit {
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .wallpaperBottomPanel()
                    .navigationBarsPadding()
                    .heightIn(max = maxPanelHeight),
        ) {
            WallpaperPanelGrabber(Modifier.align(Alignment.CenterHorizontally).padding(top = BuddySpacing.sm))
            WallpaperPanelHeader(
                title = title,
                subtitle = if (minimized) collapsedSubtitle else expandedSubtitle,
                minimized = minimized,
                onToggle = { minimized = !minimized },
            )
            if (!minimized) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(start = BuddySpacing.md, end = BuddySpacing.md, bottom = BuddySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
                ) {
                    WallpaperEffectControls(effects, onBlurChange, onMotionChange, onBrightnessChange)
                    WallpaperApplyButtons(targets, onApplyThread, onApplyServer)
                    if (sourcePicker != null) {
                        SettingsGroup {
                            SettingsRow(
                                title = "选择其他壁纸",
                                subtitle = "主题、照片、颜色、视频以及 URL 来源",
                                icon = Icons.Outlined.Wallpaper,
                                onClick = { sourcesExpanded = !sourcesExpanded },
                                onClickLabel = if (sourcesExpanded) "折叠壁纸来源" else "展开壁纸来源",
                                trailing = {
                                    Icon(
                                        imageVector = if (sourcesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                        tint = AgentBuddyTheme.textSecondary,
                                    )
                                },
                            )
                        }
                        if (sourcesExpanded) sourcePicker()
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperPanelHeader(
    title: String,
    subtitle: String,
    minimized: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowMinHeight)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (minimized) "展开控件" else "收起控件",
                    onClick = onToggle,
                ).padding(horizontal = BuddySpacing.lg, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = buddyTextStyle(BuddyTextStyle.HEADING), color = AgentBuddyTheme.textPrimary)
            Text(subtitle, style = buddyTextStyle(BuddyTextStyle.CAPTION), color = AgentBuddyTheme.textSecondary)
        }
        Icon(
            imageVector = if (minimized) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = AgentBuddyTheme.textSecondary,
            modifier = Modifier.size(BuddySize.iconLarge),
        )
    }
}

@Composable
private fun WallpaperEffectControls(
    effects: WallpaperEffects,
    onBlurChange: (Boolean) -> Unit,
    onMotionChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
) {
    SettingsGroup {
        SettingsSwitchRow(
            title = "模糊",
            icon = Icons.Outlined.BlurOn,
            checked = effects.blur > 0.01f,
            onCheckedChange = onBlurChange,
        )
        SettingsRowDivider()
        SettingsSwitchRow(
            title = "动效",
            icon = Icons.Outlined.Animation,
            checked = effects.motionEnabled,
            onCheckedChange = onMotionChange,
        )
        SettingsRowDivider()
        Column(Modifier.padding(start = BuddySpacing.md, end = BuddySpacing.md, top = BuddySpacing.sm, bottom = BuddySpacing.xxs)) {
            Text("亮度", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                Icon(Icons.Outlined.BrightnessLow, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.icon))
                Slider(
                    value = effects.brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.2f..1f,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = AgentBuddyTheme.action,
                            activeTrackColor = AgentBuddyTheme.action,
                            inactiveTrackColor = AgentBuddyTheme.surfaceSoft,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "亮度"
                                stateDescription = "${(effects.brightness * 100).roundToInt()}%"
                            },
                )
                Icon(Icons.Outlined.BrightnessHigh, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(BuddySize.iconLarge))
            }
        }
    }
}

@Composable
private fun WallpaperApplyButtons(
    targets: WallpaperApplyTargets,
    onApplyThread: () -> Unit,
    onApplyServer: () -> Unit,
) {
    if (!targets.thread && !targets.server) return
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        if (targets.thread) {
            BuddyButton(text = "应用到此任务", onClick = onApplyThread)
        }
        if (targets.server) {
            BuddyButton(
                text = "应用到此主机",
                onClick = onApplyServer,
                kind = if (targets.serverIsPrimary) BuddyButtonKind.PRIMARY else BuddyButtonKind.SECONDARY,
            )
        }
    }
}
