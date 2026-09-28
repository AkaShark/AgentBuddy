package com.akashark.agentbuddy.android.ui.apps

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.akashark.agentbuddy.android.ui.conversation.AppStateInjection
import com.akashark.agentbuddy.android.ui.conversation.WidgetBridge
import com.akashark.agentbuddy.android.ui.conversation.escapeJsString
import com.akashark.agentbuddy.android.ui.conversation.pushWidgetContent
import com.akashark.agentbuddy.android.ui.conversation.wrapWidgetHtml
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.SavedAppWithPayload

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AppModeWebView(
    payload: SavedAppWithPayload,
    dimmed: Boolean,
    appModel: com.akashark.agentbuddy.android.state.AppModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = payload.app
    val appId = app.id

    // Per-view-session cache of the Rust-owned ephemeral `structuredResponse`
    // thread id. remember(appId) resets it whenever the user navigates to a
    // different saved app or leaves and re-enters the view. Deliberately NOT
    // rememberSaveable — ephemeral thread ids must not survive process death.
    val cachedStructuredThreadId = remember(appId) { mutableStateOf<String?>(null) }
    val webViewRef = remember(appId) { mutableStateOf<WebView?>(null) }

    val savedBridge = remember(appId) {
        SavedAppBridge(
            context = context,
            appId = appId,
            onStructuredRequest = { requestId, prompt, schemaJson ->
                val serverId = resolveServerId(appModel, app)
                if (serverId == null) {
                    rejectStructuredResponse(
                        webViewRef.value,
                        requestId,
                        "No connected server available",
                    )
                    return@SavedAppBridge
                }
                scope.launch {
                    val cached = cachedStructuredThreadId.value
                    val result = try {
                        appModel.client.structuredResponse(
                            serverId = serverId,
                            cachedThreadId = cached,
                            prompt = prompt,
                            outputSchemaJson = schemaJson,
                        )
                    } catch (e: Throwable) {
                        rejectStructuredResponse(
                            webViewRef.value,
                            requestId,
                            e.message ?: "structuredResponse failed",
                        )
                        return@launch
                    }
                    when (result) {
                        is uniffi.codex_mobile_client.StructuredResponseResult.Success -> {
                            cachedStructuredThreadId.value = result.threadId
                            resolveStructuredResponse(
                                webViewRef.value,
                                requestId,
                                result.responseJson,
                            )
                        }
                        is uniffi.codex_mobile_client.StructuredResponseResult.Error -> {
                            rejectStructuredResponse(
                                webViewRef.value,
                                requestId,
                                result.message,
                            )
                        }
                    }
                }
            },
        )
    }
    val widgetBridge = remember(appId) {
        WidgetBridge(
            onHeight = { /* saved-app fills its Composable; no dynamic height needed. */ },
            onSendPrompt = { /* Saved apps don't plumb a composer here. */ },
            onOpenLink = { url ->
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                    )
                } catch (_: Exception) {}
            },
            onReady = { /* Informational. */ },
        )
    }

    // Shell is static for a given payload; only the body HTML changes if the
    // app is updated via the overlay. Build shell once (empty body) and push
    // the widget HTML through `window._setContent` after `onPageFinished`.
    val shell = remember(payload.stateJson, app.schemaVersion) {
        wrapWidgetHtml(
            context = context,
            widgetHtml = "",
            appState = AppStateInjection(
                stateJson = payload.stateJson,
                schemaVersion = app.schemaVersion,
            ),
        )
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewRef.value = this
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                addJavascriptInterface(savedBridge, SavedAppBridge.INTERFACE_NAME)
                addJavascriptInterface(widgetBridge, WidgetBridge.INTERFACE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (view == null) return
                        view.setTag(
                            com.akashark.agentbuddy.android.R.id.widget_webview_shell_ready,
                            true,
                        )
                        val pending = view.getTag(
                            com.akashark.agentbuddy.android.R.id.widget_webview_pending_html,
                        ) as? String
                        if (pending != null) {
                            view.setTag(
                                com.akashark.agentbuddy.android.R.id.widget_webview_pending_html,
                                null,
                            )
                            pushWidgetContent(view, pending, runScripts = true)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (url.isBlank() || url.startsWith("about:")) return false
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
                    shell,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (dimmed) 0.55f else 1f),
        update = { webView ->
            val html = payload.widgetHtml
            val lastEscaped = webView.getTag(
                com.akashark.agentbuddy.android.R.id.widget_webview_last_escaped,
            ) as? String
            val escaped = escapeJsString(html)
            if (escaped == lastEscaped) return@AndroidView
            webView.setTag(
                com.akashark.agentbuddy.android.R.id.widget_webview_last_escaped,
                escaped,
            )
            val shellReady = webView.getTag(
                com.akashark.agentbuddy.android.R.id.widget_webview_shell_ready,
            ) as? Boolean ?: false
            if (!shellReady) {
                webView.setTag(
                    com.akashark.agentbuddy.android.R.id.widget_webview_pending_html,
                    html,
                )
            } else {
                pushWidgetContent(webView, html, runScripts = true)
            }
        },
    )
}

private fun resolveStructuredResponse(
    webView: WebView?,
    requestId: String,
    responseJson: String,
) {
    if (webView == null) return
    val script = "window.__resolveStructuredResponse(" +
        jsStringLiteral(requestId) +
        "," + jsStringLiteral(responseJson) + ");"
    webView.post { webView.evaluateJavascript(script, null) }
}

private fun rejectStructuredResponse(
    webView: WebView?,
    requestId: String,
    message: String,
) {
    if (webView == null) return
    val script = "window.__rejectStructuredResponse(" +
        jsStringLiteral(requestId) +
        "," + jsStringLiteral(message) + ");"
    webView.post { webView.evaluateJavascript(script, null) }
}

/** JS single-quoted string literal for safe splicing into `evaluateJavascript`. */
private fun jsStringLiteral(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('\'')
    for (c in s) {
        when (c.code) {
            0x5C -> sb.append("\\\\")
            0x27 -> sb.append("\\'")
            0x0A -> sb.append("\\n")
            0x0D -> sb.append("\\r")
            0x2028 -> sb.append("\\u2028")
            0x2029 -> sb.append("\\u2029")
            else -> sb.append(c)
        }
    }
    sb.append('\'')
    return sb.toString()
}
