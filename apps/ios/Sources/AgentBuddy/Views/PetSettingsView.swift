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
                    SettingsMintRowLabel(
                        title: Text("Show Pet"),
                        subtitle: controller.selectedPet.map { Text(verbatim: $0.displayName) } ?? Text("No pet selected"),
                        systemImage: "pawprint.fill"
                    )
                }
                .tint(AgentBuddyTheme.action)
                .settingsMintRow()
            } header: {
                Text("Wake")
                    .settingsMintHeader()
            }

            Section {
                if connectedServers.isEmpty {
                    placeholder(Text("Connect to a server first"))
                } else {
                    ForEach(connectedServers, id: \.serverId) { server in
                        let isSelected = server.serverId == selectedServerId
                        Button {
                            selectedServerId = server.serverId
                            Task { await refreshPets() }
                        } label: {
                            HStack(spacing: BuddySpacing.sm) {
                                SettingsMintRowLabel(
                                    title: Text(verbatim: server.displayName),
                                    subtitle: Text(verbatim: server.connectionModeLabel),
                                    systemImage: "laptopcomputer"
                                )
                                Spacer(minLength: BuddySpacing.xs)
                                if isSelected {
                                    SettingsMintCheckmark()
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .settingsMintSelected(isSelected)
                        .settingsMintRow()
                    }
                }
            } header: {
                Text("Server")
                    .settingsMintHeader()
            }

            Section {
                if selectedServerId.isEmpty {
                    placeholder(Text("No server selected"))
                } else if isLoading {
                    HStack(spacing: BuddySpacing.sm) {
                        ProgressView().tint(AgentBuddyTheme.textSecondary)
                        Text("Loading pets")
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                    }
                    .frame(minHeight: BuddySize.minHitTarget)
                    .settingsMintRow()
                } else if let errorMessage {
                    errorRow(errorMessage)
                } else if pets.isEmpty {
                    placeholder(Text("~/.codex/pets has no hatch-pet packages"))
                } else {
                    ForEach(pets, id: \.id) { pet in
                        petRow(pet)
                    }
                }

                if let message = controller.errorMessage {
                    errorRow(message)
                }
            } header: {
                HStack {
                    Text("Pets")
                        .settingsMintHeader()
                    Spacer()
                    Button {
                        Task { await refreshPets() }
                    } label: {
                        Text("Refresh")
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(
                                selectedServerId.isEmpty || isLoading ? AgentBuddyTheme.onDisabled : AgentBuddyTheme.link
                            )
                            .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.borderless)
                    .textCase(nil)
                    .disabled(selectedServerId.isEmpty || isLoading)
                }
            }
        }
        .settingsMintList()
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

    private func petRow(_ pet: AppPetSummary) -> some View {
        let isChosen = controller.selectedPet?.id == pet.id
            && controller.selectedPet?.serverId == selectedServerId
        return Button {
            guard pet.hasValidSpritesheet else { return }
            Task {
                await controller.selectPet(
                    appModel: appModel,
                    serverId: selectedServerId,
                    pet: pet
                )
            }
        } label: {
            HStack(spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: pet.displayName)
                        .buddyText(.body, weight: isChosen ? .semibold : .regular)
                        .foregroundStyle(pet.hasValidSpritesheet ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.onDisabled)
                    HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xxs) {
                        if pet.validationError != nil {
                            Image(systemName: "exclamationmark.triangle")
                                .foregroundStyle(AgentBuddyTheme.warning)
                                .accessibilityHidden(true)
                        }
                        Text(verbatim: pet.validationError ?? pet.description ?? pet.sourcePath)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(2)
                    }
                    .buddyText(.caption)
                }
                Spacer(minLength: BuddySpacing.xs)
                if controller.isLoading, isChosen {
                    ProgressView().tint(AgentBuddyTheme.textSecondary)
                } else if isChosen {
                    SettingsMintCheckmark()
                }
            }
            .padding(.vertical, BuddySpacing.xxs)
            .contentShape(Rectangle())
        }
        .disabled(!pet.hasValidSpritesheet)
        .settingsMintSelected(isChosen)
        .settingsMintRow()
    }

    private func placeholder(_ text: Text) -> some View {
        text
            .buddyText(.label, weight: .regular)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .frame(minHeight: BuddySize.minHitTarget, alignment: .leading)
            .settingsMintRow()
    }

    private func errorRow(_ message: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
            Image(systemName: "exclamationmark.circle")
                .accessibilityHidden(true)
            Text(verbatim: message)
                .buddyText(.label, weight: .regular)
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(AgentBuddyTheme.danger)
        .padding(.vertical, BuddySpacing.xxs)
        .settingsMintRow()
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
