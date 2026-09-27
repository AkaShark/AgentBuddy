import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import Hairball
import HairballUI

struct WallpaperSelectionView: View {
    @Environment(WallpaperManager.self) var wallpaperManager
    @Environment(ThemeManager.self) var themeManager
    @Environment(\.dismiss) private var dismiss

    let threadKey: ThreadKey?
    var serverId: String? = nil
    var onSelectWallpaper: ((WallpaperConfig, UIImage?) -> Void)?
    var onClose: (() -> Void)?

    var resolvedServerId: String? {
        threadKey?.serverId ?? serverId
    }

    @State var selectedThemeSlug: String?
    @State var selectedColor: Color?
    @State var selectedPhoto: PhotosPickerItem?
    @State var customImage: UIImage?
    @State var previewConfig: WallpaperConfig?
    @State var selectedVideoItem: PhotosPickerItem?
    @State var isProcessingVideo = false
    @State var videoURLText: String = ""
    @State var videoFileURL: URL?
    @State var videoErrorMessage: String?
    @State private var activeTab: WallpaperTab = .background
    @State var typingEffectConfig: TypingEffectConfig = .default
    @State private var sheetOffset: CGFloat = 0
    @GestureState private var dragOffset: CGFloat = 0

    var body: some View {
        ZStack {
            // Sample bubbles overlay
            sampleBubbles
                .padding(.top, 80)
                .padding(.bottom, max(300 - sheetOffset - dragOffset, 80))

            // Bottom card
            VStack {
                Spacer()
                bottomCard
            }
            .offset(y: max(sheetOffset + dragOffset, 0))
            .gesture(
                DragGesture()
                    .updating($dragOffset) { value, state, _ in
                        state = value.translation.height
                    }
                    .onEnded { value in
                        withAnimation(.interactiveSpring(response: 0.35, dampingFraction: 0.85)) {
                            let projected = value.predictedEndTranslation.height
                            if projected > 120 {
                                // Snap down (collapsed)
                                sheetOffset = 280
                            } else if projected < -80 {
                                // Snap up (expanded)
                                sheetOffset = 0
                            } else {
                                // Stay at nearest snap point
                                sheetOffset = sheetOffset + value.translation.height > 140 ? 280 : 0
                            }
                        }
                    }
            )

            // Close button (top-left)
            VStack {
                HStack {
                    WallpaperFloatingPillButton(title: "Close") {
                        onClose?()
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
                .ignoresSafeArea()
        }
        .navigationBarBackButtonHidden(true)
        .alert("Video Error", isPresented: Binding(
            get: { videoErrorMessage != nil },
            set: { if !$0 { videoErrorMessage = nil } }
        )) {
            Button("OK") { videoErrorMessage = nil }
        } message: {
            Text(videoErrorMessage ?? "")
        }
        .onAppear {
            if let threadKey {
                typingEffectConfig = wallpaperManager.resolveTypingEffect(for: threadKey)
            } else if let resolvedServerId {
                typingEffectConfig = wallpaperManager.resolveTypingEffectForServer(resolvedServerId)
            }
        }
    }

    // MARK: - Preview Background

    @ViewBuilder
    private var wallpaperPreview: some View {
        if let config = previewConfig {
            switch config.type {
            case .theme:
                if let slug = config.themeSlug,
                   let image = wallpaperManager.generateWallpaper(themeSlug: slug, themeManager: themeManager) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    AgentBuddyTheme.backgroundGradient
                }
            case .solidColor:
                if let hex = config.colorHex {
                    Color(hex: hex)
                } else {
                    AgentBuddyTheme.backgroundGradient
                }
            case .customImage:
                if let customImage {
                    Image(uiImage: customImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } else {
                    AgentBuddyTheme.backgroundGradient
                }
            case .customVideo, .videoUrl:
                if let videoFileURL, FileManager.default.fileExists(atPath: videoFileURL.path) {
                    VideoWallpaperPlayerView(fileURL: videoFileURL)
                } else {
                    AgentBuddyTheme.backgroundGradient
                }
            case .none:
                AgentBuddyTheme.backgroundGradient
            }
        } else {
            ChatWallpaperBackground(threadKey: threadKey)
        }
    }

    // MARK: - Sample Bubbles

    private var sampleBubbles: some View {
        VStack(spacing: BuddySpacing.sm) {
            Spacer()
            // User bubble
            HStack {
                Spacer(minLength: BuddySpacing.xxxl)
                WallpaperSampleUserBubble(text: "Fix the login bug on the profile page")
            }
            .padding(.horizontal, BuddySpacing.md)

            // Streaming assistant reply
            HStack {
                StreamingEffectPreview(config: typingEffectConfig)
                    .id(typingEffectConfig)
                    .wallpaperSampleAssistantCard()
                Spacer(minLength: 0)
            }
            .padding(.horizontal, BuddySpacing.md)

            Spacer()
        }
    }

    // MARK: - Bottom Card

    private var bottomCard: some View {
        VStack(spacing: 0) {
            // Handle
            WallpaperPanelGrabber()
                .padding(.top, BuddySpacing.sm)
                .padding(.bottom, BuddySpacing.sm)

            // Tab picker
            Picker("", selection: $activeTab) {
                ForEach(WallpaperTab.allCases) { tab in
                    Text(tab.label).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, BuddySpacing.md)
            .padding(.bottom, BuddySpacing.sm)

            // Tab content
            switch activeTab {
            case .background:
                backgroundTabContent
            case .typingEffect:
                typingEffectTabContent
            }
        }
        .wallpaperBottomPanel()
    }
}

// MARK: - Tab Enum

private enum WallpaperTab: String, CaseIterable, Identifiable {
    case background
    case typingEffect

    var id: String { rawValue }

    var label: String {
        switch self {
        case .background: "Background"
        case .typingEffect: "Typing Effect"
        }
    }
}
