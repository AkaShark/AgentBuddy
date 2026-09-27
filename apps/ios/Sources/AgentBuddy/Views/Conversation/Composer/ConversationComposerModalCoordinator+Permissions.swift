import SwiftUI

extension ConversationComposerModalCoordinator {
    private var selectedApprovalValue: String {
        appState.approvalPolicy(for: snapshot.threadKey)
    }

    private var selectedSandboxValue: String {
        appState.sandboxMode(for: snapshot.threadKey)
    }

    private var selectedApprovalLabel: String {
        ComposerApprovalOption.allCases.first { $0.wireValue == selectedApprovalValue }?.title ?? "Custom"
    }

    private var selectedApprovalDescription: String {
        ComposerApprovalOption.allCases.first { $0.wireValue == selectedApprovalValue }?.description ?? "This approval policy is managed by the server."
    }

    private var selectedSandboxLabel: String {
        ComposerSandboxOption.allCases.first { $0.wireValue == selectedSandboxValue }?.title ?? "Custom"
    }

    private var selectedSandboxDescription: String {
        ComposerSandboxOption.allCases.first { $0.wireValue == selectedSandboxValue }?.description ?? "This sandbox setting is managed by the server."
    }

    private var currentRuntimeSupportsPermissionOverrides: Bool {
        currentThread?.agentRuntimeKind.supportsThreadPermissionOverrides ?? true
    }

    private var hasAuthoritativeThreadPermissions: Bool {
        guard let thread = currentThread,
              thread.agentRuntimeKind.reportsEffectiveThreadPermissions else { return false }
        return threadPermissionsAreAuthoritative(
            approvalPolicy: thread.effectiveApprovalPolicy,
            sandboxPolicy: thread.effectiveSandboxPolicy
        )
    }

    private var currentApprovalLabel: String {
        guard hasAuthoritativeThreadPermissions else { return "Syncing..." }
        return currentThread?.effectiveApprovalPolicy?.displayTitle ?? "Syncing..."
    }

    private var currentSandboxLabel: String {
        guard hasAuthoritativeThreadPermissions else { return "Syncing..." }
        return currentThread?.effectiveSandboxPolicy?.displayTitle ?? "Syncing..."
    }

    private var usesThreadDefaults: Bool {
        selectedApprovalValue == ComposerApprovalOption.default.wireValue
            && selectedSandboxValue == ComposerSandboxOption.default.wireValue
    }

    private var selectedApprovalIsKnown: Bool {
        ComposerApprovalOption.allCases.contains { $0.wireValue == selectedApprovalValue }
    }

    private var selectedSandboxIsKnown: Bool {
        ComposerSandboxOption.allCases.contains { $0.wireValue == selectedSandboxValue }
    }

    @ViewBuilder
    var permissionsSheetContent: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: BuddySpacing.xl) {
                    permissionSummaryCard

                    if currentRuntimeSupportsPermissionOverrides {
                        permissionSection(
                            title: "Approval policy",
                            subtitle: "Choose when Codex asks for approval",
                            customNote: selectedApprovalIsKnown ? nil : selectedApprovalDescription
                        ) {
                            ForEach(Array(ComposerApprovalOption.allCases.enumerated()), id: \.element.id) { index, option in
                                if index > 0 { BuddyDivider() }
                                permissionOptionRow(
                                    title: option.title,
                                    description: option.description,
                                    isSelected: selectedApprovalValue == option.wireValue
                                ) {
                                    appState.setPermissions(
                                        approvalPolicy: option.wireValue,
                                        sandboxMode: selectedSandboxValue,
                                        for: snapshot.threadKey
                                    )
                                }
                            }
                        }

                        permissionSection(
                            title: "Sandbox settings",
                            subtitle: "Choose how much Codex can do when running commands",
                            customNote: selectedSandboxIsKnown ? nil : selectedSandboxDescription
                        ) {
                            ForEach(Array(ComposerSandboxOption.allCases.enumerated()), id: \.element.id) { index, option in
                                if index > 0 { BuddyDivider() }
                                permissionOptionRow(
                                    title: option.title,
                                    description: option.description,
                                    isSelected: selectedSandboxValue == option.wireValue,
                                    isDanger: option == .fullAccess
                                ) {
                                    appState.setPermissions(
                                        approvalPolicy: selectedApprovalValue,
                                        sandboxMode: option.wireValue,
                                        for: snapshot.threadKey
                                    )
                                }
                            }
                        }
                    } else {
                        unsupportedPermissionRuntimeCard
                    }
                }
                .padding(.horizontal, BuddySpacing.xl)
                .padding(.top, BuddySpacing.xs)
                .padding(.bottom, BuddySpacing.xxl)
            }
            .buddyPageBackground()
            .navigationTitle("Permissions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showPermissionsSheet = false }
                        .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
    }

    /// Next-turn vs current-thread summary with the override state.
    private var permissionSummaryCard: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Thread permissions")
                        .buddyText(.heading)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    (currentRuntimeSupportsPermissionOverrides
                        ? Text("Changes apply on your next turn and later turns.")
                        : Text("This runtime controls its own permissions."))
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                permissionOverrideBadge
            }

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: BuddySpacing.sm) { permissionSummaryTiles }
                VStack(spacing: BuddySpacing.sm) { permissionSummaryTiles }
            }
        }
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }

    @ViewBuilder
    private var permissionSummaryTiles: some View {
        permissionSummaryTile(
            title: "Next turn",
            approval: selectedApprovalLabel,
            sandbox: selectedSandboxLabel,
            accent: AgentBuddyTheme.textPrimary
        )
        permissionSummaryTile(
            title: "Current thread",
            approval: currentApprovalLabel,
            sandbox: currentSandboxLabel,
            accent: hasAuthoritativeThreadPermissions ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.warning
        )
    }

    /// "Using defaults" / "Custom override" / "Runtime managed" as icon + text.
    private var permissionOverrideBadge: some View {
        let isCustom = currentRuntimeSupportsPermissionOverrides && !usesThreadDefaults
        let title: LocalizedStringKey = currentRuntimeSupportsPermissionOverrides
            ? (usesThreadDefaults ? "Using defaults" : "Custom override")
            : "Runtime managed"
        let systemImage = currentRuntimeSupportsPermissionOverrides
            ? (usesThreadDefaults ? "checkmark.circle" : "slider.horizontal.3")
            : "gearshape"
        return Label(title, systemImage: systemImage)
            .buddyText(.caption, weight: .semibold)
            .foregroundStyle(isCustom ? AgentBuddyTheme.onBrand : AgentBuddyTheme.textSecondary)
            .padding(.horizontal, BuddySpacing.sm)
            .padding(.vertical, 6)
            .background(isCustom ? AgentBuddyTheme.brand : AgentBuddyTheme.surfaceSoft, in: Capsule())
            .fixedSize()
    }

    private var unsupportedPermissionRuntimeCard: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            Label {
                Text("Runtime-managed permissions")
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            } icon: {
                Image(systemName: "info.circle")
                    .foregroundStyle(AgentBuddyTheme.link)
                    .accessibilityHidden(true)
            }
            Text("This agent does not support AgentBuddy-side thread permission overrides, so approval and sandbox choices are not sent for this session.")
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.resultCard, padding: BuddySpacing.lg)
    }

    private func permissionSummaryTile(
        title: LocalizedStringKey,
        approval: String,
        sandbox: String,
        accent: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            Text(title)
                .buddyText(.caption, weight: .semibold)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            permissionSummaryRow(label: "Approval", value: approval, accent: accent)
            permissionSummaryRow(label: "Sandbox", value: sandbox, accent: accent)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .buddyCard(.soft, radius: BuddyRadius.tile, padding: BuddySpacing.md)
    }

    private func permissionSummaryRow(label: LocalizedStringKey, value: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .buddyText(.caption)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Text(LocalizedStringKey(value))
                .buddyText(.label, weight: .semibold)
                .foregroundStyle(accent)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    private func permissionSection<SectionContent: View>(
        title: LocalizedStringKey,
        subtitle: LocalizedStringKey,
        customNote: String?,
        @ViewBuilder content: () -> SectionContent
    ) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(subtitle)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let customNote {
                BuddyBanner(
                    tone: .info,
                    message: Text(LocalizedStringKey(customNote)),
                    systemImage: "slider.horizontal.3"
                )
            }
            VStack(spacing: 0) {
                content()
            }
            .padding(.horizontal, BuddySpacing.md)
            .buddyCard(.surface, radius: BuddyRadius.card, padding: nil)
        }
    }

    /// One option: title, what it allows, and — when current — a checkmark
    /// with the word "Selected". Risky options carry the danger role.
    private func permissionOptionRow(
        title: String,
        description: String,
        isSelected: Bool,
        isDanger: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.sm) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        if isDanger {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(AgentBuddyTheme.danger)
                                .accessibilityHidden(true)
                        }
                        Text(LocalizedStringKey(title))
                            .buddyText(.body, weight: isSelected ? .semibold : .regular)
                            .foregroundStyle(isDanger ? AgentBuddyTheme.danger : AgentBuddyTheme.textPrimary)
                    }
                    Text(LocalizedStringKey(description))
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected {
                    Label("Selected", systemImage: "checkmark")
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                        .fixedSize()
                }
            }
            .padding(.vertical, BuddySpacing.sm)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
