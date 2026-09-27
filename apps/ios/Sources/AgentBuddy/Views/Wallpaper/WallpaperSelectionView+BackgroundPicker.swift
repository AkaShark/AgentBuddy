import SwiftUI
import PhotosUI

extension WallpaperSelectionView {
    var backgroundTabContent: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: 16) {
                // Theme thumbnails
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        noWallpaperThumbnail
                        ForEach(themeManager.themeIndex) { entry in
                            themeThumbnail(for: entry)
                        }
                    }
                    .padding(.horizontal, 16)
                }

                Divider().overlay(AgentBuddyTheme.separator)

                // Photos picker
                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    HStack(spacing: 10) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 16))
                            .foregroundStyle(AgentBuddyTheme.accent)
                        Text("Choose Wallpaper from Photos")
                            .agentBuddyFont(size: 14)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12))
                            .foregroundStyle(AgentBuddyTheme.textMuted)
                    }
                    .padding(.horizontal, 16)
                }
                .onChange(of: selectedPhoto) { _, newItem in
                    Task { await loadPhoto(newItem) }
                }

                // Video picker
                PhotosPicker(selection: $selectedVideoItem, matching: .videos) {
                    HStack(spacing: 10) {
                        Image(systemName: "video.fill")
                            .font(.system(size: 16))
                            .foregroundStyle(AgentBuddyTheme.accent)
                        Text("Choose Video from Photos")
                            .agentBuddyFont(size: 14)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                        Spacer()
                        if isProcessingVideo {
                            ProgressView()
                                .tint(AgentBuddyTheme.accent)
                        } else {
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12))
                                .foregroundStyle(AgentBuddyTheme.textMuted)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .disabled(isProcessingVideo)
                .onChange(of: selectedVideoItem) { _, newItem in
                    Task { await loadVideo(newItem) }
                }

                // Video URL input
                HStack(spacing: 10) {
                    Image(systemName: "link")
                        .font(.system(size: 16))
                        .foregroundStyle(AgentBuddyTheme.accent)
                    TextField("Paste video URL", text: $videoURLText)
                        .agentBuddyFont(size: 14)
                        .foregroundStyle(AgentBuddyTheme.textPrimary)
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
                                .agentBuddyFont(size: 13, weight: .semibold)
                                .foregroundStyle(AgentBuddyTheme.accent)
                        }
                        .disabled(isProcessingVideo)
                    }
                }
                .padding(.horizontal, 16)

                // Color picker
                colorRow

                Spacer().frame(height: 16)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Thumbnails

    private var noWallpaperThumbnail: some View {
        Button {
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
            VStack(spacing: 6) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(AgentBuddyTheme.surface)
                        .frame(width: 68, height: 100)
                    Image(systemName: "xmark")
                        .font(.system(size: 18))
                        .foregroundStyle(AgentBuddyTheme.textMuted)
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(selectedThemeSlug == nil && previewConfig?.type == .none ? AgentBuddyTheme.accent : AgentBuddyTheme.border, lineWidth: 2)
                )

                Text("None")
                    .agentBuddyFont(size: 10)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
            }
        }
    }

    private func themeThumbnail(for entry: ThemeIndexEntry) -> some View {
        Button {
            selectedThemeSlug = entry.slug
            selectedColor = nil
            customImage = nil
            let config = WallpaperConfig(type: .theme, themeSlug: entry.slug)
            previewConfig = config
            onSelectWallpaper?(config, nil)
        } label: {
            VStack(spacing: 6) {
                Image(uiImage: wallpaperManager.generateThumbnail(for: entry))
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 68, height: 100)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(selectedThemeSlug == entry.slug ? AgentBuddyTheme.accent : AgentBuddyTheme.border, lineWidth: 2)
                    )

                Text(entry.name)
                    .agentBuddyFont(size: 10)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .lineLimit(1)
                    .frame(width: 68)
            }
        }
    }

    // MARK: - Color Picker

    private var colorRow: some View {
        HStack(spacing: 10) {
            Image(systemName: "paintpalette")
                .font(.system(size: 16))
                .foregroundStyle(AgentBuddyTheme.accent)
            Text("Set a Color")
                .agentBuddyFont(size: 14)
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
            .frame(width: 30, height: 30)
        }
        .padding(.horizontal, 16)
    }

    private func colorToHex(_ color: Color) -> String {
        let uiColor = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
