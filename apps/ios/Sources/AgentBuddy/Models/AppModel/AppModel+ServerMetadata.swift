import Foundation

extension AppModel {
    func availableModels(for serverId: String) -> [ModelInfo] {
        snapshot?.serverSnapshot(for: serverId)?.availableModels ?? []
    }

    func rateLimits(for serverId: String) -> RateLimitSnapshot? {
        snapshot?.serverSnapshot(for: serverId)?.rateLimits
    }

    /// Per-runtime rate limits. Returns the snapshot reported by the agent
    /// runtime that owns the thread the user is currently looking at — e.g.
    /// Claude Code threads return Claude usage, Codex threads return Codex
    /// usage. Returns `nil` when the runtime hasn't reported any.
    func rateLimits(forServer serverId: String, runtime: AgentRuntimeKind) -> RateLimitSnapshot? {
        snapshot?
            .serverSnapshot(for: serverId)?
            .rateLimitsByRuntime
            .first(where: { $0.runtimeKind == runtime })?
            .rateLimits
    }

    func loadConversationMetadataIfNeeded(serverId: String) async {
        if hasFreshConversationMetadata(for: serverId) {
            return
        }
        await loadAvailableModelsIfNeeded(serverId: serverId)
        await loadRateLimitsIfNeeded(serverId: serverId)
        recentConversationMetadataLoads[serverId] = Date()
    }

    func loadAvailableModelsIfNeeded(serverId: String) async {
        guard let server = snapshot?.serverSnapshot(for: serverId), server.isConnected else { return }
        guard server.availableModels == nil else { return }
        guard !loadingModelServerIds.contains(serverId) else { return }
        loadingModelServerIds.insert(serverId)
        defer { loadingModelServerIds.remove(serverId) }
        do {
            _ = try await client.refreshModels(
                serverId: serverId,
                params: AppRefreshModelsRequest(cursor: nil, limit: nil, includeHidden: false)
            )
            await refreshSnapshot()
        } catch {
            lastError = error.localizedDescription
        }
    }

    func loadRateLimitsIfNeeded(serverId: String) async {
        guard let server = snapshot?.serverSnapshot(for: serverId), server.isConnected else { return }
        guard server.rateLimits == nil else { return }
        guard server.account != nil else { return }
        guard !loadingRateLimitServerIds.contains(serverId) else { return }
        loadingRateLimitServerIds.insert(serverId)
        defer { loadingRateLimitServerIds.remove(serverId) }
        do {
            _ = try await client.refreshRateLimits(serverId: serverId)
        } catch {
            lastError = error.localizedDescription
        }
    }

    private func hasFreshConversationMetadata(for serverId: String) -> Bool {
        guard let server = snapshot?.serverSnapshot(for: serverId) else { return false }
        let hasModels = server.availableModels != nil
        let hasRateLimits = server.account == nil || server.rateLimits != nil
        if hasModels && hasRateLimits {
            return true
        }

        guard let lastLoad = recentConversationMetadataLoads[serverId] else { return false }
        return Date().timeIntervalSince(lastLoad) < 10
    }
}
