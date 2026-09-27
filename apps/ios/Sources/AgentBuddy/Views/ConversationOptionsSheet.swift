import SwiftUI

/// Shared sheet for model + reasoning + plan/fast/permissions options. Used
/// by the home composer's `HomeModelChip` (no thread yet — `threadKey` nil)
/// and by any caller in a thread context (existing conversation).
struct ConversationOptionsSheet: View {
    let models: [ModelInfo]
    @Binding var selectedModel: String
    @Binding var selectedAgentRuntimeKind: AgentRuntimeKind?
    @Binding var reasoningEffort: String
    var threadKey: ThreadKey?
    var collaborationMode: AppModeKind = .default
    var effectiveApprovalPolicy: AppAskForApproval?
    var effectiveSandboxPolicy: AppSandboxPolicy?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        // Present the inline selector exactly as it appears in the
        // conversation popover, under a sheet title. The sheet drag
        // indicator handles dismissal; InlineModelSelectorView still gets
        // `onDismiss` for its own close paths.
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            Text("Partner, model and permissions")
                .buddyText(.title)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .padding(.horizontal, BuddySpacing.md)
                .padding(.top, BuddySpacing.xl)
            InlineModelSelectorView(
                models: models,
                selectedModel: $selectedModel,
                selectedAgentRuntimeKind: $selectedAgentRuntimeKind,
                reasoningEffort: $reasoningEffort,
                threadKey: threadKey,
                collaborationMode: collaborationMode,
                effectiveApprovalPolicy: effectiveApprovalPolicy,
                effectiveSandboxPolicy: effectiveSandboxPolicy,
                onDismiss: { dismiss() }
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(AgentBuddyTheme.surface.ignoresSafeArea())
    }
}
