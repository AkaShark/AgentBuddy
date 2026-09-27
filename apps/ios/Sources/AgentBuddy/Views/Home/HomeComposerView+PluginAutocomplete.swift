import SwiftUI

extension HomeComposerView {
    // MARK: - Plugin autocomplete

    var filteredPluginSuggestions: [PluginSummary] {
        guard let project else { return [] }
        let plugins = pluginCacheByCwd[project.cwd] ?? []
        guard !plugins.isEmpty else { return [] }
        let query = (activeAtToken?.value ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        guard !query.isEmpty else { return plugins }
        return plugins.filter { plugin in
            if plugin.name.lowercased().contains(query) { return true }
            if plugin.displayTitle.lowercased().contains(query) { return true }
            if let desc = plugin.interface?.shortDescription?.lowercased(), desc.contains(query) {
                return true
            }
            return plugin.marketplaceName.lowercased().contains(query)
        }
    }

    func scheduleHomePopupRefresh(for nextText: String) {
        popupRefreshTask?.cancel()
        popupRefreshTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 70_000_000)
            guard !Task.isCancelled else { return }
            refreshHomePopup(for: nextText)
        }
    }

    private func refreshHomePopup(for nextText: String) {
        guard project != nil else {
            showPluginPopup = false
            activeAtToken = nil
            return
        }
        let cursor = nextText.count
        if let atToken = currentPrefixedToken(
            text: nextText,
            cursor: cursor,
            prefix: "@",
            allowEmpty: true
        ) {
            if activeAtToken != atToken {
                activeAtToken = atToken
                loadPluginsIfNeeded()
            }
            showPluginPopup = true
        } else if showPluginPopup || activeAtToken != nil {
            showPluginPopup = false
            activeAtToken = nil
        }
    }

    private func loadPluginsIfNeeded() {
        guard let project else { return }
        let cwd = project.cwd
        guard !pluginUnsupportedCwds.contains(cwd),
              pluginCacheByCwd[cwd] == nil,
              !pluginLoadingCwds.contains(cwd) else {
            return
        }
        pluginLoadingCwds.insert(cwd)
        Task {
            defer { pluginLoadingCwds.remove(cwd) }
            do {
                let plugins = try await appModel.client.listPlugins(
                    serverId: project.serverId,
                    params: AppListPluginsRequest(cwds: [cwd])
                )
                pluginCacheByCwd[cwd] = plugins
            } catch {
                pluginUnsupportedCwds.insert(cwd)
            }
        }
    }

    func applyPluginSuggestion(_ plugin: PluginSummary) {
        guard let token = activeAtToken else { return }
        let replacement = "@\(plugin.name) "
        if let updated = replacingRange(
            in: inputText,
            with: token.range,
            replacement: replacement
        ) {
            inputText = updated
        }
        let selection = PluginMentionSelection(
            name: plugin.name,
            marketplace: plugin.marketplaceName,
            displayName: plugin.interface?.displayName ?? plugin.displayTitle
        )
        if !pluginMentionSelections.contains(selection) {
            pluginMentionSelections.append(selection)
        }
        showPluginPopup = false
        activeAtToken = nil
    }

    func removePluginMention(_ selection: PluginMentionSelection) {
        pluginMentionSelections.removeAll { $0 == selection }
        let needle = "@\(selection.name)"
        if let range = inputText.range(of: needle) {
            var replaced = inputText
            replaced.removeSubrange(range)
            inputText = replaced.replacingOccurrences(of: "  ", with: " ")
        }
    }

    func collectPluginMentionsForSubmission(_ text: String) -> [PluginMentionSelection] {
        guard !pluginMentionSelections.isEmpty else { return [] }
        let lowered = text.lowercased()
        var seen = Set<String>()
        var resolved: [PluginMentionSelection] = []
        for selection in pluginMentionSelections {
            guard lowered.contains("@\(selection.name.lowercased())") else { continue }
            guard seen.insert(selection.path).inserted else { continue }
            resolved.append(selection)
        }
        return resolved
    }
}
