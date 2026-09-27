import SwiftUI
import Hairball
import HairballUI
import Nuke
import NukeUI
import UIKit

struct AssistantBubble: View, Equatable {
    let markdownString: String
    let markdownIdentity: Int
    var label: String? = nil
    var compact: Bool = false
    var themeVersion: Int = 0
    var allowsInlineSelection: Bool = true
    private let contentFontSize = AgentBuddyFont.mintBodyPointSize
    private let codeFontSize = AgentBuddyFont.mintCodePointSize

    init(
        text: String,
        label: String? = nil,
        compact: Bool = false,
        themeVersion: Int = 0,
        allowsInlineSelection: Bool = true
    ) {
        self.markdownString = text
        self.markdownIdentity = text.hashValue
        self.label = label
        self.compact = compact
        self.themeVersion = themeVersion
        self.allowsInlineSelection = allowsInlineSelection
    }

    init(
        markdownString: String,
        markdownIdentity: Int,
        label: String? = nil,
        compact: Bool = false,
        themeVersion: Int = 0,
        allowsInlineSelection: Bool = true
    ) {
        self.markdownString = markdownString
        self.markdownIdentity = markdownIdentity
        self.label = label
        self.compact = compact
        self.themeVersion = themeVersion
        self.allowsInlineSelection = allowsInlineSelection
    }

    static func == (lhs: AssistantBubble, rhs: AssistantBubble) -> Bool {
        lhs.markdownIdentity == rhs.markdownIdentity &&
        lhs.label == rhs.label &&
        lhs.compact == rhs.compact &&
        lhs.themeVersion == rhs.themeVersion &&
        lhs.allowsInlineSelection == rhs.allowsInlineSelection
    }

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            if allowsInlineSelection {
                InlineSelectableMarkdownMessage(
                    markdown: markdownString,
                    style: .content,
                    bodySize: contentFontSize,
                    codeSize: codeFontSize
                ) {
                    bubbleContent
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                bubbleContent
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Spacer(minLength: compact ? 8 : 20)
        }
    }

    private var bubbleContent: some View {
        VStack(alignment: .leading, spacing: compact ? BuddySpacing.xxs : BuddySpacing.xs) {
            if let label {
                Text(label)
                    .buddyText(.caption, weight: .medium)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)
            }
            AgentBuddyMarkdownView(
                markdown: markdownString,
                style: .content,
                bodySize: contentFontSize,
                codeSize: codeFontSize
            )
            .fixedSize(horizontal: false, vertical: true)
            .transaction { $0.animation = nil }
        }
    }
}

struct AssistantBlocksBubble: View {
    let segments: [MessageRenderCache.AssistantSegment]
    var label: String? = nil
    var compact: Bool = false
    private let contentFontSize = AgentBuddyFont.mintBodyPointSize
    private let codeFontSize = AgentBuddyFont.mintCodePointSize

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: compact ? BuddySpacing.xxs : BuddySpacing.sm) {
                if let label {
                    Text(label)
                        .buddyText(.caption, weight: .medium)
                        .foregroundStyle(AgentBuddyTheme.textSecondary)
                }

                ForEach(segments) { segment in
                    segmentView(segment)
                        .transition(.asymmetric(
                            insertion: .push(from: .top),
                            removal: .identity
                        ))
                }
            }
            .transaction { $0.animation = nil }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer(minLength: compact ? 8 : 20)
        }
    }

    @ViewBuilder
    private func segmentView(_ segment: MessageRenderCache.AssistantSegment) -> some View {
        switch segment.kind {
        case .markdown(let content, let identity):
            AgentBuddyMarkdownView(
                markdown: content,
                style: .content,
                bodySize: contentFontSize,
                codeSize: codeFontSize
            )
            .frame(maxWidth: .infinity, alignment: .leading)
            .id(identity)
        case .codeBlock(let language, let code, let identity):
            if isMathCodeBlock(language) {
                AgentBuddyMathBlockView(latex: code)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .id(identity)
            } else {
                CodeBlockView(
                    language: language ?? "",
                    code: code,
                    fontSize: codeFontSize
                )
                .id(identity)
            }
        case .image(let data, let cacheKey):
            LazyImage(
                request: ImageRequest(
                    id: cacheKey,
                    data: { data },
                    processors: [
                        ImageProcessors.Resize(
                            size: CGSize(width: 1200, height: 300),
                            unit: .points,
                            contentMode: .aspectFit
                        )
                    ]
                )
            ) { state in
                if let image = state.image {
                    if let ui = state.imageContainer?.image {
                        image
                            .resizable()
                            .scaledToFit()
                            .frame(maxHeight: 300)
                            .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
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
                            .frame(maxHeight: 300)
                            .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                    }
                }
            }
        }
    }

    private func isMathCodeBlock(_ language: String?) -> Bool {
        guard let language else { return false }
        return language.trimmingCharacters(in: .whitespacesAndNewlines)
            .caseInsensitiveCompare("math") == .orderedSame
    }
}

private struct AgentBuddyMathBlockView: View {
    let latex: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: true) {
            LatexBlockView(content: latex)
                .fixedSize(horizontal: true, vertical: false)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
