package com.akashark.agentbuddy.android.ui.gallery

import com.akashark.agentbuddy.android.ui.AgentBuddyAppearanceMode
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyConnectionState
import com.akashark.agentbuddy.android.ui.settings.AccountSheetState
import com.akashark.agentbuddy.android.ui.settings.AccountSignIn
import com.akashark.agentbuddy.android.ui.settings.AppearanceState
import com.akashark.agentbuddy.android.ui.settings.ServerConnectionMode
import com.akashark.agentbuddy.android.ui.settings.ServerEditFormState
import com.akashark.agentbuddy.android.ui.settings.ServerEditKind
import com.akashark.agentbuddy.android.ui.settings.SettingsAccountItem
import com.akashark.agentbuddy.android.ui.settings.SettingsServerItem
import com.akashark.agentbuddy.android.ui.settings.SettingsTopLevelActions
import com.akashark.agentbuddy.android.ui.settings.SettingsTopLevelState

/** Fixture data for the settings gallery pages (no AppModel, no network). */
internal object GallerySettingsFixtures {
    val topLevel =
        SettingsTopLevelState(
            monoFontEnabled = false,
            collapseTurns = true,
            showHomeTaskDetails = false,
            petVisible = true,
            petSubtitle = "小橘",
            showApps = true,
            showDebug = true,
            account =
                SettingsAccountItem(
                    serverId = "local",
                    serverName = "本设备",
                    status = "dev@example.com",
                ),
            servers =
                listOf(
                    SettingsServerItem("local", "本设备", isLocal = true, BuddyConnectionState.CONNECTED, "已连接 · 本地"),
                    SettingsServerItem("mbp", "MacBook Pro", isLocal = false, BuddyConnectionState.CONNECTED, "已连接 · 远程"),
                    SettingsServerItem("mini", "Mac mini", isLocal = false, BuddyConnectionState.CONNECTING, "正在连接 SSH… · 远程"),
                    SettingsServerItem("nas", "家里的 NAS", isLocal = false, BuddyConnectionState.DISCONNECTED, "已断开 · 远程"),
                ),
        )

    val noopTopLevelActions =
        SettingsTopLevelActions(
            onDone = {},
            onOpenAppearance = {},
            onSelectFont = {},
            onCollapseTurnsChange = {},
            onShowHomeTaskDetailsChange = {},
            onPetVisibleChange = {},
            onOpenPets = {},
            onOpenApps = {},
            onOpenExperimental = {},
            onOpenDebug = {},
            onOpenAccount = {},
            onEditServer = {},
            onRenameServer = {},
            onRemoveServer = {},
        )

    val account =
        AccountSheetState(
            serverName = "本设备",
            isLocal = true,
            signIn = AccountSignIn.CHATGPT,
            email = "dev@example.com",
            hasStoredApiKey = true,
            hasStoredBaseUrl = false,
            apiKey = "",
            baseUrl = "",
            isAuthWorking = false,
            error = null,
        )

    val serverEdit =
        ServerEditFormState(
            kind = ServerEditKind.EDITABLE,
            displayName = "Mac mini",
            mode = ServerConnectionMode.SSH,
            host = "mac-mini.local",
            sshPort = "22",
            wakeMac = "",
            codexPort = "8390",
            websocketUrl = "",
            isLocal = false,
            showReconnect = true,
            isReconnecting = false,
        )

    /** Theme rows come from the bundled theme index, which the gallery loads from assets. */
    fun appearance() =
        AppearanceState(
            mode = AgentBuddyAppearanceMode.SYSTEM,
            textSizeStep = 2,
            hasWallpaper = true,
            wallpaperError = null,
            lightTheme = AgentBuddyThemeManager.lightThemes.firstOrNull { it.slug == AgentBuddyThemeManager.lightTheme.slug },
            darkTheme = AgentBuddyThemeManager.darkThemes.firstOrNull { it.slug == AgentBuddyThemeManager.darkTheme.slug },
        )
}
