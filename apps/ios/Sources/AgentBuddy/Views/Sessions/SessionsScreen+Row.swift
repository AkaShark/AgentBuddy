import SwiftUI

extension SessionsScreen {
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

        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top, spacing: 6) {
                HStack(spacing: 0) {
                    Color.clear
                        .frame(width: CGFloat(depth) * 8)
                    if hasChildren {
                        Button(action: onToggleNode) {
                            Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                                .agentBuddyFont(size: 9, weight: .semibold)
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                                .frame(width: 10, height: 10)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Color.clear.frame(width: 10, height: 10)
                    }
                }
                .padding(.top, 2)

                HStack(alignment: .top, spacing: 6) {
                    if hasTurnActive {
                        PulsingDot().padding(.top, 3)
                    } else if thread.isSubagent {
                        subagentStatusIndicator(thread.subagentStatus).padding(.top, 3)
                    } else {
                        Circle().fill(AgentBuddyTheme.textMuted.opacity(0.4)).frame(width: 8, height: 8).padding(.top, 3)
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            FormattedText(text: thread.sessionTitle, lineLimit: 2)
                                .agentBuddyFont(.footnote)
                                .foregroundColor(AgentBuddyTheme.textPrimary)
                                .multilineTextAlignment(.leading)
                                .accessibilityIdentifier("sessions.sessionTitle")

                            if thread.isSubagent {
                                HStack(spacing: 3) {
                                    Image(systemName: "person.2.fill")
                                        .agentBuddyFont(size: 8, weight: .semibold)
                                    Text(thread.agentDisplayLabel ?? "Agent")
                                        .agentBuddyFont(.caption2)
                                }
                                .foregroundColor(AgentBuddyTheme.textOnAccent)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AgentBuddyTheme.success)
                                .cornerRadius(4)
                            } else if thread.isFork {
                                Text("Fork")
                                    .agentBuddyFont(.caption2)
                                    .foregroundColor(AgentBuddyTheme.textOnAccent)
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(AgentBuddyTheme.accent)
                                    .cornerRadius(4)
                            }

                            Spacer(minLength: 0)

                            if resumingKey == thread.key {
                                ProgressView()
                                    .controlSize(.small)
                                    .tint(AgentBuddyTheme.accent)
                            }
                        }

                        HStack(spacing: 4) {
                            Text(relativeDate(updatedAt))
                                .foregroundColor(AgentBuddyTheme.textSecondary)
                            if let provider = thread.sessionModelLabel {
                                Text("•")
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                                Text(provider)
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                            }
                            if let parent {
                                Text("•")
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                                Text("from \(parent.sessionTitle)")
                                    .foregroundColor(AgentBuddyTheme.textMuted)
                            }
                        }
                        .agentBuddyFont(.caption2)
                        .lineLimit(1)
                    }
                }
                .contentShape(Rectangle())
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("sessions.sessionRow")
                .onTapGesture(perform: onSelectSession)
            }

            if isActive {
                lineageSummary(for: thread, derived: derived)
            }
        }
        .padding(.leading, 1)
        .padding(.trailing, 8)
        .padding(.vertical, 5)
        .background {
            if isActive {
                RoundedRectangle(cornerRadius: 6)
                    .fill(AgentBuddyTheme.surfaceLight.opacity(0.55))
            }
        }
        .contentShape(Rectangle())
        .hoverEffect(.highlight)
    }

    private func lineageSummary(for thread: AppSessionSummary, derived: SessionsDerivedData) -> some View {
        let parent = derived.parentByKey[thread.key]
        let siblings = derived.siblingsByKey[thread.key] ?? []
        let children = derived.childrenByKey[thread.key] ?? []
        let hasLineage = parent != nil || !siblings.isEmpty || !children.isEmpty

        return Group {
            if hasLineage {
                VStack(alignment: .leading, spacing: 5) {
                    Divider().background(AgentBuddyTheme.border.opacity(0.7))

                    HStack(spacing: 6) {
                        if let parent {
                            Button {
                                Task { await resumeSession(parent) }
                            } label: {
                                lineageChip(title: "Parent", count: 1, isInteractive: true)
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
                                lineageChip(title: "Siblings", count: siblings.count, isInteractive: true)
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
                                lineageChip(title: "Children", count: children.count, isInteractive: true)
                            }
                        }

                        Spacer(minLength: 0)
                    }
                }
            }
        }
    }

    private func lineageChip(title: String, count: Int, isInteractive: Bool) -> some View {
        Text("\(title) \(count)")
            .agentBuddyFont(.caption2)
            .foregroundColor(isInteractive ? AgentBuddyTheme.accent : AgentBuddyTheme.textMuted)
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .background(AgentBuddyTheme.surface.opacity(0.8))
            .overlay(
                RoundedRectangle(cornerRadius: 5)
                    .stroke(isInteractive ? AgentBuddyTheme.accent.opacity(0.5) : AgentBuddyTheme.border.opacity(0.5), lineWidth: 1)
            )
            .cornerRadius(5)
    }

    @ViewBuilder
    private func subagentStatusIndicator(_ status: AppSubagentStatus) -> some View {
        switch status {
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .agentBuddyFont(size: 8)
                .foregroundColor(AgentBuddyTheme.success)
                .frame(width: 8, height: 8)
        case .errored:
            Image(systemName: "exclamationmark.circle.fill")
                .agentBuddyFont(size: 8)
                .foregroundColor(AgentBuddyTheme.danger)
                .frame(width: 8, height: 8)
        case .shutdown:
            Image(systemName: "stop.circle.fill")
                .agentBuddyFont(size: 8)
                .foregroundColor(AgentBuddyTheme.textMuted)
                .frame(width: 8, height: 8)
        case .interrupted:
            Image(systemName: "pause.circle.fill")
                .agentBuddyFont(size: 8)
                .foregroundColor(AgentBuddyTheme.warning)
                .frame(width: 8, height: 8)
        case .pendingInit, .running, .unknown:
            Circle()
                .fill(AgentBuddyTheme.textMuted.opacity(0.4))
                .frame(width: 8, height: 8)
        }
    }

    private func relativeDate(_ date: Date) -> String {
        Self.relativeFormatter.localizedString(for: date, relativeTo: Date())
    }
}
