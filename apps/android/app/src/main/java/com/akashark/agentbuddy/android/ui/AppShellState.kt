package com.akashark.agentbuddy.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.ui.homeshell.HomeTaskMemory
import com.akashark.agentbuddy.android.ui.settings.SettingsStartDestination
import uniffi.codex_mobile_client.AppProject
import uniffi.codex_mobile_client.ThreadKey

/**
 * UI-only state of the app root: the hand-rolled navigation stack, which
 * root sheet is open, the home host / project selection and the home memory.
 * Business state stays in the Rust `AppStore`.
 */
class AppShellState(initialServerId: String?) {
    var navStack by mutableStateOf<List<Route>>(listOf(Route.Home))
        private set

    val currentRoute: Route get() = navStack.lastOrNull() ?: Route.Home

    // Root sheets.
    var showDiscovery by mutableStateOf(false)
    var showQrPairing by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var settingsStartDestination by mutableStateOf(SettingsStartDestination.TopLevel)
    var showAccountForServer by mutableStateOf<String?>(null)
    var directoryPickerServerId by mutableStateOf<String?>(null)
    var directoryPickerForProject by mutableStateOf(false)
    var showProjectPicker by mutableStateOf(false)
    var isStartingVoice by mutableStateOf(false)

    // Home selection (persisted through SavedProjectStore by the root effects).
    var selectedServerId by mutableStateOf(initialServerId)
    var selectedProject by mutableStateOf<AppProject?>(null)

    val homeMemory = HomeTaskMemory()

    fun navigate(route: Route) {
        navStack = navStack + route
    }

    fun navigateBack() {
        if (navStack.size > 1) navStack = navStack.dropLast(1)
    }

    fun navigateToConversation(key: ThreadKey) {
        navStack = listOf(Route.Home, Route.Conversation(key))
    }

    /**
     * Opens a conversation on top of the current route so back returns there
     * (全部任务, reached from home or `/resume`). A no-op when that
     * conversation is already on top, e.g. after the active-thread
     * auto-navigation handled a fork first.
     */
    fun pushConversation(key: ThreadKey) {
        if ((currentRoute as? Route.Conversation)?.key == key) return
        navigate(Route.Conversation(key))
    }

    /** Drops routes matching [predicate] (wallpaper flows pop back to their origin). */
    fun popRoutes(predicate: (Route) -> Boolean) {
        navStack = navStack.filterNot(predicate)
    }

    fun openSettings(destination: SettingsStartDestination = SettingsStartDestination.TopLevel) {
        settingsStartDestination = destination
        showSettings = true
    }

    fun closeSettings() {
        showSettings = false
        settingsStartDestination = SettingsStartDestination.TopLevel
    }

    val interceptsBack: Boolean
        get() =
            showDiscovery || showQrPairing || showSettings || showAccountForServer != null ||
                directoryPickerServerId != null || showProjectPicker || navStack.size > 1

    /** Closes the top-most root layer, in the same priority order as before. */
    fun handleBack(onCloseDiscovery: () -> Unit) {
        when {
            showAccountForServer != null -> showAccountForServer = null
            directoryPickerServerId != null -> directoryPickerServerId = null
            showProjectPicker -> showProjectPicker = false
            showQrPairing -> showQrPairing = false
            showSettings -> showSettings = false
            showDiscovery -> {
                showDiscovery = false
                onCloseDiscovery()
            }
            navStack.size > 1 -> navStack = navStack.dropLast(1)
        }
    }
}
