package com.akashark.agentbuddy.android.ui.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerBarSlashCommandTest {
    @Test
    fun parseSlashCommandInvocationHandlesRenameArguments() {
        val invocation = parseSlashCommandInvocation("/rename Ship It")

        assertNotNull(invocation)
        assertEquals("rename", invocation?.command?.name)
        assertEquals("Ship It", invocation?.args)
    }

    @Test
    fun parseSlashCommandInvocationRecognizesAndroidParityCommands() {
        val commands = listOf("/skills", "/permissions", "/experimental", "/goal")

        val parsed = commands.mapNotNull(::parseSlashCommandInvocation)

        assertEquals(listOf("skills", "permissions", "experimental", "goal"), parsed.map { it.command.name })
    }

    @Test
    fun parseSlashCommandInvocationUsesEnglishIosCommandNames() {
        // Wire/command names must stay English and match iOS ComposerSlashCommand.
        val commands = listOf(
            "/plan", "/model", "/permissions", "/experimental", "/skills", "/review",
            "/goal", "/rename", "/new", "/fork", "/resume",
        )

        val parsed = commands.mapNotNull(::parseSlashCommandInvocation)

        assertEquals(commands.map { it.removePrefix("/") }, parsed.map { it.command.name })
    }

    @Test
    fun parseSlashCommandInvocationRejectsTranslatedCommandNames() {
        assertNull(parseSlashCommandInvocation("/计划"))
        assertNull(parseSlashCommandInvocation("/分叉"))
    }

    @Test
    fun parseSlashCommandInvocationRejectsUnknownCommands() {
        assertNull(parseSlashCommandInvocation("/definitely-not-real"))
    }
}
