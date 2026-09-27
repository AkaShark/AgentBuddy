package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** What the primary composer control does right now. */
internal enum class ComposerSendAction {
    /** Starts a turn. */
    SEND,

    /** A turn is running: the shared store queues the message until it ends. */
    QUEUE,
}

/**
 * Composer control state (iOS `ConversationComposerEntryRowView`). Every
 * input except [isStopping] / [isCreating] comes from the Rust snapshot;
 * those two are UI-only markers held by the composer.
 */
internal data class ComposerControlsState(
    val hasContent: Boolean,
    val isConnected: Boolean,
    val isTurnActive: Boolean,
    val isStopping: Boolean = false,
    val isCreating: Boolean = false,
    val isVoiceBusy: Boolean = false,
) {
    /** Send (or queue) is possible: content, a live host, no send in flight. */
    val canSend: Boolean
        get() = hasContent && isConnected && !isVoiceBusy && !isCreating

    val sendAction: ComposerSendAction
        get() = if (isTurnActive) ComposerSendAction.QUEUE else ComposerSendAction.SEND

    /** A running turn always shows an explicit stop control. */
    val showsStop: Boolean
        get() = isTurnActive

    val canStop: Boolean
        get() = isTurnActive && !isStopping && isConnected

    /** With nothing typed the stop pill carries the 「停止」 label; with input it shrinks to an icon. */
    val stopShowsLabel: Boolean
        get() = !hasContent

    /** Idle shows send (disabled when empty); running shows 「排队」 only once there is input. */
    val showsSend: Boolean
        get() = hasContent || !isTurnActive

    val showsAttach: Boolean
        get() = !isVoiceBusy
}

/**
 * The turn a stop request is still waiting on, or null when the request is
 * over: the turn ended, a different turn started (a queued message), or the
 * host disconnected.
 */
internal fun composerStoppingTurn(
    requestedTurnId: String?,
    activeTurnId: String?,
    isTurnActive: Boolean,
    isConnected: Boolean,
): String? = requestedTurnId?.takeIf { isConnected && isTurnActive && it == activeTurnId }

/** Outcome of the single send path (button, expanded editor, keyboard). */
internal enum class ComposerSendGate {
    /** Local slash command: runs even while disconnected, as before. */
    SLASH_COMMAND,
    SEND,
    NOTHING_TO_SEND,

    /** Keep the draft, attachments and any pending question untouched. */
    BLOCKED_DISCONNECTED,

    /** A send is already in flight or dictation is busy. */
    BLOCKED_BUSY,
}

internal fun composerSendGate(
    isSlashCommand: Boolean,
    hasContent: Boolean,
    isConnected: Boolean,
    isCreating: Boolean,
    isVoiceBusy: Boolean,
): ComposerSendGate =
    when {
        isSlashCommand -> ComposerSendGate.SLASH_COMMAND
        !hasContent -> ComposerSendGate.NOTHING_TO_SEND
        !isConnected -> ComposerSendGate.BLOCKED_DISCONNECTED
        isCreating || isVoiceBusy -> ComposerSendGate.BLOCKED_BUSY
        else -> ComposerSendGate.SEND
    }

/**
 * After a failed send the draft comes back only into an empty composer, so
 * text typed while the send was in flight is never overwritten.
 */
internal fun shouldRestoreFailedDraft(currentText: String, hasCurrentAttachments: Boolean): Boolean =
    currentText.isEmpty() && !hasCurrentAttachments

internal fun insertComposerTranscript(current: TextFieldValue, transcript: String): TextFieldValue {
    val insertion = transcript.trim()
    if (insertion.isEmpty()) return current

    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    var replacement = insertion
    if (start > 0 && !text[start - 1].isWhitespace()) replacement = " $replacement"
    if (end < text.length && !text[end].isWhitespace()) replacement += " "
    val updated = text.replaceRange(start, end, replacement)
    return TextFieldValue(text = updated, selection = TextRange(start + replacement.length))
}
