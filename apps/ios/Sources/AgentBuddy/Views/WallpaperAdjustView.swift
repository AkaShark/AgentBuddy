import SwiftUI

struct WallpaperAdjustView: View {
    @Environment(WallpaperManager.self) private var wallpaperManager
    @Environment(ThemeManager.self) private var themeManager
    @Environment(\.dismiss) private var dismiss

    let threadKey: ThreadKey?
    var serverId: String? = nil
    let initialConfig: WallpaperConfig
    var customImage: UIImage?
    var onDone: (() -> Void)?

    private var isServerOnly: Bool { threadKey == nil }
    private var resolvedServerId: String? { threadKey?.serverId ?? serverId }

    @State private var isBlurred: Bool = false
    @State private var motionEnabled: Bool = false
    @State private var brightness: Double = 1.0
    @State private var hasLoaded = false

    var body: some View {
        ZStack {
            // Sample bubbles
            sampleBubbles
                .padding(.top, 80)
                .padding(.bottom, 280)

            // Bottom controls
            VStack {
                Spacer()
                controlsCard
            }

            // Cancel button (top-left)
            VStack {
                HStack {
                    WallpaperFloatingPillButton(title: "Cancel") {
                        onDone?()
                    }
                    Spacer()
                }
                .padding(.horizontal, BuddySpacing.md)
                .padding(.top, BuddySpacing.xs)
                Spacer()
            }
        }
        .background {
            wallpaperPreview
                .blur(radius: isBlurred ? brightness * 20 : 0)
                .opacity(brightness)
                .ignoresSafeArea()
        }
        .navigationBarBackButtonHidden(true)
        .onAppear {
            guard !hasLoaded else { return }
            hasLoaded = true
            isBlurred = initialConfig.blur > 0.01
            motionEnabled = initialConfig.motionEnabled
            brightness = initialConfig.brightness
        }
    }

    // MARK: - Preview

    @ViewBuilder
    private var wallpaperPreview: some View {
        switch initialConfig.type {
        case .theme:
            if let slug = initialConfig.themeSlug,
               let image = wallpaperManager.generateWallpaper(themeSlug: slug, themeManager: themeManager) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                AgentBuddyTheme.backgroundGradient
            }
        case .customImage:
            if let image = customImage ?? {
                if let threadKey {
                    return wallpaperManager.wallpaperImage(for: initialConfig, scope: .thread(threadKey), themeManager: themeManager)
                } else if let resolvedServerId {
                    return wallpaperManager.wallpaperImage(for: initialConfig, scope: .server(resolvedServerId), themeManager: themeManager)
                }
                return nil
            }() {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else {
                AgentBuddyTheme.backgroundGradient
            }
        case .solidColor:
            if let hex = initialConfig.colorHex {
                Color(hex: hex)
            } else {
                AgentBuddyTheme.backgroundGradient
            }
        case .customVideo, .videoUrl:
            let fileURL: URL = {
                if let threadKey {
                    return wallpaperManager.videoFileURL(for: .thread(threadKey))
                } else if let resolvedServerId {
                    return wallpaperManager.videoFileURL(for: .server(resolvedServerId))
                }
                return URL(fileURLWithPath: "/dev/null")
            }()
            if FileManager.default.fileExists(atPath: fileURL.path) {
                VideoWallpaperPlayerView(fileURL: fileURL)
            } else {
                AgentBuddyTheme.backgroundGradient
            }
        case .none:
            AgentBuddyTheme.backgroundGradient
        }
    }

    // MARK: - Sample Bubbles

    private var sampleBubbles: some View {
        VStack(spacing: BuddySpacing.sm) {
            Spacer()
            HStack {
                Spacer(minLength: BuddySpacing.xxxl)
                WallpaperSampleUserBubble(text: "Refactor the auth middleware")
            }
            .padding(.horizontal, BuddySpacing.md)

            HStack {
                Text("I'll review the auth middleware and refactor it for better separation of concerns.")
                    .buddyText(.body)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .wallpaperSampleAssistantCard()
                Spacer(minLength: 0)
            }
            .padding(.horizontal, BuddySpacing.md)

            Spacer()
        }
    }

    // MARK: - Controls Card

    private var controlsCard: some View {
        VStack(spacing: BuddySpacing.md) {
            WallpaperPanelGrabber()
                .padding(.top, BuddySpacing.sm)

            // Toggles
            HStack(spacing: BuddySpacing.xl) {
                toggleOption(label: "Blurred", isOn: $isBlurred)
                toggleOption(label: "Motion", isOn: $motionEnabled)
            }
            .padding(.horizontal, BuddySpacing.md)

            // Brightness slider
            HStack(spacing: BuddySpacing.sm) {
                Image(systemName: "sun.min")
                    .font(.system(size: 17))
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
                    .accessibilityHidden(true)
                Slider(value: $brightness, in: 0.2...1.0)
                    .tint(AgentBuddyTheme.action)
                Image(systemName: "sun.max")
                    .font(.system(size: 17))
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, BuddySpacing.md)

            // Apply buttons: the thread scope is the primary action when present.
            VStack(spacing: BuddySpacing.sm) {
                if let threadKey {
                    BuddyButton("Apply for This Thread") {
                        applyWallpaper(scope: .thread(threadKey))
                    }
                }

                if let resolvedServerId {
                    BuddyButton("Apply for This Server", kind: threadKey == nil ? .primary : .secondary) {
                        applyWallpaper(scope: .server(resolvedServerId))
                    }
                }
            }
            .padding(.horizontal, BuddySpacing.md)

            Spacer().frame(height: BuddySpacing.md)
        }
        .wallpaperBottomPanel()
    }

    /// Checkbox-style option: the symbol shape (filled check vs. empty square)
    /// and the selected trait carry the state, not just the colour.
    private func toggleOption(label: LocalizedStringKey, isOn: Binding<Bool>) -> some View {
        Button {
            isOn.wrappedValue.toggle()
        } label: {
            HStack(spacing: BuddySpacing.xs) {
                Image(systemName: isOn.wrappedValue ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20))
                    .foregroundStyle(isOn.wrappedValue ? AgentBuddyTheme.action : AgentBuddyTheme.borderControl)
                    .accessibilityHidden(true)
                Text(label)
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
            }
            .frame(minHeight: BuddySize.minHitTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn.wrappedValue ? .isSelected : [])
    }

    // MARK: - Apply

    private func applyWallpaper(scope: WallpaperScope) {
        var config = initialConfig
        config.blur = isBlurred ? 0.5 : 0.0
        config.brightness = brightness
        config.motionEnabled = motionEnabled
        wallpaperManager.setWallpaper(config, scope: scope)
        onDone?()
    }
}
