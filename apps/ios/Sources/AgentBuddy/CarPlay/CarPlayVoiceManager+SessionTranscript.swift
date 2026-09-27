import CarPlay
import UIKit

extension CarPlayVoiceManager {
    // MARK: - Session Transcript (pushed when a session row is tapped)

    func openSessionTranscript(_ summary: AppSessionSummary) {
        let template = buildSessionTranscriptTemplate(summary)
        sessionTranscriptTemplate = template
        sessionTranscriptKey = summary.key
        lastSessionTranscriptSig = transcriptSignature(for: summary.key)
        pushTemplate(template, context: "session transcript") { [weak self] success in
            guard !success else { return }
            self?.sessionTranscriptTemplate = nil
            self?.sessionTranscriptKey = nil
            self?.lastSessionTranscriptSig = nil
        }
    }

    private func transcriptSignature(for key: ThreadKey) -> String {
        let items = appModel.threadSnapshot(for: key)?.hydratedConversationItems ?? []
        // id + renderDigest (via content hash proxy) is enough to diff turn changes
        return items.suffix(30)
            .map { "\($0.id)|\($0.content.hashValue)" }
            .joined(separator: ";")
    }

    func refreshSessionTranscriptIfNeeded() {
        guard let key = sessionTranscriptKey,
              let template = sessionTranscriptTemplate,
              let ic = interfaceController else { return }

        // If the user popped the template, clear refs.
        let isStillPushed = ic.templates.contains(where: { $0 === template })
        if !isStillPushed {
            sessionTranscriptTemplate = nil
            sessionTranscriptKey = nil
            lastSessionTranscriptSig = nil
            return
        }

        let sig = transcriptSignature(for: key)
        guard sig != lastSessionTranscriptSig else { return }
        lastSessionTranscriptSig = sig

        guard let summary = appModel.snapshot?.sessionSummaries.first(where: { $0.key == key }) else { return }
        template.updateSections([sessionTranscriptSection(for: summary)])
    }

    private func buildSessionTranscriptTemplate(_ summary: AppSessionSummary) -> CPListTemplate {
        let title = String(summary.displayTitle.prefix(40))
        let template = CPListTemplate(
            title: title,
            sections: [sessionTranscriptSection(for: summary)]
        )
        // Only local sessions can be resumed as voice — hide the mic for remote.
        if summary.key.serverId == VoiceRuntimeController.localServerID {
            let voiceButton = CPBarButton(image: UIImage(systemName: "mic.fill") ?? UIImage()) { [weak self] _ in
                self?.handleResume(summary.key)
            }
            template.trailingNavigationBarButtons = [voiceButton]
        }
        return template
    }

    private func sessionTranscriptSection(for summary: AppSessionSummary) -> CPListSection {
        let thread = appModel.threadSnapshot(for: summary.key)
        let items = (thread?.hydratedConversationItems ?? [])
            .suffix(30) // most recent turns
            .compactMap { transcriptRow(for: $0) }
        if items.isEmpty {
            let empty = CPListItem(
                text: "No messages yet",
                detailText: "Tap the mic to start speaking",
                image: UIImage(systemName: "text.bubble")
            )
            return CPListSection(items: [empty])
        }
        return CPListSection(items: Array(items.reversed()))
    }

    private func transcriptRow(for item: HydratedConversationItem) -> CPListItem? {
        switch item.content {
        case .user(let data):
            return messageRow(role: "YOU", body: data.text)
        case .assistant(let data):
            return messageRow(role: "CODEX", body: data.text)
        case .reasoning(let data):
            let body = data.summary.first ?? data.content.first ?? ""
            return messageRow(role: "REASONING", body: body)
        case .commandExecution(let data):
            return messageRow(role: "COMMAND", body: data.command)
        case .fileChange(let data):
            let firstPath = data.changes.first?.path ?? ""
            let extra = data.changes.count > 1 ? " +\(data.changes.count - 1) more" : ""
            return messageRow(role: "EDIT", body: firstPath + extra)
        case .mcpToolCall(let data):
            return messageRow(role: "TOOL", body: "\(data.server) · \(data.tool)")
        case .dynamicToolCall(let data):
            return messageRow(role: "TOOL", body: data.tool)
        case .webSearch(let data):
            return messageRow(role: "SEARCH", body: data.query)
        case .error(let data):
            return messageRow(role: "ERROR", body: data.message)
        default:
            return nil
        }
    }

    /// Splits a message across a row's two single-line fields:
    ///   - If it fits in one line, `text` carries the body and `detailText` is the role label.
    ///   - If it overflows, `text` gets the first chunk and `detailText` gets the
    ///     continuation prefixed with the role. Role is never lost.
    private func messageRow(role: String, body: String) -> CPListItem? {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let mainCap = 110
        if trimmed.count <= mainCap {
            return CPListItem(text: trimmed, detailText: role)
        }
        let splitIdx = trimmed.index(trimmed.startIndex, offsetBy: mainCap)
        let main = String(trimmed[..<splitIdx])
        let remainder = String(trimmed[splitIdx...])
        let detailCap = 120 - role.count - 3 // "ROLE · "
        let remainderText: String
        if remainder.count <= detailCap {
            remainderText = remainder
        } else {
            let end = remainder.index(remainder.startIndex, offsetBy: detailCap - 1)
            remainderText = String(remainder[..<end]) + "…"
        }
        return CPListItem(text: main, detailText: "\(role) · \(remainderText)")
    }
}
