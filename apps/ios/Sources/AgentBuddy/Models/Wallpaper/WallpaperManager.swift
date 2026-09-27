import SwiftUI
import Observation
import UIKit
import HairballUI

// MARK: - WallpaperManager

@MainActor
@Observable
final class WallpaperManager {
    @MainActor static let shared = WallpaperManager()

    var activeThreadKey: ThreadKey?
    private(set) var resolvedWallpaperImage: UIImage?
    private(set) var resolvedConfig: WallpaperConfig?
    private(set) var version: Int = 0

    @ObservationIgnored
    var prefs = WallpaperPrefsFile()

    @ObservationIgnored
    var imageCache: [String: UIImage] = [:]

    @ObservationIgnored
    private static let prefsFileName = "wallpaper_prefs.json"

    @ObservationIgnored
    static var prefsFileURL: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return dir.appendingPathComponent(prefsFileName)
    }

    @ObservationIgnored
    private static var documentsDir: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    // Legacy compat — some views still check this
    var wallpaperImage: UIImage? { resolvedWallpaperImage }
    var isWallpaperSet: Bool { resolvedConfig != nil && resolvedConfig?.type != .none }

    private init() {
        loadPrefs()
    }

    // MARK: - Public API

    func resolveConfig(for threadKey: ThreadKey?) -> WallpaperConfig? {
        guard let key = threadKey else { return nil }

        // Thread-specific
        let threadScopeKey = scopeKey(for: .thread(key))
        if let cfg = prefs.threads[threadScopeKey], cfg.type != .none {
            return cfg
        }

        // Server default
        let serverScopeKey = key.serverId
        if let cfg = prefs.servers[serverScopeKey], cfg.type != .none {
            return cfg
        }

        return nil
    }

    func resolveScope(for threadKey: ThreadKey?) -> WallpaperScope? {
        guard let key = threadKey else { return nil }

        let threadScopeKey = scopeKey(for: .thread(key))
        if let cfg = prefs.threads[threadScopeKey], cfg.type != .none {
            return .thread(key)
        }

        if let cfg = prefs.servers[key.serverId], cfg.type != .none {
            return .server(key.serverId)
        }

        return nil
    }

    func resolveConfigForServer(_ serverId: String) -> WallpaperConfig? {
        if let cfg = prefs.servers[serverId], cfg.type != .none {
            return cfg
        }
        return nil
    }

    func setWallpaper(_ config: WallpaperConfig, scope: WallpaperScope) {
        switch scope {
        case .thread(let key):
            let k = scopeKey(for: .thread(key))
            if config.type == .none {
                prefs.threads.removeValue(forKey: k)
            } else {
                prefs.threads[k] = config
            }
        case .server(let serverId):
            if config.type == .none {
                prefs.servers.removeValue(forKey: serverId)
            } else {
                prefs.servers[serverId] = config
            }
        }
        savePrefs()
        refreshResolved()
    }

    func setCustomImage(_ image: UIImage, scope: WallpaperScope) {
        guard let data = image.jpegData(compressionQuality: 0.85) else { return }

        let fileName: String
        switch scope {
        case .thread(let key):
            fileName = "wallpaper_thread_\(key.serverId)_\(key.threadId).jpg"
        case .server(let serverId):
            fileName = "wallpaper_server_\(serverId).jpg"
        }

        let fileURL = Self.documentsDir.appendingPathComponent(fileName)
        try? data.write(to: fileURL, options: .atomic)

        var config = WallpaperConfig(type: .customImage)
        config.blur = 0.0
        config.brightness = 1.0
        setWallpaper(config, scope: scope)
    }

    // MARK: - Typing Effect

    func resolveTypingEffect(for threadKey: ThreadKey?) -> TypingEffectConfig {
        guard let key = threadKey else { return .default }

        let threadScopeKey = scopeKey(for: .thread(key))
        if let cfg = prefs.typingEffectThreads[threadScopeKey] {
            return cfg
        }

        if let cfg = prefs.typingEffectServers[key.serverId] {
            return cfg
        }

        return .default
    }

    func resolveTypingEffectForServer(_ serverId: String) -> TypingEffectConfig {
        prefs.typingEffectServers[serverId] ?? .default
    }

    func setTypingEffect(_ config: TypingEffectConfig, scope: WallpaperScope) {
        switch scope {
        case .thread(let key):
            let k = scopeKey(for: .thread(key))
            prefs.typingEffectThreads[k] = config
        case .server(let serverId):
            prefs.typingEffectServers[serverId] = config
        }
        savePrefs()
        version += 1
    }

    func setActiveThreadKey(_ key: ThreadKey?) {
        guard activeThreadKey != key else { return }
        activeThreadKey = key
        refreshResolved()
    }

    func cleanup(knownServerIds: Set<String>, knownThreadKeys: Set<String>) {
        var changed = false

        for key in prefs.threads.keys {
            if !knownThreadKeys.contains(key) {
                prefs.threads.removeValue(forKey: key)
                // Remove orphaned image and video files
                let parts = key.split(separator: ":")
                if parts.count == 2 {
                    for ext in ["jpg", "mp4"] {
                        let fileName = "wallpaper_thread_\(parts[0])_\(parts[1]).\(ext)"
                        let fileURL = Self.documentsDir.appendingPathComponent(fileName)
                        try? FileManager.default.removeItem(at: fileURL)
                    }
                }
                changed = true
            }
        }

        for serverId in prefs.servers.keys {
            if !knownServerIds.contains(serverId) {
                prefs.servers.removeValue(forKey: serverId)
                for ext in ["jpg", "mp4"] {
                    let fileName = "wallpaper_server_\(serverId).\(ext)"
                    let fileURL = Self.documentsDir.appendingPathComponent(fileName)
                    try? FileManager.default.removeItem(at: fileURL)
                }
                changed = true
            }
        }

        for key in prefs.typingEffectThreads.keys where !knownThreadKeys.contains(key) {
            prefs.typingEffectThreads.removeValue(forKey: key)
            changed = true
        }
        for serverId in prefs.typingEffectServers.keys where !knownServerIds.contains(serverId) {
            prefs.typingEffectServers.removeValue(forKey: serverId)
            changed = true
        }

        if changed {
            savePrefs()
        }
    }

    // MARK: - Private Helpers

    private func scopeKey(for scope: WallpaperScope) -> String {
        switch scope {
        case .thread(let key):
            return "\(key.serverId)::\(key.threadId)"
        case .server(let serverId):
            return serverId
        }
    }

    private func refreshResolved() {
        version += 1
        resolvedConfig = resolveConfig(for: activeThreadKey)
        // Image resolution is deferred to view layer which has themeManager access
        if resolvedConfig == nil || resolvedConfig?.type == .none {
            resolvedWallpaperImage = nil
        }
    }

    func updateResolvedImage(_ image: UIImage?) {
        resolvedWallpaperImage = image
    }

    func videoFileURL(for scope: WallpaperScope) -> URL {
        let fileName: String
        switch scope {
        case .thread(let key):
            fileName = "wallpaper_thread_\(key.serverId)_\(key.threadId).mp4"
        case .server(let serverId):
            fileName = "wallpaper_server_\(serverId).mp4"
        }
        return Self.documentsDir.appendingPathComponent(fileName)
    }

    func loadCustomImage(for scope: WallpaperScope?) -> UIImage? {
        guard let scope = scope else { return nil }
        let fileName: String
        switch scope {
        case .thread(let key):
            fileName = "wallpaper_thread_\(key.serverId)_\(key.threadId).jpg"
        case .server(let serverId):
            fileName = "wallpaper_server_\(serverId).jpg"
        }
        let fileURL = Self.documentsDir.appendingPathComponent(fileName)
        return UIImage(contentsOfFile: fileURL.path)
    }
}
