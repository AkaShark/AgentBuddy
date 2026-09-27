import SwiftUI

// MARK: - Active Thread Key Environment

private struct ActiveThreadKeyKey: EnvironmentKey {
    static let defaultValue: ThreadKey? = nil
}

extension EnvironmentValues {
    var activeThreadKey: ThreadKey? {
        get { self[ActiveThreadKeyKey.self] }
        set { self[ActiveThreadKeyKey.self] = newValue }
    }
}

extension View {
    func activeThreadKey(_ key: ThreadKey?) -> some View {
        environment(\.activeThreadKey, key)
    }
}
