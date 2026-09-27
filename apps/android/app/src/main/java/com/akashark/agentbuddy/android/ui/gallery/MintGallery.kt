package com.akashark.agentbuddy.android.ui.gallery

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akashark.agentbuddy.android.BuildConfig
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyPageBackground
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing

/**
 * DEBUG state gallery: renders screens from fixed fixture data so every state
 * can be screenshotted on a device without a host. Mirrors the iOS
 * `--mint-gallery=<page>` / `--mint-dark` launch arguments:
 *
 * ```
 * adb shell am force-stop com.akashark.agentbuddy.android
 * adb shell am start -n com.akashark.agentbuddy.android/.MainActivity \
 *     --es mint_gallery home --ez mint_dark true
 * ```
 *
 * The gallery never starts the runtime, never connects and never writes the
 * user's theme preferences.
 */
object MintGallery {
    const val EXTRA_PAGE = "mint_gallery"
    const val EXTRA_DARK = "mint_dark"

    fun requestedPage(intent: Intent?): String? =
        if (BuildConfig.DEBUG) intent?.getStringExtra(EXTRA_PAGE)?.takeIf { it.isNotBlank() } else null

    fun requestsDark(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_DARK, false) ?: false
}

/** One screen of the gallery. */
class MintGalleryPage(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit,
)

@Composable
fun MintGalleryScreen(pageId: String) {
    val page = mintGalleryPages.firstOrNull { it.id == pageId }
    Box(Modifier.fillMaxSize().buddyPageBackground()) {
        if (page != null) {
            page.content()
        } else {
            Text(
                text = "未知页面「$pageId」。可用：" + mintGalleryPages.joinToString { it.id },
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.padding(BuddySpacing.xl),
            )
        }
    }
}
