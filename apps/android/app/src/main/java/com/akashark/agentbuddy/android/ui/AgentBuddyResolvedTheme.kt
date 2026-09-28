package com.akashark.agentbuddy.android.ui

import androidx.compose.ui.graphics.Color

enum class AgentBuddyColorThemeType {
    LIGHT,
    DARK,
}

data class AgentBuddyThemeIndexEntry(
    val slug: String,
    val name: String,
    val type: AgentBuddyColorThemeType,
    val accentHex: String,
    val backgroundHex: String,
    val foregroundHex: String,
)

data class AgentBuddyThemeDefinition(
    val name: String,
    val type: AgentBuddyColorThemeType,
    val colors: Map<String, String>,
)

/**
 * App-ready colours for one theme. Mirrors iOS `ResolvedTheme`: Mint themes
 * opt in to the semantic roles with explicit `agentbuddy.*` keys, every other
 * theme gets the same roles derived from its existing palette, so it keeps
 * looking the way it did before the Mint rebuild.
 */
data class AgentBuddyResolvedTheme(
    val slug: String,
    val name: String,
    val type: AgentBuddyColorThemeType,
    val background: Color,
    val surface: Color,
    val surfaceLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textBody: Color,
    val textSystem: Color,
    val accent: Color,
    val accentStrong: Color,
    val border: Color,
    val separator: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val textOnAccent: Color,
    val codeBackground: Color,
    // Semantic roles from the Mint design system (spec/UI-GUIDELINES.md §2).
    val brand: Color,
    val onBrand: Color,
    val borderControl: Color,
    val focus: Color,
    val successSurface: Color,
    val warningSurface: Color,
    val dangerSurface: Color,
    val disabled: Color,
    val onDisabled: Color,
) {
    companion object {
        val defaultLight =
            resolve(
                slug = "codex-light",
                definition =
                    AgentBuddyThemeDefinition(
                        name = "Codex Light",
                        type = AgentBuddyColorThemeType.LIGHT,
                        colors =
                            mapOf(
                                "editor.background" to "#FFFFFF",
                                "editor.foreground" to "#0D0D0D",
                                "sideBar.background" to "#FCFCFC",
                                "sideBar.foreground" to "#212121",
                                "activityBar.background" to "#FCFCFC",
                                "textLink.foreground" to "#0169CC",
                                "button.background" to "#0169CC",
                            ),
                    ),
            )

        val defaultDark =
            resolve(
                slug = "codex-dark",
                definition =
                    AgentBuddyThemeDefinition(
                        name = "Codex Dark",
                        type = AgentBuddyColorThemeType.DARK,
                        colors =
                            mapOf(
                                "editor.background" to "#111111",
                                "editor.foreground" to "#FCFCFC",
                                "sideBar.background" to "#131313",
                                "sideBar.foreground" to "#8F8F8F",
                                "activityBar.background" to "#131313",
                                "textLink.foreground" to "#0169CC",
                                "button.background" to "#0169CC",
                            ),
                    ),
            )

        fun resolve(
            slug: String,
            definition: AgentBuddyThemeDefinition,
        ): AgentBuddyResolvedTheme {
            val colors = definition.colors
            val isDark = definition.type == AgentBuddyColorThemeType.DARK
            fun key(name: String): Color? = colors[name]?.let(::colorFromHex)

            val background =
                colorFromHex(
                    colors["editor.background"],
                    fallback = if (isDark) Color(0xFF111111) else Color.White,
                )
            val foreground =
                colorFromHex(
                    colors["editor.foreground"],
                    fallback = if (isDark) Color(0xFFFCFCFC) else Color(0xFF0D0D0D),
                )
            val surface =
                key("sideBar.background")
                    ?: adjustBrightness(background, if (isDark) 0.03f else -0.02f)
            val surfaceLight =
                key("activityBar.background")
                    ?: adjustBrightness(surface, if (isDark) 0.04f else -0.03f)
            val accent =
                key("textLink.foreground")
                    ?: key("button.background")
                    ?: if (isDark) Color(0xFFB0B0B0) else Color(0xFF4A4A4A)
            val accentStrong = key("button.background") ?: key("textLink.foreground") ?: accent
            val border =
                key("editorGroup.border")
                    ?: key("sideBar.border")
                    ?: adjustBrightness(surface, if (isDark) 0.05f else -0.05f)
            val separator =
                key("panel.border")
                    ?: adjustBrightness(background, if (isDark) 0.04f else -0.04f)
            val danger = key("agentbuddy.error") ?: if (isDark) Color(0xFFFF5555) else Color(0xFFD32F2F)
            val success = key("agentbuddy.success") ?: if (isDark) Color(0xFF6EA676) else Color(0xFF2E7D32)
            val warning = key("agentbuddy.warning") ?: if (isDark) Color(0xFFE2A644) else Color(0xFFE65100)
            val textMuted = key("editorLineNumber.foreground") ?: dimColor(foreground, 0.35f)

            return AgentBuddyResolvedTheme(
                slug = slug,
                name = definition.name,
                type = definition.type,
                background = background,
                surface = surface,
                surfaceLight = surfaceLight,
                textPrimary = foreground,
                textSecondary = key("sideBar.foreground") ?: dimColor(foreground, 0.55f),
                textMuted = textMuted,
                textBody = key("agentbuddy.textBody") ?: dimColor(foreground, 0.88f),
                textSystem = key("agentbuddy.textSystem") ?: dimColor(foreground, 0.7f),
                accent = accent,
                accentStrong = accentStrong,
                border = border,
                separator = separator,
                danger = danger,
                success = success,
                warning = warning,
                textOnAccent =
                    key("agentbuddy.onAction")
                        ?: if (brightness(accentStrong) > 0.5f) Color(0xFF0D0D0D) else Color.White,
                codeBackground = key("agentbuddy.codeBackground") ?: background,
                brand = key("agentbuddy.brand") ?: blend(accentStrong, over = surface, alpha = if (isDark) 0.28f else 0.22f),
                onBrand = key("agentbuddy.onBrand") ?: foreground,
                borderControl = key("agentbuddy.borderControl") ?: dimColor(foreground, 0.5f),
                focus = key("agentbuddy.focus") ?: accentStrong,
                successSurface =
                    key("agentbuddy.successSurface")
                        ?: blend(success, over = background, alpha = if (isDark) 0.2f else 0.12f),
                warningSurface =
                    key("agentbuddy.warningSurface")
                        ?: blend(warning, over = background, alpha = if (isDark) 0.2f else 0.14f),
                dangerSurface =
                    key("agentbuddy.errorSurface")
                        ?: blend(danger, over = background, alpha = if (isDark) 0.2f else 0.1f),
                disabled = key("agentbuddy.disabled") ?: surfaceLight,
                onDisabled = key("agentbuddy.onDisabled") ?: textMuted,
            )
        }

        fun brightness(color: Color): Float = (0.299f * color.red) + (0.587f * color.green) + (0.114f * color.blue)

        fun adjustBrightness(
            color: Color,
            amount: Float,
        ): Color =
            Color(
                red = (color.red + amount).coerceIn(0f, 1f),
                green = (color.green + amount).coerceIn(0f, 1f),
                blue = (color.blue + amount).coerceIn(0f, 1f),
                alpha = color.alpha,
            )

        /** Composites [foreground] at [alpha] over an opaque [over] colour. */
        fun blend(
            foreground: Color,
            over: Color,
            alpha: Float,
        ): Color {
            val a = alpha.coerceIn(0f, 1f)
            return Color(
                red = foreground.red * a + over.red * (1f - a),
                green = foreground.green * a + over.green * (1f - a),
                blue = foreground.blue * a + over.blue * (1f - a),
                alpha = 1f,
            )
        }

        fun dimColor(
            color: Color,
            factor: Float,
        ): Color =
            if (brightness(color) > 0.5f) {
                Color(
                    red = (color.red * factor).coerceIn(0f, 1f),
                    green = (color.green * factor).coerceIn(0f, 1f),
                    blue = (color.blue * factor).coerceIn(0f, 1f),
                    alpha = color.alpha,
                )
            } else {
                val inverse = 1f - factor
                Color(
                    red = (color.red + ((1f - color.red) * inverse)).coerceIn(0f, 1f),
                    green = (color.green + ((1f - color.green) * inverse)).coerceIn(0f, 1f),
                    blue = (color.blue + ((1f - color.blue) * inverse)).coerceIn(0f, 1f),
                    alpha = color.alpha,
                )
            }
    }
}

internal fun colorFromHex(
    hex: String?,
    fallback: Color = Color.Transparent,
): Color {
    val normalized = hex?.trim()?.takeIf { it.isNotEmpty() }?.let(::sanitizeThemeHex) ?: return fallback
    parseHexRgb(normalized)?.let { return it }
    // Named colours ("red") still go through the platform parser.
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrElse { fallback }
}

/** `#RRGGBB` → opaque colour; anything else → null. */
private fun parseHexRgb(hex: String): Color? {
    if (hex.length != 7 || !hex.startsWith("#")) return null
    val rgb = hex.substring(1).toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

/**
 * Theme files use CSS-style `#RRGGBBAA`, but `Color.parseColor` reads eight
 * digits as `#AARRGGBB`. Drop the alpha like iOS `sanitizeHex` does.
 */
internal fun sanitizeThemeHex(hex: String): String =
    if (hex.length == 9 && hex.startsWith("#")) hex.substring(0, 7) else hex
