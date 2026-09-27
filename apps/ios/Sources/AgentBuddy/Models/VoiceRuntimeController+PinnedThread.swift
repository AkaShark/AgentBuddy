import Foundation

extension VoiceRuntimeController {
    func ensurePinnedLocalVoiceThread(
        cwd: String,
        model: String?,
        approvalPolicy: AppAskForApproval?,
        sandboxMode: AppSandboxMode?
    ) async throws -> ThreadKey {
        let appModel = requireAppModel()
        let serverId = try await ensureLocalServerConnected()

        if let storedThreadId = persistedLocalVoiceThreadId() {
            let key = ThreadKey(serverId: serverId, threadId: storedThreadId)
            if let resolvedKey = await appModel.ensureThreadLoaded(key: key),
               let thread = appModel.threadSnapshot(for: resolvedKey),
               pinnedVoiceThreadMatchesRequestedConfig(
                   thread,
                   cwd: cwd,
                   model: model,
                   approvalPolicy: approvalPolicy,
                   sandboxMode: sandboxMode
               ) {
                appModel.store.setActiveThread(key: resolvedKey)
                await appModel.refreshSnapshot()
                return resolvedKey
            } else {
                setPersistedLocalVoiceThreadId(nil)
            }
        }

        let key = try await appModel.client.startThread(
            serverId: serverId,
            params: AppThreadLaunchConfig(
                model: model,
                approvalPolicy: approvalPolicy,
                sandbox: sandboxMode,
                developerInstructions: nil,
                persistExtendedHistory: true
            ).threadStartRequest(
                cwd: preferredVoiceThreadCwd(for: nil, fallback: cwd),
                dynamicTools: appModel.localGenerativeUiToolSpecs(for: serverId)
            )
        )
        do {
            try await appModel.renameThread(
                serverId: serverId,
                threadId: key.threadId,
                title: "realtime session"
            )
        } catch {
            LLog.warn(
                "voice",
                "failed to name realtime session thread",
                fields: ["error": String(describing: error)]
            )
        }
        SavedThreadsStore.add(.init(threadKey: key))
        appModel.store.setActiveThread(key: key)
        setPersistedLocalVoiceThreadId(key.threadId)
        await appModel.refreshSnapshot()
        return key
    }

    private func ensureLocalServerConnected() async throws -> String {
        if let server = appModel?.snapshot?.serverSnapshot(for: Self.localServerID), server.isConnected {
            return server.serverId
        }
        let serverId = try await requireAppModel().serverBridge.connectLocalServer(
            serverId: Self.localServerID,
            displayName: requireAppModel().resolvedLocalServerDisplayName(),
            host: "127.0.0.1",
            port: 0
        )
        await requireAppModel().restoreStoredLocalAuthState(serverId: serverId)
        await requireAppModel().refreshSnapshot()
        syncHandoffServers()
        return serverId
    }

    func persistedLocalVoiceThreadId() -> String? {
        let stored = UserDefaults.standard.string(forKey: Self.persistedLocalVoiceThreadIDKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return stored.isEmpty ? nil : stored
    }

    private func setPersistedLocalVoiceThreadId(_ threadId: String?) {
        let trimmed = threadId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmed.isEmpty {
            UserDefaults.standard.removeObject(forKey: Self.persistedLocalVoiceThreadIDKey)
        } else {
            UserDefaults.standard.set(trimmed, forKey: Self.persistedLocalVoiceThreadIDKey)
        }
    }

    private func preferredVoiceThreadCwd(for key: ThreadKey?, fallback: String) -> String {
        let existingCwd = key.flatMap {
            appModel?.snapshot?.threadSnapshot(for: $0)?.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines)
        } ?? ""
        if !existingCwd.isEmpty {
            return existingCwd
        }
        let trimmedFallback = fallback.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedFallback.isEmpty {
            return trimmedFallback
        }
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
    }

    private func pinnedVoiceThreadMatchesRequestedConfig(
        _ thread: AppThreadSnapshot,
        cwd: String,
        model: String?,
        approvalPolicy: AppAskForApproval?,
        sandboxMode: AppSandboxMode?
    ) -> Bool {
        let requestedCwd = preferredVoiceThreadCwd(for: thread.key, fallback: cwd)
        let existingCwd = thread.info.cwd?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !requestedCwd.isEmpty, requestedCwd != existingCwd {
            return false
        }

        let requestedModel = model?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let existingModel = (thread.model ?? thread.info.model)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !requestedModel.isEmpty, requestedModel != existingModel {
            return false
        }
        if let approvalPolicy, approvalPolicy != thread.effectiveApprovalPolicy {
            return false
        }
        if let sandboxMode, sandboxMode != thread.effectiveSandboxPolicy?.launchOverrideMode {
            return false
        }
        return true
    }
}
