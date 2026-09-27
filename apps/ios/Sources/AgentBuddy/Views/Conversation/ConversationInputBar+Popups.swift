import SwiftUI

extension ConversationInputBar {
    func clearFileSearchState(incrementGeneration: Bool = true) {
        let hadTask = fileSearchTask != nil
        fileSearchTask?.cancel()
        fileSearchTask = nil
        if incrementGeneration && (hadTask || fileSearchLoading || fileSearchError != nil || !fileSuggestions.isEmpty) {
            fileSearchGeneration += 1
        }
        if fileSearchLoading {
            fileSearchLoading = false
        }
        if fileSearchError != nil {
            fileSearchError = nil
        }
        if !fileSuggestions.isEmpty {
            fileSuggestions = []
        }
    }

    func hideComposerPopups() {
        popupRefreshTask?.cancel()
        popupRefreshTask = nil
        if showSlashPopup {
            showSlashPopup = false
        }
        if activeSlashToken != nil {
            activeSlashToken = nil
        }
        if !slashSuggestions.isEmpty {
            slashSuggestions = []
        }
        if showFilePopup {
            showFilePopup = false
        }
        if activeAtToken != nil {
            activeAtToken = nil
        }
        if showSkillPopup {
            showSkillPopup = false
        }
        if activeDollarToken != nil {
            activeDollarToken = nil
        }
        clearFileSearchState()
    }

    private func startFileSearch(_ query: String) {
        fileSearchTask?.cancel()
        fileSearchTask = nil
        let requestId = fileSearchGeneration + 1
        fileSearchGeneration = requestId
        if !fileSearchLoading {
            fileSearchLoading = true
        }
        if fileSearchError != nil {
            fileSearchError = nil
        }
        if !fileSuggestions.isEmpty {
            fileSuggestions = []
        }

        fileSearchTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 140_000_000)
            guard !Task.isCancelled else { return }
            guard activeAtToken?.value == query else { return }

            do {
                let matches = try await onFileSearch(query)
                guard !Task.isCancelled else { return }
                guard requestId == fileSearchGeneration, activeAtToken?.value == query else { return }
                fileSuggestions = matches
                fileSearchLoading = false
                fileSearchError = nil
            } catch {
                guard !Task.isCancelled else { return }
                guard requestId == fileSearchGeneration, activeAtToken?.value == query else { return }
                fileSuggestions = []
                fileSearchLoading = false
                fileSearchError = error.localizedDescription
            }
        }
    }

    func scheduleComposerPopupRefresh(for nextText: String) {
        popupRefreshTask?.cancel()
        let needsPopupEvaluation =
            showSlashPopup ||
            showFilePopup ||
            showSkillPopup ||
            activeSlashToken != nil ||
            activeAtToken != nil ||
            activeDollarToken != nil ||
            nextText.contains("/") ||
            nextText.contains("@") ||
            nextText.contains("$")

        guard needsPopupEvaluation else {
            hideComposerPopups()
            return
        }

        popupRefreshTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 70_000_000)
            guard !Task.isCancelled else { return }
            refreshComposerPopups(for: nextText)
        }
    }

    private func refreshComposerPopups(for nextText: String) {
        let cursor = nextText.count
        if let atToken = currentPrefixedToken(
            text: nextText,
            cursor: cursor,
            prefix: "@",
            allowEmpty: true
        ) {
            if showSlashPopup {
                showSlashPopup = false
            }
            if activeSlashToken != nil {
                activeSlashToken = nil
            }
            if !slashSuggestions.isEmpty {
                slashSuggestions = []
            }
            if showSkillPopup {
                showSkillPopup = false
            }
            if activeDollarToken != nil {
                activeDollarToken = nil
            }
            if !showFilePopup {
                showFilePopup = true
            }
            if activeAtToken != atToken {
                activeAtToken = atToken
                startFileSearch(atToken.value)
                loadPluginsIfNeeded()
            }
            return
        }

        if activeAtToken != nil || showFilePopup || fileSearchTask != nil || fileSearchLoading || fileSearchError != nil || !fileSuggestions.isEmpty {
            activeAtToken = nil
            if showFilePopup {
                showFilePopup = false
            }
            clearFileSearchState()
        }

        if let dollarToken = currentPrefixedToken(
            text: nextText,
            cursor: cursor,
            prefix: "$",
            allowEmpty: true
        ), isMentionQueryValid(dollarToken.value) {
            if showSlashPopup {
                showSlashPopup = false
            }
            if activeSlashToken != nil {
                activeSlashToken = nil
            }
            if !slashSuggestions.isEmpty {
                slashSuggestions = []
            }
            if !showSkillPopup {
                showSkillPopup = true
            }
            if activeDollarToken != dollarToken {
                activeDollarToken = dollarToken
            }
            if !hasAttemptedSkillMentionLoad && !skillsLoading {
                hasAttemptedSkillMentionLoad = true
                Task { await loadSkills(showErrors: false) }
            }
            return
        }

        if activeDollarToken != nil || showSkillPopup {
            activeDollarToken = nil
            if showSkillPopup {
                showSkillPopup = false
            }
        }

        guard let slashToken = currentSlashQueryContext(text: nextText, cursor: cursor) else {
            if showSlashPopup {
                showSlashPopup = false
            }
            if activeSlashToken != nil {
                activeSlashToken = nil
            }
            if !slashSuggestions.isEmpty {
                slashSuggestions = []
            }
            return
        }

        if activeSlashToken != slashToken {
            activeSlashToken = slashToken
        }
        let suggestions = filterSlashCommands(slashToken.query)
        if slashSuggestions != suggestions {
            slashSuggestions = suggestions
        }
        let shouldShow = !suggestions.isEmpty
        if showSlashPopup != shouldShow {
            showSlashPopup = shouldShow
        }
    }
}
