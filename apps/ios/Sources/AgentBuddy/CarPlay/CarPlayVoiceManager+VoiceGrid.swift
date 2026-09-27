import CarPlay
import UIKit

extension CarPlayVoiceManager {
    // MARK: - Voice Tab (Grid)

    func voiceGridButtons() -> [CPGridButton] {
        let session = voiceActions.activeVoiceSession
        let phase = session?.phase
        var buttons: [CPGridButton] = []

        // Primary: Tap to Talk / End
        if let phase {
            buttons.append(makeGridButton(
                titles: [primaryActionLabel(for: phase)],
                systemImage: phase == .error ? "mic.fill" : "stop.fill"
            ) { [weak self] _ in
                self?.handleEnd()
            })
        } else {
            buttons.append(makeGridButton(
                titles: ["Tap to Talk"],
                systemImage: "mic.fill"
            ) { [weak self] _ in
                self?.handleStart()
            })
        }

        // Continue last session (visible when no active voice session but a recent one exists)
        if session == nil, let recent = mostRecentResumable() {
            buttons.append(makeGridButton(
                titles: ["Continue", String(recent.displayTitle.prefix(24))],
                systemImage: "arrow.uturn.backward.circle.fill"
            ) { [weak self] _ in
                self?.handleResume(recent.key)
            })
        }

        // Sessions shortcut
        buttons.append(makeGridButton(
            titles: ["Sessions"],
            systemImage: "list.bullet.rectangle.portrait"
        ) { [weak self] _ in
            self?.openSessionsTab()
        })

        // Transcript (only meaningful during an active session)
        if session != nil {
            buttons.append(makeGridButton(
                titles: ["Transcript"],
                systemImage: "text.bubble.fill"
            ) { [weak self] _ in
                self?.openActiveSession()
            })
        }

        return buttons
    }

    private func primaryActionLabel(for phase: VoiceSessionPhase) -> String {
        switch phase {
        case .connecting: return "Cancel"
        case .listening:  return "Stop"
        case .thinking, .handoff: return "Interrupt"
        case .speaking:   return "Done"
        case .error:      return "Tap to Talk"
        }
    }

    private func makeGridButton(
        titles: [String],
        systemImage: String,
        handler: @escaping (CPGridButton) -> Void
    ) -> CPGridButton {
        let image = UIImage(systemName: systemImage,
                            withConfiguration: UIImage.SymbolConfiguration(pointSize: 48, weight: .semibold))
            ?? UIImage()
        return CPGridButton(titleVariants: titles, image: image, handler: handler)
    }

    func refreshVoiceTab() {
        voiceTabTemplate?.updateGridButtons(voiceGridButtons())
    }
}
