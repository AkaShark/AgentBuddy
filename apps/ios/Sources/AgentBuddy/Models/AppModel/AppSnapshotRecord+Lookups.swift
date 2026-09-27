import Foundation

extension AppSnapshotRecord {
    func threadSnapshot(for key: ThreadKey) -> AppThreadSnapshot? {
        if let idx = threads.firstIndex(where: { $0.key == key }) {
            return threads[idx]
        }
        return nil
    }

    func serverSnapshot(for serverId: String) -> AppServerSnapshot? {
        servers.first { $0.serverId == serverId }
    }

    func sessionSummary(for key: ThreadKey) -> AppSessionSummary? {
        sessionSummaries.first { $0.key == key }
    }

    func resolvedThreadKey(for receiverId: String, serverId: String) -> ThreadKey? {
        guard let normalized = AgentLabelFormatter.sanitized(receiverId) else { return nil }
        if let summary = sessionSummaries.first(where: {
            $0.key.serverId == serverId && $0.key.threadId == normalized
        }) {
            return summary.key
        }
        return ThreadKey(serverId: serverId, threadId: normalized)
    }

    func resolvedAgentTargetLabel(for target: String, serverId: String) -> String? {
        if AgentLabelFormatter.looksLikeDisplayLabel(target) {
            return AgentLabelFormatter.sanitized(target)
        }
        guard let normalized = AgentLabelFormatter.sanitized(target) else { return nil }
        if let summary = sessionSummaries.first(where: {
            $0.key.serverId == serverId && $0.key.threadId == normalized
        }) {
            return summary.agentDisplayLabel ?? AgentLabelFormatter.sanitized(target)
        }
        return nil
    }
}
