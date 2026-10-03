import SwiftUI
import UIKit
import UserNotifications

extension ConversationView {
    func loadInitialTurnsIfNeeded() async {
        guard !thread.initialTurnsLoaded else { return }
        await appModel.loadInitialTurnsIfNeeded(threadId: activeThreadKey)
    }

    /// Re-probed when the server, its health (the host capability is known
    /// once connected) or the scene phase (permission changed in Settings)
    /// changes.
    func refreshUnsupportedHostHint() async {
        let serverId = activeThreadKey.serverId
        let support = appModel.client.hostPushSupport(serverId: serverId)
        guard support == .unsupportedHost else {
            showsUnsupportedHostHint = false
            return
        }
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        guard !Task.isCancelled else { return }
        showsUnsupportedHostHint = PushNotificationSupport.shouldShowUnsupportedHostHint(
            support: support,
            authorizationStatus: settings.authorizationStatus,
            dismissed: UserDefaults.standard.bool(
                forKey: PushNotificationSupport.unsupportedHostHintDismissedKey(serverId: serverId)
            )
        )
    }

    func dismissUnsupportedHostHint() {
        UserDefaults.standard.set(
            true,
            forKey: PushNotificationSupport.unsupportedHostHintDismissedKey(serverId: activeThreadKey.serverId)
        )
        showsUnsupportedHostHint = false
    }

    func sendMessage(
        _ text: String,
        attachmentImage: UIImage?,
        fileAttachments: [ComposerFileAttachment],
        skillMentions: [SkillMentionSelection],
        pluginMentions: [PluginMentionSelection]
    ) {
        localSendScrollToken &+= 1
        Task {
            do {
                NSLog(
                    "[ConversationView] sendMessage start server=%@ thread=%@ textLength=%ld",
                    activeThreadKey.serverId,
                    activeThreadKey.threadId,
                    text.count
                )
                let payload = try makeComposerPayload(
                    text: text,
                    attachmentImage: attachmentImage,
                    fileAttachments: fileAttachments,
                    skillMentions: skillMentions,
                    pluginMentions: pluginMentions
                )
                try await appModel.startTurn(key: activeThreadKey, payload: payload)
                NSLog(
                    "[ConversationView] sendMessage turnStart returned server=%@ thread=%@",
                    activeThreadKey.serverId,
                    activeThreadKey.threadId
                )
            } catch {
                NSLog(
                    "[ConversationView] sendMessage error server=%@ thread=%@ error=%@",
                    activeThreadKey.serverId,
                    activeThreadKey.threadId,
                    error.localizedDescription
                )
                messageActionError = error.localizedDescription
            }
        }
    }

    func sendWidgetPrompt(_ text: String) {
        guard !text.isEmpty else { return }
        localSendScrollToken &+= 1
        Task {
            do {
                let payload = try makeComposerPayload(
                    text: text,
                    attachmentImage: nil,
                    fileAttachments: [],
                    skillMentions: [],
                    pluginMentions: []
                )
                try await appModel.startTurn(key: activeThreadKey, payload: payload)
            } catch {
                messageActionError = error.localizedDescription
            }
        }
    }

    func resolveTargetLabel(_ target: String) -> String? {
        appModel.snapshot?.resolvedAgentTargetLabel(for: target, serverId: activeThreadKey.serverId)
    }

    /// Resolve the user-message position in the currently-loaded transcript.
    /// `forkThreadFromMessage` / `editMessage` on the Rust side expect an
    /// index into `thread.items` filtered to user messages — see
    /// `rollback_depth_for_turn` in `mobile_client/message_actions.rs`.
    /// Recomputing from the live `items` keeps the index correct under
    /// pagination (older turns can shift positions; cached `sourceTurnIndex`
    /// from a prior hydrate would be stale).
    private func loadedUserItemIndex(for item: ConversationItem) -> Int? {
        var idx = 0
        for candidate in items {
            guard candidate.isUserItem else { continue }
            if candidate.id == item.id { return idx }
            idx += 1
        }
        return nil
    }

    func editMessage(_ item: ConversationItem) {
        Task {
            do {
                guard item.isUserItem, item.isFromUserTurnBoundary,
                      let selectedTurnIndex = loadedUserItemIndex(for: item) else {
                    throw NSError(
                        domain: "AgentBuddy",
                        code: 1020,
                        userInfo: [NSLocalizedDescriptionKey: "Only user messages can be edited"]
                    )
                }
                let result = try await appModel.store.editMessage(
                    key: activeThreadKey,
                    selectedTurnIndex: UInt32(selectedTurnIndex)
                )
                appModel.queueComposerPrefill(threadKey: activeThreadKey, text: result)
            } catch {
                messageActionError = error.localizedDescription
            }
        }
    }

    func forkFromMessage(_ item: ConversationItem) {
        Task {
            do {
                guard item.isUserItem, item.isFromUserTurnBoundary,
                      let selectedTurnIndex = loadedUserItemIndex(for: item) else {
                    throw NSError(
                        domain: "AgentBuddy",
                        code: 1016,
                        userInfo: [NSLocalizedDescriptionKey: "Fork from here is only supported for user messages"]
                    )
                }
                let nextKey = try await appModel.store.forkThreadFromMessage(
                    key: activeThreadKey,
                    selectedTurnIndex: UInt32(selectedTurnIndex),
                    params: launchConfig().forkThreadFromMessageRequest(
                        cwdOverride: thread.info.cwd
                    )
                )
                await appModel.refreshThreadSnapshot(key: nextKey)
                let nextCwd = thread.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if !nextCwd.isEmpty {
                    workDir = nextCwd
                    appState.currentCwd = nextCwd
                }
                onOpenConversation?(nextKey)
            } catch {
                messageActionError = error.localizedDescription
            }
        }
    }

    func searchComposerFiles(_ query: String) async throws -> [FileSearchResult] {
        let searchRoot = workDir.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "/" : workDir
        return try await appModel.client.searchFiles(
            serverId: activeThreadKey.serverId,
            params: AppSearchFilesRequest(
                query: query,
                roots: [searchRoot],
                cancellationToken: "ios-composer-file-search"
            )
        )
    }

    private func makeComposerPayload(
        text: String,
        attachmentImage: UIImage?,
        fileAttachments: [ComposerFileAttachment],
        skillMentions: [SkillMentionSelection],
        pluginMentions: [PluginMentionSelection]
    ) throws -> AppComposerPayload {
        let preparedAttachment = attachmentImage.flatMap(ConversationAttachmentSupport.prepareImage)
        var additionalInputs = skillMentions.map { mention in
            AppUserInput.skill(name: mention.name, path: AbsolutePath(value: mention.path))
        }
        for mention in pluginMentions {
            additionalInputs.append(
                AppUserInput.mention(name: mention.name, path: mention.path)
            )
        }
        if let preparedAttachment {
            additionalInputs.append(preparedAttachment.userInput)
        }
        return AppComposerPayload(
            text: text,
            additionalInputs: additionalInputs,
            fileAttachments: fileAttachments,
            approvalPolicy: appState.launchApprovalPolicy(for: activeThreadKey),
            sandboxPolicy: appState.turnSandboxPolicy(for: activeThreadKey),
            model: pendingModelOverride,
            effort: ReasoningEffort(wireValue: pendingReasoningOverride),
            serviceTier: ServiceTier(wireValue: fastMode ? "fast" : nil)
        )
    }

    private func launchConfig() -> AppThreadLaunchConfig {
        AppThreadLaunchConfig(
            agentRuntimeKind: pendingAgentRuntimeKindOverride,
            model: pendingModelOverride,
            approvalPolicy: appState.launchApprovalPolicy(for: activeThreadKey),
            sandbox: appState.launchSandboxMode(for: activeThreadKey),
            developerInstructions: nil,
            persistExtendedHistory: true
        )
    }
}
