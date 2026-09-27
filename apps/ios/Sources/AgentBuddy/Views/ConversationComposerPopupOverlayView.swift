import SwiftUI

enum ConversationComposerPopupState {
    case none
    case slash([ComposerSlashCommand])
    case file(
        loading: Bool,
        error: String?,
        suggestions: [FileSearchResult],
        plugins: [PluginSummary]
    )
    case skill(loading: Bool, suggestions: [SkillMetadata])
}

/// Suggestion popup above the composer for `/` commands, `@` files/plugins and
/// `$` skills. Rows are 44pt tall; the token itself is shown in code style.
struct ConversationComposerPopupOverlayView: View {
    let state: ConversationComposerPopupState
    let onApplySlashSuggestion: (ComposerSlashCommand) -> Void
    let onApplyFileSuggestion: (FileSearchResult) -> Void
    let onApplySkillSuggestion: (SkillMetadata) -> Void
    let onApplyPluginSuggestion: (PluginSummary) -> Void

    var body: some View {
        switch state {
        case .none:
            EmptyView()

        case .slash(let suggestions):
            suggestionPopup {
                ForEach(Array(suggestions.enumerated()), id: \.offset) { index, command in
                    suggestionRow(showsDivider: index < suggestions.count - 1) {
                        onApplySlashSuggestion(command)
                    } content: {
                        tokenText("/\(command.rawValue)")
                        detailText(command.description)
                    }
                }
            }

        case .file(let loading, let error, let suggestions, let plugins):
            suggestionPopup {
                let cappedPlugins = Array(plugins.prefix(6))
                let cappedFiles = Array(suggestions.prefix(8))
                if cappedPlugins.isEmpty && loading {
                    popupStateText(Text("Searching files..."), showsProgress: true)
                } else if cappedPlugins.isEmpty && cappedFiles.isEmpty {
                    if let error, !error.isEmpty {
                        popupStateText(Text(verbatim: error), systemImage: "exclamationmark.triangle", color: AgentBuddyTheme.danger)
                    } else {
                        popupStateText(Text("No matches"))
                    }
                } else {
                    if !cappedPlugins.isEmpty {
                        sectionHeader("Plugins")
                        ForEach(Array(cappedPlugins.enumerated()), id: \.element.id) { index, plugin in
                            suggestionRow(showsDivider: index < cappedPlugins.count - 1 || !cappedFiles.isEmpty) {
                                onApplyPluginSuggestion(plugin)
                            } content: {
                                rowIcon("puzzlepiece.extension.fill", color: AgentBuddyTheme.link)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(verbatim: plugin.displayTitle)
                                        .buddyText(.label)
                                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                                        .lineLimit(1)
                                    if let subtitle = plugin.interface?.shortDescription, !subtitle.isEmpty {
                                        Text(verbatim: subtitle)
                                            .buddyText(.caption)
                                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }
                    }

                    if !cappedFiles.isEmpty {
                        if !cappedPlugins.isEmpty {
                            sectionHeader("Files")
                        }
                        ForEach(Array(cappedFiles.enumerated()), id: \.offset) { index, suggestion in
                            suggestionRow(showsDivider: index < cappedFiles.count - 1) {
                                onApplyFileSuggestion(suggestion)
                            } content: {
                                rowIcon("doc", color: AgentBuddyTheme.textSecondary)
                                Text(verbatim: suggestion.path)
                                    .buddyText(.code)
                                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                                    .lineLimit(1)
                                    .truncationMode(.middle)
                            }
                        }
                    }
                }
            }

        case .skill(let loading, let suggestions):
            suggestionPopup {
                if loading && suggestions.isEmpty {
                    popupStateText(Text("Loading skills..."), showsProgress: true)
                } else if suggestions.isEmpty {
                    popupStateText(Text("No skills found"))
                } else {
                    let capped = Array(suggestions.prefix(8))
                    ForEach(Array(capped.enumerated()), id: \.offset) { index, skill in
                        suggestionRow(showsDivider: index < capped.count - 1) {
                            onApplySkillSuggestion(skill)
                        } content: {
                            tokenText("$\(skill.name)")
                            detailText(skill.description)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Building blocks

    private func suggestionRow<Content: View>(
        showsDivider: Bool,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(spacing: 0) {
            Button(action: action) {
                HStack(spacing: BuddySpacing.xs) {
                    content()
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, BuddySpacing.sm)
                .padding(.vertical, BuddySpacing.xxs)
                .frame(minHeight: BuddySize.minHitTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if showsDivider {
                BuddyDivider()
                    .padding(.leading, BuddySpacing.sm)
            }
        }
    }

    private func tokenText(_ token: String) -> some View {
        Text(verbatim: token)
            .buddyText(.code)
            .foregroundStyle(AgentBuddyTheme.link)
            .lineLimit(1)
            .fixedSize()
    }

    private func detailText(_ text: String) -> some View {
        Text(verbatim: text)
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .lineLimit(1)
    }

    private func rowIcon(_ systemImage: String, color: Color) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(color)
            .frame(width: 24)
            .accessibilityHidden(true)
    }

    private func sectionHeader(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .buddyText(.caption, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, BuddySpacing.sm)
            .padding(.top, BuddySpacing.xs)
            .padding(.bottom, BuddySpacing.xxs)
    }

    private func popupStateText(
        _ text: Text,
        systemImage: String? = nil,
        color: Color = AgentBuddyTheme.textSecondary,
        showsProgress: Bool = false
    ) -> some View {
        HStack(spacing: BuddySpacing.xs) {
            if showsProgress {
                ProgressView()
                    .controlSize(.small)
                    .tint(AgentBuddyTheme.textSecondary)
            } else if let systemImage {
                Image(systemName: systemImage)
                    .accessibilityHidden(true)
            }
            text
                .buddyText(.label, weight: .regular)
        }
        .foregroundStyle(color)
        .frame(maxWidth: .infinity, minHeight: BuddySize.minHitTarget, alignment: .leading)
        .padding(.horizontal, BuddySpacing.sm)
    }

    private func suggestionPopup<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) {
            content()
        }
        .padding(.vertical, BuddySpacing.xxs)
        .frame(maxWidth: .infinity)
        .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
        .shadow(color: AgentBuddyTheme.floatingShadow, radius: 16, y: 6)
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.bottom, BuddySpacing.xxs)
        .padding(.bottom, 56)
    }
}
