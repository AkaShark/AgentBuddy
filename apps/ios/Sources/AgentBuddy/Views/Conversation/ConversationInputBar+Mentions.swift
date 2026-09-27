import SwiftUI

extension ConversationInputBar {
    func loadSkills(forceReload: Bool = false) async {
        await loadSkills(forceReload: forceReload, showErrors: true)
    }

    func loadSkills(forceReload: Bool = false, showErrors: Bool) async {
        guard appModel.snapshot?.servers.first(where: { $0.serverId == snapshot.threadKey.serverId })?.canUseTransportActions == true else {
            skills = []
            mentionSkillPathsByName = [:]
            if showErrors {
                slashErrorMessage = "Not connected to a server"
            }
            return
        }
        skillsLoading = true
        defer { skillsLoading = false }
        do {
            let fetchedSkills = try await appModel.client.listSkills(
                serverId: snapshot.threadKey.serverId,
                params: AppListSkillsRequest(
                    cwds: [workDir],
                    forceReload: forceReload
                )
            )
            let loadedSkills = fetchedSkills.sorted { $0.name.lowercased() < $1.name.lowercased() }
            skills = loadedSkills
            let validPaths = Set(loadedSkills.map { $0.path.value })
            mentionSkillPathsByName = mentionSkillPathsByName.filter { _, path in validPaths.contains(path) }
        } catch {
            if showErrors {
                slashErrorMessage = error.localizedDescription
            }
        }
    }

    func collectPluginMentionsForSubmission(_ text: String) -> [PluginMentionSelection] {
        guard !pluginMentionSelections.isEmpty else { return [] }
        let lowered = text.lowercased()
        var seen = Set<String>()
        var resolved: [PluginMentionSelection] = []
        for selection in pluginMentionSelections {
            // Drop selections the user has since deleted from the input text.
            guard lowered.contains("@\(selection.name.lowercased())") else { continue }
            guard seen.insert(selection.path).inserted else { continue }
            resolved.append(selection)
        }
        return resolved
    }

    func applyFileSuggestion(_ match: FileSearchResult) {
        guard let token = activeAtToken else { return }
        let quotedPath = (match.path.contains(" ") && !match.path.contains("\"")) ? "\"\(match.path)\"" : match.path
        let replacement = "\(quotedPath) "
        guard let updated = replacingRange(
            in: inputText,
            with: token.range,
            replacement: replacement
        ) else { return }
        inputText = updated
        showFilePopup = false
        activeAtToken = nil
        clearFileSearchState()
    }

    var pluginSuggestions: [PluginSummary] {
        guard let token = activeAtToken else { return [] }
        let plugins = pluginCacheByCwd[workDir] ?? []
        guard !plugins.isEmpty else { return [] }
        let query = token.value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if query.isEmpty {
            return plugins
        }
        return plugins.filter { plugin in
            if plugin.name.lowercased().contains(query) { return true }
            if plugin.displayTitle.lowercased().contains(query) { return true }
            if let desc = plugin.interface?.shortDescription?.lowercased(), desc.contains(query) {
                return true
            }
            return plugin.marketplaceName.lowercased().contains(query)
        }
    }

    func loadPluginsIfNeeded() {
        let cwd = workDir
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
                    serverId: snapshot.threadKey.serverId,
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
        guard let updated = replacingRange(
            in: inputText,
            with: token.range,
            replacement: replacement
        ) else { return }
        inputText = updated
        let selection = PluginMentionSelection(
            name: plugin.name,
            marketplace: plugin.marketplaceName,
            displayName: plugin.interface?.displayName ?? plugin.displayTitle
        )
        if !pluginMentionSelections.contains(selection) {
            pluginMentionSelections.append(selection)
        }
        showFilePopup = false
        activeAtToken = nil
        clearFileSearchState()
    }

    func removePluginMention(_ selection: PluginMentionSelection) {
        pluginMentionSelections.removeAll { $0 == selection }
        // Best-effort strip of the inline `@name` token from the input.
        let needle = "@\(selection.name)"
        if let range = inputText.range(of: needle) {
            var replaced = inputText
            replaced.removeSubrange(range)
            // Collapse any double-space artifact left behind.
            inputText = replaced.replacingOccurrences(of: "  ", with: " ")
        }
    }

    var skillSuggestions: [SkillMetadata] {
        guard let token = activeDollarToken else { return [] }
        return filterSkillSuggestions(token.value)
    }

    private func filterSkillSuggestions(_ query: String) -> [SkillMetadata] {
        guard !skills.isEmpty else { return [] }
        guard !query.isEmpty else { return skills.sorted { lhs, rhs in lhs.name.lowercased() < rhs.name.lowercased() } }
        return skills
            .compactMap { skill -> (SkillMetadata, Int)? in
                let scoreFromName = composerFuzzyScore(candidate: skill.name, query: query)
                let scoreFromDescription = composerFuzzyScore(candidate: skill.description, query: query)
                let best = max(scoreFromName ?? Int.min, scoreFromDescription ?? Int.min)
                guard best != Int.min else { return nil }
                return (skill, best)
            }
            .sorted { lhs, rhs in
                if lhs.1 != rhs.1 {
                    return lhs.1 > rhs.1
                }
                return lhs.0.name.lowercased() < rhs.0.name.lowercased()
            }
            .map(\.0)
    }

    func applySkillSuggestion(_ skill: SkillMetadata) {
        guard let token = activeDollarToken else { return }
        let replacement = "$\(skill.name) "
        guard let updated = replacingRange(
            in: inputText,
            with: token.range,
            replacement: replacement
        ) else { return }
        inputText = updated
        mentionSkillPathsByName[skill.name.lowercased()] = skill.path.value
        showSkillPopup = false
        activeDollarToken = nil
    }

    func collectSkillMentionsForSubmission(_ text: String) -> [SkillMentionSelection] {
        guard !skills.isEmpty else { return [] }
        let mentionNames = extractMentionNames(text)
        guard !mentionNames.isEmpty else { return [] }

        let skillsByName = Dictionary(grouping: skills, by: { $0.name.lowercased() })
        let skillsByPath = Dictionary(grouping: skills, by: \.path.value)
        var seenPaths = Set<String>()
        var resolved: [SkillMentionSelection] = []

        for mentionName in mentionNames {
            let normalizedName = mentionName.lowercased()
            if let selectedPath = mentionSkillPathsByName[normalizedName], !selectedPath.isEmpty {
                if let selectedSkill = skillsByPath[selectedPath]?.first {
                    guard seenPaths.insert(selectedPath).inserted else { continue }
                    resolved.append(SkillMentionSelection(name: selectedSkill.name, path: selectedPath))
                    continue
                }
                mentionSkillPathsByName.removeValue(forKey: normalizedName)
            }

            guard let candidates = skillsByName[normalizedName], candidates.count == 1 else {
                continue
            }
            let match = candidates[0]
            guard seenPaths.insert(match.path.value).inserted else { continue }
            resolved.append(SkillMentionSelection(name: match.name, path: match.path.value))
        }
        return resolved
    }
}
