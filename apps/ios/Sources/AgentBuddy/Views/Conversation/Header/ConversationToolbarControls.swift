import SafariServices
import SwiftUI

struct ConversationToolbarControls: View {
    enum Control {
        case reload
        case info
    }

    @Environment(AppState.self) private var appState
    @Environment(AppModel.self) private var appModel
    let thread: AppThreadSnapshot
    let control: Control
    var onInfo: (() -> Void)?
    @State private var isReloading = false
    @State private var remoteAuthSession: RemoteAuthSession?

    private var server: AppServerSnapshot? {
        appModel.snapshot?.serverSnapshot(for: thread.key.serverId)
    }

    var body: some View {
        Group {
            switch control {
            case .reload:
                reloadButton
            case .info:
                infoButton
            }
        }
        .frame(width: 28, height: 28)
        .contentShape(Rectangle())
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .sheet(item: $remoteAuthSession) { session in
            InAppSafariView(url: session.url)
                .ignoresSafeArea()
        }
        .onChange(of: server?.account != nil) { _, isLoggedIn in
            if isLoggedIn {
                remoteAuthSession = nil
            }
        }
    }

    private var reloadButton: some View {
        Button {
            Task {
                isReloading = true
                defer { isReloading = false }
                if await handleRemoteLoginIfNeeded() {
                    return
                }
                if server?.account == nil {
                    appState.showSettings = true
                } else {
                    do {
                        let nextKey = try await appModel.refreshThreadIncludingTurns(key: thread.key)
                        appModel.store.setActiveThread(
                            key: nextKey
                        )
                    } catch {
                        // `AppModel` records the failure; keep the toolbar interaction quiet.
                    }
                }
            }
        } label: {
            reloadButtonLabel
        }
        .accessibilityIdentifier("header.reloadButton")
        .disabled(isReloading || server?.isConnected != true)
    }

    @ViewBuilder
    private var reloadButtonLabel: some View {
        if isReloading {
            ProgressView()
                .scaleEffect(0.7)
                .tint(AgentBuddyTheme.accent)
        } else {
            Image(systemName: "arrow.clockwise")
                .font(AgentBuddyFont.styled(size: 16, weight: .semibold))
                .foregroundColor(server?.isConnected == true ? AgentBuddyTheme.accent : AgentBuddyTheme.textMuted)
        }
    }

    private var infoButton: some View {
        Button {
            onInfo?()
        } label: {
            Image(systemName: "info.circle")
                .font(AgentBuddyFont.styled(size: 16, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.accent)
        }
        .accessibilityIdentifier("header.infoButton")
    }

    private func handleRemoteLoginIfNeeded() async -> Bool {
        guard let server, !server.isLocal else {
            return false
        }
        guard server.account == nil else {
            return false
        }
        do {
            let authURL = try await appModel.client.startRemoteSshOauthLogin(
                serverId: server.serverId
            )
            if let url = URL(string: authURL) {
                await MainActor.run {
                    remoteAuthSession = RemoteAuthSession(url: url)
                }
            }
        } catch {}
        return true
    }
}

private struct RemoteAuthSession: Identifiable {
    let id = UUID()
    let url: URL
}

private struct InAppSafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.dismissButtonStyle = .close
        return controller
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}
