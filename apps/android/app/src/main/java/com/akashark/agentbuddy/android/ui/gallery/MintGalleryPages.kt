package com.akashark.agentbuddy.android.ui.gallery

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AnimatedSplashScreen

/**
 * Registered gallery pages. Each feature adds its page here; keep one entry
 * per line so parallel additions merge cleanly.
 */
val mintGalleryPages: List<MintGalleryPage> =
    listOf(
        MintGalleryPage("splash", "启动画面") { GallerySplashPage() },
        MintGalleryPage("components", "组件") { GalleryComponentsPage() },
        MintGalleryPage("settings", "设置") { GallerySettingsPage() },
        MintGalleryPage("appearance", "外观") { GalleryAppearancePage() },
        MintGalleryPage("themes", "主题") { GalleryThemesPage() },
        MintGalleryPage("wallpaper", "壁纸") { GalleryWallpaperPage() },
        MintGalleryPage("account", "账户") { GalleryAccountPage() },
        MintGalleryPage("server-edit", "编辑服务器") { GalleryServerEditPage() },
        MintGalleryPage("home", "任务") { GalleryHomePage() },
        MintGalleryPage("home-detail", "任务（详情）") { GalleryHomeDetailPage() },
        MintGalleryPage("home-empty", "任务（无主机）") { GalleryHomeEmptyPage() },
        MintGalleryPage("home-notasks", "任务（无任务）") { GalleryHomeNoTasksPage() },
        MintGalleryPage("home-offline", "任务（主机离线）") { GalleryHomeOfflinePage() },
        MintGalleryPage("home-search", "任务搜索") { GalleryHomeSearchPage() },
        MintGalleryPage("projects", "项目") { GalleryProjectsPage() },
        MintGalleryPage("hosts", "主机") { GalleryHostsPage() },
        MintGalleryPage("newtask", "新建任务") { GalleryNewTaskPage() },
        MintGalleryPage("approvals", "审批") { GalleryApprovalsPage() },
        MintGalleryPage("approval-banner", "审批横幅") { GalleryApprovalBannerPage() },
        MintGalleryPage("conversation-header", "会话标题栏") { GalleryConversationHeaderPage() },
        MintGalleryPage("composer", "输入框") { GalleryComposerPage() },
        MintGalleryPage("composer-expanded-queue", "全屏编辑（排队）") { GalleryComposerExpandedQueuePage() },
        MintGalleryPage("info", "任务信息") { GalleryInfoPage() },
        MintGalleryPage("info-server", "服务器信息") { GalleryServerInfoPage() },
        MintGalleryPage("models", "模型面板") { GalleryModelsPage() },
        MintGalleryPage("models-locked", "模型面板（锁定）") { GalleryModelsLockedPage() },
        MintGalleryPage("apps", "应用") { GalleryAppsPage() },
        MintGalleryPage("apps-empty", "应用（空）") { GalleryAppsEmptyPage() },
        MintGalleryPage("app-update", "更新应用") { GalleryAppUpdatePage() },
        MintGalleryPage("app-broken", "应用文件丢失") { GalleryAppBrokenPage() },
        MintGalleryPage("voice", "实时语音") { GalleryVoicePage() },
        MintGalleryPage("voice-connecting", "实时语音（连接中）") { GalleryVoiceConnectingPage() },
        MintGalleryPage("terminal-chrome", "终端") { GalleryTerminalChromePage() },
        MintGalleryPage("terminal-config", "终端设置") { GalleryTerminalConfigPage() },
        MintGalleryPage("conversation", "会话时间线") { GalleryConversationPage() },
        MintGalleryPage("conversation-long", "会话长内容") { GalleryConversationLongPage() },
        MintGalleryPage("discovery", "添加主机") { GalleryDiscoveryPage() },
        MintGalleryPage("discovery-waking", "添加主机（唤醒中）") { GalleryDiscoveryPage(wakingHostName = "studio.local") },
        MintGalleryPage("pair", "扫码配对") { GalleryPairPage() },
        MintGalleryPage("pair-empty", "扫码配对（粘贴 JSON）") { GalleryPairPage(empty = true) },
        MintGalleryPage("pair-scan", "扫描二维码") { GalleryPairScanPage() },
        MintGalleryPage("manual-entry", "SSH 或地址") { GalleryManualEntryPage() },
        MintGalleryPage("ssh-login", "SSH 登录") { GallerySshLoginPage() },
        MintGalleryPage("ssh-agents", "远程智能体") { GallerySshAgentsPage() },
        MintGalleryPage("slingshot", "已连接电脑") { GallerySlingshotPage() },
        MintGalleryPage("tasks-all", "全部任务") { GalleryTasksAllPage() },
        MintGalleryPage("tasks-all-forks", "全部任务（只看分叉）") { GalleryTasksAllPage(GallerySessionsFixtures.state(showOnlyForks = true)) },
        MintGalleryPage("directory-picker", "选择目录") { GalleryDirectoryPickerPage() },
        MintGalleryPage("directory-picker-error", "选择目录（无法加载）") { GalleryDirectoryPickerPage(error = true) },
        MintGalleryPage("project-picker", "项目") { GalleryProjectPickerPage() },
        MintGalleryPage("project-picker-empty", "项目（暂无）") { GalleryProjectPickerPage(empty = true) },
        MintGalleryPage("tasks-all-empty", "全部任务（无主机）") { GalleryTasksAllPage(GallerySessionsFixtures.state(summaries = emptyList(), connectedHostCount = 0, activeKey = null)) },
    )

/** Preview the launch resources in the gallery's requested mode without changing the device. */
@Composable
private fun GallerySplashPage() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = AgentBuddyTheme.isDark
    val previewContext = remember(context, configuration, dark) {
        val previewConfiguration = Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        context.createConfigurationContext(previewConfiguration)
    }
    // Only this resource-based preview needs an overridden context. Activity
    // effects and every other gallery page keep their original context.
    CompositionLocalProvider(
        LocalContext provides previewContext,
        LocalConfiguration provides previewContext.resources.configuration,
    ) {
        AnimatedSplashScreen()
    }
}
