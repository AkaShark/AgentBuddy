import SwiftUI

extension DiscoveryView {
    // MARK: - Chooser

    @ViewBuilder
    var chooserContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Pick how you want to connect.")
                    .agentBuddyFont(.footnote)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .padding(.top, 8)

                chooserCard(
                    title: "Pair with AgentBuddy",
                    subtitle: "Install the AgentBuddy desktop app on your Mac, open its Pairing page, then scan the QR code.",
                    badge: "RECOMMENDED",
                    icon: "qrcode.viewfinder",
                    supportedAgents: Self.kittylitterAgents,
                    isRecommended: true,
                    accessibilityID: "discovery.chooser.kittylitter"
                ) {
                    showAlleycatSheet = true
                }

                chooserCard(
                    title: "Connected Computer",
                    subtitle: "Connect to a computer already signed in and running Codex for this ChatGPT account.",
                    badge: nil,
                    icon: "desktopcomputer",
                    supportedAgents: [AgentRuntimeKind.codex],
                    isRecommended: false,
                    accessibilityID: "discovery.chooser.slingshot"
                ) {
                    showSlingshotHosts = true
                }

                chooserCard(
                    title: "SSH or Codex URL",
                    subtitle: "Connect over SSH or paste a ws:// codex URL.",
                    badge: nil,
                    icon: "terminal",
                    supportedAgents: [AgentRuntimeKind.codex],
                    isRecommended: false,
                    accessibilityID: "discovery.chooser.manual"
                ) {
                    showManualEntry = true
                }

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollIndicators(.hidden)
    }

    /// Canonical agent list shown on the kittylitter chooser card.
    /// Mirrors the splash carousel order so cold-start branding stays
    /// consistent. New agents added in the alleycat manifest still
    /// surface on connected hosts via the real metadata store; this list
    /// only seeds the pre-pair preview.
    private static let kittylitterAgents: [AgentRuntimeKind] = [
        "codex",
        "pi",
        "amp",
        "opencode",
        "claude",
        "droid",
        "hermes",
        "devin",
        "grok",
    ]

    private func chooserCard(
        title: String,
        subtitle: String,
        badge: String?,
        icon: String,
        supportedAgents: [AgentRuntimeKind],
        isRecommended: Bool,
        accessibilityID: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: icon)
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(AgentBuddyTheme.accent)
                        .frame(width: 36, height: 36)
                        .background(
                            Circle()
                                .fill(AgentBuddyTheme.accent.opacity(isRecommended ? 0.16 : 0.10))
                        )
                        .padding(.top, 2)

                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Text(title)
                                .agentBuddyFont(.subheadline, weight: .semibold)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                            if let badge {
                                Text(badge)
                                    .agentBuddyFont(.caption2, weight: .semibold)
                                    .foregroundColor(AgentBuddyTheme.accentStrong)
                                    .tracking(0.5)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(
                                        Capsule()
                                            .fill(AgentBuddyTheme.accent.opacity(0.14))
                                    )
                                    .overlay(
                                        Capsule()
                                            .stroke(AgentBuddyTheme.accent.opacity(0.45), lineWidth: 0.6)
                                    )
                            }
                        }
                        Text(subtitle)
                            .agentBuddyFont(.caption)
                            .foregroundColor(AgentBuddyTheme.textSecondary)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 2)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(AgentBuddyTheme.textMuted)
                        .padding(.top, 10)
                }

                if !supportedAgents.isEmpty {
                    supportedAgentsStrip(supportedAgents)
                }
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(AgentBuddyTheme.surface.opacity(isRecommended ? 0.85 : 0.6))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(
                        AgentBuddyTheme.accent.opacity(isRecommended ? 0.45 : 0.18),
                        lineWidth: isRecommended ? 1.0 : 0.8
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(accessibilityID)
    }

    @ViewBuilder
    private func supportedAgentsStrip(_ agents: [AgentRuntimeKind]) -> some View {
        HStack(spacing: 8) {
            Text("Works with")
                .agentBuddyFont(.caption2)
                .foregroundColor(AgentBuddyTheme.textMuted)
                .tracking(0.4)
                .fixedSize(horizontal: true, vertical: false)
            HStack(spacing: 5) {
                ForEach(agents, id: \.self) { agent in
                    AgentIconView(kind: agent, size: 18)
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                }
            }
            Spacer(minLength: 0)
        }
    }
}
