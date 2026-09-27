import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

private struct VideoTransferable: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { video in
            SentTransferredFile(video.url)
        } importing: { received in
            let tempDir = FileManager.default.temporaryDirectory
            let destURL = tempDir.appendingPathComponent(UUID().uuidString + ".mov")
            try FileManager.default.copyItem(at: received.file, to: destURL)
            return Self(url: destURL)
        }
    }
}

extension WallpaperSelectionView {
    // MARK: - Helpers

    func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        await MainActor.run {
            customImage = image
            selectedThemeSlug = nil
            selectedColor = nil
            let config = WallpaperConfig(type: .customImage)
            previewConfig = config
            if let threadKey {
                wallpaperManager.setCustomImage(image, scope: .thread(threadKey))
            } else if let resolvedServerId {
                wallpaperManager.setCustomImage(image, scope: .server(resolvedServerId))
            }
            onSelectWallpaper?(config, image)
        }
    }

    func loadVideo(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        await MainActor.run { isProcessingVideo = true }
        defer { Task { @MainActor in isProcessingVideo = false } }

        // Load the video data as a transferable file URL
        guard let movie = try? await item.loadTransferable(type: VideoTransferable.self) else {
            LLog.error("wallpaper", "failed to load video from picker")
            return
        }

        let scope: WallpaperScope
        if let threadKey {
            scope = .thread(threadKey)
        } else if let resolvedServerId {
            scope = .server(resolvedServerId)
        } else {
            return
        }
        let destURL = wallpaperManager.videoFileURL(for: scope)

        do {
            let duration = try await VideoWallpaperProcessor.transcode(source: movie.url, destination: destURL)
            await MainActor.run {
                var config = WallpaperConfig(type: .customVideo)
                config.videoDuration = duration
                previewConfig = config
                videoFileURL = destURL
                selectedThemeSlug = nil
                selectedColor = nil
                customImage = nil
                wallpaperManager.setWallpaper(config, scope: scope)
                onSelectWallpaper?(config, nil)
            }
        } catch {
            LLog.error("wallpaper", "video transcode failed", error: error)
            await MainActor.run { videoErrorMessage = error.localizedDescription }
        }
    }

    func loadVideoFromURL() async {
        let trimmed = videoURLText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let remoteURL = URL(string: trimmed), remoteURL.scheme == "http" || remoteURL.scheme == "https" else {
            return
        }

        await MainActor.run { isProcessingVideo = true }
        defer { Task { @MainActor in isProcessingVideo = false } }

        let scope: WallpaperScope
        if let threadKey {
            scope = .thread(threadKey)
        } else if let resolvedServerId {
            scope = .server(resolvedServerId)
        } else {
            return
        }
        let destURL = wallpaperManager.videoFileURL(for: scope)

        do {
            let duration = try await VideoWallpaperProcessor.downloadAndTranscode(remoteURL: remoteURL, destination: destURL)
            await MainActor.run {
                var config = WallpaperConfig(type: .videoUrl, videoURL: trimmed)
                config.videoDuration = duration
                previewConfig = config
                videoFileURL = destURL
                selectedThemeSlug = nil
                selectedColor = nil
                customImage = nil
                videoURLText = ""
                wallpaperManager.setWallpaper(config, scope: scope)
                onSelectWallpaper?(config, nil)
            }
        } catch {
            LLog.error("wallpaper", "video URL download/transcode failed", error: error)
            await MainActor.run { videoErrorMessage = error.localizedDescription }
        }
    }
}
