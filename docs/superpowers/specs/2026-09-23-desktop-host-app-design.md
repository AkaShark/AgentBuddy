# AgentBuddy 桌面主机 App 设计（Tauri v2）

日期：2026-09-23
状态：设计已逐节确认，待实现计划

## 1. 目标与非目标

**目标**

- 一个独立的原生 macOS 应用 `AgentBuddy.app`，定位「主机 + 轻量控制台」：常驻菜单栏，负责安装、运行、监控本机的 `agentbuddy` 守护进程，并提供配对二维码。
- 替代 `npx agentbuddycli` 这条命令行安装路径。用户下载 dmg，拖进 Applications，首次启动点一次「安装后台服务」，用手机 App 扫码即可连上。
- 退出 App、重启 Mac 之后手机仍能连上这台 Mac：守护进程作为 LaunchAgent 独立于 App 存活，App 只是它的控制台。

**非目标（本期）**

- 不做聊天界面。会话、审批、终端仍在手机 App 上。
- 不上 Mac App Store。守护进程要拉起 codex / claude 等 CLI 并监听端口，沙盒做不到；只做 Developer ID 签名加公证的 dmg。
- 不改 alleycat 协议和守护进程行为。「已配对设备」「正在跑的会话」是守护进程今天不提供的数据，放到第二阶段。
- 不做 npm 发布，也不再用 cargo-dist。

**成功标准**

1. 全新 Mac 上：安装 dmg → 首启安装服务 → 手机扫码配对成功。
2. 退出 App 后、重启 Mac 后，手机仍能连接并正常使用 agent。
3. 控制台能看到守护进程状态、agent 可用性并能开关 agent、能重新生成配对码、能看日志。
4. CI 能在打 tag 时产出已签名公证的 arm64 与 x86_64 两个 dmg。

## 2. 背景事实（决定设计的约束）

- 守护进程 `agentbuddy` 是 `services/kittylitter` 编出的二进制，内部就是 alleycat crate 的 `App { binary_name: "agentbuddy", organization: "akashark", application: "agentbuddycli", label: "com.akashark.agentbuddycli" }.run()`。
- alleycat 的模块全部私有，crate 只公开 `App`、`binary_name`、`binary_version`。App 侧不能 `use alleycat::daemon::control`。
- alleycat 的 CLI 已经提供控制台需要的输出：
  - `status --json` 打印 `StatusInfo { pid, node_id, token_short, relay, config_path, uptime_secs, agents: [AgentInfo { name, display_name, wire, available, presentation?, capabilities? }] }`；守护进程没跑时 pid 与 uptime 为 0，其余字段仍填。
  - `pair` 在 stdout 打印一行 `PairPayload { v, node_id, token, host_name?, relay? }` JSON，`--qr` 额外打印 ASCII 二维码。手机扫的就是这段 JSON。
  - `install` / `uninstall` 用 `std::env::current_exe()` 生成 `~/Library/LaunchAgents/com.akashark.agentbuddycli.plist`（RunAtLoad、KeepAlive、日志重定向）并 bootstrap 到 `gui/$UID`，无需管理员。
  - `stop` / `restart` / `reload` / `rotate` / `upgrade` / `logs -n N [-f]`。
- 目录约定（`directories::ProjectDirs::from("com", "akashark", "agentbuddycli")`）：配置 `~/Library/Application Support/com.akashark.agentbuddycli/host.toml`，日志 `~/Library/Logs/com.akashark.agentbuddycli/`，控制 socket `$TMPDIR/alleycat-<hash>/control.sock`。
- `host.toml` 里每个 agent 都是 `{ enabled: bool, bin: String, ... }`，codex 另有 `host` / `port`。
- Tauri v2 官方支持：`bundle.externalBin` sidecar（按 `rustc --print host-tuple` 命名 `agentbuddy-<triple>`，通过 shell 插件的 `sidecar()` 执行）、原生托盘 `TrayIconBuilder` 与菜单、`setDockVisibility(false)` / Accessory 激活策略、`tauri build` 内置 macOS 签名与公证（`APPLE_SIGNING_IDENTITY`、`APPLE_CERTIFICATE*`、`APPLE_API_KEY*`）、`tauri-apps/tauri-action`。

## 3. 架构与组件

三个运行单元：

1. **守护进程 `agentbuddy`**（已有，零改动）。作为 sidecar 打进 App 包，位于 `Contents/MacOS/agentbuddy`。以 LaunchAgent 身份运行 `agentbuddy serve`，拥有 iroh 节点身份、配对 token、控制 socket。目录约定不变。
2. **`AgentBuddy.app`**（新，Tauri v2）。`apps/desktop/src-tauri` 为 Rust 后端，`apps/desktop/src` 为 React + TypeScript 前端。原生托盘图标加菜单；一个控制台窗口（WebView）；无 Dock 图标；关闭窗口只是隐藏，不退出。bundle id `com.akashark.agentbuddy.host`。LaunchAgent label 沿用守护进程自己的 `com.akashark.agentbuddycli`，使命令行 `agentbuddy install` 与 App 管理同一个服务。
3. **手机 App、移动端 Rust crate、alleycat 均不改。**

**后端如何与守护进程交互**：只通过 sidecar 的 CLI。后端用 shell 插件的 `sidecar("binaries/agentbuddy")` 启动子进程，解析 stdout 上由守护进程自身 serde 类型序列化出的 JSON。这个契约由「App 与 sidecar 出自同一 commit、同一个包」保证，不存在版本错位。不链接 alleycat crate 的原因：模块私有；会把 iroh 整套依赖拖进 App；`install()` 以 `current_exe()` 写 plist，由 sidecar 自己执行才会指向包内路径。

**仓库布局**

```
apps/desktop/
  package.json               Vite + React + TS，@tauri-apps/cli 作 devDependency
  src/                       前端：App.tsx、pages/{Overview,Agents,Pairing,Logs}.tsx、lib/host.ts（invoke 封装与类型）
  src-tauri/
    Cargo.toml               独立 workspace，不并入 shared/rust-bridge
    tauri.conf.json          bundle.externalBin = ["binaries/agentbuddy"]，macOS 目标 14.0
    capabilities/default.json
    binaries/agentbuddy-<triple>   gitignore，由 scripts/build-sidecar.sh 生成
    icons/
    src/{main.rs, tray.rs, state.rs, sidecar.rs, commands.rs, config.rs, logs.rs, error.rs}
  scripts/build-sidecar.sh   cargo build --release --manifest-path services/kittylitter/Cargo.toml --target <triple>，拷入 binaries/
  scripts/sync-version.sh    把 services/kittylitter/Cargo.toml 的 version 写进 tauri.conf.json
  docs/qa.md                 手工 QA 清单
```

Makefile 新增 `desktop-sidecar`、`desktop-dev`、`desktop-build`、`desktop-dist`。

**数据流**：拉模式。前端 `invoke` → Rust command → 启动 sidecar 子进程 → 解析 JSON → 强类型返回。窗口可见时 2 秒轮询一次 `host_state`，隐藏时 30 秒。托盘图标与首行菜单随状态变化。日志页的「跟随」用 `logs -f` 子进程，每行作为 Tauri 事件 `log-line` 推给前端。

## 4. 守护进程生命周期与安装

后端由三个事实推导状态：plist `~/Library/LaunchAgents/com.akashark.agentbuddycli.plist` 是否存在；plist 内 `ProgramArguments[0]` 是否等于本包 sidecar 的绝对路径；`status --json` 的 pid 是否大于 0。

| 状态 | 条件 | 控制台表现 |
|---|---|---|
| `NotInstalled` | 无 plist | 引导卡片覆盖整页，唯一动作「安装后台服务」 |
| `Installed { running: false }` | 有 plist、路径匹配、pid = 0 | 提供「启动」（sidecar `restart`） |
| `Installed { running: true }` | 有 plist、路径匹配、pid > 0 | 正常控制台 |
| `PathMismatch { plist_exe }` | 有 plist 但路径不等于本包 sidecar | 横幅「服务指向旧版本或旧位置，点此修复」→ 重新 `install` 覆盖 plist → `restart` |

规则：

- 「安装」= sidecar `install`；成功后轮询到 pid > 0 即跳到配对页。
- 「开机自启」开关的勾选态就是「已安装」，勾选 = `install`，取消 = `uninstall`（同时停止服务，需确认）。
- 退出 App 永远不停守护进程。「停止主机服务」是托盘里独立一项，走 `stop`，需确认。
- App 升级后包路径不变、plist 仍有效，但运行中的仍是旧二进制。App 每次启动比较自身版本与上次记录（存 app data），变化时执行 sidecar `upgrade`，把运行中的守护进程切到本二进制。
- 移动 App 触发 `PathMismatch`，由用户点修复；不自动改动 plist。
- 会改变服务状态的命令（install / uninstall / stop / restart / upgrade / rotate）在后端用一把互斥锁串行。

## 5. Rust 后端命令

所有命令返回 `Result<T, HostError>`，`T` 为可序列化 struct，前端在 `src/lib/host.ts` 里有对应 TypeScript 类型。

| 命令 | sidecar 调用 | 返回 |
|---|---|---|
| `host_state()` | 读 plist + `status --json` | `HostState { install: InstallState, running: bool, status: Option<StatusInfo>, app_version: String, sidecar_path: String }` |
| `host_install()` / `host_uninstall()` | `install` / `uninstall` | `()` |
| `host_start()` / `host_restart()` | `restart` | `()` |
| `host_stop()` | `stop` | `()` |
| `host_reload()` | `reload` | `()` |
| `host_upgrade()` | `upgrade` | `()` |
| `pair_payload()` | `pair` | `PairPayload { raw: String, node_id, token, host_name, relay }`，`raw` 用于二维码 |
| `rotate_token()` | `rotate` | `()` |
| `agent_set_enabled(name, enabled)` | 改 `host.toml` 后 `reload` | `()` |
| `agent_set_bin(name, path)` | 改 `host.toml` 后 `reload` | `()` |
| `logs_tail(lines)` | `logs -n N` | `Vec<String>` |
| `logs_follow_start()` / `logs_follow_stop()` | `logs -f` 子进程 | 事件 `log-line { line }` |
| `reveal_path(kind)` | opener 插件 | `()`，kind ∈ { config, logs } |

`host.toml` 的写入规则：读取原文用 `toml_edit` 修改指定 agent 表的 `enabled` / `bin`，写临时文件后原子替换；首次写入前备份为 `host.toml.bak`；解析失败则拒绝写入并报错。agent 列表本身取自 `status.agents[]`，不单独跑 `agents list`。

**Tauri capabilities**：shell 仅允许执行该 sidecar，且子命令限定为上表出现的集合；另开 dialog（选文件）、clipboard、opener、positioner。不开任何 fs 或 http 权限给前端。

## 6. 托盘与控制台 UI

**托盘菜单**（原生）

1. 只读状态行：运行中 / 已停止 / 未安装 / 需要修复
2. 打开控制台
3. 显示配对二维码（打开窗口并切到配对页）
4. 启动主机服务 或 停止主机服务（按状态二选一）
5. 开机自启（勾选 = 已安装）
6. 退出 AgentBuddy（只退 App；首次退出提示「守护进程会继续运行」）

左键点击图标切换窗口显示；窗口用 positioner 贴在图标下方。

**控制台四页**（React，暗色主题，沿用 iOS `agentbuddy-dark` 配色、`#00FF9C` 强调色、等宽字体）

1. **概览**：状态徽章；节点 id（可复制）；relay；运行时长；守护进程版本与 App 版本；配置与日志路径各带「在 Finder 中显示」；启动 / 停止 / 重启 / 重载。`NotInstalled` 时整页为引导卡片。
2. **Agents**：表格，每行 display name、是否找到可执行文件、启用开关、bin 路径输入框加「选择…」；codex 的 host / port 在可折叠高级项中。改动即保存并 reload，行内显示结果。
3. **配对**：大二维码（`raw` 编码）、主机名、节点 id 缩写、token 指纹；「复制 payload」「轮换 token」。轮换前确认框说明所有手机需重新扫码。操作指引一句：手机 App → 添加服务器 → 扫码。
4. **日志**：默认最近 500 行；「跟随」开关；文本过滤；「打开日志目录」。

## 7. 错误处理

后端统一错误类型：

```rust
enum HostErrorKind { SidecarMissing, CommandFailed { code: Option<i32>, stderr: String }, ParseFailed, PermissionDenied, ConfigInvalid }
struct HostError { kind: HostErrorKind, detail: String }
```

- 前端在页面顶部横幅展示错误并提供「复制诊断信息」（含命令、退出码、stderr、App 与守护进程版本）。托盘和窗口永不因错误崩溃。
- `status --json` 在守护进程未运行时 pid 为 0，按「已停止」处理，不是错误。
- `install` 失败显示 stderr，并提示前往「系统设置 → 通用 → 登录项」检查。
- 轮询连续失败时退避到 30 秒，状态徽章改为「无法连接控制通道」。
- `logs -f` 子进程在窗口隐藏或切页时停止，避免后台常驻。

## 8. 构建、签名、CI

- **Sidecar**：`scripts/build-sidecar.sh [triple]`，默认 `rustc --print host-tuple`；产物 `src-tauri/binaries/agentbuddy-<triple>`。本地开发只编 arm64。
- **版本**：`scripts/sync-version.sh` 把 `services/kittylitter/Cargo.toml` 的 version 写进 `tauri.conf.json`，App 与守护进程版本号恒等。
- **本地签名公证**：`APPLE_SIGNING_IDENTITY="Developer ID Application: … (HNKUYWPBVC)"`，`APPLE_API_KEY` / `APPLE_API_ISSUER` / `APPLE_API_KEY_PATH` 用 App Store Connect API key；`tauri build` 一并签 App 与 sidecar、公证、staple。Hardened Runtime 开，不沙盒，entitlements 最小集。需要为 HNKUYWPBVC 新签发 Developer ID Application 证书，现有 `MAC_DEVELOPER_ID_CERT_*` secret 的值属于旧 team。
- **CI**：`.github/workflows/desktop-release.yml`，`tauri-apps/tauri-action`，矩阵 `aarch64-apple-darwin` 与 `x86_64-apple-darwin`，各出一个 dmg。证书从 `MAC_DEVELOPER_ID_CERT_P12_B64` / `MAC_DEVELOPER_ID_CERT_PASSWORD` 导入，公证用 `ASC_KEY_ID` / `ASC_ISSUER_ID` / `ASC_PRIVATE_KEY_P8_B64`；secret 名沿用、值换新。tag `desktop-v*` 触发并发布到 GitHub Releases。

## 9. 测试

- **Rust 单测**：状态机推导（三输入到四状态全覆盖）；用真实 CLI 抓取的 `status --json` 与 `pair` fixture 做解析测试；`toml_edit` 修改后注释与排版保持；退出码 / stderr 到 `HostError` 的映射。
- **Sidecar 契约测试**（CI macOS job）：编出 sidecar，在无守护进程环境跑 `status --json` 与 `pair`，断言可解析。alleycat pin 变动导致 JSON 形状变化时此处先红。
- **前端**：Vitest + React Testing Library，四页对 mock 的 `invoke` 运行。
- **手工 QA**（`apps/desktop/docs/qa.md`）：全新安装 → 扫码 → 退出 App 仍可连 → 重启 Mac 仍可连 → 移动 App 触发修复 → 轮换 token 后旧手机被拒。

## 10. 阶段划分

- **阶段 0，清理**：删 `dist-workspace.toml`、`.github/workflows/release.yml`、`.github/workflows/auto-release.yml`、`.github/dist-build-setup.yml`、`services/kittylitter/wix/`；`NPM_TOKEN` 不再需要。保留 `services/kittylitter` crate 作为 sidecar 源码。README「Mac 端守护进程」段改写为桌面 App 安装说明。
- **阶段 1，桌面 App**：第 3 至 9 节全部内容，产出本地 dev 版与 CI 签名 dmg。
- **阶段 1.5，App 可用后的清理**：删 `AgentBuddyMac` Catalyst target、`apps/ios/scripts/direct-dist-mac.sh`、`testflight-upload-mac.sh`、`mac-direct-dist.yml`、`mac-testflight.yml` 及仅在 Catalyst 下编译的 Swift 文件（`MacPairingHost`、`LocalCodexBootstrap`、`CatalystRuntimeStubs`、`MacCommands`）。随之消失的是 Catalyst 独有的「iPhone 靠近 Mac 自动配对」，手机端对应的 `NearbyMacPairing` 入口一并移除，桌面统一走扫码。
- **阶段 2，需改 alleycat fork**：Status 增加已连接设备与会话；开一个公共 API；控制台加「设备」「会话」两页；Tauri updater 自动更新；评估 Linux / Windows 构建。

## 11. 决策记录

| 决策 | 选择 | 放弃的选项与原因 |
|---|---|---|
| UI 技术 | Tauri v2 + React + TS | SwiftUI 原生：需要新建 UniFFI crate 与 macOS Rust 构建 lane，且锁死 Mac；Tauri 后端即 Rust，跨平台白送 |
| 与守护进程交互 | sidecar CLI + JSON | 链接 alleycat crate：模块私有、拖入 iroh 依赖、`install()` 路径指向错误；纯 Swift 调 CLI：与「协议解析放 Rust」原则相悖 |
| 守护进程寿命 | LaunchAgent 独立存活 | 进程内运行：与「退出 App 仍可连」冲突 |
| 分发 | Developer ID dmg，arm64 与 x86_64 各一 | universal 包：sidecar 需 lipo，多一道工序；App Store：沙盒不可行 |
| bundle id | `com.akashark.agentbuddy.host` | 复用 `com.akashark.agentbuddy`：与手机 App 和现存 Catalyst target 冲突 |
| LaunchAgent label | `com.akashark.agentbuddycli` | 新 label：会与命令行 `agentbuddy install` 产生两个服务 |

## 12. 开放事项

- HNKUYWPBVC 的 Developer ID Application 证书与 App Store Connect API key 由仓库所有者签发并写入 secrets。
- Intel 机器上的手工 QA 需要一台 x86_64 Mac 或 CI 产物人工验证。
- 阶段 2 的 alleycat 公共 API 形状（连接跟踪、会话列表）另行设计。
