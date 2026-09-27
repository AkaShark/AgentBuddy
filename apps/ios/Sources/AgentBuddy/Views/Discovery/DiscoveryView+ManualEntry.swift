import SwiftUI

extension DiscoveryView {
    // MARK: - Manual Entry

    var manualEntrySheet: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                    DiscoveryFormSection("Connection") {
                        DiscoverySegmentedPicker(
                            options: DiscoveryManualConnectionMode.allCases.map { ($0, LocalizedStringKey($0.label)) },
                            selection: $manualConnectionMode
                        )
                    }

                    DiscoveryFormSection(
                        LocalizedStringKey(manualConnectionMode.formHeader),
                        footer: manualConnectionMode == .codex ? Text(Self.manualCodexSafetyNote) : nil
                    ) {
                        if manualConnectionMode == .codex {
                            TextField("ws://host:port or wss://...", text: $manualCodexURL)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled(true)
                                .keyboardType(.URL)
                                .discoveryFieldStyle()
                        } else {
                            VStack(spacing: BuddySpacing.sm) {
                                TextField("hostname or IP", text: $manualHost)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .discoveryFieldStyle()
                                TextField("ssh port", text: $manualSSHPort)
                                    .keyboardType(.numberPad)
                                    .discoveryFieldStyle()
                                TextField("wake MAC (optional)", text: $manualWakeMAC)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled(true)
                                    .discoveryFieldStyle()
                            }
                        }
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.md)
                .padding(.bottom, BuddySpacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
            .buddyPageBackground()
            .safeAreaInset(edge: .bottom) {
                BuddyButton(LocalizedStringKey(manualConnectionMode.primaryButtonTitle)) {
                    submitManualEntry()
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.vertical, BuddySpacing.sm)
                .background(AgentBuddyTheme.background)
            }
            .navigationTitle("SSH or address")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cancel") { showManualEntry = false }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
        .buddySheetStyle()
    }

    private static let manualCodexSafetyNote: LocalizedStringKey = "Prefer the SSH flow — it bootstraps codex on the remote bound to 127.0.0.1 and forwards the port over SSH.\nIf you run it manually, bind loopback and tunnel yourself: codex app-server --listen ws://127.0.0.1:8390\nFor reverse proxies: wss://example.com/ws?token=SECRET\nDo not bind 0.0.0.0 or expose directly to the internet unless you know what you are doing."

    func maybeStartSimulatorAutoSSH() {
#if DEBUG
        guard !autoSSHStarted else { return }
        let env = ProcessInfo.processInfo.environment
        guard env["CODEXIOS_SIM_AUTO_SSH"] == "1",
              let host = env["CODEXIOS_SIM_AUTO_SSH_HOST"], !host.isEmpty,
              let user = env["CODEXIOS_SIM_AUTO_SSH_USER"], !user.isEmpty else {
            return
        }
        let password = env["CODEXIOS_SIM_AUTO_SSH_PASS"]
        let keyPath = env["CODEXIOS_SIM_AUTO_SSH_KEY_PATH"]
        let keyPem: String? = keyPath.flatMap { path -> String? in
            guard !path.isEmpty else { return nil }
            return try? String(contentsOfFile: path, encoding: .utf8)
        }
        guard (password?.isEmpty == false) || (keyPem?.isEmpty == false) else { return }
        autoSSHStarted = true

        Task {
            NSLog("[AUTO_SSH] connecting to %@ as %@ (method=%@)", host, user, keyPem == nil ? "password" : "key")
            let server = DiscoveredServer(
                id: "auto-ssh-\(host)",
                name: host,
                hostname: host,
                port: nil,
                sshPort: 22,
                source: .ssh,
                hasCodexServer: false,
                sshPortForwardingEnabled: false,
                preferredConnectionMode: .ssh
            )
            let credentials: SSHCredentials
            if let keyPem, !keyPem.isEmpty {
                credentials = .key(
                    username: user,
                    privateKey: keyPem,
                    passphrase: env["CODEXIOS_SIM_AUTO_SSH_PASSPHRASE"]
                )
            } else {
                credentials = .password(
                    username: user,
                    password: password ?? "",
                    unlockMacosKeychain: false
                )
            }
            await connectToServer(
                server,
                targetOverride: .sshThenRemote(
                    host: host,
                    credentials: credentials
                )
            )
        }
#endif
    }

    private func submitManualEntry() {
        switch manualConnectionMode {
        case .codex:
            submitManualCodexEntry()
        case .ssh:
            submitManualSSHEntry()
        }
    }

    private func submitManualCodexEntry() {
        let raw = manualCodexURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else { return }

        // Full URL: ws:// or wss://
        if let url = URL(string: raw),
           let scheme = url.scheme?.lowercased(),
           (scheme == "ws" || scheme == "wss"),
           let host = url.host, !host.isEmpty {
            let port = url.port.flatMap { UInt16(exactly: $0) }
            let server = DiscoveredServer(
                id: "manual-url-\(raw)",
                name: host,
                hostname: host,
                port: port,
                codexPorts: port.map { [$0] } ?? [],
                sshPort: nil,
                source: .manual,
                hasCodexServer: true,
                websocketURL: raw,
                preferredConnectionMode: .directCodex,
                preferredCodexPort: port
            )
            showManualEntry = false
            Task { await connectToServer(server) }
            return
        }

        // Bare host:port (e.g. "192.168.1.5:8390" or "myhost:8390")
        let parts = raw.split(separator: ":", maxSplits: 1)
        let host: String
        let port: UInt16
        if parts.count == 2, let p = UInt16(parts[1]) {
            host = String(parts[0])
            port = p
        } else if parts.count == 1 {
            host = raw
            port = 8390
        } else {
            connectError = "Enter a ws:// URL or host:port"
            return
        }

        guard !host.isEmpty else { return }
        let server = DiscoveredServer(
            id: "manual-\(host):\(port)",
            name: host,
            hostname: host,
            port: port,
            codexPorts: [port],
            sshPort: nil,
            source: .manual,
            hasCodexServer: true,
            preferredConnectionMode: .directCodex,
            preferredCodexPort: port
        )
        showManualEntry = false
        Task { await connectToServer(server) }
    }

    private func submitManualSSHEntry() {
        let host = manualHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty else { return }

        let wakeInput = manualWakeMAC.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedWakeMAC = DiscoveredServer.normalizeWakeMAC(wakeInput)
        if !wakeInput.isEmpty && normalizedWakeMAC == nil {
            connectError = "Wake MAC must look like aa:bb:cc:dd:ee:ff"
            return
        }

        guard let sshPort = UInt16(manualSSHPort) else {
            connectError = "SSH port must be a valid number"
            return
        }
        pendingSSHServer = DiscoveredServer(
            id: "manual-ssh-\(host):\(sshPort)",
            name: host,
            hostname: host,
            port: nil,
            sshPort: sshPort,
            source: .manual,
            hasCodexServer: false,
            wakeMAC: normalizedWakeMAC,
            preferredConnectionMode: .ssh
        )
        showManualEntry = false
    }
}

enum DiscoveryManualConnectionMode: String, CaseIterable, Identifiable {
    case codex
    case ssh

    var id: String { rawValue }

    var label: String {
        switch self {
        case .codex:
            return "Codex"
        case .ssh:
            return "SSH"
        }
    }

    var formHeader: String {
        switch self {
        case .codex:
            return "Codex Server"
        case .ssh:
            return "SSH Bootstrap"
        }
    }

    var primaryButtonTitle: String {
        switch self {
        case .codex:
            return "Connect"
        case .ssh:
            return "Continue to SSH Login"
        }
    }
}
