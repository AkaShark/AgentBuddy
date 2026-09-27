import SwiftUI

/// 主机 tab: every known host with its connection state, available partners
/// and management actions, plus the entry for pairing another computer.
/// Connection flows (QR, SSH, URL) stay in the existing Discovery screens.
struct HostsHomeView: View {
    let model: HomeDashboardModel
    let actions: HomeShellActions
    let onStartTask: (HomeDashboardServer) -> Void

    @State private var renameTarget: HomeDashboardServer?
    @State private var renameText = ""
    @State private var removeTarget: HomeDashboardServer?
    @State private var showsMountedFolders = false

    private var primaryServer: HomeDashboardServer? {
        if let id = model.selectedServerId, let selected = model.connectedServers.first(where: { $0.id == id }) {
            return selected
        }
        return model.connectedServers.first(where: { $0.health == .connected }) ?? model.connectedServers.first
    }

    var body: some View {
        let primary = primaryServer
        let others = model.connectedServers.filter { $0.id != primary?.id }

        ScrollView {
            VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                BuddyPageHeader(
                    eyebrow: "Connected, anywhere",
                    title: "Your hosts",
                    subtitle: Text("Step away from the computer. The work keeps going.")
                ) {
                    BuddyIconButton(
                        systemImage: "plus",
                        accessibilityLabel: "Add a host",
                        tone: .surface,
                        diameter: 44,
                        action: actions.addServer
                    )
                }

                if let primary {
                    VStack(spacing: BuddySpacing.sm) {
                        HostCard(
                            server: primary,
                            runningCount: runningCount(for: primary),
                            style: .featured,
                            menu: { menuItems(for: primary) },
                            onTap: { actions.selectServer(primary) },
                            onStartTask: { onStartTask(primary) }
                        )
                        ForEach(others) { server in
                            HostCard(
                                server: server,
                                runningCount: runningCount(for: server),
                                style: .compact,
                                menu: { menuItems(for: server) },
                                onTap: { actions.selectServer(server) },
                                onStartTask: { onStartTask(server) }
                            )
                        }
                    }
                }

                addAnotherComputer(isFirst: primary == nil)
            }
            .padding(.horizontal, BuddySpacing.xl)
            .padding(.top, BuddySpacing.lg)
            .padding(.bottom, BuddySpacing.xl)
        }
        .scrollIndicators(.hidden)
        .buddyPageBackground()
        .alert("Rename server", isPresented: Binding(
            get: { renameTarget != nil },
            set: { if !$0 { renameTarget = nil } }
        )) {
            TextField("Server name", text: $renameText)
            Button("Cancel", role: .cancel) { renameTarget = nil }
            Button("Save") {
                let trimmed = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
                if let target = renameTarget, !trimmed.isEmpty {
                    actions.renameServer(target.id, trimmed)
                }
                renameTarget = nil
            }
        }
        .confirmationDialog(
            Text("Remove \(removeTarget?.displayName ?? "")?"),
            isPresented: Binding(get: { removeTarget != nil }, set: { if !$0 { removeTarget = nil } }),
            titleVisibility: .visible
        ) {
            Button("Disconnect and remove", role: .destructive) {
                if let target = removeTarget { actions.removeServer(target.id) }
                removeTarget = nil
            }
        } message: {
            Text("The host forgets this phone's saved connection. Tasks already running on the computer are not stopped.")
        }
        .sheet(isPresented: $showsMountedFolders) {
            MountedFoldersView()
        }
    }

    private func runningCount(for server: HomeDashboardServer) -> Int {
        model.allSessions.filter { $0.serverId == server.id && $0.hasTurnActive }.count
    }

    @ViewBuilder
    private func menuItems(for server: HomeDashboardServer) -> some View {
        Button { actions.reconnectServer(server) } label: {
            Label("Reconnect", systemImage: "arrow.clockwise")
        }
        Button { actions.restartAppServer(server) } label: {
            Label("Restart app server", systemImage: "arrow.triangle.2.circlepath")
        }
        if server.isLocal {
            Button { showsMountedFolders = true } label: {
                Label("Mounted folders", systemImage: "folder.badge.gearshape")
            }
        } else {
            Button {
                renameText = server.displayName
                renameTarget = server
            } label: {
                Label("Rename", systemImage: "pencil")
            }
        }
        Divider()
        Button(role: .destructive) { removeTarget = server } label: {
            Label("Remove", systemImage: "trash")
        }
    }

    private func addAnotherComputer(isFirst: Bool) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            Text(isFirst ? "Connect a computer" : "Add another computer")
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)
            Text("Open AgentBuddy on the computer and scan the pairing QR code it shows.")
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            BuddyButton("Scan to connect", systemImage: "qrcode.viewfinder", kind: .brand, action: actions.pairWithQRCode)
                .padding(.top, BuddySpacing.xxs)
            BuddyButton("Other ways to connect (SSH, address)", kind: .quiet, action: actions.addServer)
        }
    }
}
