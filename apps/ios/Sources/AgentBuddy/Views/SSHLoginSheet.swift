import SwiftUI

struct SSHLoginSheet: View {
    let server: DiscoveredServer
    let onConnect: (ConnectionTarget) -> Void
    private let autoLoadSavedCredentials: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var username: String
    @State private var password = ""
    @State private var isPasswordVisible = false
    @State private var useKey = false
    @State private var privateKey = ""
    @State private var passphrase = ""
    @State private var rememberCredentials = true
    @State private var unlockMacosKeychain = false
    @State private var hasSavedCredentials = false
    @State private var loadedSavedCredentials = false
    @State private var isConnecting = false
    @State private var errorMessage: String?

    init(
        server: DiscoveredServer,
        autoLoadSavedCredentials: Bool = true,
        initialUsername: String = "",
        onConnect: @escaping (ConnectionTarget) -> Void
    ) {
        self.server = server
        self.onConnect = onConnect
        self.autoLoadSavedCredentials = autoLoadSavedCredentials
        _username = State(initialValue: initialUsername)
    }

    private var sshPort: Int {
        Int(server.resolvedSSHPort)
    }

    private var hostDisplay: String {
        if sshPort == 22 {
            return server.hostname
        }
        return "\(server.hostname):\(sshPort)"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    Section {
                        HStack(spacing: 12) {
                            Image(systemName: "terminal")
                                .foregroundColor(AgentBuddyTheme.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(server.name)
                                    .agentBuddyFont(.subheadline)
                                    .foregroundColor(AgentBuddyTheme.textPrimary)
                                Text(hostDisplay)
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    Section {
                        TextField("username", text: $username)
                            .agentBuddyFont(.footnote)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } header: {
                        Text("Username")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    Section {
                        Picker("Method", selection: $useKey) {
                            Text("Password").tag(false)
                            Text("SSH Key").tag(true)
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                        if useKey {
                            TextEditor(text: $privateKey)
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 100)
                                .overlay(alignment: .topLeading) {
                                    if privateKey.isEmpty {
                                        Text("Paste private key here...")
                                            .agentBuddyFont(.caption)
                                            .foregroundColor(AgentBuddyTheme.textMuted)
                                            .padding(.top, 8)
                                            .padding(.leading, 4)
                                            .allowsHitTesting(false)
                                    }
                                }
                            SecureField("passphrase (optional)", text: $passphrase)
                                .agentBuddyFont(.footnote)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                        } else {
                            passwordInput

                            VStack(alignment: .leading, spacing: 4) {
                                Toggle(isOn: $unlockMacosKeychain) {
                                    Text("Unlock keychain (macOS)")
                                        .agentBuddyFont(.footnote)
                                        .foregroundColor(AgentBuddyTheme.textPrimary)
                                }
                                .tint(AgentBuddyTheme.accent)

                                Text("Uses your SSH/login password during headless bootstrap. Required for tools like gh CLI auth.")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                        }
                    } header: {
                        Text("Authentication")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    Section {
                        Toggle(isOn: $rememberCredentials) {
                            Text("Remember credentials on this device")
                                .agentBuddyFont(.footnote)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                        }
                        .tint(AgentBuddyTheme.accent)

                        if hasSavedCredentials {
                            Button(role: .destructive) {
                                forgetSavedCredentials()
                            } label: {
                                Text("Forget saved credentials")
                                    .agentBuddyFont(.footnote)
                            }
                        }
                    } header: {
                        Text("Saved Credentials")
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    Section {
                        Button {
                            connect()
                        } label: {
                            HStack {
                                if isConnecting {
                                    ProgressView().tint(AgentBuddyTheme.accent)
                                }
                                Text("Connect")
                                    .foregroundColor(AgentBuddyTheme.accent)
                                    .agentBuddyFont(.subheadline)
                            }
                        }
                        .disabled(isConnecting || username.isEmpty || (!useKey && password.isEmpty) || (useKey && privateKey.isEmpty))
                    }
                    .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))

                    if let err = errorMessage {
                        Section {
                            Text(err)
                                .foregroundColor(.red)
                                .agentBuddyFont(.caption)
                        }
                        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("SSH Login")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
        .task {
            guard autoLoadSavedCredentials else { return }
            loadSavedCredentialsIfNeeded()
        }
        .onChange(of: useKey) { _, isUsingKey in
            if isUsingKey {
                isPasswordVisible = false
                unlockMacosKeychain = false
            }
        }
    }

    private var passwordInput: some View {
        HStack(spacing: 8) {
            Group {
                if isPasswordVisible {
                    TextField("password", text: $password)
                        .textContentType(.password)
                } else {
                    SecureField("password", text: $password)
                        .textContentType(.password)
                }
            }
            .agentBuddyFont(.footnote)
            .foregroundColor(AgentBuddyTheme.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)

            Button {
                isPasswordVisible.toggle()
            } label: {
                Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                    .foregroundColor(AgentBuddyTheme.textSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPasswordVisible ? "Hide password" : "Show password")
        }
    }

    private func connect() {
        let credentials: SSHCredentials
        if useKey {
            credentials = .key(
                username: username,
                privateKey: privateKey,
                passphrase: passphrase.isEmpty ? nil : passphrase
            )
        } else {
            credentials = .password(
                username: username,
                password: password,
                unlockMacosKeychain: unlockMacosKeychain
            )
        }
        isConnecting = true
        errorMessage = nil

        Task {
            do {
                do {
                    if rememberCredentials {
                        try SSHCredentialStore.shared.save(
                            savedCredential(from: credentials),
                            host: server.hostname,
                            port: sshPort
                        )
                        hasSavedCredentials = true
                    } else {
                        try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
                        hasSavedCredentials = false
                    }
                } catch {
                    NSLog("[SSH_CREDENTIALS] keychain update failed: %@", error.localizedDescription)
                }

                clearSensitiveInput()
                isConnecting = false
                onConnect(.sshThenRemote(host: server.hostname, credentials: credentials))
            } catch {
                isConnecting = false
                errorMessage = error.localizedDescription
            }
        }
    }

    private func loadSavedCredentialsIfNeeded() {
        guard !loadedSavedCredentials else { return }
        loadedSavedCredentials = true

        do {
            guard let saved = try SSHCredentialStore.shared.load(host: server.hostname, port: sshPort) else {
                hasSavedCredentials = false
                return
            }
            hasSavedCredentials = true
            rememberCredentials = true
            username = saved.username
            useKey = saved.method == .key
            if saved.method == .key {
                privateKey = saved.privateKey ?? ""
                passphrase = saved.passphrase ?? ""
                password = ""
                unlockMacosKeychain = false
            } else {
                password = saved.password ?? ""
                privateKey = ""
                passphrase = ""
                unlockMacosKeychain = saved.unlockMacosKeychain ?? false
            }
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to load: %@", error.localizedDescription)
        }
    }

    private func forgetSavedCredentials() {
        do {
            try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
            hasSavedCredentials = false
            rememberCredentials = false
            clearSensitiveInput()
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to delete: %@", error.localizedDescription)
        }
    }

    private func savedCredential(from credentials: SSHCredentials) -> SavedSSHCredential {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return SavedSSHCredential(
                username: username,
                method: .password,
                password: password,
                privateKey: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain
            )
        case .key(let username, let privateKey, let passphrase):
            return SavedSSHCredential(
                username: username,
                method: .key,
                password: nil,
                privateKey: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false
            )
        }
    }

    private func clearSensitiveInput() {
        password = ""
        isPasswordVisible = false
        privateKey = ""
        passphrase = ""
    }
}

#if DEBUG
#Preview("SSH Login") {
    SSHLoginSheet(
        server: AgentBuddyPreviewData.sampleSSHServer,
        autoLoadSavedCredentials: false,
        initialUsername: "builder"
    ) { _ in }
}
#endif
