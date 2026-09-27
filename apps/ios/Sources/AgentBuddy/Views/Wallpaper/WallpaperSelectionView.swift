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
                    Button {
                        onClose?()
                    } label: {
                        Text("Close")
                            .agentBuddyFont(size: 15, weight: .medium)
                            .foregroundStyle(AgentBuddyTheme.textPrimary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .modifier(GlassRectModifier(cornerRadius: 10))
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
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
        VStack(spacing: 12) {
            Spacer()
            // User bubble
            HStack {
                Spacer()
                Text("Fix the login bug on the profile page")
                    .agentBuddyFont(size: 14)
                    .foregroundStyle(AgentBuddyTheme.textPrimary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .modifier(GlassRectModifier(cornerRadius: 14, tint: AgentBuddyTheme.accent.opacity(0.3)))
            }
            .padding(.horizontal, 16)

            // Streaming assistant bubble
            HStack {
                StreamingEffectPreview(config: typingEffectConfig)
                    .id(typingEffectConfig)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .modifier(GlassRectModifier(cornerRadius: 14))
                Spacer()
            }
            .padding(.horizontal, 16)

            Spacer()
        }
    }

    // MARK: - Bottom Card

    private var bottomCard: some View {
        VStack(spacing: 0) {
            // Handle
            RoundedRectangle(cornerRadius: 2)
                .fill(AgentBuddyTheme.textMuted.opacity(0.4))
                .frame(width: 36, height: 4)
                .padding(.top, 12)
                .padding(.bottom, 8)

            // Tab picker
            Picker("", selection: $activeTab) {
                ForEach(WallpaperTab.allCases) { tab in
                    Text(tab.label).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 12)

            // Tab content
            switch activeTab {
            case .background:
                backgroundTabContent
            case .typingEffect:
                typingEffectTabContent
            }
        }
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20)
                .fill(AgentBuddyTheme.surface.opacity(0.95))
        )
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
