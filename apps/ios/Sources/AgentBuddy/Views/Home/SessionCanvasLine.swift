import SwiftUI
import UIKit

// MARK: - Session Canvas Layout

private enum SessionCanvasLayout {
    static let horizontalPadding: CGFloat = 14
    static let markerWidth: CGFloat = 14
    static let markerSpacing: CGFloat = 8
}

// MARK: - Session Canvas Line

struct SessionCanvasLine: View {
    let session: HomeDashboardRecentSession
    let isOpening: Bool
    let isHydrating: Bool
    let isCancelling: Bool
    /// Committed integer zoom level — drives which zoom-gated layers
    /// are visible and the preview cap. UIKit controls the visible
    /// *container* height during a pinch; this view is purely a
    /// function of the integer display zoom.
    let zoomLevel: Int

    // No `@Environment(AppModel.self)` — the card is purely prop-driven.
    // That was the core of the streaming AttributeGraph hotspot: reading
    // `appModel.snapshot` from 20 cards created 20 subscription edges, each
    // invalidated per streaming-delta bump. `session` reaches us through
    // `HomeDashboardModel.refreshState`'s debounced observation path, so
    // propagation fans out to one observer (the parent), not twenty.

    /// Vertical padding around the card content. Matches the iOS zoom
    /// anchors `[3, 6, 10, 12]` for levels 1–4.
    fileprivate static func verticalPadding(for zoom: Int) -> CGFloat {
        let anchors: [CGFloat] = [3, 6, 10, 12]
        let idx = max(0, min(anchors.count - 1, zoom - 1))
        return anchors[idx]
    }

    var isActive: Bool { session.hasTurnActive }
    var timeAgo: String { relativeDate(Int64(session.updatedAt.timeIntervalSince1970)) }
    var s: AppConversationStats? { session.stats }
    var toolCallCount: UInt32 { s?.toolCallCount ?? 0 }
    var turnCount: UInt32 { s?.turnCount ?? 0 }

    /// True when the most recent tool-capable item is still running.
    /// Derived from the Rust-side `recent_tool_log`, which records tool
    /// entries in chronological order — the last entry's status reflects
    /// the most recent tool. Tool-call activity is only updated on item
    /// upserts (not streaming deltas), so the log is always fresh for this
    /// check.
    var isToolCallRunning: Bool {
        guard let last = session.recentToolLog.last else { return false }
        let s = last.status.lowercased()
        return s == "pending" || s == "inprogress"
    }

    /// Keep home-screen tool activity subordinate to assistant/user text.
    /// The home card's response preview uses conversation-body sizing, so
    /// the tool log should step down a tier rather than compete with it.
    var toolLogFontSize: CGFloat {
        max(12, AgentBuddyFont.conversationBodyPointSize - 3)
    }

    // ────────────────────────────────────────────────────
    // Zoom levels — each must feel distinct:
    //
    //  1  SCAN     title + age (right). Max density for scanning a backlog.
    //  2  GLANCE   + identity strip (time · server · model · branch).
    //  3  READ     + telemetry strip (counts · adds/rems · ⏱ · ctx%) +
    //              user message (quoted) + 1 tool-log entry.
    //  4  DEEP     + lineage breadcrumb + 3 tool-log entries + response
    //              preview + sibling pills + cwd footer w/ Working pill.
    //
    // Identity (text) and telemetry (numbers) live on separate lines so a
    // 390 px iPhone row never has to truncate one to fit the other.
    // ────────────────────────────────────────────────────

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Group {
                if isOpening {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(AgentBuddyTheme.accent)
                } else {
                    statusIndicator
                }
            }
            .frame(width: SessionCanvasLayout.markerWidth, height: 16)
            .padding(.trailing, SessionCanvasLayout.markerSpacing)
            .padding(.top, 2)

            VStack(alignment: .leading, spacing: 0) {
                // Lineage breadcrumb (zoom 4 only). Always present in the
                // tree so zoom transitions just animate its height; matches
                // the visibleWhen pattern used for the rest of the layers.
                lineageBreadcrumb
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .visibleWhen(zoomLevel >= 4 && (session.lineage?.ancestors.isEmpty == false))

                // Title row: title + fork rune (if branched) on the left,
                // a relative-age chip pinned to the right at zoom 1 so the
                // SCAN row carries recency info without crowding identity.
                // Higher zooms surface age inside `modelBadgeLine` and drop
                // the chip here so we never duplicate.
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    FormattedText(text: session.sessionTitle, lineLimit: zoomLevel >= 4 ? 4 : 2)
                        .modifier(MarkdownMatchedTitleFont())
                        .foregroundStyle(isActive ? AgentBuddyTheme.accent : AgentBuddyTheme.textPrimary)
                        .modifier(SessionShimmerEffect(active: isActive))
                        .fixedSize(horizontal: false, vertical: true)
                    if let lineage = session.lineage, lineage.hasMultipleBranches {
                        forkRune(lineage: lineage)
                    }
                    Spacer(minLength: 6)
                    if zoomLevel == 1 {
                        Text(timeAgo)
                            .agentBuddyMonoFont(size: 10, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textMuted.opacity(0.7))
                            .fixedSize()
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Detail below — gets full width. As zoom grows, additional
                // rows are revealed by the container's layout animation.
                // Inner VStack is pinned to full width so removals collapse
                // vertically only — otherwise the container sizes to the
                // widest child and short rows visually shrink to the left.
                // Every zoom-gated layer is *always* in the view tree;
                // per-zoom visibility is controlled via
                // `.visibleWhen(...)` which squashes the view to zero
                // height + zero opacity when hidden. SwiftUI still runs
                // layout for these views at every zoom (cost paid on
                // scroll, not on zoom change), but zoom transitions no
                // longer materialize new subtrees — they just animate
                // frame heights. Simpler, smoother zoom; uniform scroll
                // cost across zoom levels.
                VStack(alignment: .leading, spacing: 0) {
                    // Zoom-gated visibility — binary on committed
                    // `zoomLevel`. The UIKit host (HomeSessionsScrollView)
                    // sets zoomLevel=4 during a pinch so every layer is
                    // present and the UIKit frame clip reveals it
                    // progressively.
                    //
                    // Stacking order is the row's reading order:
                    //   identity  →  goal  →  telemetry  →  user msg  →
                    //   activity  →  response  →  branches  →  cwd
                    // Identity (time/server/model) and telemetry (counts/%/
                    // adds/rems) are intentionally on separate lines so a
                    // 390 px row never has to choose between truncating the
                    // server name and dropping a stat.
                    modelBadgeLine
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 2)
                    // Goal at z2/z3 renders standalone. At z4 it folds
                    // into `telemetryDashboard` (banner above the grid)
                    // so the dashed-bordered panel is the single home for
                    // both the objective and the metrics.
                    goalLine
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 2 && zoomLevel < 4 && session.goal != nil)
                    telemetryStrip
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 3)
                    userMessageLine
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 3)
                    activityHeader
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 4 && !session.recentToolLog.isEmpty)
                    // Zoom 4 takes the whole screen — show the full
                    // recent_tool_log (Rust caps at 8 already) so Edit
                    // entries don't get pushed off the visible suffix
                    // by newer Bash commands. Zoom 3 keeps it tight at
                    // 1 entry so multiple sessions can fit on screen.
                    toolLog(maxEntries: zoomLevel >= 4 ? 8 : 1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 3)
                    responsePreview
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 4)
                    siblingPillsRow
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel >= 4 && (session.lineage?.hasMultipleBranches == true))
                    cwdFooter
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .visibleWhen(zoomLevel == 4 && !session.cwd.isEmpty)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, SessionCanvasLayout.horizontalPadding)
        .padding(.bottom, Self.verticalPadding(for: zoomLevel))
        .background(alignment: .leading) {
            if isActive {
                AgentBuddyTheme.accent.opacity(0.3).frame(width: 2)
            }
        }
        .background(isActive ? AgentBuddyTheme.accent.opacity(0.02) : Color.clear)
        .contentShape(Rectangle())
        .clipped()
        // Zoom transitions animate when triggered via `withAnimation`
        // (zoom button, pinch-end snap). Live pinch updates bypass
        // this — they set state directly so the card tracks the
        // finger without overshooting. We deliberately don't attach
        // `.animation(_:value: zoomLevel)` here because that would
        // wrap every zoomLevel change (including mid-pinch threshold
        // crossings) in an implicit animation and fight the live
        // tracking.
        .animation(.easeInOut(duration: 0.25), value: isActive)
        .accessibilityIdentifier("home.recentSessionCard")
    }

    // MARK: - Status Indicator

    private var dotState: StatusDotState {
        if isCancelling { return .error }
        if isActive { return .active }
        if isHydrating { return .pending }
        if session.isResumed { return .ok }
        return .idle
    }

    private var statusIndicator: some View {
        StatusDot(state: dotState)
    }
}

/// Renders the task title at the same size the conversation view uses for
/// message bodies (`AgentBuddyFont.conversationBodyPointSize × textScale`) so
/// titles and user/assistant messages in the home list match the sizes you
/// see inside a conversation. Kept medium-weight (rather than bold) so the
/// title reads as a row heading without visually dominating the response.
private struct MarkdownMatchedTitleFont: ViewModifier {
    @Environment(\.textScale) private var textScale
    func body(content: Content) -> some View {
        content
            .font(.custom(
                AgentBuddyFont.markdownFontName,
                size: AgentBuddyFont.conversationBodyPointSize * textScale
            ))
            .fontWeight(.medium)
    }
}

private struct SessionShimmerEffect: ViewModifier {
    let active: Bool

    func body(content: Content) -> some View {
        if active {
            // `TimelineView(.animation)` drives a time-based phase.
            // Every tick rebuilds the gradient stops — fine here
            // because the overlay is a single SwiftUI.LinearGradient
            // (cheap) and its body eval doesn't cascade upward thanks
            // to `compositingGroup` isolating the blend scope.
            //
            // `.blendMode(.sourceAtop)` + `.compositingGroup()`
            // constrains the white highlight to paint only on the
            // underlying glyphs' opaque pixels — so the shimmer
            // tracks the text shape without needing a mask.
            TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                let t = timeline.date.timeIntervalSinceReferenceDate
                let phase = CGFloat(t.truncatingRemainder(dividingBy: 2.0) / 2.0)

                content
                    .overlay {
                        LinearGradient(
                            stops: [
                                .init(color: .white.opacity(0), location: max(0, phase - 0.2)),
                                .init(color: .white.opacity(0.7), location: phase),
                                .init(color: .white.opacity(0), location: min(1, phase + 0.2))
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                        .blendMode(.sourceAtop)
                    }
                    .compositingGroup()
            }
        } else {
            content
        }
    }
}
