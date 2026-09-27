import SwiftUI
import UIKit

extension SessionCanvasLine {
    // MARK: - Zoom 3+: last user message (quoted, single line)

    @ViewBuilder
    var userMessageLine: some View {
        let message = (session.lastUserMessage ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let title = session.sessionTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if !message.isEmpty && message != title {
            // Quoted block: accent left rule + faint accent fill so the
            // last user message reads as content rather than meta. Sits
            // between telemetry (above) and the tool log / response
            // preview (below) and visually breaks the two apart.
            FormattedText(text: message, lineLimit: zoomLevel >= 4 ? 3 : 1)
                .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.95))
                .agentBuddyFont(size: AgentBuddyFont.conversationBodyPointSize)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 5)
                .padding(.leading, 8)
                .padding(.trailing, 6)
                .background(AgentBuddyTheme.accent.opacity(0.06))
                .overlay(alignment: .leading) {
                    AgentBuddyTheme.accent.opacity(0.55).frame(width: 2)
                }
                .clipShape(
                    RoundedRectangle(cornerRadius: 3, style: .continuous)
                )
                .padding(.top, 6)
        }
    }

    // MARK: - Zoom 4: "Recent activity" section header
    //
    // A small monospace label above the tool log so the icon list is
    // unambiguously "what just happened" rather than blending into the
    // user message preview above or the response preview below.

    @ViewBuilder
    var activityHeader: some View {
        Text("RECENT ACTIVITY")
            .agentBuddyMonoFont(size: 9, weight: .semibold)
            .tracking(1.2)
            .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.65))
            .padding(.top, 10)
    }

    // MARK: - Zoom 4: cwd footer (paired with working pill if active)
    //
    // Workspace path on the left, a small "Working…" pill on the right
    // when the session has a live turn. Both are peer-status info — they
    // belong on one line, not stacked.

    @ViewBuilder
    var cwdFooter: some View {
        HStack(spacing: 8) {
            Text(PathDisplay.display(session.cwd, isLocal: session.isLocal))
                .agentBuddyMonoFont(size: 10, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            if isActive {
                HStack(spacing: 5) {
                    Circle()
                        .fill(AgentBuddyTheme.accent)
                        .frame(width: 4, height: 4)
                    Text("Working")
                        .agentBuddyMonoFont(size: 9, weight: .semibold)
                        .foregroundStyle(AgentBuddyTheme.accent.opacity(0.85))
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 2)
                .overlay(
                    Capsule()
                        .stroke(AgentBuddyTheme.accent.opacity(0.4), lineWidth: 0.5)
                )
                .clipShape(Capsule())
                .fixedSize()
            }
        }
        .padding(.top, 6)
    }

    // MARK: - Zoom 3+: tool call log

    @ViewBuilder
    func toolLog(maxEntries: Int) -> some View {
        // Rust-side `recent_tool_log` (see `extract_conversation_activity`
        // in shared/rust-bridge/.../boundary.rs) already derives this; the
        // iOS copy that used to live here was the dominant AttributeGraph
        // subscription during streaming. Entries come through newest-last;
        // take the tail to show the most recent `maxEntries`.
        let entries = Array(session.recentToolLog.suffix(maxEntries))
        if !entries.isEmpty {
            VStack(alignment: .leading, spacing: 1) {
                ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                    toolRowView(entry)
                }
            }
            .padding(.top, 6)
            .padding(.bottom, 2)
        }
    }

    @ViewBuilder
    private func toolRowView(_ entry: AppToolLogEntry) -> some View {
        HStack(spacing: 8) {
            toolIconView(for: entry.tool)
                .foregroundStyle(AgentBuddyTheme.accent.opacity(0.6))
                .frame(minWidth: 20, alignment: .leading)
            Text(formatToolDetail(entry))
                .foregroundStyle(AgentBuddyTheme.textSecondary.opacity(0.8))
                .lineLimit(1)
                .truncationMode(.middle)
        }
        // Keep tool activity smaller than the assistant response preview so
        // the response remains the primary content on the card.
        .agentBuddyFont(size: toolLogFontSize)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Render the tool detail text. For `Edit` we use the same
    /// `workspaceTitle(for:)` helper the conversation timeline uses for
    /// FileChange items — gives `Edited MainActivity.kt` rather than
    /// the full absolute path, which is unreadable in a 390 px row.
    private func formatToolDetail(_ entry: AppToolLogEntry) -> String {
        switch entry.tool {
        case "Edit":
            return "Edited \(workspaceTitle(for: entry.detail))"
        default:
            return entry.detail
        }
    }

    /// Pick the display glyph/icon for a Rust tool-log entry. `AppToolLogEntry.tool`
    /// is a short category name the reducer emits (`"Bash"`, `"Edit"`, `"MCP"`,
    /// `"Tool"`, `"Explore"`, `"WebSearch"`). MCP and generic tools read better
    /// as SF Symbols than as abbreviated text; the rest render as a single
    /// character.
    @ViewBuilder
    func toolIconView(for tool: String) -> some View {
        switch tool {
        case "MCP":
            Image(systemName: "desktopcomputer")
                .agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        case "Tool":
            Image(systemName: "wrench.and.screwdriver")
                .agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        case "Bash":
            Text("$").agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        case "Edit":
            Text("✎").agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        case "Explore", "WebSearch":
            // Use an SF Symbol to match the size/weight of the other
            // Image-based icons (MCP/Tool); the "⌕" glyph renders
            // larger than `$` / `✎` at the same point size because
            // Unicode metrics for that character put the visual mass
            // over a bigger box.
            Image(systemName: "magnifyingglass")
                .agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        default:
            Text(tool.prefix(1).uppercased())
                .agentBuddyFont(size: toolLogFontSize - 1, weight: .semibold)
        }
    }

    // MARK: - Zoom 4: last response preview

    @ViewBuilder
    var responsePreview: some View {
        // Source the preview from `session.lastResponsePreview` rather than
        // walking `hydratedConversationItems` live. The Rust reducer
        // refreshes the session summary on every item delta, but the home
        // dashboard's observation path is debounced at 120ms in
        // `HomeDashboardModel.scheduleObservedRefresh` — so the preview
        // naturally updates at ~8Hz instead of forcing `AgentBuddyMarkdownView`
        // to re-parse markdown on every streaming token (30–60Hz). The old
        // live-walk path made the streaming card the dominant frame-time
        // cost after all the other scroll fixes landed.
        //
        // Crossfade key is the `source_turn_id` of the assistant message
        // that produced `lastResponsePreview`. Keying on `stats.turnCount`
        // would flip the id the moment the user submits a new prompt —
        // before any new assistant text exists — so the preview would
        // fade out (and back in with the same previous text) on every
        // send. Using the assistant's turn id keeps the old answer
        // visible until a new assistant reply actually arrives.
        let markdown = (session.lastResponsePreview ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let blockId = session.lastResponseTurnId ?? "empty"
        if markdown.count > 20 {
            // ViewThatFits picks the first child whose natural size fits
            // the proposed container. The container is capped at
            // `responsePreviewMaxHeight`, so:
            //   - Short markdown (natural ≤ cap): the fixed-size rendering
            //     wins, frame shrinks to natural height → no blank space.
            //   - Long markdown (natural > cap): the first child is too
            //     tall, so ViewThatFits falls through to the scroll-based
            //     fallback — scroll is disabled but `defaultScrollAnchor(.bottom)`
            //     keeps the tail visible, and the frame stays at cap.
            // This pattern is the clean SwiftUI answer for "shrink to
            // content OR cap-with-tail-visible"; the earlier
            // `fixedSize + frame(maxHeight:, alignment: .bottom)` combo
            AgentBuddyMarkdownView(
                markdown: markdown,
                selectionEnabled: false
            )
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(blockId)
            // Top-alignment so long markdown clips at the bottom
            // (where the fade mask hides the cut) rather than
            // center-clipping and revealing the middle. Replaces the
            // prior `ViewThatFits` + disabled-ScrollView pair.
            .frame(maxHeight: responsePreviewMaxHeight, alignment: .top)
            .clipped()
            .mask(
                LinearGradient(
                    gradient: Gradient(stops: [
                        .init(color: .black.opacity(0.55), location: 0),
                        .init(color: .black.opacity(0.85), location: 0.10),
                        .init(color: .black, location: 0.22)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .padding(.top, 4)
        }
    }

    /// Height cap for the response preview. Zoom 3 keeps it tight
    /// (25% of screen) so rows stay scan-able in a dense list. Zoom 4
    /// is uncapped — the full assistant reply renders at its natural
    /// height so the user can actually read it.
    private var responsePreviewMaxHeight: CGFloat {
        if zoomLevel >= 4 { return .infinity }
        let screenHeight = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.screen.bounds.height ?? 800
        return screenHeight * 0.25
    }
}
