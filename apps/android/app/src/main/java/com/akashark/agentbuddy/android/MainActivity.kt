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
import com.akashark.agentbuddy.android.state.AppLifecycleController
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.state.OpenAIApiKeyStore
import com.akashark.agentbuddy.android.state.PetOverlayController
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
        const val EXTRA_NOTIFICATION_SERVER_ID = "agentbuddy.notification.serverId"
        const val EXTRA_NOTIFICATION_THREAD_ID = "agentbuddy.notification.threadId"
        const val EXTRA_OPEN_PET_SETTINGS = "agentbuddy.openPetSettings"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

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
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            LLog.i("MainActivity", "POST_NOTIFICATIONS granted=$granted")
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

    override fun onResume() {
        super.onResume()
        val model = appModel ?: return
        lifecycleScope.launch {
            lifecycleController.onResume(this@MainActivity, model)
            PetOverlayController.syncOverlayService(this@MainActivity)
        }
    }

    override fun onPause() {
        super.onPause()
        val model = appModel ?: return
        lifecycleController.onPause(this, model)
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
            model.activateThread(threadKey)

            val resolvedKey = model.ensureThreadLoaded(threadKey) ?: threadKey
            model.activateThread(resolvedKey)
            model.refreshThreadSnapshot(resolvedKey)
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
        val prefs = getSharedPreferences("agentbuddy_push", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) return
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun loadPushToken() {
        val cachedToken = getSharedPreferences("agentbuddy_push", MODE_PRIVATE)
            .getString("fcm_token", null)
            ?.takeIf { it.isNotBlank() }
        if (cachedToken != null) {
            lifecycleController.setDevicePushToken(cachedToken)
        }
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
                    getSharedPreferences("agentbuddy_push", MODE_PRIVATE)
                        .edit()
                        .putString("fcm_token", token)
                        .apply()
                    lifecycleController.setDevicePushToken(token)
                }
            }
            .addOnFailureListener { error ->
                LLog.e("MainActivity", "Failed to fetch FCM token", error)
            }
    }
}
