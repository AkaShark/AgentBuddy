package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.akashark.agentbuddy.android.core.bridge.GhosttyInputCallback
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererStatus
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import uniffi.codex_mobile_client.TerminalCellMetrics
import uniffi.codex_mobile_client.TerminalCellRange
import uniffi.codex_mobile_client.TerminalConfig

/// Composable wrapper around the native Ghostty surface. Owns lifecycle
/// for the SurfaceView + paints a Compose-side selection overlay on top
/// (handles + highlight rectangles).
@Composable
internal fun GhosttyTerminalSurface(
    controller: TerminalSessionController,
    rendererStatus: GhosttyRendererStatus,
    onRendererUnavailable: () -> Unit,
    config: TerminalConfig? = null,
    onFontSizeChanged: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val viewRef = remember { GhosttySurfaceHolder() }
    val selectionState = remember { mutableStateOf<TerminalCellRange?>(null) }
    val metricsState = remember { mutableStateOf<TerminalCellMetrics?>(null) }
    val contentScaleState = remember { mutableStateOf(density.density) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GhosttyAndroidSurfaceView(
                    context = ctx,
                    rendererStatus = rendererStatus,
                    scale = density.density,
                    fontSize = with(density) { TerminalConfigPrefs.fontSize.toSp().value },
                    onRendererUnavailable = onRendererUnavailable,
                    inputCallback = GhosttyInputCallback { bytes -> controller.sendBytes(bytes) },
                    onFontSizeChanged = onFontSizeChanged,
                ).also { view ->
                    viewRef.view = view
                    view.onSelectionRangeChanged = { range ->
                        selectionState.value = range
                        metricsState.value = view.cellMetrics()
                    }
                    view.onMetricsChanged = { metrics ->
                        metricsState.value = metrics
                    }
                    contentScaleState.value = density.density
                }
            },
            update = { view ->
                view.scale = density.density
                view.fontSize = with(density) { TerminalConfigPrefs.fontSize.toSp().value }
                view.inputCallback = GhosttyInputCallback { bytes -> controller.sendBytes(bytes) }
                view.onFontSizeChanged = onFontSizeChanged
                viewRef.view = view
                contentScaleState.value = density.density
            },
        )

        // Sibling Compose layer painting the selection highlight + handles
        // on top of the SurfaceView. Touches on handles are forwarded to
        // the surface view (its gesture detector hit-tests them).
        SelectionOverlay(
            range = selectionState.value,
            metrics = metricsState.value,
            contentScale = contentScaleState.value,
        )

        // Floating action menu (Copy / Paste / Select All) anchored at the
        // top of the current selection range. Hidden when no selection.
        SelectionActionMenu(
            range = selectionState.value,
            metrics = metricsState.value,
            contentScale = contentScaleState.value,
            onCopy = {
                val view = viewRef.view ?: return@SelectionActionMenu
                view.copySelectionToClipboard()
            },
            onPaste = {
                viewRef.view?.pasteFromClipboard()
            },
            onSelectAll = {
                viewRef.view?.selectAll()
            },
            onDismiss = {
                viewRef.view?.clearSelection()
            },
        )
    }

    DisposableEffect(controller, viewRef) {
        controller.setOutputByteSink { bytes ->
            viewRef.view?.writeTerminalBytes(bytes)
        }
        onDispose {
            controller.setOutputByteSink(null)
            viewRef.view?.inputCallback = null
            viewRef.view?.onSelectionRangeChanged = null
            viewRef.view?.onMetricsChanged = null
            viewRef.view?.onFontSizeChanged = null
            viewRef.view = null
        }
    }

    LaunchedEffect(config, viewRef) {
        config?.let { viewRef.view?.applyConfig(it) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewRef.view?.setOccluded(false)
                Lifecycle.Event.ON_STOP -> viewRef.view?.setOccluded(true)
                Lifecycle.Event.ON_RESUME -> viewRef.view?.setFocused(true)
                Lifecycle.Event.ON_PAUSE -> viewRef.view?.setFocused(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private class GhosttySurfaceHolder {
    var view: GhosttyAndroidSurfaceView? = null
}

@Composable
private fun SelectionOverlay(
    range: TerminalCellRange?,
    metrics: TerminalCellMetrics?,
    contentScale: Float,
) {
    if (range == null || metrics == null || metrics.cols == 0u || contentScale <= 0f) return
    val highlight = ComposeColor(0xFF1F6FEB).copy(alpha = 0.30f)
    val handle = ComposeColor(0xFF1F6FEB)
    val normalized = normalizeRange(range)
    val cellW = metrics.cellWidthPx.toFloat() / contentScale
    val cellH = metrics.cellHeightPx.toFloat() / contentScale
    val lastCol = if (metrics.cols == 0u) 0u else metrics.cols - 1u

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cellWPx = cellW * density
        val cellHPx = cellH * density
        // Highlight rectangles.
        val startRow = normalized.start.row.toInt()
        val endRow = normalized.end.row.toInt()
        for (row in startRow..endRow) {
            val firstCol = if (row.toUInt() == normalized.start.row) normalized.start.col else 0u
            val endCol = if (row.toUInt() == normalized.end.row) normalized.end.col else lastCol
            if (endCol < firstCol) continue
            val width = (endCol.toInt() - firstCol.toInt() + 1).toFloat() * cellWPx
            val topInset = (cellHPx * 0.06f).coerceAtLeast(1f)
            drawRect(
                color = highlight,
                topLeft = Offset(firstCol.toInt() * cellWPx, row * cellHPx + topInset),
                size = Size(width, (cellHPx - 2 * topInset).coerceAtLeast(1f)),
            )
        }
        // Handles at start (bottom-left) + end (bottom-right) of the range.
        val handleRadius = 8f * density
        val startCenter = Offset(
            x = normalized.start.col.toFloat() * cellWPx,
            y = (normalized.start.row.toInt() + 1) * cellHPx,
        )
        val endCenter = Offset(
            x = (normalized.end.col.toInt() + 1) * cellWPx,
            y = (normalized.end.row.toInt() + 1) * cellHPx,
        )
        drawCircle(color = handle, radius = handleRadius, center = startCenter)
        drawCircle(color = handle, radius = handleRadius, center = endCenter)
    }
}

@Composable
private fun SelectionActionMenu(
    range: TerminalCellRange?,
    metrics: TerminalCellMetrics?,
    contentScale: Float,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (range == null || metrics == null || metrics.cols == 0u || contentScale <= 0f) return
    val normalized = normalizeRange(range)
    val density = LocalDensity.current
    val cellWPt = metrics.cellWidthPx.toFloat() / contentScale
    val cellHPt = metrics.cellHeightPx.toFloat() / contentScale
    // Anchor the menu just above the first selection row.
    val xPt = normalized.start.col.toFloat() * cellWPt
    val yPt = normalized.start.row.toInt() * cellHPt

    val xOffsetDp = with(density) { xPt.toDp() }
    val yOffsetDp = with(density) { (yPt - 48f).coerceAtLeast(0f).toDp() }

    Box(
        modifier = Modifier
            .offset(x = xOffsetDp, y = yOffsetDp)
            .clip(RoundedCornerShape(10.dp))
            .background(ComposeColor(0xFF1F1F1F)),
    ) {
        androidx.compose.foundation.layout.Row {
            ActionMenuItem("复制", onCopy)
            ActionMenuItem("粘贴", onPaste)
            ActionMenuItem("全选", onSelectAll)
            ActionMenuItem("✕", onDismiss)
        }
    }
}

@Composable
private fun ActionMenuItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = AgentBuddyTheme.textPrimary,
        fontFamily = AgentBuddyTheme.monoFont,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun normalizeRange(range: TerminalCellRange): TerminalCellRange {
    val startBeforeEnd = range.start.row < range.end.row ||
        (range.start.row == range.end.row && range.start.col <= range.end.col)
    return if (startBeforeEnd) {
        range
    } else {
        TerminalCellRange(range.end, range.start, range.rectangle)
    }
}
