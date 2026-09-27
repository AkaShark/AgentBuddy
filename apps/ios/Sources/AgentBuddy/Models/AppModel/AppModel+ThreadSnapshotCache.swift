import Foundation

extension AppModel {
    func threadSnapshot(for key: ThreadKey) -> AppThreadSnapshot? {
        snapshot?.threadSnapshot(for: key) ?? cachedThreadSnapshots[key]
    }

    func restoreCachedThreadSnapshotIfNeeded(for key: ThreadKey?) {
        guard let key,
              snapshot?.threadSnapshot(for: key) == nil,
              let cached = cachedThreadSnapshots[key] else {
            return
        }
        applyThreadSnapshot(cached)
    }

    func cacheThreadSnapshot(_ thread: AppThreadSnapshot) {
        cachedThreadSnapshots[thread.key] = thread
    }

    func mergedThreadSnapshotPreservingHydratedItems(_ thread: AppThreadSnapshot) -> AppThreadSnapshot {
        guard let cached = cachedThreadSnapshots[thread.key],
              !cached.hydratedConversationItems.isEmpty else {
            return thread
        }

        if thread.hydratedConversationItems.count < cached.hydratedConversationItems.count {
            LLog.warn("streaming", "threadUpsert arrived with fewer items than cached", fields: [
                "threadId": thread.key.threadId,
                "incoming": thread.hydratedConversationItems.count,
                "cached": cached.hydratedConversationItems.count,
                "status": String(describing: thread.info.status)
            ])
        }

        // Incoming has no items → use cached items entirely.
        if thread.hydratedConversationItems.isEmpty {
            var merged = thread
            merged.hydratedConversationItems = cached.hydratedConversationItems
            return merged
        }

        return thread
    }

    func mergingCachedThreadSnapshots(_ snapshot: AppSnapshotRecord) -> AppSnapshotRecord {
        var snapshot = snapshot

        for index in snapshot.threads.indices {
            let thread = snapshot.threads[index]
            snapshot.threads[index] = mergedThreadSnapshotPreservingHydratedItems(thread)
        }

        for (key, cached) in cachedThreadSnapshots {
            guard snapshot.threads.contains(where: { $0.key == key }) == false else { continue }
            guard snapshot.activeThread == key ||
                  snapshot.sessionSummaries.contains(where: { $0.key == key }) else {
                continue
            }
            snapshot.threads.append(cached)
        }

        return snapshot
    }
}
