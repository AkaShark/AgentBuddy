import SwiftUI

extension ConversationInputBar {
    func loadExperimentalFeatures() async {
        guard appModel.snapshot?.servers.first(where: { $0.serverId == snapshot.threadKey.serverId })?.canUseTransportActions == true else {
            experimentalFeatures = []
            slashErrorMessage = "Not connected to a server"
            return
        }
        experimentalFeaturesLoading = true
        defer { experimentalFeaturesLoading = false }
        do {
            let features = try await appModel.client.listExperimentalFeatures(
                serverId: snapshot.threadKey.serverId,
                params: AppListExperimentalFeaturesRequest(cursor: nil, limit: 200)
            )
            experimentalFeatures = features.sorted { lhs, rhs in
                let left = (lhs.displayName?.isEmpty == false ? lhs.displayName! : lhs.name).lowercased()
                let right = (rhs.displayName?.isEmpty == false ? rhs.displayName! : rhs.name).lowercased()
                return left < right
            }
        } catch {
            slashErrorMessage = error.localizedDescription
        }
    }

    func isExperimentalFeatureEnabled(_ featureId: String, fallback: Bool) -> Bool {
        experimentalFeatures.first(where: { $0.id == featureId })?.enabled ?? fallback
    }

    func setExperimentalFeature(named featureName: String, enabled: Bool) async {
        guard appModel.snapshot?.servers.first(where: { $0.serverId == snapshot.threadKey.serverId })?.canUseTransportActions == true else {
            slashErrorMessage = "Not connected to a server"
            return
        }
        guard let currentIndex = experimentalFeatures.firstIndex(where: { $0.name == featureName }) else {
            return
        }
        let currentFeature = experimentalFeatures[currentIndex]
        if currentFeature.enabled != enabled {
            experimentalFeatures[currentIndex] = ExperimentalFeature(
                name: currentFeature.name,
                stage: currentFeature.stage,
                displayName: currentFeature.displayName,
                description: currentFeature.description,
                announcement: currentFeature.announcement,
                enabled: enabled,
                defaultEnabled: currentFeature.defaultEnabled
            )
        }
        do {
            _ = try await appModel.client.writeConfigValue(
                serverId: snapshot.threadKey.serverId,
                params: AppWriteConfigValueRequest(
                    keyPath: "features.\(featureName)",
                    valueJson: enabled ? "true" : "false",
                    mergeStrategy: .upsert,
                    filePath: nil,
                    expectedVersion: nil
                )
            )
        } catch {
            slashErrorMessage = error.localizedDescription
            if let rollbackIndex = experimentalFeatures.firstIndex(where: { $0.name == currentFeature.name }) {
                experimentalFeatures[rollbackIndex] = ExperimentalFeature(
                    name: currentFeature.name,
                    stage: currentFeature.stage,
                    displayName: currentFeature.displayName,
                    description: currentFeature.description,
                    announcement: currentFeature.announcement,
                    enabled: currentFeature.enabled,
                    defaultEnabled: currentFeature.defaultEnabled
                )
            }
        }
    }
}
