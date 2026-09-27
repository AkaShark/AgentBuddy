package com.akashark.agentbuddy.android.ui.gallery

/**
 * Registered gallery pages. Each feature adds its page here; keep one entry
 * per line so parallel additions merge cleanly.
 */
val mintGalleryPages: List<MintGalleryPage> =
    listOf(
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
    )
