import SwiftUI
import UIKit

struct ApprovalPromptView: View {
    let approval: PendingApproval
    let onDecision: (ApprovalDecisionValue) -> Void
    var onViewThread: ((ThreadKey) -> Void)? = nil

    private var title: String {
        switch approval.kind {
        case .command:
            return "Command Approval Required"
        case .fileChange:
            return "File Change Approval Required"
        case .permissions:
            return "Permissions Approval Required"
        case .mcpElicitation:
            return "MCP Input Required"
        }
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 12) {
                Text(title)
                    .agentBuddyFont(.headline)
                    .foregroundColor(AgentBuddyTheme.textPrimary)

                ScrollView(.vertical, showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 12) {
                        if let reason = approval.reason, !reason.isEmpty {
                            Text(reason)
                                .agentBuddyFont(.footnote)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                        }

                        if let threadId = approval.threadId, onViewThread != nil {
                            HStack {
                                Button {
                                    onViewThread?(ThreadKey(serverId: approval.serverId, threadId: threadId))
                                } label: {
                                    HStack(spacing: 3) {
                                        Text("View Thread")
                                            .agentBuddyFont(.caption, weight: .medium)
                                        Image(systemName: "arrow.right")
                                            .agentBuddyFont(size: 9, weight: .semibold)
                                    }
                                    .foregroundColor(AgentBuddyTheme.accent)
                                }
                                .buttonStyle(.plain)

                                Spacer()
                            }
                        }

                        if let command = approval.command, !command.isEmpty {
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Command")
                                    .agentBuddyFont(.caption)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                                Text(command)
                                    .agentBuddyFont(.footnote)
                                    .foregroundColor(AgentBuddyTheme.textBody)
                                    .textSelection(.enabled)
                                    .padding(10)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(AgentBuddyTheme.surface)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }

                        if let cwd = approval.cwd, !cwd.isEmpty {
                            Text("CWD: \(cwd)")
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textMuted)
                        }

                        if let grantRoot = approval.grantRoot, !grantRoot.isEmpty {
                            Text("Grant Root: \(grantRoot)")
                                .agentBuddyFont(.caption)
                                .foregroundColor(AgentBuddyTheme.textMuted)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                VStack(spacing: 8) {
                    Button("Allow Once") { onDecision(.accept) }
                        .buttonStyle(.borderedProminent)
                        .tint(AgentBuddyTheme.accent)
                        .frame(maxWidth: .infinity)

                    Button("Allow for Session") { onDecision(.acceptForSession) }
                        .buttonStyle(.bordered)
                        .frame(maxWidth: .infinity)

                    HStack(spacing: 8) {
                        Button("Deny") { onDecision(.decline) }
                            .buttonStyle(.bordered)
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)

                        Button("Abort") { onDecision(.cancel) }
                            .buttonStyle(.bordered)
                            .frame(maxWidth: .infinity)
                    }
                }
                .agentBuddyFont(.callout)
            }
            .padding(16)
            .frame(maxHeight: UIScreen.main.bounds.height * 0.8)
            .modifier(GlassRectModifier(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(AgentBuddyTheme.border, lineWidth: 1)
            )
            .padding(.horizontal, 16)
        }
        .transition(.opacity)
    }
}
