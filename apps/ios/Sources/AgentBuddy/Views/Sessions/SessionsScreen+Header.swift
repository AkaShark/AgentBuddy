import SwiftUI

extension SessionsScreen {
    var newSessionButton: some View {
        Button {
            if let defaultServerId = defaultNewSessionServerId(preferredServerId: appState.sessionsSelectedServerFilterId) {
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
            HStack {
                if isStartingNewSession {
                    ProgressView()
                        .controlSize(.small)
                        .tint(AgentBuddyTheme.textOnAccent)
                } else {
                    Image(systemName: "plus")
                        .agentBuddyFont(.subheadline, weight: .medium)
                    Text("New Session")
                        .agentBuddyFont(.subheadline)
                }
            }
            .foregroundColor(AgentBuddyTheme.textOnAccent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(AgentBuddyTheme.accent)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .disabled(isStartingNewSession)
        // Mac builds (Catalyst + iOS-on-Mac) bind Cmd+N at the menu
        // level via `MacCommands`; the in-view shortcut would either
        // double-bind (Catalyst) or be the only binding (iOS-on-Mac
        // doesn't get menus, so we keep it on then).
        .keyboardShortcut(AgentBuddyPlatform.isCatalyst ? nil : KeyboardShortcut("n", modifiers: [.command]))
        .accessibilityIdentifier("sessions.newSessionButton")
        .padding(isRegularSurface ? 12 : 16)
    }

    private var isRegularSurface: Bool {
        AgentBuddyPlatform.isRegularSurface(horizontalSizeClass: horizontalSizeClass)
    }

    var refreshToolbarButton: some View {
        Button(action: refreshSessions) {
            Group {
                if isLoading && hasLoadedInitialSessions {
                    ProgressView()
                        .controlSize(.small)
                        .tint(AgentBuddyTheme.accent)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .agentBuddyFont(.subheadline, weight: .semibold)
                        .foregroundColor(connectedServers.isEmpty ? AgentBuddyTheme.textMuted : AgentBuddyTheme.accent)
                }
            }
        }
        .disabled(isLoading || connectedServers.isEmpty)
        .accessibilityLabel("Refresh sessions")
        .accessibilityIdentifier("sessions.refreshButton")
    }

    var serversRow: some View {
        let connected = connectedServers
        let activeThread = sessionsModel.derivedData.allThreads.first(where: { $0.key == activeThreadKey })
        let activeThreadEphemeralState = activeThread.flatMap { ephemeralStateByThreadKey[$0.key] }

        return ViewThatFits(in: .horizontal) {
            serversRowContent(
                connected: connected,
                activeThread: activeThread,
                activeThreadEphemeralState: activeThreadEphemeralState,
                useSpacer: true
            )
            ScrollView(.horizontal, showsIndicators: false) {
                serversRowContent(
                    connected: connected,
                    activeThread: activeThread,
                    activeThreadEphemeralState: activeThreadEphemeralState,
                    useSpacer: false
                )
            }
        }
        .padding(.horizontal, isRegularSurface ? 12 : 16)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func serversRowContent(
        connected: [HomeDashboardServer],
        activeThread: AppSessionSummary?,
        activeThreadEphemeralState: SessionsModel.ThreadEphemeralState?,
        useSpacer: Bool
    ) -> some View {
        HStack(spacing: 10) {
            if connected.isEmpty {
                Image(systemName: "xmark.circle")
                    .foregroundColor(AgentBuddyTheme.textMuted)
                    .frame(width: 20)
                Text("Not connected")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textMuted)
                    .fixedSize(horizontal: true, vertical: false)
                if useSpacer { Spacer() }
                Button("Connect") {
                    appState.showServerPicker = true
                }
                .accessibilityIdentifier("sessions.connectButton")
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.accent)
                .hoverEffect(.highlight)
            } else {
                Image(systemName: "server.rack")
                    .foregroundColor(AgentBuddyTheme.accent)
                    .frame(width: 20)
                Text("\(connected.count) server\(connected.count == 1 ? "" : "s")")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textPrimary)
                    .fixedSize(horizontal: true, vertical: false)
                if useSpacer { Spacer() }
                Button("Add") {
                    appState.showServerPicker = true
                }
                .accessibilityIdentifier("sessions.addServerButton")
                .agentBuddyFont(.caption)
                .foregroundColor(AgentBuddyTheme.accent)
                .hoverEffect(.highlight)
                if let activeThread {
                    Button {
                        Task { await forkThread(activeThread) }
                    } label: {
                        if isForkingActiveThread {
                            ProgressView()
                                .controlSize(.small)
                                .tint(AgentBuddyTheme.accent)
                        } else {
                            Text("Fork")
                        }
                    }
                    .disabled(isForkingActiveThread || (activeThreadEphemeralState?.hasTurnActive ?? activeThread.hasActiveTurn))
                    .agentBuddyFont(.caption)
                    .foregroundColor((activeThreadEphemeralState?.hasTurnActive ?? activeThread.hasActiveTurn) ? AgentBuddyTheme.textMuted : AgentBuddyTheme.accent)
                    .hoverEffect(.highlight)
                }
            }
        }
    }
}
