package com.akashark.agentbuddy.android.state

import uniffi.codex_mobile_client.ThreadKey

/**
 * What the user is looking at right now. Written by MainActivity (started /
 * stopped) and the Compose nav stack (conversation on screen); read from the
 * notification posting code to keep alerts quiet while the app is visible.
 */
object VisibleThreadTracker {
    @Volatile
    var appInForeground: Boolean = false

    @Volatile
    var visibleThread: ThreadKey? = null
}
