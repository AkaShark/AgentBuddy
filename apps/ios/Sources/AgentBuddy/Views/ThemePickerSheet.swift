import SwiftUI

// MARK: - Theme picker row

enum ThemePickerTrailingAccessory {
    case none
    case chevron
    case checkmark
}

struct ThemePickerRow: View {
    let entry: ThemeIndexEntry?
    let trailingAccessory: ThemePickerTrailingAccessory

    var body: some View {
        HStack(spacing: BuddySpacing.sm) {
            ThemePreviewBadge(
                backgroundHex: entry?.backgroundHex ?? "#000000",
                foregroundHex: entry?.foregroundHex ?? "#FFFFFF",
                accentHex: entry?.accentHex ?? "#00FF00"
            )
            .accessibilityHidden(true)

            (entry.map { Text(verbatim: $0.name) } ?? Text("Unknown Theme"))
                .buddyText(.body, weight: trailingAccessory == .checkmark ? .semibold : .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)

            Spacer(minLength: BuddySpacing.sm)

            switch trailingAccessory {
            case .none:
                EmptyView()
            case .chevron:
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
            case .checkmark:
                SettingsMintCheckmark()
            }
        }
        .contentShape(Rectangle())
    }
}

// MARK: - Theme picker sheet

/// Every theme stays selectable. Without a search, the AgentBuddy themes (Mint
/// first) are listed on top, followed by all other themes.
struct ThemePickerSheet: View {
    let title: String
    let themes: [ThemeIndexEntry]
    let selectedSlug: String
    let onSelect: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var searchQuery = ""

    private static let brandPrefix = "agentbuddy-"

    private var trimmedSearchQuery: String {
        searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var filteredThemes: [ThemeIndexEntry] {
        guard !trimmedSearchQuery.isEmpty else { return themes }
        return themes.filter { entry in
            entry.name.localizedCaseInsensitiveContains(trimmedSearchQuery) ||
            entry.slug.localizedCaseInsensitiveContains(trimmedSearchQuery)
        }
    }

    private var suggestedThemes: [ThemeIndexEntry] {
        let brand = themes.filter { $0.slug.hasPrefix(Self.brandPrefix) }
        return brand.filter { $0.slug.contains("mint") } + brand.filter { !$0.slug.contains("mint") }
    }

    private var otherThemes: [ThemeIndexEntry] {
        themes.filter { !$0.slug.hasPrefix(Self.brandPrefix) }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: BuddySpacing.sm) {
                searchField

                if filteredThemes.isEmpty {
                    emptyState
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: BuddySpacing.xl) {
                            if trimmedSearchQuery.isEmpty, !suggestedThemes.isEmpty {
                                group(title: "Suggested", entries: suggestedThemes)
                                group(title: "All themes", entries: otherThemes)
                            } else {
                                group(title: nil, entries: filteredThemes)
                            }
                        }
                        .padding(.horizontal, BuddySpacing.xl)
                        .padding(.bottom, BuddySpacing.xl)
                    }
                }
            }
            .padding(.top, BuddySpacing.xs)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .buddyPageBackground()
            .navigationTitle(LocalizedStringKey(title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                    .fontWeight(.semibold)
                    .foregroundStyle(AgentBuddyTheme.link)
                }
            }
        }
    }

    @ViewBuilder
    private func group(title: LocalizedStringKey?, entries: [ThemeIndexEntry]) -> some View {
        if !entries.isEmpty {
            VStack(alignment: .leading, spacing: BuddySpacing.xs) {
                if let title {
                    Text(title)
                        .settingsMintHeader()
                        .accessibilityAddTraits(.isHeader)
                        .padding(.horizontal, BuddySpacing.xxs)
                }
                VStack(spacing: 0) {
                    ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                        if index > 0 {
                            BuddyDivider()
                                .padding(.leading, BuddySpacing.md)
                        }
                        row(for: entry)
                    }
                }
                .buddyCard(.surface, radius: BuddyRadius.card, padding: 0)
            }
        }
    }

    private func row(for entry: ThemeIndexEntry) -> some View {
        let isSelected = entry.slug == selectedSlug
        return Button {
            onSelect(entry.slug)
            dismiss()
        } label: {
            ThemePickerRow(
                entry: entry,
                trailingAccessory: isSelected ? .checkmark : .none
            )
            .padding(.horizontal, BuddySpacing.md)
            .frame(maxWidth: .infinity, minHeight: BuddySize.control, alignment: .leading)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var searchField: some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.button, style: .continuous)
        return HStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)

            TextField("Search themes", text: $searchQuery)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .tint(AgentBuddyTheme.focus)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)

            if !searchQuery.isEmpty {
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                        .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Clear search"))
            }
        }
        .padding(.leading, BuddySpacing.md)
        .frame(minHeight: BuddySize.control)
        .background(AgentBuddyTheme.surface, in: shape)
        .overlay { shape.strokeBorder(AgentBuddyTheme.borderControl, lineWidth: 1) }
        .padding(.horizontal, BuddySpacing.xl)
    }

    private var emptyState: some View {
        VStack(spacing: BuddySpacing.xs) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(AgentBuddyTheme.textSecondary)
                .accessibilityHidden(true)

            Text("No matching themes")
                .buddyText(.heading)
                .foregroundStyle(AgentBuddyTheme.textPrimary)

            if !trimmedSearchQuery.isEmpty {
                Text(trimmedSearchQuery)
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding(.top, BuddySpacing.xxxl)
        .padding(.horizontal, BuddySpacing.xl)
    }
}

// MARK: - Theme Preview Badge

/// Swatch drawn from the theme's own colours (data, not interface colour).
struct ThemePreviewBadge: View {
    let backgroundHex: String
    let foregroundHex: String
    let accentHex: String

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Text(verbatim: "Aa")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(Color(hex: foregroundHex))
                .frame(width: 28, height: 22)
                .background(
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .fill(Color(hex: backgroundHex))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .stroke(AgentBuddyTheme.border, lineWidth: 0.5)
                )
            Circle()
                .fill(Color(hex: accentHex))
                .frame(width: 6, height: 6)
                .offset(x: 1, y: 1)
        }
    }

    @MainActor
    static func renderToImage(backgroundHex: String, foregroundHex: String, accentHex: String) -> UIImage {
        let badge = ThemePreviewBadge(backgroundHex: backgroundHex, foregroundHex: foregroundHex, accentHex: accentHex)
        let renderer = ImageRenderer(content: badge)
        renderer.scale = UIScreen.main.scale
        guard let cgImage = renderer.cgImage else { return UIImage() }
        return UIImage(cgImage: cgImage).withRenderingMode(.alwaysOriginal)
    }
}
