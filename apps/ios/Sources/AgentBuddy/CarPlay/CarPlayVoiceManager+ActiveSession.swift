import CarPlay
import UIKit

extension CarPlayVoiceManager {
    func pushActiveSession(_ session: VoiceSessionState) {
        guard let interfaceController else { return }
        configureNowPlayingTemplate(session)
        updateNowPlayingInfo(session)
        lastPhase = session.phase
        lastTranscriptHistoryID = session.transcriptHistory.last?.id
        lastTranscriptLive = session.transcriptText
        if let template = transcriptTemplate,
           isShowingActiveSession,
           interfaceController.templates.contains(where: { $0 === template }) {
            template.updateSections(transcriptSections(session))
            lastActiveSessionPushFailureAt = nil
            return
        }
        if let lastActiveSessionPushFailureAt,
           Date().timeIntervalSince(lastActiveSessionPushFailureAt) < 5 {
            return
        }
        let template = buildTranscriptTemplate(session)
        transcriptTemplate = template
        pushTemplate(template, context: "active voice session") { [weak self] success in
            self?.isShowingActiveSession = success
            self?.isShowingTranscript = success
            if !success {
                self?.transcriptTemplate = nil
            }
            self?.lastActiveSessionPushFailureAt = success ? nil : Date()
        }
    }

    func openTranscript() {
        guard let session = voiceActions.activeVoiceSession, !isShowingTranscript else { return }
        if isShowingActiveSession {
            isShowingTranscript = true
            return
        }
        let template = buildTranscriptTemplate(session)
        transcriptTemplate = template
        pushTemplate(template, context: "active transcript") { [weak self] success in
            self?.isShowingTranscript = success
            self?.isShowingActiveSession = success
            if !success {
                self?.transcriptTemplate = nil
            }
        }
    }

    private func updateActiveSession(_ session: VoiceSessionState) {
        updateNowPlayingInfo(session)
        configureNowPlayingTemplate(session)
        if let template = transcriptTemplate, isShowingTranscript {
            template.updateSections(transcriptSections(session))
        }
    }

    func refreshActiveSessionIfNeeded() async {
        let session = voiceActions.activeVoiceSession

        if let session {
            if !isShowingActiveSession {
                pushActiveSession(session)
            } else {
                guard let template = transcriptTemplate,
                      interfaceController?.templates.contains(where: { $0 === template }) == true else {
                    isShowingActiveSession = false
                    isShowingTranscript = false
                    transcriptTemplate = nil
                    pushActiveSession(session)
                    refreshVoiceTab()
                    return
                }
                let historyID = session.transcriptHistory.last?.id
                if session.phase != lastPhase
                    || historyID != lastTranscriptHistoryID
                    || session.transcriptText != lastTranscriptLive {
                    updateActiveSession(session)
                    lastPhase = session.phase
                    lastTranscriptHistoryID = historyID
                    lastTranscriptLive = session.transcriptText
                }
            }
            refreshVoiceTab()
        } else if isShowingActiveSession {
            isShowingActiveSession = false
            isShowingTranscript = false
            transcriptTemplate = nil
            lastPhase = nil
            lastTranscriptHistoryID = nil
            lastTranscriptLive = nil
            clearNowPlayingInfo()
            _ = try? await interfaceController?.popToRootTemplate(animated: true)
            refreshVoiceTab()
        }
    }
}
