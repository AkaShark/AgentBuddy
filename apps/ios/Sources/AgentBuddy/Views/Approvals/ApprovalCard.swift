import SwiftUI
import UIKit

/// Inline confirmation card (radius 20, warning surface). Shows exactly what
/// will run and where, offers "Deny" / "Allow once" as the decision, keeps
/// session-wide approval as an explicit secondary choice, and blocks repeat
/// taps while a decision is in flight.
struct ApprovalCard: View {
    let approval: PendingApproval
    var hostName: String?
    var position: (index: Int, total: Int)?
    var submittingDecision: ApprovalDecisionValue?
    var failureMessage: String?
    var onViewThread: (() -> Void)?
    let onDecision: (ApprovalDecisionValue) -> Void

    @State private var lastDecision: ApprovalDecisionValue?
    @State private var showsSessionExplanation = false

    private var isSubmitting: Bool { submittingDecision != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.md) {
            header
            ApprovalDetails(approval: approval, hostName: hostName)
            if let failureMessage {
                BuddyBanner(
                    tone: .danger,
                    message: Text("Couldn't send your decision: \(failureMessage)"),
                    actionTitle: "Retry",
                    action: lastDecision.map { decision in { onDecision(decision) } }
                )
            }
            actions
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.warning, radius: BuddyRadius.confirmCard, padding: BuddySpacing.lg)
        .accessibilityElement(children: .contain)
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
            Image(systemName: "checkmark.shield")
                .foregroundStyle(AgentBuddyTheme.warning)
                .accessibilityHidden(true)
            Text(ApprovalCopy.title(for: approval.kind))
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.warning)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: BuddySpacing.xs)
            if let position, position.total > 1 {
                Text("\(position.index) of \(position.total)")
                    .buddyText(.caption, weight: .medium)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.warning)
            }
        }
    }

    private var actions: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(spacing: BuddySpacing.sm) {
                BuddyButton(
                    "Deny",
                    kind: .secondary,
                    isLoading: submittingDecision == .decline,
                    action: { decide(.decline) }
                )
                BuddyButton(
                    "Allow once",
                    trailingSystemImage: "arrow.right",
                    kind: .primary,
                    isLoading: submittingDecision == .accept,
                    action: { decide(.accept) }
                )
            }
            .disabled(isSubmitting)

            HStack(spacing: 0) {
                Button {
                    showsSessionExplanation = true
                } label: {
                    Text("Allow for this session…")
                        .buddyText(.label, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                        .frame(minHeight: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                Spacer()
                if let onViewThread {
                    Button(action: onViewThread) {
                        Text("View task")
                            .buddyText(.label, weight: .semibold)
                            .foregroundStyle(AgentBuddyTheme.link)
                            .frame(minHeight: BuddySize.minHitTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                Menu {
                    Button(role: .destructive) {
                        decide(.cancel)
                    } label: {
                        Label("Stop the task instead", systemImage: "stop.circle")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.warning)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("More choices"))
            }
            .disabled(isSubmitting)
        }
        .confirmationDialog(
            Text("Allow for this session?"),
            isPresented: $showsSessionExplanation,
            titleVisibility: .visible
        ) {
            Button("Allow for this session") { decide(.acceptForSession) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(ApprovalCopy.sessionScopeExplanation(for: approval.kind))
        }
    }

    private func decide(_ decision: ApprovalDecisionValue) {
        guard !isSubmitting else { return }
        lastDecision = decision
        onDecision(decision)
    }
}

// MARK: - Details

/// Command, file, permission scope, working directory and host, with long
/// content in a bounded scroll area so the actions stay reachable. Copying
/// never runs anything.
struct ApprovalDetails: View {
    let approval: PendingApproval
    var hostName: String?

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            Text(ApprovalCopy.question(for: approval.kind, hostName: hostName))
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            if let reason = nonEmpty(approval.reason) {
                Text(verbatim: reason)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let command = nonEmpty(approval.command) {
                ApprovalCodeBlock(text: command, label: "Command")
            }
            if let path = nonEmpty(approval.path) {
                detailRow("File", value: path)
            }
            if let grantRoot = nonEmpty(approval.grantRoot) {
                detailRow("Access to", value: grantRoot)
            }
            if let cwd = nonEmpty(approval.cwd) {
                detailRow("Working directory", value: abbreviateHomePath(cwd))
            }
        }
    }

    private func detailRow(_ label: LocalizedStringKey, value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
            Text(label)
                .buddyText(.label)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Text(verbatim: value)
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(3)
                .truncationMode(.middle)
                .textSelection(.enabled)
        }
        .accessibilityElement(children: .combine)
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else { return nil }
        return trimmed
    }
}

/// Monospaced, scrollable command block with a copy button.
struct ApprovalCodeBlock: View {
    let text: String
    let label: LocalizedStringKey
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(label)
                    .buddyText(.caption, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                Spacer()
                Button {
                    UIPasteboard.general.string = text
                    copied = true
                } label: {
                    Label(copied ? "Copied" : "Copy", systemImage: copied ? "checkmark" : "doc.on.doc")
                        .buddyText(.caption, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.link)
                        .frame(minHeight: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, BuddySpacing.sm)
            ScrollView([.vertical, .horizontal]) {
                Text(verbatim: text)
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: true, vertical: true)
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.bottom, BuddySpacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: 180)
        }
        .background(AgentBuddyTheme.surface, in: RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
    }
}
