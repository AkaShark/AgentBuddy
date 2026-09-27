import CarPlay
import MediaPlayer
import UIKit

extension CarPlayVoiceManager {
    // MARK: - Active Session (CPNowPlayingTemplate immersive view)

    func configureNowPlayingTemplate(_ session: VoiceSessionState) {
        let template = CPNowPlayingTemplate.shared
        template.isUpNextButtonEnabled = false
        template.isAlbumArtistButtonEnabled = false
        template.updateNowPlayingButtons(nowPlayingButtons(session))
    }

    private func nowPlayingButtons(_ session: VoiceSessionState) -> [CPNowPlayingButton] {
        let transcriptImg = UIImage(systemName: "text.bubble.fill") ?? UIImage()
        let endImg = UIImage(systemName: "xmark.circle.fill") ?? UIImage()
        let speakerImg = UIImage(systemName: session.route.iconName) ?? UIImage()

        let transcriptBtn = CPNowPlayingImageButton(image: transcriptImg) { [weak self] _ in
            self?.openTranscript()
        }
        let speakerBtn = CPNowPlayingImageButton(image: speakerImg) { [weak self] _ in
            Task { try? await self?.voiceActions.toggleActiveVoiceSessionSpeaker() }
        }
        let endBtn = CPNowPlayingImageButton(image: endImg) { [weak self] _ in
            Task { await self?.voiceActions.stopActiveVoiceSession() }
        }
        return [transcriptBtn, speakerBtn, endBtn]
    }

    func updateNowPlayingInfo(_ session: VoiceSessionState) {
        let center = MPNowPlayingInfoCenter.default()
        var info = center.nowPlayingInfo ?? [:]
        info[MPMediaItemPropertyTitle] = session.phase.displayTitle
        info[MPMediaItemPropertyArtist] = session.threadTitle
        let sub = [session.model, session.route.label]
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: " · ")
        info[MPMediaItemPropertyAlbumTitle] = sub
        info[MPNowPlayingInfoPropertyIsLiveStream] = true
        info[MPNowPlayingInfoPropertyPlaybackRate] = 1.0
        center.nowPlayingInfo = info
        center.playbackState = .playing
    }

    func clearNowPlayingInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        MPNowPlayingInfoCenter.default().playbackState = .stopped
    }

    // MARK: - Transcript (pushed on top of CPNowPlayingTemplate)

    func buildTranscriptTemplate(_ session: VoiceSessionState) -> CPListTemplate {
        let template = CPListTemplate(
            title: session.phase.displayTitle,
            sections: transcriptSections(session)
        )
        let end = CPBarButton(image: UIImage(systemName: "xmark.circle.fill") ?? UIImage()) { [weak self] _ in
            Task { await self?.voiceActions.stopActiveVoiceSession() }
        }
        template.trailingNavigationBarButtons = [end]
        return template
    }

    func transcriptSections(_ session: VoiceSessionState) -> [CPListSection] {
        // Status row — phase + route, glanceable.
        let statusItem = CPListItem(
            text: session.phase.displayTitle,
            detailText: "\(session.model) · \(session.route.label)",
            image: UIImage(systemName: activePhaseSymbol(session.phase))
        )
        if session.phase == .listening || session.phase == .thinking
            || session.phase == .speaking || session.phase == .handoff {
            statusItem.isPlaying = true
            statusItem.playingIndicatorLocation = .leading
        }
        let statusSection = CPListSection(
            items: [statusItem],
            header: "status",
            sectionIndexTitle: nil
        )

        // Transcript — recent turns.
        let turns = session.transcriptHistory.suffix(6).reversed()
        var transcriptItems: [CPListItem] = []
        for entry in turns {
            let text = truncate(entry.text, max: 110)
            let item = CPListItem(text: text, detailText: entry.speaker.uppercased())
            transcriptItems.append(item)
        }
        // Live in-flight turn
        if let live = session.transcriptText?.trimmingCharacters(in: .whitespacesAndNewlines),
           !live.isEmpty {
            let speaker = (session.transcriptSpeaker?.uppercased() ?? "…")
            let item = CPListItem(text: truncate(live, max: 110), detailText: speaker + " · live")
            item.isPlaying = true
            item.playingIndicatorLocation = .leading
            transcriptItems.insert(item, at: 0)
        }
        if transcriptItems.isEmpty {
            transcriptItems.append(CPListItem(
                text: emptyTranscriptText(session.phase),
                detailText: nil
            ))
        }
        let transcriptSection = CPListSection(
            items: transcriptItems,
            header: "transcript",
            sectionIndexTitle: nil
        )

        return [statusSection, transcriptSection]
    }

    private func activePhaseSymbol(_ phase: VoiceSessionPhase) -> String {
        switch phase {
        case .connecting: return "antenna.radiowaves.left.and.right"
        case .listening:  return "ear.fill"
        case .thinking:   return "cpu"
        case .handoff:    return "hammer.fill"
        case .speaking:   return "waveform"
        case .error:      return "exclamationmark.triangle.fill"
        }
    }

    private func emptyTranscriptText(_ phase: VoiceSessionPhase) -> String {
        switch phase {
        case .connecting: return "Connecting…"
        case .listening:  return "Listening — say something"
        case .thinking:   return "Codex is thinking"
        case .handoff:    return "Running tools"
        case .speaking:   return "Codex is speaking"
        case .error:      return "Session ended"
        }
    }
}
