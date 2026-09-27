import Foundation
import Observation

/// What the user last saw happen to an approval on a thread. Used for the
/// inline result card; the task state itself still comes from Rust.
struct ApprovalOutcome: Equatable {
    enum Kind: Equatable {
        case allowedOnce
        case allowedForSession
        case denied
        case aborted
        /// The request left the queue without a decision from this phone
        /// (answered on another device, or it expired upstream).
        case resolvedElsewhere
    }

    let approvalId: String
    let kind: Kind
    let approvalKind: ApprovalKind
    let date: Date
}

/// UI-side bookkeeping for approval decisions: one submission per request,
/// visible failures with retry, and a short-lived outcome per thread.
/// The decision itself is always sent to the shared Rust store; this type
/// never marks a request resolved on its own.
@MainActor
@Observable
final class ApprovalCoordinator {
    typealias Responder = (_ requestId: String, _ decision: ApprovalDecisionValue) async throws -> Void

    /// Request ids with a decision in flight, and the decision being sent.
    private(set) var submitting: [String: ApprovalDecisionValue] = [:]
    /// Last submission error per request id.
    private(set) var failures: [String: String] = [:]
    /// Most recent outcome per thread (keyed by "serverId/threadId").
    private(set) var outcomes: [String: ApprovalOutcome] = [:]

    @ObservationIgnored private var lastPending: [String: PendingApproval] = [:]
    @ObservationIgnored private var decidedLocally: Set<String> = []

    func isSubmitting(_ approval: PendingApproval) -> Bool {
        submitting[approval.id] != nil
    }

    func submit(_ approval: PendingApproval, decision: ApprovalDecisionValue, using respond: Responder) async {
        guard submitting[approval.id] == nil else { return }
        submitting[approval.id] = decision
        failures[approval.id] = nil
        do {
            try await respond(approval.id, decision)
            decidedLocally.insert(approval.id)
            if let key = Self.threadKey(for: approval) {
                outcomes[key] = ApprovalOutcome(
                    approvalId: approval.id,
                    kind: Self.outcomeKind(for: decision),
                    approvalKind: approval.kind,
                    date: Date()
                )
            }
        } catch {
            failures[approval.id] = error.localizedDescription
        }
        submitting[approval.id] = nil
    }

    /// Call whenever the snapshot's pending approvals change. Requests that
    /// disappear without a local decision are reported as handled elsewhere.
    func reconcile(pending: [PendingApproval]) {
        let current = Dictionary(pending.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        for (id, approval) in lastPending where current[id] == nil {
            failures[id] = nil
            if decidedLocally.remove(id) == nil, let key = Self.threadKey(for: approval) {
                outcomes[key] = ApprovalOutcome(
                    approvalId: id,
                    kind: .resolvedElsewhere,
                    approvalKind: approval.kind,
                    date: Date()
                )
            }
        }
        lastPending = current
    }

    func outcome(for threadKey: ThreadKey) -> ApprovalOutcome? {
        outcomes["\(threadKey.serverId)/\(threadKey.threadId)"]
    }

    func dismissOutcome(for threadKey: ThreadKey) {
        outcomes["\(threadKey.serverId)/\(threadKey.threadId)"] = nil
    }

    static func threadKey(for approval: PendingApproval) -> String? {
        approval.threadId.map { "\(approval.serverId)/\($0)" }
    }

    static func outcomeKind(for decision: ApprovalDecisionValue) -> ApprovalOutcome.Kind {
        switch decision {
        case .accept: return .allowedOnce
        case .acceptForSession: return .allowedForSession
        case .decline: return .denied
        case .cancel: return .aborted
        }
    }
}

extension PendingApproval {
    /// Requests the phone can render and answer. MCP elicitations carry no
    /// form schema in `PendingApproval`, so they stay with the host UI.
    var isAnswerableOnPhone: Bool { kind != .mcpElicitation }

    func belongs(to key: ThreadKey) -> Bool {
        serverId == key.serverId && threadId == key.threadId
    }
}
