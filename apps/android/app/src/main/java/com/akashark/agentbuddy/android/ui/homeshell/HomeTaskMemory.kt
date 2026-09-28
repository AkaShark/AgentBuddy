package com.akashark.agentbuddy.android.ui.homeshell

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akashark.agentbuddy.android.state.SavedThreadsStore
import com.akashark.agentbuddy.android.ui.home.HomeComposerDraft
import uniffi.codex_mobile_client.PinnedThreadKey

/**
 * UI-only home memory that outlives the home screen's composition (it lives
 * at the app root): the observable copy of the pinned / hidden lists from
 * [SavedThreadsStore], the "stop requested" markers (task id → the turn the
 * stop was sent for) that turn a running card into 「正在停止…」 until that
 * turn ends, another turn starts or the host drops, the pinned threads being
 * hydrated, and the new-task draft.
 */
class HomeTaskMemory {
    var pinned by mutableStateOf<List<PinnedThreadKey>>(emptyList())
        private set
    var hidden by mutableStateOf<List<PinnedThreadKey>>(emptyList())
        private set
    var cancelling by mutableStateOf<Map<String, String>>(emptyMap())
    var hydrating by mutableStateOf<Set<String>>(emptySet())

    /** New-task draft, kept while the sheet is closed and after a failed creation. */
    val newTaskDraft = HomeComposerDraft()

    /** Re-reads the saved lists; call after any write to [SavedThreadsStore]. */
    fun reload(context: Context) {
        pinned = SavedThreadsStore.pinnedKeys(context)
        hidden = SavedThreadsStore.hiddenKeys(context)
    }
}
