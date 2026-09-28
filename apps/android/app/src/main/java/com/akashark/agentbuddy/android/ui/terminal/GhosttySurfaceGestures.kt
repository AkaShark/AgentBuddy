package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import uniffi.codex_mobile_client.TerminalCellPosition
import uniffi.codex_mobile_client.TerminalCellRange
import uniffi.codex_mobile_client.TerminalRenderer

/**
 * Touch handling for [GhosttyAndroidSurfaceView]: pinch-to-zoom font size,
 * long-press word selection + drag-to-extend, single-tap (clear selection /
 * open link / show IME), two-finger scroll and mouse-tracking drags.
 */
internal class GhosttySurfaceGestures(
    context: Context,
    private val view: GhosttyAndroidSurfaceView,
) {
    private var selectionAnchor: TerminalCellPosition? = null
    private var selectionDragActive: Boolean = false
    private var activeHandle: GhosttyAndroidSurfaceView.SelectionHandle? = null
    private var pinchStartFontSize: Float = TerminalConfigPrefs.fontSize

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinchStartFontSize = TerminalConfigPrefs.fontSize
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (pinchStartFontSize * detector.scaleFactor)
                    .coerceIn(10f, 24f)
                TerminalConfigPrefs.setFontSize(context, target)
                view.onFontSizeChanged?.invoke(target)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // Notify the host one more time so it can re-run the
                // grid math against the new cell metrics.
                view.onFontSizeChanged?.invoke(TerminalConfigPrefs.fontSize)
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (selectionDragActive) return false
                val twoFinger = e2.pointerCount >= 2
                if (!twoFinger) return false
                view.exposedRendererSurface()?.mouseScroll(
                    x = -distanceX.toDouble(),
                    y = -distanceY.toDouble(),
                    precise = true,
                )
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val renderer = view.exposedTerminalRenderer() ?: return
                val pos = renderer.hitTest(e.x * view.scale, e.y * view.scale) ?: return
                val initial = renderer.wordRangeAt(pos)
                    ?: TerminalCellRange(pos, pos, false)
                renderer.selectionSet(initial)
                selectionAnchor = pos
                selectionDragActive = true
                activeHandle = null
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val renderer = view.exposedTerminalRenderer() ?: return false
                if (view.currentSelectionRange() != null) {
                    view.clearSelection()
                    return true
                }
                renderer.updateViewportLinksFromSurface()
                val link = renderer.linkAtPoint(e.x * view.scale, e.y * view.scale)
                if (link != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    return true
                }
                view.showIme()
                return true
            }
        },
    )

    /// Returns null when the view should fall back to `super.onTouchEvent`.
    fun onTouchEvent(event: MotionEvent): Boolean? {
        // Pinch wins outright while two fingers are down.
        val pinchHandled = scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) return true

        // Long-press / single-tap / two-finger scroll.
        if (gestureDetector.onTouchEvent(event)) return true

        // Active selection drag — finger movement extends the selection.
        if (selectionDragActive) {
            val renderer = view.exposedTerminalRenderer()
            if (renderer != null) {
                val px = event.x * view.scale
                val py = event.y * view.scale
                val focus = renderer.hitTest(px, py)
                if (focus != null && selectionAnchor != null) {
                    val anchor = selectionAnchor ?: return false
                    renderer.selectionSet(
                        TerminalCellRange(anchor, focus, false),
                    )
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                selectionDragActive = false
                selectionAnchor = null
            }
            return true
        }

        // Mouse-tracking applications (vim, htop) — single-touch drag
        // becomes a mouse drag inside the terminal.
        val renderer = view.exposedRendererSurface()
        if (renderer != null && renderer.mouseCaptured()) {
            val px = event.x.toDouble()
            val py = event.y.toDouble()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    renderer.mouseMove(px, py)
                    renderer.mouseButton(pressed = true, button = 1)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    renderer.mouseMove(px, py)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    renderer.mouseButton(pressed = false, button = 1)
                    return true
                }
            }
        }
        return if (pinchHandled) true else null
    }
}

/// Helper extension that snapshots the current viewport text and feeds
/// it to the Rust renderer's URL detector. Called once just before a
/// single-tap dispatches so OSC8 + plain-text URL detection is fresh.
private fun TerminalRenderer.updateViewportLinksFromSurface() {
    // The Rust renderer is fed PTY bytes via `feedOutput` so OSC8 anchors
    // accumulate as the shell emits them. The plain-text URL detector
    // needs an explicit viewport snapshot — but we don't have one
    // immediately available from the Android JNI surface here (the
    // helper exists for iOS where the bridge exposes `visibleText`).
    //
    // Skipping this on Android is fine for OSC8 hyperlinks (the parser
    // already tracked them); plain-text URL detection lights up once the
    // bridge wires a `read_text(viewport)` helper at the Kotlin layer.
}
