import SwiftUI

extension AlleycatAddServerSheet {
    var agentSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            DiscoveryAgentListHeader(
                showsToggle: !availableAgents.isEmpty,
                allSelected: selectedAgents.count == availableAgents.count,
                isEnabled: true
            ) {
                if selectedAgents.count == availableAgents.count {
                    selectedAgentNames = []
                } else {
                    selectedAgentNames = Set(availableAgents.map(\.name))
                }
            }

            if isLoadingAgents {
                HStack(spacing: BuddySpacing.sm) {
                    ProgressView().tint(AgentBuddyTheme.textSecondary)
                    Text("Loading agents")
                        .buddyText(.body)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
                .frame(minHeight: BuddySize.control)
            } else if agents.isEmpty {
                Text("No agents are available on this host.")
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(agents.enumerated()), id: \.element.name) { index, agent in
                        if index > 0 { BuddyDivider().padding(.leading, BuddySpacing.md) }
                        Button {
                            guard agent.available else { return }
                            toggleAgentSelection(agent)
                        } label: {
                            DiscoveryAgentRow(
                                kind: agent.name.lowercased(),
                                title: agent.displayName,
                                detail: wireLabel(agent.wire),
                                isBeta: AgentRuntimeKind.isBetaAgentName(agent.name, displayName: agent.displayName),
                                mark: agentMark(for: agent)
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(!agent.available)
                    }
                }
                .buddyCard(.surface, radius: BuddyRadius.detailCard, padding: nil)
            }
        }
    }

    private func agentMark(for agent: AppAlleycatAgentInfo) -> DiscoveryAgentRow.Mark {
        if selectedAgentNames.contains(agent.name) { return .selected }
        if !agent.available { return .unavailable(note: "Unavailable") }
        return .unselected
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
