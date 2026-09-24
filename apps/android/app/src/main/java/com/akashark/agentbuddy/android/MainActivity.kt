package com.akashark.agentbuddy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.akashark.agentbuddy.android.push.PUSH_PREFS
import com.akashark.agentbuddy.android.push.PUSH_PREFS_FCM_TOKEN
import com.akashark.agentbuddy.android.push.PushDataKeys
import com.akashark.agentbuddy.android.push.PushNotifications
import com.akashark.agentbuddy.android.state.AppLifecycleController
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.OpenAIApiKeyStore
import com.akashark.agentbuddy.android.state.PetOverlayController
import com.akashark.agentbuddy.android.state.VisibleThreadTracker
import com.akashark.agentbuddy.android.ui.AnimatedSplashScreen
import com.akashark.agentbuddy.android.ui.ExperimentalFeatures
import com.akashark.agentbuddy.android.ui.AgentBuddyApp
import com.akashark.agentbuddy.android.ui.AgentBuddyAppTheme
import com.akashark.agentbuddy.android.ui.WallpaperManager
import com.akashark.agentbuddy.android.util.LLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey

class MainActivity : ComponentActivity() {
    companion object {
        // Same strings as the FCM data keys: a system-displayed push delivers
        // its data as extras of the launch intent.
        const val EXTRA_NOTIFICATION_SERVER_ID = PushDataKeys.SERVER_ID
        const val EXTRA_NOTIFICATION_THREAD_ID = PushDataKeys.THREAD_ID
        const val EXTRA_OPEN_PET_SETTINGS = "agentbuddy.openPetSettings"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

        /** How long a notification tap waits for its host to reconnect. */
        private const val NOTIFICATION_CONNECT_TIMEOUT_MS: ULong = 20_000uL

        /**
         * Set when the previous Activity instance was destroyed for a
         * configuration change (rotation, dark mode, ...) and deliberately
         * kept its [AppModel.start] reference alive, so the recreated
         * instance must not start it a second time.
         */
        private var appModelRetainedAcrossConfigChange = false
    }

    private var appModel: AppModel? = null
    private val lifecycleController = AppLifecycleController()
    private var openPetSettingsRequest by mutableStateOf(0)
    /** Latest FCM token (cached or fetched); registration is gated on notification permission. */
    private var fcmToken: String? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            LLog.i("MainActivity", "POST_NOTIFICATIONS granted=$granted")
            syncPushRegistration()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate to hand off the system splash
        // (Theme.App.Starting) to the Compose AnimatedSplashScreen without a
        // theme-background flash between them.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        OpenAIApiKeyStore(applicationContext).applyToEnvironment()
        ExperimentalFeatures.initialize(applicationContext)
        PetOverlayController.initialize(applicationContext)
        // The system displays background completion pushes on this channel.
        PushNotifications.ensureChannel(applicationContext)

        try {
            appModel = AppModel.init(this)
            WallpaperManager.initialize(this)
            if (appModelRetainedAcrossConfigChange) {
                appModelRetainedAcrossConfigChange = false
            } else {
                appModel?.start()
            }
        } catch (e: Exception) {
            LLog.e("MainActivity", "AppModel.start() failed", e)
        }
        loadPushToken()

        var showSplash by mutableStateOf(true)
        var contentReady by mutableStateOf(false)
        var minTimeElapsed by mutableStateOf(false)

        setContent {
            AgentBuddyAppTheme {
                Box(Modifier.fillMaxSize()) {
                    val model = appModel
                    if (model != null) {
                        AgentBuddyApp(
                            appModel = model,
                            openPetSettingsRequest = openPetSettingsRequest,
                        )
                    } else {
                        Text(
                            text = "AgentBuddy couldn't finish starting.",
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }

                    // Signal content ready when AgentBuddyApp composes
                    LaunchedEffect(model) {
                        if (model != null) {
                            contentReady = true
                        }
                    }

                    // Minimum display time
                    LaunchedEffect(Unit) {
                        delay(800)
                        minTimeElapsed = true
                    }

                    // Dismiss when both ready and min time elapsed (or hard max 3s)
                    LaunchedEffect(contentReady, minTimeElapsed) {
                        if (contentReady && minTimeElapsed) showSplash = false
                    }
                    LaunchedEffect(Unit) {
                        delay(3000)
                        showSplash = false
                    }

                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut(),
                    ) {
                        AnimatedSplashScreen()
                    }
                }
            }
        }

        handleNotificationIntent(intent)
        consumeOverlayNavigationIntent(intent)
        requestNotificationPermissionOnce()
    }

    override fun onStart() {
        super.onStart()
        VisibleThreadTracker.appInForeground = true
    }

    override fun onStop() {
        VisibleThreadTracker.appInForeground = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Notification permission / channel may have changed in system settings.
        syncPushRegistration()
        val model = appModel ?: return
        lifecycleScope.launch {
            lifecycleController.onResume(this@MainActivity, model)
            PetOverlayController.syncOverlayService(this@MainActivity)
        }
    }

    override fun onPause() {
        super.onPause()
        val model = appModel ?: return
        lifecycleController.onPause(model)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        consumeOverlayNavigationIntent(intent)
    }

    override fun onDestroy() {
        // A configuration change recreates the Activity immediately; keep the
        // connection and the AppModel subscription alive across it.
        if (isChangingConfigurations && appModel != null) {
            appModelRetainedAcrossConfigChange = true
            super.onDestroy()
            return
        }
        // Best-effort graceful shutdown of the iroh endpoint before the
        // Activity is fully destroyed. `runBlocking` keeps the close
        // handshake bounded so we don't ANR if the network stack is
        // unresponsive; `withTimeoutOrNull` caps it.
        appModel?.let { model ->
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(2_500) {
                    model.client.shutdownAlleycatEndpoint()
                }
            }
        }
        appModel?.stop()
        super.onDestroy()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val threadKey = consumeNotificationThreadKey(intent) ?: return
        val model = appModel ?: return
        lifecycleScope.launch {
            // Cold start / resume: onResume reconnects saved servers. Wait for
            // this host before loading so the read is authoritative; the push
            // itself is only a locator and never patches turn state.
            val connected = model.client.awaitServerConnected(
                threadKey.serverId,
                NOTIFICATION_CONNECT_TIMEOUT_MS,
            )
            if (!connected) {
                LLog.w("MainActivity", "notification tap: ${threadKey.serverId} not connected after wait; loading anyway")
            }
            val resolvedKey = model.ensureThreadLoaded(threadKey) ?: threadKey
            model.activateThread(resolvedKey)
            try {
                model.forceRefreshThreadAuthoritative(resolvedKey)
            } catch (error: Exception) {
                LLog.w("MainActivity", "notification tap: authoritative refresh failed: ${error.message}")
                model.refreshThreadSnapshot(resolvedKey)
            }
        }
    }

    private fun consumeNotificationThreadKey(intent: Intent?): ThreadKey? {
        intent ?: return null
        val serverId = intent.getStringExtra(EXTRA_NOTIFICATION_SERVER_ID)?.trim().orEmpty()
        val threadId = intent.getStringExtra(EXTRA_NOTIFICATION_THREAD_ID)?.trim().orEmpty()
        if (serverId.isEmpty() || threadId.isEmpty()) {
            return null
        }

        intent.removeExtra(EXTRA_NOTIFICATION_SERVER_ID)
        intent.removeExtra(EXTRA_NOTIFICATION_THREAD_ID)
        return ThreadKey(serverId = serverId, threadId = threadId)
    }

    private fun consumeOverlayNavigationIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_PET_SETTINGS, false)) {
            openPetSettingsRequest += 1
            intent.removeExtra(EXTRA_OPEN_PET_SETTINGS)
        }
    }

    /**
     * Android 13+ requires a runtime grant for POST_NOTIFICATIONS. Ask once
     * (remembered in prefs) so a denial is not re-prompted on every launch;
     * the request is async and does not block startup.
     */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = getSharedPreferences(PUSH_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun loadPushToken() {
        fcmToken = getSharedPreferences(PUSH_PREFS, MODE_PRIVATE)
            .getString(PUSH_PREFS_FCM_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
        syncPushRegistration()
        val messaging = try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
            }
            FirebaseMessaging.getInstance()
        } catch (e: IllegalStateException) {
            LLog.i("MainActivity", "Firebase is not configured; skipping FCM token fetch: ${e.message}")
            return
        }

        messaging.token
            .addOnSuccessListener { token ->
                if (token.isNotBlank()) {
                    getSharedPreferences(PUSH_PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(PUSH_PREFS_FCM_TOKEN, token)
                        .apply()
                    fcmToken = token
                    syncPushRegistration()
                }
            }
            .addOnFailureListener { error ->
                LLog.e("MainActivity", "Failed to fetch FCM token", error)
            }
    }

    /**
     * Hand Rust the FCM token only while notifications can be shown; `null`
     * (denied / disabled / no token) revokes host subscriptions. Chat and
     * turn flows do not depend on it.
     */
    private fun syncPushRegistration() {
        val model = appModel ?: return
        runCatching { PushNotifications.syncRegistration(applicationContext, model.client, fcmToken) }
            .onFailure { LLog.w("MainActivity", "setPushRegistration failed: ${it.message}") }
    }
}
