package com.akashark.agentbuddy.android.ui.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Settings — hierarchical navigation matching iOS:
 * Top level: Appearance → | Font | Conversation | Experimental → | Account | Servers
 * Appearance pushes to sub-screen with theme pickers.
 * Experimental pushes to sub-screen with feature toggles.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Top-level Settings
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onOpenAccount: (serverId: String) -> Unit,
    initialSubScreen: SettingsStartDestination = SettingsStartDestination.TopLevel,
    onOpenApps: (() -> Unit)? = null,
    /** Opens that host's connection editor right away (home host menu 「编辑连接」). */
    initialEditServerId: String? = null,
    onInitialEditShown: () -> Unit = {},
) {
    // Sub-screen navigation
    var subScreen by remember(initialSubScreen) {
        mutableStateOf(
            when (initialSubScreen) {
                SettingsStartDestination.TopLevel -> null
                SettingsStartDestination.Pets -> SettingsSubScreen.Pets
            },
        )
    }

    // Kept here so returning from a sub-screen restores the list position.
    val topLevelListState = rememberLazyListState()

    when (subScreen) {
        SettingsSubScreen.Appearance -> AppearanceScreen(onBack = { subScreen = null })
        SettingsSubScreen.Experimental -> ExperimentalScreen(onBack = { subScreen = null })
        SettingsSubScreen.Pets -> PetsScreen(onBack = { subScreen = null })
        SettingsSubScreen.Debug -> DebugScreen(onBack = { subScreen = null })
        null -> SettingsTopLevel(
            onDismiss = onDismiss,
            onOpenAppearance = { subScreen = SettingsSubScreen.Appearance },
            onOpenExperimental = { subScreen = SettingsSubScreen.Experimental },
            onOpenPets = { subScreen = SettingsSubScreen.Pets },
            onOpenDebug = { subScreen = SettingsSubScreen.Debug },
            onOpenAccount = onOpenAccount,
            onOpenApps = onOpenApps,
            listState = topLevelListState,
            initialEditServerId = initialEditServerId,
            onInitialEditShown = onInitialEditShown,
        )
    }
}

enum class SettingsStartDestination { TopLevel, Pets }

private enum class SettingsSubScreen { Appearance, Experimental, Pets, Debug }
