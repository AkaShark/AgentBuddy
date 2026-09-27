import SwiftUI

extension ConversationInfoView {
    // MARK: - Section C: Server Info

    var serverInfoSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Server")
                .agentBuddyFont(size: 14, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textPrimary)

            if let server {
                infoRow("Name", value: server.displayName)
                infoRow("Address", value: "\(server.host):\(server.port)")
                infoRow("Mode", value: server.connectionModeLabel)

                HStack(spacing: 6) {
                    Text("Health")
                        .agentBuddyFont(size: 12)
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                    Spacer()
                    Circle()
                        .fill(healthColor(server.health))
                        .frame(width: 8, height: 8)
                    Text(healthLabel(server.health))
                        .agentBuddyFont(size: 12)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }

                if let account = server.account {
                    accountRow(account)
                }

                if let models = server.availableModels, !models.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Available Models")
                            .agentBuddyFont(size: 12)
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                        ForEach(models.prefix(8), id: \.id) { model in
                            Text(model.displayName)
                                .agentBuddyFont(size: 12)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                        if models.count > 8 {
                            Text("+\(models.count - 8) more")
                                .agentBuddyFont(size: 11)
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                        }
                    }
                }

                if server.isLocal {
                    Button {
                        isShowingMountedFolders = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "externaldrive.badge.icloud")
                                .agentBuddyFont(size: 12)
                                .foregroundStyle(AgentBuddyTheme.accent)
                            Text("Mounted folders")
                                .agentBuddyFont(size: 12)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .agentBuddyFont(size: 11, weight: .semibold)
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .modifier(GlassRectModifier(cornerRadius: 12))
    }

    private func infoRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label)
                .agentBuddyFont(size: 12)
                .foregroundStyle(AgentBuddyTheme.textMuted)
            Spacer()
            Text(value)
                .agentBuddyFont(size: 12)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        }
    }

    private func healthColor(_ health: AppServerHealth) -> Color {
        switch health {
        case .connected: return AgentBuddyTheme.success
        case .connecting: return AgentBuddyTheme.warning
        case .disconnected, .unresponsive: return AgentBuddyTheme.danger
        case .unknown: return AgentBuddyTheme.textMuted
        }
    }

    private func healthLabel(_ health: AppServerHealth) -> String {
        switch health {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .disconnected: return "Disconnected"
        case .unresponsive: return "Unresponsive"
        case .unknown: return "Unknown"
        }
    }

    private func accountRow(_ account: Account) -> some View {
        HStack {
            Text("Account")
                .agentBuddyFont(size: 12)
                .foregroundStyle(AgentBuddyTheme.textMuted)
            Spacer()
            switch account {
            case .apiKey:
                Text("API Key")
                    .agentBuddyFont(size: 12)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            case .chatgpt(let email, let planType):
                VStack(alignment: .trailing, spacing: 2) {
                    Text(email)
                        .agentBuddyFont(size: 12)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                    Text(planTypeLabel(planType))
                        .agentBuddyFont(size: 10)
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
            }
        }
    }

    private func planTypeLabel(_ planType: PlanType) -> String {
        switch planType {
        case .free: return "Free"
        case .go: return "Go"
        case .plus: return "Plus"
        case .pro: return "Pro"
        case .team: return "Team"
        case .business: return "Business"
        case .enterprise: return "Enterprise"
        case .edu: return "Edu"
        case .unknown: return "Unknown"
        }
    }
}
