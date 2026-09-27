import SwiftUI
import UIKit

extension DirectoryPickerView {
    private var showRecentDirectories: Bool {
        model.trimmedSearchQuery.isEmpty && !model.recentEntries.isEmpty
    }

    private var mostRecentEntry: RecentDirectoryEntry? {
        model.recentEntries.first
    }

    @ViewBuilder
    var content: some View {
        if model.isLoading {
            ProgressView().tint(AgentBuddyTheme.accent).frame(maxHeight: .infinity)
        } else if let err = model.errorMessage {
            VStack(spacing: 12) {
                Text(DirectoryPickerStrings.loadError)
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.danger)
                Text(err)
                    .agentBuddyFont(.caption2)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                HStack(spacing: 12) {
                    Button(DirectoryPickerStrings.retry) {
                        Task {
                            await model.listDirectory(
                                for: selectedServerId,
                                path: model.currentPath,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    }
                    .foregroundColor(AgentBuddyTheme.accent)

                    Button(DirectoryPickerStrings.changeServer) {
                        selectNextServer()
                    }
                    .foregroundColor(AgentBuddyTheme.accent)
                }
            }
            .frame(maxHeight: .infinity)
        } else {
            directoryList
        }
    }

    private var directoryList: some View {
        List {
            if let recent = mostRecentEntry {
                Section {
                    Button {
                        emitSuccessHaptic()
                        withAnimation(.easeInOut(duration: 0.16)) {
                            onDirectorySelected?(selectedServerId, recent.path)
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "play.fill")
                                .foregroundColor(AgentBuddyTheme.accent)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(DirectoryPickerStrings.continueIn((recent.path as NSString).lastPathComponent))
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                    .lineLimit(1)
                                Text(PathDisplay.display(recent.path, isLocal: selectedServerIsLocal))
                                    .agentBuddyFont(.caption2)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                                    .lineLimit(1)
                            }
                            Spacer()
                        }
                    }
                }
                .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            }

            if showRecentDirectories {
                Section {
                    ForEach(model.recentEntries) { recent in
                        Button {
                            emitSuccessHaptic()
                            withAnimation(.easeInOut(duration: 0.16)) {
                                onDirectorySelected?(selectedServerId, recent.path)
                            }
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "clock.arrow.circlepath")
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                                    .frame(width: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text((recent.path as NSString).lastPathComponent)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                        .lineLimit(1)
                                    Text(PathDisplay.display(recent.path, isLocal: selectedServerIsLocal))
                                        .agentBuddyFont(.caption2)
                                        .foregroundColor(AgentBuddyTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Text(model.relativeDate(for: recent.lastUsedAt))
                                    .agentBuddyFont(.caption2)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                model.removeRecentEntry(recent, selectedServerId: selectedServerId)
                            } label: {
                                Label(String(localized: "directory_picker_remove_recent"), systemImage: "trash")
                            }
                        }
                        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    }
                } header: {
                    HStack {
                        Text(DirectoryPickerStrings.recentDirectories)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                        Spacer()
                        Menu {
                            Button(DirectoryPickerStrings.clearRecentDirectories, role: .destructive) {
                                showClearRecentsConfirmation = true
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .foregroundColor(AgentBuddyTheme.textMuted)
                        }
                    }
                } footer: {
                    Text(DirectoryPickerStrings.recentFooter)
                        .agentBuddyFont(.caption2)
                        .foregroundColor(AgentBuddyTheme.textMuted)
                }
            }

            let visibleEntries = model.visibleEntries()
            if visibleEntries.isEmpty {
                Text(model.emptyMessage())
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textMuted)
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
            } else {
                ForEach(visibleEntries, id: \.self) { entry in
                    Button {
                        emitSelectionHaptic()
                        Task {
                            await model.navigateInto(
                                entry,
                                selectedServerId: selectedServerId,
                                appModel: appModel,
                                isLocalServer: selectedServerIsLocal
                            )
                        }
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "folder.fill")
                                .foregroundColor(AgentBuddyTheme.accent)
                                .frame(width: 20)
                            Text(entry)
                                .agentBuddyFont(.subheadline)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(AgentBuddyTheme.textMuted)
                                .agentBuddyFont(.caption)
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                }
            }
        }
        .scrollContentBackground(.hidden)
        .animation(.easeInOut(duration: 0.2), value: model.recentEntries)
        .accessibilityIdentifier("directoryPicker.list")
    }

    private func selectNextServer() {
        guard !servers.isEmpty else { return }
        guard let currentIndex = servers.firstIndex(where: { $0.id == selectedServerId }) else {
            selectedServerId = servers[0].id
            return
        }
        let nextIndex = (currentIndex + 1) % servers.count
        selectedServerId = servers[nextIndex].id
    }

    private func emitSelectionHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
