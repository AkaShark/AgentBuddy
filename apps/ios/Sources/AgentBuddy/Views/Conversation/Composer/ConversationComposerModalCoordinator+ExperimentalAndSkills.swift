import SwiftUI

extension ConversationComposerModalCoordinator {
    @ViewBuilder
    var experimentalSheetContent: some View {
        NavigationStack {
            Group {
                if experimentalFeaturesLoading {
                    ProgressView().tint(AgentBuddyTheme.accent)
                } else if experimentalFeatures.isEmpty {
                    Text("No experimental features available")
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                } else {
                    List {
                        ForEach(Array(experimentalFeatures.enumerated()), id: \.element.id) { _, feature in
                            HStack(alignment: .top, spacing: 10) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(feature.displayName ?? feature.name)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                    Text(feature.description ?? "")
                                        .agentBuddyFont(.caption)
                                        .foregroundColor(AgentBuddyTheme.textSecondary)
                                }
                                Spacer(minLength: 0)
                                Toggle(
                                    "",
                                    isOn: Binding(
                                        get: { onIsExperimentalFeatureEnabled(feature.id, feature.enabled) },
                                        set: { value in
                                            Task { await onSetExperimentalFeature(feature.name, value) }
                                        }
                                    )
                                )
                                .labelsHidden()
                                .tint(AgentBuddyTheme.accent)
                            }
                            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Experimental")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Reload") { Task { await onLoadExperimentalFeatures() } }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showExperimentalSheet = false }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
    }

    @ViewBuilder
    var skillsSheetContent: some View {
        NavigationStack {
            Group {
                if skillsLoading {
                    ProgressView().tint(AgentBuddyTheme.accent)
                } else if skills.isEmpty {
                    Text("No skills available for this workspace")
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                } else {
                    List {
                        ForEach(skills) { skill in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(skill.name)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                    Spacer()
                                    if skill.enabled {
                                        Text("enabled")
                                            .agentBuddyFont(.caption2)
                                            .foregroundColor(AgentBuddyTheme.accent)
                                    }
                                }
                                Text(skill.description)
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                                Text(skill.path.value)
                                    .agentBuddyFont(.caption2)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                            }
                            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Skills")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Reload") { Task { await onLoadSkills(true, true) } }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showSkillsSheet = false }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
    }
}
