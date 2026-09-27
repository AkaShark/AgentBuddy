import SwiftUI
import UIKit

enum ToolCallImageDescriptor: Equatable {
    case inlineData(Data)
    case filePath(String)

    var cacheKey: String {
        switch self {
        case .inlineData(let data):
            return "inline-\(data.hashValue)"
        case .filePath(let path):
            return "path-\(path)"
        }
    }
}

struct ToolCallImagePreview: View {
    @Environment(AppModel.self) private var appModel

    let descriptor: ToolCallImageDescriptor
    let serverId: String?

    @State private var renderedImage: UIImage?
    @State private var isLoading = false
    @State private var loadError: String?

    private static let imageCache = NSCache<NSString, UIImage>()

    var body: some View {
        VStack(alignment: .leading, spacing: BuddySpacing.xs) {
            TimelineSectionLabel("Image")

            ZStack {
                RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous)
                    .fill(TimelineCodeStyle.fill(nested: true))

                if let renderedImage {
                    Image(uiImage: renderedImage)
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
                        .draggable(Image(uiImage: renderedImage)) {
                            Image(uiImage: renderedImage)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 120)
                        }
                } else if isLoading {
                    ProgressView()
                        .tint(AgentBuddyTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, BuddySpacing.xxl)
                } else {
                    Label {
                        Text(loadError ?? "Image unavailable")
                    } icon: {
                        Image(systemName: loadError == nil ? "photo" : "exclamationmark.triangle")
                            .accessibilityHidden(true)
                    }
                    .buddyText(.label, weight: .regular)
                    .foregroundStyle(loadError == nil ? AgentBuddyTheme.textSecondary : AgentBuddyTheme.danger)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.horizontal, BuddySpacing.sm)
                    .padding(.vertical, BuddySpacing.xl)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: BuddyRadius.control, style: .continuous))
        }
        .task(id: taskKey) {
            await loadImage()
        }
    }

    private var taskKey: String {
        "\(descriptor.cacheKey)|\(serverId ?? "<none>")"
    }

    private func loadImage() async {
        if let cached = Self.imageCache.object(forKey: taskKey as NSString) {
            renderedImage = cached
            loadError = nil
            isLoading = false
            return
        }

        isLoading = true
        loadError = nil

        defer {
            isLoading = false
        }

        do {
            let image: UIImage
            switch descriptor {
            case .inlineData(let data):
                guard let decoded = UIImage(data: data) else {
                    throw ToolCallImageError.invalidImageData
                }
                image = decoded
            case .filePath(let path):
                let data = try await fetchImageData(path: path)
                guard let decoded = UIImage(data: data) else {
                    throw ToolCallImageError.invalidImageData
                }
                image = decoded
            }

            Self.imageCache.setObject(image, forKey: taskKey as NSString)
            renderedImage = image
            loadError = nil
        } catch {
            renderedImage = nil
            loadError = ToolCallImageError.message(for: error)
        }
    }

    private func fetchImageData(path: String) async throws -> Data {
        let resolved = try await appModel.client.resolveImageView(
            serverId: serverId ?? "",
            path: path
        )
        return Data(resolved.bytes)
    }
}

private enum ToolCallImageError: LocalizedError {
    case invalidImageData

    var errorDescription: String? {
        switch self {
        case .invalidImageData:
            return "Could not decode the image."
        }
    }

    static func message(for error: Error) -> String {
        let message = error.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        return message.isEmpty ? "Image unavailable" : message
    }
}
