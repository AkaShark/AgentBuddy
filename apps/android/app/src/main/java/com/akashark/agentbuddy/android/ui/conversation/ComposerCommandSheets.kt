package com.akashark.agentbuddy.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.LocalAppModel
import com.akashark.agentbuddy.android.ui.AgentBuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyTextStyle
import com.akashark.agentbuddy.android.ui.designsystem.tokens.buddyTextStyle
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppListExperimentalFeaturesRequest
import uniffi.codex_mobile_client.AppListSkillsRequest
import uniffi.codex_mobile_client.AppWriteConfigValueRequest
import uniffi.codex_mobile_client.ExperimentalFeature
import uniffi.codex_mobile_client.AppMergeStrategy
import uniffi.codex_mobile_client.SkillMetadata

@Composable
fun ComposerExperimentalSheet(
    serverId: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var features by remember(serverId) { mutableStateOf<List<ExperimentalFeature>>(emptyList()) }
    var isLoading by remember(serverId) { mutableStateOf(true) }
    var reloadToken by remember(serverId) { mutableIntStateOf(0) }

    LaunchedEffect(serverId, reloadToken) {
        isLoading = true
        runCatching {
            appModel.client.listExperimentalFeatures(
                serverId,
                AppListExperimentalFeaturesRequest(cursor = null, limit = 200u),
            )
        }.onSuccess { featuresResult ->
            features = featuresResult.sortedBy { (it.displayName ?: it.name).lowercase() }
        }.onFailure { error ->
            features = emptyList()
            onError(error.message ?: "加载实验性功能失败")
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.9f)
            .imePadding()
            .padding(16.dp),
    ) {
        SheetHeader(
            title = "实验性",
            leadingActionLabel = "重新加载",
            onLeadingAction = { reloadToken += 1 },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AgentBuddyTheme.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            features.isEmpty() -> {
                Text("没有可用的实验性功能", color = AgentBuddyTheme.textMuted, fontSize = AgentBuddyTextStyle.code.scaled)
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(features, key = { it.name }) { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(feature.displayName ?: feature.name, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.body.scaled)
                                feature.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(description, color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled)
                                }
                            }
                            Switch(
                                checked = feature.enabled,
                                onCheckedChange = { enabled ->
                                    val previous = feature.enabled
                                    features = features.map {
                                        if (it.name == feature.name) {
                                            ExperimentalFeature(
                                                name = it.name,
                                                stage = it.stage,
                                                displayName = it.displayName,
                                                description = it.description,
                                                announcement = it.announcement,
                                                enabled = enabled,
                                                defaultEnabled = it.defaultEnabled,
                                            )
                                        } else {
                                            it
                                        }
                                    }
                                    scope.launch {
                                        runCatching {
                                            appModel.client.writeConfigValue(
                                                serverId,
                                                AppWriteConfigValueRequest(
                                                    keyPath = "features.${feature.name}",
                                                    valueJson = if (enabled) "true" else "false",
                                                    mergeStrategy = AppMergeStrategy.UPSERT,
                                                    filePath = null,
                                                    expectedVersion = null,
                                                ),
                                            )
                                        }.onFailure { error ->
                                            features = features.map {
                                                if (it.name == feature.name) {
                                                    ExperimentalFeature(
                                                        name = it.name,
                                                        stage = it.stage,
                                                        displayName = it.displayName,
                                                        description = it.description,
                                                        announcement = it.announcement,
                                                        enabled = previous,
                                                        defaultEnabled = it.defaultEnabled,
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                            onError(error.message ?: "更新实验性功能失败")
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = AgentBuddyTheme.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerSkillsSheet(
    serverId: String,
    cwd: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val appModel = LocalAppModel.current
    var skills by remember(serverId, cwd) { mutableStateOf<List<SkillMetadata>>(emptyList()) }
    var isLoading by remember(serverId, cwd) { mutableStateOf(true) }
    var reloadToken by remember(serverId, cwd) { mutableIntStateOf(0) }

    LaunchedEffect(serverId, cwd, reloadToken) {
        isLoading = true
        runCatching {
            appModel.client.listSkills(
                serverId,
                AppListSkillsRequest(
                    cwds = listOf(cwd),
                    forceReload = reloadToken > 0,
                ),
            )
        }.onSuccess { skillResults ->
            skills = skillResults.sortedBy { it.name.lowercase() }
        }.onFailure { error ->
            skills = emptyList()
            onError(error.message ?: "加载技能失败")
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.9f)
            .imePadding()
            .padding(16.dp),
    ) {
        SheetHeader(
            title = "技能",
            leadingActionLabel = "重新加载",
            onLeadingAction = { reloadToken += 1 },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AgentBuddyTheme.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            skills.isEmpty() -> {
                Text("此工作区没有可用的技能", color = AgentBuddyTheme.textMuted, fontSize = AgentBuddyTextStyle.code.scaled)
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skills, key = { "${it.path.value}#${it.name}" }) { skill ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AgentBuddyTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(skill.name, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.body.scaled, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                if (skill.enabled) {
                                    Text(
                                        "已启用",
                                        color = AgentBuddyTheme.accent,
                                        style = buddyTextStyle(BuddyTextStyle.CAPTION),
                                        modifier = Modifier
                                            .background(AgentBuddyTheme.accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(skill.description, color = AgentBuddyTheme.textSecondary, fontSize = AgentBuddyTextStyle.caption.scaled)
                            Text(skill.path.value, color = AgentBuddyTheme.textMuted, style = buddyTextStyle(BuddyTextStyle.CAPTION))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SheetHeader(
    title: String,
    leadingActionLabel: String? = null,
    onLeadingAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingActionLabel != null && onLeadingAction != null) {
            TextButton(onClick = onLeadingAction) {
                Text(leadingActionLabel, color = AgentBuddyTheme.accent)
            }
        } else {
            Spacer(Modifier.width(64.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = AgentBuddyTheme.textPrimary, fontSize = AgentBuddyTextStyle.headline.scaled, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text("完成", color = AgentBuddyTheme.accent)
        }
    }
    HorizontalDivider(color = AgentBuddyTheme.divider, modifier = Modifier.padding(top = 8.dp))
}
