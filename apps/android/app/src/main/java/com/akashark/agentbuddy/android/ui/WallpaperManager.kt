package com.akashark.agentbuddy.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uniffi.codex_mobile_client.ThreadKey
import java.io.File
import java.io.FileOutputStream

internal const val WALLPAPER_MANAGER_TAG = "WallpaperManager"
private const val PREFS_FILENAME = "wallpaper_prefs.json"

object WallpaperManager {
    private var appContext: Context? = null
    private var initialized = false
    private var prefsData = JSONObject()

    var activeThreadKey by mutableStateOf<ThreadKey?>(null)

    // Transient config set by selection screen, consumed by adjust screen
    var pendingConfig by mutableStateOf<WallpaperConfig?>(null)

    // Incremented on every setWallpaper/clear to trigger recomposition in observers
    var version by mutableStateOf(0)
        private set

    var resolvedBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var resolvedConfig by mutableStateOf<WallpaperConfig?>(null)
        private set

    val isWallpaperSet: Boolean
        get() = resolvedConfig?.type?.let { it != WallpaperType.NONE } == true

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        loadPrefs()
        initialized = true
    }

    fun resolvedConfig(threadKey: ThreadKey?): WallpaperConfig? {
        if (threadKey == null) return null
        val threadScopeKey = "${threadKey.serverId}::${threadKey.threadId}"
        val threads = prefsData.optJSONObject("threads")
        val threadConfig = threads?.optJSONObject(threadScopeKey)
        if (threadConfig != null) return WallpaperConfig.fromJson(threadConfig)
        val servers = prefsData.optJSONObject("servers")
        val serverConfig = servers?.optJSONObject(threadKey.serverId)
        if (serverConfig != null) return WallpaperConfig.fromJson(serverConfig)
        return null
    }

    fun resolvedConfigForServer(serverId: String): WallpaperConfig? {
        val servers = prefsData.optJSONObject("servers")
        val serverConfig = servers?.optJSONObject(serverId)
        if (serverConfig != null) return WallpaperConfig.fromJson(serverConfig)
        return null
    }

    fun resolvedScope(threadKey: ThreadKey?): WallpaperScope? {
        if (threadKey == null) return null
        val threads = prefsData.optJSONObject("threads")
        val threadScopeKey = "${threadKey.serverId}::${threadKey.threadId}"
        if (threads?.optJSONObject(threadScopeKey) != null) {
            return WallpaperScope.Thread(threadKey)
        }
        val servers = prefsData.optJSONObject("servers")
        if (servers?.optJSONObject(threadKey.serverId) != null) {
            return WallpaperScope.Server(threadKey.serverId)
        }
        return null
    }

    fun resolvedScopeForServer(serverId: String): WallpaperScope? {
        val servers = prefsData.optJSONObject("servers")
        return if (servers?.optJSONObject(serverId) != null) {
            WallpaperScope.Server(serverId)
        } else {
            null
        }
    }

    fun setWallpaper(config: WallpaperConfig, scope: WallpaperScope) {
        if (scope == WallpaperScope.Pending) {
            pendingConfig = config
            return
        }
        when (scope) {
            is WallpaperScope.Thread -> {
                val key = "${scope.key.serverId}::${scope.key.threadId}"
                val threads = prefsData.optJSONObject("threads") ?: JSONObject()
                threads.put(key, config.toJson())
                prefsData.put("threads", threads)
            }
            is WallpaperScope.Server -> {
                val servers = prefsData.optJSONObject("servers") ?: JSONObject()
                servers.put(scope.serverId, config.toJson())
                prefsData.put("servers", servers)
            }
            WallpaperScope.Pending -> Unit
        }
        savePrefs()
        refreshResolved()
        version++
    }

    fun clearWallpaper(scope: WallpaperScope) {
        if (scope == WallpaperScope.Pending) {
            clearPendingWallpaper()
            return
        }
        when (scope) {
            is WallpaperScope.Thread -> {
                val key = "${scope.key.serverId}::${scope.key.threadId}"
                prefsData.optJSONObject("threads")?.remove(key)
                // Also remove custom image and video files
                imageFileForScope(scope)?.delete()
                videoFileForScope(scope)?.delete()
            }
            is WallpaperScope.Server -> {
                prefsData.optJSONObject("servers")?.remove(scope.serverId)
                imageFileForScope(scope)?.delete()
                videoFileForScope(scope)?.delete()
            }
            WallpaperScope.Pending -> Unit
        }
        savePrefs()
        refreshResolved()
        version++
    }

    fun clearPendingWallpaper() {
        pendingConfig = null
        imageFileForScope(WallpaperScope.Pending)?.delete()
        videoFileForScope(WallpaperScope.Pending)?.delete()
        thumbnailFileForScope(WallpaperScope.Pending)?.delete()
    }

    suspend fun stagePendingImageFromUri(uri: Uri): Boolean {
        val bitmap = WallpaperStorage.decodeBitmap(appContext ?: return false, uri) ?: return false
        val file = imageFileForScope(WallpaperScope.Pending) ?: return false
        val wrote = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream))
                    stream.fd.sync()
                }
            }.onFailure { Log.e(WALLPAPER_MANAGER_TAG, "Failed to write pending wallpaper image", it) }.isSuccess
        }
        if (!wrote) return false
        pendingConfig = WallpaperConfig(type = WallpaperType.CUSTOM_IMAGE)
        return true
    }

    fun previewBitmapForConfig(
        config: WallpaperConfig,
        threadKey: ThreadKey? = null,
        serverId: String? = null,
    ): Bitmap? {
        if (pendingConfig == config && config.type == WallpaperType.CUSTOM_IMAGE) {
            val pendingFile = imageFileForScope(WallpaperScope.Pending)
            if (pendingFile?.exists() == true) {
                return BitmapFactory.decodeFile(pendingFile.absolutePath)
            }
        }
        return resolvedBitmapForConfig(config, threadKey = threadKey, serverId = serverId)
    }

    fun previewVideoPathForConfig(
        config: WallpaperConfig,
        threadKey: ThreadKey? = null,
        serverId: String? = null,
    ): String? {
        if (pendingConfig == config &&
            (config.type == WallpaperType.CUSTOM_VIDEO || config.type == WallpaperType.VIDEO_URL)
        ) {
            val pendingFile = videoFileForScope(WallpaperScope.Pending)
            if (pendingFile?.exists() == true) {
                return pendingFile.absolutePath
            }
        }
        return if (threadKey != null) {
            videoFilePath(threadKey)
        } else {
            serverId?.let(::videoFilePathForServer)
        }
    }

    fun applyWallpaper(
        config: WallpaperConfig,
        targetScope: WallpaperScope,
        sourceScope: WallpaperScope? = null,
    ): Boolean {
        if (targetScope == WallpaperScope.Pending) {
            pendingConfig = config
            return true
        }

        when (config.type) {
            WallpaperType.CUSTOM_IMAGE -> {
                val source = imageFileForScope(WallpaperScope.Pending)
                    ?.takeIf(File::exists)
                    ?: sourceScope?.let(::imageFileForScope)?.takeIf(File::exists)
                val target = imageFileForScope(targetScope)
                if (source != null && target != null && source.absolutePath != target.absolutePath) {
                    WallpaperStorage.copyFile(source, target) ?: return false
                } else if (source == null && target?.exists() != true) {
                    return false
                }
            }
            WallpaperType.CUSTOM_VIDEO, WallpaperType.VIDEO_URL -> {
                val source = videoFileForScope(WallpaperScope.Pending)
                    ?.takeIf(File::exists)
                    ?: sourceScope?.let(::videoFileForScope)?.takeIf(File::exists)
                val target = videoFileForScope(targetScope)
                if (source != null && target != null && source.absolutePath != target.absolutePath) {
                    WallpaperStorage.copyFile(source, target) ?: return false
                } else if (source == null && target?.exists() != true) {
                    return false
                }

                val sourceThumb = thumbnailFileForScope(WallpaperScope.Pending)
                    ?.takeIf(File::exists)
                    ?: sourceScope?.let(::thumbnailFileForScope)?.takeIf(File::exists)
                val targetThumb = thumbnailFileForScope(targetScope)
                if (sourceThumb != null && targetThumb != null && sourceThumb.absolutePath != targetThumb.absolutePath) {
                    WallpaperStorage.copyFile(sourceThumb, targetThumb)
                }
            }
            else -> Unit
        }

        setWallpaper(config, targetScope)
        clearPendingWallpaper()
        return true
    }

    fun setActiveThread(key: ThreadKey?) {
        activeThreadKey = key
        refreshResolved()
    }

    suspend fun setCustomFromUri(uri: Uri): Boolean {
        val scope = activeScope() ?: return false
        return setCustomImageFromUri(uri, scope)
    }

    fun clear() {
        val scope = activeScope() ?: return
        clearWallpaper(scope)
    }

    suspend fun setCustomImageFromUri(uri: Uri, scope: WallpaperScope): Boolean {
        val context = appContext ?: return false
        val bitmap = WallpaperStorage.decodeBitmap(context, uri) ?: return false
        val file = imageFileForScope(scope) ?: return false
        val wrote = withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream))
                    stream.fd.sync()
                }
            }.onFailure { Log.e(WALLPAPER_MANAGER_TAG, "Failed to write wallpaper image", it) }.isSuccess
        }
        if (!wrote) return false

        val config = WallpaperConfig(type = WallpaperType.CUSTOM_IMAGE)
        setWallpaper(config, scope)
        return true
    }

    fun generatePatternBitmap(
        background: Color,
        accent: Color,
        patternType: PatternType,
    ): Bitmap = WallpaperPatternRenderer.generatePatternBitmap(background, accent, patternType)

    fun patternTypeForIndex(index: Int): PatternType {
        val types = PatternType.entries
        return types[index % types.size]
    }

    fun videoFilePath(scope: WallpaperScope): String? {
        val file = videoFileForScope(scope) ?: return null
        return if (file.exists()) file.absolutePath else null
    }

    fun videoFilePathForServer(serverId: String): String? {
        return videoFilePath(WallpaperScope.Server(serverId))
    }

    fun videoFilePath(threadKey: ThreadKey?): String? {
        if (threadKey == null) return null
        // Try thread-scoped first
        val threadPath = videoFilePath(WallpaperScope.Thread(threadKey))
        if (threadPath != null) return threadPath
        // Fall back to server-scoped
        return videoFilePath(WallpaperScope.Server(threadKey.serverId))
    }

    fun videoFileForScope(scope: WallpaperScope): File? {
        val context = appContext ?: return null
        return File(context.filesDir, "wallpaper_${fileKeyForScope(scope)}.mp4")
    }

    fun resolvedBitmapForConfig(config: WallpaperConfig, threadKey: ThreadKey?): Bitmap? {
        return resolvedBitmapForConfig(config, threadKey = threadKey, serverId = threadKey?.serverId)
    }

    fun resolvedBitmapForConfig(config: WallpaperConfig, threadKey: ThreadKey? = null, serverId: String? = null): Bitmap? {
        val resolvedServerId = threadKey?.serverId ?: serverId
        return when (config.type) {
            WallpaperType.NONE -> null
            WallpaperType.CUSTOM_VIDEO, WallpaperType.VIDEO_URL -> null // Video handled by player
            WallpaperType.THEME -> {
                val slug = config.themeSlug ?: return null
                val entry = AgentBuddyThemeManager.themeIndex.find { it.slug == slug } ?: return null
                val bg = colorFromHex(entry.backgroundHex)
                val accent = colorFromHex(entry.accentHex)
                val patternIndex = AgentBuddyThemeManager.themeIndex.indexOf(entry)
                generatePatternBitmap(bg, accent, patternTypeForIndex(patternIndex))
            }
            WallpaperType.CUSTOM_IMAGE -> {
                val context = appContext ?: return null
                if (threadKey != null) {
                    val fileKey = "${threadKey.serverId}_${threadKey.threadId}"
                    val file = File(context.filesDir, "wallpaper_$fileKey.jpg")
                    if (!file.exists()) {
                        // Try server-scoped
                        val serverFile = File(context.filesDir, "wallpaper_server_${threadKey.serverId}.jpg")
                        if (serverFile.exists()) {
                            BitmapFactory.decodeFile(serverFile.absolutePath)
                        } else null
                    } else {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                } else if (resolvedServerId != null) {
                    val serverFile = File(context.filesDir, "wallpaper_server_${resolvedServerId}.jpg")
                    if (serverFile.exists()) {
                        BitmapFactory.decodeFile(serverFile.absolutePath)
                    } else null
                } else {
                    null
                }
            }
            WallpaperType.SOLID_COLOR -> null // Solid color is handled via Compose background
        }
    }

    fun cleanup(knownServerIds: Set<String>, knownThreadKeys: Set<String>) {
        val threads = prefsData.optJSONObject("threads")
        if (threads != null) {
            val keysToRemove = mutableListOf<String>()
            val iter = threads.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                if (key !in knownThreadKeys) keysToRemove.add(key)
            }
            keysToRemove.forEach { threads.remove(it) }
        }
        val servers = prefsData.optJSONObject("servers")
        if (servers != null) {
            val keysToRemove = mutableListOf<String>()
            val iter = servers.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                if (key !in knownServerIds) keysToRemove.add(key)
            }
            keysToRemove.forEach { servers.remove(it) }
        }
        savePrefs()
        // Clean orphaned image and video files
        val context = appContext ?: return
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("wallpaper_") && (it.name.endsWith(".jpg") || it.name.endsWith(".mp4"))
        }?.forEach { file ->
            val name = file.nameWithoutExtension.removePrefix("wallpaper_")
            val isThreadFile = knownThreadKeys.any { key ->
                val parts = key.split("::")
                if (parts.size == 2) name == "${parts[0]}_${parts[1]}" else false
            }
            val isServerFile = knownServerIds.any { name == "server_$it" }
            if (!isThreadFile && !isServerFile) file.delete()
        }
    }

    fun refreshResolved() {
        val key = activeThreadKey
        val config = resolvedConfig(key)
        resolvedConfig = config
        resolvedBitmap = if (config != null) resolvedBitmapForConfig(config, key) else null
    }

    private fun loadPrefs() {
        val context = appContext ?: return
        val file = File(context.filesDir, PREFS_FILENAME)
        if (file.exists()) {
            prefsData = runCatching {
                JSONObject(file.readText())
            }.getOrElse {
                Log.w(WALLPAPER_MANAGER_TAG, "Failed to parse wallpaper prefs", it)
                JSONObject()
            }
        }
    }

    private fun savePrefs() {
        val context = appContext ?: return
        val file = File(context.filesDir, PREFS_FILENAME)
        runCatching {
            file.writeText(prefsData.toString(2))
        }.onFailure {
            Log.e(WALLPAPER_MANAGER_TAG, "Failed to save wallpaper prefs", it)
        }
    }

    private fun imageFileForScope(scope: WallpaperScope): File? {
        val context = appContext ?: return null
        return File(context.filesDir, "wallpaper_${fileKeyForScope(scope)}.jpg")
    }

    private fun activeScope(): WallpaperScope? {
        val key = activeThreadKey ?: return null
        return WallpaperScope.Thread(key)
    }

    private fun thumbnailFileForScope(scope: WallpaperScope): File? {
        val context = appContext ?: return null
        return File(context.filesDir, "wallpaper_${fileKeyForScope(scope)}_thumb.jpg")
    }

    private fun fileKeyForScope(scope: WallpaperScope): String =
        when (scope) {
            is WallpaperScope.Thread -> "${scope.key.serverId}_${scope.key.threadId}"
            is WallpaperScope.Server -> "server_${scope.serverId}"
            WallpaperScope.Pending -> "pending"
        }
}
