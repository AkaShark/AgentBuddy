import SwiftUI

/// Loading and empty states for the All tasks screen. Each empty state says
/// what happened and offers one specific next step.
extension SessionsScreen {
    @ViewBuilder
    var emptySessionsState: some View {
        if isLoading {
            HStack(spacing: BuddySpacing.sm) {
                ProgressView()
                    .controlSize(.small)
                    .tint(AgentBuddyTheme.textSecondary)
                Text("Loading tasks…")
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
            .frame(maxWidth: .infinity, minHeight: 120)
            .accessibilityElement(children: .combine)
        } else if connectedServers.isEmpty {
            BuddyEmptyState(
                systemImage: "laptopcomputer.and.iphone",
                title: "No hosts connected",
                message: "Open AgentBuddy on your Mac and scan its pairing QR code. Tasks then run on that computer while you follow along here.",
                actionTitle: "Connect a computer",
                actionSystemImage: "laptopcomputer",
                actionKind: .secondary,
                action: { appState.showServerPicker = true }
            )
        } else {
            BuddyEmptyState(
                systemImage: "sparkles",
                title: "No tasks yet",
                message: "Tap New task to start one on a connected host. Your tasks show up here, grouped by project."
            )
        }
    }

    var loadingMoreSessionsRow: some View {
        HStack(spacing: BuddySpacing.xs) {
            ProgressView()
                .controlSize(.small)
                .tint(AgentBuddyTheme.textSecondary)
            Text("Loading more tasks…")
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textSecondary)
            Spacer(minLength: 0)
        }
        .frame(minHeight: BuddySize.minHitTarget)
        .accessibilityElement(children: .combine)
    }

    func noMatchingSessionsState(query: String) -> some View {
        let message: LocalizedStringKey = query.isEmpty
            ? "No tasks match these filters."
            : "No tasks match “\(query)”. Try another word."
        return BuddyEmptyState(
            systemImage: "magnifyingglass",
            title: "No matching tasks",
            message: message,
            actionTitle: "Clear search and filters",
            actionSystemImage: "xmark.circle",
            actionKind: .secondary,
            action: {
                sessionSearchQuery = ""
                selectedRuntimeKindFilter = nil
                selectedServerFilterId = nil
                showOnlyForks = false
            }
        )
    }
}
