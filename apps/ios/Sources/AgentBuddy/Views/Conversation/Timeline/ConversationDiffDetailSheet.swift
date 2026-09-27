import SwiftUI

struct ConversationDiffDetailSheet: View {
    let title: String
    let stats: DiffStats
    let sections: [PresentedDiffSectionModel]
    @Environment(ThemeManager.self) private var themeManager
    @Environment(\.dismiss) private var dismiss
    @State private var collapsedSectionIDs: Set<String> = []
    private let fullDiffFontSize = AgentBuddyFont.conversationDiffPointSize
    private let maxStickyDiffSections = 8
    private let maxStickyDiffCharacters = 20_000

    init(title: String, diff: String, sections: [PresentedDiffSection]) {
        self.title = title
        let sectionModels = sections.isEmpty
            ? [PresentedDiffSectionModel(PresentedDiffSection(title: "", diff: diff))]
            : sections.map(PresentedDiffSectionModel.init)
        self.stats = DiffStats(
            additions: sectionModels.reduce(0) { $0 + $1.stats.additions },
            deletions: sectionModels.reduce(0) { $0 + $1.stats.deletions }
        )
        self.sections = sectionModels
        _collapsedSectionIDs = State(
            initialValue: Set(
                sectionModels
                    .filter { !$0.title.isEmpty }
                    .map(\.id)
            )
        )
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text("+\(stats.additions)")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.success)
                    Text("-\(stats.deletions)")
                        .agentBuddyFont(.caption2, weight: .semibold)
                        .foregroundColor(AgentBuddyTheme.danger)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)

                ScrollView(.vertical) {
                    LazyVStack(
                        alignment: .leading,
                        spacing: 8,
                        pinnedViews: usesStickyHeaders ? [.sectionHeaders] : []
                    ) {
                        ForEach(sections) { section in
                            if section.title.isEmpty {
                                diffSectionBody(section)
                            } else {
                                Section {
                                    diffSectionBody(section)
                                } header: {
                                    diffSectionHeader(section)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .background(AgentBuddyTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .id(themeManager.themeVersion)
    }

    private var usesStickyHeaders: Bool {
        guard sections.count <= maxStickyDiffSections else { return false }
        return sections.reduce(0) { $0 + $1.diff.count } <= maxStickyDiffCharacters
    }

    @ViewBuilder
    private func diffSection(_ section: PresentedDiffSectionModel) -> some View {
        let isExpanded = !collapsedSectionIDs.contains(section.id)

        VStack(alignment: .leading, spacing: 6) {
            if isExpanded {
                ScrollView(.horizontal, showsIndicators: true) {
                    SyntaxHighlightedDiffText(
                        diff: section.diff,
                        titleHint: section.title.isEmpty ? nil : section.title,
                        fontSize: fullDiffFontSize
                    )
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                }
                .background(AgentBuddyTheme.codeBackground.opacity(0.72))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
    }

    private func diffSectionHeader(_ section: PresentedDiffSectionModel) -> some View {
        let isExpanded = !collapsedSectionIDs.contains(section.id)

        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                toggleSection(section.id)
            }
        } label: {
            HStack(spacing: 8) {
                Text(section.title)
                    .agentBuddyFont(.caption2, weight: .bold)
                    .foregroundColor(AgentBuddyTheme.textSecondary)
                    .textCase(.uppercase)
                Spacer(minLength: 0)
                Text("+\(section.stats.additions)")
                    .agentBuddyFont(.caption2, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.success)
                Text("-\(section.stats.deletions)")
                    .agentBuddyFont(.caption2, weight: .semibold)
                    .foregroundColor(AgentBuddyTheme.danger)
                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    .agentBuddyFont(size: 10, weight: .medium)
                    .foregroundColor(AgentBuddyTheme.textMuted)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 6)
            .padding(.horizontal, 12)
            .background(AgentBuddyTheme.backgroundGradient)
        }
        .buttonStyle(.plain)
    }

    private func diffSectionBody(_ section: PresentedDiffSectionModel) -> some View {
        diffSection(section)
    }

    private func toggleSection(_ id: String) {
        if collapsedSectionIDs.contains(id) {
            collapsedSectionIDs.remove(id)
        } else {
            collapsedSectionIDs.insert(id)
        }
    }
}

struct PresentedDiffSectionModel: Identifiable {
    let id: String
    let title: String
    let diff: String
    let stats: DiffStats

    init(_ section: PresentedDiffSection) {
        self.id = section.id
        self.title = section.title
        self.diff = section.diff
        self.stats = DiffStats(diff: section.diff)
    }
}
