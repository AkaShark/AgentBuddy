package com.akashark.agentbuddy.android.ui.homeshell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.buddyPageBackground
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle

/** Widest the phone layout grows before it centres (tablets, landscape). */
val HomeShellMaxContentWidth = 640.dp

/**
 * Home shell chrome: the selected page, the composer pill on 任务 / 项目, and
 * the Material 3 bottom navigation. Back from 项目 / 主机 returns to 任务
 * before the normal back behaviour.
 */
@Composable
fun HomeShellScaffold(
    selectedTab: HomeShellTab,
    onSelectTab: (HomeShellTab) -> Unit,
    composerPill: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    page: @Composable (HomeShellTab) -> Unit,
) {
    BackHandler(enabled = selectedTab != HomeShellTab.TASKS) {
        onSelectTab(HomeShellTab.TASKS)
    }
    Column(modifier = modifier.fillMaxSize().buddyPageBackground()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = HomeShellMaxContentWidth).fillMaxSize()) {
                page(selectedTab)
            }
        }
        if (composerPill != null && selectedTab.showsComposerPill) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BuddySpacing.md)
                        .padding(top = BuddySpacing.xs, bottom = BuddySpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.widthIn(max = HomeShellMaxContentWidth)) { composerPill() }
            }
        }
        HomeShellNavigationBar(selectedTab = selectedTab, onSelectTab = onSelectTab)
    }
}

/**
 * The selected tab gets a filled icon on the brand indicator plus a heavier,
 * primary-coloured label, so selection never relies on colour alone.
 */
@Composable
fun HomeShellNavigationBar(
    selectedTab: HomeShellTab,
    onSelectTab: (HomeShellTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    BuddyChromeTypeLimit {
        NavigationBar(
            modifier = modifier,
            containerColor = AgentBuddyTheme.background,
            contentColor = AgentBuddyTheme.textPrimary,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            HomeShellTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            style =
                                buddyTextStyle(
                                    BuddyTextStyle.CAPTION,
                                    if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                        )
                    },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = AgentBuddyTheme.onBrand,
                            selectedTextColor = AgentBuddyTheme.textPrimary,
                            indicatorColor = AgentBuddyTheme.brand,
                            unselectedIconColor = AgentBuddyTheme.textSecondary,
                            unselectedTextColor = AgentBuddyTheme.textSecondary,
                        ),
                )
            }
        }
    }
}
