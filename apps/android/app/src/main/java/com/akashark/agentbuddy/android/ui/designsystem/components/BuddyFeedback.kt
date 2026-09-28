package com.akashark.agentbuddy.android.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyShapes
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/**
 * Explains what happened and offers exactly one next step. Every state gets
 * its own specific action instead of a generic "retry".
 */
@Composable
fun BuddyEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    actionIcon: ImageVector? = null,
    actionKind: BuddyButtonKind = BuddyButtonKind.PRIMARY,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().buddyCard(BuddySurfaceTone.SOFT),
        verticalArrangement = Arrangement.spacedBy(BuddySpacing.md),
    ) {
        BuddyIconTile(BuddyTileContent.Symbol(icon), size = 48.dp, fill = AgentBuddyTheme.surface)
        Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.HEADING),
                color = AgentBuddyTheme.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = buddyTextStyle(BuddyTextStyle.BODY),
                color = AgentBuddyTheme.textSecondary,
            )
        }
        if (actionTitle != null && onAction != null) {
            BuddyButton(text = actionTitle, onClick = onAction, kind = actionKind, icon = actionIcon)
        }
    }
}

enum class BuddyBannerTone(val defaultIcon: ImageVector) {
    INFO(Icons.Outlined.Info),
    WARNING(Icons.Outlined.WarningAmber),
    DANGER(Icons.Outlined.ErrorOutline),
    SUCCESS(Icons.Outlined.CheckCircle),
    ;

    val fill: Color
        get() =
            when (this) {
                INFO -> AgentBuddyTheme.surfaceSoft
                WARNING -> AgentBuddyTheme.warningSurface
                DANGER -> AgentBuddyTheme.dangerSurface
                SUCCESS -> AgentBuddyTheme.successSurface
            }

    val foreground: Color
        get() =
            when (this) {
                INFO -> AgentBuddyTheme.textPrimary
                WARNING -> AgentBuddyTheme.warning
                DANGER -> AgentBuddyTheme.danger
                SUCCESS -> AgentBuddyTheme.success
            }
}

/**
 * Persistent inline notice for connection problems, failures and pending sync.
 * Critical states never rely on a transient toast.
 */
@Composable
fun BuddyBanner(
    tone: BuddyBannerTone,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = tone.defaultIcon,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(tone.fill, BuddyShapes.detailCard)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone.foreground, modifier = Modifier.size(BuddySize.icon))
        Text(
            text = message,
            style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.Normal),
            color = AgentBuddyTheme.textPrimary,
            modifier = Modifier.weight(1f).padding(vertical = BuddySpacing.xs),
        )
        if (actionTitle != null && onAction != null) {
            Box(
                modifier =
                    Modifier
                        .heightIn(min = BuddySize.minHitTarget)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = BuddySpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionTitle,
                    style = buddyTextStyle(BuddyTextStyle.LABEL, FontWeight.SemiBold),
                    color = AgentBuddyTheme.link,
                )
            }
        }
    }
}

/**
 * Mint bottom sheet chrome: 30dp top corners (square bottom, the platform owns
 * the safe area) on the page background, with the standard drag handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuddyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = BuddyShapes.sheet,
        containerColor = AgentBuddyTheme.background,
        contentColor = AgentBuddyTheme.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AgentBuddyTheme.borderControl) },
        content = content,
    )
}
