import Foundation

// MARK: - JSON Storage

struct WallpaperPrefsFile: Codable {
    var threads: [String: WallpaperConfig] = [:]
    var servers: [String: WallpaperConfig] = [:]
    var typingEffectThreads: [String: TypingEffectConfig] = [:]
    var typingEffectServers: [String: TypingEffectConfig] = [:]
}

extension WallpaperManager {
    // MARK: - JSON Persistence

    func loadPrefs() {
        let url = Self.prefsFileURL
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let data = try Data(contentsOf: url)
            prefs = try JSONDecoder().decode(WallpaperPrefsFile.self, from: data)
        } catch {
            LLog.error("wallpaper", "failed to load wallpaper prefs", error: error)
        }
    }

    func savePrefs() {
        do {
            let data = try JSONEncoder().encode(prefs)
            try data.write(to: Self.prefsFileURL, options: .atomic)
        } catch {
            LLog.error("wallpaper", "failed to save wallpaper prefs", error: error)
        }
    }
}
