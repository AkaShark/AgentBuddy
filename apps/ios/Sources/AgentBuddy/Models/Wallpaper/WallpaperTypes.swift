import Foundation
import HairballUI

// MARK: - Types

enum WallpaperType: String, Codable {
    case none
    case theme
    case customImage = "custom_image"
    case solidColor = "solid_color"
    case customVideo = "custom_video"
    case videoUrl = "video_url"
}

enum WallpaperScope: Equatable {
    case thread(ThreadKey)
    case server(String)
}

enum PatternType: Int, CaseIterable {
    case dotGrid
    case diagonalLines
    case concentricCircles
    case hexagonalMesh
    case crossHatch
    case waveLines
}

struct WallpaperConfig: Codable, Equatable {
    var type: WallpaperType = .none
    var themeSlug: String?
    var colorHex: String?
    var blur: Double = 0.0
    var brightness: Double = 1.0
    var motionEnabled: Bool = false
    var videoURL: String?
    var videoDuration: Double?
}

enum GranularityKind: String, CaseIterable, Identifiable {
    case character = "Character"
    case chunk8 = "Chunk (8)"
    case chunk32 = "Chunk (32)"
    case line = "Line"
    case block = "Block"

    var id: String { rawValue }

    var shortLabel: String {
        switch self {
        case .character: return "Char"
        case .chunk8: return "8"
        case .chunk32: return "32"
        case .line: return "Line"
        case .block: return "Block"
        }
    }

    var granularity: RevealGranularity {
        switch self {
        case .character: return .character
        case .chunk8: return .chunk(8)
        case .chunk32: return .chunk(32)
        case .line: return .line
        case .block: return .block
        }
    }
}

struct TypingEffectConfig: Codable, Equatable, Hashable {
    var effects: [String] = []
    var revealDuration: Double = 0.5
    var granularity: String = "Character"
    var revealMode: String = "Continuous"

    static let `default` = TypingEffectConfig(effects: ["Fade Edge"], revealDuration: 0.5, granularity: "Character", revealMode: "Continuous")

    var activeEffect: StreamingEffectKind? {
        effects.first.flatMap { StreamingEffectKind(rawValue: $0) }
    }

    var resolvedEffect: (any StreamingTextEffect)? {
        activeEffect?.effect
    }

    var effectiveRevealMode: TokenRevealMode {
        revealMode == "Continuous" ? .continuous : .linear
    }

    var effectiveGranularity: RevealGranularity {
        (GranularityKind(rawValue: granularity) ?? .block).granularity
    }
}
