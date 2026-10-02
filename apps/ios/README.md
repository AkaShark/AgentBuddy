# 搭子 · iOS

<p align="center">
  <img src="Sources/AgentBuddy/Assets.xcassets/AppIcon.appiconset/Icon-1024.png" width="112" height="112" alt="搭子 iOS App 图标" />
</p>

AgentBuddy 的 iPhone / iPad 客户端，使用 SwiftUI，最低支持 iOS 18。工程同时包含 Live Activity 扩展、Apple Watch App 和表盘复杂功能；Watch 最低支持 watchOS 11。

## 代码结构

| 路径 | 内容 |
| --- | --- |
| [Sources/AgentBuddy/App](Sources/AgentBuddy/App/) | App 入口、主导航、通知与启动页管理 |
| [Sources/AgentBuddy/Views](Sources/AgentBuddy/Views/) | 任务、项目、主机、会话与审批界面 |
| [Sources/AgentBuddy/Models](Sources/AgentBuddy/Models/) | Swift 观察层、界面状态与 iOS 平台服务 |
| [Sources/AgentBuddy/DesignSystem](Sources/AgentBuddy/DesignSystem/) | Mint 语义色、排版、间距与通用组件 |
| [Sources/AgentBuddy/Bridge](Sources/AgentBuddy/Bridge/) | UniFFI Swift 绑定及薄层平台桥接 |
| [Sources/AgentBuddyLiveActivity](Sources/AgentBuddyLiveActivity/) | Live Activity 扩展 |
| [Sources/AgentBuddyWatch](Sources/AgentBuddyWatch/) / [AgentBuddyWatchComplications](Sources/AgentBuddyWatchComplications/) | Watch App 与表盘复杂功能 |
| [Tests/AgentBuddyTests](Tests/AgentBuddyTests/) | XCTest 测试 |

iOS 与 Android 共用 [codex-mobile-client](../../shared/rust-bridge/codex-mobile-client/) Rust 客户端。Rust `AppStore` 持有运行时状态，`AppClient` 提供服务端操作；Swift `AppModel` 观察 Rust 快照，平台代码负责界面、权限、音频及系统集成。会话归并、传输、发现和 SSH 策略应放在共享 Rust 层。

UniFFI Swift / Kotlin 绑定通过仓库根目录的 `make bindings` 生成。Swift 输出位于 `Sources/AgentBuddy/Bridge/UniFFICodexClient.generated.swift`，生成文件与 `GeneratedRust/` 下的本地构建产物不提交到 Git。

## 真机构建与运行

准备完整 Xcode、rustup 工具链和 XcodeGen，并连接已配对的 iPhone。以下命令均在**仓库根目录**执行：

```sh
make rust-ios-device-fast
make xcgen
open apps/ios/AgentBuddy.xcodeproj
```

[project.yml](project.yml) 是 Xcode 工程的唯一配置源；修改后运行 `make xcgen`，不要手工编辑生成的 `.xcodeproj`。Debug 真机构建链接 `GeneratedRust/ios-device/libcodex_mobile_client.a`，不需要先打包 XCFramework。

在 Xcode 中选择 `AgentBuddy` scheme 和连接的 iPhone，构建并运行。仅检查编译时可选择 `Any iOS Device (arm64)`。命令行备用入口为：

| 命令 | 用途 |
| --- | --- |
| `make ios-device-fast` | 快速真机构建 |
| `make ios-device-run` | 快速真机构建、安装并启动 |
| `make bindings` | 修改共享接口后重新生成 UniFFI 绑定 |

本地构建、运行与 XCTest 测试均面向物理 iPhone，不使用 iOS 模拟器。完整前置条件、签名和自动化构建规则见 [AGENTS.md](../../AGENTS.md) 与 [开发文档](../../docs/DEVELOPMENT.md)。

## 图标与启动页

生产 App 图标位于 [AppIcon.appiconset](Sources/AgentBuddy/Assets.xcassets/AppIcon.appiconset/)，包含浅色、深色与着色版本；Watch 使用 [AppIcon.icon](Sources/AgentBuddy/AppIcon.icon/)。各端共用[品牌矢量母版](../../assets/brand/agentbuddy-mark.svg)，通过[资源生成脚本](../../tools/scripts/generate-brand-assets.cjs) 导出生产资源。

[AnimatedSplashView](Sources/AgentBuddy/Views/AnimatedSplashView.swift) 使用原生连接标记和本地化名称，保留底部代理名称纵向轮播；开启「减少动态效果」时显示静态名称。品牌组件位于 [BuddyBrand.swift](Sources/AgentBuddy/DesignSystem/Components/BuddyBrand.swift)。
