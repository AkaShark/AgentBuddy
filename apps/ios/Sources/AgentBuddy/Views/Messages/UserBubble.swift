import SwiftUI
import Nuke
import NukeUI
import UIKit

struct UserBubble: View {
    let text: String
    var images: [ChatImage] = []
    var compact: Bool = false
    var maxVisibleCharacters: Int = 1_000
    @State private var expandedLongText = false
    private let contentFontSize = AgentBuddyFont.conversationBodyPointSize

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Spacer(minLength: compact ? 30 : 60)
            VStack(alignment: .trailing, spacing: compact ? 4 : 8) {
                ForEach(images) { img in
                    if let request = UserBubble.imageRequest(for: img) {
                        LazyImage(request: request) { state in
                            if let image = state.image {
                                if let ui = state.imageContainer?.image {
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .frame(maxWidth: 200, maxHeight: 200)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                        .draggable(Image(uiImage: ui)) {
                                            Image(uiImage: ui)
                                                .resizable()
                                                .scaledToFit()
                                                .frame(width: 120)
                                        }
                                } else {
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .frame(maxWidth: 200, maxHeight: 200)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                    }
                }
                if !text.isEmpty {
                    VStack(alignment: .trailing, spacing: 4) {
                        FormattedText(text: visibleText)
                            .agentBuddyFont(size: contentFontSize)
                            .lineSpacing(contentFontSize * 0.42)
                            .foregroundColor(AgentBuddyTheme.textPrimary)
                            .textSelection(.enabled)

                        if shouldLimitText {
                            Button {
                                withAnimation(.easeInOut(duration: 0.18)) {
                                    expandedLongText.toggle()
                                }
                            } label: {
                                Text(expandedLongText ? "Show less" : "Show more")
                                    .agentBuddyFont(.caption, weight: .semibold)
                                    .foregroundColor(AgentBuddyTheme.link)
                                    .frame(minHeight: 32)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(expandedLongText ? "Show less user message" : "Show more user message")
                        }
                    }
                }
            }
            .padding(.horizontal, compact ? 12 : BuddySpacing.md)
            .padding(.vertical, compact ? 8 : BuddySpacing.sm)
            // Mint: surfaceSoft fill, 19/19/5/19 corners pointing at the sender.
            .background(AgentBuddyTheme.surfaceSoft, in: BuddyUserBubbleShape())
        }
        .padding(.bottom, 14)
        .onChange(of: text) { _, _ in
            expandedLongText = false
        }
    }

    private var visibleText: String {
        guard shouldLimitText, !expandedLongText else {
            return text
        }
        return String(text.prefix(maxVisibleCharacters))
    }

    private var shouldLimitText: Bool {
        text.count > maxVisibleCharacters
    }

    fileprivate static func imageRequest(for image: ChatImage) -> ImageRequest? {
        let source = image.source
        guard source.hasPrefix("data:") || source.hasPrefix("file://") else {
            return nil
        }
        let cacheKey = image.cacheKey
        let processors: [any ImageProcessing] = [
            ImageProcessors.Resize(
                size: CGSize(width: 200, height: 200),
                unit: .points,
                contentMode: .aspectFit
            )
        ]
        return ImageRequest(
            id: cacheKey,
            data: { @Sendable in
                guard let data = imageData(forSource: source) else {
                    throw URLError(.fileDoesNotExist)
                }
                return data
            },
            processors: processors
        )
    }

    private static func imageData(forSource source: String) -> Data? {
        if source.hasPrefix("file://") {
            let path = String(source.dropFirst("file://".count))
            return FileManager.default.contents(atPath: path)
        }
        guard let commaIndex = source.firstIndex(of: ",") else { return nil }
        let base64 = String(source[source.index(after: commaIndex)...])
        return Data(base64Encoded: base64, options: .ignoreUnknownCharacters)
    }
}
