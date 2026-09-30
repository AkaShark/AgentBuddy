package com.akashark.agentbuddy.android.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akashark.agentbuddy.android.R
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyMotion
import com.akashark.agentbuddy.android.ui.designsystem.tokens.LocalBuddyReduceMotion

object AgentBuddyTheme {
    private val activeTheme: AgentBuddyResolvedTheme
        get() = AgentBuddyThemeManager.activeTheme

    val themeKey: String
        get() = activeTheme.slug

    val isDark: Boolean
        get() = activeTheme.type == AgentBuddyColorThemeType.DARK

    val background: Color
        get() = activeTheme.background

    val accent: Color
        get() = activeTheme.accent

    val accentStrong: Color
        get() = activeTheme.accentStrong

    val onAccentStrong: Color
        get() = activeTheme.textOnAccent

    val textPrimary: Color
        get() = activeTheme.textPrimary

    val textSecondary: Color
        get() = activeTheme.textSecondary

    val textMuted: Color
        get() = activeTheme.textMuted

    val textBody: Color
        get() = activeTheme.textBody

    val textSystem: Color
        get() = activeTheme.textSystem

    val surface: Color
        get() = activeTheme.surface

    val surfaceLight: Color
        get() = activeTheme.surfaceLight

    val border: Color
        get() = activeTheme.border

    val divider: Color
        get() = activeTheme.separator

    val danger: Color
        get() = activeTheme.danger

    val success: Color
        get() = activeTheme.success

    val warning: Color
        get() = activeTheme.warning

    val codeBackground: Color
        get() = activeTheme.codeBackground

    // Semantic roles from the Mint design system (spec/UI-GUIDELINES.md §2).
    // Pick a role by meaning, not by hue; non-Mint themes resolve every role
    // through the fallbacks in AgentBuddyResolvedTheme.

    /** Secondary grouping surface and user message fill (`surfaceSoft`). */
    val surfaceSoft: Color
        get() = activeTheme.surfaceLight

    /** Emphasis surface for the current task and the main call to action. */
    val brand: Color
        get() = activeTheme.brand

    /** Text and icons placed on [brand]. */
    val onBrand: Color
        get() = activeTheme.onBrand

    /** Fill of the primary button. */
    val action: Color
        get() = activeTheme.accentStrong

    /** Content on [action]. */
    val onAction: Color
        get() = activeTheme.textOnAccent

    /** Text actions and links. */
    val link: Color
        get() = activeTheme.accent

    /** Outline of required controls and unselected inputs; [border] stays decorative. */
    val borderControl: Color
        get() = activeTheme.borderControl

    /** Keyboard and assistive focus ring. */
    val focus: Color
        get() = activeTheme.focus

    val successSurface: Color
        get() = activeTheme.successSurface

    val warningSurface: Color
        get() = activeTheme.warningSurface

    val dangerSurface: Color
        get() = activeTheme.dangerSurface

    /** Fill of controls that cannot be used right now. */
    val disabled: Color
        get() = activeTheme.disabled

    /** Content on [disabled]. */
    val onDisabled: Color
        get() = activeTheme.onDisabled

    /** Subtle lift on dark brand surfaces; preserve white chips on light brands. */
    val brandChipFill: Color
        get() =
            if (AgentBuddyResolvedTheme.brightness(brand) > 0.5f) {
                Color.White.copy(alpha = 0.45f)
            } else {
                onBrand.copy(alpha = 0.08f)
            }

    /** Shadow for floating layers in light mode; dark mode relies on outlines. */
    val floatingShadow: Color
        get() = if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.10f)

    /**
     * Swipe-action fill. Swipe labels are white, so these always use the light
     * palette's strong colours, which keep white text readable in dark mode too.
     */
    fun swipeFill(tone: BuddySwipeTone): Color {
        val light = AgentBuddyThemeManager.lightTheme
        return when (tone) {
            BuddySwipeTone.LINK -> light.accent
            BuddySwipeTone.DANGER -> light.danger
            BuddySwipeTone.NEUTRAL -> light.textSecondary
        }
    }

    val info = Color(0xFF7CAFD9)
    val violet = Color(0xFFC797D8)
    val amber = Color(0xFFD3A85E)
    val teal = Color(0xFF88C6C7)
    val olive = Color(0xFF9BCF8E)
    val sand = Color(0xFFE3A66F)

    val statusConnecting = warning
    val statusReady = accentStrong
    val statusError = danger
    val statusDisconnected = textMuted

    val toolCallCommand = Color(0xFFC7B072)
    val toolCallFileChange = info
    val toolCallFileDiff = Color(0xFF6FA9D8)
    val toolCallMcpCall = violet
    val toolCallMcpProgress = amber
    val toolCallWebSearch = teal
    val toolCallCollaboration = olive
    val toolCallImage = sand

    /** Code, paths and commands are always set in the bundled Berkeley Mono. */
    val monoFont: FontFamily
        get() = BerkeleyMono

    val backgroundBrush: Brush
        get() =
            Brush.linearGradient(
            colors =
                listOf(
                    background,
                    AgentBuddyResolvedTheme.adjustBrightness(
                        background,
                        if (isDark) 0.02f else -0.01f,
                    ),
                    AgentBuddyResolvedTheme.adjustBrightness(
                        background,
                        if (isDark) -0.01f else 0.01f,
                    ),
                ),
            )
}

/** Meaning of a swipe action, mapped to a fill by [AgentBuddyTheme.swipeFill]. */
enum class BuddySwipeTone {
    LINK,
    DANGER,
    NEUTRAL,
}

val BerkeleyMono =
    FontFamily(
        Font(R.font.berkeley_mono_regular, weight = FontWeight.Normal, style = FontStyle.Normal),
        Font(R.font.berkeley_mono_oblique, weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(R.font.berkeley_mono_bold, weight = FontWeight.Bold, style = FontStyle.Normal),
        Font(R.font.berkeley_mono_bold_oblique, weight = FontWeight.Bold, style = FontStyle.Italic),
    )

private val Mono = BerkeleyMono

@Suppress("DEPRECATION")
private val AgentBuddyPlatformTextStyle = PlatformTextStyle(includeFontPadding = false)

private fun agentBuddyTextStyle(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: androidx.compose.ui.unit.TextUnit,
) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    platformStyle = AgentBuddyPlatformTextStyle,
)

/**
 * Material type roles mapped onto the Mint scale (display 32 … caption 12) so
 * Material components never render text below 12. Mint screens use
 * `buddyTextStyle`, which adds line heights and the app text scale.
 */
private fun buildTypography(fontFamily: FontFamily) =
    Typography(
        displaySmall = agentBuddyTextStyle(fontFamily, FontWeight.SemiBold, 32.sp),
        headlineSmall = agentBuddyTextStyle(fontFamily, FontWeight.SemiBold, 24.sp),
        titleLarge = agentBuddyTextStyle(fontFamily, FontWeight.SemiBold, 20.sp),
        titleMedium = agentBuddyTextStyle(fontFamily, FontWeight.SemiBold, 17.sp),
        titleSmall = agentBuddyTextStyle(fontFamily, FontWeight.Medium, 14.sp),
        bodyLarge = agentBuddyTextStyle(fontFamily, FontWeight.Normal, 16.sp),
        bodyMedium = agentBuddyTextStyle(fontFamily, FontWeight.Normal, 14.sp),
        bodySmall = agentBuddyTextStyle(fontFamily, FontWeight.Normal, 12.sp),
        labelLarge = agentBuddyTextStyle(fontFamily, FontWeight.Medium, 14.sp),
        labelMedium = agentBuddyTextStyle(fontFamily, FontWeight.Medium, 12.sp),
        labelSmall = agentBuddyTextStyle(fontFamily, FontWeight.Medium, 12.sp),
    )

@Composable
fun AgentBuddyAppTheme(content: @Composable () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(appContext) {
        AgentBuddyThemeManager.initialize(appContext)
        onDispose {}
    }

    val systemIsDark = isSystemInDarkTheme()
    val appearanceMode = AgentBuddyThemeManager.appearanceMode
    val lightThemeSlug = AgentBuddyThemeManager.lightTheme.slug
    val darkThemeSlug = AgentBuddyThemeManager.darkTheme.slug
    LaunchedEffect(appearanceMode, systemIsDark, lightThemeSlug, darkThemeSlug) {
        AgentBuddyThemeManager.applySystemTheme(systemIsDark)
    }

    val activeTheme = AgentBuddyThemeManager.activeTheme
    AgentBuddySystemBarsEffect(
        useDarkTheme = activeTheme.type == AgentBuddyColorThemeType.DARK || AgentBuddySystemBars.forcesDark,
    )

    val colorScheme = remember(activeTheme) { activeTheme.toMaterialColorScheme() }

    val monoFontEnabled = AgentBuddyThemeManager.monoFontEnabled
    val typography = if (monoFontEnabled) buildTypography(Mono) else buildTypography(FontFamily.Default)

    // Re-read "Remove animations" whenever the app resumes.
    val context = LocalContext.current
    var reduceMotion by remember { mutableStateOf(BuddyMotion.isReducedMotion(context)) }
    LifecycleResumeEffect(context) {
        reduceMotion = BuddyMotion.isReducedMotion(context)
        onPauseOrDispose {}
    }

    CompositionLocalProvider(LocalBuddyReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Screens that are dark in every appearance (the terminal) ask for light
 * status / navigation bar icons while they are on screen.
 */
object AgentBuddySystemBars {
    private var darkRequests by mutableIntStateOf(0)

    val forcesDark: Boolean
        get() = darkRequests > 0

    /** Keeps the system bar icons light while the calling composable is shown. */
    @Composable
    fun ForceDarkBars() {
        DisposableEffect(Unit) {
            darkRequests += 1
            onDispose { darkRequests -= 1 }
        }
    }
}

@Composable
private fun AgentBuddySystemBarsEffect(useDarkTheme: Boolean) {
    val activity = LocalContext.current.findActivity()

    SideEffect {
        val componentActivity = activity ?: return@SideEffect
        val transparent = android.graphics.Color.TRANSPARENT
        val systemBarStyle =
            if (useDarkTheme) {
                SystemBarStyle.dark(transparent)
            } else {
                SystemBarStyle.light(transparent, transparent)
            }
        componentActivity.enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
    }
}

/**
 * Material roles mapped to Mint semantics so stock components (navigation bar,
 * switches, text fields, dialogs) follow the active theme without per-call
 * colours.
 */
private fun AgentBuddyResolvedTheme.toMaterialColorScheme(): ColorScheme {
    val base = if (type == AgentBuddyColorThemeType.DARK) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accentStrong,
        onPrimary = textOnAccent,
        primaryContainer = brand,
        onPrimaryContainer = onBrand,
        secondary = textSecondary,
        onSecondary = textPrimary,
        secondaryContainer = brand,
        onSecondaryContainer = onBrand,
        tertiary = accent,
        background = background,
        onBackground = textBody,
        surface = surface,
        onSurface = textBody,
        surfaceVariant = surfaceLight,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceLight,
        surfaceBright = surface,
        surfaceDim = background,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = danger,
        onError = textOnAccent,
        errorContainer = dangerSurface,
        onErrorContainer = danger,
        outline = borderControl,
        outlineVariant = border,
        scrim = Color.Black,
    )
}

private tailrec fun Context.findActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun AgentBuddyThemePreview() {
    AgentBuddyAppTheme {
        Surface(color = AgentBuddyTheme.background) {
            Text(
                text = "AgentBuddy Theme",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
