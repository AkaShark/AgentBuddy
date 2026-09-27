package com.akashark.agentbuddy.android.ui.conversation

import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.BerkeleyMono
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
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
        accent = AgentBuddyTheme.toolCallMcpCall,
        status = data.status,
        durationMs = data.durationMs,
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
        accent = AgentBuddyTheme.toolCallMcpCall,
        status = data.status,
        durationMs = data.durationMs,
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
    Column {
        Text(
            text = "截图",
            color = AgentBuddyTheme.textSecondary,
            fontSize = 10f.scaled,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(bytes)
                .crossfade(false)
                .build(),
            contentDescription = "Computer Use 截图",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AgentBuddyTheme.codeBackground),
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

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "辅助功能树",
                color = AgentBuddyTheme.textSecondary,
                fontSize = 10f.scaled,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (lines.size > previewLineCount) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    color = AgentBuddyTheme.accent,
                    fontSize = 10f.scaled,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = display,
            color = AgentBuddyTheme.textSecondary,
            fontSize = AgentBuddyTextStyle.caption2.scaled,
            fontFamily = BerkeleyMono,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AgentBuddyTheme.codeBackground)
                .padding(10.dp),
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
        accent = AgentBuddyTheme.toolCallMcpCall,
        status = data.status,
        durationMs = data.durationMs,
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
        accent = AgentBuddyTheme.toolCallWebSearch,
        status = if (data.isInProgress) AppOperationStatus.IN_PROGRESS else AppOperationStatus.COMPLETED,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                payload.items.forEach { item ->
                    SessionServerCard(
                        icon = {
                            Icon(
                                if (item.isLocal) Icons.Default.PhoneAndroid else Icons.Default.Dns,
                                contentDescription = null,
                                tint = AgentBuddyTheme.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        title = item.name,
                        subtitle = item.hostname,
                        trailing = if (item.isConnected) "已连接" else "离线",
                        statusDotColor = if (item.isConnected) AgentBuddyTheme.success else AgentBuddyTheme.textMuted,
                    )
                }
            }
        }
        is RichDynamicToolPayload.Sessions -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                payload.items.forEach { item ->
                    val subtitle = listOfNotNull(
                        item.serverName?.takeIf { it.isNotBlank() },
                        item.model?.takeIf { it.isNotBlank() },
                    ).joinToString(" \u00b7 ")
                    SessionServerCard(
                        icon = {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = null,
                                tint = AgentBuddyTheme.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
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
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String?,
    statusDotColor: Color?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AgentBuddyTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(AgentBuddyTheme.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AgentBuddyTheme.textPrimary,
                fontSize = AgentBuddyTextStyle.subheadline.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = AgentBuddyTheme.textMuted,
                    fontSize = AgentBuddyTextStyle.caption.scaled,
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
                        color = AgentBuddyTheme.textMuted,
                        fontSize = AgentBuddyTextStyle.caption.scaled,
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
