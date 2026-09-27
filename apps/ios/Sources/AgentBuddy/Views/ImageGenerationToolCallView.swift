import Nuke
import NukeUI
import SwiftUI
import UIKit

struct ImageGenerationToolCallView: View {
    let data: ConversationImageGenerationData
    private let externalExpanded: Bool?
    @State private var expanded: Bool
    @State private var promptExpanded = false
    @State private var showShareSheet = false

    init(
        data: ConversationImageGenerationData,
        externalExpanded: Bool? = nil
    ) {
        self.data = data
        self.externalExpanded = externalExpanded
        _expanded = State(initialValue: externalExpanded ?? true)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            if expanded {
                VStack(alignment: .leading, spacing: BuddySpacing.sm) {
                    imagePreview
                    if let prompt = data.revisedPrompt, !prompt.isEmpty {
                        promptBlock(prompt)
                    }
                }
                .padding(.top, BuddySpacing.xxs)
                .padding(.bottom, BuddySpacing.sm)
                .transition(.sectionReveal)
            }
        }
        .padding(.horizontal, BuddySpacing.md)
        .padding(.vertical, BuddySpacing.xxs)
        .timelineDetailCard()
        .animation(.spring(duration: 0.32, bounce: 0.12), value: expanded)
        .onChange(of: externalExpanded) { _, newValue in
            if let newValue, newValue != expanded {
                withAnimation(.spring(duration: 0.35, bounce: 0.15)) {
                    expanded = newValue
                }
            }
        }
    }

    private var header: some View {
        HStack(spacing: BuddySpacing.sm) {
            TimelineStatusGlyph(status: data.status.toolCallStatus, fallbackSystemImage: "sparkles")

            Text(verbatim: summary)
                .buddyText(.label)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer(minLength: BuddySpacing.xs)

            TimelineDisclosureChevron(expanded: expanded)
        }
        .frame(minHeight: BuddySize.minHitTarget)
        .contentShape(Rectangle())
        .accessibilityAddTraits(.isButton)
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                expanded.toggle()
            }
        }
    }

    private var summary: String {
        switch data.status {
        case .completed: return String(localized: "Generated image")
        case .failed: return String(localized: "Image generation failed")
        default: return String(localized: "Generating image…")
        }
    }

    @ViewBuilder
    private var imagePreview: some View {
        if let bytes = data.imagePNG {
            let cacheKey = "image-gen-\(bytes.count)-\(bytes.hashValue)"
            LazyImage(
                request: ImageRequest(
                    id: cacheKey,
                    data: { bytes },
                    processors: [
                        ImageProcessors.Resize(
                            size: CGSize(width: 1200, height: 1200),
                            unit: .points,
                            contentMode: .aspectFit
                        )
                    ]
                )
            ) { state in
                if let image = state.image, let ui = state.imageContainer?.image {
                    image
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                                .strokeBorder(AgentBuddyTheme.border, lineWidth: 1)
                        )
                        .draggable(Image(uiImage: ui)) {
                            Image(uiImage: ui)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 120)
                        }
                        .contextMenu {
                            Button {
                                UIPasteboard.general.image = ui
                            } label: {
                                Label("Copy Image", systemImage: "doc.on.doc")
                            }
                            Button {
                                showShareSheet = true
                            } label: {
                                Label("Share…", systemImage: "square.and.arrow.up")
                            }
                        }
                        .sheet(isPresented: $showShareSheet) {
                            ShareSheet(items: [ui])
                        }
                }
            }
        } else if data.isInProgress {
            ImageGenerationLoadingTile()
        } else if data.status == .failed {
            placeholderTile(icon: "exclamationmark.triangle.fill", message: "Image unavailable", tone: AgentBuddyTheme.danger)
        } else {
            placeholderTile(icon: "photo", message: "Image unavailable", tone: AgentBuddyTheme.textSecondary)
        }
    }

    private func promptBlock(_ prompt: String) -> some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xxs) {
            HStack(spacing: BuddySpacing.xs) {
                TimelineSectionLabel("Revised prompt")
                Spacer()
                if shouldShowPromptToggle(prompt) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            promptExpanded.toggle()
                        }
                    } label: {
                        Text(promptExpanded ? "Show less" : "Show more")
                            .timelineLinkAction()
                    }
                    .buttonStyle(.plain)
                }
            }

            Text(promptExpanded ? prompt : collapsedPreview(prompt))
                .buddyText(.label, weight: .regular)
                .foregroundStyle(AgentBuddyTheme.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(BuddySpacing.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .timelineCodeSurface()
        }
    }

    private func placeholderTile(icon: String, message: LocalizedStringKey, tone: Color) -> some View {
        VStack(spacing: BuddySpacing.xs) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(tone)
                .accessibilityHidden(true)
            Text(message)
                .buddyText(.label, weight: .regular)
                .foregroundStyle(tone)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.vertical, BuddySpacing.xxl)
        .timelineCodeSurface()
    }

    private func shouldShowPromptToggle(_ prompt: String) -> Bool {
        prompt.count > 220 || prompt.split(separator: "\n", omittingEmptySubsequences: false).count > 4
    }

    private func collapsedPreview(_ text: String) -> String {
        let limit = 220
        if text.count <= limit { return text }
        let head = String(text.prefix(limit)).trimmingCharacters(in: .whitespaces)
        return head + "…"
    }
}

private struct ImageGenerationLoadingTile: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        VStack(spacing: BuddySpacing.sm) {
            ZStack {
                RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                    .fill(AgentBuddyTheme.brand)
                    .frame(width: 48, height: 48)
                    .opacity(pulse ? 1 : 0.7)

                Image(systemName: "sparkles")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(AgentBuddyTheme.onBrand)
                    .scaleEffect(pulse ? 1.06 : 0.96)
            }
            .accessibilityHidden(true)

            VStack(spacing: BuddySpacing.xs) {
                Text("Generating image")
                    .buddyText(.label)
                    .foregroundStyle(AgentBuddyTheme.textSecondary)

                HStack(spacing: 5) {
                    ForEach(0..<3, id: \.self) { index in
                        Capsule()
                            .fill(AgentBuddyTheme.borderControl)
                            .frame(width: index == 1 ? 42 : 28, height: 4)
                            .opacity(pulse ? 1.0 - Double(index) * 0.18 : 0.38 + Double(index) * 0.16)
                    }
                }
                .frame(height: 8)
                .accessibilityHidden(true)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, BuddySpacing.xl)
        .timelineCodeSurface()
        .task {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
