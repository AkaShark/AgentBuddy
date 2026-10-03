# 仓库指南

## 项目结构与模块划分
- `apps/ios/Sources/AgentBuddy/` 存放 iOS 应用代码。
- `apps/ios/Sources/AgentBuddy/Views/` 存放 SwiftUI 界面，`Models/` 存放应用状态与会话逻辑，`Bridge/` 存放生成的 UniFFI Swift 代码及轻量 Swift/ObjC 桥接代码。
- `apps/ios/Sources/AgentBuddy/App/` 存放应用入口、`AppDelegate`（通知、启动屏）、`ContentView`，以及 `App/HomeNavigation/`（按职责拆分的路由、导航目标与任务/主机/对话操作）。
- `apps/ios/Sources/AgentBuddy/DesignSystem/` 存放 Mint 设计系统：`Tokens/`（`AgentBuddyTheme` 上的语义颜色、`BuddySpacing`/`BuddyRadius`/`BuddySize`、`.buddyText(_:)` 字体层级）和 `Components/`（`BuddyButton`、`BuddyIconButton`、状态胶囊、卡片/标签、列表行、空状态/横幅/弹出面板样式）。新增或调整样式的 iOS UI 应使用这些定义，不要直接指定颜色、字体或尺寸。每个 Swift 文件不得超过 500 行。
- `apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/` 存放 Android Compose 外层框架与界面。
- `apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/designsystem/` 存放 Android Mint 设计系统：`tokens/`（`BuddySpacing`/`BuddyRadius`/`BuddyShapes`/`BuddySize`、`BuddyMotion` + `buddyReduceMotion`、`buddyTextStyle(BuddyTextStyle.X)` 和 `BuddyChromeTypeLimit`）和 `components/`（`BuddyButton`、`BuddyIconButton`、状态胶囊、卡片/标签、列表行、空状态/横幅/`BuddyBottomSheet`）。语义颜色定义在 `AgentBuddyTheme` 上（brand、action、link、borderControl、success/warning/danger 背景、disabled 等）。新增或调整样式的 Android UI 应使用这些定义，不要直接指定颜色、尺寸或使用 `.sp`。手机首页位于 `ui/homeshell/`（任务 / 项目 / 主机），审批界面位于 `ui/approvals/`，DEBUG 状态展示页位于 `ui/gallery/`（通过 `--es mint_gallery <page>` 启动，可选 `--ez mint_dark true`）。每个 Kotlin 文件不得超过 500 行。Android Mint 迁移记录见 `docs/design/android-mint-ui-migration.md`。
- `apps/android/app/src/main/java/com/akashark/agentbuddy/android/state/` 存放 Android 应用状态（`AppModel.kt`）、生命周期/语音控制器，以及平台存储（已保存的服务器/线程/应用、SSH 凭据）。传输与 SSH 在 Rust 中运行。
- `apps/android/core/bridge/` 存放 Android UniFFI 初始化代码（`UniffiInit.kt`，同时加载旧版 `codex_bridge` JNI 库）、Ghostty 渲染器 JNI 桥接，以及按 ABI 划分的 `jniLibs/`。
- `apps/android/app/src/test/java/` 存放 Android 单元测试。
- `apps/android/docs/qa-matrix.md` 跟踪 Android 与 iOS 功能对齐的 QA 覆盖情况。
- `apps/desktop/` 是唯一的 macOS 应用与发布路径；不要恢复已退役的 Catalyst target、签名配置或移动端发布任务。这是基于 Tauri v2 的 macOS 菜单栏主机应用：`src/` 是 React + TS 控制台，`src-tauri/` 是 Rust 后端，通过 CLI 驱动内置的 `agentbuddy` sidecar（`sidecar.rs` 维护子命令白名单，`shellenv.rs` 注入用户登录 shell 的 PATH）。它不链接 alleycat，也不接触移动端 Rust crate。参见 `apps/desktop/README.md`。
- `shared/rust-bridge/codex-mobile-client/` 是 iOS 和 Android 共用的唯一 Rust 客户端库。它负责公开 UniFFI 接口、类型化上游 RPC 支持、权威 store/reducer 状态、状态补全（hydration）、发现、SSH，以及共享运行时逻辑。`MobileClient` 是 Rust 内部的顶层门面。
- `shared/rust-bridge/codex-bridge/` 提供旧版 C-FFI 支持，不应用于新增移动端运行时功能。
- `apps/ios/Sources/AgentBuddy/Bridge/` 存放生成的 `UniFFICodexClient.generated.swift`（仅保留在本地）和手写桥接代码。仅有的手写 `Rust*.swift` 辅助文件是 `RustAlleycatBridge.swift` 和 `RustVoiceHandoff.swift`；其余内容包括 Ghostty 渲染器、Swift SSH 凭据/信任提供器、动态工具和消息内容桥接。
- Android 没有手写的 `Rust*.kt` 辅助文件：应用代码直接调用生成的 `uniffi.codex_mobile_client` 包。UniFFI Kotlin 源码生成到 `shared/rust-bridge/generated/kotlin/`，通过 `apps/android/app/build.gradle.kts` 中的 `java.srcDir` 直接编译到 `:app`；不要在 Android 源码目录下维护绑定文件的副本。
- `shared/third_party/codex/` 和 `shared/third_party/ghostty/` 固定使用 AkaShark fork 的 `codex/agentbuddy` 维护分支。移动端适配直接提交到这些 fork；构建时不应用补丁。
- `apps/ios/GeneratedRust/` 存放本地生成的 iOS Rust 构建产物：UniFFI 头文件/modulemap，以及原始真机/模拟器静态库。这些产物不提交到仓库。
- `apps/ios/Frameworks/` 存放下载或通过打包流程生成的 iOS XCFramework（打包构建中的 `codex_mobile_client.xcframework`）。这些产物不提交到仓库。
- `apps/ios/project.yml` 是项目生成的唯一依据；应重新生成 `apps/ios/AgentBuddy.xcodeproj`，不要手动编辑项目文件。

## 架构
- **iOS 根布局：** `ContentView`（`App/ContentView.swift`）使用 `ZStack` 叠放主题背景、`HomeNavigationView`（主 `NavigationStack`：首页框架 → 对话）和悬浮层（宠物悬浮层、待审批横幅）。手机首页为 `HomeShellView`（`Views/HomeShell/`），包含任务 / 项目 / 主机标签页和底部的新建任务胶囊按钮；在 iPad 上，同一框架作为 `NavigationSplitView` 的侧边栏呈现。`HeaderView` 仅用作对话界面工具栏的 principal 项。审批通过 `Views/Approvals/` 渲染（`ApprovalCoordinator` 只保存 UI 层的提交中/失败/结果状态；审批决定仍交给 `store.respondToApproval`）。DEBUG 状态展示页（`Views/Previews/MintGalleryView.swift`，通过 `--mint-gallery=<page>` 启动，可选 `--mint-dark`）使用预置数据渲染界面，用于设备截图。
- **iOS 状态管理：** `AppStore`（Rust，通过 UniFFI 访问）是运行时状态的权威来源。`AppModel` 是观察 Rust 快照和更新的轻量 Swift 外壳。`AppState` 仅保存 UI 状态。
- **iOS 服务器流程：** 发现与 SSH 使用独立的工具桥接；线程/会话/账户操作通过生成的 Rust RPC 和 store 更新完成。
- **Android 根布局：** `ui/AgentBuddyApp.kt` 中的 `AgentBuddyApp()` 是 Compose 入口（由 `MainActivity` 承载）；`state/AppModel.kt` 是观察 Rust `AppStore` 快照和更新的轻量外壳。
- **Android 状态/传输：** Android 应与 iOS 使用同一套由 Rust 管理的运行时模型，不要在 Kotlin 中重新实现共享的会话/线程/账户逻辑。
- **Android 服务器流程：** 发现初始信息来自 Android NSD，但发现结果的合并/探测策略位于 Rust；连接、认证及线程/账户流程通过 Rust RPC + store 更新完成。
- **消息渲染一致性：** 两个平台都支持推理/系统分区、代码块渲染和内联图片处理。

### 共享 Rust 层
- `codex-mobile-client` 是唯一公开的移动端 Rust crate。保持一套生成的 Swift/Kotlin 绑定接口，不要再次将 UniFFI 拆分到多个移动端 crate。
- 实时语音使用 libwebrtc（iOS 通过 stasel/WebRTC SPM 使用 Google WebRTC.framework，Android 使用 `io.github.webrtc-sdk:android`）。对等连接在各平台原生运行；AEC/NS/VAD 由 libwebrtc 的音频处理模块负责。Rust 层仅负责信令、会话生命周期、转录状态和交接编排。
- `AppStore` 是由 Rust 管理的状态接口，负责快照、类型化更新，以及少量真正的组合操作/store 本地操作。
- `AppClient` 是公开的 UniFFI 客户端接口，用于直接服务器操作和类型化结果。
- `DiscoveryBridge` 和 `SshBridge` 是独立的 Rust 工具接口。不要将发现/SSH 策略移回 Swift/Kotlin。
- iOS 使用 UniFFI 生成的 Swift 加两个轻量辅助文件（`RustAlleycatBridge.swift`、`RustVoiceHandoff.swift`）；Android 直接调用 UniFFI 生成的 Kotlin。
- iOS Debug/真机构建链接 `apps/ios/GeneratedRust/ios-device/libcodex_mobile_client.a` 中的原始静态库。打包/发布流程仍可创建 `apps/ios/Frameworks/codex_mobile_client.xcframework`，但它不是默认的 Debug/真机构建产物。

## 功能归属规则
- 优先使用 Rust。会话状态、线程状态、流式处理、状态补全、审批、认证/账户、发现合并策略、语音转录/交接规范化、状态规范化等逻辑，都应放在 `shared/rust-bridge/codex-mobile-client/`。
- 桌面主机应用：控制台需要从守护进程获取的内容，必须来自已有的 sidecar 子命令（`status --json`、`pair` 等）。如果 CLI 缺少能力，先在 alleycat fork 中添加；不要抓取日志文件，也不要在应用中重新实现守护进程逻辑。
- 保持 Swift/Kotlin 轻量。平台代码仅负责 UI、平台持久化、平台权限、音频/会话 API、通知、ActivityKit/CarPlay/Android 服务，以及仅用于渲染的数据投影。
- 不要在 Swift/Kotlin 中解析上游传输格式字符串。如果某个状态、事件类型或载荷结构对两个平台都有意义，应由 Rust 通过类型化 UniFFI 枚举/记录暴露。
- 不要在 iOS 或 Android 中重复实现合并/reducer/状态机逻辑。共享状态协调逻辑应放在 Rust reducer/store 代码中。
- 如果共享 Rust 需要直接服务器操作，应在 `AppClient` 上暴露由移动端定义的请求/结果类型，不要在 `AppStore` 上添加手写包装方法。
- 公开 UniFFI 接口应保持手写且精简。状态协调策略放在手写的 Rust reducer/reconcile 代码中。
- `AppStore` 应保持最小化：仅包含快照、订阅和真正的组合操作/store 本地操作。直接服务器操作属于 `AppClient`。
- 优先使用权威更新。store 状态应首先由上游事件填充；只有上游事件不足时，才进行有针对性的刷新/协调。不要在 RPC 成功后手动修补平台状态。
- 跨入 Swift/Kotlin 的新增边界类型应使用兼容 UniFFI 的 Rust 记录/枚举。仅供 Rust 内部使用的状态可以更丰富，不必兼容 UniFFI。
- 仓库没有生成的 Rust 源码。唯一生成的代码是 UniFFI Swift/Kotlin 绑定（`shared/rust-bridge/generated/`、`apps/ios/Sources/AgentBuddy/Bridge/UniFFICodexClient.generated.swift`）；仅保留在本地（由 gitignore 忽略），通过 `make bindings` / `./shared/rust-bridge/generate-bindings.sh` 重新生成。

## 新功能的实现位置
- 新增或修改直接服务器操作支持：
  - 在 `shared/rust-bridge/codex-mobile-client/src/ffi/client.rs` 中添加类型化 `AppClient` 方法，使用移动端定义的请求/结果记录
  - 通过 `shared/rust-bridge/codex-mobile-client/src/mobile_client/` 中的 `MobileClient` 路由（`request_typed_for_server`）
  - 上游事件不足时，在 `shared/rust-bridge/codex-mobile-client/src/store/reconcile.rs` 中添加状态协调逻辑
  - 重新生成绑定
- 新增权威运行时状态、reducer 逻辑或状态协调：
  - `shared/rust-bridge/codex-mobile-client/src/store/`
- 新增对话状态补全、类型化条目转换或共享状态规范化：
  - `shared/rust-bridge/codex-mobile-client/src/conversation.rs`
  - `shared/rust-bridge/codex-mobile-client/src/conversation_uniffi.rs`
  - `shared/rust-bridge/codex-mobile-client/src/ffi/shared.rs`
- 新增发现结果排序/去重/协调：
  - `shared/rust-bridge/codex-mobile-client/src/discovery.rs`
  - `shared/rust-bridge/codex-mobile-client/src/discovery_uniffi.rs`
- 新增语音转录/交接/共享实时数据规范化：
  - `shared/rust-bridge/codex-mobile-client/src/store/voice.rs`
  - `store/` 中的 reducer/update 边界类型
- 新增 iOS 专属行为：
  - 控制器/平台服务放在 `apps/ios/Sources/AgentBuddy/Models/`
  - SwiftUI 放在 `apps/ios/Sources/AgentBuddy/Views/`
  - 这些文件中不得包含共享协议解析和共享业务规则
- 新增 Android 专属行为：
  - `apps/android/app/` 和 `apps/android/core/bridge/`
  - 这些文件中不得重复实现由 Rust 管理的状态/reducer 逻辑

## 防止架构与平台偏离的约束
- 默认保持移动端功能一致。变更影响共享移动端行为或面向用户的移动端流程时，应在同一轮工作中实现并验证 iOS 和 Android，除非确实是平台专属功能。
- 如果某项移动端变更有意只在一个平台发布，应在总结中说明原因，并记录另一平台需要跟进的工作。
- 新增 Swift/Kotlin 逻辑前，先判断：Android/iOS 是否都需要这个行为？如果是，放到 Rust。
- 在 Swift/Kotlin 模型中新增 `String` 状态字段前，先判断：是否应使用 Rust 枚举？通常应该。
- 新增 `AppStore` 方法前，先判断：这是否是真正的组合操作/store 操作，还是应该放在 `AppClient` 上？
- 新增平台缓存前，先判断：这是否是应存放在 Rust store 中的权威运行时数据？
- 不确定时，优先采用一份共享 Rust 实现加轻量平台投影，避免两套并行的原生实现。
- 修改 Codex/Ghostty 时，先在 fork 分支提交并推送到对应的 AkaShark fork，再提交父仓库的 gitlink。绝不推送到官方上游，也不要以为推送顶层仓库会包含子模块修改。维护分支使用 `codex/agentbuddy`；不要重写已发布的历史。工作流程见 `docs/DEVELOPMENT.md`。

## 依赖
### iOS（通过 `apps/ios/project.yml` 配置 SPM）
- **Hairball**（`dnakov/hairball`，产品 `HairballUI`）— 使用自定义主题渲染助手/系统消息中的 Markdown。
### Android（Gradle）
- **Compose Material3** — 主要 Android UI 工具包。
- **Markwon** — 渲染助手/系统文本中的 Markdown。
- **androidx.security:security-crypto** — 加密凭据存储。
### 共享 Rust 层（Cargo）
- **codex-app-server-protocol**、**codex-app-server-client**、**codex-protocol**、**codex-core** — 上游 Codex crate。
- **tokio-tungstenite** — 异步 WebSocket 传输。
- **russh** — SSH 客户端（共享 Rust SSH，替代平台原生 SSH 库）。
- **uniffi** — 从 Rust 生成 Swift/Kotlin 绑定。
- **lru**、**base64**、**regex** — 工具 crate。

## 新检出环境的前置条件
在新机器上构建前，请确认：
1. `xcode-select -p` 必须输出 `/Applications/Xcode.app/Contents/Developer`，而不是 `/Library/Developer/CommandLineTools`。可用 `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` 修复。Command Line Tools 不包含 iOS 模拟器 SDK。
2. `cargo` 和 `rustc` 必须来自 **rustup**，而不是 Homebrew 的 `rust` 软件包。如果 `which cargo` 指向 `/opt/homebrew/bin/cargo`（Homebrew 独立二进制，而非 rustup 代理），即使 `rustup target list` 显示已安装，`aarch64-apple-ios-sim` 等交叉编译目标仍会失败。Makefile 会自动将 rustup 工具链的 bin 目录放到 PATH 前面，但单独运行脚本和 CI 环境也必须确保解析到正确的工具。可以执行 `brew uninstall rust`，或在 PATH 中将 `~/.cargo/bin`（或 `rustup which cargo` 对应的 rustup 工具链 bin 目录）放在 `/opt/homebrew/bin` 前面。
3. 必须安装 `xcodegen`（`brew install xcodegen`），用于生成 Xcode 项目。
4. 桌面应用：需要 Node 22，并在 `apps/desktop` 中执行 `npm ci`；`make desktop-sidecar` 需要 rustup 工具链。
5. *（可选）* 当设备不在本地网络时，`pymobiledevice3` 可让 `make ios-device-run` 通过 Tailscale 运行。使用 `pipx install pymobiledevice3`（或 `uv tool install pymobiledevice3`）安装。Mac 和 iOS 设备上都需要安装 Tailscale。

## iOS 构建与运行策略（禁止使用模拟器）
- 不要在模拟器上构建、运行或测试 iOS。禁止使用 `make ios-sim`、`make ios-sim-fast`、`make rust-ios-sim-fast`、`xcrun simctl`，以及任何 `-destination 'platform=iOS Simulator,…'`。Makefile 中的模拟器流程仅为兼容性保留，不得在本地使用。
- 通过 Apple Xcode MCP（`mcp__xcode__*`，由 `xcrun mcpbridge` 提供；不是 XcodeBuildMCP）构建到实体 iPhone：
  1. 在 Xcode 中打开当前编辑的检出目录或 worktree 中的 `apps/ios/AgentBuddy.xcodeproj`，scheme 选择 `AgentBuddy`。
  2. 工具栏运行目标必须是已连接的 iPhone（仅编译检查时可选 `Any iOS Device (arm64)`）。`BuildProject` 使用当前选中的 scheme 和目标进行构建，无法切换它们。应通过 AppleScript 切换，不要询问用户（变量不要命名为 `target`，它是 Xcode 字典中的保留字）：
     `osascript -e 'tell application "/Applications/Xcode-26.3.0.app"' -e 'repeat with d in workspace documents' -e 'if (path of d) contains "<checkout>" then set active run destination of d to (first run destination of d whose name is "<device name>")' -e 'end repeat' -e 'end tell'`
     使用 `name of run destinations of d` 列出可选目标；实体设备的平台为 `iphoneos`。
  3. 通过 `XcodeListWindows` 获取 `tabIdentifier`，然后调用 `BuildProject`，通过 `GetBuildLog` / `XcodeListNavigatorIssues` 查看失败信息。测试使用 `RunSomeTests` / `RunAllTests`，并保持同一真机目标。
  4. 安装并启动构建出的应用：先执行 `xcrun devicectl device install app --device <id> <DerivedData>/Build/Products/Debug-iphoneos/AgentBuddy.app`，再执行 `xcrun devicectl device process launch --device <id> com.akashark.agentbuddy`。通过 `xcrun devicectl list devices` 查找 `<id>`。
- 真机构建链接 `apps/ios/GeneratedRust/ios-device`（`make rust-ios-device-fast`），使用团队 `HNKUYWPBVC` 签名。Xcode MCP 不可用时，命令行备用方案为 `make ios-device-fast`（仍为真机构建）。
- 不要重新创建模拟器缓存。如果出现 `apps/ios/GeneratedRust/ios-sim` 或 `Debug-iphonesimulator` 构建产物，应删除它们。

## 构建系统
根目录的 `Makefile` 是主要构建入口，负责协调 fork 子模块同步、UniFFI 绑定生成、Rust 交叉编译、原始静态库生成、可选 xcframework 打包、Xcode 项目生成，以及各平台构建。它通过 `.build-stamps/` 中的标记文件缓存，让重复运行跳过已完成的步骤。如果安装了 `sccache`，打包/CI 构建会通过 `RUSTC_WRAPPER=sccache` 使用本地磁盘缓存；远端 S3/R2 后端需主动配置（R2：`SCCACHE_BUCKET` + `SCCACHE_ENDPOINT` + `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`；普通 AWS S3：`SCCACHE_BUCKET` + AWS 凭据或 `AWS_PROFILE`/`SCCACHE_AWS_PROFILE`，可选 `SCCACHE_REGION`/`AWS_REGION`；仅配置 bucket、未配置 endpoint 和凭据时，会回退到本地缓存，参见 `tools/scripts/load-sccache-aws-creds.sh`）。

iOS Rust 构建分为快速开发与打包两类流程，其中快速流程区分真机和模拟器：
- 快速开发流程：在 `apps/ios/GeneratedRust/` 中生成原始静态库和头文件，用于 Debug/真机构建（`make rust-ios-device-fast`、`make ios-device-fast`）。
- 快速模拟器流程：在 `apps/ios/GeneratedRust/ios-sim` 中生成原始模拟器静态库和头文件，用于 Debug/模拟器构建（`make rust-ios-sim-fast`、`make ios-sim-fast`）。本地不使用；参见 iOS 构建与运行策略。
- 打包流程：构建真机和模拟器 Rust 产物，并打包 `codex_mobile_client.xcframework`（`make rust-ios-package`、`make ios`、`make ios-device`、`make ios-sim`）。

增量编译策略：
- 打包目标使用 `CARGO_INCREMENTAL=0`。
- 开发目标（`DEV_CARGO_ENV`）设置 `CARGO_INCREMENTAL=1` 并取消 `RUSTC_WRAPPER`：sccache 不支持增量编译，而小改动后的重新构建使用增量编译更快。

### 常用目标
| 目标 | 说明 |
|---|---|
| `make ios` | 完整 iOS 打包流程：同步 → Ghostty → 绑定 → Rust（真机+模拟器）→ xcframework → xcgen → 模拟器构建（不运行 `alpine-fs`） |
| `make alpine-fs` | 从 `dnakov/litter-ish` 下载固定版本的 Alpine rootfs；升级时修改 `Makefile` 中的 `ALPINE_FS_VERSION`。本地 iSH 禁用期间不打包进 iOS 应用（参见 fork 说明）。 |
| `make ios-sim` | 完整 iOS 打包流程 + 模拟器构建（本地不使用；参见 iOS 构建与运行策略） |
| `make ios-sim-fast` | 使用 `GeneratedRust/ios-sim` 中原始模拟器静态库产物的快速 iOS 模拟器流程（本地不使用） |
| `make ios-device` | 完整 iOS 打包流程 + 真机构建 |
| `make ios-device-fast` | 使用 `GeneratedRust/` 中原始静态库产物的快速 iOS 真机流程 |
| `make ios-run` | 完整构建 iOS 后打开 Xcode |
| `make android` | 快速 Android 开发构建（默认 `arm64-v8a` / `android-dev` 配置）：Rust JNI → Alpine fs + proot → Gradle `assembleDebug` |
| `make android-emulator-fast` | 使用适合宿主机的模拟器 ABI 进行快速 Android 开发构建（Apple Silicon 使用 `arm64-v8a`，Intel 使用 `x86_64`） |
| `make android-install` | 构建并将调试 APK 安装到已连接的实体设备（可用 `ANDROID_DEVICE_SERIAL` 覆盖设备选择） |
| `make all` | 构建两个平台 |
| `make rust-ios` | 完整 Rust iOS 打包流程的别名 |
| `make rust-ios-package` | 构建/打包 iOS Rust（真机+模拟器+xcframework） |
| `make rust-ios-sim-fast` | 仅构建原始 Rust 模拟器静态库和头文件（本地不使用） |
| `make rust-ios-device-fast` | 仅构建原始 Rust 真机静态库和头文件 |
| `make rust-android` | 仅构建 Android JNI `.so` 文件 |
| `make rust-check` | 在宿主机上对共享 Rust crate 执行 `cargo check` |
| `make rust-test` | 在宿主机上对共享 Rust crate 执行 `cargo test` |
| `make bindings` | 重新生成 UniFFI Swift + Kotlin 绑定 |
| `make xcgen` | 从 `project.yml` 重新生成 `AgentBuddy.xcodeproj` |
| `make test` | 运行 Rust + iOS + Android 测试 |
| `make testflight` | 完整构建 iOS 并上传 TestFlight |
| `make play-upload` | 完整构建 Android 并上传 Google Play |
| `make clean` | 删除所有构建产物与标记缓存 |

### 缓存失效处理
- `make rebuild-bindings` — 强制重新构建 UniFFI 绑定。
- `make clean-rust` / `make clean-ios` / `make clean-android` — 删除对应平台的产物。

### 配置覆盖（环境变量）
- `IOS_SIM_DEVICE` — 模拟器名称（默认：`iPhone 17 Pro`）
- `XCODE_CONFIG` — Xcode 构建配置（默认：`Debug`）
- `IOS_SCHEME` — Xcode scheme（默认：`AgentBuddy`）
- `IOS_DEPLOYMENT_TARGET` — 最低 iOS 版本（默认：`18.0`）
- `ANDROID_SDK_ROOT` / `ANDROID_NDK_HOME` / `JAVA_HOME` — 在未预配置环境的 shell 中构建 Android 时必需；本地常见值分别为 `$HOME/Library/Android/sdk`、`$HOME/Library/Android/sdk/ndk/<version>` 和 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`

### 独立脚本（由 Make 调用，也可单独运行）
- `./apps/ios/scripts/build-rust.sh` — 为 iOS 交叉编译 Rust；快速模式将原始静态库和头文件输出到 `apps/ios/GeneratedRust/`，打包模式还会创建 `codex_mobile_client.xcframework`
- `./apps/ios/scripts/download-alpine-fs.sh` — 获取固定版本的 `dnakov/litter-ish` rootfs，验证校验和，并解压到 `apps/ios/Resources/fs/`。从环境变量读取 `ALPINE_FS_VERSION`（由 `make alpine-fs` 设置）。
- `./apps/ios/scripts/sync-codex.sh` / `sync-ghostty.sh` — 初始化固定版本的 fork，或保留当前开发检出状态；`--recorded-gitlink` 会检出 gitlink 记录的提交。绝不应用或反向应用补丁。`patches/` 仅用于追溯历史来源。
- `./apps/ios/scripts/regenerate-project.sh` — 通过 xcodegen 重新生成 Xcode 项目；这是安全的生成方式，因为它会先删除意外产生的嵌套目录 `apps/ios/AgentBuddy.xcodeproj/AgentBuddy.xcodeproj`
- `./apps/ios/scripts/testflight-upload.sh` — 归档、导出 IPA、上传 TestFlight
- `./shared/rust-bridge/generate-bindings.sh` — 生成 UniFFI Swift/Kotlin 绑定
- `./tools/scripts/build-android-rust.sh` — 通过 `cargo-ndk` 为 Android 交叉编译 Rust JNI 库
- `./tools/scripts/testflight-feedback.sh` — 获取 TestFlight 反馈，可选下载截图；支持用 `SINCE` / `UNTIL` 环境变量按 createdDate 时间范围筛选
- `./tools/scripts/fetch-mobile-store-artifacts.py` — 一次性获取 iOS + Android 商店问题排查资料；使用 `--last-hours N` 或 `--since ... --until ...`，将 TestFlight 反馈/崩溃/崩溃日志和 Play 评论/崩溃问题/报告拉取到同一输出目录，并输出带本地产物链接的 Markdown 摘要。TestFlight 反馈获取复用 `testflight-feedback.sh`。Android 私密测试反馈仍只能在 Play Console UI 中查看，无法通过此处使用的公开 API 获取。
- `./tools/scripts/triage-mobile-feedback.py` — 可重复运行的 GitHub + TestFlight + Play 问题分诊台账。它封装 `fetch-mobile-store-artifacts.py`，获取 GitHub issue/PR，将每次运行的原始快照存入 `artifacts/mobile-triage/runs/`，并在 `artifacts/mobile-triage/triage-state.json` 中保留各条目的状态/备注。处理条目后使用 `mark '<item-id>' --status done --note ...`；已创建修复 PR 时使用 `--status pr-open --note 'Fix PR #...'`，避免后续运行将同一条目再次放入未处理队列。

### Ghostty（终端渲染器）
- libghostty 使用 zig 0.15.2 从 `shared/third_party/ghostty` 中固定版本的 `AkaShark/ghostty` fork 构建：`make ghostty-ios` / `make ghostty-android`。
- `GHOSTTY_KEEP_ZIG_CACHE=1` 可保留预先填充的 zig 包缓存，避免清空（适用于 iOS 和 Android Ghostty 脚本）。这适用于 zig 包下载器卡住的网络环境，或终端安全软件删除已下载包内文件、导致 zig 报告 `hash mismatch` 的情况（先预取一次依赖，再构建）。
- Xcode 26 下，Ghostty 的 Metal 渲染器需要执行 `xcodebuild -downloadComponent MetalToolchain`。

### 热重载
- 尚未接入：仓库没有集成 InjectionIII / `@ObserveInjection`。

## 自主调试操作指南
- 本地迭代优先使用快速流程，再考虑打包/发布流程：iOS 通过 Xcode MCP 构建到实体 iPhone（备用方案为 `make ios-device-fast`）；Android 使用 `make android-emulator-fast`。绝不使用 iOS 模拟器。
- 需要反复分诊 GitHub、TestFlight 和 Play 的商店反馈/崩溃时，从 `./tools/scripts/triage-mobile-feedback.py --last-hours 24`（或明确的 `--since` / `--until` 时间范围）开始。查看 `artifacts/mobile-triage/triage-board.md`，然后通过 `./tools/scripts/triage-mobile-feedback.py mark '<item-id>' --status done --note 'fixed in ...'` 或 `--status pr-open --note 'Fix PR #...'` 标记已处理条目。只有一次性获取 iOS/Android 商店原始快照，或深入调试 ASC / Play API 时，才直接使用 `fetch-mobile-store-artifacts.py`。
- 调试 iOS 时，直接从 DerivedData 安装最新真机构建，不要依赖设备上已安装的旧版本：先执行 `xcrun devicectl device install app --device <id> <.../Build/Products/Debug-iphoneos/AgentBuddy.app>`，再执行 `xcrun devicectl device process launch --device <id> com.akashark.agentbuddy`。
- 重新生成 Xcode 项目时，使用 `make xcgen` 或 `./apps/ios/scripts/regenerate-project.sh`。不要在 `apps/ios` 内执行 `xcodegen generate --spec project.yml --project AgentBuddy.xcodeproj`，这会生成嵌套目录 `apps/ios/AgentBuddy.xcodeproj/AgentBuddy.xcodeproj`。
- 调试 Android 模拟器时，使用 `make android-emulator-fast` 构建，使用 `adb -e install -r apps/android/app/build/outputs/apk/debug/app-debug.apk` 安装，然后通过 `adb -e shell am start -n com.akashark.agentbuddy.android/com.akashark.agentbuddy.android.MainActivity` 启动。
- 验证共享 Rust 变更时，保持两端运行环境都可用：连接 iPhone（通过 `xcrun devicectl list devices` 检查），并通过 `adb devices -l` 确认 Android 模拟器可见。
- 移动端日志现保留在本地：iOS 使用 Xcode/设备控制台，Android 使用 Logcat，Rust 使用常规 `tracing` 输出，不使用日志收集器或暂存目录。

## 代码风格与命名约定
- Swift 遵循标准 Xcode 默认风格：4 空格缩进，类型使用 `UpperCamelCase`，属性/函数使用 `lowerCamelCase`。
- Kotlin 遵循标准 Android/Kotlin 约定：4 空格缩进，类型使用 `UpperCamelCase`，成员使用 `lowerCamelCase`。
- 主题由 JSON 驱动：`apps/ios/Sources/AgentBuddy/Resources/Themes/` 中约有 80 个主题（也作为 Android assets 打包）。默认主题为 `agentbuddy-mint-light` / `agentbuddy-mint-dark`（`ThemeManager`），仅在用户未选择主题时生效。Mint 主题包含额外的 `agentbuddy.*` 语义键（brand、onBrand、borderControl、focus、success/warning/danger 背景、disabled）；其他主题通过 `ResolvedTheme` 中的回退逻辑获得这些值。默认字体为系统字体；内置 Berkeley Mono（回退到 `SFMono-Regular`）仍可选择，并始终用于代码。使用语义主题角色，不要硬编码值。iOS Mint 迁移记录见 `docs/design/ios-mint-ui-migration.md`。
- 明确并发边界（`actor`、`@MainActor`），避免跨 actor 的可变状态。
- iOS 文件按层组织（`Views`、`Models`、`Bridge`），Android 文件按模块组织（`app/ui`、`app/state`、`core/*`）。
- 仓库目前没有提交本地 SwiftLint/SwiftFormat 配置；格式应与现有文件保持一致。

## 测试指南
- iOS 测试：优先在 `apps/ios/Tests/AgentBuddyTests/` 下使用 XCTest，文件命名为 `*Tests.swift`。
- Android 测试：单元测试放在 `apps/android/app/src/test/java/`。
- iOS 测试在已连接的 iPhone 上运行，绝不使用模拟器：使用 Xcode MCP `RunSomeTests` / `RunAllTests`，或 `xcodebuild test -destination 'platform=iOS,id=<device-id>'`。
- Android 测试命令：`cd apps/android && ./gradlew :app:testDebugUnitTest`。
- 两端功能对齐范围变化时，同步更新 `apps/android/docs/qa-matrix.md`。

## 提交与 Pull Request 指南
- 提交标题简洁，使用祈使语气，可选添加范围前缀（示例：`bridge: retry initialize handshake`）。
- PR 应包含：目的、主要变更、验证步骤（命令/设备）；UI 变更还应附截图。
- 项目结构变更时，应同步更新 `apps/ios/project.yml`，并说明是否已重新生成项目。
- iOS 构建和测试通过 Apple Xcode MCP（`mcp__xcode__*`）在实体 iPhone 上进行；参见 iOS 构建与运行策略。

## AgentBuddy fork 说明

AgentBuddy（中文显示名「搭子」）是包子/Baozi（`huangguang1999/baozi`）更换品牌后独立发布的 fork；Baozi 本身又是 `dnakov/litter`（`kittylitter` Mac 守护进程 + `alleycat` P2P 传输）更换品牌后的 fork。共享 Rust 核心与架构沿用上游；本 fork 的差异主要在品牌、分发身份和少量产品决策。

- **品牌：** 所有面向用户的名称、bundle/package ID、scheme 和标识符均使用 AgentBuddy（zh-Hans / Android UI 显示「搭子」）。iOS scheme + 项目：`AgentBuddy` / `apps/ios/AgentBuddy.xcodeproj`（从 `project.yml` 重新生成，绝不手动编辑）。Android 包名：`com.akashark.agentbuddy.android`（核心桥接包为 `com.akashark.agentbuddy.android.core.bridge`；`android_jni.rs` / `android_context.rs` 中的 Rust JNI 导出编码了该包名，必须随包名一起修改）。Mac 守护进程二进制名为 `agentbuddy`（crate 为 `services/kittylitter` 中的 `agentbuddycli`，作为桌面应用 sidecar 分发，bundle ID 为 `com.akashark.agentbuddy.host`；不发布到 npm；launchd 标签为 `com.akashark.agentbuddycli`；目录名和 Makefile 中的 `KITTYLITTER_*` 变量有意保留上游名称）。GitHub 仓库：`https://github.com/AkaShark/AgentBuddy`。
- **签名（iOS）：** Apple 团队为 `HNKUYWPBVC`，bundle 为 `com.akashark.agentbuddy`（另有 `.liveactivity`、`.watchkitapp`、`.watchkitapp.complications`；应用组为 `group.com.akashark.agentbuddy`；URL scheme 为 `agentbuddyauth`）。真机构建使用自动签名：`xcodebuild ... -allowProvisioningUpdates DEVELOPMENT_TEAM=HNKUYWPBVC`。App Store Connect API 密钥位于 `~/.appstoreconnect/private_keys/`。Baozi 时期的 `DDZU3W897W` 和 litter 时期的 `UCYH39VCQT` 团队证书不适用于本应用，不要使用。
- **Android 构建：** 需要 Android SDK platform-35 + NDK + `cargo-ndk` + JDK 17；Rust `.so` 通过 `tools/scripts/build-android-rust.sh` 构建。当每个打包 ABI 都存在 `ghostty.h` + `libghostty.so` 时，构建会自动启用 Ghostty JNI。ABI 列表优先使用 `ANDROID_ABIS`，未设置时使用 `build-android-rust.sh` 上次构建到 `core/bridge` jniLibs 的 ABI；`:app` 使用同一列表打包（`build-android-rust.sh` 按需使用 zig 0.15.2 构建这些库；brew 默认的 0.16.0 无法通过 `requireZig`；可用 `-Plitter.enableGhosttyAndroid=false` 或 `LITTER_ENABLE_GHOSTTY_ANDROID=0` 强制关闭）。UI 中终端是否显示仅由 `TERMINAL` 实验性开关控制（默认关闭）。Firebase `google-services.json` 不提交到仓库，必须为 `com.akashark.agentbuddy.android` 生成。
- **alleycat fork：** `shared/rust-bridge/Cargo.toml` 和 `services/kittylitter/Cargo.toml` 中的 Rust 依赖指向公开 fork `https://github.com/AkaShark/alleycat.git`，固定在 `feat/host-push-notifications` fork 分支的头部提交 `4e95effd9f8d51cbee4d1e64c1d1bf79acb7d1be`。该分支包含所有 AgentBuddy 对 alleycat 的变更：上游 `3c6dfe2`、主机推送通知、MyFlicker `mfcli` agent 和 ACP 桥接修复（AkaShark/alleycat#2，已合入该分支）、提示词执行期间即可响应的 ACP `turn/start`，以及为缺少 cwd 的 ACP 命令补上会话 cwd（手机端会丢弃 `cwd` 不是绝对路径的条目）。AgentBuddy 在此分支维护 alleycat：新增 alleycat 变更直接进入此分支，或通过其他分支合入，再将固定版本更新到新的分支头部。fork 的 `main` 跟踪上游，目前不使用；暂时没有合入该分支或拉取上游变更的计划（PR AkaShark/alleycat#1 已关闭，未合并）。`tools/scripts/update-alleycat-main.sh` 默认不执行任何操作；绝不要设置 `AGENTBUDDY_REFRESH_ALLEYCAT=1` 来运行它，否则会将固定版本移到 fork 的 `main`，丢失主机推送和 MyFlicker 支持。`.cargo/config.toml` 设置了 `net.git-fetch-with-cli = true`，让 Cargo 通过系统 `git` 拉取 Git 依赖。（`ish-embed-host` 保留在 `dnakov/litter-ish` 是合理的：这是上游依赖，未创建 fork。）
- **本地化：** 基础语言（英语）显示名为 `AgentBuddy`；iOS zh-Hans 字符串位于 `apps/ios/Sources/AgentBuddy/zh-Hans.lproj/Localizable.strings` 和 `InfoPlist.strings`，显示「搭子」。Android UI 字符串是 **Kotlin 中硬编码的中文字符串字面量**（没有 `values-zh` 资源），翻译时直接原地修改；`android:label` 为「搭子」。注意，`Text(stringVariable)` 会原样渲染；只有 `Text("literal")` / `LocalizedStringKey` 才会进行本地化。
- **相对上游的产品差异：** 两端均移除了打赏/TipJar；使用自带 API key 模式（不提供托管登录）；应用图标、启动屏和首页品牌使用 `assets/brand/agentbuddy-mark.svg` 中的 AgentBuddy 双链标志（通过 `tools/scripts/generate-brand-assets.cjs` 导出，需要 `sharp` 和 macOS `iconutil`）；已移除「喵闻联播」`cat_transmission` 彩蛋和 TipJar StoreKit 配置。
- **推送基础设施：** 轮次完成通知由主机上报。当轮次在声明支持 `push.v1` 的 alleycat 主机上开始时，共享 Rust `PushManager`（`src/push/`）会将平台推送令牌加密封装给 Worker，并通过 iroh 通道发送 `push_subscribe`；主机（alleycat fork 的 `push` 模块）监视轮次的终态，并通过签名请求上报给 AgentBuddy Cloudflare Worker `https://agentbuddy-push-proxy.aaksharker.workers.dev`（`services/push-proxy`，v2 API，`HostChannel` Durable Object），后者发送可见的 APNs/FCM 通知。平台仅负责交付令牌（`AppClient.setPushRegistration`）、显示/路由通知，并在应用处于前台时让所有通知保持静默（任何界面，包括调试通知）。旧的 30 秒静默保活（`/register`）已从应用移除，仅在 Worker 上保留，直到设置 `LEGACY_KEEPALIVE_ENABLED=false`。设计、签名字符串和测试向量见 `docs/superpowers/specs/2026-09-24-host-push-notifications-design.md`；Worker API、密钥（`PUSH_TARGET_SEAL_KEY`，可选 `DEBUG_PUSH_ADMIN_TOKEN`）、迁移与回滚见 `services/push-proxy/README.md`；一次性测试推送使用 `tools/scripts/debug-push.py`。APNs、Firebase 凭据和封装密钥属于 Worker secrets，绝不能进入 Git。Android Alpine rootfs 与 iOS 一样来自 `dnakov/litter-ish` releases（Baozi 时期的 `huangguang1999/baozi-ish` 仓库已不存在）。`[baozi-fork]` 注释标记继承的变更，应保留用于追溯来源。
- **共享哨兵值：** `"This Device"` 是 iOS/Android/Rust 中用于比较的跨平台哨兵值；绝不翻译存储值，只在渲染时映射为显示字符串。
- **配对安全（已知风险，暂不修复）：** alleycat 配对对所有手机共用一个 bearer token（没有按设备划分的令牌/白名单）；撤销需执行 `agentbuddy rotate`，这会使所有手机的令牌失效。alleycat 默认启用 `shell` agent（PTY 登录 shell）、claude `bypass_permissions=true`（`--dangerously-skip-permissions`）和 amp `dangerously_allow_all=true`，因此任何持有二维码/配对载荷的人都能以 Mac 用户身份执行命令。`host.key` / `host.toml`（令牌）是权限为 0600 的明文文件，不在 Keychain 中。当前缓解措施：在 `host.toml`（桌面 Agents 页面）中禁用 agent 或将这些开关设为 false，并在任何泄露后轮换令牌。正确的修复方案（按设备配对）应在 `AkaShark/alleycat` fork 中实现。
- **iOS 上的 ChatGPT OAuth：** 仅移除了登录按钮（`[baozi-fork]`）。`ChatGPTOAuth.swift` 及其入口（`AppModel.ensureLocalAuthForThreadStart` → `loginLocalChatGPTAccount`、`SettingsView`、`AccountView`）仍需保留，因为 Slingshot 远程控制和基于 ChatGPT 的语音转录依赖 ChatGPT 令牌。除非移除这些功能，否则不要删除。
- **iOS 本地 iSH 已禁用：** `AgentBuddyPlatform.swift` 中设置了 `ishDisabledForFork = true`（iSH 因 "invalid vdso" 触发 SIGABRT）。Rust `ish` feature 已关闭，iOS 不打包 Alpine fs，但有意保留 `IshFS` / `ishDefaultCwd` 调用点，以便将来重新启用。因此，iOS 上的 "This Device" / `localIsh` 终端不可用。
- **延后重构（已记录，尚未完成）：** Swift/Kotlin 的 `AppModel` 仍重复实现了一些应迁移到 Rust 的 reducer 逻辑（流式增量合并、`preserveStreamingText`、插入索引）；Android 仍在加载 `codex_mobile_client` 的同时加载旧版 `codex_bridge` `.so`（`nativeBridgeInit`），应将其合并到 `codex-mobile-client`。
