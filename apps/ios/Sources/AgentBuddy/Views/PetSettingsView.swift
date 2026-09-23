import SwiftUI

struct PetSettingsView: View {
    @Environment(AppModel.self) private var appModel
    @State private var controller = PetOverlayController.shared
    @State private var selectedServerId = ""
    @State private var pets: [AppPetSummary] = []
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var connectedServers: [AppServerSnapshot] {
        appModel.snapshot?.servers.filter(\.isConnected) ?? []
    }

    var body: some View {
        Form {
            Section {
                Toggle(isOn: Binding(
                    get: { controller.visible },
                    set: { controller.setVisible($0) }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Show Pet")
                            .agentBuddyFont(.subheadline)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                        Text(controller.selectedPet?.displayName ?? "No pet selected")
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                }
                .tint(AgentBuddyTheme.accent)
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            } header: {
                Text("Wake")
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }

            Section {
                if connectedServers.isEmpty {
                    Text("Connect to a server first")
                        .agentBuddyFont(.footnote)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                } else {
                    ForEach(connectedServers, id: \.serverId) { server in
                        Button {
                            selectedServerId = server.serverId
                            Task { await refreshPets() }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(server.displayName)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                    Text(server.connectionModeLabel)
                                        .agentBuddyFont(.caption)
                                        .foregroundColor(AgentBuddyTheme.textSecondary)
                                }
                                Spacer()
                                if server.serverId == selectedServerId {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(AgentBuddyTheme.accentStrong)
                                }
                            }
                        }
                    }
                }
            } header: {
                Text("Server")
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }

            Section {
                if selectedServerId.isEmpty {
                    Text("No server selected")
                        .foregroundColor(AgentBuddyTheme.textMuted)
                } else if isLoading {
                    HStack {
                        ProgressView().tint(AgentBuddyTheme.accent)
                        Text("Loading pets")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                } else if let errorMessage {
                    Text(errorMessage)
                        .foregroundColor(AgentBuddyTheme.danger)
                } else if pets.isEmpty {
                    Text("~/.codex/pets has no hatch-pet packages")
                        .foregroundColor(AgentBuddyTheme.textMuted)
                } else {
                    ForEach(pets, id: \.id) { pet in
                        Button {
                            guard pet.hasValidSpritesheet else { return }
                            Task {
                                await controller.selectPet(
                                    appModel: appModel,
                                    serverId: selectedServerId,
                                    pet: pet
                                )
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(pet.displayName)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(pet.hasValidSpritesheet ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textMuted)
                                    Text(pet.validationError ?? pet.description ?? pet.sourcePath)
                                        .agentBuddyFont(.caption)
                                        .foregroundColor(AgentBuddyTheme.textSecondary)
                                        .lineLimit(2)
                                }
                                Spacer()
                                if controller.isLoading,
                                   controller.selectedPet?.id == pet.id,
                                   controller.selectedPet?.serverId == selectedServerId {
                                    ProgressView().tint(AgentBuddyTheme.accent)
                                } else if controller.selectedPet?.id == pet.id,
                                          controller.selectedPet?.serverId == selectedServerId {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(AgentBuddyTheme.accentStrong)
                                }
                            }
                        }
                        .disabled(!pet.hasValidSpritesheet)
                    }
                }

                if let message = controller.errorMessage {
                    Text(message)
                        .foregroundColor(AgentBuddyTheme.danger)
                }
            } header: {
                HStack {
                    Text("Pets")
                    Spacer()
                    Button("Refresh") {
                        Task { await refreshPets() }
                    }
                    .disabled(selectedServerId.isEmpty || isLoading)
                }
                .foregroundColor(AgentBuddyTheme.textSecondary)
            }
        }
        .scrollContentBackground(.hidden)
        .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Pet")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if selectedServerId.isEmpty {
                selectedServerId = controller.selectedPet?.serverId
                    ?? appModel.snapshot?.activeThread?.serverId
                    ?? connectedServers.first?.serverId
                    ?? ""
            }
            await refreshPets()
        }
    }

    @MainActor
    private func refreshPets() async {
        guard !selectedServerId.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        do {
            pets = try await appModel.client.listPets(serverId: selectedServerId)
        } catch {
            pets = []
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
