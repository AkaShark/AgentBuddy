// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "AgentBuddy",
    platforms: [
        .iOS(.v26)
    ],
    products: [
        .library(name: "AgentBuddy", targets: ["AgentBuddy"])
    ],
    targets: [
        .binaryTarget(
            name: "codex_bridge",
            path: "apps/ios/Frameworks/codex_bridge.xcframework"
        ),
        .target(
            name: "AgentBuddy",
            dependencies: ["codex_bridge"],
            path: "apps/ios/Sources/AgentBuddy",
            publicHeadersPath: "Bridge"
        )
    ]
)
