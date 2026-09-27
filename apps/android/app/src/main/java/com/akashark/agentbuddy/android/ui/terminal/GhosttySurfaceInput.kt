package com.akashark.agentbuddy.android.ui.terminal

import android.content.Context
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/// IME configuration for [GhosttyAndroidSurfaceView.onCreateInputConnection].
internal fun GhosttyAndroidSurfaceView.createGhosttyInputConnection(
    outAttrs: EditorInfo,
): InputConnection {
    outAttrs.inputType = (
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    )
    outAttrs.imeOptions = (
        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            EditorInfo.IME_ACTION_NONE
    )
    return GhosttyInputConnection(this)
}

/// Body of [GhosttyAndroidSurfaceView.sendKeyEventToGhostty].
internal fun GhosttyAndroidSurfaceView.forwardKeyEventToGhostty(event: KeyEvent): Boolean {
    val renderer = exposedRendererSurface() ?: return false
    val action = when (event.action) {
        KeyEvent.ACTION_DOWN -> if (event.repeatCount > 0) 2 else 1
        KeyEvent.ACTION_UP -> 0
        else -> return false
    }
    val bridgeKey = KeyEventTranslator.bridgeKey(event.keyCode)
    if (bridgeKey == 0 && event.unicodeChar == 0) {
        return false
    }
    val mods = KeyEventTranslator.packMods(event)
    val text = when {
        event.unicodeChar != 0 -> Character.toString(event.unicodeChar.toChar())
        else -> null
    }
    renderer.sendKey(action, bridgeKey, mods, text, composing = false)
    return true
}

internal fun GhosttyAndroidSurfaceView.showIme() {
    requestFocus()
    post {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowInsetsController?.show(WindowInsets.Type.ime())
        }
        showImeWithInputMethodManager()
    }
}

private fun GhosttyAndroidSurfaceView.showImeWithInputMethodManager() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        ?: return
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

/**
 * `BaseInputConnection` shim that funnels IME commits, composing-text updates,
 * and synthesized backspaces into the Ghostty renderer. Real hardware key
 * events still flow through [GhosttyAndroidSurfaceView.onKeyDown].
 */
private class GhosttyInputConnection(
    private val view: GhosttyAndroidSurfaceView,
) : BaseInputConnection(view, /* fullEditor = */ false) {

    private fun renderer() = view.exposedRendererSurface()

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val payload = text?.toString().orEmpty()
        if (payload.isNotEmpty()) {
            renderer()?.sendText(payload)
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        renderer()?.sendPreedit(text?.toString().takeIf { !it.isNullOrEmpty() })
        return true
    }

    override fun finishComposingText(): Boolean {
        renderer()?.sendPreedit(null)
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        // We don't track an editable buffer; translate to backspaces.
        val renderer = renderer() ?: return true
        repeat(beforeLength.coerceAtLeast(0)) {
            renderer.sendKey(
                action = 1,
                key = 3, // AgentBuddyBridgeKey::Backspace
                mods = 0,
                text = null,
                composing = false,
            )
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        val real = event ?: return false
        return view.sendKeyEventToGhostty(real)
    }
}

private object KeyEventTranslator {
    fun packMods(event: KeyEvent): Int {
        var bits = 0
        if (event.isShiftPressed) bits = bits or (1 shl 0)
        if (event.isCtrlPressed) bits = bits or (1 shl 1)
        if (event.isAltPressed) bits = bits or (1 shl 2)
        if (event.isMetaPressed) bits = bits or (1 shl 3)
        return bits
    }

    /**
     * Map Android [KeyEvent] codes to the `AgentBuddyBridgeKey` enum the JNI
     * bridge expects (1=Enter, 2=Tab, …). Returns 0 (Unidentified) for
     * codes we want to forward as Unicode text instead.
     */
    fun bridgeKey(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 1
        KeyEvent.KEYCODE_TAB -> 2
        KeyEvent.KEYCODE_DEL -> 3
        KeyEvent.KEYCODE_ESCAPE -> 4
        KeyEvent.KEYCODE_SPACE -> 5
        KeyEvent.KEYCODE_DPAD_UP -> 6
        KeyEvent.KEYCODE_DPAD_DOWN -> 7
        KeyEvent.KEYCODE_DPAD_LEFT -> 8
        KeyEvent.KEYCODE_DPAD_RIGHT -> 9
        KeyEvent.KEYCODE_PAGE_UP -> 10
        KeyEvent.KEYCODE_PAGE_DOWN -> 11
        KeyEvent.KEYCODE_MOVE_HOME -> 12
        KeyEvent.KEYCODE_MOVE_END -> 13
        KeyEvent.KEYCODE_FORWARD_DEL -> 14
        KeyEvent.KEYCODE_INSERT -> 15
        else -> 0
    }
}
