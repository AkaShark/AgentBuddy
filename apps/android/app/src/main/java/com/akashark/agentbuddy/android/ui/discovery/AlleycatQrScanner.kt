package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButton
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconButtonTone
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddySurfaceTone
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyCard
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyRadius
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private const val PAIR_COMMAND = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"

/**
 * Camera step of the QR pairing sheet: live preview in a rounded viewfinder,
 * instructions and the pairing command for hosts without the desktop app.
 * Reports the first QR payload once.
 */
@Composable
internal fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    QrScannerContent(onCancel = onCancel) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                bindCameraUseCases(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    barcodeScanner = barcodeScanner,
                    executor = executor,
                    onResult = { payload ->
                        if (!scanned) {
                            scanned = true
                            onScanned(payload)
                        }
                    },
                )
                previewView
            },
        )
    }
}

/** Stateless scanner layout; [viewfinder] is the camera preview (a placeholder in the gallery). */
@Composable
internal fun QrScannerContent(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: @Composable () -> Unit,
) {
    DiscoverySheetScaffold(
        modifier = modifier,
        contentSpacing = BuddySpacing.md,
        header = {
            DiscoverySheetHeader(
                title = "扫描配对二维码",
                actionTitle = "取消",
                onAction = onCancel,
                subtitle = "二维码来自电脑上的搭子。",
            )
        },
    ) {
        Viewfinder(viewfinder)
        Text(
            text = "请保持稳定 —— QR 码会被自动识别。",
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        InstructionsCard()
    }
}

@Composable
private fun Viewfinder(preview: @Composable () -> Unit) {
    val frameShape = RoundedCornerShape(BuddyRadius.card)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "相机取景框：把二维码放进框内" },
        contentAlignment = Alignment.Center,
    ) {
        val side = minOf(maxWidth, 360.dp)
        Box(
            modifier = Modifier
                .size(side)
                .clip(frameShape)
                .background(Color.Black, frameShape),
            contentAlignment = Alignment.Center,
        ) {
            preview()
            Box(
                modifier = Modifier
                    .padding(side * 0.14f)
                    .fillMaxSize()
                    .border(3.dp, AgentBuddyTheme.brand, frameShape),
            )
        }
    }
}

@Composable
private fun InstructionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .buddyCard(BuddySurfaceTone.SURFACE),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        Text(
            text = "如何配对",
            style = buddyTextStyle(BuddyTextStyle.HEADING),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        StepRow(number = "1", title = "在电脑上打开搭子，进入「配对」页。")
        StepRow(number = "2", title = "用相机对准电脑上显示的二维码。")
        BuddyDivider()
        Text(
            text = "没有装桌面端？在要连接的主机上运行：",
            style = buddyTextStyle(BuddyTextStyle.CAPTION),
            color = AgentBuddyTheme.textSecondary,
        )
        AlleycatPairCommandRow()
    }
}

@Composable
private fun StepRow(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(AgentBuddyTheme.brand, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = buddyTextStyle(BuddyTextStyle.CAPTION, FontWeight.SemiBold),
                color = AgentBuddyTheme.onBrand,
            )
        }
        Text(
            text = title,
            style = buddyTextStyle(BuddyTextStyle.BODY),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The public pairing command in code type with a copy button (「已复制」 for 1.4s). */
@Composable
internal fun AlleycatPairCommandRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
    ) {
        Text(
            text = PAIR_COMMAND,
            style = buddyTextStyle(BuddyTextStyle.CODE),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier
                .weight(1f)
                .background(AgentBuddyTheme.surfaceSoft, BuddyShapes.control)
                .padding(horizontal = BuddySpacing.sm, vertical = BuddySpacing.xs),
        )
        BuddyIconButton(
            icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (copied) "已复制" else "复制命令",
            tone = BuddyIconButtonTone.SOFT,
            diameter = 44.dp,
            tint = if (copied) AgentBuddyTheme.success else null,
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(
                    android.content.ClipData.newPlainText("搭子", PAIR_COMMAND),
                )
                copied = true
                scope.launch {
                    kotlinx.coroutines.delay(1400)
                    copied = false
                }
            },
        )
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    executor: java.util.concurrent.ExecutorService,
    onResult: (String) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setAnalyzer
            }
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes
                        .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                        ?.rawValue
                        ?.let(onResult)
                }
                .addOnFailureListener { err ->
                    Log.w(LOG_TAG, "barcode analyze failed", err)
                }
                .addOnCompleteListener { proxy.close() }
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }.onFailure {
            Log.w(LOG_TAG, "bindToLifecycle failed", it)
        }
    }, ContextCompat.getMainExecutor(context))
}
