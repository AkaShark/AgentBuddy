import Foundation
import SwiftUI
import os
import Observation

struct DirectoryPathBreadcrumb: Identifiable {
    let id: String
    let label: String
    let path: String
}

private let directoryPickerSignpostLog = OSLog(
    subsystem: Bundle.main.bundleIdentifier ?? "com.akashark.agentbuddy.ios",
    category: "DirectoryPicker"
)

private func isDisconnectedClientError(_ error: Error) -> Bool {
    switch error {
    case let ClientError.Transport(message):
        return message.localizedCaseInsensitiveContains("disconnected")
    case let ClientError.Rpc(message):
        return message.localizedCaseInsensitiveContains("transport error") &&
            message.localizedCaseInsensitiveContains("disconnected")
    default:
        return false
    }
}

@MainActor
@Observable
final class DirectoryPickerSheetModel {
    var currentPath = ""
    var allEntries: [String] = []
    var recentEntries: [RecentDirectoryEntry] = []
    var isLoading = true
    var errorMessage: String?
    var showHiddenDirectories = false
    var searchQuery = ""
    var homePath = ""

    @ObservationIgnored private var lastLoadedServerId = ""

    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .abbreviated
        return formatter
    }()

    var trimmedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var isLocal: Bool = false

    var canNavigateUp: Bool {
        guard !currentPath.isEmpty, !RemotePath.parse(path: currentPath).isRoot() else { return false }
        // Clamp the local picker at the user-facing `~` anchor. Everything
        // above it is iOS container internals the user has no business
        // poking at.
        if isLocal, currentPath == HomeAnchor.path { return false }
        return true
    }

    func visibleEntries() -> [String] {
        let hiddenFiltered = showHiddenDirectories ? allEntries : allEntries.filter { !$0.hasPrefix(".") }
        guard !trimmedSearchQuery.isEmpty else { return hiddenFiltered }
        return hiddenFiltered.filter { $0.localizedCaseInsensitiveContains(trimmedSearchQuery) }
    }

    func emptyMessage() -> String {
        if trimmedSearchQuery.isEmpty {
            return DirectoryPickerStrings.noSubdirectories
        }
        return DirectoryPickerStrings.noMatches(trimmedSearchQuery)
    }

    func pathSegments() -> [DirectoryPathBreadcrumb] {
        let raw = RemotePath.parse(path: currentPath).segments().map {
            DirectoryPathBreadcrumb(id: $0.fullPath, label: $0.label, path: $0.fullPath)
        }
        guard isLocal else { return raw }
        // Hide every breadcrumb above the user-facing `~` anchor and
        // relabel the anchor segment itself to "~" so the trail reads
        // `~ / projects / foo` instead of `var / mobile / … / codex / projects / foo`.
        let home = HomeAnchor.path
        let homeRoot = DirectoryPathBreadcrumb(id: home, label: "~", path: home)
        let suffix = raw.drop { $0.path != home }.dropFirst()
        return [homeRoot] + Array(suffix)
    }

    func relativeDate(for date: Date) -> String {
        Self.relativeFormatter.localizedString(for: date, relativeTo: Date())
    }

    func handleServerSelectionChanged(_ serverId: String) {
        if lastLoadedServerId != serverId {
            searchQuery = ""
            lastLoadedServerId = serverId
        }
        refreshRecentEntries(serverId: serverId)
    }

    func loadInitialPath(
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        self.isLocal = isLocalServer
        let signpostID = OSSignpostID(log: directoryPickerSignpostLog)
        os_signpost(
            .begin,
            log: directoryPickerSignpostLog,
            name: "LoadInitialPath",
            signpostID: signpostID,
            "server=%{public}@",
            selectedServerId
        )
        defer {
            os_signpost(
                .end,
                log: directoryPickerSignpostLog,
                name: "LoadInitialPath",
                signpostID: signpostID
            )
        }

        let targetServerId = selectedServerId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !targetServerId.isEmpty else {
            isLoading = false
            allEntries = []
            errorMessage = DirectoryPickerStrings.noServerSelected
            currentPath = ""
            homePath = ""
            return
        }

        isLoading = true
        errorMessage = nil
        allEntries = []
        currentPath = ""
        homePath = ""

        let home = await resolveHome(for: targetServerId, appModel: appModel, isLocalServer: isLocalServer)
        guard targetServerId == selectedServerId else { return }
        homePath = home
        currentPath = home
        await listDirectory(for: targetServerId, path: home, appModel: appModel, isLocalServer: isLocalServer)
    }

    func listDirectory(
        for serverId: String,
        path: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let signpostID = OSSignpostID(log: directoryPickerSignpostLog)
        os_signpost(
            .begin,
            log: directoryPickerSignpostLog,
            name: "ListDirectory",
            signpostID: signpostID,
            "server=%{public}@ path=%{public}@",
            serverId,
            path
        )
        defer {
            os_signpost(
                .end,
                log: directoryPickerSignpostLog,
                name: "ListDirectory",
                signpostID: signpostID
            )
        }

        guard appModel.snapshot?.servers.first(where: { $0.serverId == serverId })?.canBrowseDirectories == true else {
            if serverId == lastLoadedServerId {
                isLoading = false
                allEntries = []
                errorMessage = DirectoryPickerStrings.serverNotConnected
            }
            return
        }

        let normalizedPath = path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "/" : path
        isLoading = true
        errorMessage = nil

        if isLocalServer {
            await listLocalDirectory(normalizedPath, serverId: serverId)
        } else {
            await listRemoteDirectory(normalizedPath, serverId: serverId, appModel: appModel)
        }

        if serverId == lastLoadedServerId {
            isLoading = false
        }
    }

    private func listLocalDirectory(_ path: String, serverId: String) async {
        // Local paths live inside the iSH fakefs, which iOS-side `FileManager`
        // cannot see. Route directory enumeration through the iSH shell so we
        // get the actual fakefs contents. BusyBox-safe pipeline (no GNU
        // `-printf`).
        let result = await IshFS.run(
            "find \(IshFS.shellQuote(path)) -mindepth 1 -maxdepth 1 -type d 2>/dev/null | awk -F/ '{print $NF}' | sort"
        )
        guard serverId == lastLoadedServerId else { return }
        guard result.exitCode == 0 else {
            errorMessage = result.output.isEmpty
                ? "Couldn't list \(path)"
                : result.output.trimmingCharacters(in: .whitespacesAndNewlines)
            return
        }
        let dirs = result.output
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map(String.init)
        allEntries = dirs
        withAnimation(.easeInOut(duration: 0.2)) {
            currentPath = path
        }
    }

    private func listRemoteDirectory(_ path: String, serverId: String, appModel: AppModel) async {
        do {
            let result = try await appModel.client.listRemoteDirectory(serverId: serverId, path: path)
            guard serverId == lastLoadedServerId else { return }
            allEntries = result.directories
            withAnimation(.easeInOut(duration: 0.2)) {
                currentPath = result.path
            }
        } catch {
            guard serverId == lastLoadedServerId else { return }
            errorMessage = isDisconnectedClientError(error) ?
                DirectoryPickerStrings.serverNotConnected :
                error.localizedDescription
        }
    }

    func navigateInto(
        _ name: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let nextPath = RemotePath.parse(path: currentPath).join(name: name).asString()
        await listDirectory(for: selectedServerId, path: nextPath, appModel: appModel, isLocalServer: isLocalServer)
    }

    func navigateUp(
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        let nextPath = RemotePath.parse(path: currentPath).parent().asString()
        await listDirectory(for: selectedServerId, path: nextPath, appModel: appModel, isLocalServer: isLocalServer)
    }

    func navigateToPath(
        _ path: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async {
        await listDirectory(for: selectedServerId, path: path, appModel: appModel, isLocalServer: isLocalServer)
    }

    /// Create a new subdirectory under `currentPath` and navigate into it.
    /// Returns an error string on failure; nil on success.
    @discardableResult
    func createSubdirectory(
        name: String,
        selectedServerId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async -> String? {
        let trimmed = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/\\"))
        guard !trimmed.isEmpty else { return nil }
        guard !currentPath.isEmpty else {
            return DirectoryPickerStrings.createFolderFailed
        }
        let target = RemotePath.parse(path: currentPath).join(name: trimmed).asString()
        do {
            if isLocalServer {
                let result = await IshFS.run("mkdir -p \(IshFS.shellQuote(target))")
                if result.exitCode != 0 {
                    return result.output.isEmpty
                        ? DirectoryPickerStrings.createFolderFailed
                        : result.output.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } else {
                try await appModel.client.createRemoteDirectory(
                    serverId: selectedServerId,
                    path: target
                )
            }
        } catch {
            return error.localizedDescription
        }
        await listDirectory(
            for: selectedServerId,
            path: target,
            appModel: appModel,
            isLocalServer: isLocalServer
        )
        return nil
    }

    func removeRecentEntry(_ entry: RecentDirectoryEntry, selectedServerId: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            recentEntries = RecentDirectoryStore.shared.remove(path: entry.path, for: selectedServerId, limit: 3)
        }
    }

    func clearRecentEntries(selectedServerId: String) {
        withAnimation(.easeInOut(duration: 0.2)) {
            recentEntries = RecentDirectoryStore.shared.clear(for: selectedServerId)
        }
    }

    private func refreshRecentEntries(serverId: String) {
        recentEntries = RecentDirectoryStore.shared.recentDirectories(for: serverId, limit: 3)
    }

    private func resolveHome(
        for serverId: String,
        appModel: AppModel,
        isLocalServer: Bool
    ) async -> String {
        guard appModel.snapshot?.servers.first(where: { $0.serverId == serverId })?.canBrowseDirectories == true else {
            return "/"
        }
        if isLocalServer {
            return HomeAnchor.path
        }
        do {
            return try await appModel.client.resolveRemoteHome(serverId: serverId)
        } catch {
            if isDisconnectedClientError(error) {
                errorMessage = DirectoryPickerStrings.serverNotConnected
            }
            return "/"
        }
    }

}
