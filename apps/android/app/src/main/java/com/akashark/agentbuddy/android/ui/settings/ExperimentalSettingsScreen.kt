package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.state.DebugSettings
import com.akashark.agentbuddy.android.ui.AgentBuddyFeature
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.ExperimentalFeatures

// ═══════════════════════════════════════════════════════════════════════════════
// Experimental Sub-Screen (matches iOS ExperimentalFeaturesView)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ExperimentalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ExperimentalSettingsContent(
        features = AgentBuddyFeature.entries.map { it to ExperimentalFeatures.isEnabled(it) },
        debugEnabled = DebugSettings.enabled,
        onFeatureChange = { feature, enabled -> ExperimentalFeatures.setEnabled(context, feature, enabled) },
        onDebugChange = { DebugSettings.setEnabled(context, it) },
        onBack = onBack,
    )
}

/** Feature flags plus the debug-mode switch (off by default), as plain data. */
@Composable
internal fun ExperimentalSettingsContent(
    features: List<Pair<AgentBuddyFeature, Boolean>>,
    debugEnabled: Boolean,
    onFeatureChange: (AgentBuddyFeature, Boolean) -> Unit,
    onDebugChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = "实验性", onBack = onBack) {
        settingsSection(
            title = "功能",
            key = "features",
            footer = {
                SettingsFooter(
                    text = "实验性功能可能不稳定，或在不另行通知的情况下变更。",
                    icon = Icons.Outlined.WarningAmber,
                    iconTint = AgentBuddyTheme.warning,
                )
            },
        ) {
            features.forEachIndexed { index, (feature, enabled) ->
                if (index > 0) SettingsRowDivider(indentForIcon = false)
                SettingsSwitchRow(
                    title = feature.displayName,
                    subtitle = feature.description,
                    checked = enabled,
                    onCheckedChange = { onFeatureChange(feature, it) },
                )
            }
        }

        settingsSection(title = "调试", key = "debug") {
            SettingsSwitchRow(
                title = "调试模式",
                subtitle = "在设置中显示调试选项，并启用对话中的调试显示",
                icon = Icons.Outlined.BugReport,
                checked = debugEnabled,
                onCheckedChange = onDebugChange,
            )
        }
    }
}
