import SwiftUI

extension SettingsView {
    // MARK: - Experimental Section

    var petSection: some View {
        Section {
            NavigationLink {
                PetSettingsView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "pawprint.fill")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Wake Pet")
                            .agentBuddyFont(.subheadline)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                        if let pet = PetOverlayController.shared.selectedPet {
                            Text(pet.displayName)
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        }
                    }
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
        } header: {
            Text("Pet")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }

    // MARK: - Experimental Section

    var experimentalSection: some View {
        Section {
            NavigationLink {
                ExperimentalFeaturesView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "flask")
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 20)
                    Text("Experimental Features")
                        .agentBuddyFont(.subheadline)
                        .foregroundColor(AgentBuddyTheme.textPrimary)
                }
            }
            .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
        } header: {
            Text("Experimental")
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
    }
}
