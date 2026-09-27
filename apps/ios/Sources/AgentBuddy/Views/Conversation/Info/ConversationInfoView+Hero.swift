import SwiftUI

extension ConversationInfoView {
    // MARK: - Server-Only Action Row

    var serverOnlyActionRow: some View {
        infoActionRow {
            BuddyButton("Appearance", systemImage: "paintbrush", kind: .secondary) {
                onOpenWallpaper?()
            }
            if let onOpenShell {
                BuddyButton("Shell", systemImage: "terminal", kind: .secondary) {
                    onOpenShell()
                }
            }
        }
    }

    // MARK: - Hero Section

    /// Task hero: status, title, "partner · host", working directory,
    /// model / effort chips, then the thread id and timestamps.
    var heroSection: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.sm) {
            statusBadge

            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                Text(verbatim: thread?.displayTitle ?? "Untitled session")
                    .buddyText(.title)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let partnerAndHost {
                    Text(verbatim: partnerAndHost)
                        .buddyText(.label, weight: .regular)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }

            if let cwd = thread?.info.cwd {
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: "folder")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .accessibilityHidden(true)
                    Text(verbatim: abbreviatePath(cwd))
                        .buddyText(.code)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                        .textSelection(.enabled)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(Text(verbatim: cwd))
            }

            modelChips

            BuddyDivider()
                .padding(.vertical, BuddySpacing.xxs)

            metadataRows
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .buddyCard(.surface, radius: BuddyRadius.card, padding: BuddySpacing.lg)
    }

    @ViewBuilder
    private var statusBadge: some View {
        if let state = heroTaskState {
            BuddyStatusPill(state: state)
        } else {
            Label {
                Text(statusLabel)
            } icon: {
                Image(systemName: "circle.dashed")
                    .accessibilityHidden(true)
            }
            .buddyText(.label, weight: .medium)
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .padding(.horizontal, BuddySpacing.sm)
            .padding(.vertical, 6)
            .background(AgentBuddyTheme.surfaceSoft, in: Capsule())
        }
    }

    @ViewBuilder
    private var modelChips: some View {
        let model = thread?.displayModelLabel.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let effort = thread?.reasoningEffort
        if !model.isEmpty || effort != nil {
            HStack(spacing: BuddySpacing.xs) {
                if !model.isEmpty {
                    BuddyChip(model, systemImage: "cpu")
                }
                if let effort {
                    BuddyChip(effort, systemImage: "gauge.with.dots.needle.50percent")
                }
            }
        }
    }

    private var metadataRows: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            if let tid = threadKey?.threadId {
                HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                    Image(systemName: "number")
                        .accessibilityHidden(true)
                    Text(verbatim: tid)
                        .buddyText(.code)
                        .lineLimit(1)
                        .truncationMode(.middle)
                        .textSelection(.enabled)
                }
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            }

            HStack(spacing: BuddySpacing.md) {
                if let created = thread?.info.createdAt {
                    timestampLabel(systemImage: "clock", timestamp: created)
                        .accessibilityLabel(Text("Created \(relativeDate(created))"))
                }
                if let updated = thread?.info.updatedAt {
                    timestampLabel(systemImage: "arrow.clockwise", timestamp: updated)
                        .accessibilityLabel(Text("Updated \(relativeDate(updated))"))
                }
            }
        }
    }

    private var partnerAndHost: String? {
        guard let thread else { return nil }
        let partner = thread.agentRuntimeKind.displayLabel
        guard let host = server?.displayName.trimmingCharacters(in: .whitespacesAndNewlines), !host.isEmpty else {
            return partner
        }
        return "\(partner) · \(host)"
    }

    private func abbreviatePath(_ path: String) -> String {
        PathDisplay.display(path, isLocal: server?.isLocal == true)
    }

    // MARK: - Action Buttons Row

    var actionButtonsRow: some View {
        infoActionRow {
            BuddyButton("Appearance", systemImage: "paintbrush", kind: .secondary) {
                onOpenWallpaper?()
            }
            BuddyButton("Fork", systemImage: "arrow.branch", kind: .secondary) {
                Task { await forkConversation() }
            }
            BuddyButton("Rename", systemImage: "pencil", kind: .secondary) {
                renameText = thread?.info.title ?? ""
                isRenaming = true
            }
        }
    }

    /// Buttons side by side when they fit, stacked full width otherwise
    /// (large text, narrow screens).
    private func infoActionRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        let buttons = content()
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: BuddySpacing.sm) { buttons }
            VStack(spacing: BuddySpacing.sm) { buttons }
        }
    }

    private func timestampLabel(systemImage: String, timestamp: Int64) -> some View {
        Label {
            Text(verbatim: relativeDate(timestamp))
        } icon: {
            Image(systemName: systemImage)
                .accessibilityHidden(true)
        }
        .buddyText(.caption)
        .foregroundStyle(AgentBuddyTheme.textSecondary)
    }

    /// Thread status as a task state, so the pill pairs an icon with text.
    private var heroTaskState: BuddyTaskState? {
        switch thread?.info.status {
        case .active: return .running
        case .idle: return .idle
        case .systemError: return .failed
        default: return nil
        }
    }

    private var statusLabel: LocalizedStringKey {
        switch thread?.info.status {
        case .active: return "Active"
        case .idle: return "Idle"
        case .systemError: return "Error"
        case .notLoaded: return "Not Loaded"
        default: return "Unknown"
        }
    }
}
