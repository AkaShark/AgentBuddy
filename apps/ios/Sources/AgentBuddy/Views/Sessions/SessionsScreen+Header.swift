import SwiftUI

extension SessionsScreen {
    var newSessionButton: some View {
        Button {
            if let projectScope {
                // A project's page starts the task in that project's folder.
                Task { await startNewSession(serverId: projectScope.serverId, cwd: projectScope.cwd) }
            } else if let defaultServerId = defaultNewSessionServerId(preferredServerId: appState.sessionsSelectedServerFilterId) {
                if connectedServers.first(where: { $0.id == defaultServerId })?.isLocal == true {
                    let cwd = AgentBuddyPlatform.defaultLocalWorkingDirectory()
                    Task { await startNewSession(serverId: defaultServerId, cwd: cwd) }
                } else {
                    directoryPickerSheet = SessionLaunchSupport.DirectoryPickerSheetModel(selectedServerId: defaultServerId)
                }
            } else {
                appState.showServerPicker = true
            }
        } label: {
            HStack(spacing: BuddySpacing.xs) {
                if !isStartingNewSession {
                    Image(systemName: "plus")
                        .imageScale(.medium)
                        .accessibilityHidden(true)
                }
                Text("New task")
            }
        }
        .buttonStyle(BuddyButtonStyle(kind: .primary, isLoading: isStartingNewSession))
        .disabled(isStartingNewSession)
        .keyboardShortcut("n", modifiers: [.command])
        .accessibilityIdentifier("sessions.newSessionButton")
    }

    var refreshToolbarButton: some View {
        Button(action: refreshSessions) {
            Group {
                if isLoading && hasLoadedInitialSessions {
                    ProgressView()
                        .controlSize(.small)
                        .tint(AgentBuddyTheme.textSecondary)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(connectedServers.isEmpty ? AgentBuddyTheme.onDisabled : AgentBuddyTheme.textPrimary)
                }
            }
        }
        .disabled(isLoading || connectedServers.isEmpty)
        .accessibilityLabel("Refresh sessions")
        .accessibilityIdentifier("sessions.refreshButton")
    }

    /// Host summary: a connection pill (dot + text) with quiet text actions to
    /// add a host or fork the task that is open right now.
    var serversRow: some View {
        let connected = connectedServers
        let activeThread = sessionsModel.derivedData.allThreads.first(where: { $0.key == activeThreadKey })
        let activeThreadEphemeralState = activeThread.flatMap { ephemeralStateByThreadKey[$0.key] }

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: BuddySpacing.xs) {
                connectionPill(connected: connected)
                Spacer(minLength: BuddySpacing.xs)
                serversRowActions(
                    connected: connected,
                    activeThread: activeThread,
                    activeThreadEphemeralState: activeThreadEphemeralState
                )
            }
            VStack(alignment: .leading, spacing: 0) {
                connectionPill(connected: connected)
                    .padding(.vertical, BuddySpacing.xxs)
                HStack(spacing: BuddySpacing.md) {
                    serversRowActions(
                        connected: connected,
                        activeThread: activeThread,
                        activeThreadEphemeralState: activeThreadEphemeralState
                    )
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func connectionPill(connected: [HomeDashboardServer]) -> some View {
        BuddyConnectionPill(
            state: connected.isEmpty ? .disconnected : .connected,
            title: connected.isEmpty ? Text("Not connected") : Text("\(connected.count) hosts online")
        )
        .fixedSize()
    }

    @ViewBuilder
    private func serversRowActions(
        connected: [HomeDashboardServer],
        activeThread: AppSessionSummary?,
        activeThreadEphemeralState: SessionsModel.ThreadEphemeralState?
    ) -> some View {
        if connected.isEmpty {
            Button {
                appState.showServerPicker = true
            } label: {
                quietActionLabel(Text("Connect"), isEnabled: true)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("sessions.connectButton")
            .hoverEffect(.highlight)
        } else {
            Button {
                appState.showServerPicker = true
            } label: {
                quietActionLabel(Text("Add a host"), isEnabled: true)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("sessions.addServerButton")
            .hoverEffect(.highlight)

            if let activeThread {
                let isTurnActive = activeThreadEphemeralState?.hasTurnActive ?? activeThread.hasActiveTurn
                Button {
                    Task { await forkThread(activeThread) }
                } label: {
                    HStack(spacing: BuddySpacing.xxs) {
                        if isForkingActiveThread {
                            ProgressView()
                                .controlSize(.small)
                                .tint(AgentBuddyTheme.link)
                        }
                        quietActionLabel(Text("Fork current task"), isEnabled: !isTurnActive)
                    }
                }
                .buttonStyle(.plain)
                .disabled(isForkingActiveThread || isTurnActive)
                .hoverEffect(.highlight)
            }
        }
    }

    private func quietActionLabel(_ title: Text, isEnabled: Bool) -> some View {
        title
            .buddyText(.label, weight: .semibold)
            .foregroundStyle(isEnabled ? AgentBuddyTheme.link : AgentBuddyTheme.onDisabled)
            .lineLimit(1)
            .fixedSize()
            .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
    }
}
