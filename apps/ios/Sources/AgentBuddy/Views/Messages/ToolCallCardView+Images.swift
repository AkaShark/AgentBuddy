import Foundation

extension ToolCallCardView {
    var imageDescriptor: ToolCallImageDescriptor? {
        guard model.kind == .imageView else { return nil }

        for section in model.sections {
            switch section {
            case .kv(_, let entries):
                for entry in entries {
                    if let descriptor = imageDescriptor(from: entry.value) {
                        return descriptor
                    }
                }
            case .code(_, _, let content),
                 .json(_, let content),
                 .text(_, let content):
                if let descriptor = imageDescriptor(from: content) {
                    return descriptor
                }
            default:
                continue
            }
        }

        return nil
    }

    func sectionContainsInlineImagePayload(_ section: ToolCallSection) -> Bool {
        switch section {
        case .code(_, _, let content),
             .json(_, let content),
             .text(_, let content):
            return Self.inlineImageData(from: content) != nil
        default:
            return false
        }
    }

    private func imageDescriptor(from rawValue: String) -> ToolCallImageDescriptor? {
        if let data = Self.inlineImageData(from: rawValue) {
            return .inlineData(data)
        }
        if let path = Self.normalizedImagePath(from: rawValue) {
            return .filePath(path)
        }
        return nil
    }

    private static func normalizedImagePath(from rawValue: String) -> String? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if trimmed.hasPrefix("file://"),
           let url = URL(string: trimmed),
           url.isFileURL {
            return url.path(percentEncoded: false)
        }
        if trimmed.hasPrefix("/") || trimmed.hasPrefix("~/") || trimmed.hasPrefix("\\\\") {
            return trimmed
        }
        if trimmed.range(of: #"^[A-Za-z]:[\\/]"#, options: .regularExpression) != nil {
            return trimmed
        }

        return nil
    }

    private static func inlineImageData(from rawValue: String) -> Data? {
        guard let match = rawValue.range(
            of: #"data:image/[^;]+;base64,[A-Za-z0-9+/=\s]+"#,
            options: .regularExpression
        ) else {
            return nil
        }

        let source = String(rawValue[match]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard let commaIndex = source.firstIndex(of: ",") else { return nil }
        let base64 = String(source[source.index(after: commaIndex)...])
        return Data(base64Encoded: base64, options: .ignoreUnknownCharacters)
    }
}
