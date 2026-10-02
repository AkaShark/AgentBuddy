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
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

// Two open corners joined by a diagonal: the shared AgentBuddy connection mark.
// Keep these paths in sync with the iOS mark and source brand artwork.
private val LINK_PATHS = listOf(
    "M13 5 H9 C6.79 5 5 6.79 5 9 V13",
    "M11 19 H15 C17.21 19 19 17.21 19 15 V11",
    "M10 14 L14 10",
)

/** The app mark as a stroked vector; tint it like any icon. */
val BuddyLinkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BuddyLink",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (data in LINK_PATHS) {
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** App identity tile using the theme's brand surface and ink. */
@Composable
fun BuddyBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = BuddySize.brandMark,
) {
    BuddyIconTile(
        content = BuddyTileContent.BrandMark,
        modifier = modifier,
        fill = AgentBuddyTheme.brand,
        foreground = AgentBuddyTheme.onBrand,
        size = size,
    )
}

/** 「搭子」 wordmark with the app mark. */
@Composable
fun BuddyWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = BuddySize.brandMark,
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
