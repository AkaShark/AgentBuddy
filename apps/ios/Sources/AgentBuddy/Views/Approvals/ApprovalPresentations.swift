import SwiftUI

// MARK: - Result card

/// Shown after a decision (radius 18, soft surface). It describes what was
/// sent, not what the task has done; the timeline shows the real progress.
struct ApprovalResultCard: View {
    let outcome: ApprovalOutcome
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: BuddySpacing.sm) {
            Image(systemName: ApprovalCopy.outcomeSymbol(outcome.kind))
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text(ApprovalCopy.outcomeTitle(outcome.kind))
                    .buddyText(.heading)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                Text(ApprovalCopy.outcomeDetail(outcome.kind))
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            BuddyIconButton(systemImage: "xmark", accessibilityLabel: "Dismiss", tone: .plain, iconSize: 14, action: onDismiss)
                .padding(.top, -BuddySpacing.sm)
                .padding(.trailing, -BuddySpacing.sm)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.soft, radius: BuddyRadius.resultCard, padding: BuddySpacing.lg)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - In-conversation stack

/// Approvals for the open conversation, rendered inline above the composer.
/// Requests are handled one at a time with a visible count; the most recent
/// outcome replaces the card once the queue is empty.
struct ConversationApprovalStack: View {
    let threadKey: ThreadKey
    @Environment(AppModel.self) private var appModel
    @Environment(ApprovalCoordinator.self) private var coordinator: ApprovalCoordinator?

    private var pending: [PendingApproval] {
        (appModel.snapshot?.pendingApprovals ?? []).filter { $0.isAnswerableOnPhone && $0.belongs(to: threadKey) }
    }

    private var hostName: String? {
        appModel.snapshot?.serverSnapshot(for: threadKey.serverId)?.displayName
    }

    var body: some View {
        if let coordinator {
            content(coordinator)
        }
    }

    private func content(_ coordinator: ApprovalCoordinator) -> some View {
        let pending = pending
        return Group {
            if let first = pending.first {
                ApprovalCard(
                    approval: first,
                    hostName: hostName,
                    position: (1, pending.count),
                    submittingDecision: coordinator.submitting[first.id],
                    failureMessage: coordinator.failures[first.id],
                    onDecision: { decision in
                        Task {
                            await coordinator.submit(first, decision: decision) { id, decision in
                                try await appModel.store.respondToApproval(requestId: id, decision: decision)
                            }
                        }
                    }
                )
                .id(first.id)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            } else if let outcome = coordinator.outcome(for: threadKey) {
                ApprovalResultCard(outcome: outcome) {
                    coordinator.dismissOutcome(for: threadKey)
                }
                .transition(.opacity)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .animation(.easeOut(duration: BuddyMotion.Kind.state.duration), value: pending.map(\.id))
    }
}

// MARK: - Banner outside the conversation

/// Non-blocking banner for requests that belong to a conversation that is not
/// on screen. Expanding it shows the same card so the user can decide in
/// place; closing it only hides the banner until a new request arrives and
/// never counts as a denial.
struct PendingApprovalBanner: View {
    let visibleThreadKey: ThreadKey?
    let onViewThread: (ThreadKey) -> Void
    @Environment(AppModel.self) private var appModel
    @Environment(ApprovalCoordinator.self) private var coordinator: ApprovalCoordinator?
    @State private var isExpanded = false
    @State private var hiddenIds: Set<String> = []

    private var pending: [PendingApproval] {
        (appModel.snapshot?.pendingApprovals ?? []).filter { approval in
            guard approval.isAnswerableOnPhone else { return false }
            if let visibleThreadKey, approval.belongs(to: visibleThreadKey) { return false }
            return true
        }
    }

    var body: some View {
        if let coordinator {
            content(coordinator)
        }
    }

    @ViewBuilder
    private func content(_ coordinator: ApprovalCoordinator) -> some View {
        let pending = pending
        let visible = pending.filter { !hiddenIds.contains($0.id) }
        if let first = visible.first {
            VStack(spacing: BuddySpacing.sm) {
                if isExpanded {
                    ApprovalCard(
                        approval: first,
                        hostName: appModel.snapshot?.serverSnapshot(for: first.serverId)?.displayName,
                        position: (1, visible.count),
                        submittingDecision: coordinator.submitting[first.id],
                        failureMessage: coordinator.failures[first.id],
                        onViewThread: first.threadId.map { threadId in
                            { onViewThread(ThreadKey(serverId: first.serverId, threadId: threadId)) }
                        },
                        onDecision: { decision in
                            Task {
                                await coordinator.submit(first, decision: decision) { id, decision in
                                    try await appModel.store.respondToApproval(requestId: id, decision: decision)
                                }
                            }
                        }
                    )
                } else {
                    collapsedBanner(count: visible.count, approval: first)
                }
            }
            .padding(.horizontal, BuddySpacing.md)
            .shadow(color: AgentBuddyTheme.floatingShadow, radius: 12, y: 8)
            .transition(.move(edge: .top).combined(with: .opacity))
            .onChange(of: pending.map(\.id)) { _, ids in
                hiddenIds.formIntersection(ids)
                if ids.isEmpty { isExpanded = false }
            }
        }
    }

    private func collapsedBanner(count: Int, approval: PendingApproval) -> some View {
        HStack(spacing: BuddySpacing.sm) {
            Image(systemName: "checkmark.shield")
                .foregroundStyle(AgentBuddyTheme.warning)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(count) waiting for your OK")
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                Text(ApprovalCopy.title(for: approval.kind))
                    .buddyText(.caption)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: BuddySpacing.xs)
            Button {
                isExpanded = true
            } label: {
                Text("Review")
                    .buddyText(.label, weight: .semibold)
                    .foregroundStyle(AgentBuddyTheme.onAction)
                    .padding(.horizontal, BuddySpacing.md)
                    .frame(minHeight: 36)
                    .background(AgentBuddyTheme.action, in: Capsule())
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            BuddyIconButton(systemImage: "xmark", accessibilityLabel: "Hide for now", tone: .plain, iconSize: 13) {
                hiddenIds.formUnion(pending.map(\.id))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .padding(.trailing, BuddySpacing.xxs)
        .padding(.vertical, BuddySpacing.xxs)
        .background(AgentBuddyTheme.warningSurface, in: RoundedRectangle(cornerRadius: BuddyRadius.detailCard, style: .continuous))
        .accessibilityElement(children: .contain)
    }
}
