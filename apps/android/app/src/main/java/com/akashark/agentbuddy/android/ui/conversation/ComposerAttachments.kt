package com.akashark.agentbuddy.android.ui.conversation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyBottomSheet
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyListRow
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import java.io.ByteArrayOutputStream

private val SUPPORTED_IMAGE_FILE_MIME_TYPES = arrayOf(
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
)

private val ALL_FILE_MIME_TYPES = arrayOf("*/*")

@Composable
internal fun ComposerAttachmentPreviews(
    attachedImage: ComposerImageAttachment?,
    attachedFiles: List<ComposerFileAttachment>,
    onRemoveImage: () -> Unit,
    onRemoveFile: (ComposerFileAttachment) -> Unit,
) {
    if (attachedImage != null) {
        val previewBitmap = remember(attachedImage.data) {
            BitmapFactory.decodeByteArray(attachedImage.data, 0, attachedImage.data.size)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BuddySpacing.lg, end = BuddySpacing.md, top = BuddySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            previewBitmap?.let { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "已附加图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(BuddyShapes.control),
                )
            }
            BuddyIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "移除附件",
                onClick = onRemoveImage,
                tone = BuddyIconButtonTone.SOFT,
                diameter = 28.dp,
                iconSize = 14.dp,
            )
        }
    }

    if (attachedFiles.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BuddySpacing.md, end = BuddySpacing.md, top = BuddySpacing.xs),
            verticalArrangement = Arrangement.spacedBy(BuddySpacing.xxs),
        ) {
            attachedFiles.forEach { file ->
                ComposerFileAttachmentRow(attachment = file, onRemove = { onRemoveFile(file) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerAttachSheet(
    showAttachMenu: Boolean,
    onDismiss: () -> Unit,
    onAttachedImageChange: (ComposerImageAttachment?) -> Unit,
    photoPicker: ActivityResultLauncher<PickVisualMediaRequest>,
    filePicker: ActivityResultLauncher<Array<String>>,
    cameraLauncher: ActivityResultLauncher<Void?>,
) {
    val context = LocalContext.current
    if (showAttachMenu) {
        val clipboardManager = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
        val clipboardHasImage = remember(showAttachMenu) {
            val clip = clipboardManager.primaryClip ?: return@remember false
            if (clip.itemCount == 0) return@remember false
            val desc = clip.description
            for (i in 0 until desc.mimeTypeCount) {
                if (desc.getMimeType(i).startsWith("image/")) return@remember true
            }
            clip.getItemAt(0)?.uri?.let { uri ->
                context.contentResolver.getType(uri)?.startsWith("image/") == true
            } ?: false
        }

        BuddyBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuddySpacing.xl)
                    .padding(bottom = BuddySpacing.xl),
            ) {
                Text(
                    text = "附加",
                    style = buddyTextStyle(BuddyTextStyle.TITLE),
                    color = AgentBuddyTheme.textPrimary,
                    modifier = Modifier
                        .padding(bottom = BuddySpacing.xs)
                        .semantics { heading() },
                )
                if (clipboardHasImage) {
                    AttachmentActionRow(title = "粘贴图片", icon = Icons.Outlined.ContentPaste) {
                        onDismiss()
                        val uri = clipboardManager.primaryClip?.getItemAt(0)?.uri
                        if (uri != null) onAttachedImageChange(readAttachmentFromUri(context, uri))
                    }
                }
                AttachmentActionRow(title = "相册", icon = Icons.Outlined.PhotoLibrary) {
                    onDismiss()
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                AttachmentActionRow(title = "选择文件", icon = Icons.Outlined.Description) {
                    onDismiss()
                    filePicker.launch(ALL_FILE_MIME_TYPES)
                }
                AttachmentActionRow(title = "拍照", icon = Icons.Outlined.PhotoCamera) {
                    onDismiss()
                    cameraLauncher.launch(null)
                }
            }
        }
    }
}

@Composable
private fun AttachmentActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    BuddyListRow(
        title = title,
        onClick = onClick,
        tile = { BuddyIconTile(BuddyTileContent.Symbol(icon)) },
        accessory = {},
    )
}

@Composable
private fun ComposerFileAttachmentRow(
    attachment: ComposerFileAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
            .padding(start = BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = AgentBuddyTheme.textSecondary, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = attachment.label,
                style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.path,
                style = buddyTextStyle(BuddyTextStyle.CAPTION),
                color = AgentBuddyTheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BuddyIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "移除文件 ${attachment.label}",
            onClick = onRemove,
            iconSize = 16.dp,
            tint = AgentBuddyTheme.textSecondary,
        )
    }
}

internal sealed interface PickedComposerAttachment {
    data class Image(val attachment: ComposerImageAttachment) : PickedComposerAttachment
    data class File(val attachment: ComposerFileAttachment) : PickedComposerAttachment
}

internal fun readPickedComposerAttachment(
    context: android.content.Context,
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
    val normalizedMimeType = mimeType.lowercase()
    if (SUPPORTED_IMAGE_FILE_MIME_TYPES.any { it == normalizedMimeType }) {
        return true
    }
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in setOf("png", "jpg", "jpeg", "gif", "webp")
}

internal fun readAttachmentFromUri(context: android.content.Context, uri: Uri): ComposerImageAttachment? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mimeType = resolver.getType(uri).orEmpty()
    return prepareImageAttachment(bytes, mimeType)
}

internal fun prepareBitmapAttachment(bitmap: Bitmap): ComposerImageAttachment? {
    val output = ByteArrayOutputStream()
    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val mimeType = if (bitmap.hasAlpha()) "image/png" else "image/jpeg"
    val quality = if (bitmap.hasAlpha()) 100 else 85
    if (!bitmap.compress(format, quality, output)) return null
    return ComposerImageAttachment(output.toByteArray(), mimeType)
}

private fun prepareImageAttachment(bytes: ByteArray, mimeTypeHint: String): ComposerImageAttachment? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val inferredMime = mimeTypeHint.lowercase()
    if (inferredMime == "image/png" && bitmap.hasAlpha()) {
        return ComposerImageAttachment(bytes, "image/png")
    }
    return prepareBitmapAttachment(bitmap)
}
