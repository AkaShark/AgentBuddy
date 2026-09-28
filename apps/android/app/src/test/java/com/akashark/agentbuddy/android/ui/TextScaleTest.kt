package com.akashark.agentbuddy.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {
    @Test
    fun scaledTextNeverDropsBelowTwelve() {
        // 极小 (0.65×) would turn a 12 caption into 7.8.
        assertEquals(12f, scaledTextDp(12f, ConversationTextSize.TINY.scale), 0.001f)
        assertEquals(12f, scaledTextDp(11f, ConversationTextSize.MEDIUM.scale), 0.001f)
        assertEquals(16f, scaledTextDp(16f, ConversationTextSize.MEDIUM.scale), 0.001f)
        assertEquals(28.8f, scaledTextDp(16f, ConversationTextSize.HUGE.scale), 0.001f)
    }
}
