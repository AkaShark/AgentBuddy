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

契约测试（跑真实守护进程二进制）：

```bash
cd apps/desktop/src-tauri
AGENTBUDDY_SIDECAR="$PWD/binaries/agentbuddy-$(rustc --print host-tuple)" cargo test --test sidecar_contract
"$PWD/binaries/agentbuddy-$(rustc --print host-tuple)" stop   # pair 会顺带拉起一个守护进程
```

## 发布

```bash
APPLE_SIGNING_IDENTITY="Developer ID Application: … (HNKUYWPBVC)" \
APPLE_API_KEY=<key id> APPLE_API_ISSUER=<issuer id> \
APPLE_API_KEY_PATH=~/.appstoreconnect/private_keys/AuthKey_<key id>.p8 \
make desktop-dist
```

CI：打 `desktop-vX.Y.Z` tag 触发 `.github/workflows/desktop-release.yml`，产出 arm64 与 x86_64 两个 dmg
到 GitHub Releases 草稿。配置了 `MAC_DEVELOPER_ID_CERT_*` 与 `ASC_*` secrets 时会签名并公证，否则产出未签名包。

## 目录

- `src/` React 前端；`src/lib/host.ts` 是唯一的 `invoke` 入口。
- `src-tauri/src/` Rust 后端：`sidecar.rs` 是唯一执行守护进程 CLI 的地方，子命令白名单在 `Subcommand`；
  `shellenv.rs` 给 sidecar 注入用户登录 shell 的 PATH；`launchd.rs` 推导安装状态；`config.rs` 编辑 host.toml；
  `tray.rs` 托盘菜单；`commands.rs` 是暴露给前端的 Tauri 命令。
- `scripts/build-sidecar.sh`、`scripts/sync-version.mjs`。
- `docs/qa.md` 手工验收清单。
