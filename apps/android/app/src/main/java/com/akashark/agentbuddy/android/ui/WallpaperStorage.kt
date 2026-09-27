package com.akashark.agentbuddy.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val MAX_WALLPAPER_DIMENSION = 2048

/** Asset file helpers for [WallpaperManager]: copying wallpaper files and decoding picked images. */
internal object WallpaperStorage {
    fun copyFile(source: File, dest: File): File? =
        runCatching {
            dest.parentFile?.mkdirs()
            source.inputStream().use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            dest
        }.onFailure {
            Log.e(WALLPAPER_MANAGER_TAG, "Failed to copy wallpaper asset from ${source.absolutePath} to ${dest.absolutePath}", it)
        }.getOrNull()

    suspend fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val size = info.size
                        val largest = maxOf(size.width, size.height)
                        if (largest > MAX_WALLPAPER_DIMENSION) {
                            val scale = MAX_WALLPAPER_DIMENSION.toFloat() / largest.toFloat()
                            decoder.setTargetSize(
                                (size.width * scale).toInt().coerceAtLeast(1),
                                (size.height * scale).toInt().coerceAtLeast(1),
                            )
                        }
                    }
                }.onFailure {
                    Log.w(WALLPAPER_MANAGER_TAG, "ImageDecoder failed for uri=$uri", it)
                }.getOrNull()?.let { return@withContext it }
            }

            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        if (width <= 0 || height <= 0) return sampleSize
        while ((width / sampleSize) > MAX_WALLPAPER_DIMENSION || (height / sampleSize) > MAX_WALLPAPER_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
