package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

// Lucide "cat" outline (ISC licence, see
// artifacts/design/agentbuddy-ui-v1/spec/LUCIDE-LICENSE.txt), on its 24×24 grid.
private const val CAT_HEAD =
    "M12 5c.67 0 1.35.09 2 .26 1.78-2 5.03-2.84 6.42-2.26 1.4.58-.42 7-.42 7 .57 1.07 1 2.24 1 3.44" +
        "C21 17.9 16.97 21 12 21s-9-3-9-7.56c0-1.25.5-2.4 1-3.44 0 0-1.89-6.42-.5-7 1.39-.58 4.72.23 6.5 2.23" +
        "A9.04 9.04 0 0 1 12 5Z"
private const val CAT_FACE = "M8 14v.5 M16 14v.5 M11.25 16.25h1.5L12 17l-.75-.75Z"

/** The app mark as a stroked vector; tint it like any icon. */
val BuddyCatIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BuddyCat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (data in listOf(CAT_HEAD, CAT_FACE)) {
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** App mark: cat outline on an `action` tile (deep green in light, mint in dark). */
@Composable
fun BuddyBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    BuddyIconTile(
        content = BuddyTileContent.BrandMark,
        modifier = modifier,
        fill = AgentBuddyTheme.action,
        foreground = AgentBuddyTheme.onAction,
        size = size,
    )
}

/** 「搭子」 wordmark with the app mark. */
@Composable
fun BuddyWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 32.dp,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuddyBrandMark(size = markSize)
        Text(
            text = "搭子",
            style = buddyTextStyle(BuddyTextStyle.TITLE),
            color = AgentBuddyTheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
