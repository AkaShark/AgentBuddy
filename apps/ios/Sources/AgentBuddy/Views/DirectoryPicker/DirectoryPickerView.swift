import Foundation
import SwiftUI
import UIKit

struct DirectoryPickerServerOption: Identifiable, Hashable {
    let id: String
    let name: String
    let sourceLabel: String
}

struct DirectoryPickerView: View {
    let servers: [DirectoryPickerServerOption]
    @Binding var selectedServerId: String
    var onServerChanged: ((String) -> Void)?
    var onDirectorySelected: ((String, String) -> Void)?
    var onDismissRequested: (() -> Void)?

    @Environment(AppModel.self) var appModel
    @State var model = DirectoryPickerSheetModel()
    @State var showClearRecentsConfirmation = false
    @State var showNewFolderAlert = false
    @State var showGoToPathAlert = false
    @State var newFolderName = ""
    @State var pathInput = ""
    @State private var newFolderError: String?

    var selectedServerOption: DirectoryPickerServerOption? {
        servers.first { $0.id == selectedServerId }
    }

    var selectedServerSnapshot: AppServerSnapshot? {
        appModel.snapshot?.servers.first(where: { $0.serverId == selectedServerId })
    }

    var selectedServerIsLocal: Bool {
        selectedServerSnapshot?.isLocal ?? false
    }

    var canSelectPath: Bool {
        !model.currentPath.isEmpty &&
            selectedServerSnapshot?.canBrowseDirectories == true &&
            selectedServerOption != nil
    }

    var body: some View {
        ZStack {
            AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
            VStack(spacing: 0) {
                controls
                Divider().background(AgentBuddyTheme.separator)
                content
            }
        }
        .safeAreaInset(edge: .bottom) {
            bottomActionBar
        }
        .navigationTitle(DirectoryPickerStrings.title)
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(model.canNavigateUp)
        .task(id: selectedServerId) {
            onServerChanged?(selectedServerId)
            model.handleServerSelectionChanged(selectedServerId)
            await model.loadInitialPath(
                selectedServerId: selectedServerId,
                appModel: appModel,
                isLocalServer: selectedServerIsLocal
            )
        }
        .onChange(of: servers.map(\.id)) { _, ids in
            if !ids.contains(selectedServerId), let fallback = ids.first {
                selectedServerId = fallback
            }
        }
        .confirmationDialog(
            DirectoryPickerStrings.clearRecentTitle,
            isPresented: $showClearRecentsConfirmation,
            titleVisibility: .visible
        ) {
            Button(DirectoryPickerStrings.clear, role: .destructive) {
                model.clearRecentEntries(selectedServerId: selectedServerId)
            }
            Button(DirectoryPickerStrings.cancel, role: .cancel) {}
        } message: {
            Text(DirectoryPickerStrings.clearRecentMessage)
        }
        .alert(DirectoryPickerStrings.newFolderTitle, isPresented: $showNewFolderAlert) {
            TextField(DirectoryPickerStrings.newFolderPlaceholder, text: $newFolderName)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
            Button(DirectoryPickerStrings.cancel, role: .cancel) { newFolderName = "" }
            Button(DirectoryPickerStrings.create) {
                let name = newFolderName
                newFolderName = ""
                Task {
                    if let err = await model.createSubdirectory(
                        name: name,
                        selectedServerId: selectedServerId,
                        appModel: appModel,
                        isLocalServer: selectedServerIsLocal
                    ) {
                        newFolderError = err
                    } else {
                        emitSuccessHaptic()
                    }
                }
            }
        } message: {
            Text(PathDisplay.display(model.currentPath, isLocal: selectedServerIsLocal))
        }
        .alert(DirectoryPickerStrings.goToPathTitle, isPresented: $showGoToPathAlert) {
            TextField(DirectoryPickerStrings.pathPlaceholder, text: $pathInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
            Button(DirectoryPickerStrings.cancel, role: .cancel) { pathInput = "" }
            Button(DirectoryPickerStrings.go) {
                let target = PathDisplay.expand(
                    pathInput,
                    isLocal: selectedServerIsLocal,
                    remoteHome: model.homePath
                )
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                pathInput = ""
                guard !target.isEmpty else { return }
                Task {
                    await model.navigateToPath(
                        target,
                        selectedServerId: selectedServerId,
                        appModel: appModel,
                        isLocalServer: selectedServerIsLocal
                    )
                }
            }
        }
        .alert(DirectoryPickerStrings.createFolderFailed, isPresented: Binding(
            get: { newFolderError != nil },
            set: { if !$0 { newFolderError = nil } }
        )) {
            Button("OK", role: .cancel) { newFolderError = nil }
        } message: {
            Text(newFolderError ?? "")
        }
    }

    private var bottomActionBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !model.currentPath.isEmpty {
                Text(PathDisplay.display(model.currentPath, isLocal: selectedServerIsLocal))
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else if !canSelectPath {
                Text(DirectoryPickerStrings.chooseFolderHelper)
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: 10) {
                Button(DirectoryPickerStrings.cancel) {
                    onDismissRequested?()
                }
                .buttonStyle(.plain)
                .agentBuddyFont(.subheadline)
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(AgentBuddyTheme.surface.opacity(0.65))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(AgentBuddyTheme.border.opacity(0.75), lineWidth: 1)
                )
                .cornerRadius(8)

                Button(DirectoryPickerStrings.selectFolder) {
                    emitSuccessHaptic()
                    withAnimation(.easeInOut(duration: 0.16)) {
                        onDirectorySelected?(selectedServerId, model.currentPath)
                    }
                }
                .accessibilityIdentifier("directoryPicker.selectFolderButton")
                .disabled(!canSelectPath)
                .buttonStyle(.plain)
                .agentBuddyFont(.subheadline)
                .foregroundColor(canSelectPath ? AgentBuddyTheme.textOnAccent : AgentBuddyTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(canSelectPath ? AgentBuddyTheme.accent : AgentBuddyTheme.surface.opacity(0.65))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(canSelectPath ? AgentBuddyTheme.accent.opacity(0.8) : AgentBuddyTheme.border.opacity(0.75), lineWidth: 1)
                )
                .cornerRadius(8)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
    }

    func emitSuccessHaptic() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
}

#if DEBUG
#Preview("Directory Picker") {
    NavigationStack {
        DirectoryPickerView(
            servers: [],
            selectedServerId: .constant(""),
            onDismissRequested: {}
        )
        .environment(AgentBuddyPreviewData.makeDiscoveryAppModel())
    }
}
#endif
