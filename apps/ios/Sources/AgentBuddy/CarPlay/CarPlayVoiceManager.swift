import CarPlay
import MediaPlayer
import os
import UIKit

@MainActor
final class CarPlayVoiceManager {
    private static let log = Logger(subsystem: "com.akashark.agentbuddy", category: "CarPlay")

    let voiceActions: VoiceActions
    let appModel: AppModel
    weak var interfaceController: CPInterfaceController?

    var voiceTabTemplate: CPGridTemplate?
    var sessionsTabTemplate: CPListTemplate?
    var transcriptTemplate: CPListTemplate?
    var sessionTranscriptTemplate: CPListTemplate?
    var sessionTranscriptKey: ThreadKey?
    var lastSessionTranscriptSig: String?

    private var observationTask: Task<Void, Error>?
    var lastPhase: VoiceSessionPhase?
    var lastTranscriptHistoryID: String?
    var lastTranscriptLive: String?
    var lastSessionsSignature: String?
    var isShowingActiveSession = false
    var isShowingTranscript = false
    var lastActiveSessionPushFailureAt: Date?

    init(voiceActions: VoiceActions, appModel: AppModel, interfaceController: CPInterfaceController) {
        self.voiceActions = voiceActions
        self.appModel = appModel
        self.interfaceController = interfaceController
    }

    // MARK: - Tab Templates

    func buildVoiceTab() -> CPGridTemplate {
        let template = CPGridTemplate(title: "Voice", gridButtons: voiceGridButtons())
        template.tabImage = UIImage(systemName: "waveform")
        template.tabTitle = "Voice"
        voiceTabTemplate = template
        return template
    }

    func buildSessionsTab() -> CPListTemplate {
        let template = CPListTemplate(
            title: "Sessions",
            sections: [sessionsSection()]
        )
        template.tabImage = UIImage(systemName: "list.bullet")
        template.tabTitle = "Sessions"
        template.emptyViewTitleVariants = ["No recent sessions"]
        template.emptyViewSubtitleVariants = ["Start a voice session to see it here"]
        sessionsTabTemplate = template
        return template
    }

    // MARK: - Observation

    func startObserving() {
        observationTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(600))
                guard let self else { break }
                await self.tick()
            }
        }
    }

    func stopObserving() {
        observationTask?.cancel()
        observationTask = nil
    }

    private func tick() async {
        refreshSessionsIfNeeded()
        refreshSessionTranscriptIfNeeded()
        await refreshActiveSessionIfNeeded()
    }

    // MARK: - Actions

    func handleStart() {
        Task { @MainActor in
            if voiceActions.activeVoiceSession != nil {
                openActiveSession()
                return
            }
            do {
                let cwd = FileManager.default.urls(
                    for: .documentDirectory, in: .userDomainMask
                ).first?.path ?? "/"
                _ = try await voiceActions.startPinnedLocalVoiceCall(
                    cwd: cwd,
                    model: nil,
                    approvalPolicy: .never,
                    sandboxMode: nil
                )
                if let session = voiceActions.activeVoiceSession {
                    pushActiveSession(session)
                }
            } catch {
                showError(error.localizedDescription)
            }
        }
    }

    func handleResume(_ key: ThreadKey) {
        Task { @MainActor in
            do {
                _ = try await voiceActions.startVoiceOnThread(key)
                if let session = voiceActions.activeVoiceSession {
                    pushActiveSession(session)
                }
            } catch {
                showError(error.localizedDescription)
            }
        }
    }

    func handleEnd() {
        Task { @MainActor in
            await voiceActions.stopActiveVoiceSession()
        }
    }

    func openActiveSession() {
        guard let session = voiceActions.activeVoiceSession else { return }
        if !isShowingActiveSession {
            pushActiveSession(session)
            return
        }
        openTranscript()
    }

    func openSessionsTab() {
        guard let sessionsTemplate = sessionsTabTemplate,
              let root = interfaceController?.rootTemplate as? CPTabBarTemplate,
              let idx = root.templates.firstIndex(where: { $0 === sessionsTemplate }) else { return }
        root.selectTemplate(at: idx)
    }

    func mostRecentResumable() -> AppSessionSummary? {
        (appModel.snapshot?.sessionSummaries ?? [])
            .filter { !$0.isSubagent && $0.key.serverId == VoiceRuntimeController.localServerID }
            .sorted { ($0.updatedAt ?? 0) > ($1.updatedAt ?? 0) }
            .first
    }

    private func showError(_ message: String) {
        let action = CPAlertAction(title: "OK", style: .cancel) { _ in }
        let alert = CPAlertTemplate(
            titleVariants: [message],
            actions: [action]
        )
        presentTemplate(alert, context: "error alert")
    }

    // MARK: - Utilities

    func pushTemplate(
        _ template: CPTemplate,
        context: String,
        completion: ((Bool) -> Void)? = nil
    ) {
        guard let interfaceController else {
            Self.log.error("CarPlay cannot push \(context, privacy: .public): missing interface controller")
            completion?(false)
            return
        }
        interfaceController.pushTemplate(template, animated: true) { success, error in
            if !success {
                let message = error?.localizedDescription ?? "unknown error"
                Self.log.error("CarPlay failed to push \(context, privacy: .public): \(message, privacy: .public)")
            }
            Task { @MainActor in
                completion?(success)
            }
        }
    }

    private func presentTemplate(_ template: CPTemplate, context: String) {
        guard let interfaceController else {
            Self.log.error("CarPlay cannot present \(context, privacy: .public): missing interface controller")
            return
        }
        interfaceController.presentTemplate(template, animated: true) { success, error in
            guard !success else { return }
            let message = error?.localizedDescription ?? "unknown error"
            Self.log.error("CarPlay failed to present \(context, privacy: .public): \(message, privacy: .public)")
        }
    }

    func truncate(_ s: String, max: Int) -> String {
        let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.count > max ? String(trimmed.prefix(max)) + "…" : trimmed
    }

    func relativeTime(fromEpoch epoch: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epoch))
        let delta = Date().timeIntervalSince(date)
        if delta < 60 { return "now" }
        if delta < 3600 { return "\(Int(delta / 60))m ago" }
        if delta < 86400 { return "\(Int(delta / 3600))h ago" }
        return "\(Int(delta / 86400))d ago"
    }
}
