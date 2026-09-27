import SwiftUI

extension DirectoryPickerView {
    private var searchQueryBinding: Binding<String> {
        Binding(
            get: { model.searchQuery },
            set: { model.searchQuery = $0 }
        )
    }

    var controls: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Text(
                    DirectoryPickerStrings.connectedServer(
                        selectedServerOption.map { "\($0.name) • \($0.sourceLabel)" } ??
                            DirectoryPickerStrings.noServerSelected
                    )
                )
                .agentBuddyFont(.caption)
                .foregroundColor(selectedServerOption == nil ? AgentBuddyTheme.textMuted : AgentBuddyTheme.textSecondary)
                .lineLimit(1)

                Spacer()

                if !servers.isEmpty {
                    Menu(DirectoryPickerStrings.changeServer) {
                        ForEach(servers) { server in
                            Button("\(server.name) • \(server.sourceLabel)") {
                                selectedServerId = server.id
                            }
                        }
                    }
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.accent)
                }

                Button {
                    model.showHiddenDirectories.toggle()
                } label: {
                    Image(systemName: model.showHiddenDirectories ? "eye" : "eye.slash")
                        .foregroundColor(model.showHiddenDirectories ? AgentBuddyTheme.accent : AgentBuddyTheme.textSecondary)
                }
                .accessibilityLabel(
                    model.showHiddenDirectories ?
                        String(localized: "directory_picker_hide_hidden_folders") :
                        String(localized: "directory_picker_show_hidden_folders")
                )
            }

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(AgentBuddyTheme.textMuted)
                TextField(
                    DirectoryPickerStrings.searchFolders,
                    text: searchQueryBinding
                )
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)

                if !model.searchQuery.isEmpty {
                    Button {
                        model.searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(AgentBuddyTheme.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(AgentBuddyTheme.surface.opacity(0.65))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(AgentBuddyTheme.border.opacity(0.85), lineWidth: 1)
            )
            .cornerRadius(8)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button {
                        Task {
                            await model.navigateUp(
                                selectedServerId: selectedServerId,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    } label: {
                        Label(DirectoryPickerStrings.upOneLevel, systemImage: "arrow.up.backward")
                            .agentBuddyFont(.caption)
                    }
                    .disabled(!model.canNavigateUp)

                    Button {
                        if selectedServerIsLocal {
                            pathInput = PathDisplay.display(model.currentPath, isLocal: true)
                        } else {
                            pathInput = model.currentPath
                        }
                        showGoToPathAlert = true
                    } label: {
                        Label(DirectoryPickerStrings.goToPath, systemImage: "arrow.right.to.line")
                            .agentBuddyFont(.caption)
                    }
                    .disabled(selectedServerSnapshot?.canBrowseDirectories != true)

                    Button {
                        newFolderName = ""
                        showNewFolderAlert = true
                    } label: {
                        Label(DirectoryPickerStrings.newFolder, systemImage: "folder.badge.plus")
                            .agentBuddyFont(.caption)
                    }
                    .disabled(!canSelectPath)

                    ForEach(model.pathSegments()) { segment in
                        Button {
                            Task {
                                await model.navigateToPath(
                                    segment.path,
                                    selectedServerId: selectedServerId,
                                    appModel: appModel,
                                    isLocalServer: selectedServerIsLocal
                                )
                            }
                        } label: {
                            Text(segment.label)
                                .agentBuddyFont(.caption)
                                .foregroundColor(segment.path == model.currentPath ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(segment.path == model.currentPath ? AgentBuddyTheme.accent : AgentBuddyTheme.surface.opacity(0.65))
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.ultraThinMaterial)
    }
}
