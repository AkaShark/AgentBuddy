package com.akashark.agentbuddy.android.ui.discovery

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private const val PAIR_COMMAND = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"

@Composable
internal fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0f),
                        ),
                    ),
                )
                .align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCancel,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(50),
                        ),
                ) {
                    Text(
                        text = "取消",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            InstructionsCard()

            Spacer(modifier = Modifier.weight(1f))

            FramingHint()
        }
    }
}

@Composable
private fun InstructionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "与 搭子 配对",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        StepRow(number = "1", title = "在你要连接的电脑上运行：")
        CommandRow()
        StepRow(number = "2", title = "用相机对准它打印出的二维码。")
    }
}

@Composable
private fun StepRow(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(AgentBuddyTheme.accent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = androidx.compose.ui.graphics.Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = title,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CommandRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 30.dp),
    ) {
        Text(
            text = PAIR_COMMAND,
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .background(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
        TextButton(
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
            modifier = Modifier
                .size(36.dp)
                .background(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f),
                    androidx.compose.foundation.shape.CircleShape,
                ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (copied) "已复制" else "复制命令",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun FramingHint() {
    Text(
        text = "请保持稳定 —— QR 码会被自动识别。",
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
