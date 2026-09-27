import SwiftUI

extension DiscoveryView {
    // MARK: - Chooser

    @ViewBuilder
    var chooserContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: BuddySpacing.md) {
                Text("Pick how you want to connect.")
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: BuddySpacing.sm) {
                    chooserCard(
                        title: "Scan QR from the computer",
                        subtitle: "Open AgentBuddy on your computer and scan the pairing code it shows.",
                        icon: "qrcode.viewfinder",
                        supportedAgents: Self.kittylitterAgents,
                        isRecommended: true,
                        accessibilityID: "discovery.chooser.kittylitter"
                    ) {
                        showAlleycatSheet = true
                    }

                    chooserCard(
                        title: "Connected computer",
                        subtitle: "A computer running Codex with this ChatGPT account.",
                        icon: "desktopcomputer",
                        supportedAgents: [AgentRuntimeKind.codex],
                        isRecommended: false,
                        accessibilityID: "discovery.chooser.slingshot"
                    ) {
                        showSlingshotHosts = true
                    }

                    chooserCard(
                        title: "SSH or address",
                        subtitle: "Sign in over SSH, or enter a ws:// Codex address.",
                        icon: "terminal",
                        supportedAgents: [AgentRuntimeKind.codex],
                        isRecommended: false,
                        accessibilityID: "discovery.chooser.manual"
                    ) {
                        showManualEntry = true
                    }
                }
            }
            .padding(.horizontal, BuddySpacing.xl)
            .padding(.top, BuddySpacing.xs)
            .padding(.bottom, BuddySpacing.xl)
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

    /// One connection option. The recommended QR option uses the brand
    /// surface; the others sit on plain surface cards.
    private func chooserCard(
        title: LocalizedStringKey,
        subtitle: LocalizedStringKey,
        icon: String,
        supportedAgents: [AgentRuntimeKind],
        isRecommended: Bool,
        accessibilityID: String,
        action: @escaping () -> Void
    ) -> some View {
        let primaryText = isRecommended ? AgentBuddyTheme.onBrand : AgentBuddyTheme.textPrimary
        let secondaryText = isRecommended ? AgentBuddyTheme.onBrand : AgentBuddyTheme.textSecondary
        return Button(action: action) {
            VStack(alignment: .leading, spacing: BuddySpacing.md) {
                HStack(alignment: .top, spacing: BuddySpacing.md) {
                    BuddyIconTile(
                        content: .symbol(icon),
                        fill: isRecommended ? AgentBuddyTheme.onBrand.opacity(0.1) : AgentBuddyTheme.surfaceSoft,
                        foreground: primaryText
                    )

                    VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                        if isRecommended {
                            BuddyChip(localized: "Recommended", tone: .onBrand)
                                .padding(.bottom, BuddySpacing.xxs)
                        }
                        Text(title)
                            .buddyText(.heading)
                            .foregroundStyle(primaryText)
                        Text(subtitle)
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(secondaryText)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Image(systemName: "chevron.right")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(secondaryText)
                        .frame(minHeight: BuddySize.rowTile)
                        .accessibilityHidden(true)
                }

                if !supportedAgents.isEmpty {
                    supportedAgentsStrip(supportedAgents, textColor: secondaryText)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .buddyCard(isRecommended ? .brand : .surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
            .contentShape(RoundedRectangle(cornerRadius: BuddyRadius.card, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier(accessibilityID)
    }

    @ViewBuilder
    private func supportedAgentsStrip(_ agents: [AgentRuntimeKind], textColor: Color) -> some View {
        HStack(spacing: BuddySpacing.xs) {
            Text("Works with")
                .buddyText(.caption, weight: .medium)
                .foregroundStyle(textColor)
                .fixedSize(horizontal: true, vertical: false)
            HStack(spacing: 6) {
                ForEach(agents, id: \.self) { agent in
                    AgentIconView(kind: agent, size: 20)
                        .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
                        .accessibilityLabel(Text(verbatim: agent.displayLabel))
                }
            }
            Spacer(minLength: 0)
        }
    }
}
