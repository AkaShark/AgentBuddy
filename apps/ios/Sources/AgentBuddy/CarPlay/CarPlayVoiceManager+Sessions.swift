import CarPlay
import UIKit

extension CarPlayVoiceManager {
    // MARK: - Sessions Tab

    func sessionsSection() -> CPListSection {
        let sorted = (appModel.snapshot?.sessionSummaries ?? [])
            .filter { !$0.isSubagent }
            .sorted { ($0.updatedAt ?? 0) > ($1.updatedAt ?? 0) }
            .prefix(12)

        var items: [CPListItem] = []
        let activeKey = voiceActions.activeVoiceSession?.threadKey

        for summary in sorted {
            items.append(makeSessionItem(summary, isActive: summary.key == activeKey))
        }
        if items.isEmpty {
            let placeholder = CPListItem(
                text: "No recent sessions",
                detailText: "Start a voice session from the Voice tab",
                image: UIImage(systemName: "waveform")
            )
            items.append(placeholder)
        }
        return CPListSection(items: items)
    }

    private func makeSessionItem(_ summary: AppSessionSummary, isActive: Bool) -> CPListItem {
        let title = String(summary.displayTitle.prefix(60))
        let detail = sessionDetail(summary, isActive: isActive)
        let image = UIImage(systemName: sessionStateSymbol(summary, isActive: isActive))
        let item = CPListItem(text: title, detailText: detail, image: image)
        if isActive || summary.hasActiveTurn {
            item.isPlaying = true
            item.playingIndicatorLocation = .trailing
        }
        item.accessoryType = .disclosureIndicator
        item.handler = { [weak self] _, completion in
            self?.openSessionTranscript(summary)
            completion()
        }
        return item
    }

    private func sessionDetail(_ summary: AppSessionSummary, isActive: Bool) -> String {
        let server = summary.key.serverId == VoiceRuntimeController.localServerID
            ? "local"
            : summary.serverDisplayName
        let modelLabel = summary.sessionModelLabel ?? summary.model
        let state: String = {
            if isActive { return "now" }
            if summary.hasActiveTurn { return "working" }
            if let updated = summary.updatedAt {
                return relativeTime(fromEpoch: updated)
            }
            return "idle"
        }()
        var parts = [server]
        if !modelLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            parts.append(modelLabel)
        }
        parts.append(state)
        return parts.joined(separator: " · ")
    }

    private func sessionStateSymbol(_ summary: AppSessionSummary, isActive: Bool) -> String {
        if isActive { return "waveform.circle.fill" }
        if summary.hasActiveTurn { return "circle.hexagongrid.circle.fill" }
        if summary.key.serverId == VoiceRuntimeController.localServerID {
            return "laptopcomputer"
        }
        return "server.rack"
    }

    private func sessionsSignature() -> String {
        let active = voiceActions.activeVoiceSession?.threadKey
        let summaries = (appModel.snapshot?.sessionSummaries ?? [])
            .filter { !$0.isSubagent }
            .sorted { ($0.updatedAt ?? 0) > ($1.updatedAt ?? 0) }
            .prefix(12)
        let parts = summaries.map { s -> String in
            let isActive = s.key == active
            return "\(s.key.serverId)|\(s.key.threadId)|\(s.updatedAt ?? 0)|\(s.hasActiveTurn ? 1 : 0)|\(isActive ? 1 : 0)|\(s.displayTitle.prefix(40))"
        }
        return parts.joined(separator: ";")
    }

    func refreshSessionsIfNeeded() {
        let sig = sessionsSignature()
        guard sig != lastSessionsSignature else { return }
        lastSessionsSignature = sig
        sessionsTabTemplate?.updateSections([sessionsSection()])
    }
}
