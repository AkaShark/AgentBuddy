import SwiftUI

extension ConversationInfoView {
    // MARK: - Server-Only Action Row

    var serverOnlyActionRow: some View {
        HStack(spacing: 0) {
            actionCircle(icon: "paintbrush", label: "Appearance") {
                onOpenWallpaper?()
            }
            if let onOpenShell {
                actionCircle(icon: "terminal", label: "Shell") {
                    onOpenShell()
                }
            }
        }
    }

    // MARK: - Hero Section

    var heroSection: some View {
        VStack(spacing: 12) {
            // Status dot + title
            HStack(spacing: 8) {
                Circle()
                    .fill(statusColor)
                    .frame(width: 10, height: 10)
                Text(thread?.displayTitle ?? "Untitled session")
                    .agentBuddyFont(size: 22, weight: .bold)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
            }

            // Model + reasoning badges
            HStack(spacing: 8) {
                if let model = thread?.displayModelLabel,
                   !model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(model)
                        .agentBuddyFont(size: 13, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .modifier(GlassRectModifier(cornerRadius: 8))
                }
                if let effort = thread?.reasoningEffort {
                    Text(effort)
                        .agentBuddyFont(size: 12, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .modifier(GlassRectModifier(cornerRadius: 8))
                }
            }

            // Metadata row: cwd + timestamps
            VStack(spacing: 6) {
                if let cwd = thread?.info.cwd {
                    HStack(spacing: 5) {
                        Image(systemName: "folder.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                        Text(abbreviatePath(cwd))
                            .agentBuddyFont(size: 12)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }

                if let tid = threadKey?.threadId {
                    HStack(spacing: 5) {
                        Image(systemName: "number")
                            .font(.system(size: 10))
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                        Text(tid)
                            .agentBuddyFont(size: 11)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                            .textSelection(.enabled)
                    }
                }

                HStack(spacing: 12) {
                    if let created = thread?.info.createdAt {
                        HStack(spacing: 3) {
                            Image(systemName: "clock")
                                .font(.system(size: 9))
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                            Text(relativeDate(created))
                                .agentBuddyFont(size: 11)
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                        }
                    }
                    if let updated = thread?.info.updatedAt {
                        HStack(spacing: 3) {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 9))
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                            Text(relativeDate(updated))
                                .agentBuddyFont(size: 11)
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                        }
                    }
                }
            }
        }
        .padding(.top, 16)
    }

    private func abbreviatePath(_ path: String) -> String {
        PathDisplay.display(path, isLocal: server?.isLocal == true)
    }

    // MARK: - Action Buttons Row (Telegram-style)

    var actionButtonsRow: some View {
        HStack(spacing: 0) {
            actionCircle(icon: "paintbrush", label: "Appearance") {
                onOpenWallpaper?()
            }
            actionCircle(icon: "arrow.branch", label: "Fork") {
                Task { await forkConversation() }
            }
            actionCircle(icon: "pencil", label: "Rename") {
                renameText = thread?.info.title ?? ""
                isRenaming = true
            }
        }
    }

    private func actionCircle(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(AgentBuddyTheme.accent)
                    .frame(width: 52, height: 52)
                    .modifier(GlassRectModifier(cornerRadius: 14))
                Text(label)
                    .agentBuddyFont(size: 11, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }

    private func timestampLabel(_ label: String, timestamp: Int64) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .agentBuddyFont(size: 10, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textMuted)
            Text(relativeDate(timestamp))
                .agentBuddyFont(size: 12)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
        }
    }

    private var statusColor: Color {
        switch thread?.info.status {
        case .active: return AgentBuddyTheme.success
        case .idle: return AgentBuddyTheme.textMuted
        case .systemError: return AgentBuddyTheme.danger
        case .notLoaded: return AgentBuddyTheme.textMuted
        default: return AgentBuddyTheme.textMuted
        }
    }

    private var statusLabel: String {
        switch thread?.info.status {
        case .active: return "Active"
        case .idle: return "Idle"
        case .systemError: return "Error"
        case .notLoaded: return "Not Loaded"
        default: return "Unknown"
        }
    }
}
