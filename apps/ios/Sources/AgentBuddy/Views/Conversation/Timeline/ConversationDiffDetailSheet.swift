import SwiftUI

struct ConversationDiffDetailSheet: View {
    let title: String
    let stats: DiffStats
    let sections: [PresentedDiffSectionModel]
    @Environment(ThemeManager.self) private var themeManager
    @Environment(\.dismiss) private var dismiss
    @State private var collapsedSectionIDs: Set<String> = []
    private let fullDiffFontSize = BuddyTextStyle.code.size
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
                HStack(spacing: BuddySpacing.xs) {
                    Text(verbatim: "+\(stats.additions)")
                        .buddyText(.label, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.success)
                    Text(verbatim: "−\(stats.deletions)")
                        .buddyText(.label, weight: .semibold)
                        .monospacedDigit()
                        .foregroundStyle(AgentBuddyTheme.danger)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("\(stats.additions) additions, \(stats.deletions) deletions"))
                .padding(.horizontal, BuddySpacing.md)
                .padding(.top, BuddySpacing.sm)
                .padding(.bottom, BuddySpacing.xs)

                ScrollView(.vertical) {
                    LazyVStack(
                        alignment: .leading,
                        spacing: BuddySpacing.xs,
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
                    .padding(.horizontal, BuddySpacing.md)
                    .padding(.bottom, BuddySpacing.md)
                }
            }
            .buddyPageBackground()
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .buddySheetStyle()
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
                        .padding(.horizontal, BuddySpacing.sm)
                        .padding(.vertical, BuddySpacing.xs)
                }
                .timelineCodeSurface(nested: false)
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
            HStack(spacing: BuddySpacing.xs) {
                Text(verbatim: section.title)
                    .buddyText(.code)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Spacer(minLength: 0)
                Text(verbatim: "+\(section.stats.additions)")
                    .buddyText(.caption, weight: .semibold)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.success)
                Text(verbatim: "−\(section.stats.deletions)")
                    .buddyText(.caption, weight: .semibold)
                    .monospacedDigit()
                    .foregroundStyle(AgentBuddyTheme.danger)
                TimelineDisclosureChevron(expanded: isExpanded)
            }
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
            .padding(.horizontal, BuddySpacing.xxs)
            .background(AgentBuddyTheme.background)
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
