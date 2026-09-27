package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import kotlin.math.roundToInt

/**
 * Theme block of the widget shell. The colour variables come from the App's
 * active theme (as iOS `buildShellHTML` does), `color-scheme` follows the App
 * mode instead of always being dark, and the fixed colour-ramp classes use a
 * light or dark variant so widget text stays readable on the transparent
 * WebView over a light or dark card. Read when the shell is built; a theme
 * switch applies to widgets created afterwards.
 */
internal fun widgetShellThemeCss(): String {
    val dark = AgentBuddyTheme.isDark
    val ink = AgentBuddyTheme.textPrimary
    val vars = listOf(
        "--color-background-primary" to hex(AgentBuddyTheme.background),
        "--color-background-secondary" to hex(AgentBuddyTheme.surface),
        "--color-background-tertiary" to hex(AgentBuddyTheme.surfaceSoft),
        "--color-background-info" to hex(AgentBuddyTheme.surfaceSoft),
        "--color-background-danger" to hex(AgentBuddyTheme.dangerSurface),
        "--color-background-success" to hex(AgentBuddyTheme.successSurface),
        "--color-background-warning" to hex(AgentBuddyTheme.warningSurface),
        "--color-text-primary" to hex(ink),
        "--color-text-secondary" to hex(AgentBuddyTheme.textSecondary),
        "--color-text-tertiary" to hex(AgentBuddyTheme.textMuted),
        "--color-text-info" to hex(AgentBuddyTheme.link),
        "--color-text-danger" to hex(AgentBuddyTheme.danger),
        "--color-text-success" to hex(AgentBuddyTheme.success),
        "--color-text-warning" to hex(AgentBuddyTheme.warning),
        "--color-info" to "var(--color-text-info)",
        "--color-danger" to "var(--color-text-danger)",
        "--color-success" to "var(--color-text-success)",
        "--color-warning" to "var(--color-text-warning)",
        "--color-border-tertiary" to rgba(ink, 0.08f),
        "--color-border-secondary" to rgba(ink, 0.16f),
        "--color-border-primary" to rgba(ink, 0.24f),
        "--color-border-info" to rgba(AgentBuddyTheme.link, 0.4f),
        "--color-border-danger" to rgba(AgentBuddyTheme.danger, 0.4f),
        "--color-border-success" to rgba(AgentBuddyTheme.success, 0.4f),
        "--color-border-warning" to rgba(AgentBuddyTheme.warning, 0.4f),
    )
    return buildString {
        append(":root {\n")
        vars.forEach { (name, value) -> append("    $name: $value;\n") }
        append("    color-scheme: ${if (dark) "dark" else "light"};\n")
        append("}\n")
        append(if (dark) DARK_RAMP else LIGHT_RAMP)
    }
}

private fun hex(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

private fun rgba(color: Color, alpha: Float): String {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    return "rgba($r,$g,$b,$alpha)"
}

/** Colour ramp for dark cards (unchanged from the original shell). */
private val DARK_RAMP = """
.c-blue > rect, .c-blue > circle, .c-blue > ellipse { fill: #1e3a5f; stroke: rgba(96,165,250,0.4); }
.c-blue > .t, .c-blue > .th { fill: #93c5fd; }
.c-blue > .ts { fill: #60a5fa; }
.c-teal > rect, .c-teal > circle, .c-teal > ellipse { fill: #134e4a; stroke: rgba(45,212,191,0.4); }
.c-teal > .t, .c-teal > .th { fill: #5eead4; }
.c-teal > .ts { fill: #2dd4bf; }
.c-amber > rect, .c-amber > circle, .c-amber > ellipse { fill: #451a03; stroke: rgba(251,191,36,0.4); }
.c-amber > .t, .c-amber > .th { fill: #fcd34d; }
.c-amber > .ts { fill: #fbbf24; }
.c-green > rect, .c-green > circle, .c-green > ellipse { fill: #14532d; stroke: rgba(74,222,128,0.4); }
.c-green > .t, .c-green > .th { fill: #86efac; }
.c-green > .ts { fill: #4ade80; }
.c-red > rect, .c-red > circle, .c-red > ellipse { fill: #450a0a; stroke: rgba(248,113,113,0.4); }
.c-red > .t, .c-red > .th { fill: #fca5a5; }
.c-red > .ts { fill: #f87171; }
.c-purple > rect, .c-purple > circle, .c-purple > ellipse { fill: #2e1065; stroke: rgba(168,85,247,0.4); }
.c-purple > .t, .c-purple > .th { fill: #c4b5fd; }
.c-purple > .ts { fill: #a78bfa; }
.c-coral > rect, .c-coral > circle, .c-coral > ellipse { fill: #431407; stroke: rgba(251,146,60,0.4); }
.c-coral > .t, .c-coral > .th { fill: #fdba74; }
.c-coral > .ts { fill: #fb923c; }
.c-pink > rect, .c-pink > circle, .c-pink > ellipse { fill: #500724; stroke: rgba(244,114,182,0.4); }
.c-pink > .t, .c-pink > .th { fill: #f9a8d4; }
.c-pink > .ts { fill: #f472b6; }
""".trimIndent()

/** The same ramp for light cards: pale fills with deep text. */
private val LIGHT_RAMP = """
.c-blue > rect, .c-blue > circle, .c-blue > ellipse { fill: #dbeafe; stroke: rgba(59,130,246,0.4); }
.c-blue > .t, .c-blue > .th { fill: #1e3a8a; }
.c-blue > .ts { fill: #1d4ed8; }
.c-teal > rect, .c-teal > circle, .c-teal > ellipse { fill: #ccfbf1; stroke: rgba(20,184,166,0.4); }
.c-teal > .t, .c-teal > .th { fill: #134e4a; }
.c-teal > .ts { fill: #0f766e; }
.c-amber > rect, .c-amber > circle, .c-amber > ellipse { fill: #fef3c7; stroke: rgba(245,158,11,0.4); }
.c-amber > .t, .c-amber > .th { fill: #78350f; }
.c-amber > .ts { fill: #b45309; }
.c-green > rect, .c-green > circle, .c-green > ellipse { fill: #dcfce7; stroke: rgba(34,197,94,0.4); }
.c-green > .t, .c-green > .th { fill: #14532d; }
.c-green > .ts { fill: #15803d; }
.c-red > rect, .c-red > circle, .c-red > ellipse { fill: #fee2e2; stroke: rgba(239,68,68,0.4); }
.c-red > .t, .c-red > .th { fill: #7f1d1d; }
.c-red > .ts { fill: #b91c1c; }
.c-purple > rect, .c-purple > circle, .c-purple > ellipse { fill: #ede9fe; stroke: rgba(139,92,246,0.4); }
.c-purple > .t, .c-purple > .th { fill: #4c1d95; }
.c-purple > .ts { fill: #6d28d9; }
.c-coral > rect, .c-coral > circle, .c-coral > ellipse { fill: #ffedd5; stroke: rgba(249,115,22,0.4); }
.c-coral > .t, .c-coral > .th { fill: #7c2d12; }
.c-coral > .ts { fill: #c2410c; }
.c-pink > rect, .c-pink > circle, .c-pink > ellipse { fill: #fce7f3; stroke: rgba(236,72,153,0.4); }
.c-pink > .t, .c-pink > .th { fill: #831843; }
.c-pink > .ts { fill: #be185d; }
""".trimIndent()
