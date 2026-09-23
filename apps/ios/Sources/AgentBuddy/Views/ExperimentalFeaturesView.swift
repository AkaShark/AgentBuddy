import SwiftUI

struct ExperimentalFeaturesView: View {
    @State private var experimentalFeatures = ExperimentalFeatures.shared
    @State private var debugSettings = DebugSettings.shared

    var body: some View {
        ZStack {
            AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
            Form {
                Section {
                    ForEach(AgentBuddyFeature.allCases) { feature in
                        Toggle(isOn: binding(for: feature)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(feature.displayName)
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Text(feature.description)
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                        .tint(AgentBuddyTheme.accentStrong)
                        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    }
                } header: {
                    Text("Features")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                } footer: {
                    Text("Experimental features may be unstable or change without notice.")
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }

                Section {
                    Toggle(isOn: Binding(
                        get: { debugSettings.enabled },
                        set: { debugSettings.enabled = $0 }
                    )) {
                        HStack(spacing: 10) {
                            Image(systemName: "ant")
                                .foregroundColor(AgentBuddyTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Debug Mode")
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Text("Show debug controls in conversations")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                    }
                    .tint(AgentBuddyTheme.accent)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    #if DEBUG
                    NavigationLink {
                        ProximityPairView()
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "wave.3.right")
                                .foregroundColor(AgentBuddyTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Pair")
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Text("Walk-up pairing with proximity + haptics")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    #endif

                    #if !targetEnvironment(macCatalyst) && DEBUG
                    NavigationLink {
                        UWBDebugView()
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "dot.radiowaves.left.and.right")
                                .foregroundColor(AgentBuddyTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("UWB Debug")
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Text("Live distance & direction to a paired Mac")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    #endif
                } header: {
                    Text("Debug")
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Experimental")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func binding(for feature: AgentBuddyFeature) -> Binding<Bool> {
        Binding(
            get: { experimentalFeatures.isEnabled(feature) },
            set: { newValue in
                experimentalFeatures.setEnabled(feature, newValue)
            }
        )
    }
}

#if DEBUG
#Preview("Experimental Features") {
    NavigationStack {
        ExperimentalFeaturesView()
    }
}
#endif
