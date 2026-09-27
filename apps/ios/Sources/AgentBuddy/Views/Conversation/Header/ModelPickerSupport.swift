import Foundation

func modelMatchesSelection(
    _ model: ModelInfo,
    _ selection: String,
    runtime: AgentRuntimeKind? = nil
) -> Bool {
    let trimmed = selection.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return false }
    if let runtime, model.agentRuntimeKind != runtime { return false }
    return model.id == trimmed || model.model == trimmed
}

func defaultReasoningEffortSelection(for model: ModelInfo) -> String {
    model.supportedReasoningEfforts.isEmpty ? "" : model.defaultReasoningEffort.wireValue
}

/// Allowlist of model "mode" names the runtime advertises (e.g. Amp's
/// `smart` / `rush` / `deep`). Pulled from `capabilities.visible_modes`
/// in the alleycat manifest so the rule is per-agent, not Amp-hardcoded.
func visibleModeNames(for kind: AgentRuntimeKind) -> Set<String>? {
    kind.metadata?.capabilities?.visibleModes.map(Set.init)
}

/// Strip the optional agent-name prefix (`<kind>/` or `<kind>:`) the
/// remote sometimes adds when reporting modes, so the bare mode name
/// matches the allowlist.
private func normalizedModeName(_ value: String, kind: AgentRuntimeKind) -> String {
    var out = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let prefixes = ["\(kind)/", "\(kind):", "\(kind)\\"]
    for prefix in prefixes where out.hasPrefix(prefix) {
        out = String(out.dropFirst(prefix.count))
    }
    return out
}

private func modeName(for model: ModelInfo) -> String {
    let kind = model.agentRuntimeKind
    let idMode = normalizedModeName(model.id, kind: kind)
    if !idMode.isEmpty { return idMode }
    return normalizedModeName(model.model, kind: kind)
}

func modelPickerDisplayName(_ model: ModelInfo) -> String {
    if visibleModeNames(for: model.agentRuntimeKind) != nil {
        let mode = modeName(for: model)
        if !mode.isEmpty { return mode }
    }
    return model.displayName.isEmpty ? model.id : model.displayName
}

func isVisibleModelOption(_ model: ModelInfo) -> Bool {
    guard let modes = visibleModeNames(for: model.agentRuntimeKind) else {
        return true
    }
    return modes.contains(modeName(for: model))
}

struct RuntimeModelBucket: Identifiable {
    let kind: AgentRuntimeKind
    let count: Int

    var id: AgentRuntimeKind { kind }
}

func runtimeModelBuckets(for models: [ModelInfo]) -> [RuntimeModelBucket] {
    let grouped = Dictionary(grouping: models, by: \.agentRuntimeKind)
    return AgentRuntimeKind.presentationOrder.compactMap { kind in
        guard let models = grouped[kind], !models.isEmpty else { return nil }
        return RuntimeModelBucket(kind: kind, count: models.count)
    }
}

extension [ModelInfo] {
    func filtered(by runtime: AgentRuntimeKind?) -> [ModelInfo] {
        guard let runtime else { return self }
        return filter { $0.agentRuntimeKind == runtime }
    }
}

struct ModelSearchIndex {
    private struct Row {
        let model: ModelInfo
        let searchableText: String
    }

    private static let maxResults = 80

    private var rows: [Row] = []

    var isEmpty: Bool {
        rows.isEmpty
    }

    init() {}

    init(models: [ModelInfo]) {
        rows = models.map { model in
            Row(
                model: model,
                searchableText: [
                    model.id,
                    model.model,
                    model.agentRuntimeKind.displayLabel,
                    model.agentRuntimeKind.titleDisplayLabel,
                    modelPickerDisplayName(model),
                    model.description
                ]
                .joined(separator: "\n")
                .lowercased()
            )
        }
    }

    func results(matching query: String) -> [ModelInfo] {
        let normalizedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalizedQuery.isEmpty else {
            return Array(rows.prefix(Self.maxResults).map(\.model))
        }

        var matches: [ModelInfo] = []
        matches.reserveCapacity(min(Self.maxResults, rows.count))
        for row in rows where row.searchableText.contains(normalizedQuery) {
            matches.append(row.model)
            if matches.count == Self.maxResults {
                break
            }
        }
        return matches
    }
}
