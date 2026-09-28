package com.akashark.agentbuddy.android.ui.conversation

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LineBackgroundSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.viewinterop.AndroidView
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.LocalTextScale
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import kotlin.math.roundToInt

internal fun isDiffLanguage(language: String?): Boolean {
    return language
        ?.trim()
        ?.lowercase()
        ?.let { it == "diff" || it == "patch" }
        ?: false
}

@Composable
internal fun SyntaxHighlightedDiffBlock(
    diff: String,
    titleHint: String? = null,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val textScale = LocalTextScale.current
    // When the caller omits fontSize, default to the shared caption size.
    // Non-composable default parameters can't call `.scaled`, so we resolve
    // it here and skip the per-call multiplier below for the default path.
    val resolvedFontPx: Float = if (fontSize.isUnspecified) {
        AgentBuddyTextStyle.caption * textScale
    } else {
        fontSize.value * textScale
    }
    // Diff colours follow the theme: additions and deletions sit on the
    // success / danger surfaces; context and metadata lines take the fill of
    // the surrounding code surface.
    val palette = DiffSyntaxPalette(
        context = AgentBuddyTheme.textBody.toArgb(),
        metadata = AgentBuddyTheme.textSecondary.toArgb(),
        addition = AgentBuddyTheme.success.toArgb(),
        deletion = AgentBuddyTheme.danger.toArgb(),
        hunk = AgentBuddyTheme.accentStrong.toArgb(),
        contextBackground = android.graphics.Color.TRANSPARENT,
        metadataBackground = android.graphics.Color.TRANSPARENT,
        additionBackground = AgentBuddyTheme.successSurface.toArgb(),
        deletionBackground = AgentBuddyTheme.dangerSurface.toArgb(),
        hunkBackground = AgentBuddyTheme.accentStrong.copy(alpha = 0.12f).toArgb(),
    )
    val context = LocalContext.current
    val codeTypeface = remember(context) {
        runCatching {
            ResourcesCompat.getFont(context, com.akashark.agentbuddy.android.R.font.berkeley_mono_regular)
        }.getOrNull() ?: Typeface.MONOSPACE
    }
    val highlighted = remember(diff, titleHint, palette) {
        buildHighlightedDiff(diff = diff, titleHint = titleHint, palette = palette)
    }

    AndroidView(
        factory = { context ->
            HorizontalScrollView(context).apply {
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isHorizontalScrollBarEnabled = true
                // Fill the viewport so line bands span the whole code area.
                isFillViewport = true
                addView(
                    TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        typeface = codeTypeface
                        includeFontPadding = false
                        setHorizontallyScrolling(true)
                        setTextIsSelectable(true)
                    },
                )
            }
        },
        update = { scrollView ->
            val textView = scrollView.getChildAt(0) as TextView
            textView.typeface = codeTypeface
            textView.includeFontPadding = false
            textView.textSize = resolvedFontPx
            // CODE line height (22 / 14) relative to the rendered size.
            TextViewCompat.setLineHeight(
                textView,
                (textView.textSize * CodeLineHeightRatio).roundToInt(),
            )
            textView.setTextColor(AgentBuddyTheme.textBody.toArgb())
            textView.text = highlighted
        },
        modifier = modifier,
    )
}

private const val CodeLineHeightRatio = 22f / 14f

private fun buildHighlightedDiff(
    diff: String,
    titleHint: String?,
    palette: DiffSyntaxPalette,
): CharSequence {
    val builder = SpannableStringBuilder()
    val lines = diff.lines()

    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.ifEmpty { " " }
        val kind = DiffSyntaxLineKind.from(rawLine)
        val lineStart = builder.length
        builder.append(line)
        builder.setSpan(
            ForegroundColorSpan(kind.foreground(palette)),
            lineStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        val lineEnd = builder.length
        val background = kind.background(palette)
        if (background != android.graphics.Color.TRANSPARENT) {
            builder.setSpan(
                DiffLineBackgroundSpan(background),
                lineStart,
                lineEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        if (index < lines.lastIndex) {
            builder.append('\n')
        }
    }

    return builder
}

/** Paints a full-width band behind one diff line (not just behind its glyphs). */
private class DiffLineBackgroundSpan(private val color: Int) : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val previous = paint.color
        paint.color = color
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        paint.color = previous
    }
}

private data class DiffSyntaxPalette(
    val context: Int,
    val metadata: Int,
    val addition: Int,
    val deletion: Int,
    val hunk: Int,
    val contextBackground: Int,
    val metadataBackground: Int,
    val additionBackground: Int,
    val deletionBackground: Int,
    val hunkBackground: Int,
)

private enum class DiffSyntaxLineKind {
    ADDITION,
    DELETION,
    HUNK,
    METADATA,
    CONTEXT,
    ;

    fun foreground(palette: DiffSyntaxPalette): Int = when (this) {
        ADDITION -> palette.addition
        DELETION -> palette.deletion
        HUNK -> palette.hunk
        METADATA -> palette.metadata
        CONTEXT -> palette.context
    }

    fun background(palette: DiffSyntaxPalette): Int = when (this) {
        ADDITION -> palette.additionBackground
        DELETION -> palette.deletionBackground
        HUNK -> palette.hunkBackground
        METADATA -> palette.metadataBackground
        CONTEXT -> palette.contextBackground
    }

    companion object {
        fun from(text: String): DiffSyntaxLineKind = when {
            text.startsWith("@@") -> HUNK
            text.startsWith("+") && !text.startsWith("+++") -> ADDITION
            text.startsWith("-") && !text.startsWith("---") -> DELETION
            text.startsWith("diff --git ")
                || text.startsWith("index ")
                || text.startsWith("+++ ")
                || text.startsWith("--- ")
                || text.startsWith("new file mode ")
                || text.startsWith("deleted file mode ")
                || text.startsWith("rename from ")
                || text.startsWith("rename to ")
                || text.startsWith("similarity index ")
                || text.startsWith("Binary files ") -> METADATA
            else -> CONTEXT
        }
    }
}
