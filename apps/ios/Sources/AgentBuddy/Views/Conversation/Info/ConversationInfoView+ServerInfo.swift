import SwiftUI

extension ConversationInfoView {
    // MARK: - Section C: Server Info

    var serverInfoSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            Text("Server")
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityAddTraits(.isHeader)

            if let server {
                infoRow("Name", value: server.displayName)
                infoRow("Address", value: "\(server.host):\(server.port)", isCode: true)
                infoRow("Mode", value: server.connectionModeLabel)

                infoRow("Health") {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(healthColor(server.health))
                            .frame(width: 8, height: 8)
                            .accessibilityHidden(true)
                        Text(healthLabel(server.health))
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                    }
                }

                if let account = server.account {
                    accountRow(account)
                }

                if let models = server.availableModels, !models.isEmpty {
                    VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                        Text("Available Models")
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                        ForEach(models.prefix(8), id: \.id) { model in
                            Text(verbatim: model.displayName)
                                .buddyText(.label, weight: .regular)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                        }
                        if models.count > 8 {
                            Text("+\(models.count - 8) more")
                                .buddyText(.caption)
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                        }
                    }
                    .padding(.top, BuddySpacing.xxs)
                }

                if server.isLocal {
                    BuddyDivider()
                    Button {
                        isShowingMountedFolders = true
                    } label: {
                        HStack(spacing: BuddySpacing.sm) {
                            Image(systemName: "externaldrive.badge.icloud")
                                .font(.system(size: 17, weight: .medium))
                                .foregroundStyle(AgentBuddyTheme.link)
                                .accessibilityHidden(true)
                            Text("Mounted folders")
                                .buddyText(.body)
                                .foregroundStyle(AgentBuddyTheme.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(AgentBuddyTheme.textSecondary)
                                .accessibilityHidden(true)
                        }
                        .frame(minHeight: BuddySize.control)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }

    private func infoRow(_ label: LocalizedStringKey, value: String, isCode: Bool = false) -> some View {
        infoRow(label) {
            Text(verbatim: value)
                .buddyText(isCode ? .code : .label, weight: isCode ? nil : .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
    }

    /// Label on the leading edge, value on the trailing edge; the value
    /// wraps instead of shrinking.
    private func infoRow<Value: View>(_ label: LocalizedStringKey, @ViewBuilder value: () -> Value) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.md) {
            Text(label)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Spacer(minLength: BuddySpacing.xs)
            value()
        }
        .accessibilityElement(children: .combine)
    }

    private func healthColor(_ health: AppServerHealth) -> Color {
        switch health {
        case .connected: return AgentBuddyTheme.success
        case .connecting: return AgentBuddyTheme.warning
        case .disconnected, .unresponsive: return AgentBuddyTheme.danger
        case .unknown: return AgentBuddyTheme.textSecondary
        }
    }

    private func healthLabel(_ health: AppServerHealth) -> LocalizedStringKey {
        switch health {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .disconnected: return "Disconnected"
        case .unresponsive: return "Unresponsive"
        case .unknown: return "Unknown"
        }
    }

    private func accountRow(_ account: Account) -> some View {
        infoRow("Account") {
            switch account {
            case .apiKey:
                Text("API Key")
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            case .chatgpt(let email, let planType):
                VStack(alignment: .trailing, spacing: 2) {
                    Text(verbatim: email)
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                    Text(verbatim: planTypeLabel(planType))
                        .buddyText(.caption)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
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
