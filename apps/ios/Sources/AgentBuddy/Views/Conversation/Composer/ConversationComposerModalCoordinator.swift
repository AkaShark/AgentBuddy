import SwiftUI
import PhotosUI
import UIKit

struct ConversationComposerModalCoordinator<Content: View>: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) var appState

    let snapshot: ConversationComposerSnapshot
    let experimentalFeatures: [ExperimentalFeature]
    let experimentalFeaturesLoading: Bool
    let skills: [SkillMetadata]
    let skillsLoading: Bool
    @Binding var showAttachMenu: Bool
    @Binding var showPhotoPicker: Bool
    @Binding var showCamera: Bool
    @Binding var showFileImporter: Bool
    @Binding var selectedPhoto: PhotosPickerItem?
    @Binding var attachedImage: UIImage?
    @Binding var showModelSelector: Bool
    @Binding var showPermissionsSheet: Bool
    @Binding var showExperimentalSheet: Bool
    @Binding var showSkillsSheet: Bool
    @Binding var showRenamePrompt: Bool
    @Binding var renameCurrentThreadTitle: String
    @Binding var renameDraft: String
    @Binding var slashErrorMessage: String?
    @Binding var showMicPermissionAlert: Bool
    let onOpenSettings: () -> Void
    let onLoadSelectedPhoto: (PhotosPickerItem) async -> Void
    let onLoadSelectedFile: (URL) -> Void
    let onLoadExperimentalFeatures: () async -> Void
    let onIsExperimentalFeatureEnabled: (String, Bool) -> Bool
    let onSetExperimentalFeature: (String, Bool) async -> Void
    let onLoadSkills: (Bool, Bool) async -> Void
    let onRenameThread: (String) async -> Void
    @ViewBuilder let content: Content
    @State private var modelSelectorDetent: PresentationDetent = .large

    private var selectedModelBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return pending
                }
                return snapshot.threadModel.trimmingCharacters(in: .whitespacesAndNewlines)
            },
            set: { appState.selectedModel = $0 }
        )
    }

    private var selectedAgentRuntimeKindBinding: Binding<AgentRuntimeKind?> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return appState.selectedAgentRuntimeKind
                }
                return currentThread?.agentRuntimeKind
            },
            set: { appState.selectedAgentRuntimeKind = $0 }
        )
    }

    private var reasoningEffortBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return pending
                }
                return snapshot.threadReasoningEffort?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            },
            set: { appState.reasoningEffort = $0 }
        )
    }

    var currentThread: AppThreadSnapshot? {
        appModel.snapshot?.threads.first(where: { $0.key == snapshot.threadKey })
    }

    private var attachSheetDetentHeight: CGFloat {
        let showsCamera = !AgentBuddyPlatform.isCatalyst
        let count = 2 + (showsCamera ? 1 : 0)
        return count >= 3 ? 260 : 210
    }

    var body: some View {
        content
            .sheet(isPresented: $showAttachMenu) {
                ConversationComposerAttachSheet(
                    onPickPhotoLibrary: {
                        showAttachMenu = false
                        showPhotoPicker = true
                    },
                    onChooseFile: {
                        showAttachMenu = false
                        showFileImporter = true
                    },
                    onTakePhoto: AgentBuddyPlatform.isCatalyst ? nil : {
                        showAttachMenu = false
                        showCamera = true
                    }
                )
                .presentationDetents([.height(attachSheetDetentHeight)])
                .presentationDragIndicator(.visible)
            }
            .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images)
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: ConversationAttachmentSupport.supportedFileContentTypes,
                allowsMultipleSelection: false
            ) { result in
                guard case let .success(urls) = result,
                      let url = urls.first else { return }
                onLoadSelectedFile(url)
            }
            .onChange(of: selectedPhoto) { _, item in
                guard let item else { return }
                Task { await onLoadSelectedPhoto(item) }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraView(image: $attachedImage)
                    .ignoresSafeArea()
            }
            .sheet(isPresented: $showModelSelector) {
                ModelSelectorSheet(
                    models: snapshot.availableModels,
                    selectedModel: selectedModelBinding,
                    selectedAgentRuntimeKind: selectedAgentRuntimeKindBinding,
                    reasoningEffort: reasoningEffortBinding,
                    isReasoningEffortLocked: currentThread?.ampReasoningEffortLocked == true
                )
                .presentationDetents([.medium, .large], selection: $modelSelectorDetent)
                .presentationDragIndicator(.visible)
                .presentationContentInteraction(.scrolls)
                .presentationBackground(AgentBuddyTheme.surface)
            }
            .onChange(of: showModelSelector) { _, isPresented in
                if isPresented {
                    modelSelectorDetent = .large
                }
            }
            .sheet(isPresented: $showPermissionsSheet) {
                permissionsSheetContent
                    .task(id: snapshot.threadKey.threadId) {
                        _ = await appModel.hydrateThreadPermissions(for: snapshot.threadKey, appState: appState)
                    }
            }
            .sheet(isPresented: $showExperimentalSheet) {
                experimentalSheetContent
            }
            .sheet(isPresented: $showSkillsSheet) {
                skillsSheetContent
            }
            .alert("Rename Thread", isPresented: Binding(
                get: { showRenamePrompt },
                set: { isPresented in
                    showRenamePrompt = isPresented
                    if !isPresented {
                        renameCurrentThreadTitle = ""
                        renameDraft = ""
                    }
                }
            )) {
                TextField("New thread title", text: $renameDraft)
                Button("Cancel", role: .cancel) {
                    showRenamePrompt = false
                }
                Button("Rename") {
                    let nextName = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !nextName.isEmpty else { return }
                    Task { await onRenameThread(nextName) }
                }
            } message: {
                Text("Current thread title:\n\(renameCurrentThreadTitle)")
            }
            .alert("Slash Command Error", isPresented: Binding(
                get: { slashErrorMessage != nil },
                set: { if !$0 { slashErrorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { slashErrorMessage = nil }
            } message: {
                Text(slashErrorMessage ?? "Unknown error")
            }
            .alert("Microphone Access", isPresented: $showMicPermissionAlert) {
                Button("Open Settings", action: onOpenSettings)
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Microphone permission is required for voice input. Enable it in Settings.")
            }
    }
}
