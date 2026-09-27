package com.akashark.agentbuddy.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akashark.agentbuddy.android.ui.AgentBuddyTheme
import com.akashark.agentbuddy.android.ui.designsystem.components.BuddyDivider
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddyChromeTypeLimit
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySize
import com.akashark.agentbuddy.android.ui.designsystem.tokens.BuddySpacing
import uniffi.codex_mobile_client.ModelInfo
import uniffi.codex_mobile_client.ReasoningEffortOption

/**
 * Render-only input of the model panel. The stateful [ModelSelectorPanel]
 * derives it from `AppLaunchState` and the thread snapshot.
 */
internal data class ModelPanelState(
    /** Models that may be picked (hidden Amp modes already removed). */
    val models: List<ModelInfo>,
    val selectedModel: String?,
    val selectedRuntime: AgentRuntimeKind?,
    val efforts: List<ReasoningEffortOption>,
    /** Wire value of the chosen effort ("low", "high", …). */
    val selectedEffort: String?,
    /** The partner fixes the effort once the task has started (Amp). */
    val effortLocked: Boolean,
    /** Plan mode on/off, or null when there is no thread to switch. */
    val planMode: Boolean?,
    /** Full access on/off, or null when the partner manages permissions itself. */
    val fullAccess: Boolean?,
    val fastMode: Boolean,
)

internal class ModelPanelActions(
    val onSelectModel: (ModelInfo) -> Unit,
    val onSelectEffort: (String) -> Unit,
    val onPlanModeChange: (Boolean) -> Unit,
    val onFullAccessChange: (Boolean) -> Unit,
    val onFastModeChange: (Boolean) -> Unit,
)

/**
 * Partner, model and permission panel. Groups, top to bottom: 「搭档」
 * (runtime filter), 「模型」 (search + list), 「推理强度」, then 「模式与权限」.
 * The whole panel scrolls inside [maxHeight] so large text never clips the
 * switches. The runtime filter and search query are UI-only state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelSelectorPanelContent(
    state: ModelPanelState,
    actions: ModelPanelActions,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true,
    maxHeight: Dp = (LocalConfiguration.current.screenHeightDp * 0.7f).dp,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val buckets =
        remember(state.models) {
            state.models
                .groupBy { it.agentRuntimeKind }
                .map { (kind, models) -> RuntimeModelBucket(kind = kind, count = models.size) }
                .sortedBy { it.kind.runtimeSortIndex }
        }
    var runtimeFilterName by rememberSaveable { mutableStateOf<String?>(null) }
    var initializedRuntimeFilter by rememberSaveable { mutableStateOf(false) }
    val runtimeFilter = buckets.firstOrNull { it.kind == runtimeFilterName }?.kind

    LaunchedEffect(state.selectedRuntime, buckets) {
        if (!initializedRuntimeFilter) {
            if (state.selectedRuntime != null && buckets.any { it.kind == state.selectedRuntime }) {
                runtimeFilterName = state.selectedRuntime
            }
            initializedRuntimeFilter = true
        } else if (runtimeFilterName != null && buckets.none { it.kind == runtimeFilterName }) {
            runtimeFilterName = null
        }
    }

    val scopedModels =
        remember(state.models, runtimeFilter) {
            runtimeFilter?.let { runtime -> state.models.filter { it.agentRuntimeKind == runtime } } ?: state.models
        }
    val searchIndex = remember(scopedModels) { ModelSearchIndex(scopedModels) }
    val filteredModels = remember(searchIndex, query) { searchIndex.results(query) }

    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .then(if (showBackground) Modifier.background(AgentBuddyTheme.surface) else Modifier),
        contentPadding = PaddingValues(bottom = BuddySpacing.sm),
    ) {
        if (buckets.isNotEmpty()) {
            item(key = "partner") {
                PartnerSection(
                    buckets = buckets,
                    totalCount = state.models.size,
                    selected = runtimeFilter,
                    onSelect = { runtimeFilterName = it },
                )
            }
        }
        item(key = "model-header") {
            ModelPickerSectionLabel("模型")
            ModelSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = BuddySpacing.md, vertical = BuddySpacing.xxs),
            )
        }
        when {
            state.models.isEmpty() -> item(key = "loading") { ModelPanelStatusText("正在加载模型...") }
            filteredModels.isEmpty() -> item(key = "no-match") { ModelPanelStatusText("没有匹配的模型") }
        }
        itemsIndexed(filteredModels, key = { _, model -> "${model.agentRuntimeKind}:${model.id}" }) { index, model ->
            ModelOptionRow(
                model = model,
                selected = model.matchesModelSelection(state.selectedModel, state.selectedRuntime),
                onClick = { actions.onSelectModel(model) },
            )
            if (index < filteredModels.lastIndex) {
                BuddyDivider(startIndent = BuddySpacing.md + BuddySize.icon + BuddySpacing.sm)
            }
        }
        item(key = "effort") { EffortSection(state, actions) }
        item(key = "mode") { ModeSection(state, actions) }
    }
}

/** 「搭档」: runtime filter chips when there is a choice, otherwise the single partner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnerSection(
    buckets: List<RuntimeModelBucket>,
    totalCount: Int,
    selected: AgentRuntimeKind?,
    onSelect: (AgentRuntimeKind?) -> Unit,
) {
    ModelPickerSectionLabel("搭档")
    BuddyChromeTypeLimit {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuddySpacing.md)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            if (buckets.size > 1) {
                ModelPickerChip(
                    text = "全部",
                    count = totalCount,
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
            }
            buckets.forEach { bucket ->
                ModelPickerChip(
                    text = bucket.kind.titleDisplayLabel,
                    count = bucket.count.takeIf { buckets.size > 1 },
                    selected = buckets.size == 1 || selected == bucket.kind,
                    onClick = { onSelect(bucket.kind) },
                    leading = { AgentIconView(kind = bucket.kind, sizeDp = 16) },
                )
            }
        }
    }
}

/** 「推理强度」: effort chips, or why the effort cannot change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EffortSection(
    state: ModelPanelState,
    actions: ModelPanelActions,
) {
    if (state.effortLocked) {
        ModelPickerSectionLabel("推理强度")
        ModelPanelNote(text = "推理强度在首条消息后即被锁定。", icon = Icons.Outlined.Lock)
        return
    }
    if (state.efforts.isEmpty()) return
    ModelPickerSectionLabel("推理强度")
    BuddyChromeTypeLimit {
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BuddySpacing.md)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(BuddySpacing.xs),
        ) {
            state.efforts.forEach { option ->
                val value = effortLabel(option.reasoningEffort)
                ModelPickerChip(
                    text = effortDisplayName(option.reasoningEffort),
                    selected = state.selectedEffort == value,
                    onClick = { actions.onSelectEffort(value) },
                )
            }
        }
    }
}

/** 「模式与权限」: plan mode, fast mode and full access as switch rows. */
@Composable
private fun ModeSection(
    state: ModelPanelState,
    actions: ModelPanelActions,
) {
    ModelPickerSectionLabel("模式与权限")
    val planMode = state.planMode
    if (planMode != null) {
        ModelPanelSwitchRow(
            title = "计划模式",
            subtitle = "先提出计划，再动手修改。",
            icon = Icons.Outlined.Checklist,
            checked = planMode,
            onCheckedChange = actions.onPlanModeChange,
        )
        BuddyDivider(startIndent = ModeRowDividerIndent)
    }
    ModelPanelSwitchRow(
        title = "快速模式",
        subtitle = "使用更快的服务档位。",
        icon = Icons.Outlined.Bolt,
        checked = state.fastMode,
        onCheckedChange = actions.onFastModeChange,
    )
    BuddyDivider(startIndent = ModeRowDividerIndent)
    val fullAccess = state.fullAccess
    if (fullAccess != null) {
        ModelPanelSwitchRow(
            title = "完全访问",
            subtitle =
                if (fullAccess) {
                    "完全访问：可在主机上任意位置运行命令、修改文件，不再询问。"
                } else {
                    "受监督：只在本项目内工作，需要更多权限时先询问你。"
                },
            icon = if (fullAccess) Icons.Outlined.WarningAmber else Icons.Outlined.Lock,
            iconTint = if (fullAccess) AgentBuddyTheme.warning else AgentBuddyTheme.textSecondary,
            checked = fullAccess,
            onCheckedChange = actions.onFullAccessChange,
            highlighted = fullAccess,
        )
    } else {
        ModelPanelNote(text = "这个搭档自行管理权限，无法在这里切换完全访问。", icon = Icons.Outlined.Lock)
    }
}

/** Separators under the mode rows start after the icon column, like settings groups. */
private val ModeRowDividerIndent = BuddySpacing.md + BuddySize.iconLarge + BuddySpacing.sm
