import SwiftUI

extension SessionsScreen {
    /// Task row in the home-row style: status tile, heading title (two lines),
    /// label subtitle with status / updated time / model, tags, and a visible
    /// "…" menu so long press is never the only way to reach row actions.
    func sessionRow(
        _ thread: AppSessionSummary,
        isActive: Bool,
        derived: SessionsDerivedData,
        ephemeralState: SessionsModel.ThreadEphemeralState?,
        depth: Int,
        hasChildren: Bool,
        isCollapsed: Bool,
        onToggleNode: @escaping () -> Void,
        onSelectSession: @escaping () -> Void
    ) -> some View {
        let parent = derived.parentByKey[thread.key]
        let hasTurnActive = ephemeralState?.hasTurnActive ?? thread.hasActiveTurn
        let updatedAt = ephemeralState?.updatedAt ?? thread.updatedAtDate
        let state = rowTaskState(for: thread, hasTurnActive: hasTurnActive)
        let indent = CGFloat(min(depth, 4)) * BuddySpacing.md

        return VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            HStack(alignment: .center, spacing: BuddySpacing.xs) {
                HStack(alignment: .center, spacing: BuddySpacing.md) {
                    statusTile(state)

                    VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                        FormattedText(text: thread.sessionTitle, lineLimit: 2)
                            .buddyText(.heading)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .multilineTextAlignment(.leading)
                            .accessibilityIdentifier("sessions.sessionTitle")

                        Text(verbatim: rowSubtitle(thread: thread, state: state, updatedAt: updatedAt, parent: parent))
                            .buddyText(.label, weight: .regular)
                            .foregroundStyle(AgentBuddyTheme.textSecondary)
                            .lineLimit(3)

                        rowTags(thread: thread, isActive: isActive)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : .isButton)
                .accessibilityIdentifier("sessions.sessionRow")
                .onTapGesture(perform: onSelectSession)

                rowTrailingControls(
                    thread,
                    hasChildren: hasChildren,
                    isCollapsed: isCollapsed,
                    onToggleNode: onToggleNode
                )
            }

            if isActive {
                lineageSummary(for: thread, derived: derived)
                    .padding(.leading, BuddySize.rowTile + BuddySpacing.md)
            }
        }
        .padding(.leading, indent)
        .overlay(alignment: .leading) {
            if depth > 0 {
                Rectangle()
                    .fill(AgentBuddyTheme.border)
                    .frame(width: 1)
                    .padding(.leading, indent - BuddySpacing.xs)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, BuddySpacing.sm)
        .padding(.vertical, BuddySpacing.sm)
        .frame(minHeight: 64)
        .background {
            if isActive {
                let shape = RoundedRectangle(cornerRadius: BuddyRadius.detailCard, style: .continuous)
                shape
                    .fill(AgentBuddyTheme.surface)
                    .overlay { shape.strokeBorder(AgentBuddyTheme.border, lineWidth: 1) }
            }
        }
        .contentShape(Rectangle())
        .hoverEffect(.highlight)
    }

    // MARK: - Row parts

    private func statusTile(_ state: BuddyTaskState) -> some View {
        let isQuiet = state == .completed || state == .idle
        return BuddyIconTile(
            content: .symbol(state.systemImage),
            fill: isQuiet ? AgentBuddyTheme.surface : state.fill,
            foreground: isQuiet ? AgentBuddyTheme.textSecondary : state.foreground
        )
        .overlay {
            RoundedRectangle(cornerRadius: BuddyRadius.tile, style: .continuous)
                .strokeBorder(AgentBuddyTheme.border, lineWidth: isQuiet ? 1 : 0)
        }
    }

    @ViewBuilder
    private func rowTags(thread: AppSessionSummary, isActive: Bool) -> some View {
        if isActive || thread.isSubagent || thread.isFork {
            HStack(spacing: 6) {
                if isActive {
                    SessionsRowTag(title: Text("Current task"), systemImage: "eye")
                }
                if thread.isSubagent {
                    SessionsRowTag(
                        title: thread.agentDisplayLabel.map { Text(verbatim: $0) } ?? Text("Sub-agent"),
                        systemImage: "person.2"
                    )
                } else if thread.isFork {
                    SessionsRowTag(title: Text("Fork"), systemImage: "arrow.triangle.branch")
                }
            }
            .padding(.top, 2)
        }
    }

    private func rowTrailingControls(
        _ thread: AppSessionSummary,
        hasChildren: Bool,
        isCollapsed: Bool,
        onToggleNode: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 0) {
            if hasChildren {
                Button(action: onToggleNode) {
                    Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(isCollapsed ? "Show forks" : "Hide forks"))
            }

            if resumingKey == thread.key {
                ProgressView()
                    .controlSize(.small)
                    .tint(AgentBuddyTheme.textSecondary)
                    .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
            } else {
                Menu {
                    sessionRowContextMenu(thread)
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("More actions"))
            }
        }
    }

    // MARK: - Lineage

    @ViewBuilder
    private func lineageSummary(for thread: AppSessionSummary, derived: SessionsDerivedData) -> some View {
        let parent = derived.parentByKey[thread.key]
        let siblings = derived.siblingsByKey[thread.key] ?? []
        let children = derived.childrenByKey[thread.key] ?? []

        if parent != nil || !siblings.isEmpty || !children.isEmpty {
            VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                BuddyDivider()
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: BuddySpacing.xs) {
                        lineageChips(parent: parent, siblings: siblings, children: children)
                    }
                    VStack(alignment: .leading, spacing: 0) {
                        lineageChips(parent: parent, siblings: siblings, children: children)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func lineageChips(
        parent: AppSessionSummary?,
        siblings: [AppSessionSummary],
        children: [AppSessionSummary]
    ) -> some View {
        if let parent {
            Button {
                Task { await resumeSession(parent) }
            } label: {
                lineageChipLabel(Text("Parent task"), systemImage: "arrow.up.left", opensMenu: false)
            }
            .buttonStyle(.plain)
        }

        if !siblings.isEmpty {
            Menu {
                ForEach(siblings) { sibling in
                    Button(sibling.sessionTitle) {
                        Task { await resumeSession(sibling) }
                    }
                }
            } label: {
                lineageChipLabel(Text("\(siblings.count) sibling tasks"), systemImage: "arrow.triangle.branch", opensMenu: true)
            }
        }

        if !children.isEmpty {
            Menu {
                ForEach(children) { child in
                    Button(child.sessionTitle) {
                        Task { await resumeSession(child) }
                    }
                }
            } label: {
                lineageChipLabel(Text("\(children.count) forked tasks"), systemImage: "arrow.turn.down.right", opensMenu: true)
            }
        }
    }

    private func lineageChipLabel(_ title: Text, systemImage: String, opensMenu: Bool) -> some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .imageScale(.small)
                .accessibilityHidden(true)
            title
            if opensMenu {
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .semibold))
                    .accessibilityHidden(true)
            }
        }
        .buddyContextChip()
    }

    // MARK: - Presentation

    /// Render-only mapping from snapshot fields to the Mint task state; it
    /// mirrors the home rows (`HomeTaskPresentation.state`).
    private func rowTaskState(for thread: AppSessionSummary, hasTurnActive: Bool) -> BuddyTaskState {
        if hasTurnActive { return .running }
        if thread.isSubagent {
            switch thread.subagentStatus {
            case .completed: return .completed
            case .errored: return .failed
            case .shutdown, .interrupted: return .interrupted
            case .pendingInit, .running, .unknown: return .idle
            }
        }
        return thread.lastTurnEndMs == nil ? .idle : .completed
    }

    private func rowSubtitle(
        thread: AppSessionSummary,
        state: BuddyTaskState,
        updatedAt: Date,
        parent: AppSessionSummary?
    ) -> String {
        var parts: [String] = []
        switch state {
        case .running: parts.append(String(localized: "Running"))
        case .failed: parts.append(String(localized: "Failed"))
        case .interrupted: parts.append(String(localized: "Stopped"))
        case .completed where thread.isSubagent: parts.append(String(localized: "Completed"))
        default: break
        }
        parts.append(String(localized: "Updated \(relativeDate(updatedAt))"))
        if let provider = thread.sessionModelLabel {
            parts.append(provider)
        }
        var text = parts.joined(separator: " · ")
        if let parent {
            text += "\n" + String(localized: "from \(parent.sessionTitle)")
        }
        return text
    }

    private func relativeDate(_ date: Date) -> String {
        Self.relativeFormatter.localizedString(for: date, relativeTo: Date())
    }
}
