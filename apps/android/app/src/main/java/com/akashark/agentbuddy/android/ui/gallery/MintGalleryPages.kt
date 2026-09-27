package com.akashark.agentbuddy.android.ui.gallery

/**
 * Registered gallery pages. Each feature adds its page here; keep one entry
 * per line so parallel additions merge cleanly.
 */
val mintGalleryPages: List<MintGalleryPage> =
    listOf(
        MintGalleryPage("components", "组件") { GalleryComponentsPage() },
    )
