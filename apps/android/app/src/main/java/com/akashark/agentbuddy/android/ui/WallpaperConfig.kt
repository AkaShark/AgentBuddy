package com.akashark.agentbuddy.android.ui

import org.json.JSONObject
import uniffi.codex_mobile_client.ThreadKey

enum class WallpaperType {
    NONE, THEME, CUSTOM_IMAGE, SOLID_COLOR, CUSTOM_VIDEO, VIDEO_URL
}

enum class PatternType {
    DOT_GRID, DIAGONAL_LINES, CONCENTRIC_CIRCLES, HEXAGONAL_MESH, CROSS_HATCH, WAVE_LINES
}

data class WallpaperConfig(
    val type: WallpaperType = WallpaperType.NONE,
    val themeSlug: String? = null,
    val colorHex: String? = null,
    val blur: Float = 0f,
    val brightness: Float = 1f,
    val motionEnabled: Boolean = false,
    val videoURL: String? = null,
    val videoDuration: Float? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.name.lowercase())
        themeSlug?.let { put("themeSlug", it) }
        colorHex?.let { put("colorHex", it) }
        put("blur", blur.toDouble())
        put("brightness", brightness.toDouble())
        put("motionEnabled", motionEnabled)
        videoURL?.let { put("videoURL", it) }
        videoDuration?.let { put("videoDuration", it.toDouble()) }
    }

    companion object {
        fun fromJson(json: JSONObject): WallpaperConfig = WallpaperConfig(
            type = when (json.optString("type", "none")) {
                "theme" -> WallpaperType.THEME
                "custom_image" -> WallpaperType.CUSTOM_IMAGE
                "solid_color" -> WallpaperType.SOLID_COLOR
                "custom_video" -> WallpaperType.CUSTOM_VIDEO
                "video_url" -> WallpaperType.VIDEO_URL
                else -> WallpaperType.NONE
            },
            themeSlug = json.optString("themeSlug").trim().ifEmpty { null },
            colorHex = json.optString("colorHex").trim().ifEmpty { null },
            blur = json.optDouble("blur", 0.0).toFloat(),
            brightness = json.optDouble("brightness", 1.0).toFloat(),
            motionEnabled = json.optBoolean("motionEnabled", false),
            videoURL = json.optString("videoURL").trim().ifEmpty { null },
            videoDuration = if (json.has("videoDuration")) json.optDouble("videoDuration").toFloat() else null,
        )
    }
}

sealed class WallpaperScope {
    data class Thread(val key: ThreadKey) : WallpaperScope()
    data class Server(val serverId: String) : WallpaperScope()
    data object Pending : WallpaperScope()
}
