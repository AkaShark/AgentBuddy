import SwiftUI
import PhotosUI

extension WallpaperSelectionView {
    var backgroundTabContent: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: BuddySpacing.md) {
                // Theme thumbnails
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: BuddySpacing.sm) {
                        noWallpaperThumbnail
                        ForEach(themeManager.themeIndex) { entry in
                            themeThumbnail(for: entry)
                        }
                    }
                    .padding(.horizontal, BuddySpacing.md)
                    .padding(.vertical, BuddySpacing.xxs)
                }

                VStack(spacing: 0) {
                    // Photos picker
                    PhotosPicker(selection: $selectedPhoto, matching: .images) {
                        sourceRow(title: "Choose Wallpaper from Photos", systemImage: "photo.on.rectangle") {
                            chevron
                        }
                    }
                    .buttonStyle(.plain)
                    .onChange(of: selectedPhoto) { _, newItem in
                        Task { await loadPhoto(newItem) }
                    }

                    BuddyDivider().padding(.leading, BuddySpacing.md + 24 + BuddySpacing.sm)

                    // Video picker
                    PhotosPicker(selection: $selectedVideoItem, matching: .videos) {
                        sourceRow(title: "Choose Video from Photos", systemImage: "video.fill") {
                            if isProcessingVideo {
                                ProgressView()
                                    .tint(AgentBuddyTheme.textSecondary)
                            } else {
                                chevron
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(isProcessingVideo)
                    .onChange(of: selectedVideoItem) { _, newItem in
                        Task { await loadVideo(newItem) }
                    }

                    BuddyDivider().padding(.leading, BuddySpacing.md + 24 + BuddySpacing.sm)

                    // Video URL input
                    HStack(spacing: BuddySpacing.sm) {
                        sourceIcon("link")
                        TextField("Paste video URL", text: $videoURLText)
                            .buddyText(.body)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .tint(AgentBuddyTheme.focus)
                            .textContentType(.URL)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .submitLabel(.go)
                            .onSubmit { Task { await loadVideoFromURL() } }
                        if !videoURLText.isEmpty {
                            Button {
                                Task { await loadVideoFromURL() }
                            } label: {
                                Text("Go")
                                    .buddyText(.label, weight: .semibold)
                                    .foregroundStyle(isProcessingVideo ? AgentBuddyTheme.onDisabled : AgentBuddyTheme.link)
                                    .frame(minWidth: BuddySize.minHitTarget, minHeight: BuddySize.minHitTarget)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(isProcessingVideo)
                        }
                    }
                    .padding(.horizontal, BuddySpacing.md)
                    .frame(minHeight: BuddySize.control)

                    BuddyDivider().padding(.leading, BuddySpacing.md + 24 + BuddySpacing.sm)

                    // Color picker
                    colorRow
                }
                .buddyCard(.surface, radius: BuddyRadius.card, padding: 0)
                .padding(.horizontal, BuddySpacing.md)

                Spacer().frame(height: BuddySpacing.md)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Rows

    private func sourceRow<Accessory: View>(
        title: LocalizedStringKey,
        systemImage: String,
        @ViewBuilder accessory: () -> Accessory
    ) -> some View {
        HStack(spacing: BuddySpacing.sm) {
            sourceIcon(systemImage)
            Text(title)
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            Spacer(minLength: BuddySpacing.xs)
            accessory()
        }
        .padding(.horizontal, BuddySpacing.md)
        .frame(minHeight: BuddySize.control)
        .contentShape(Rectangle())
    }

    private func sourceIcon(_ systemImage: String) -> some View {
        Image(systemName: systemImage)
            .font(.system(size: 17, weight: .medium))
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .frame(width: 24)
            .accessibilityHidden(true)
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(AgentBuddyTheme.textSecondary)
            .accessibilityHidden(true)
    }

    // MARK: - Thumbnails

    private var noWallpaperThumbnail: some View {
        // Same condition as before the restyle: the old `previewConfig?.type == .none`
        // compared against `Optional.none`, i.e. "nothing previewed yet".
        let isSelected = selectedThemeSlug == nil && previewConfig == nil
        return Button {
            previewConfig = WallpaperConfig(type: .none)
            selectedThemeSlug = nil
            selectedColor = nil
            customImage = nil
            // Apply immediately
            if let threadKey {
                wallpaperManager.setWallpaper(WallpaperConfig(type: .none), scope: .thread(threadKey))
            } else if let resolvedServerId {
                wallpaperManager.setWallpaper(WallpaperConfig(type: .none), scope: .server(resolvedServerId))
            }
        } label: {
            thumbnailLabel(title: Text("None"), isSelected: isSelected) {
                ZStack {
                    AgentBuddyTheme.surface
                    Image(systemName: "xmark")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func themeThumbnail(for entry: ThemeIndexEntry) -> some View {
        let isSelected = selectedThemeSlug == entry.slug
        return Button {
            selectedThemeSlug = entry.slug
            selectedColor = nil
            customImage = nil
            let config = WallpaperConfig(type: .theme, themeSlug: entry.slug)
            previewConfig = config
            onSelectWallpaper?(config, nil)
        } label: {
            thumbnailLabel(title: Text(verbatim: entry.name), isSelected: isSelected) {
                Image(uiImage: wallpaperManager.generateThumbnail(for: entry))
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    /// 68×100 thumbnail with its name below. Selection shows a 2pt action
    /// outline plus a checkmark badge, so it does not depend on colour alone.
    private func thumbnailLabel<Preview: View>(
        title: Text,
        isSelected: Bool,
        @ViewBuilder preview: () -> Preview
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
        return VStack(spacing: BuddySpacing.xxs) {
            preview()
                .frame(width: 68, height: 100)
                .clipShape(shape)
                .overlay {
                    shape.strokeBorder(
                        isSelected ? AgentBuddyTheme.action : AgentBuddyTheme.border,
                        lineWidth: isSelected ? 2 : 1
                    )
                }
                .overlay(alignment: .topTrailing) {
                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(AgentBuddyTheme.onAction, AgentBuddyTheme.action)
                            .padding(BuddySpacing.xxs)
                            .accessibilityHidden(true)
                    }
                }

            title
                .buddyText(.caption, weight: isSelected ? .semibold : .regular)
                .foregroundStyle(isSelected ? AgentBuddyTheme.textPrimary : AgentBuddyTheme.textSecondary)
                .lineLimit(1)
                .frame(width: 68)
        }
        .contentShape(Rectangle())
    }

    // MARK: - Color Picker

    private var colorRow: some View {
        HStack(spacing: BuddySpacing.sm) {
            sourceIcon("paintpalette")
            Text("Set a Color")
                .buddyText(.body)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
            Spacer()

            ColorPicker("", selection: Binding(
                get: { selectedColor ?? .black },
                set: { color in
                    selectedColor = color
                    selectedThemeSlug = nil
                    customImage = nil
                    let hex = colorToHex(color)
                    let config = WallpaperConfig(type: .solidColor, colorHex: hex)
                    previewConfig = config
                    onSelectWallpaper?(config, nil)
                }
            ), supportsOpacity: false)
            .labelsHidden()
            .frame(width: BuddySize.minHitTarget, height: BuddySize.minHitTarget)
            .accessibilityLabel(Text("Set a Color"))
        }
        .padding(.horizontal, BuddySpacing.md)
        .frame(minHeight: BuddySize.control)
    }

    private func colorToHex(_ color: Color) -> String {
        let uiColor = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
