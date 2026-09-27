package com.akashark.agentbuddy.android.ui.conversation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.state.ComposerImageAttachment
import com.akashark.agentbuddy.android.state.ComposerFileAttachment
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
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
        val previewBitmap = remember(attachedImage?.data) {
            attachedImage?.data?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            Box {
                previewBitmap?.let { bitmap ->
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "已附加图片",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                IconButton(
                    onClick = { onRemoveImage() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除附件",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }

    if (attachedFiles.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            attachedFiles.forEach { file ->
                ComposerFileAttachmentRow(
                    attachment = file,
                    onRemove = {
                        onRemoveFile(file)
                    },
                )
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

        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AgentBuddyTheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "附加",
                    color = AgentBuddyTheme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (clipboardHasImage) {
                    AttachmentActionRow(
                        title = "粘贴图片",
                        onClick = {
                            onDismiss()
                            val clip = clipboardManager.primaryClip
                            val uri = clip?.getItemAt(0)?.uri
                            if (uri != null) {
                                onAttachedImageChange(readAttachmentFromUri(context, uri))
                            }
                        },
                    )
                }

                AttachmentActionRow(
                    title = "相册",
                    onClick = {
                        onDismiss()
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )

                AttachmentActionRow(
                    title = "选择文件",
                    onClick = {
                        onDismiss()
                        filePicker.launch(ALL_FILE_MIME_TYPES)
                    },
                )

                AttachmentActionRow(
                    title = "拍照",
                    onClick = {
                        onDismiss()
                        cameraLauncher.launch(null)
                    },
                )
            }
        }
    }
}

@Composable
private fun AttachmentActionRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.body.scaled, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ComposerFileAttachmentRow(
    attachment: ComposerFileAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AgentBuddyTheme.codeBackground.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "FILE",
            color = AgentBuddyTheme.accent,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BerkeleyMono,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.label,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.path,
                color = AgentBuddyTheme.textMuted,
                fontSize = AgentBuddyTextStyle.caption2.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除文件",
                tint = AgentBuddyTheme.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
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
