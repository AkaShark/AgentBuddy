import SwiftUI

struct ExperimentalFeaturesView: View {
    @State private var experimentalFeatures = ExperimentalFeatures.shared
    @State private var debugSettings = DebugSettings.shared

    var body: some View {
        Form {
            Section {
                ForEach(AgentBuddyFeature.allCases) { feature in
                    Toggle(isOn: binding(for: feature)) {
                        SettingsMintRowLabel(
                            title: Text(verbatim: feature.displayName),
                            subtitle: Text(verbatim: feature.description)
                        )
                    }
                    .tint(AgentBuddyTheme.action)
                    .settingsMintRow()
                }
            } header: {
                Text("Features")
                    .settingsMintHeader()
            } footer: {
                Label {
                    Text("Experimental features may be unstable or change without notice.")
                } icon: {
                    Image(systemName: "exclamationmark.triangle")
                        .foregroundStyle(AgentBuddyTheme.warning)
                        .accessibilityHidden(true)
                }
                .settingsMintFooter()
            }

            Section {
                Toggle(isOn: Binding(
                    get: { debugSettings.enabled },
                    set: { debugSettings.enabled = $0 }
                )) {
                    SettingsMintRowLabel(
                        "Debug Mode",
                        subtitle: "Show debug controls in conversations",
                        systemImage: "ant"
                    )
                }
                .tint(AgentBuddyTheme.action)
                .settingsMintRow()


            } header: {
                Text("Debug")
                    .settingsMintHeader()
            }
        }
        .settingsMintList()
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
