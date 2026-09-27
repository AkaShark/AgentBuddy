import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) var appModel
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.textScale) private var textScale
    @AppStorage("fontFamily") var fontFamily = FontFamilyOption.defaultOption.rawValue
    @AppStorage("collapseTurns") var collapseTurns = false
    @AppStorage(ConversationDisplayPreferenceKey.reasoning) var reasoningDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.commands) var commandDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.tools) var toolDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @State var activeServerSheet: SettingsServerSheet?
    @State var serverEditError: String?
    @State var sshHostKeyChange: SSHHostKeyChangePrompt?
    @State var pendingSSHReconnect: SettingsSSHReconnectAttempt?

    var localServer: AppServerSnapshot? {
        // Account management (ChatGPT login / API key) is local-only, always.
        // If the local Codex bridge hasn't spun up there's no login target, and
        // the caller falls through to `SettingsDisconnectedAccountSection`.
        appModel.snapshot?.servers.first(where: \.isLocal)
    }

    var connectedServers: [HomeDashboardServer] {
        HomeDashboardSupport.sortedConnectedServers(
            from: appModel.snapshot?.servers ?? [],
            savedServers: SavedServerStore.rememberedServers(),
            activeServerId: appModel.snapshot?.activeThread?.serverId
        )
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AgentBuddyTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    appearanceSection
                    fontSection
                    conversationSection
                    petSection
                    experimentalSection
                    accountSection
                    serversSection
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
            .sheet(item: $activeServerSheet) { sheet in
                switch sheet {
                case .add:
                    NavigationStack {
                        DiscoveryView(onServerSelected: { _ in
                            activeServerSheet = nil
                        })
                    }
                    .environment(appModel)
                    .environment(appState)
                    .environment(\.textScale, textScale)
                case .edit(let server):
                    SettingsServerConnectionEditor(
                        server: server,
                        onSave: { configuration in
                            saveServerConfiguration(configuration, reconnect: false)
                            activeServerSheet = nil
                        },
                        onReconnect: { configuration in
                            activeServerSheet = nil
                            saveServerConfiguration(configuration, reconnect: true)
                        }
                    )
                    .environment(\.textScale, textScale)
                case .sshReconnect(let server):
                    SSHLoginSheet(server: server) { target in
                        activeServerSheet = nil
                        if case .sshThenRemote(let host, let credentials) = target {
                            Task { await reconnectViaSSH(server: server, host: host, credentials: credentials) }
                        }
                    }
                }
            }
            .onChange(of: appModel.snapshot) { _, snapshot in
                handleSSHReconnectProgress(snapshot)
            }
            .sshHostKeyChangeAlert($sshHostKeyChange)
            .alert("Server Update Failed", isPresented: Binding(
                get: { serverEditError != nil },
                set: { if !$0 { serverEditError = nil } }
            )) {
                Button("OK") { serverEditError = nil }
            } message: {
                Text(serverEditError ?? "Unable to update this server.")
            }
        }
    }
}

#if DEBUG
#Preview("Settings") {
    AgentBuddyPreviewScene(includeBackground: false) {
        SettingsView()
    }
}
#endif
