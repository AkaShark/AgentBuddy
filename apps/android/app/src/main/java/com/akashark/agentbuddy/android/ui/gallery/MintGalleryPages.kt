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
    )
