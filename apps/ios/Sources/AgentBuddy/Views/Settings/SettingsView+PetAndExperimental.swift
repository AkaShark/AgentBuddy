import SwiftUI

extension SettingsView {
    // MARK: - Pet Section

    var petSection: some View {
        Section {
            NavigationLink {
                PetSettingsView()
            } label: {
                SettingsMintRowLabel(
                    title: Text("Wake Pet"),
                    subtitle: PetOverlayController.shared.selectedPet.map { Text(verbatim: $0.displayName) },
                    systemImage: "pawprint.fill"
                )
            }
            .settingsMintRow()
        } header: {
            Text("Pet")
                .settingsMintHeader()
        }
    }

    // MARK: - Experimental Section

    var experimentalSection: some View {
        Section {
            NavigationLink {
                ExperimentalFeaturesView()
            } label: {
                SettingsMintRowLabel("Experimental Features", systemImage: "flask")
            }
            .settingsMintRow()
        } header: {
            Text("Experimental")
                .settingsMintHeader()
        }
    }
}
