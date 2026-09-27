import SwiftUI

extension AlleycatAddServerSheet {
    var agentSection: some View {
        Section {
            if isLoadingAgents {
                HStack {
                    ProgressView().tint(AgentBuddyTheme.accent)
                    Text("Loading agents")
                        .agentBuddyFont(.caption)
                        .foregroundColor(AgentBuddyTheme.textSecondary)
                }
            } else if agents.isEmpty {
                Text("No agents are available on this host.")
                    .agentBuddyFont(.caption)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            } else {
                ForEach(agents, id: \.name) { agent in
                    Button {
                        guard agent.available else { return }
                        toggleAgentSelection(agent)
                    } label: {
                        HStack(spacing: 10) {
                            AgentIconView(kind: agent.name.lowercased(), size: 22)
                                .opacity(agent.available ? 1 : 0.45)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(agent.displayName)
                                        .agentBuddyFont(.subheadline)
                                        .foregroundColor(agent.available ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textMuted)
                                    if AgentRuntimeKind.isBetaAgentName(agent.name, displayName: agent.displayName) {
                                        BetaBadge()
                                    }
                                }
                                Text(wireLabel(agent.wire))
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textSecondary)
                            }
                            Spacer()
                            if selectedAgentNames.contains(agent.name) {
                                Image(systemName: "checkmark.square.fill")
                                    .foregroundColor(AgentBuddyTheme.accent)
                            } else if !agent.available {
                                Text("Unavailable")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                            } else {
                                Image(systemName: "square")
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                            }
                        }
                    }
                    .disabled(!agent.available)
                }
            }
        } header: {
            HStack {
                Text("Agents")
                Spacer()
                if !availableAgents.isEmpty {
                    Button(selectedAgents.count == availableAgents.count ? "None" : "All") {
                        if selectedAgents.count == availableAgents.count {
                            selectedAgentNames = []
                        } else {
                            selectedAgentNames = Set(availableAgents.map(\.name))
                        }
                    }
                    .font(.caption)
                    .foregroundColor(AgentBuddyTheme.accent)
                }
            }
                .foregroundColor(AgentBuddyTheme.textSecondary)
        }
        .listRowBackground(AgentBuddyTheme.surface.opacity(0.6))
    }

    private var availableAgents: [AppAlleycatAgentInfo] {
        agents.filter(\.available)
    }

    var selectedAgents: [AppAlleycatAgentInfo] {
        agents.filter { $0.available && selectedAgentNames.contains($0.name) }
    }

    private func toggleAgentSelection(_ agent: AppAlleycatAgentInfo) {
        if selectedAgentNames.contains(agent.name) {
            selectedAgentNames.remove(agent.name)
        } else {
            selectedAgentNames.insert(agent.name)
        }
    }

    func loadAgents(params: AppAlleycatPairPayload) {
        isLoadingAgents = true
        Task {
            do {
                let loaded = try await appModel.serverBridge.listAlleycatAgents(params: params)
                await MainActor.run {
                    guard parsedParams?.nodeId == params.nodeId else { return }
                    agents = loaded
                    selectedAgentNames = Set(
                        loaded
                            .filter { $0.available && !AgentRuntimeKind.isBetaAgentName($0.name, displayName: $0.displayName) }
                            .map(\.name)
                    )
                    isLoadingAgents = false
                    agentError = nil
                }
            } catch {
                await MainActor.run {
                    guard parsedParams?.nodeId == params.nodeId else { return }
                    agents = []
                    selectedAgentNames = []
                    isLoadingAgents = false
                    agentError = error.localizedDescription
                }
            }
        }
    }

    private func wireLabel(_ wire: AppAlleycatAgentWire) -> String {
        switch wire {
        case .websocket:
            return "websocket"
        case .jsonl:
            return "jsonl"
        }
    }
}
