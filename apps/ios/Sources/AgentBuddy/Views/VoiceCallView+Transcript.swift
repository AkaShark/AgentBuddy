import SwiftUI
import UIKit

struct VoiceCreditsTranscriptView: View {
    let items: [ConversationItem]
    let threadStatus: ConversationStatus
    let session: VoiceSessionState
    let textScale: CGFloat

    private var entries: [VoiceTranscriptEntry] {
        var result = items.compactMap(VoiceTranscriptEntry.init)
        if let liveEntry = VoiceTranscriptEntry.live(from: session),
           result.last != liveEntry {
            result.append(liveEntry)
        }
        return result
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    VStack(spacing: 26) {
                        if entries.isEmpty {
                            placeholder
                        } else {
                            ForEach(entries) { entry in
                                VoiceCreditsEntryRow(entry: entry, textScale: textScale)
                            }
                        }

                        if case .thinking = threadStatus {
                            Text("...")
                                .font(AgentBuddyFont.monospaced(.title3, weight: .semibold))
                                .foregroundColor(AgentBuddyTheme.textMuted)
                        }
                    }
                    .padding(.horizontal, 26)
                    .padding(.top, 36)
                    .padding(.bottom, 150)

                    Color.clear
                        .frame(height: 1)
                        .id("bottom")
                }
                .frame(maxWidth: .infinity)
            }
            .mask {
                VStack(spacing: 0) {
                    LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom)
                        .frame(height: 52)
                    Rectangle().fill(.black)
                    LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                        .frame(height: 68)
                }
                .ignoresSafeArea()
            }
            .onAppear {
                scrollToBottom(proxy, animated: false)
            }
            .onChange(of: entries) { _, _ in
                scrollToBottom(proxy, animated: true)
            }
        }
    }

    private var placeholder: some View {
        VStack(spacing: 10) {
            Text("Voice Transcript")
                .font(AgentBuddyFont.monospaced(.caption, weight: .semibold))
                .foregroundColor(AgentBuddyTheme.textMuted)
            Text("The live conversation, tool calls, and other text output will appear here.")
                .font(AgentBuddyFont.styled(.body, scale: textScale))
                .foregroundColor(AgentBuddyTheme.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 320)
        }
        .frame(maxWidth: .infinity, minHeight: 280)
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy, animated: Bool) {
        DispatchQueue.main.async {
            if animated {
                withAnimation(.easeOut(duration: 0.22)) {
                    proxy.scrollTo("bottom", anchor: .bottom)
                }
            } else {
                proxy.scrollTo("bottom", anchor: .bottom)
            }
        }
    }
}

private struct VoiceCreditsEntryRow: View {
    let entry: VoiceTranscriptEntry
    let textScale: CGFloat

    private var titleColor: Color {
        switch entry.kind {
        case .user, .liveUser:
            return AgentBuddyTheme.accent
        case .assistant, .liveAssistant:
            return AgentBuddyTheme.warning
        case .reasoning:
            return AgentBuddyTheme.textMuted
        case .tool, .note, .system:
            return AgentBuddyTheme.textSecondary
        case .error:
            return AgentBuddyTheme.danger
        }
    }

    private var bodyColor: Color {
        switch entry.kind {
        case .error:
            return AgentBuddyTheme.danger.opacity(0.92)
        case .reasoning:
            return AgentBuddyTheme.textSecondary
        default:
            return AgentBuddyTheme.textPrimary
        }
    }

    private var maxContentWidth: CGFloat {
        entry.media == nil ? 520 : 760
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(entry.title)
                .font(AgentBuddyFont.monospaced(.caption, weight: .bold, scale: textScale))
                .foregroundColor(titleColor)

            if !entry.body.isEmpty {
                Group {
                    if entry.kind == .reasoning {
                        Text(verbatim: entry.body)
                            .italic()
                    } else {
                        Text(verbatim: entry.body)
                    }
                }
                .font(AgentBuddyFont.styled(.body, scale: textScale))
                .foregroundColor(bodyColor)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            if let media = entry.media {
                VoiceTranscriptMediaView(media: media)
            }
        }
        .frame(maxWidth: maxContentWidth, alignment: .leading)
        .frame(maxWidth: .infinity)
        .opacity(entry.kind == .liveAssistant || entry.kind == .liveUser ? 0.94 : 1)
    }
}

private struct VoiceTranscriptMediaView: View {
    let media: VoiceTranscriptMedia

    @ViewBuilder
    var body: some View {
        switch media {
        case .userImages(let images):
            VoiceTranscriptUserImagesView(images: images)
        case .computerUse(let data, let view):
            ComputerUseToolCallView(data: data, view: view, externalExpanded: true)
        case .imageGeneration(let data):
            ImageGenerationToolCallView(data: data, externalExpanded: true)
        }
    }
}

private struct VoiceTranscriptUserImagesView: View {
    let images: [ChatImage]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(images) { image in
                if let uiImage = decodeImage(image) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(AgentBuddyTheme.border.opacity(0.4), lineWidth: 0.5)
                        )
                        .draggable(Image(uiImage: uiImage)) {
                            Image(uiImage: uiImage)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 120)
                        }
                }
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(AgentBuddyTheme.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(AgentBuddyTheme.border, lineWidth: 0.5)
        )
    }

    private func decodeImage(_ image: ChatImage) -> UIImage? {
        guard let data = imageData(for: image) else { return nil }
        return UIImage(data: data)
    }

    private func imageData(for image: ChatImage) -> Data? {
        let source = image.source
        guard source.hasPrefix("data:") || source.hasPrefix("file://") else {
            return nil
        }

        if source.hasPrefix("file://") {
            let path = String(source.dropFirst("file://".count))
            return FileManager.default.contents(atPath: path)
        }

        guard let commaIndex = source.firstIndex(of: ",") else { return nil }
        let base64 = String(source[source.index(after: commaIndex)...])
        return Data(base64Encoded: base64, options: .ignoreUnknownCharacters)
    }
}
