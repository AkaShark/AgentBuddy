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
            ProgressView().tint(AgentBuddyTheme.textSecondary).frame(maxHeight: .infinity)
        } else if let err = model.errorMessage {
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.md) {
                    BuddyIconTile(
                        content: .symbol("exclamationmark.triangle"),
                        fill: AgentBuddyTheme.warningSurface,
                        foreground: AgentBuddyTheme.warning,
                        size: 48
                    )
                    VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                        Text(DirectoryPickerStrings.loadError)
                            .buddyText(.heading)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        Text(err)
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    HStack(spacing: BuddySpacing.sm) {
                        BuddyButton(verbatim: DirectoryPickerStrings.retry, systemImage: "arrow.clockwise") {
                            Task {
                                await model.listDirectory(
                                    for: selectedServerId,
                                    path: model.currentPath,
                                    appModel: appModel,
                                    isLocalServer: selectedServerIsLocal
                                )
                            }
                        }

                        BuddyButton(verbatim: DirectoryPickerStrings.changeServer, kind: .secondary) {
                            selectNextServer()
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .buddyCard(.soft, radius: BuddyRadius.card, padding: BuddySpacing.lg)
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.vertical, BuddySpacing.xl)
            }
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
                        directoryRow(
                            symbol: "play.fill",
                            tileFill: AgentBuddyTheme.brand,
                            tileForeground: AgentBuddyTheme.onBrand,
                            title: DirectoryPickerStrings.continueIn((recent.path as NSString).lastPathComponent),
                            path: recent.path
                        ) { EmptyView() }
                    }
                    .buttonStyle(.plain)
                }
                .listRowBackground(AgentBuddyTheme.surface)
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
                            directoryRow(
                                symbol: "clock.arrow.circlepath",
                                title: (recent.path as NSString).lastPathComponent,
                                path: recent.path
                            ) {
                                Text(model.relativeDate(for: recent.lastUsedAt))
                                    .buddyText(.caption)
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                model.removeRecentEntry(recent, selectedServerId: selectedServerId)
                            } label: {
                                Label(String(localized: "directory_picker_remove_recent"), systemImage: "trash")
                            }
                        }
                        .listRowBackground(AgentBuddyTheme.surface)
                    }
                } header: {
                    HStack {
                        Text(DirectoryPickerStrings.recentDirectories)
                            .buddyText(.caption, weight: .medium)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .textCase(nil)
                        Spacer()
                        Menu {
                            Button(DirectoryPickerStrings.clearRecentDirectories, role: .destructive) {
                                showClearRecentsConfirmation = true
                            }
                        } label: {
                            Image(systemName: "ellipsis.circle")
                                .font(.system(size: 17, weight: .medium))
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel(Text(DirectoryPickerStrings.clearRecentDirectories))
                    }
                } footer: {
                    Text(DirectoryPickerStrings.recentFooter)
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }

            let visibleEntries = model.visibleEntries()
            if visibleEntries.isEmpty {
                Text(model.emptyMessage())
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .padding(.vertical, BuddySpacing.xs)
                    .listRowBackground(AgentBuddyTheme.surface)
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
                        HStack(spacing: BuddySpacing.md) {
                            Image(systemName: "folder")
                                .font(.system(size: 20, weight: .regular))
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .frame(width: 24)
                                .accessibilityHidden(true)
                            Text(entry)
                                .buddyText(.body)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                                .lineLimit(2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.right")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .accessibilityHidden(true)
                        }
                        .frame(minHeight: BuddySize.control)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .listRowBackground(AgentBuddyTheme.surface)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .animation(.easeInOut(duration: 0.2), value: model.recentEntries)
        .accessibilityIdentifier("directoryPicker.list")
    }

    /// Recent / continue row: tile, folder name, full path in code type.
    private func directoryRow<Accessory: View>(
        symbol: String,
        tileFill: Color = AgentBuddyTheme.surfaceSoft,
        tileForeground: Color = AgentBuddyTheme.textPrimary,
        title: String,
        path: String,
        @ViewBuilder accessory: () -> Accessory
    ) -> some View {
        HStack(spacing: BuddySpacing.md) {
            BuddyIconTile(content: .symbol(symbol), fill: tileFill, foreground: tileForeground, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
                Text(PathDisplay.display(path, isLocal: selectedServerIsLocal))
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            accessory()
        }
        .padding(.vertical, BuddySpacing.xxs)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
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
