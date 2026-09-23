# AgentBuddy（搭子）

<p align="center">
  <img src="apps/ios/Sources/AgentBuddy/Resources/brand_logo.png" alt="AgentBuddy logo" width="180" />
</p>

<p align="center">
  <b>iOS + Android 原生 App，遥控跑在你自己 Mac 上的 AI 编程 agent。</b><br/>
  支持 <a href="https://github.com/openai/codex">Codex</a>、Claude Code、Pi、OpenCode 等。Mac 端只跑一个轻量守护进程，
  手机通过端到端加密的 P2P 链路配对，不经过任何托管中继，也不需要账号。
</p>

<p align="center">
  <a href="https://github.com/AkaShark/AgentBuddy">GitHub</a>
  &nbsp;·&nbsp;
  <a href="https://github.com/AkaShark/AgentBuddy/releases/latest">Releases</a>
  &nbsp;·&nbsp;
  <a href="docs/DEVELOPMENT.md">开发文档</a>
</p>

## 工作原理

AgentBuddy 分三层，手机 App 只做「遥控器」，真正的 agent 在你的 Mac 上运行：

```
┌──────────────────────────────┐
│  手机 App（iOS SwiftUI / Android Compose）
│  只负责 UI、平台权限、通知、音频等平台能力
└──────────────┬───────────────┘
               │ UniFFI 生成的 Swift / Kotlin 绑定
┌──────────────▼───────────────┐
│  Rust 共享核心  shared/rust-bridge/codex-mobile-client
│  会话/线程状态、流式渲染、hydration、审批、账号、发现、SSH、语音转写
└──────────────┬───────────────┘
               │ 端到端加密 P2P（Iroh）/ 局域网 / SSH
┌──────────────▼───────────────┐
│  Mac 桌面 App 内置的守护进程 agentbuddy（alleycat 的品牌封装，services/kittylitter）
│  把本机 agent 多路复用给已配对的手机
└──────┬───────────┬───────────┬───────────┬──────┘
       │           │           │           │
   Codex      Claude Code      Pi       OpenCode …
 (app-server   (bridge 翻译   (bridge)   (bridge)
  协议直通)     成同一协议)
```

- **Codex** 走 app-server 协议直通：守护进程把 `codex app-server` 的 JSON-RPC 原样转给手机，Rust 核心直接复用上游 `codex-app-server-protocol`。
- **Claude Code、Pi、OpenCode 等其他 agent** 由 alleycat 里对应的 bridge（`alleycat-claude-bridge`、`alleycat-pi-bridge`、`alleycat-opencode-bridge`）翻译成同一套协议，手机端不需要知道差别。
- 两端共用一个 Rust 核心，Swift/Kotlin 保持很薄：状态机、合并策略、协议解析都不在平台层重复实现。

## 快速开始

### 手机 App

```bash
make ios-device-fast          # iOS 真机快速构建（raw staticlib）
make ios-sim-fast             # iOS 模拟器快速构建
make android-emulator-fast    # Android 模拟器快速构建
```

### Mac 端

下载 [Releases](https://github.com/AkaShark/AgentBuddy/releases) 里的 `AgentBuddy_<版本>_<架构>.dmg`，拖进 Applications 后打开。
首次启动在菜单栏图标 → 打开控制台 → 「安装后台服务」，随后在「配对」页用手机 App 扫码。守护进程作为
LaunchAgent 独立运行，退出 App 或重启 Mac 后手机仍能连接。终端用户也可以直接调用包内的守护进程：

```bash
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr
```

从源码运行桌面 App：`make desktop-dev`，详见 [apps/desktop/README.md](apps/desktop/README.md)。

App 会自动在局域网发现守护进程，也可以手动配对或通过 SSH 引导。自带 API key 即可，没有托管登录。

首次构建前的环境要求（Xcode、rustup、xcodegen、Android SDK/NDK/JDK 17）、完整构建选项、TestFlight / App Store 发布和 SSH 配置见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。仓库约定见 [AGENTS.md](AGENTS.md)。

## 仓库布局

```
apps/ios/                      iOS / watchOS / Mac Catalyst App（AgentBuddy scheme，project.yml 是唯一真源）
apps/android/                  Android App（Compose UI，Gradle 构建，包名 com.akashark.agentbuddy.android）
apps/desktop/                  macOS 菜单栏主机 App（Tauri v2 + React/TS）
shared/rust-bridge/
  codex-mobile-client/         两端共用的 Rust 客户端 crate + UniFFI 公共面
  codex-slingshot/             JSON-line / websocket 传输适配
  codex-bridge/                旧的 C-FFI 支持层（不用于新功能）
  uniffi-bindgen/              绑定生成工具
shared/third_party/codex/      上游 Codex 子模块
shared/third_party/ghostty/    上游 Ghostty 子模块（终端渲染）
patches/codex/, patches/ghostty/  构建时应用的本地补丁
services/kittylitter/          Mac 守护进程二进制 agentbuddy（alleycat 封装，作为桌面 App 的 sidecar）
services/push-proxy/           APNs / FCM 推送代理（Cloudflare Worker）
tools/scripts/                 跨平台辅助脚本
docs/                          开发、架构与发布文档
```

## 截图

截图区暂留占位。后续会补上 iOS / Android 的首页、远程会话、多 agent 切换与配对流程截图，
放在 `docs/screenshots/` 下并在这里引用。

## 致谢与许可

AgentBuddy 采用 **GNU GPLv3**，并依据 GPLv3 第 7 节附加了 Apple App Store / Google Play 分发例外，见 [LICENSE](LICENSE)。

衍生链：

**AgentBuddy** ← [包子 / Baozi](https://github.com/huangguang1999/baozi) ← [litter](https://github.com/dnakov/litter) + [alleycat](https://github.com/dnakov/alleycat)（作者 [@dnakov](https://github.com/dnakov)）

- [litter](https://github.com/dnakov/litter) 是最初的手机端 App 与 `kittylitter` Mac 守护进程，GPLv3。
- [alleycat](https://github.com/dnakov/alleycat) 是 P2P 传输与各 agent bridge，GPLv3。AgentBuddy 当前使用
  [AkaShark/alleycat](https://github.com/AkaShark/alleycat) fork，固定在与上游相同的 commit。
- [包子 / Baozi](https://github.com/huangguang1999/baozi) 是 litter 的中文化换皮 fork，GPLv3；AgentBuddy 由它衍生而来。

源码文件头中保留的上游版权声明均为原作者所有。

## Make Targets

| Target | Description |
|---|---|
| `make ios-device-fast` | Fast device build (raw staticlib) |
| `make ios-sim-fast` | Fast simulator build |
| `make ios` | Full package lane (device + sim + xcframework) |
| `make android-emulator-fast` | Fast Android emulator build |
| `make android` | Full Android pipeline |
| `make rust-check` | Host `cargo check` for shared Rust crates |
| `make rust-test` | Host `cargo test` for shared Rust crates |
| `make bindings` | Regenerate UniFFI Swift + Kotlin bindings |
| `make xcgen` | Regenerate Xcode project from `project.yml` |
| `make watch-register` | Register a newly paired Apple Watch with the developer portal (idempotent) |
| `make clean` | Remove all build artifacts |
