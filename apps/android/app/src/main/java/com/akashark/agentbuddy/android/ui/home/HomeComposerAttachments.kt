package com.akashark.agentbuddy.android.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import java.io.ByteArrayOutputStream
import uniffi.codex_mobile_client.ReasoningEffort

/**
 * Draft of the new-task composer. Held by the caller so the text and
 * attachments survive closing and reopening the new-task sheet, and are put
 * back when creating the task fails.
 */
class HomeComposerDraft {
    var text by mutableStateOf(TextFieldValue(""))
    var image by mutableStateOf<ComposerImageAttachment?>(null)
    var files by mutableStateOf<List<ComposerFileAttachment>>(emptyList())

    val hasContent: Boolean get() = text.text.isNotBlank() || image != null || files.isNotEmpty()

    fun clear() {
        text = TextFieldValue("")
        image = null
        files = emptyList()
    }

    fun restore(text: String, image: ComposerImageAttachment?, files: List<ComposerFileAttachment>) {
        this.text = TextFieldValue(text = text, selection = TextRange(text.length))
        this.image = image
        this.files = files
    }
}

private val SUPPORTED_IMAGE_FILE_MIME_TYPES = arrayOf("image/png", "image/jpeg", "image/gif", "image/webp")

internal val ALL_FILE_MIME_TYPES = arrayOf("*/*")

internal sealed interface PickedComposerAttachment {
    data class Image(val attachment: ComposerImageAttachment) : PickedComposerAttachment

    data class File(val attachment: ComposerFileAttachment) : PickedComposerAttachment
}

internal fun insertHomeComposerTranscript(current: TextFieldValue, transcript: String): TextFieldValue {
    val insertion = transcript.trim()
    if (insertion.isEmpty()) return current
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    var replacement = insertion
    if (start > 0 && !text[start - 1].isWhitespace()) replacement = " $replacement"
    if (end < text.length && !text[end].isWhitespace()) replacement += " "
    val updated = text.replaceRange(start, end, replacement)
    return TextFieldValue(text = updated, selection = TextRange(start + replacement.length))
}

internal fun reasoningEffortFromServerValue(value: String): ReasoningEffort? =
    when (value.trim().lowercase()) {
        "none" -> ReasoningEffort.NONE
        "minimal" -> ReasoningEffort.MINIMAL
        "low" -> ReasoningEffort.LOW
        "medium" -> ReasoningEffort.MEDIUM
        "high" -> ReasoningEffort.HIGH
        "xhigh" -> ReasoningEffort.X_HIGH
        "max" -> ReasoningEffort.MAX
        else -> null
    }

internal fun readPickedComposerAttachment(
    context: Context,
    uri: Uri,
): PickedComposerAttachment? {
    val resolver = context.contentResolver
    val displayName = resolver.displayName(uri) ?: uri.lastPathSegment ?: "selected-file"
    val mimeType = resolver.getType(uri).orEmpty()
    if (isSupportedImageFile(displayName, mimeType)) {
        readAttachmentFromUri(context, uri)?.let { return PickedComposerAttachment.Image(it) }
    }
    return PickedComposerAttachment.File(
        ComposerFileAttachment(
            label = displayName.substringBeforeLast('.', displayName),
            path = uri.toString(),
        ),
    )
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }

private fun isSupportedImageFile(displayName: String, mimeType: String): Boolean {
    if (SUPPORTED_IMAGE_FILE_MIME_TYPES.any { it == mimeType.lowercase() }) return true
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in setOf("png", "jpg", "jpeg", "gif", "webp")
}

internal fun readAttachmentFromUri(context: Context, uri: Uri): ComposerImageAttachment? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mimeType = resolver.getType(uri).orEmpty()
    return prepareImageAttachment(bytes, mimeType)
}

private fun prepareBitmapAttachment(bitmap: Bitmap): ComposerImageAttachment? {
    val output = ByteArrayOutputStream()
    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val mimeType = if (bitmap.hasAlpha()) "image/png" else "image/jpeg"
    val quality = if (bitmap.hasAlpha()) 100 else 85
    if (!bitmap.compress(format, quality, output)) return null
    return ComposerImageAttachment(output.toByteArray(), mimeType)
}

private fun prepareImageAttachment(bytes: ByteArray, mimeTypeHint: String): ComposerImageAttachment? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    if (mimeTypeHint.lowercase() == "image/png" && bitmap.hasAlpha()) {
        return ComposerImageAttachment(bytes, "image/png")
    }
    return prepareBitmapAttachment(bitmap)
}
