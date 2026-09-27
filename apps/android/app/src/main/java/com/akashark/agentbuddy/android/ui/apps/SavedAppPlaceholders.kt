package com.akashark.agentbuddy.android.ui.apps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled

@Composable
internal fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AgentBuddyTheme.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
internal fun BrokenPlaceholder(onDelete: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = AgentBuddyTheme.warning,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "该应用的文件已丢失",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.headline.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "删除它以清除该条目。",
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
            )
            TextButton(onClick = onDelete) {
                Text("删除应用", color = AgentBuddyTheme.danger)
            }
        }
    }
}

@Composable
internal fun FailurePlaceholder(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "无法加载该应用。",
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.headline.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = AgentBuddyTheme.textSecondary,
                fontSize = AgentBuddyTextStyle.footnote.scaled,
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AgentBuddyTheme.accent)
                Spacer(Modifier.width(6.dp))
                Text("重试", color = AgentBuddyTheme.accent)
            }
        }
    }
}

@Composable
internal fun ShimmerOverlay() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = alpha * 0.05f)),
    ) {
        // Thin progress bar along the top.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AgentBuddyTheme.accent.copy(alpha = alpha))
                .size(width = 0.dp, height = 2.dp)
                .fillMaxWidth(),
        )
    }
}
