package com.akashark.agentbuddy.android.ui.conversation

import android.util.TypedValue
import android.text.method.LinkMovementMethod
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.akashark.agentbuddy.android.state.AppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.AgentBuddyThemeManager
import com.akashark.agentbuddy.android.ui.LocalTextScale
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SelectableConversationText(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SelectionContainer(modifier = modifier) {
        content()
    }
}

/**
 * Markdown through Markwon in a selectable TextView. [lineHeightRatio] sets
 * the line height relative to the text size (Mint BODY is 26 / 16); null
 * keeps the font's natural line height.
 */
@Composable
internal fun SelectableMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    bodySize: Float = AgentBuddyTextStyle.body,
    usePhysicalDpTextSize: Boolean = false,
    lineHeightRatio: Float? = null,
    onTextViewReady: ((TextView) -> Unit)? = null,
) {
    val context = LocalContext.current
    val textScale = LocalTextScale.current
    val resolvedTextSize = bodySize * textScale
    val textColor = AgentBuddyTheme.textBody.toArgb()
    val palette = MarkdownPalette(
        link = AgentBuddyTheme.link.toArgb(),
        secondary = AgentBuddyTheme.textSecondary.toArgb(),
        codeText = AgentBuddyTheme.textPrimary.toArgb(),
        codeBackground = AgentBuddyTheme.codeBackground.toArgb(),
        rule = AgentBuddyTheme.border.toArgb(),
    )
    val codeTypeface = remember(context) {
        runCatching {
            androidx.core.content.res.ResourcesCompat.getFont(
                context,
                com.akashark.agentbuddy.android.R.font.berkeley_mono_regular,
            )
        }.getOrNull() ?: android.graphics.Typeface.MONOSPACE
    }
    val useMono = AgentBuddyThemeManager.monoFontEnabled
    val typeface = remember(context, useMono) {
        if (useMono) {
            runCatching {
                androidx.core.content.res.ResourcesCompat.getFont(
                    context,
                    com.akashark.agentbuddy.android.R.font.berkeley_mono_regular,
                )
            }.getOrNull() ?: android.graphics.Typeface.MONOSPACE
        } else {
            android.graphics.Typeface.DEFAULT
        }
    }
    val markdownTextSizePx = remember(context, resolvedTextSize, usePhysicalDpTextSize) {
        resolvedTextSize.toTextSizePx(context, usePhysicalDpTextSize)
    }
    val markwon = rememberConversationMarkwon(
        context = context,
        codeTypeface = codeTypeface,
        markdownTextSizePx = markdownTextSizePx,
        textColor = textColor,
        palette = palette,
    )
    val markdown = remember(text) { normalizeMathMarkdown(text) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                configureSelectableMarkdownTextView(
                    textView = this,
                    textColor = textColor,
                    linkColor = palette.link,
                    textSize = resolvedTextSize,
                    typeface = typeface,
                    usePhysicalDpTextSize = usePhysicalDpTextSize,
                    lineHeightPx = lineHeightRatio?.let { markdownTextSizePx * it },
                )
                onTextViewReady?.invoke(this)
            }
        },
        update = { tv ->
            configureSelectableMarkdownTextView(
                textView = tv,
                textColor = textColor,
                linkColor = palette.link,
                textSize = resolvedTextSize,
                typeface = typeface,
                usePhysicalDpTextSize = usePhysicalDpTextSize,
                lineHeightPx = lineHeightRatio?.let { markdownTextSizePx * it },
            )
            markwon.setMarkdown(tv, markdown)
        },
        modifier = modifier,
    )
}

internal fun configureSelectableMarkdownTextView(
    textView: TextView,
    textColor: Int,
    linkColor: Int,
    textSize: Float,
    typeface: android.graphics.Typeface? = null,
    usePhysicalDpTextSize: Boolean = false,
    lineHeightPx: Float? = null,
) {
    textView.setTextColor(textColor)
    textView.typeface = typeface
    textView.includeFontPadding = false
    if (usePhysicalDpTextSize) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
    } else {
        textView.textSize = textSize
    }
    lineHeightPx?.let { TextViewCompat.setLineHeight(textView, it.roundToInt()) }
    textView.linksClickable = true
    textView.movementMethod = LinkMovementMethod.getInstance()
    textView.setLinkTextColor(linkColor)
    textView.setTextIsSelectable(true)
    textView.customSelectionActionModeCallback = RunInTerminalSelectionMenu(textView)
}

/**
 * Adds a "Run in Terminal" item to the text-selection ActionMode of the
 * markwon-rendered conversation text. Available only when the Rust store has
 * an active terminal session.
 */
private class RunInTerminalSelectionMenu(
    private val textView: TextView,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        if (hasActiveTerminalSession()) {
            menu.add(Menu.NONE, MENU_ID_RUN_IN_TERMINAL, Menu.CATEGORY_SECONDARY, "在终端运行")
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val existing = menu.findItem(MENU_ID_RUN_IN_TERMINAL)
        val hasSession = hasActiveTerminalSession()
        if (hasSession && existing == null) {
            menu.add(Menu.NONE, MENU_ID_RUN_IN_TERMINAL, Menu.CATEGORY_SECONDARY, "在终端运行")
            return true
        }
        if (!hasSession && existing != null) {
            menu.removeItem(MENU_ID_RUN_IN_TERMINAL)
            return true
        }
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != MENU_ID_RUN_IN_TERMINAL) return false
        val start = textView.selectionStart.coerceAtLeast(0)
        val end = textView.selectionEnd.coerceAtMost(textView.text.length)
        if (start >= end) {
            mode.finish()
            return true
        }
        val selected = textView.text.subSequence(start, end).toString()
        val bytes = selected.toByteArray(Charsets.UTF_8)
        CoroutineScope(Dispatchers.Main.immediate).launch {
            runCatching {
                AppModel.shared.store.writeToActiveTerminal(bytes)
            }
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {}

    companion object {
        private const val MENU_ID_RUN_IN_TERMINAL = 0x6c697474 // 'litt'

        private fun hasActiveTerminalSession(): Boolean =
            AppModel.shared.store.activeTerminalId() != null
    }
}

/** Mint colours applied to the Markwon theme (ARGB ints so they key `remember`). */
private data class MarkdownPalette(
    val link: Int,
    val secondary: Int,
    val codeText: Int,
    val codeBackground: Int,
    val rule: Int,
)

@Composable
private fun rememberConversationMarkwon(
    context: android.content.Context,
    codeTypeface: android.graphics.Typeface,
    markdownTextSizePx: Float,
    textColor: Int,
    palette: MarkdownPalette,
): Markwon = remember(context, codeTypeface, markdownTextSizePx, textColor, palette) {
    try {
        val prism4j = Prism4j(com.akashark.agentbuddy.android.ui.Prism4jGrammarLocator())
        Markwon.builder(context)
            .usePlugin(
                SyntaxHighlightPlugin.create(
                    prism4j,
                    io.noties.markwon.syntax.Prism4jThemeDarkula.create(),
                ),
            )
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                JLatexMathPlugin.create(markdownTextSizePx, markdownTextSizePx * 1.12f) { builder ->
                    builder.inlinesEnabled(true)
                    builder.blocksEnabled(true)
                    builder.theme().textColor(textColor)
                },
            )
            // Registered last so the Mint theme wins over the syntax plugin's
            // code-block colours.
            .usePlugin(MintMarkdownThemePlugin(context, codeTypeface, markdownTextSizePx, palette))
            .build()
    } catch (_: Exception) {
        Markwon.create(context)
    }
}

/**
 * Mint Markdown typography: link colour, Berkeley Mono code on the code
 * background (CODE 14 relative to BODY 16), quiet quote bar and rules, and
 * heading sizes stepped close to the body instead of Markwon's 2x H1.
 */
private class MintMarkdownThemePlugin(
    private val context: android.content.Context,
    private val codeTypeface: android.graphics.Typeface,
    private val bodyTextSizePx: Float,
    private val palette: MarkdownPalette,
) : AbstractMarkwonPlugin() {
    override fun configureTheme(builder: MarkwonTheme.Builder) {
        val density = context.resources.displayMetrics.density
        val codeTextSizePx = (bodyTextSizePx * CODE_TO_BODY_RATIO).roundToInt()
        builder
            .linkColor(palette.link)
            .codeTypeface(codeTypeface)
            .codeBlockTypeface(codeTypeface)
            .codeTextSize(codeTextSizePx)
            .codeBlockTextSize(codeTextSizePx)
            .codeTextColor(palette.codeText)
            .codeBlockTextColor(palette.codeText)
            .codeBackgroundColor(palette.codeBackground)
            .codeBlockBackgroundColor(palette.codeBackground)
            .codeBlockMargin((12 * density).roundToInt())
            .blockQuoteColor(palette.rule)
            .blockQuoteWidth((3 * density).roundToInt())
            .listItemColor(palette.secondary)
            .thematicBreakColor(palette.rule)
            .headingBreakHeight(0)
            .headingTextSizeMultipliers(floatArrayOf(1.375f, 1.25f, 1.125f, 1.0625f, 1f, 1f))
    }

    private companion object {
        const val CODE_TO_BODY_RATIO = 14f / 16f
    }
}

private fun Float.toTextSizePx(
    context: android.content.Context,
    usePhysicalDpTextSize: Boolean,
): Float {
    val unit = if (usePhysicalDpTextSize) {
        TypedValue.COMPLEX_UNIT_DIP
    } else {
        TypedValue.COMPLEX_UNIT_SP
    }
    return TypedValue.applyDimension(unit, this, context.resources.displayMetrics)
}
