package com.akashark.agentbuddy.android.ui.conversation

import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyIconTile
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyTileContent
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import org.json.JSONArray
import org.json.JSONObject
import uniffi.codex_mobile_client.AppOperationStatus

// ── MCP Tool Call ────────────────────────────────────────────────────────────

@Composable
internal fun McpToolCallRow(
    data: uniffi.codex_mobile_client.HydratedMcpToolCallData,
) {
    val summary = if (data.server.isBlank()) data.tool else "${data.server}.${data.tool}"
    ToolCardShell(
        summary = summary,
        status = data.status,
        durationMs = data.durationMs,
        fallbackIcon = Icons.Outlined.Extension,
    ) {
        data.argumentsJson?.takeIf { it.isNotBlank() }?.let { CodeSection("参数", it) }
        data.contentSummary?.takeIf { it.isNotBlank() }?.let { InlineTextSection("结果", it) }
        data.structuredContentJson?.takeIf { it.isNotBlank() }?.let { CodeSection("结构化内容", it) }
        data.rawOutputJson?.takeIf { it.isNotBlank() }?.let { CodeSection("原始输出", it) }
        if (data.progressMessages.isNotEmpty()) {
            ProgressSection("进度", data.progressMessages)
        }
        data.errorMessage?.takeIf { it.isNotBlank() }?.let { InlineTextSection("错误", it, tone = AgentBuddyTheme.danger) }
    }
}

// ── Computer Use Tool Call (computer-use MCP) ───────────────────────────────

@Composable
internal fun ComputerUseToolCallRow(
    data: uniffi.codex_mobile_client.HydratedMcpToolCallData,
    view: uniffi.codex_mobile_client.ComputerUseView,
) {
    ToolCardShell(
        summary = view.summary,
        status = data.status,
        durationMs = data.durationMs,
        fallbackIcon = Icons.Outlined.Mouse,
    ) {
        view.screenshotPng?.let { bytes ->
            ScreenshotPreview(bytes)
        }
        data.errorMessage?.takeIf { it.isNotBlank() }?.let {
            InlineTextSection("错误", it, tone = AgentBuddyTheme.danger)
        }
        view.accessibilityText?.takeIf { it.isNotBlank() }?.let {
            AccessibilityTreeSection(it)
        }
    }
}

@Composable
private fun ScreenshotPreview(bytes: ByteArray) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        SectionLabel("截图")
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(bytes)
                .crossfade(false)
                .build(),
            contentDescription = "Computer Use 截图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(TimelineImageShape)
                .background(timelineCodeFill(nested = true)),
        )
    }
}

@Composable
private fun AccessibilityTreeSection(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val lines = remember(text) { text.split('\n') }
    val previewLineCount = 6
    val display = if (expanded || lines.size <= previewLineCount) {
        text
    } else {
        lines.take(previewLineCount).joinToString("\n") + "\n…（还有 ${lines.size - previewLineCount} 行）"
    }

    Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("辅助功能树") }
            if (lines.size > previewLineCount) {
                TimelineLinkButton(
                    text = if (expanded) "收起" else "展开",
                    onClick = { expanded = !expanded },
                )
            }
        }
        Text(
            text = display,
            style = buddyTextStyle(BuddyTextStyle.CAPTION).copy(fontFamily = BerkeleyMono),
            color = AgentBuddyTheme.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(timelineCodeShape(nested = true))
                .background(timelineCodeFill(nested = true))
                .padding(BuddySpacing.sm),
        )
    }
}

// ── Dynamic Tool Call ────────────────────────────────────────────────────────

@Composable
internal fun DynamicToolCallRow(
    data: uniffi.codex_mobile_client.HydratedDynamicToolCallData,
) {
    val richPayload = remember(data.tool, data.contentSummary) {
        decodeRichDynamicToolPayload(data.tool, data.contentSummary)
    }
    if (richPayload != null) {
        RichDynamicToolResult(payload = richPayload)
        return
    }

    val display = data.display
    val summary = display?.summary?.takeIf { it.isNotBlank() }
        ?: data.namespace?.takeIf { it.isNotBlank() }?.let { "$it.${data.tool}" }
        ?: data.tool
    val metadata = buildList {
        display?.metadata?.forEach { entry ->
            add(entry.key to entry.value)
        }
        data.namespace?.takeIf { it.isNotBlank() }?.let { add("命名空间" to it) }
        data.success?.let { add("成功" to it.toString()) }
    }
    ToolCardShell(
        summary = summary,
        status = data.status,
        durationMs = data.durationMs,
        fallbackIcon = Icons.Outlined.Extension,
    ) {
        if (metadata.isNotEmpty()) {
            KeyValueSection(label = "元数据", entries = metadata)
        }
        data.argumentsJson?.takeIf { it.isNotBlank() }?.let { CodeSection("参数", it) }
        data.contentSummary?.takeIf { it.isNotBlank() }?.let { InlineTextSection("结果", it) }
    }
}

// ── Web Search ───────────────────────────────────────────────────────────────

@Composable
internal fun WebSearchRow(
    data: uniffi.codex_mobile_client.HydratedWebSearchData,
) {
    ToolCardShell(
        summary = if (data.query.isBlank()) "网页搜索" else "网页搜索：${data.query}",
        status = if (data.isInProgress) AppOperationStatus.IN_PROGRESS else AppOperationStatus.COMPLETED,
        fallbackIcon = Icons.Outlined.TravelExplore,
    ) {
        if (data.query.isNotBlank()) {
            InlineTextSection("查询", data.query)
        }
        data.actionJson?.takeIf { it.isNotBlank() }?.let { CodeSection("操作", it) }
    }
}

@Composable
private fun RichDynamicToolResult(
    payload: RichDynamicToolPayload,
) {
    when (payload) {
        is RichDynamicToolPayload.Servers -> {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                payload.items.forEach { item ->
                    SessionServerCard(
                        icon = if (item.isLocal) Icons.Outlined.PhoneAndroid else Icons.Outlined.Dns,
                        title = item.name,
                        subtitle = item.hostname,
                        trailing = if (item.isConnected) "已连接" else "离线",
                        statusDotColor = if (item.isConnected) AgentBuddyTheme.success else AgentBuddyTheme.textMuted,
                    )
                }
            }
        }
        is RichDynamicToolPayload.Sessions -> {
            Column(verticalArrangement = Arrangement.spacedBy(BuddySpacing.xs)) {
                payload.items.forEach { item ->
                    val subtitle = listOfNotNull(
                        item.serverName?.takeIf { it.isNotBlank() },
                        item.model?.takeIf { it.isNotBlank() },
                    ).joinToString(" \u00b7 ")
                    SessionServerCard(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        title = item.title.ifBlank { "未命名会话" },
                        subtitle = subtitle,
                        trailing = null,
                        statusDotColor = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionServerCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String?,
    statusDotColor: Color?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BuddySize.listRow)
            .timelineDetailCard()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BuddySpacing.sm),
    ) {
        BuddyIconTile(BuddyTileContent.Symbol(icon), size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = buddyTextStyle(BuddyTextStyle.LABEL),
                color = AgentBuddyTheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = buddyTextStyle(BuddyTextStyle.CAPTION),
                    color = AgentBuddyTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (statusDotColor != null || trailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                statusDotColor?.let { dotColor ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
                trailing?.let {
                    Text(
                        text = it,
                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                        color = AgentBuddyTheme.textSecondary,
                    )
                }
            }
        }
    }
}

private sealed class RichDynamicToolPayload {
    data class Servers(val items: List<ServerItem>) : RichDynamicToolPayload()
    data class Sessions(val items: List<SessionItem>) : RichDynamicToolPayload()
}

private data class ServerItem(
    val name: String,
    val hostname: String,
    val isConnected: Boolean,
    val isLocal: Boolean,
)

private data class SessionItem(
    val title: String,
    val serverName: String?,
    val model: String?,
)

private fun decodeRichDynamicToolPayload(
    tool: String,
    contentSummary: String?,
): RichDynamicToolPayload? {
    if (contentSummary.isNullOrBlank()) return null
    if (tool != "list_servers" && tool != "list_sessions") return null
    return try {
        val root = JSONObject(contentSummary)
        when (root.optString("type")) {
            "servers" -> {
                val items = root.optJSONArray("items") ?: JSONArray()
                RichDynamicToolPayload.Servers(
                    List(items.length()) { index ->
                        val item = items.optJSONObject(index) ?: JSONObject()
                        ServerItem(
                            name = item.optString("name"),
                            hostname = item.optString("hostname"),
                            isConnected = item.optBoolean("isConnected"),
                            isLocal = item.optBoolean("isLocal"),
                        )
                    },
                )
            }
            "sessions" -> {
                val items = root.optJSONArray("items") ?: JSONArray()
                RichDynamicToolPayload.Sessions(
                    List(items.length()) { index ->
                        val item = items.optJSONObject(index) ?: JSONObject()
                        SessionItem(
                            title = item.optString("preview"),
                            serverName = item.optString("serverName").takeIf { it.isNotBlank() },
                            model = item.optString("modelProvider").ifBlank {
                                item.optString("model_provider")
                            }.takeIf { it.isNotBlank() },
                        )
                    },
                )
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
