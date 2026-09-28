package com.akashark.agentbuddy.android.ui.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererStatus
import com.akashark.agentbuddy.android.state.ActiveTerminalRegistry
import com.akashark.agentbuddy.android.state.AndroidProotBootstrap
import com.akashark.agentbuddy.android.state.TerminalSessionController
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme

@Composable
internal fun TerminalOutputPane(
    controller: TerminalSessionController,
    rendererStatus: GhosttyRendererStatus,
    nativeRendererAvailable: Boolean,
    onNativeRendererUnavailable: () -> Unit,
    prootState: AndroidProotBootstrap.BootstrapState,
    selectedBackend: TerminalBackendOption?,
    terminalGridSize: TerminalGridSize,
    onTerminalGridSizeChanged: (TerminalGridSize) -> Unit,
    density: androidx.compose.ui.unit.Density,
    terminalConfig: uniffi.codex_mobile_client.TerminalConfig?,
    modifier: Modifier = Modifier,
) {
    val outputScroll = rememberScrollState()
    LaunchedEffect(controller.output.length) {
        outputScroll.scrollTo(outputScroll.maxValue)
    }

    // Track the pane size so the pinch-driven font-size callback can
    // re-grid against the same container dimensions without going
    // through another layout pass.
    var paneSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                paneSize = size
                applyResize(
                    width = size.width,
                    height = size.height,
                    density = density,
                    terminalGridSize = terminalGridSize,
                    onTerminalGridSizeChanged = onTerminalGridSizeChanged,
                    controller = controller,
                    selectedBackend = selectedBackend,
                )
            },
    ) {
        if (nativeRendererAvailable) {
            GhosttyTerminalSurface(
                controller = controller,
                rendererStatus = rendererStatus,
                onRendererUnavailable = onNativeRendererUnavailable,
                config = terminalConfig,
                onFontSizeChanged = { _ ->
                    // Font size change shifts cell metrics; re-grid against
                    // the current container size so the PTY learns the new
                    // dimensions on the next event.
                    if (paneSize.width > 0 && paneSize.height > 0) {
                        applyResize(
                            width = paneSize.width,
                            height = paneSize.height,
                            density = density,
                            terminalGridSize = terminalGridSize,
                            onTerminalGridSizeChanged = onTerminalGridSizeChanged,
                            controller = controller,
                            selectedBackend = selectedBackend,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(outputScroll)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = controller.output.ifEmpty {
                            terminalEmptyMessage(prootState, selectedBackend)
                        },
                        color = AgentBuddyTheme.accent,
                        fontFamily = AgentBuddyTheme.monoFont,
                        fontSize = TerminalConfigPrefs.fontSize.sp,
                        lineHeight = (TerminalConfigPrefs.fontSize * 1.31f).sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/// Recompute (cols, rows) for the PTY. Prefer the live Ghostty cell
/// metrics returned by the active renderer — they're keyed to the
/// actual painted font — and fall back to a font-size-aware estimate
/// only when no renderer has been bound yet (first paint).
private fun applyResize(
    width: Int,
    height: Int,
    density: androidx.compose.ui.unit.Density,
    terminalGridSize: TerminalGridSize,
    onTerminalGridSizeChanged: (TerminalGridSize) -> Unit,
    controller: TerminalSessionController,
    selectedBackend: TerminalBackendOption?,
) {
    val metrics = ActiveTerminalRegistry.current()?.cellMetrics()
    val grid = if (metrics != null && metrics.cellWidthPx > 0 && metrics.cellHeightPx > 0) {
        TerminalGridSize.fromCellMetrics(
            widthPx = width,
            heightPx = height,
            density = density,
            cellWidthPx = metrics.cellWidthPx,
            cellHeightPx = metrics.cellHeightPx,
        )
    } else {
        TerminalGridSize.fromEstimate(
            widthPx = width,
            heightPx = height,
            density = density,
            fontSizeSp = TerminalConfigPrefs.fontSize,
        )
    }
    if (grid != terminalGridSize) {
        onTerminalGridSizeChanged(grid)
        controller.resize(
            cols = grid.cols,
            rows = grid.rows,
            notifyBackend = selectedBackend?.supportsResize == true,
        )
    }
}

internal data class TerminalGridSize(
    val cols: Int,
    val rows: Int,
) {
    companion object {
        /// Compute the grid from the live cell metrics Ghostty reports.
        /// Divides the container pixel size by the cell pixel size to
        /// land on the same grid Ghostty paints.
        fun fromCellMetrics(
            widthPx: Int,
            heightPx: Int,
            density: androidx.compose.ui.unit.Density,
            cellWidthPx: Float,
            cellHeightPx: Float,
        ): TerminalGridSize {
            val w = widthPx.coerceAtLeast(1)
            val h = heightPx.coerceAtLeast(1)
            val cellW = cellWidthPx.coerceAtLeast(1f)
            val cellH = cellHeightPx.coerceAtLeast(1f)
            val cols = (w / cellW).toInt().coerceIn(20, 240)
            val rows = (h / cellH).toInt().coerceIn(4, 120)
            return TerminalGridSize(cols = cols, rows = rows)
        }

        /// First-paint fallback when the renderer hasn't measured the
        /// font yet. Scale cell estimates with the chosen font size so a
        /// 24sp font doesn't over-report cols/rows.
        fun fromEstimate(
            widthPx: Int,
            heightPx: Int,
            density: androidx.compose.ui.unit.Density,
            fontSizeSp: Float,
        ): TerminalGridSize = with(density) {
            val contentWidth = widthPx.coerceAtLeast(0)
            val contentHeight = heightPx.coerceAtLeast(0)
            val cellWidthPx = (fontSizeSp.coerceAtLeast(8f) * 0.6f).sp.toPx()
            val cellHeightPx = (fontSizeSp.coerceAtLeast(8f) * 1.31f).sp.toPx()
            val cols = (contentWidth / cellWidthPx.coerceAtLeast(1f))
                .toInt().coerceIn(20, 240)
            val rows = (contentHeight / cellHeightPx.coerceAtLeast(1f))
                .toInt().coerceIn(4, 120)
            TerminalGridSize(cols = cols, rows = rows)
        }
    }
}
