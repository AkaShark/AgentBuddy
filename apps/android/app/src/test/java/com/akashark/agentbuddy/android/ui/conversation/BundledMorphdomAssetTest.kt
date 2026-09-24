package com.akashark.agentbuddy.android.ui.conversation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledMorphdomAssetTest {
    private val assetDir = File("src/main/assets/widget")

    @Test
    fun morphdomIsBundledAndSafeToInline() {
        val js = File(assetDir, "morphdom-umd.min.js").readText()

        assertTrue(js.contains("global.morphdom=factory()"))
        // The widget shell inlines this file inside a <script> element.
        assertFalse(js.contains("</script", ignoreCase = true))
    }

    @Test
    fun morphdomLicenseIsBundled() {
        val license = File(assetDir, "morphdom-LICENSE.txt").readText()

        assertTrue(license.contains("morphdom 2.7.4"))
        assertTrue(license.contains("The MIT License"))
    }
}
