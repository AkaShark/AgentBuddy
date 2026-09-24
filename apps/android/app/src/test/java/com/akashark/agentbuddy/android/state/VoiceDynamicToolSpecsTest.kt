package com.akashark.agentbuddy.android.state

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDynamicToolSpecsTest {

    @Test
    fun toolNamesMatchIosAndRust() {
        val specs = VoiceRuntimeController().buildDynamicToolSpecs()

        assertEquals(listOf("list_servers", "list_sessions"), specs.map { it.name })
    }

    @Test
    fun listSessionsSchemaUsesEnglishServerProperty() {
        // Rust dynamic_tools.rs reads `server_id` / `server`; iOS CrossServerTools uses `server`.
        val spec = VoiceRuntimeController().buildDynamicToolSpecs().single { it.name == "list_sessions" }
        val properties = JSONObject(spec.inputSchemaJson).getJSONObject("properties")

        assertEquals(listOf("server"), properties.keys().asSequence().toList())
        assertEquals("string", properties.getJSONObject("server").getString("type"))
    }

    @Test
    fun allSchemasParseAndUseAsciiPropertyNames() {
        for (spec in VoiceRuntimeController().buildDynamicToolSpecs()) {
            val schema = JSONObject(spec.inputSchemaJson)
            assertEquals("object", schema.getString("type"))
            for (key in schema.getJSONObject("properties").keys()) {
                assertTrue("non-ASCII schema key '$key' in ${spec.name}", key.all { it.code < 128 })
            }
        }
    }
}
