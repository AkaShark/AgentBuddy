import SwiftUI

extension DiscoveryView {
    // MARK: - Connected Computers

    var slingshotHostsSheet: some View {
        NavigationStack {
            List {
                Section {
                    if slingshotIsLoading && slingshotEnvironments.isEmpty {
                        HStack(spacing: BuddySpacing.sm) {
                            ProgressView()
                                .tint(AgentBuddyTheme.textSecondary)
                            Text("Loading connected computers...")
                                .buddyText(.body)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                        .frame(minHeight: BuddySize.control)
                    } else if let slingshotError {
                        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                            Label {
                                Text(slingshotError)
                                    .buddyText(.body)
                                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                                    .fixedSize(horizontal: false, vertical: true)
                            } icon: {
                                Image(systemName: "exclamationmark.triangle")
                                    .foregroundStyle(AgentBuddyTheme.warning)
                                    .accessibilityHidden(true)
                            }
                            BuddyButton("Retry", systemImage: "arrow.clockwise", kind: .secondary) {
                                Task { await loadSlingshotEnvironments() }
                            }
                        }
                        .padding(.vertical, BuddySpacing.xs)
                    } else if slingshotEnvironments.isEmpty {
                        Text("No connected computers were found for this account.")
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .padding(.vertical, BuddySpacing.xs)
                    } else {
                        ForEach(slingshotEnvironments, id: \.id) { environment in
                            Button {
                                showSlingshotHosts = false
                                Task { await connectSlingshotEnvironment(environment) }
                            } label: {
                                slingshotEnvironmentRow(environment)
                            }
                            .buttonStyle(.plain)
                            .disabled(!environment.online)
                        }
                    }
                } header: {
                    Text("Connected Computers")
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .textCase(nil)
                } footer: {
                    Text("These computers come from ChatGPT using your signed-in account. Start Codex on the computer first so it appears here.")
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                .listRowBackground(AgentBuddyTheme.surface)
                .listRowSeparatorTint(AgentBuddyTheme.border)
            }
            .scrollContentBackground(.hidden)
            .buddyPageBackground()
            .navigationTitle("Connected Computers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Refresh") {
                        Task { await loadSlingshotEnvironments() }
                    }
                    .disabled(slingshotIsLoading)
                    .foregroundStyle(AgentBuddyTheme.link)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { showSlingshotHosts = false }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
            .task {
                if slingshotEnvironments.isEmpty && !slingshotIsLoading {
                    await loadSlingshotEnvironments()
                }
            }
        }
        .buddySheetStyle()
    }

    private func slingshotEnvironmentRow(_ environment: AppSlingshotEnvironment) -> some View {
        HStack(spacing: BuddySpacing.md) {
            BuddyIconTile(
                content: .symbol(slingshotIconName(for: environment)),
                foreground: environment.online ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(environment.displayName)
                    .buddyText(.heading)
                    .foregroundStyle(environment.online ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
                    .lineLimit(2)
                Text(slingshotSubtitle(for: environment))
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            BuddyConnectionPill(
                state: environment.online ? (environment.busy ? .connecting : .connected) : .disconnected,
                title: Text(environment.online ? (environment.busy ? "Busy" : "Online") : "Offline")
            )
        }
        .padding(.vertical, BuddySpacing.xs)
        .frame(minHeight: 64)
        .contentShape(Rectangle())
    }

    @MainActor
    private func loadSlingshotEnvironments() async {
        guard !slingshotIsLoading else { return }
        slingshotIsLoading = true
        slingshotError = nil
        defer { slingshotIsLoading = false }

        do {
            let tokens = try await ChatGPTOAuth.loadStoredOrRefreshedTokens()
            let environments = try await appModel.serverBridge.listSlingshotEnvironments(
                baseUrl: slingshotBaseURL,
                accessToken: tokens.accessToken,
                accountId: tokens.accountID
            )
            slingshotEnvironments = environments.sorted { lhs, rhs in
                if lhs.online != rhs.online { return lhs.online && !rhs.online }
                if lhs.busy != rhs.busy { return !lhs.busy && rhs.busy }
                return lhs.displayName.localizedCaseInsensitiveCompare(rhs.displayName) == .orderedAscending
            }
        } catch {
            slingshotError = error.localizedDescription
        }
    }

    @MainActor
    private func connectSlingshotEnvironment(_ environment: AppSlingshotEnvironment) async {
        guard environment.online else {
            connectError = "\(environment.displayName) is offline."
            return
        }
        guard let server = slingshotServer(for: environment) else {
            connectError = "Could not prepare this connected computer."
            return
        }
        await connectToServer(server)
    }

    private func slingshotServer(for environment: AppSlingshotEnvironment) -> DiscoveredServer? {
        guard URL(string: environment.connectionUrl) != nil else {
            return nil
        }
        return DiscoveredServer(
            id: "slingshot-\(environment.id)",
            name: environment.displayName,
            hostname: environment.id,
            port: nil,
            codexPorts: [],
            sshPort: nil,
            source: .manual,
            hasCodexServer: true,
            websocketURL: environment.connectionUrl,
            preferredConnectionMode: .directCodex,
            os: environment.operatingSystem,
            sshBanner: nil
        )
    }

    private func slingshotSubtitle(for environment: AppSlingshotEnvironment) -> String {
        var parts: [String] = []
        if let hostName = environment.hostName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !hostName.isEmpty {
            parts.append(hostName)
        }
        let platform = [environment.operatingSystem, environment.architecture]
            .compactMap { value -> String? in
                let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed?.isEmpty == false ? trimmed : nil
            }
            .joined(separator: " ")
        if !platform.isEmpty {
            parts.append(platform)
        }
        if let version = environment.appServerVersion?.trimmingCharacters(in: .whitespacesAndNewlines),
           !version.isEmpty {
            parts.append("Codex \(version)")
        }
        if parts.isEmpty {
            parts.append(environment.id)
        }
        return parts.joined(separator: " - ")
    }

    private func slingshotIconName(for environment: AppSlingshotEnvironment) -> String {
        switch environment.operatingSystem.lowercased() {
        case "linux":
            return "server.rack"
        case "windows":
            return "desktopcomputer"
        case "macos", "darwin":
            return "desktopcomputer"
        default:
            return "laptopcomputer"
        }
    }
}
