package com.akashark.agentbuddy.android.ui.settings

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

private enum class SupportPage(val title: String, val url: String) {
    Privacy("隐私政策与数据删除", "https://akashark.github.io/AgentBuddy/privacy/"),
    Report("举报 AI 内容", "https://agentbuddy-content-reports.aaksharker.workers.dev/report"),
}

/** Available without connecting a host or signing in. No conversation data is injected. */
@Composable
internal fun SupportLinks() {
    var page by remember { mutableStateOf<SupportPage?>(null) }
    SettingsNavRow("隐私政策与数据删除", icon = Icons.Outlined.PrivacyTip, onClick = { page = SupportPage.Privacy })
    SettingsRowDivider()
    SettingsNavRow("举报 AI 内容", subtitle = "在应用内提交不当内容举报", icon = Icons.Outlined.Flag, onClick = { page = SupportPage.Report })
    page?.let { selected ->
        Dialog(onDismissRequest = { page = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            SupportWebPage(selected, onClose = { page = null })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SupportWebPage(page: SupportPage, onClose: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val webView = remember(page) {
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.saveFormData = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    return uri.scheme != "https" || uri.host !in setOf("akashark.github.io", "agentbuddy-content-reports.aaksharker.workers.dev")
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }
            }
            loadUrl(page.url)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    BackHandler { if (webView.canGoBack()) webView.goBack() else onClose() }
    Column(Modifier.fillMaxSize().background(AgentBuddyTheme.background)) {
        SettingsPageHeader(title = page.title, onBack = onClose)
        if (failed) {
            Text("页面加载失败，请检查网络后重试。", style = buddyTextStyle(BuddyTextStyle.BODY), color = AgentBuddyTheme.textPrimary, modifier = Modifier.padding(BuddySpacing.md))
            TextButton(onClick = { failed = false; webView.loadUrl(page.url) }) { Text("重试") }
        } else {
            AndroidView(factory = { webView }, modifier = Modifier.weight(1f))
        }
    }
}
