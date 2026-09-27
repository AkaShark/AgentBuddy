package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerStateTest {
    @Test
    fun `idle composer disables send until there is content`() {
        val empty = ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = false)
        assertFalse(empty.canSend)
        assertTrue(empty.showsSend)
        assertFalse(empty.showsStop)
        assertEquals(ComposerSendAction.SEND, empty.sendAction)

        val typed = empty.copy(hasContent = true)
        assertTrue(typed.canSend)
    }

    @Test
    fun `running turn always shows stop and queues input`() {
        val running = ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true)
        assertTrue(running.showsStop)
        assertTrue(running.canStop)
        assertTrue(running.stopShowsLabel)
        assertFalse(running.showsSend)

        val withInput = running.copy(hasContent = true)
        assertTrue(withInput.showsStop)
        assertFalse(withInput.stopShowsLabel)
        assertTrue(withInput.showsSend)
        assertEquals(ComposerSendAction.QUEUE, withInput.sendAction)
        assertTrue(withInput.canSend)
    }

    @Test
    fun `stopping blocks a second stop`() {
        val stopping = ComposerControlsState(hasContent = false, isConnected = true, isTurnActive = true, isStopping = true)
        assertTrue(stopping.showsStop)
        assertFalse(stopping.canStop)
    }

    @Test
    fun `disconnected composer keeps controls but blocks sending and stopping`() {
        val offline = ComposerControlsState(hasContent = true, isConnected = false, isTurnActive = true)
        assertFalse(offline.canSend)
        assertFalse(offline.canStop)
        assertTrue(offline.showsStop)
        assertTrue(offline.showsSend)
    }

    @Test
    fun `creating and dictation block double submits`() {
        val creating = ComposerControlsState(hasContent = true, isConnected = true, isTurnActive = false, isCreating = true)
        assertFalse(creating.canSend)
        val dictating = creating.copy(isCreating = false, isVoiceBusy = true)
        assertFalse(dictating.canSend)
        assertFalse(dictating.showsAttach)
    }

    @Test
    fun `stop marker ends when the turn ends switches or the host disconnects`() {
        assertEquals("t1", composerStoppingTurn("t1", "t1", isTurnActive = true, isConnected = true))
        assertNull(composerStoppingTurn(null, "t1", isTurnActive = true, isConnected = true))
        // Turn ended.
        assertNull(composerStoppingTurn("t1", null, isTurnActive = false, isConnected = true))
        // A queued message started the next turn.
        assertNull(composerStoppingTurn("t1", "t2", isTurnActive = true, isConnected = true))
        // Host went away.
        assertNull(composerStoppingTurn("t1", "t1", isTurnActive = true, isConnected = false))
    }

    @Test
    fun `send gate lets slash commands through and blocks sends offline or busy`() {
        assertEquals(
            ComposerSendGate.SLASH_COMMAND,
            composerSendGate(isSlashCommand = true, hasContent = true, isConnected = false, isCreating = true, isVoiceBusy = false),
        )
        assertEquals(
            ComposerSendGate.BLOCKED_DISCONNECTED,
            composerSendGate(isSlashCommand = false, hasContent = true, isConnected = false, isCreating = false, isVoiceBusy = false),
        )
        assertEquals(
            ComposerSendGate.BLOCKED_BUSY,
            composerSendGate(isSlashCommand = false, hasContent = true, isConnected = true, isCreating = true, isVoiceBusy = false),
        )
        assertEquals(
            ComposerSendGate.BLOCKED_BUSY,
            composerSendGate(isSlashCommand = false, hasContent = true, isConnected = true, isCreating = false, isVoiceBusy = true),
        )
        assertEquals(
            ComposerSendGate.NOTHING_TO_SEND,
            composerSendGate(isSlashCommand = false, hasContent = false, isConnected = false, isCreating = false, isVoiceBusy = false),
        )
        assertEquals(
            ComposerSendGate.SEND,
            composerSendGate(isSlashCommand = false, hasContent = true, isConnected = true, isCreating = false, isVoiceBusy = false),
        )
    }

    @Test
    fun `failed draft only returns into an empty composer`() {
        assertTrue(shouldRestoreFailedDraft(currentText = "", hasCurrentAttachments = false))
        assertFalse(shouldRestoreFailedDraft(currentText = "typed meanwhile", hasCurrentAttachments = false))
        assertFalse(shouldRestoreFailedDraft(currentText = "", hasCurrentAttachments = true))
    }

    @Test
    fun `expand affordance appears for long or multi-line drafts`() {
        assertFalse(composerShowsExpand("short"))
        assertTrue(composerShowsExpand("line one\nline two"))
        assertTrue(composerShowsExpand("x".repeat(61)))
    }

    @Test
    fun `transcript is inserted at the cursor with spacing`() {
        val current = TextFieldValue("helloworld", selection = TextRange(5))
        val updated = insertComposerTranscript(current, " there ")
        assertEquals("hello there world", updated.text)
        assertEquals(TextRange(12), updated.selection)
        assertEquals(current, insertComposerTranscript(current, "   "))
    }
}
