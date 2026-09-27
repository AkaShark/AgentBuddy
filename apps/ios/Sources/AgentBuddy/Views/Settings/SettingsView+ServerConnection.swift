import SwiftUI

extension SettingsView {
    func saveServerConfiguration(
        _ configuration: SettingsServerConnectionConfiguration,
        reconnect: Bool
    ) {
        var saved = SavedServerStore.load()
        if let index = saved.firstIndex(where: { $0.id == configuration.savedServer.id }) {
            saved[index] = configuration.savedServer
        } else {
            saved.append(configuration.savedServer)
        }
        SavedServerStore.save(saved)
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(
            servers: SavedServerStore.reconnectRecords(
                localDisplayName: appModel.resolvedLocalServerDisplayName()
            )
        )
        appModel.store.renameServer(
            serverId: configuration.savedServer.id,
            displayName: configuration.savedServer.name
        )

        guard reconnect else { return }
        reconnectServer(using: configuration)
    }

    private func reconnectServer(using configuration: SettingsServerConnectionConfiguration) {
        let server = configuration.discoveredServer

        // For SSH we keep the existing connection alive until the user actually
        // submits credentials, so a cancelled credential sheet does not leave
        // them disconnected.
        if case .ssh = configuration.connectionMode {
            activeServerSheet = .sshReconnect(server)
            return
        }

        Task {
            await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh)
            appModel.serverBridge.disconnectServer(serverId: server.id)

            do {
                switch configuration.connectionMode {
                case .local:
                    try await appModel.restartLocalServer()
                case .directCodex:
                    guard let port = server.resolvedDirectCodexPort else {
                        throw SettingsServerConnectionError.missingCodexPort
                    }
                    _ = try await appModel.serverBridge.connectRemoteServer(
                        serverId: server.id,
                        displayName: server.name,
                        host: server.hostname,
                        port: port
                    )
                    await appModel.refreshSnapshot()
                case .websocket:
                    guard let websocketURL = server.websocketURL else {
                        throw SettingsServerConnectionError.invalidWebsocketURL
                    }
                    if isSettingsSlingshotURL(websocketURL) {
                        let tokens = try await ChatGPTOAuth.loadStoredOrRefreshedTokens()
                        do {
                            _ = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                                serverId: server.id,
                                displayName: server.name,
                                connectionUrl: websocketURL,
                                accessToken: tokens.accessToken,
                                accountId: tokens.accountID,
                                stepUpToken: ""
                            )
                        } catch {
                            guard ChatGPTOAuth.isRemoteControlAuthorizationRequired(error) else {
                                throw error
                            }
                            let stepUpToken = try await ChatGPTOAuth.remoteControlEnrollmentStepUpToken()
                            _ = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                                serverId: server.id,
                                displayName: server.name,
                                connectionUrl: websocketURL,
                                accessToken: tokens.accessToken,
                                accountId: tokens.accountID,
                                stepUpToken: stepUpToken
                            )
                        }
                    } else {
                        _ = try await appModel.serverBridge.connectRemoteUrlServer(
                            serverId: server.id,
                            displayName: server.name,
                            websocketUrl: websocketURL
                        )
                    }
                    await appModel.refreshSnapshot()
                case .ssh:
                    break
                }
            } catch {
                serverEditError = error.localizedDescription
            }
        }
    }

    func reconnectViaSSH(
        server: DiscoveredServer,
        host: String,
        credentials: SSHCredentials
    ) async {
        await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh)
        appModel.serverBridge.disconnectServer(serverId: server.id)
        // Publish the removal, then watch before starting: the guided connect
        // can fail (and publish its snapshot) before `startRemoteOverSSH`
        // returns, and the watcher must never see the previous attempt's state.
        await appModel.refreshSnapshot()
        pendingSSHReconnect = SettingsSSHReconnectAttempt(
            server: server,
            host: host,
            credentials: credentials
        )
        do {
            _ = try await startRemoteOverSSH(
                serverId: server.id,
                displayName: server.name,
                host: host,
                port: server.resolvedSSHPort,
                credentials: credentials
            )
            await appModel.refreshSnapshot()
        } catch {
            pendingSSHReconnect = nil
            serverEditError = error.localizedDescription
        }
    }

    /// Watches the guided SSH reconnect started from Settings; a refusal
    /// because the host key changed offers "Trust New Key" (or, for an
    /// unreadable saved key, "Forget Saved Host Key") and retries. Any other
    /// failure is shown as an error.
    func handleSSHReconnectProgress(_ snapshot: AppSnapshotRecord?) {
        guard let attempt = pendingSSHReconnect,
              let serverSnapshot = snapshot?.serverSnapshot(for: attempt.server.id) else {
            return
        }
        if serverSnapshot.health == .connected {
            pendingSSHReconnect = nil
            return
        }
        guard serverSnapshot.health == .disconnected,
              let progress = serverSnapshot.connectionProgress,
              let message = progress.terminalMessage else {
            return
        }
        pendingSSHReconnect = nil
        guard let mismatch = progress.hostKeyMismatch, mismatch.isPromptable else {
            serverEditError = message
            return
        }
        sshHostKeyChange = SSHHostKeyChangePrompt(mismatch: mismatch) {
            await reconnectViaSSH(
                server: attempt.server,
                host: attempt.host,
                credentials: attempt.credentials
            )
        }
    }

    private func startRemoteOverSSH(
        serverId: String,
        displayName: String,
        host: String,
        port: UInt16,
        credentials: SSHCredentials
    ) async throws -> String {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return try await appModel.serverBridge.startRemoteOverSshConnect(
                serverId: serverId,
                displayName: displayName,
                host: host,
                port: port,
                username: username,
                password: password,
                privateKeyPem: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain,
                acceptUnknownHost: true,
                workingDir: nil
            )
        case .key(let username, let privateKey, let passphrase):
            return try await appModel.serverBridge.startRemoteOverSshConnect(
                serverId: serverId,
                displayName: displayName,
                host: host,
                port: port,
                username: username,
                password: nil,
                privateKeyPem: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false,
                acceptUnknownHost: true,
                workingDir: nil
            )
        }
    }
}

private func isSettingsSlingshotURL(_ rawURL: String) -> Bool {
    URL(string: rawURL)?.scheme?.lowercased() == "slingshot"
}
