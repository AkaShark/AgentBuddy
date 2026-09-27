package com.akashark.agentbuddy.android.ui.common

import com.akashark.agentbuddy.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.ModelInfo
import uniffi.codex_mobile_client.ReasoningEffort
import java.util.Locale

internal fun effortLabel(value: ReasoningEffort): String = when (value) {
    ReasoningEffort.NONE -> "none"
    ReasoningEffort.MINIMAL -> "minimal"
    ReasoningEffort.LOW -> "low"
    ReasoningEffort.MEDIUM -> "medium"
    ReasoningEffort.HIGH -> "high"
    ReasoningEffort.X_HIGH -> "xhigh"
    ReasoningEffort.MAX -> "max"
}

internal fun ModelInfo.defaultReasoningEffortSelection(): String? =
    if (supportedReasoningEfforts.isEmpty()) null else effortLabel(defaultReasoningEffort)

private val AmpVisibleModes = setOf("smart", "rush", "deep")

private fun normalizedAmpModeName(value: String): String =
    value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("amp/")
        .removePrefix("amp:")

private fun ModelInfo.ampModeName(): String =
    normalizedAmpModeName(id)
        .ifEmpty {
            normalizedAmpModeName(model)
        }

internal fun ModelInfo.modelPickerDisplayName(): String =
    if (agentRuntimeKind == "amp") {
        ampModeName().ifEmpty { displayName.ifBlank { id } }
    } else {
        displayName.ifBlank { id }
    }

internal fun ModelInfo.isVisibleModelOption(): Boolean =
    agentRuntimeKind != "amp" || ampModeName() in AmpVisibleModes

internal data class RuntimeModelBucket(
    val kind: AgentRuntimeKind,
    val count: Int,
)

private const val MaxModelSearchResults = 80

internal class ModelSearchIndex(models: List<ModelInfo>) {
    private data class Row(
        val model: ModelInfo,
        val searchableText: String,
    )

    private val rows = models.map { model ->
        Row(
            model = model,
            searchableText = buildString {
                append(model.id)
                append('\n')
                append(model.model)
                append('\n')
                append(model.agentRuntimeKind)
                append('\n')
                append(model.modelPickerDisplayName())
                append('\n')
                append(model.description)
            }.lowercase(Locale.ROOT),
        )
    }

    fun results(query: String): List<ModelInfo> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) {
            return rows.map { it.model }
        }

        val matches = ArrayList<ModelInfo>(minOf(MaxModelSearchResults, rows.size))
        for (row in rows) {
            if (row.searchableText.contains(normalizedQuery)) {
                matches += row.model
                if (matches.size == MaxModelSearchResults) {
                    break
                }
            }
        }
        return matches
    }
}

internal fun ModelInfo.matchesModelSelection(
    selection: String?,
    runtimeKind: AgentRuntimeKind? = null,
): Boolean {
    val trimmed = selection?.trim().orEmpty()
    if (trimmed.isEmpty()) return false
    if (runtimeKind != null && agentRuntimeKind != runtimeKind) return false
    return id == trimmed || model == trimmed
}
