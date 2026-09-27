import SwiftUI

extension DiscoveryView {
    // MARK: - Connected Computers

    var slingshotHostsSheet: some View {
        NavigationStack {
            ZStack {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                List {
                    Section {
                        if slingshotIsLoading && slingshotEnvironments.isEmpty {
                            HStack(spacing: 10) {
                                ProgressView()
                                    .tint(AgentBuddyTheme.accent)
                                Text("Loading connected computers...")
                                    .agentBuddyFont(.footnote)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        } else if let slingshotError {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(slingshotError)
                                    .agentBuddyFont(.footnote)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                                Button("Retry") {
                                    Task { await loadSlingshotEnvironments() }
                                }
                                .foregroundColor(AgentBuddyTheme.accent)
                                .agentBuddyFont(.footnote, weight: .semibold)
                            }
                        } else if slingshotEnvironments.isEmpty {
                            Text("No connected computers were found for this account.")
                                .agentBuddyFont(.footnote)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
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
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    } footer: {
                        Text("These computers come from ChatGPT using your signed-in account. Start Codex on the computer first so it appears here.")
                            .agentBuddyFont(.caption2)
                            .foregroundColor(AgentBuddyTheme.textMuted)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Connected Computers")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Refresh") {
                        Task { await loadSlingshotEnvironments() }
                    }
                    .disabled(slingshotIsLoading)
                    .foregroundColor(AgentBuddyTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { showSlingshotHosts = false }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
            .task {
                if slingshotEnvironments.isEmpty && !slingshotIsLoading {
                    await loadSlingshotEnvironments()
                }
            }
        }
    }

    private func slingshotEnvironmentRow(_ environment: AppSlingshotEnvironment) -> some View {
        HStack(spacing: 12) {
            Image(systemName: slingshotIconName(for: environment))
                .foregroundColor(environment.online ? AgentBuddyTheme.accent : AgentBuddyTheme.textMuted)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(environment.displayName)
                    .agentBuddyFont(.subheadline)
                    .foregroundColor(environment.online ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
                Text(slingshotSubtitle(for: environment))
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }
            Spacer()
            statusTag(
                label: environment.online ? (environment.busy ? "busy" : "online") : "offline",
                color: environment.online ? (environment.busy ? .orange : AgentBuddyTheme.accent) : AgentBuddyTheme.textMuted
            )
        }
        .padding(.vertical, 2)
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
