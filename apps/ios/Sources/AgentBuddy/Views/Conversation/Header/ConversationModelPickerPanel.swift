import SwiftUI

struct ConversationModelPickerPanel: View {
    @Environment(AppState.self) private var appState
    @Environment(AppModel.self) private var appModel
    let thread: AppThreadSnapshot

    private var availableModels: [ModelInfo] {
        appModel.availableModels(for: thread.key.serverId)
    }

    var body: some View {
        InlineModelSelectorView(
            models: availableModels,
            selectedModel: selectedModelBinding,
            selectedAgentRuntimeKind: selectedAgentRuntimeKindBinding,
            reasoningEffort: reasoningEffortBinding,
            threadKey: thread.key,
            collaborationMode: thread.collaborationMode,
            effectiveApprovalPolicy: thread.effectiveApprovalPolicy,
            effectiveSandboxPolicy: thread.effectiveSandboxPolicy,
            isReasoningEffortLocked: thread.ampReasoningEffortLocked,
            showsBackground: false,
            onDismiss: {
                appState.showModelSelector = false
            }
        )
        .padding(.vertical, BuddySpacing.xs)
        .presentationBackground(AgentBuddyTheme.surface)
        .task(id: thread.key) {
            await appModel.loadConversationMetadataIfNeeded(serverId: thread.key.serverId)
        }
    }

    private var selectedModelBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return pending }
                return (thread.model ?? thread.info.model ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            },
            set: { appState.selectedModel = $0 }
        )
    }

    private var selectedAgentRuntimeKindBinding: Binding<AgentRuntimeKind?> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return appState.selectedAgentRuntimeKind }
                return thread.agentRuntimeKind
            },
            set: { appState.selectedAgentRuntimeKind = $0 }
        )
    }

    private var reasoningEffortBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty { return pending }
                return thread.reasoningEffort?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            },
            set: { appState.reasoningEffort = $0 }
        )
    }
}
