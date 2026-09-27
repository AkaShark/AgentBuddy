import AVKit
import SwiftUI

// MARK: - SwiftUI row content

/// Hosts the session card (title + status indicator + per-zoom layers)
/// along with the `.contextMenu` and an `.onTapGesture`. This is the
/// SwiftUI view that each `HomeRowContainer` hosts inside its
/// `UIHostingController`. It is a pure function of its props — during a
/// pinch, UIKit sets `zoomLevel = 4` so the full content tree is
/// available for the outer frame to reveal; when idle, `zoomLevel` is
/// the committed integer.
struct HomeSessionRowContent: View {
    let session: HomeDashboardRecentSession
    let isOpening: Bool
    let isHydrating: Bool
    let isCancelling: Bool
    let zoomLevel: Int
    let pinned: Bool
    let onTap: () -> Void
    let onReply: () -> Void
    let onHide: () -> Void
    let onPin: () -> Void
    let onUnpin: () -> Void
    let onCancelTurn: () -> Void
    let onDelete: () -> Void
    let onFork: () -> Void
    let onShowPiP: () -> Void

    var body: some View {
        SessionCanvasLine(
            session: session,
            isOpening: isOpening,
            isHydrating: isHydrating,
            isCancelling: isCancelling,
            zoomLevel: zoomLevel
        )
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
        .contextMenu(menuItems: {
            Button { onReply() } label: {
                Label("Reply", systemImage: "arrowshape.turn.up.left")
            }
            Button { onFork() } label: {
                Label("Fork", systemImage: "arrow.triangle.branch")
            }
            .disabled(session.hasTurnActive)
            if session.hasTurnActive {
                Button(role: .destructive) { onCancelTurn() } label: {
                    Label("Cancel Turn", systemImage: "stop.circle")
                }
            }
            Button {
                if pinned { onUnpin() } else { onPin() }
            } label: {
                Label(
                    pinned ? "Remove from Home" : "Pin to Home",
                    systemImage: pinned ? "minus.circle" : "pin"
                )
            }
            if AVPictureInPictureController.isPictureInPictureSupported() {
                Button { onShowPiP() } label: {
                    Label("Show in Picture in Picture", systemImage: "pip")
                }
            }
            Button { onHide() } label: {
                Label("Hide from Home", systemImage: "eye.slash")
            }
            Button(role: .destructive) { onDelete() } label: {
                Label("Delete Session", systemImage: "trash")
            }
        }, preview: {
            // Compact preview — without this, iOS renders the whole
            // hosted row (which is huge at zoom 4) as the context-menu
            // preview and it scales up into a "giant row" on screen.
            SessionContextMenuPreview(session: session)
        })
        .accessibilityIdentifier("home.recentSessionCard")
    }
}

/// Small card that previews a session in the context-menu popup.
/// Constrained width + brief content so the long-press preview stays
/// visually compact regardless of the current zoom level.
private struct SessionContextMenuPreview: View {
    let session: HomeDashboardRecentSession

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(session.sessionTitle.isEmpty ? "Session" : session.sessionTitle)
                .agentBuddyFont(size: AgentBuddyFont.conversationBodyPointSize, weight: .medium)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(2)
            if !session.serverDisplayName.isEmpty {
                HStack(spacing: 5) {
                    Text(session.agentRuntimeKind.displayLabel)
                        .agentBuddyMonoFont(size: 9, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.accent.opacity(0.8))
                    Text(session.serverDisplayName)
                        .agentBuddyMonoFont(size: 10)
                        .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.75))
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(width: 240, alignment: .leading)
        .background(AgentBuddyTheme.surface)
    }
}
