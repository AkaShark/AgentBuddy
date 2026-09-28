package com.akashark.agentbuddy.android.ui.designsystem

import com.akashark.agentbuddy.android.ui.ConversationTextSize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BUDDY_MIN_TEXT_SIZE
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyScaledTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyTypographyTest {
    @Test
    fun `no Mint style drops below 12sp at any app text size`() {
        for (textSize in ConversationTextSize.entries) {
            for (style in BuddyTextStyle.entries) {
                assertTrue(
                    "$style at ${textSize.label}",
                    buddyScaledTextSize(style.size, textSize.scale) >= BUDDY_MIN_TEXT_SIZE,
                )
            }
        }
    }

    @Test
    fun `sizes above the floor keep scaling`() {
        assertEquals(12f, buddyScaledTextSize(BuddyTextStyle.CAPTION.size, 0.8f), 0.001f)
        assertEquals(12.8f, buddyScaledTextSize(BuddyTextStyle.BODY.size, 0.8f), 0.001f)
        assertEquals(20f, buddyScaledTextSize(BuddyTextStyle.BODY.size, 1.25f), 0.001f)
    }
}
