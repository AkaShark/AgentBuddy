package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.akashark.agentbuddy.android.core.bridge.GhosttyInputCallback
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererBridge
import com.akashark.agentbuddy.android.core.bridge.GhosttyRendererStatus
import com.akashark.agentbuddy.android.core.bridge.GhosttyWakeupListener
import com.akashark.agentbuddy.android.state.ActiveTerminalRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import uniffi.codex_mobile_client.TerminalBellListener
import uniffi.codex_mobile_client.TerminalCellMetrics
import uniffi.codex_mobile_client.TerminalCellRange
import uniffi.codex_mobile_client.TerminalConfig
import uniffi.codex_mobile_client.TerminalRenderer

/// Native Ghostty `SurfaceView`: renderer bridge, output buffering, frame
/// scheduling and surface lifecycle. Touch handling lives in
/// [GhosttySurfaceGestures]; IME/key input in `GhosttySurfaceInput.kt`; the
/// Compose host is [GhosttyTerminalSurface].
internal class GhosttyAndroidSurfaceView(
    context: Context,
    private val rendererStatus: GhosttyRendererStatus,
    var scale: Float,
    var fontSize: Float,
    private val onRendererUnavailable: () -> Unit,
    inputCallback: GhosttyInputCallback?,
    var onFontSizeChanged: ((Float) -> Unit)? = null,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val pendingBytes = ArrayDeque<ByteArray>()
    private val outputLock = Any()
    private val outputBuffer = ByteArrayOutputStream()
    private var rendererSurface: GhosttyRendererBridge.GhosttyRendererSurface? = null
    private var terminalRenderer: TerminalRenderer? = null
    private var backendBridge: GhosttyRendererBackendBridge? = null
    private var bellListenerRef: TerminalBellListener? = null
    private var widthPx: Int = 1
    private var heightPx: Int = 1
    private var frameScheduled = false
    private var rendererUnavailableReported = false
    private var didSetConfigDir = false
    private var pendingConfig: TerminalConfig? = null
    @Volatile
    private var outputFlushScheduled = false

    /// Selection state — mirrors the BackendBridge's stored range so
    /// Compose can observe it directly. Pushed by `setSelectionOverlay`
    /// via the bridge listener.
    @Volatile
    var onSelectionRangeChanged: ((TerminalCellRange?) -> Unit)? = null

    /// Optional metrics-change notification fired whenever resize might
    /// have moved cell sizes (font change, rotation, resize). Used by
    /// the Compose selection overlay so its math stays in lockstep.
    @Volatile
    var onMetricsChanged: ((TerminalCellMetrics?) -> Unit)? = null

    private var lastBellAt: Long = 0L

    enum class SelectionHandle { Start, End }

    var inputCallback: GhosttyInputCallback? = inputCallback
        set(value) {
            field = value
            rendererSurface?.setInputCallback(value)
        }

    private val wakeupListener = GhosttyWakeupListener {
        // Ghostty's wakeup runs on its own thread; hop to the view thread
        // and post a single Choreographer frame instead of self-rescheduling.
        post { scheduleFrame() }
    }

    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled = false
        val renderedByTick = rendererSurface?.tick() == true
        if (!renderedByTick) {
            rendererSurface?.draw()
        }
    }

    private val outputFlushRunnable = Runnable {
        flushTerminalBytesOnViewThread()
    }

    private val gestures = GhosttySurfaceGestures(context, this)

    init {
        setBackgroundColor(Color.BLACK)
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        isLongClickable = true
        isHapticFeedbackEnabled = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection =
        createGhosttyInputConnection(outAttrs)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (sendKeyEventToGhostty(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (sendKeyEventToGhostty(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    internal fun sendKeyEventToGhostty(event: KeyEvent): Boolean = forwardKeyEventToGhostty(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestures.onTouchEvent(event) ?: super.onTouchEvent(event)

    override fun surfaceCreated(holder: SurfaceHolder) {
        createRendererSurface(holder)
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        widthPx = width.coerceAtLeast(1)
        heightPx = height.coerceAtLeast(1)
        rendererSurface?.resize(widthPx, heightPx, scale) ?: createRendererSurface(holder)
        scheduleFrame()
        onMetricsChanged?.invoke(cellMetrics())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopFrameLoop()
        removeCallbacks(outputFlushRunnable)
        synchronized(outputLock) {
            outputBuffer.reset()
            outputFlushScheduled = false
        }
        terminalRenderer?.let { ActiveTerminalRegistry.unregister(it) }
        terminalRenderer?.detach()
        terminalRenderer?.close()
        terminalRenderer = null
        backendBridge = null
        bellListenerRef = null
        rendererSurface?.close()
        rendererSurface = null
        didSetConfigDir = false
    }

    fun writeTerminalBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        var shouldSchedule = false
        synchronized(outputLock) {
            outputBuffer.write(bytes)
            if (!outputFlushScheduled) {
                outputFlushScheduled = true
                shouldSchedule = true
            }
        }
        if (shouldSchedule) {
            postDelayed(outputFlushRunnable, 8L)
        }
    }

    private fun flushTerminalBytesOnViewThread() {
        val bytes = synchronized(outputLock) {
            val data = outputBuffer.toByteArray()
            outputBuffer.reset()
            outputFlushScheduled = false
            data
        }
        if (bytes.isEmpty()) return

        val activeRenderer = rendererSurface
        if (activeRenderer != null) {
            // Tee bytes through the Rust OSC parser + bell detector before
            // writing to Ghostty, so bell events fire and OSC8 cwd updates.
            terminalRenderer?.feedOutput(bytes)
            activeRenderer.write(bytes)
            return
        }

        if (pendingBytes.size >= 128) {
            pendingBytes.removeFirst()
        }
        pendingBytes.addLast(bytes)
    }

    private fun createRendererSurface(holder: SurfaceHolder) {
        if (!rendererStatus.canCreateAndroidSurface || rendererSurface != null) {
            return
        }

        val createdRenderer = GhosttyRendererBridge.createSurface(
            surface = holder.surface,
            width = widthPx,
            height = heightPx,
            scale = scale,
            fontSize = fontSize,
        )
        if (createdRenderer == null) {
            reportRendererUnavailable()
            return
        }
        rendererSurface = createdRenderer
        createdRenderer.setInputCallback(inputCallback)
        createdRenderer.setWakeupListener(wakeupListener)

        val bridge = GhosttyRendererBackendBridge(
            surface = createdRenderer,
            onRequestRedraw = { scheduleFrame() },
            onPasteBytes = { bytes -> inputCallback?.onInput(bytes) },
        )
        bridge.onSelectionRangeChanged = { range ->
            onSelectionRangeChanged?.invoke(range)
        }
        backendBridge = bridge
        val renderer = TerminalRenderer(backend = bridge)
        terminalRenderer = renderer
        ActiveTerminalRegistry.register(renderer)
        val bellListener = object : TerminalBellListener {
            override fun onBell() {
                post { fireBellHaptic() }
            }
        }
        renderer.subscribeBell(bellListener)
        bellListenerRef = bellListener

        while (pendingBytes.isNotEmpty()) {
            val bytes = pendingBytes.removeFirst()
            renderer.feedOutput(bytes)
            createdRenderer.write(bytes)
        }
        pendingConfig?.let { config ->
            pendingConfig = null
            applyConfig(config)
        }
        // Paint the first frame; subsequent frames are scheduled on demand
        // via `wakeupListener` or `setOccluded(false)`.
        scheduleFrame()
        onMetricsChanged?.invoke(cellMetrics())
    }

    fun setOccluded(occluded: Boolean) {
        terminalRenderer?.setOccluded(occluded) ?: rendererSurface?.setOcclusion(occluded)
    }

    fun setFocused(focused: Boolean) {
        terminalRenderer?.setFocused(focused) ?: rendererSurface?.setFocus(focused)
    }

    fun applyConfig(config: TerminalConfig) {
        val renderer = terminalRenderer
        if (renderer == null) {
            // Surface not created yet — replay once the renderer is attached.
            pendingConfig = config
            return
        }
        ensureConfigDir(renderer)
        try {
            renderer.applyConfig(config)
            rendererSurface?.resize(widthPx, heightPx, scale)
            scheduleFrame()
        } catch (_: Exception) {
            // Renderer was detached between the null-check and the call.
        }
        // Cell sizes likely changed; let the Compose overlay re-sync.
        onMetricsChanged?.invoke(cellMetrics())
    }

    fun cellMetrics(): TerminalCellMetrics? = terminalRenderer?.cellMetrics()

    fun currentSelectionRange(): TerminalCellRange? = backendBridge?.currentSelectionRange()

    fun clearSelection() {
        terminalRenderer?.selectionClear()
    }

    fun selectAll() {
        terminalRenderer?.selectionAll()
    }

    fun copySelectionToClipboard() {
        val renderer = terminalRenderer ?: return
        val text = renderer.readSelection().orEmpty()
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("终端", text),
            )
        }
        renderer.selectionClear()
    }

    fun pasteFromClipboard() {
        val renderer = terminalRenderer ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
        renderer.selectionClear()
        if (text.isNotEmpty()) renderer.sendPaste(text)
    }

    private fun ensureConfigDir(renderer: TerminalRenderer) {
        if (didSetConfigDir) return
        val dir = File(context.cacheDir, "agentbuddy/terminal")
        renderer.setConfigDir(dir.absolutePath)
        didSetConfigDir = true
    }

    internal fun exposedRendererSurface(): GhosttyRendererBridge.GhosttyRendererSurface? =
        rendererSurface

    internal fun exposedTerminalRenderer(): TerminalRenderer? = terminalRenderer

    private fun scheduleFrame() {
        if (frameScheduled || rendererSurface == null) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!frameScheduled) return
        frameScheduled = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun reportRendererUnavailable() {
        if (rendererUnavailableReported) return
        rendererUnavailableReported = true
        onRendererUnavailable()
    }

    private fun fireBellHaptic() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBellAt < 250L) return
        lastBellAt = now
        performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
        // System audio bell tied to the call volume — same behaviour
        // shells expect when running on a terminal emulator.
        runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }
}
