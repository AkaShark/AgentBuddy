import SwiftUI

extension ToolCallCardView {
    @ViewBuilder
    func sectionView(_ section: ToolCallIndexedValue<ToolCallSection>) -> some View {
        switch section.value {
        case .kv(let label, let entries):
            if !entries.isEmpty {
                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    sectionLabel(label)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(identifiedKeyValueEntries(entries)) { entry in
                            let textID = "\(section.id)-kv-\(entry.id)"
                            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                                Text(entry.value.key + ":")
                                    .buddyText(.code, weight: .semibold)
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                                    Text(visibleText(entry.value.value, id: textID))
                                        .buddyText(.code)
                                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                                        .textSelection(.enabled)
                                    longTextToggle(for: entry.value.value, id: textID)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    .padding(BuddySpacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .timelineCodeSurface()
                }
            }
        case .code(let label, let language, let content):
            codeLikeSection(id: section.id, label: label, language: language, content: content)
        case .json(let label, let content):
            codeLikeSection(id: section.id, label: label, language: "json", content: content)
        case .diff(let label, let content):
            diffSection(id: section.id, label: label, content: content)
        case .text(let label, let content):
            inlineTextSection(id: section.id, label: label, content: content)
        case .list(let label, let items):
            if !items.isEmpty {
                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    sectionLabel(label)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(identifiedTextItems(items, prefix: "list")) { item in
                            let textID = "\(section.id)-list-\(item.id)"
                            HStack(alignment: .firstTextBaseline, spacing: BuddySpacing.xs) {
                                Text(verbatim: "•")
                                    .buddyText(.label, weight: .regular)
                                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                                    .accessibilityHidden(true)
                                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                                    Text(visibleText(item.value, id: textID))
                                        .buddyText(.label, weight: .regular)
                                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                                        .textSelection(.enabled)
                                    longTextToggle(for: item.value, id: textID)
                                }
                            }
                        }
                    }
                    .padding(BuddySpacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .timelineCodeSurface()
                }
            }
        case .progress(let label, let items):
            if !items.isEmpty {
                VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                    sectionLabel(label)
                    VStack(alignment: .leading, spacing: 6) {
                        let identifiedItems = identifiedTextItems(items, prefix: "progress")
                        ForEach(identifiedItems) { item in
                            let textID = "\(section.id)-progress-\(item.id)"
                            HStack(alignment: .top, spacing: BuddySpacing.xs) {
                                Circle()
                                    .fill(item.index == identifiedItems.count - 1 ? kindAccent : AgentBuddyTheme.textSecondary)
                                    .frame(width: 6, height: 6)
                                    .padding(.top, 7)
                                    .accessibilityHidden(true)
                                VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
                                    Text(visibleText(item.value, id: textID))
                                        .buddyText(.label, weight: .regular)
                                        .foregroundStyle(AgentBuddyTheme.textPrimary)
                                        .textSelection(.enabled)
                                    longTextToggle(for: item.value, id: textID)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    .padding(BuddySpacing.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .timelineCodeSurface()
                }
            }
        }
    }

    private func sectionLabel(_ label: String) -> some View {
        TimelineSectionLabel(verbatim: label)
    }

    private func codeLikeSection(id: String, label: String, language: String, content: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            sectionLabel(label)
            CodeBlockView(language: language, code: visibleText(content, id: id), fontSize: contentFontSize, nested: true)
            longTextToggle(for: content, id: id)
        }
    }

    private func inlineTextSection(id: String, label: String, content: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            sectionLabel(label)
            Text(verbatim: visibleText(content, id: id))
                .buddyText(.code)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, BuddySpacing.sm)
                .padding(.vertical, BuddySpacing.xs)
                .timelineCodeSurface()
                .fixedSize(horizontal: false, vertical: true)
            longTextToggle(for: content, id: id)
        }
    }

    private func diffSection(id: String, label: String, content: String) -> some View {
        let isCollapsible = model.kind == .fileDiff && !label.isEmpty
        let isExpanded = !collapsedDiffSections.contains(id)

        return VStack(alignment: .leading, spacing: 6) {
            if isCollapsible {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        toggleDiffSection(id)
                    }
                } label: {
                    HStack(spacing: BuddySpacing.xs) {
                        sectionLabel(label)
                        Spacer(minLength: 0)
                        TimelineDisclosureChevron(expanded: isExpanded)
                    }
                    .frame(minHeight: BuddySize.minHitTarget)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            } else if !label.isEmpty {
                sectionLabel(label)
            }

            if isExpanded {
                ScrollView(.horizontal, showsIndicators: true) {
                    SyntaxHighlightedDiffText(
                        diff: visibleText(content, id: id),
                        titleHint: label.isEmpty ? nil : label,
                        fontSize: terminalFontSize
                    )
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.vertical, BuddySpacing.xs)
                }
                .timelineCodeSurface()
                longTextToggle(for: content, id: id)
            }
        }
    }

    private func toggleDiffSection(_ id: String) {
        if collapsedDiffSections.contains(id) {
            collapsedDiffSections.remove(id)
        } else {
            collapsedDiffSections.insert(id)
        }
    }

    private func visibleText(_ text: String, id: String) -> String {
        guard shouldLimitText(text), !expandedLongTextIDs.contains(id) else {
            return text
        }
        return String(text.prefix(maxVisibleTextCharacters))
    }

    private func shouldLimitText(_ text: String) -> Bool {
        text.count > maxVisibleTextCharacters
    }

    @ViewBuilder
    private func longTextToggle(for text: String, id: String) -> some View {
        if shouldLimitText(text) {
            Button {
                withAnimation(.easeInOut(duration: 0.18)) {
                    if expandedLongTextIDs.contains(id) {
                        expandedLongTextIDs.remove(id)
                    } else {
                        expandedLongTextIDs.insert(id)
                    }
                }
            } label: {
                Text(expandedLongTextIDs.contains(id) ? "Show less" : "Show more")
                    .timelineLinkAction()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(expandedLongTextIDs.contains(id) ? "Show less text" : "Show more text")
        }
    }

    var identifiedSections: [ToolCallIndexedValue<ToolCallSection>] {
        let visibleSections = model.sections.filter { section in
            guard model.kind == .imageView else { return true }
            return !sectionContainsInlineImagePayload(section)
        }

        return identifiedValues(visibleSections, prefix: "section") { section in
            switch section {
            case .kv(let label, let entries):
                return "\(label)|kv|\(entries.map { "\($0.key)=\($0.value)" }.joined(separator: "|"))"
            case .code(let label, let language, let content):
                return "\(label)|code|\(language)|\(content)"
            case .json(let label, let content):
                return "\(label)|json|\(content)"
            case .diff(let label, let content):
                return "\(label)|diff|\(content)"
            case .text(let label, let content):
                return "\(label)|text|\(content)"
            case .list(let label, let items):
                return "\(label)|list|\(items.joined(separator: "|"))"
            case .progress(let label, let items):
                return "\(label)|progress|\(items.joined(separator: "|"))"
            }
        }
    }

    private func identifiedKeyValueEntries(_ entries: [ToolCallKeyValue]) -> [ToolCallIndexedValue<ToolCallKeyValue>] {
        identifiedValues(entries, prefix: "kv") { entry in
            "\(entry.key)|\(entry.value)"
        }
    }

    private func identifiedTextItems(_ values: [String], prefix: String) -> [ToolCallIndexedValue<String>] {
        identifiedValues(values, prefix: prefix) { $0 }
    }

    private func identifiedValues<Value>(
        _ values: [Value],
        prefix: String,
        key: (Value) -> String
    ) -> [ToolCallIndexedValue<Value>] {
        var seen: [String: Int] = [:]
        return values.enumerated().map { index, value in
            let signature = key(value)
            let occurrence = seen[signature, default: 0]
            seen[signature] = occurrence + 1
            return ToolCallIndexedValue(
                id: "\(prefix)-\(signature.hashValue)-\(occurrence)",
                index: index,
                value: value
            )
        }
    }
}

struct ToolCallIndexedValue<Value>: Identifiable {
    let id: String
    let index: Int
    let value: Value
}
