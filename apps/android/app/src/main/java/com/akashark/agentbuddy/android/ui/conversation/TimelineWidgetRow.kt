package com.akashark.agentbuddy.android.ui.conversation

import android.annotation.SuppressLint
import android.content.Intent
import com.akashark.agentbuddy.android.R
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.SavedAppsStore
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppOperationStatus
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WidgetRow(
    data: uniffi.codex_mobile_client.HydratedWidgetData,
    originThreadId: String?,
    onOpenSavedApp: ((String) -> Unit)?,
    onWidgetPrompt: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slug = data.appId?.takeIf { it.isNotBlank() }

    // Dynamic height: seeded from the widget's declared height (same
    // `coerceIn(200, 720)` floor/ceiling as before) and then updated by the
    // shell's `_reportHeight` bridge calls once morphdom has rendered.
    val minDp = 200.dp
    val maxDp = 720.dp
    val initialDp = remember(data.height) {
        data.height.coerceIn(200.0, 720.0).roundToInt().dp
    }
    var widgetHeight by remember(minDp, maxDp) { mutableStateOf(initialDp) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Callbacks captured once at factory time still see the latest state
    // via these rememberUpdatedState proxies.
    val currentOnWidgetPrompt by androidx.compose.runtime.rememberUpdatedState(onWidgetPrompt)
    val currentIsFinalized by androidx.compose.runtime.rememberUpdatedState(data.isFinalized)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.title.ifBlank { "小组件" },
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = data.status,
                color = statusTint(
                    when (data.status.lowercase()) {
                        "completed" -> AppOperationStatus.COMPLETED
                        "failed" -> AppOperationStatus.FAILED
                        else -> AppOperationStatus.IN_PROGRESS
                    }
                ),
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                fontWeight = FontWeight.Medium,
            )
        }

        AndroidView(
            factory = { ctx ->
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val bridge = WidgetBridge(
                    onHeight = { reportedPx ->
                        mainHandler.post {
                            val dp = with(density) { reportedPx.toDp() }
                                .coerceIn(minDp, maxDp)
                            if (kotlin.math.abs((dp - widgetHeight).value) > 1f) {
                                widgetHeight = dp
                            }
                        }
                    },
                    onSendPrompt = { text ->
                        mainHandler.post { currentOnWidgetPrompt?.invoke(text) }
                    },
                    onOpenLink = { url ->
                        mainHandler.post {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                )
                            } catch (_: Exception) {}
                        }
                    },
                    onReady = {
                        // `onPageFinished` also flips the ready flag; this
                        // bridge callback is informational. No-op here.
                    },
                )
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.loadsImagesAutomatically = true
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    addJavascriptInterface(bridge, WidgetBridge.INTERFACE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (view == null) return
                            view.setTag(R.id.widget_webview_shell_ready, true)
                            val pending = view.getTag(R.id.widget_webview_pending_html) as? String
                            if (pending != null) {
                                view.setTag(R.id.widget_webview_pending_html, null)
                                pushWidgetContent(view, pending, runScripts = currentIsFinalized)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            if (url.isBlank() || url.startsWith("about:")) {
                                return false
                            }
                            return try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }
                    loadDataWithBaseURL(
                        "https://widget.local/",
                        wrapWidgetHtml(ctx, ""),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(widgetHeight)
                .clip(RoundedCornerShape(10.dp)),
            update = { webView ->
                val html = data.widgetHtml
                val lastEscaped = webView.getTag(R.id.widget_webview_last_escaped) as? String
                val hasFinalized = webView.getTag(R.id.widget_webview_document) as? Boolean ?: false
                val shellReady = webView.getTag(R.id.widget_webview_shell_ready) as? Boolean ?: false
                val escaped = escapeJsString(html)
                val shouldPush = escaped != lastEscaped || (data.isFinalized && !hasFinalized)
                if (!shouldPush) return@AndroidView
                webView.setTag(R.id.widget_webview_last_escaped, escaped)
                if (data.isFinalized) {
                    webView.setTag(R.id.widget_webview_document, true)
                }
                if (!shellReady) {
                    // Store raw html — `onPageFinished` will push it through
                    // `window._setContent` once morphdom is ready.
                    webView.setTag(R.id.widget_webview_pending_html, html)
                } else {
                    pushWidgetContent(webView, html, runScripts = data.isFinalized)
                }
            },
        )

        if (slug != null && data.isFinalized && originThreadId != null) {
            SavedAsAppChip(
                slug = slug,
                onClick = {
                    scope.launch {
                        val app = try {
                            SavedAppsStore.appForSlug(context, slug, originThreadId)
                        } catch (_: Exception) { null }
                        if (app != null) {
                            onOpenSavedApp?.invoke(app.id)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SavedAsAppChip(
    slug: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                AgentBuddyTheme.surfaceLight.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.GridView,
            contentDescription = null,
            tint = AgentBuddyTheme.accent,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = "已保存为",
            color = AgentBuddyTheme.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = slug,
            color = AgentBuddyTheme.accent,
            fontSize = 11.sp,
            fontFamily = AgentBuddyTheme.monoFont,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
