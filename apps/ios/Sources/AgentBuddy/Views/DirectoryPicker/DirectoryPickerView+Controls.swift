import SwiftUI

extension DirectoryPickerView {
    private var searchQueryBinding: Binding<String> {
        Binding(
            get: { model.searchQuery },
            set: { model.searchQuery = $0 }
        )
    }

    var controls: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(spacing: BuddySpacing.xs) {
                Text(
                    DirectoryPickerStrings.connectedServer(
                        selectedServerOption.map { "\($0.name) • \($0.sourceLabel)" } ??
                            DirectoryPickerStrings.noServerSelected
                    )
                )
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .lineLimit(1)

                Spacer(minLength: BuddySpacing.xs)

                if !servers.isEmpty {
                    Menu {
                        ForEach(servers) { server in
                            Button("\(server.name) • \(server.sourceLabel)") {
                                selectedServerId = server.id
                            }
                        }
                    } label: {
                        Text(DirectoryPickerStrings.changeServer)
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.link)
                            .frame(minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                }

                Button {
                    model.showHiddenDirectories.toggle()
                } label: {
                    Image(systemName: model.showHiddenDirectories ? "eye" : "eye.slash")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(model.showHiddenDirectories ? AgentBuddyTheme.link : AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(
                    model.showHiddenDirectories ?
                        String(localized: "directory_picker_hide_hidden_folders") :
                        String(localized: "directory_picker_show_hidden_folders")
                )
            }

            searchField

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: BuddySpacing.xs) {
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
                            .buddyContextChip(isEnabled: model.canNavigateUp)
                    }
                    .buttonStyle(.plain)
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
                            .buddyContextChip(isEnabled: selectedServerSnapshot?.canBrowseDirectories == true)
                    }
                    .buttonStyle(.plain)
                    .disabled(selectedServerSnapshot?.canBrowseDirectories != true)

                    Button {
                        newFolderName = ""
                        showNewFolderAlert = true
                    } label: {
                        Label(DirectoryPickerStrings.newFolder, systemImage: "folder.badge.plus")
                            .buddyContextChip(isEnabled: canSelectPath)
                    }
                    .buttonStyle(.plain)
                    .disabled(!canSelectPath)

                    ForEach(model.pathSegments()) { segment in
                        let isCurrent = segment.path == model.currentPath
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
                                .buddyText(.label, weight: isCurrent ? .semibold : .medium)
                                .foregroundStyle(isCurrent ? AgentBuddyTheme.onBrand : AgentBuddyTheme.textPrimary)
                                .lineLimit(1)
                                .padding(.horizontal, BuddySpacing.sm)
                                .frame(minHeight: BuddySize.compactPill)
                                .background(isCurrent ? AgentBuddyTheme.brand : AgentBuddyTheme.surface, in: Capsule())
                                .overlay {
                                    if !isCurrent {
                                        Capsule().strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                                    }
                                }
                                .frame(minHeight: BuddySize.minHitTarget)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(isCurrent ? .isSelected : [])
                    }
                }
            }
        }
        .padding(.horizontal, BuddySpacing.xl)
        .padding(.top, BuddySpacing.xs)
        .padding(.bottom, BuddySpacing.xxs)
    }

    private var searchField: some View {
        HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)
            TextField(
                DirectoryPickerStrings.searchFolders,
                text: searchQueryBinding
            )
            .buddyText(.body)
            .foregroundStyle(AgentBuddyTheme.textPrimary)
            .tint(AgentBuddyTheme.focus)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)

            if !model.searchQuery.isEmpty {
                Button {
                    model.searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Clear search"))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .frame(minHeight: BuddySize.control)
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
                .strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1)
        }
    }
}
