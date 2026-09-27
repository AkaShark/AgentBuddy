import ActivityKit
import AVFoundation
import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class VoiceRuntimeController: VoiceActions {
    static let shared = VoiceRuntimeController()
    static let localServerID = "local"
    static let persistedLocalVoiceThreadIDKey = "agentbuddy.voice.local.thread_id"

    var activeVoiceSession: VoiceSessionState?
    /// Tracks the local mic mute state for the active realtime session.
    /// Reset to `false` on every session start/end. Disabling the local
    /// `RTCAudioTrack` doesn't renegotiate the peer connection — Codex
    /// just stops receiving audio frames until we re-enable it.
    var isMicrophoneMuted: Bool = false
    var handoffModel: String?
    var handoffEffort: String?
    var handoffFastMode = false

    @ObservationIgnored weak var appModel: AppModel?
    @ObservationIgnored var realtimeSession: RealtimeWebRtcSession?
    @ObservationIgnored lazy var handoffManager = RustHandoffManager(localServerId: Self.localServerID)
    @ObservationIgnored private var updateSubscription: AppStoreSubscription?
    @ObservationIgnored private var eventTask: Task<Void, Never>?
    @ObservationIgnored var handoffActionPollTask: Task<Void, Never>?
    @ObservationIgnored var voiceCallActivity: Activity<CodexVoiceCallAttributes>?
    @ObservationIgnored var voiceInputDecayToken: UUID?
    @ObservationIgnored var voiceOutputDecayToken: UUID?
    @ObservationIgnored var voiceStopRequestedThreadKey: ThreadKey?
    @ObservationIgnored var lastHandledVoiceEndRequestToken: String?

    init() {
        installVoiceSessionControlObserver()
    }

    deinit {
        eventTask?.cancel()
        handoffActionPollTask?.cancel()
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        let observer = Unmanaged.passUnretained(self).toOpaque()
        let name = CFNotificationName(VoiceSessionControl.endRequestDarwinNotification as CFString)
        CFNotificationCenterRemoveObserver(center, observer, name, nil)
    }

    func bind(appModel: AppModel) {
        let shouldStartEventLoop = self.appModel !== appModel || eventTask == nil || updateSubscription == nil
        self.appModel = appModel
        syncHandoffServers()
        if shouldStartEventLoop {
            startEventLoopIfNeeded(appModel: appModel)
        }
    }

    @discardableResult
    func startPinnedLocalVoiceCall(
        cwd: String,
        model: String?,
        approvalPolicy: AppAskForApproval?,
        sandboxMode: AppSandboxMode?
    ) async throws -> ThreadKey {
        if let existing = activeVoiceSession, existing.phase != .error {
            return existing.threadKey
        }
        if activeVoiceSession != nil { endVoiceSessionImmediately() }
        let key = try await ensurePinnedLocalVoiceThread(
            cwd: cwd,
            model: model,
            approvalPolicy: approvalPolicy,
            sandboxMode: sandboxMode
        )
        return try await prepareAndLaunchRealtimeVoiceSession(for: key, model: model)
    }

    @discardableResult
    func startVoiceOnThread(_ key: ThreadKey) async throws -> ThreadKey {
        if let existing = activeVoiceSession, existing.phase != .error {
            return existing.threadKey
        }
        if activeVoiceSession != nil { endVoiceSessionImmediately() }
        guard key.serverId == Self.localServerID else {
            throw NSError(
                domain: "AgentBuddy",
                code: 3310,
                userInfo: [NSLocalizedDescriptionKey: "Voice is only available on the local server"]
            )
        }
        return try await prepareAndLaunchRealtimeVoiceSession(for: key)
    }

    func stopActiveVoiceSession() async {
        guard let session = activeVoiceSession else { return }
        let key = session.threadKey
        guard voiceStopRequestedThreadKey != key else { return }
        voiceStopRequestedThreadKey = key
        updateVoiceSessionForPendingStop(key)

        guard isServerConnected(key.serverId) else {
            voiceStopRequestedThreadKey = nil
            endVoiceSessionImmediately()
            return
        }

        do {
            _ = try await requireAppModel().client.stopRealtimeSession(
                serverId: key.serverId,
                params: AppStopRealtimeSessionRequest(threadId: key.threadId)
            )
            if voiceStopRequestedThreadKey == key {
                voiceStopRequestedThreadKey = nil
                endVoiceSessionImmediately()
            }
        } catch {
            voiceStopRequestedThreadKey = nil
            failVoiceSession("Failed to hang up: \(error.localizedDescription)")
        }
    }

    func toggleActiveVoiceSessionSpeaker() async throws {
        guard activeVoiceSession != nil else { return }
        try realtimeSession?.toggleSpeaker()
    }

    /// Drive the local mic mute state on the active realtime session.
    /// No-op when there is no active session — the next session will start
    /// unmuted via the reset in `prepareAndLaunchRealtimeVoiceSession`.
    func setMicrophoneMuted(_ muted: Bool) {
        guard activeVoiceSession != nil else { return }
        isMicrophoneMuted = muted
        realtimeSession?.setMicrophoneMuted(muted)
    }

    private func startEventLoopIfNeeded(appModel: AppModel) {
        guard eventTask == nil else { return }
        updateSubscription = appModel.store.subscribeUpdates()
        eventTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled, let subscription = self.updateSubscription {
                do {
                    let event = try await subscription.nextUpdate()
                    await MainActor.run {
                        self.handleUpdate(event)
                    }
                } catch {
                    if Task.isCancelled { break }
                    break
                }
            }
        }
    }

    private func handleUpdate(_ event: AppStoreUpdateRecord) {
        switch event {
        case .fullResync, .voiceSessionChanged:
            guard let key = activeVoiceSession?.threadKey else { return }
            // Realtime start/close updates can coalesce into FullResync, so
            // voice must reconcile from the shared snapshot rather than rely
            // on the dedicated RealtimeStarted/RealtimeClosed events alone.
            scheduleSharedVoiceSessionSync(for: key)
        case .realtimeStarted(let key, let notification):
            handleRealtimeStarted(key: key, notification: notification)
        case .realtimeSdp(let key, let notification):
            handleRealtimeSdp(key: key, notification: notification)
        case .realtimeTranscriptUpdated(let key, let update):
            handleRealtimeTranscriptUpdated(key: key, update: update)
        case .realtimeHandoffRequested(let key, let request):
            handleRealtimeHandoffRequested(key: key, request: request)
        case .realtimeSpeechStarted(let key):
            handleRealtimeSpeechStarted(key: key)
        case .realtimeOutputAudioDelta(let key, let notification):
            handleRealtimeOutputAudioDelta(key: key, notification: notification)
        case .realtimeError(let key, let notification):
            handleRealtimeError(key: key, notification: notification)
        case .realtimeClosed(let key, let notification):
            handleRealtimeClosed(key: key, notification: notification)
        default:
            break
        }
    }

    func requireAppModel() -> AppModel {
        if let appModel {
            return appModel
        }
        let appModel = AppModel.shared
        bind(appModel: appModel)
        return appModel
    }

    func isServerConnected(_ serverId: String) -> Bool {
        appModel?.snapshot?.serverSnapshot(for: serverId)?.isConnected == true
    }
}
