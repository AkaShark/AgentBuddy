# AgentBuddy 桌面主机 App

macOS 菜单栏应用（Tauri v2 + React/TS）。把 `agentbuddy` 守护进程作为 sidecar 打包，
以 LaunchAgent `com.akashark.agentbuddycli` 安装运行，并提供概览 / Agents / 配对 / 日志四页控制台。
设计见 `docs/superpowers/specs/2026-09-23-desktop-host-app-design.md`，实现过程中的取舍见
`docs/superpowers/plans/2026-09-23-desktop-host-app.decisions.md`。

## 开发

```bash
make desktop-sidecar      # 编 services/kittylitter 并放进 src-tauri/binaries/
make desktop-dev          # tauri dev（前端 http://localhost:1420）
cd apps/desktop && npm test && (cd src-tauri && cargo test --lib)
```

前置：Node 22、rustup 的 stable 工具链、在 `apps/desktop` 下 `npm ci`。首次 `make desktop-sidecar`
会编译 alleycat，约几分钟。本机若设置了 HTTP 代理而 npm 报网络错误，可临时 `env -u https_proxy -u http_proxy npm ci`。

本地出 dmg 时，Tauri 的 `bundle_dmg.sh` 会用 AppleScript 让 Finder 摆放窗口；若终端没有「自动化 → Finder」权限会报
`error running bundle_dmg.sh`，可以在系统设置里授权，或用 `CI=true npm run tauri build` 跳过这一步（CI 上本就会跳过）。

契约测试（跑真实守护进程二进制）：

```bash
cd apps/desktop/src-tauri
AGENTBUDDY_SIDECAR="$PWD/binaries/agentbuddy-$(rustc --print host-tuple)" cargo test --test sidecar_contract
"$PWD/binaries/agentbuddy-$(rustc --print host-tuple)" stop   # pair 会顺带拉起一个守护进程
```

测试按 `pair` → `status --json` 顺序检查同一个守护进程，每条命令限时 90 秒。
CI 将停止守护进程放在独立的 `always()` 步骤中，测试失败也会清理。

## 发布

这是项目唯一的 macOS 应用。旧 iOS Catalyst target 与发布流水线已移除。
桌面包标识为 `com.akashark.agentbuddy.host`，当前分发方式是 Developer ID 签名、公证后的 DMG；
现有构建配置未提供 Mac App Store / TestFlight 上传流程。

```bash
APPLE_SIGNING_IDENTITY="Developer ID Application: … (HNKUYWPBVC)" \
APPLE_API_KEY=<key id> APPLE_API_ISSUER=<issuer id> \
APPLE_API_KEY_PATH=~/.appstoreconnect/private_keys/AuthKey_<key id>.p8 \
make desktop-dist
```

CI：打 `desktop-vX.Y.Z` tag 触发 `.github/workflows/desktop-release.yml`，产出 arm64 与 x86_64 两个 dmg
到同一个 GitHub Releases 草稿。配置了 `MAC_DEVELOPER_ID_CERT_*` 与 `ASC_*` secrets 时会签名并公证，否则产出未签名包。
构建签名与公证分开执行：`scripts/notarize-dmg.sh` 提交 DMG 后立即保存 submission ID，
最多等待 60 分钟，仅在 `Accepted` 后 staple、validate 并上传 Release 草稿。
失败或仍为 `In Progress` 时，DMG 和诊断 JSON/log 仍保存在 Actions 的
`desktop-<target>` artifact；这种 DMG 不代表已通过公证，不会上传 Release 草稿。
Apple 超时后仍会继续处理，可用保存的 ID 执行 `xcrun notarytool info <id>` / `log <id>`
（带同一套认证参数）检查结果；确认 Accepted 后可对保留的 DMG 执行 `xcrun stapler staple` / `validate`。

## 系统代理与完成通知

macOS 主机的 HTTP 请求支持系统设置中的 HTTP/HTTPS 代理，无需把终端里的
`HTTPS_PROXY` 写进 LaunchAgent plist。显式代理环境变量仍按 reqwest 的规则生效。
此支持针对静态 HTTP/HTTPS 代理，不包含 PAC 自动代理脚本或系统 SOCKS-only 配置。

推送连接在下次发送时最多复用 30 秒，之后重新读取代理配置；网络失败也会让下一次
重试重建连接。重试时间仍受持久化队列的退避和服务端 `Retry-After` 控制，所以切换
代理不代表通知会立即重发。OpenCode、Hermes 和本地推送测试地址的回环连接始终直连。

排查时使用 sidecar 的 `push status --json` 查看订阅数、队列深度、最近成功和错误。
Worker 根路径返回 404 只能验证网络连通；完整验收仍需新回合触发的订阅、事件投递及
手机收到通知。队列超过 24 小时的项目和失效订阅不会无限补发。

## 目录

- `src/` React 前端；`src/lib/host.ts` 是唯一的 `invoke` 入口。
- `src-tauri/src/` Rust 后端：`sidecar.rs` 是唯一执行守护进程 CLI 的地方，子命令白名单在 `Subcommand`；
  `shellenv.rs` 给 sidecar 注入用户登录 shell 的 PATH；`launchd.rs` 推导安装状态；`config.rs` 编辑 host.toml；
  `tray.rs` 托盘菜单；`commands.rs` 是暴露给前端的 Tauri 命令。
- `scripts/build-sidecar.sh`、`scripts/sync-version.mjs`。
- `docs/qa.md` 手工验收清单。
