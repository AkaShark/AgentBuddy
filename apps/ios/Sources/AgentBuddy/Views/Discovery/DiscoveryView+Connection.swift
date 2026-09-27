import SwiftUI

extension DiscoveryView {
    // MARK: - Actions

    func handleTap(_ server: DiscoveredServer) {
        Task { await handleTapAsync(server) }
    }

    func navigateAfterConnect(_ server: DiscoveredServer) {
        guard let snapshot = appModel.snapshot?.servers.first(where: { $0.serverId == server.id }) else {
            onServerSelected?(server)
            return
        }
        if snapshot.isLocal, snapshot.account == nil {
            appState.showSettings = true
            return
        }
        onServerSelected?(server)
    }

    @MainActor
    private func handleTapAsync(_ server: DiscoveredServer) async {
        if appModel.snapshot?.servers.first(where: { $0.serverId == server.id })?.health == .connected {
            navigateAfterConnect(server)
            return
        }

        let prepared = await prepareServerForSelection(server)
        if prepared.server.requiresConnectionChoice {
            connectionChoiceServer = prepared.server
        } else if prepared.server.hasCodexServer, prepared.server.connectionTarget != nil {
            await connectToServer(prepared.server)
        } else if prepared.canAttemptSSH {
            sshServer = prepared.server.withConnectionPreference(.ssh)
        } else {
            connectError = "Server did not respond after wake attempt. Enable Wake for network access on the Mac."
        }
    }

    func connectToServer(_ server: DiscoveredServer, targetOverride: ConnectionTarget? = nil) async {
        guard connectingServer == nil else { return }
        connectingServer = server
        connectError = nil

        guard let target = targetOverride ?? server.connectionTarget else {
            connectError = "Server requires SSH login"
            connectingServer = nil
            return
        }

        let connectedServerId: String
        let startedAsyncBootstrap: Bool
        do {
            switch target {
            case .local:
                startedAsyncBootstrap = false
                connectedServerId = try await appModel.serverBridge.connectLocalServer(
                    serverId: server.id,
                    displayName: server.name,
                    host: "127.0.0.1",
                    port: 0
                )
                await appModel.restoreStoredLocalAuthState(serverId: server.id)
                SavedServerStore.remember(server)
            case .remote(let host, let port):
                startedAsyncBootstrap = false
                connectedServerId = try await appModel.serverBridge.connectRemoteServer(
                    serverId: server.id,
                    displayName: server.name,
                    host: host,
                    port: port
                )
                SavedServerStore.remember(server.withConnectionPreference(.directCodex, codexPort: port))
            case .remoteURL(let url):
                startedAsyncBootstrap = false
                if url.scheme?.lowercased() == "slingshot" {
                    let tokens = try await ChatGPTOAuth.loadStoredOrRefreshedTokens()
                    do {
                        connectedServerId = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                            serverId: server.id,
                            displayName: server.name,
                            connectionUrl: url.absoluteString,
                            accessToken: tokens.accessToken,
                            accountId: tokens.accountID,
                            stepUpToken: ""
                        )
                    } catch {
                        guard ChatGPTOAuth.isRemoteControlAuthorizationRequired(error) else {
                            throw error
                        }
                        let stepUpToken = try await ChatGPTOAuth.remoteControlEnrollmentStepUpToken()
                        connectedServerId = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                            serverId: server.id,
                            displayName: server.name,
                            connectionUrl: url.absoluteString,
                            accessToken: tokens.accessToken,
                            accountId: tokens.accountID,
                            stepUpToken: stepUpToken
                        )
                    }
                } else {
                    connectedServerId = try await appModel.serverBridge.connectRemoteUrlServer(
                        serverId: server.id,
                        displayName: server.name,
                        websocketUrl: url.absoluteString
                    )
                }
                SavedServerStore.remember(server)
            case .sshThenRemote(let host, let credentials):
                startedAsyncBootstrap = true
                // Watch before starting: a fast refusal (e.g. host key) can be
                // published before the guided connect returns.
                guidedSSHAttempt = DiscoveryGuidedSSHAttempt(server: server, host: host, credentials: credentials)
                pendingAutoNavigateServerId = server.id
                pendingAutoNavigateServer = server
                do {
                    connectedServerId = try await connectViaSSH(server: server, host: host, credentials: credentials)
                } catch {
                    guidedSSHAttempt = nil
                    pendingAutoNavigateServerId = nil
                    pendingAutoNavigateServer = nil
                    throw error
                }
            }
        } catch {
            connectingServer = nil
            connectError = error.localizedDescription
            return
        }
        if startedAsyncBootstrap {
            // Rust has now replaced any previous attempt's state with this
            // one, so failures seen from here on belong to this attempt.
            guidedSSHAttempt?.started = true
            pendingAutoNavigateServerId = connectedServerId
        }
        await appModel.refreshSnapshot()

        connectingServer = nil
        if startedAsyncBootstrap {
            handlePendingAutoNavigate()
            return
        }
        if appModel.snapshot?.servers.first(where: { $0.serverId == connectedServerId })?.health == .connected {
            navigateAfterConnect(server)
        } else {
            connectError = "Failed to connect"
        }
    }

    /// Called by `AlleycatAddServerSheet` after the sheet has already opened a
    /// fully connected ServerSession. Persist the stable node/agent metadata
    /// and navigate; the token stays in Keychain.
    func connectAlleycatTarget(_ result: AlleycatConnectedTarget) async {
        let synthesized = DiscoveredServer(
            id: result.serverId,
            name: result.displayName,
            hostname: result.nodeId,
            port: nil,
            codexPorts: [],
            sshPort: nil,
            source: .manual,
            hasCodexServer: true,
            wakeMAC: nil,
            sshPortForwardingEnabled: false,
            websocketURL: nil,
            preferredConnectionMode: nil,
            preferredCodexPort: nil,
            os: nil,
            sshBanner: nil
        )
        SavedServerStore.rememberAlleycat(
            synthesized,
            nodeId: result.nodeId,
            relay: result.params.relay,
            agentName: result.agentName,
            agentWire: alleycatWireStorageValue(result.agentWire)
        )
        await appModel.refreshSnapshot()
        if appModel.snapshot?.servers.first(where: { $0.serverId == result.serverId })?.health == .connected {
            navigateAfterConnect(synthesized)
        }
    }

    private func alleycatWireStorageValue(_ wire: AppAlleycatAgentWire) -> String {
        switch wire {
        case .websocket:
            return "websocket"
        case .jsonl:
            return "jsonl"
        }
    }
}
