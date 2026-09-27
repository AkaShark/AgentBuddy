import SwiftUI

extension DiscoveryView {
    func connectViaSSH(
        server: DiscoveredServer,
        host: String,
        credentials: SSHCredentials
    ) async throws -> String {
        let serverId = try await sshConnectAndConnectServer(
            serverId: server.id,
            displayName: server.name,
            host: host,
            credentials: credentials,
            port: server.resolvedSSHPort
        )
        SavedServerStore.remember(
            server.withConnectionPreference(.ssh)
        )
        return serverId
    }

    func startSSHAgentProbe(
        server: DiscoveredServer,
        host: String,
        credentials: SSHCredentials
    ) async {
        connectingServer = server
        connectError = nil
        do {
            let session = try await openSSHSession(
                host: host,
                port: server.resolvedSSHPort,
                credentials: credentials
            )
            let availability = try await appModel.ssh.sshProbeRemoteAgents(sessionId: session.sessionId)
            let bridgeAgents = availability.filter {
                // SSH bridge bootstrap can launch claude / pi / opencode
                // on the remote; everything else (codex, amp, droid,
                // hermes, anything new) only reaches the host via the
                // alleycat pairing path.
                guard $0.status == .available else { return false }
                if let supports = $0.kind.metadata?.capabilities?.supportsSshBridge {
                    return supports && $0.kind != "codex"
                }
                switch $0.kind {
                case "claude", "pi", "opencode": return true
                default: return false
                }
            }
            connectingServer = nil
            guard !bridgeAgents.isEmpty else {
                try? await appModel.ssh.sshClose(sessionId: session.sessionId)
                await connectToServer(server, targetOverride: .sshThenRemote(host: host, credentials: credentials))
                return
            }
            sshAgentContext = SSHBridgeAgentContext(
                server: server,
                sessionId: session.sessionId,
                host: session.normalizedHost,
                availability: availability,
                credentials: credentials
            )
        } catch {
            connectingServer = nil
            if let mismatch = SshHostKeyTrust.promptableHostKey(in: error) {
                presentSSHHostKeyChange(mismatch, server: server, host: host, credentials: credentials)
            } else {
                connectError = error.localizedDescription
            }
        }
    }

    /// Resolves the pending guided SSH connect against the current snapshot:
    /// navigates once connected, or reports the failure (offering "Trust New
    /// Key" / "Forget Saved Host Key" for a typed host-key refusal).
    func handlePendingAutoNavigate() {
        guard let pendingAutoNavigateServerId else { return }
        guard let serverSnapshot = appModel.snapshot?.serverSnapshot(for: pendingAutoNavigateServerId) else {
            return
        }
        if serverSnapshot.health == .connected {
            self.pendingAutoNavigateServerId = nil
            guidedSSHAttempt = nil
            if let server = pendingAutoNavigateServer
                ?? discovery.servers.first(where: { $0.id == pendingAutoNavigateServerId }) {
                self.pendingAutoNavigateServer = nil
                navigateAfterConnect(server)
            }
        } else if serverSnapshot.health == .disconnected,
                  let message = serverSnapshot.connectionProgress?.terminalMessage {
            // Until the guided connect has started, a failure in the snapshot
            // may still be the previous attempt's.
            if let attempt = guidedSSHAttempt, !attempt.started { return }
            self.pendingAutoNavigateServerId = nil
            self.pendingAutoNavigateServer = nil
            let attempt = guidedSSHAttempt
            guidedSSHAttempt = nil
            if let attempt, attempt.server.id == pendingAutoNavigateServerId,
               let mismatch = serverSnapshot.connectionProgress?.hostKeyMismatch,
               mismatch.isPromptable {
                presentSSHHostKeyChange(
                    mismatch,
                    server: attempt.server,
                    host: attempt.host,
                    credentials: attempt.credentials
                )
                return
            }
            connectError = message
        }
    }

    /// Shows the host-key confirmation for a typed Rust mismatch. Trusting
    /// pins the displayed key (or forgetting drops the unreadable saved key)
    /// and reruns the SSH login for `server`.
    private func presentSSHHostKeyChange(
        _ mismatch: AppSshHostKeyMismatch,
        server: DiscoveredServer,
        host: String,
        credentials: SSHCredentials
    ) {
        sshHostKeyChange = SSHHostKeyChangePrompt(mismatch: mismatch) {
            await startSSHAgentProbe(server: server, host: host, credentials: credentials)
        }
    }

    private func openSSHSession(
        host: String,
        port: UInt16,
        credentials: SSHCredentials
    ) async throws -> AppSshSessionResult {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return try await appModel.ssh.sshOpenSession(
                host: host,
                port: port,
                username: username,
                password: password,
                privateKeyPem: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain,
                acceptUnknownHost: true
            )
        case .key(let username, let privateKey, let passphrase):
            return try await appModel.ssh.sshOpenSession(
                host: host,
                port: port,
                username: username,
                password: nil,
                privateKeyPem: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false,
                acceptUnknownHost: true
            )
        }
    }

    func connectSSHBridgeTarget(
        _ result: SSHBridgeAgentResult,
        baseServer: DiscoveredServer
    ) async {
        let synthesized = DiscoveredServer(
            id: result.serverId,
            name: result.displayName,
            hostname: result.host,
            port: nil,
            codexPorts: [],
            sshPort: result.port,
            source: .ssh,
            hasCodexServer: true,
            wakeMAC: baseServer.wakeMAC,
            sshPortForwardingEnabled: false,
            websocketURL: nil,
            preferredConnectionMode: .ssh,
            preferredCodexPort: nil,
            os: baseServer.os,
            sshBanner: baseServer.sshBanner
        )
        SavedServerStore.rememberSSHBridge(synthesized, runtimeKinds: result.runtimeKinds)
        await SshSessionStore.shared.record(sessionId: result.sessionId, for: result.serverId)
        await appModel.refreshSnapshot()
        if appModel.snapshot?.servers.first(where: { $0.serverId == result.serverId })?.health == .connected {
            navigateAfterConnect(synthesized)
        }
    }

    private func sshConnectAndConnectServer(
        serverId: String,
        displayName: String,
        host: String,
        credentials: SSHCredentials,
        port: UInt16
    ) async throws -> String {
        let authMethod: String = switch credentials {
        case .password:
            "password"
        case .key:
            "private_key"
        }
        LLog.trace(
            "discovery",
            "starting guided SSH connect",
            fields: [
                "serverId": serverId,
                "host": host,
                "sshPort": Int(port),
                "authMethod": authMethod
            ]
        )
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

/// The in-flight guided SSH connect, kept so a host-key refusal reported
/// through the server snapshot can offer "Trust New Key" and retry.
struct DiscoveryGuidedSSHAttempt {
    let server: DiscoveredServer
    let host: String
    let credentials: SSHCredentials
    /// The guided connect call returned, so the snapshot no longer holds a
    /// previous attempt's failure.
    var started = false
}
