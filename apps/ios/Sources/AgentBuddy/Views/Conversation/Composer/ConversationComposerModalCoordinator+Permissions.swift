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

    @ViewBuilder
    var permissionsSheetContent: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Thread permissions")
                                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                                    .agentBuddyFont(.headline)
                                Text(currentRuntimeSupportsPermissionOverrides ? "Changes apply on your next turn and later turns." : "This runtime controls its own permissions.")
                                    .foregroundStyle(AgentBuddyTheme.textMuted)
                                    .agentBuddyFont(.caption)
                            }
                            Spacer(minLength: 12)
                            Text(currentRuntimeSupportsPermissionOverrides ? (usesThreadDefaults ? "Using defaults" : "Custom override") : "Runtime managed")
                                .foregroundStyle(usesThreadDefaults ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.accentStrong)
                                .agentBuddyFont(size: 11, weight: .semibold)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill((usesThreadDefaults ? AgentBuddyTheme.surfaceLight : AgentBuddyTheme.accentStrong).opacity(0.16))
                                )
                        }

                        HStack(spacing: 10) {
                            permissionSummaryTile(
                                title: "Next turn",
                                approval: selectedApprovalLabel,
                                sandbox: selectedSandboxLabel,
                                accent: AgentBuddyTheme.accentStrong
                            )
                            permissionSummaryTile(
                                title: "Current thread",
                                approval: currentApprovalLabel,
                                sandbox: currentSandboxLabel,
                                accent: hasAuthoritativeThreadPermissions ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.warning
                            )
                        }
                    }
                    .padding(14)
                    .background(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(AgentBuddyTheme.surface.opacity(0.82))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .stroke(AgentBuddyTheme.border.opacity(0.55), lineWidth: 1)
                    )

                    if currentRuntimeSupportsPermissionOverrides {
                        permissionSection(
                            title: "Approval policy",
                            subtitle: "Choose when Codex asks for approval"
                        ) {
                            permissionDropdown(
                                title: selectedApprovalLabel,
                                detail: selectedApprovalDescription
                            ) {
                                ForEach(ComposerApprovalOption.allCases) { option in
                                    permissionMenuItem(
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
                        }

                        permissionSection(
                            title: "Sandbox settings",
                            subtitle: "Choose how much Codex can do when running commands"
                        ) {
                            permissionDropdown(
                                title: selectedSandboxLabel,
                                detail: selectedSandboxDescription
                            ) {
                                ForEach(ComposerSandboxOption.allCases) { option in
                                    permissionMenuItem(
                                        title: option.title,
                                        description: option.description,
                                        isSelected: selectedSandboxValue == option.wireValue
                                    ) {
                                        appState.setPermissions(
                                            approvalPolicy: selectedApprovalValue,
                                            sandboxMode: option.wireValue,
                                            for: snapshot.threadKey
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        unsupportedPermissionRuntimeCard
                    }
                }
                .padding(16)
                .padding(.bottom, 28)
            }
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Permissions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { showPermissionsSheet = false }
                        .foregroundColor(AgentBuddyTheme.accent)
                }
            }
        }
    }

    private var unsupportedPermissionRuntimeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(AgentBuddyTheme.accentStrong)
                Text("Runtime-managed permissions")
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .agentBuddyFont(.subheadline, weight: .semibold)
            }
            Text("This agent does not support AgentBuddy-side thread permission overrides, so approval and sandbox choices are not sent for this session.")
                .foregroundStyle(AgentBuddyTheme.textMuted)
                .agentBuddyFont(.caption)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(AgentBuddyTheme.surface.opacity(0.82))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(AgentBuddyTheme.border.opacity(0.55), lineWidth: 1)
        )
    }

    private func permissionSummaryTile(
        title: String,
        approval: String,
        sandbox: String,
        accent: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .agentBuddyFont(size: 11, weight: .semibold)
            VStack(alignment: .leading, spacing: 8) {
                permissionSummaryRow(label: "Approval", value: approval, accent: accent)
                permissionSummaryRow(label: "Sandbox", value: sandbox, accent: accent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(AgentBuddyTheme.surfaceLight.opacity(0.78))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(AgentBuddyTheme.border.opacity(0.45), lineWidth: 1)
        )
    }

    private func permissionSummaryRow(label: String, value: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .foregroundStyle(AgentBuddyTheme.textMuted)
                .agentBuddyFont(size: 10, weight: .medium)
            Text(value)
                .foregroundStyle(accent)
                .agentBuddyFont(.subheadline, weight: .semibold)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func permissionSection<SectionContent: View>(
        title: String,
        subtitle: String,
        @ViewBuilder content: () -> SectionContent
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .agentBuddyFont(.headline)
                Text(subtitle)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .agentBuddyFont(.caption)
            }
            content()
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(AgentBuddyTheme.surface.opacity(0.74))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(AgentBuddyTheme.border.opacity(0.5), lineWidth: 1)
        )
    }

    private func permissionDropdown<MenuContent: View>(
        title: String,
        detail: String,
        @ViewBuilder content: () -> MenuContent
    ) -> some View {
        Menu {
            content()
        } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .agentBuddyFont(size: 14, weight: .semibold)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Text(detail)
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                        .agentBuddyFont(size: 11)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .foregroundStyle(AgentBuddyTheme.textMuted)
                    .imageScale(.small)
            }
            .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(AgentBuddyTheme.surfaceLight.opacity(0.9))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(AgentBuddyTheme.border.opacity(0.45), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func permissionMenuItem(
        title: String,
        description: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(title)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .agentBuddyFont(size: 14, weight: .semibold)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .foregroundStyle(AgentBuddyTheme.accentStrong)
                            .imageScale(.small)
                    }
                }
                Text(description)
                    .foregroundStyle(AgentBuddyTheme.textMuted)
                    .agentBuddyFont(size: 11)
                    .multilineTextAlignment(.leading)
            }
        }
    }
}
