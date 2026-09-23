# AgentBuddy 桌面主机 App 实现计划（阶段 0 + 阶段 1）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个独立的 macOS 菜单栏应用 `AgentBuddy.app`（Tauri v2 + React/TS），它把 `agentbuddy` 守护进程作为 sidecar 打包、以 LaunchAgent 安装运行，并提供四页控制台（概览 / Agents / 配对 / 日志）；同时拆掉 cargo-dist 的 npm 发布链。

**Architecture:** 守护进程零改动，作为 `Contents/MacOS/agentbuddy` sidecar 随包分发；Tauri 的 Rust 后端只通过 sidecar 自己的 CLI（`status --json`、`pair`、`install`…）与守护进程交互并解析其 serde JSON；React 前端只调 `invoke`。LaunchAgent 由 sidecar 的 `install` 写入，独立于 App 存活。

**Tech Stack:** Tauri 2（tray-icon）、tauri-plugin-shell / dialog / clipboard-manager / opener / positioner、serde、serde_json、toml_edit、plist、thiserror；Vite、React 19、TypeScript 5、qrcode.react、Vitest + React Testing Library；Node 22；Rust stable（rustup）。

**Spec:** `docs/superpowers/specs/2026-09-23-desktop-host-app-design.md`

## Global Constraints

- macOS 最低版本 `14.0`（`bundle.macOS.minimumSystemVersion`）。
- App bundle id `com.akashark.agentbuddy.host`；productName `AgentBuddy`；LaunchAgent label `com.akashark.agentbuddycli`（沿用守护进程）。
- sidecar 文件名 `agentbuddy-<target-triple>`，配置为 `bundle.externalBin: ["binaries/agentbuddy"]`；源码是 `services/kittylitter`，不新建 Rust crate，不 `use alleycat::…`。
- 后端与守护进程只通过 sidecar CLI 交互；允许的子命令集合固定为：`status --json`、`pair`、`install`、`uninstall`、`stop`、`restart`、`reload`、`rotate`、`upgrade`、`logs -n N`、`logs -f -n N`、`--version`。
- 前端 capability 只开 `core:default`、`dialog:allow-open`、`clipboard-manager:allow-write-text`；不给 webview 任何 shell / fs / http 权限（比 spec 更紧：sidecar 只由 Rust 调用，webview 不需要 shell 权限）。
- `host.toml` 只用 `toml_edit` 修改，写临时文件后原子替换，首次写前备份 `host.toml.bak`，解析失败拒绝写入。
- 退出 App 不停止守护进程；停止是独立菜单项，需确认。
- UI 文案中文；暗色主题：背景 `#000000`、前景 `#E6E6E6`、强调 `#00FF9C`、次要 `#8A8A8A`、危险 `#FF5F57`、字体 `"SF Mono", ui-monospace, Menlo, monospace`。
- `tauri.conf.json` 的 `version` 由 `apps/desktop/scripts/sync-version.mjs` 从 `services/kittylitter/Cargo.toml` 同步；App 与守护进程版本恒等。
- 每个任务单独 commit；commit 主题祈使句加 scope，结尾 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`。
- 不改 `patches/`、`shared/third_party`、`shared/rust-bridge`、手机 App 的业务逻辑；Task 13 只改文案。
- 所有 `cargo` 命令先 `export PATH="$HOME/.cargo/bin:$PATH"`；`apps/desktop/src-tauri` 是独立 workspace（`[workspace]` 空表）。

## Review Focus

1. App 从 dmg 直接运行（App Translocation）或通过符号链接启动时，plist 里的程序路径与 sidecar 路径字面不同但指向同一文件；期望判定为 `Installed` 而不是 `PathMismatch`。→ Task 6 `derive_install_state_treats_symlink_as_match`。
2. 守护进程升级后 `status --json` 多出未知字段、`wire` 出现新枚举值；期望解析仍成功。→ Task 5 `parse_status_tolerates_unknown_fields`。
3. `host.toml` 里没有 `[agents.<name>]` 表或文件为空；期望开关仍能写入并保留其余内容。→ Task 7 `set_agent_enabled_creates_missing_table`。
4. `pair` 的 stdout 前面混入 tracing 警告行；期望取到 JSON 行。→ Task 5 `parse_pair_skips_non_json_lines`。
5. 用户连点两次「安装」或在重启中点停止；期望第二次排队而不是并发执行。→ Task 8 `mutating_commands_are_serialized`。

## 文件结构

```
apps/desktop/
  package.json  vite.config.ts  tsconfig.json  index.html  .gitignore
  src/
    main.tsx  App.tsx  App.css  theme.css
    lib/host.ts            invoke 封装、类型、useHostState 轮询 hook
    lib/host.test.ts
    components/ErrorBanner.tsx  StatusBadge.tsx  Nav.tsx
    pages/Overview.tsx  Agents.tsx  Pairing.tsx  Logs.tsx（各带 .test.tsx）
    test/setup.ts
  src-tauri/
    Cargo.toml  build.rs  tauri.conf.json  entitlements.plist
    capabilities/default.json
    icons/                 由 `npm run tauri icon` 生成
    binaries/              gitignore；build-sidecar.sh 输出
    src/main.rs            调 lib::run()
    src/lib.rs             Builder、插件、命令注册、窗口事件
    src/error.rs           HostError / HostErrorKind
    src/sidecar.rs         Subcommand 枚举、RawOutput、classify、run
    src/status.rs          StatusInfo / AgentInfo / PairPayload 与解析
    src/launchd.rs         plist 读取、InstallState、状态机
    src/config.rs          toml_edit 修改与原子写
    src/logs.rs            logs -n / logs -f 事件流
    src/state.rs           AppState（互斥锁、follow 子进程、settings.json）
    src/commands.rs        #[tauri::command] 集合
    src/tray.rs            托盘菜单与窗口显隐
    tests/sidecar_contract.rs  由 AGENTBUDDY_SIDECAR 环境变量门控
  scripts/build-sidecar.sh  scripts/sync-version.mjs
  docs/qa.md
.github/workflows/desktop-release.yml
Makefile（新增 desktop-* 目标）、README.md、AGENTS.md、.gitignore
```

---

### Task 1: 拆除 cargo-dist / npm 发布链（阶段 0）

**Files:**
- Delete: `dist-workspace.toml`、`.github/workflows/release.yml`、`.github/workflows/auto-release.yml`、`.github/dist-build-setup.yml`、`services/kittylitter/wix/main.wxs`
- Modify: `services/kittylitter/Cargo.toml`（去掉 `[package.metadata.wix]` 与 `[profile.dist]`）、`services/kittylitter/README.md`、`tools/scripts/update-alleycat-main.sh:9-14`（调用方注释）

**Interfaces:**
- Produces: `services/kittylitter` 仍能 `cargo check`，且 `cargo build --release` 产出 `target/release/agentbuddy`（Task 3 依赖）。

- [ ] **Step 1: 删除文件**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git rm -q dist-workspace.toml .github/workflows/release.yml .github/workflows/auto-release.yml .github/dist-build-setup.yml
git rm -r -q services/kittylitter/wix
```

- [ ] **Step 2: 清理 Cargo.toml**

用下面的内容整体替换 `services/kittylitter/Cargo.toml`（去掉 wix 元数据和 dist profile，其余不变）：

```toml
[workspace]

[package]
# Daemon binary bundled as the desktop app's sidecar; the installed command is `agentbuddy`.
name = "agentbuddycli"
version = "0.1.0"
edition = "2024"
license = "GPL-3.0-only"
authors = ["The Alleycat Authors", "AgentBuddy"]
repository = "https://github.com/AkaShark/AgentBuddy"
homepage = "https://github.com/AkaShark/AgentBuddy"
description = "AgentBuddy（搭子）Mac 端：Iroh P2P 守护进程，把本地编码 agent（Codex/Pi/OpenCode/Claude）多路复用给已配对的搭子 App。"
readme = "README.md"

[[bin]]
name = "agentbuddy"
path = "src/main.rs"

[dependencies]
# [litter-fork] Pinned to an explicit commit of the AkaShark/alleycat fork for
# reproducible builds / GPLv3 source-correspondence (was: branch = "main").
# tools/scripts/update-alleycat-main.sh leaves this rev alone unless you opt in
# with AGENTBUDDY_REFRESH_ALLEYCAT=1, so normal builds never float it.
alleycat = { git = "https://github.com/AkaShark/alleycat.git", rev = "3c6dfe2c6b060864d8cb0fcae58f73a6ed1ea10f" }
anyhow = "1"

[profile.release]
lto = "thin"
```

- [ ] **Step 3: 改写 README 的发布段**

把 `services/kittylitter/README.md` 中从 `## Cutting a release` 到文件末尾替换为：

```markdown
## How it ships

This binary is not published to npm anymore. It is built by
`apps/desktop/scripts/build-sidecar.sh` and bundled inside the desktop app
(`AgentBuddy.app/Contents/MacOS/agentbuddy`), which installs it as the
`com.akashark.agentbuddycli` LaunchAgent. Power users can still call it
directly: `/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr`.
```

并把第 9-12 行的安装示例改成：

```sh
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy serve   # 由桌面 App 以 LaunchAgent 方式运行
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr
```

- [ ] **Step 4: 更新 update-alleycat-main.sh 的调用方注释**

把 `tools/scripts/update-alleycat-main.sh` 第 9-14 行里的 `# shared/rust-bridge/generate-bindings.sh, .github/dist-build-setup.yml and` 和 `# .github/workflows/release.yml. Upstream litter used it to float the deps to` 两行改为：

```bash
# shared/rust-bridge/generate-bindings.sh. Upstream litter used it to float
# the deps to
```

- [ ] **Step 5: 验证**

```bash
export PATH="$HOME/.cargo/bin:$PATH"
git grep -n -i 'dist-workspace\|cargo-dist\|auto-release\|dist-build-setup\|wix\|profile.dist\|NPM_TOKEN' -- . ':!docs/superpowers' ':!shared/rust-bridge/codex-bridge/src/cacert.pem'
# Expected: no output
(cd services/kittylitter && cargo check)
# Expected: Finished
```

- [ ] **Step 6: Commit**

```bash
git add -A dist-workspace.toml .github services/kittylitter tools/scripts/update-alleycat-main.sh
git commit -m "release: remove the cargo-dist npm publishing lane

The daemon now ships inside the desktop app as a sidecar; drop
dist-workspace.toml, the generated release.yml, auto-release.yml,
dist-build-setup.yml, the WiX template and the dist profile.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: 搭建 apps/desktop 的 Tauri v2 + React/TS 骨架

**Files:**
- Create: `apps/desktop/package.json`、`apps/desktop/vite.config.ts`、`apps/desktop/tsconfig.json`、`apps/desktop/index.html`、`apps/desktop/.gitignore`、`apps/desktop/src/main.tsx`、`apps/desktop/src/App.tsx`、`apps/desktop/src/test/setup.ts`、`apps/desktop/src-tauri/Cargo.toml`、`apps/desktop/src-tauri/build.rs`、`apps/desktop/src-tauri/tauri.conf.json`、`apps/desktop/src-tauri/entitlements.plist`、`apps/desktop/src-tauri/capabilities/default.json`、`apps/desktop/src-tauri/src/main.rs`、`apps/desktop/src-tauri/src/lib.rs`
- Modify: `.gitignore`

**Interfaces:**
- Produces: `agentbuddy_desktop_lib::run()`；`npm run tauri dev/build` 可用；后续任务在 `src-tauri/src/` 下加模块并在 `lib.rs` 注册。

- [ ] **Step 1: 前端骨架**

`apps/desktop/package.json`：

```json
{
  "name": "agentbuddy-desktop",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview",
    "tauri": "tauri",
    "test": "vitest run",
    "test:watch": "vitest"
  },
  "dependencies": {
    "@tauri-apps/api": "^2",
    "@tauri-apps/plugin-clipboard-manager": "^2",
    "@tauri-apps/plugin-dialog": "^2",
    "qrcode.react": "^4",
    "react": "^19",
    "react-dom": "^19"
  },
  "devDependencies": {
    "@tauri-apps/cli": "^2",
    "@testing-library/jest-dom": "^6",
    "@testing-library/react": "^16",
    "@types/react": "^19",
    "@types/react-dom": "^19",
    "@vitejs/plugin-react": "^4",
    "jsdom": "^26",
    "typescript": "^5",
    "vite": "^6",
    "vitest": "^3"
  }
}
```

`apps/desktop/vite.config.ts`：

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: { port: 1420, strictPort: true },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
```

`apps/desktop/tsconfig.json`：

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noEmit": true,
    "skipLibCheck": true,
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  },
  "include": ["src"]
}
```

`apps/desktop/index.html`：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AgentBuddy</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

`apps/desktop/src/main.tsx`：

```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

`apps/desktop/src/App.tsx`（占位，Task 11 替换）：

```tsx
export default function App() {
  return <main>AgentBuddy</main>;
}
```

`apps/desktop/src/test/setup.ts`：

```ts
import "@testing-library/jest-dom/vitest";
```

`apps/desktop/.gitignore`：

```
node_modules/
dist/
src-tauri/target/
src-tauri/binaries/
src-tauri/gen/
```

- [ ] **Step 2: Rust 骨架**

`apps/desktop/src-tauri/Cargo.toml`：

```toml
[workspace]

[package]
name = "agentbuddy-desktop"
version = "0.1.0"
edition = "2021"
license = "GPL-3.0-only"
publish = false

[lib]
name = "agentbuddy_desktop_lib"
crate-type = ["staticlib", "cdylib", "rlib"]

[build-dependencies]
tauri-build = { version = "2", features = [] }

[dependencies]
tauri = { version = "2", features = ["tray-icon", "image-png"] }
tauri-plugin-shell = "2"
tauri-plugin-dialog = "2"
tauri-plugin-clipboard-manager = "2"
tauri-plugin-opener = "2"
tauri-plugin-positioner = { version = "2", features = ["tray-icon"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
toml_edit = "0.22"
plist = "1"
thiserror = "2"
dirs = "6"
tokio = { version = "1", features = ["sync", "time"] }

[dev-dependencies]
tempfile = "3"
```

`apps/desktop/src-tauri/build.rs`：

```rust
fn main() {
    tauri_build::build()
}
```

`apps/desktop/src-tauri/src/main.rs`：

```rust
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    agentbuddy_desktop_lib::run()
}
```

`apps/desktop/src-tauri/src/lib.rs`（骨架，后续任务追加模块与命令）：

```rust
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_positioner::init())
        .setup(|app| {
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running AgentBuddy");
}
```

`apps/desktop/src-tauri/tauri.conf.json`：

```json
{
  "$schema": "https://schema.tauri.app/config/2",
  "productName": "AgentBuddy",
  "version": "0.1.0",
  "identifier": "com.akashark.agentbuddy.host",
  "build": {
    "beforeDevCommand": "npm run dev",
    "devUrl": "http://localhost:1420",
    "beforeBuildCommand": "npm run build",
    "frontendDist": "../dist"
  },
  "app": {
    "windows": [
      {
        "label": "main",
        "title": "AgentBuddy",
        "width": 760,
        "height": 540,
        "minWidth": 640,
        "minHeight": 440,
        "visible": false,
        "resizable": true
      }
    ],
    "security": {
      "csp": "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'"
    }
  },
  "bundle": {
    "active": true,
    "targets": ["app", "dmg"],
    "icon": [
      "icons/32x32.png",
      "icons/128x128.png",
      "icons/128x128@2x.png",
      "icons/icon.icns"
    ],
    "externalBin": ["binaries/agentbuddy"],
    "macOS": {
      "minimumSystemVersion": "14.0",
      "hardenedRuntime": true,
      "entitlements": "entitlements.plist"
    }
  }
}
```

`apps/desktop/src-tauri/entitlements.plist`（不沙盒，最小集）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.network.client</key>
    <true/>
    <key>com.apple.security.network.server</key>
    <true/>
</dict>
</plist>
```

`apps/desktop/src-tauri/capabilities/default.json`：

```json
{
  "$schema": "../gen/schemas/desktop-schema.json",
  "identifier": "default",
  "description": "AgentBuddy console window",
  "windows": ["main"],
  "permissions": [
    "core:default",
    "dialog:allow-open",
    "clipboard-manager:allow-write-text"
  ]
}
```

- [ ] **Step 3: 图标与依赖安装**

```bash
cd apps/desktop
npm install
npm run tauri icon ../ios/Sources/AgentBuddy/Assets.xcassets/AppIcon.appiconset/Icon-1024.png
# Expected: src-tauri/icons/ 下生成 icon.icns、32x32.png、128x128.png、128x128@2x.png 等
```

- [ ] **Step 4: 放一个占位 sidecar 让 cargo check 通过**

`tauri-build` 要求 `externalBin` 的文件存在。临时用脚本占位（Task 3 会用真二进制覆盖）：

```bash
mkdir -p src-tauri/binaries
printf '#!/bin/sh\nexit 0\n' > "src-tauri/binaries/agentbuddy-$(rustc --print host-tuple)"
chmod +x "src-tauri/binaries/agentbuddy-$(rustc --print host-tuple)"
```

- [ ] **Step 5: 验证骨架**

```bash
export PATH="$HOME/.cargo/bin:$PATH"
(cd src-tauri && cargo check)
# Expected: Finished（首次会编译 tauri，几分钟）
npm run build
# Expected: dist/ 生成，tsc 无错误
```

- [ ] **Step 6: 根 .gitignore 与提交**

在仓库根 `.gitignore` 末尾追加：

```
# Desktop (Tauri) build outputs
apps/desktop/node_modules/
apps/desktop/dist/
apps/desktop/src-tauri/target/
apps/desktop/src-tauri/binaries/
apps/desktop/src-tauri/gen/
```

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add .gitignore apps/desktop
git status --short apps/desktop | grep -E 'node_modules|binaries|target' && echo "ERROR: ignored dirs staged" || true
git commit -m "desktop: scaffold Tauri v2 + React/TS menu-bar app

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: sidecar 构建脚本、版本同步、Makefile 目标

**Files:**
- Create: `apps/desktop/scripts/build-sidecar.sh`、`apps/desktop/scripts/sync-version.mjs`、`apps/desktop/scripts/sync-version.test.mjs`
- Modify: `Makefile`（`.PHONY` 行、help、新增目标）

**Interfaces:**
- Produces: `apps/desktop/src-tauri/binaries/agentbuddy-<triple>`（Task 15 契约测试、`tauri dev/build` 依赖）；`make desktop-sidecar | desktop-dev | desktop-build | desktop-dist`。

- [ ] **Step 1: 写版本同步脚本的失败测试**

`apps/desktop/scripts/sync-version.test.mjs`：

```js
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { syncVersion } from "./sync-version.mjs";

test("copies the Cargo.toml version into tauri.conf.json", () => {
  const dir = mkdtempSync(join(tmpdir(), "syncver-"));
  const cargo = join(dir, "Cargo.toml");
  const conf = join(dir, "tauri.conf.json");
  writeFileSync(cargo, '[package]\nname = "agentbuddycli"\nversion = "1.2.3"\n');
  writeFileSync(conf, JSON.stringify({ productName: "AgentBuddy", version: "0.0.0" }, null, 2));
  const v = syncVersion(cargo, conf);
  assert.equal(v, "1.2.3");
  assert.equal(JSON.parse(readFileSync(conf, "utf8")).version, "1.2.3");
});

test("throws when Cargo.toml has no version", () => {
  const dir = mkdtempSync(join(tmpdir(), "syncver-"));
  const cargo = join(dir, "Cargo.toml");
  writeFileSync(cargo, "[package]\nname = \"x\"\n");
  assert.throws(() => syncVersion(cargo, join(dir, "c.json")), /version not found/);
});
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop && node --test scripts/sync-version.test.mjs
# Expected: FAIL — Cannot find module './sync-version.mjs'
```

- [ ] **Step 3: 实现 sync-version.mjs**

```js
#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

export function syncVersion(cargoTomlPath, tauriConfPath) {
  const cargo = readFileSync(cargoTomlPath, "utf8");
  const match = cargo.match(/^version\s*=\s*"([^"]+)"/m);
  if (!match) throw new Error(`version not found in ${cargoTomlPath}`);
  const conf = JSON.parse(readFileSync(tauriConfPath, "utf8"));
  conf.version = match[1];
  writeFileSync(tauriConfPath, JSON.stringify(conf, null, 2) + "\n");
  return match[1];
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const here = dirname(fileURLToPath(import.meta.url));
  const cargo = resolve(here, "../../../services/kittylitter/Cargo.toml");
  const conf = resolve(here, "../src-tauri/tauri.conf.json");
  const v = syncVersion(cargo, conf);
  console.log(`tauri.conf.json version -> ${v}`);
}
```

- [ ] **Step 4: 运行确认通过**

```bash
node --test scripts/sync-version.test.mjs
# Expected: 2 passed
node scripts/sync-version.mjs
# Expected: tauri.conf.json version -> 0.1.0
```

- [ ] **Step 5: 写 build-sidecar.sh**

```bash
#!/usr/bin/env bash
# Build the agentbuddy daemon (services/kittylitter) and place it where the
# Tauri bundler expects sidecars: src-tauri/binaries/agentbuddy-<triple>.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DESKTOP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_DIR="$(cd "$DESKTOP_DIR/../.." && pwd)"
export PATH="$HOME/.cargo/bin:$PATH"

TRIPLE="${1:-$(rustc --print host-tuple)}"
MANIFEST="$REPO_DIR/services/kittylitter/Cargo.toml"
OUT_DIR="$DESKTOP_DIR/src-tauri/binaries"
SRC="$REPO_DIR/services/kittylitter/target/$TRIPLE/release/agentbuddy"

echo "==> Building agentbuddy sidecar for $TRIPLE"
rustup target add "$TRIPLE" >/dev/null
cargo build --release --manifest-path "$MANIFEST" --target "$TRIPLE"

mkdir -p "$OUT_DIR"
cp "$SRC" "$OUT_DIR/agentbuddy-$TRIPLE"
chmod +x "$OUT_DIR/agentbuddy-$TRIPLE"
echo "==> $OUT_DIR/agentbuddy-$TRIPLE"
"$OUT_DIR/agentbuddy-$TRIPLE" --version
```

```bash
chmod +x scripts/build-sidecar.sh && bash -n scripts/build-sidecar.sh
```

- [ ] **Step 6: 跑一次，替换 Task 2 的占位**

```bash
./scripts/build-sidecar.sh
# Expected: 最后一行形如 "agentbuddy 0.1.0"；首次会编译 alleycat（约 5-10 分钟）
file "src-tauri/binaries/agentbuddy-$(rustc --print host-tuple)"
# Expected: Mach-O 64-bit executable arm64
```

- [ ] **Step 7: Makefile 目标**

在 `Makefile` 第 19 行 `KITTYLITTER_DIR` 附近加：

```make
DESKTOP_DIR := $(ROOT)/apps/desktop
```

在 `.PHONY` 列表的 `clean clean-rust clean-ios clean-android \` 那一行之前加一行：

```make
	desktop-sidecar desktop-dev desktop-build desktop-dist \
```

在 `kittylitter:` 目标之前加：

```make
desktop-sidecar:
	@$(DESKTOP_DIR)/scripts/build-sidecar.sh

desktop-dev: desktop-sidecar
	@cd $(DESKTOP_DIR) && node scripts/sync-version.mjs && npm run tauri dev

desktop-build: desktop-sidecar
	@cd $(DESKTOP_DIR) && node scripts/sync-version.mjs && npm run tauri build

# Signed + notarized dmg. Needs APPLE_SIGNING_IDENTITY plus APPLE_API_KEY,
# APPLE_API_ISSUER and APPLE_API_KEY_PATH in the environment.
desktop-dist: desktop-sidecar
	@cd $(DESKTOP_DIR) && node scripts/sync-version.mjs && npm run tauri build -- --bundles dmg
```

在 `help:` 的 printf 列表里 `'make alleycat-main …'` 之后加：

```make
		'make desktop-sidecar    build services/kittylitter and stage it as the Tauri sidecar' \
		'make desktop-dev        sidecar + tauri dev for the desktop host app' \
		'make desktop-build      sidecar + tauri build (.app + .dmg, unsigned unless APPLE_* set)' \
		'make desktop-dist       signed + notarized dmg (APPLE_SIGNING_IDENTITY, APPLE_API_KEY*)' \
```

- [ ] **Step 8: 验证 Makefile**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
make -n desktop-sidecar | head -2
# Expected: 打印 build-sidecar.sh 调用
make help | grep desktop
# Expected: 四行
```

- [ ] **Step 9: Commit**

```bash
git add Makefile apps/desktop/scripts
git commit -m "desktop: sidecar build script, version sync and make targets

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: HostError 与 sidecar 运行器

**Files:**
- Create: `apps/desktop/src-tauri/src/error.rs`、`apps/desktop/src-tauri/src/sidecar.rs`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（`mod error; mod sidecar;`）

**Interfaces:**
- Produces:
  - `error::HostError { kind: HostErrorKind, detail: String }`，`HostErrorKind::{SidecarMissing, CommandFailed { code: Option<i32>, stderr: String }, ParseFailed, PermissionDenied, ConfigInvalid}`，实现 `Serialize`（前端收到 `{ kind: {...}, detail }`）与 `std::error::Error`；构造函数 `HostError::sidecar_missing(d)`、`command_failed(cmd, code, stderr)`、`parse_failed(d)`、`config_invalid(d)`、`permission_denied(d)`。
  - `sidecar::Subcommand` 枚举：`StatusJson, Pair, Install, Uninstall, Stop, Restart, Reload, Rotate, Upgrade, LogsTail(usize), LogsFollow(usize), Version`；`fn args(&self) -> Vec<String>`；`fn is_mutating(&self) -> bool`；`fn label(&self) -> &'static str`。
  - `sidecar::RawOutput { code: Option<i32>, stdout: String, stderr: String }`；`fn classify(cmd: &Subcommand, out: RawOutput) -> Result<String, HostError>`。
  - `async fn sidecar::run(app: &tauri::AppHandle, cmd: Subcommand) -> Result<String, HostError>`；`fn sidecar::sidecar_path() -> Result<PathBuf, HostError>`（当前可执行文件同目录下的 `agentbuddy`）。

- [ ] **Step 1: 写失败测试（放在 sidecar.rs 底部 `#[cfg(test)]`）**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::error::HostErrorKind;

    #[test]
    fn args_match_the_daemon_cli() {
        assert_eq!(Subcommand::StatusJson.args(), vec!["status", "--json"]);
        assert_eq!(Subcommand::Pair.args(), vec!["pair"]);
        assert_eq!(Subcommand::LogsTail(500).args(), vec!["logs", "-n", "500"]);
        assert_eq!(Subcommand::LogsFollow(200).args(), vec!["logs", "-f", "-n", "200"]);
        assert_eq!(Subcommand::Version.args(), vec!["--version"]);
    }

    #[test]
    fn mutating_set_is_exactly_the_service_changing_commands() {
        let mutating = [
            Subcommand::Install, Subcommand::Uninstall, Subcommand::Stop,
            Subcommand::Restart, Subcommand::Reload, Subcommand::Rotate, Subcommand::Upgrade,
        ];
        for c in mutating { assert!(c.is_mutating(), "{c:?}"); }
        for c in [Subcommand::StatusJson, Subcommand::Pair, Subcommand::LogsTail(1), Subcommand::LogsFollow(1), Subcommand::Version] {
            assert!(!c.is_mutating(), "{c:?}");
        }
    }

    #[test]
    fn classify_returns_stdout_on_success() {
        let out = RawOutput { code: Some(0), stdout: "{\"pid\":1}\n".into(), stderr: String::new() };
        assert_eq!(classify(&Subcommand::StatusJson, out).unwrap(), "{\"pid\":1}\n");
    }

    #[test]
    fn classify_maps_nonzero_exit_to_command_failed_with_stderr() {
        let out = RawOutput { code: Some(1), stdout: String::new(), stderr: "error: daemon not running\n".into() };
        let err = classify(&Subcommand::Stop, out).unwrap_err();
        match err.kind {
            HostErrorKind::CommandFailed { code, stderr } => {
                assert_eq!(code, Some(1));
                assert!(stderr.contains("daemon not running"));
            }
            other => panic!("unexpected {other:?}"),
        }
        assert!(err.detail.contains("stop"));
    }

    #[test]
    fn classify_maps_permission_denied_stderr() {
        let out = RawOutput { code: Some(1), stdout: String::new(), stderr: "Permission denied (os error 13)".into() };
        let err = classify(&Subcommand::Install, out).unwrap_err();
        assert!(matches!(err.kind, HostErrorKind::PermissionDenied));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop/src-tauri && cargo test sidecar 2>&1 | tail -5
# Expected: error[E0583]: file not found for module `sidecar` / `error`
```

- [ ] **Step 3: 实现 error.rs**

```rust
use serde::Serialize;

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum HostErrorKind {
    SidecarMissing,
    CommandFailed { code: Option<i32>, stderr: String },
    ParseFailed,
    PermissionDenied,
    ConfigInvalid,
}

#[derive(Debug, Clone, Serialize, PartialEq, thiserror::Error)]
#[error("{detail}")]
pub struct HostError {
    pub kind: HostErrorKind,
    pub detail: String,
}

impl HostError {
    pub fn sidecar_missing(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::SidecarMissing, detail: detail.into() }
    }
    pub fn command_failed(label: &str, code: Option<i32>, stderr: impl Into<String>) -> Self {
        let stderr = stderr.into();
        let detail = format!("`agentbuddy {label}` exited with {code:?}: {}", stderr.trim());
        Self { kind: HostErrorKind::CommandFailed { code, stderr }, detail }
    }
    pub fn parse_failed(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::ParseFailed, detail: detail.into() }
    }
    pub fn permission_denied(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::PermissionDenied, detail: detail.into() }
    }
    pub fn config_invalid(detail: impl Into<String>) -> Self {
        Self { kind: HostErrorKind::ConfigInvalid, detail: detail.into() }
    }
}

impl From<std::io::Error> for HostError {
    fn from(e: std::io::Error) -> Self {
        if e.kind() == std::io::ErrorKind::PermissionDenied {
            Self::permission_denied(e.to_string())
        } else {
            Self::config_invalid(e.to_string())
        }
    }
}
```

- [ ] **Step 4: 实现 sidecar.rs**

```rust
use std::path::PathBuf;

use tauri::AppHandle;
use tauri_plugin_shell::ShellExt;

use crate::error::HostError;

pub const SIDECAR_NAME: &str = "agentbuddy";

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Subcommand {
    StatusJson,
    Pair,
    Install,
    Uninstall,
    Stop,
    Restart,
    Reload,
    Rotate,
    Upgrade,
    LogsTail(usize),
    LogsFollow(usize),
    Version,
}

impl Subcommand {
    pub fn args(&self) -> Vec<String> {
        let v: Vec<&str> = match self {
            Subcommand::StatusJson => vec!["status", "--json"],
            Subcommand::Pair => vec!["pair"],
            Subcommand::Install => vec!["install"],
            Subcommand::Uninstall => vec!["uninstall"],
            Subcommand::Stop => vec!["stop"],
            Subcommand::Restart => vec!["restart"],
            Subcommand::Reload => vec!["reload"],
            Subcommand::Rotate => vec!["rotate"],
            Subcommand::Upgrade => vec!["upgrade"],
            Subcommand::LogsTail(n) => return vec!["logs".into(), "-n".into(), n.to_string()],
            Subcommand::LogsFollow(n) => return vec!["logs".into(), "-f".into(), "-n".into(), n.to_string()],
            Subcommand::Version => vec!["--version"],
        };
        v.into_iter().map(String::from).collect()
    }

    pub fn is_mutating(&self) -> bool {
        matches!(
            self,
            Subcommand::Install | Subcommand::Uninstall | Subcommand::Stop | Subcommand::Restart
                | Subcommand::Reload | Subcommand::Rotate | Subcommand::Upgrade
        )
    }

    pub fn label(&self) -> &'static str {
        match self {
            Subcommand::StatusJson => "status --json",
            Subcommand::Pair => "pair",
            Subcommand::Install => "install",
            Subcommand::Uninstall => "uninstall",
            Subcommand::Stop => "stop",
            Subcommand::Restart => "restart",
            Subcommand::Reload => "reload",
            Subcommand::Rotate => "rotate",
            Subcommand::Upgrade => "upgrade",
            Subcommand::LogsTail(_) => "logs -n",
            Subcommand::LogsFollow(_) => "logs -f",
            Subcommand::Version => "--version",
        }
    }
}

pub struct RawOutput {
    pub code: Option<i32>,
    pub stdout: String,
    pub stderr: String,
}

pub fn classify(cmd: &Subcommand, out: RawOutput) -> Result<String, HostError> {
    if out.code == Some(0) {
        return Ok(out.stdout);
    }
    if out.stderr.contains("Permission denied") || out.stderr.contains("Operation not permitted") {
        return Err(HostError::permission_denied(format!(
            "`agentbuddy {}`: {}", cmd.label(), out.stderr.trim()
        )));
    }
    Err(HostError::command_failed(cmd.label(), out.code, out.stderr))
}

/// Absolute path of the bundled sidecar: Tauri places external binaries next
/// to the main executable (Contents/MacOS in the .app, target/<profile> in dev).
pub fn sidecar_path() -> Result<PathBuf, HostError> {
    let exe = std::env::current_exe().map_err(|e| HostError::sidecar_missing(e.to_string()))?;
    let dir = exe.parent().ok_or_else(|| HostError::sidecar_missing("executable has no parent dir"))?;
    let path = dir.join(SIDECAR_NAME);
    if !path.exists() {
        return Err(HostError::sidecar_missing(format!("{} not found", path.display())));
    }
    Ok(path)
}

pub async fn run(app: &AppHandle, cmd: Subcommand) -> Result<String, HostError> {
    let command = app
        .shell()
        .sidecar(SIDECAR_NAME)
        .map_err(|e| HostError::sidecar_missing(e.to_string()))?
        .args(cmd.args());
    let output = command
        .output()
        .await
        .map_err(|e| HostError::sidecar_missing(format!("spawn `agentbuddy {}`: {e}", cmd.label())))?;
    classify(
        &cmd,
        RawOutput {
            code: output.status.code(),
            stdout: String::from_utf8_lossy(&output.stdout).into_owned(),
            stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
        },
    )
}
```

在 `lib.rs` 顶部加 `mod error;` 和 `mod sidecar;`。

- [ ] **Step 5: 运行确认通过**

```bash
cargo test sidecar 2>&1 | tail -3
# Expected: test result: ok. 5 passed
```

- [ ] **Step 6: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri/src
git commit -m "desktop: typed HostError and sidecar command runner

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: 状态与配对 payload 的类型和解析

**Files:**
- Create: `apps/desktop/src-tauri/src/status.rs`、`apps/desktop/src-tauri/tests/fixtures/status.json`、`apps/desktop/src-tauri/tests/fixtures/pair.txt`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（`mod status;`）

**Interfaces:**
- Produces:
  - `status::AgentInfo { name: String, display_name: String, wire: serde_json::Value, available: bool, presentation: Option<Value>, capabilities: Option<Value> }`
  - `status::StatusInfo { pid: u32, node_id: String, token_short: String, relay: Option<String>, config_path: String, uptime_secs: u64, agents: Vec<AgentInfo> }`，`fn is_running(&self) -> bool`（pid > 0）
  - `status::PairPayload { raw: String, node_id: String, token: String, host_name: Option<String>, relay: Option<String> }`
  - `fn status::parse_status(stdout: &str) -> Result<StatusInfo, HostError>`；`fn status::parse_pair(stdout: &str) -> Result<PairPayload, HostError>`
  - 三个类型都 `Serialize + Deserialize + Clone + Debug + PartialEq`。

- [ ] **Step 1: 抓真实 fixture**

```bash
cd apps/desktop
B="src-tauri/binaries/agentbuddy-$(rustc --print host-tuple)"
mkdir -p src-tauri/tests/fixtures
"$B" status --json > src-tauri/tests/fixtures/status.json
"$B" pair > src-tauri/tests/fixtures/pair.txt
head -c 300 src-tauri/tests/fixtures/status.json; echo; cat src-tauri/tests/fixtures/pair.txt
# Expected: status.json 以 { "pid": 0, 开头（守护进程未跑）；pair.txt 是一行 {"v":1,"node_id":...,"token":...}
```

如果 `pair` 因为没有 host key 而先生成密钥并打印提示，保留输出原样，解析器必须能跳过非 JSON 行。

- [ ] **Step 2: 写失败测试（status.rs 底部）**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    const STATUS: &str = include_str!("../tests/fixtures/status.json");
    const PAIR: &str = include_str!("../tests/fixtures/pair.txt");

    #[test]
    fn parse_status_reads_real_cli_output() {
        let s = parse_status(STATUS).unwrap();
        assert!(!s.node_id.is_empty());
        assert!(s.config_path.ends_with("host.toml"));
        assert!(s.agents.iter().any(|a| a.name == "codex"));
        assert_eq!(s.is_running(), s.pid > 0);
    }

    #[test]
    fn parse_status_tolerates_unknown_fields() {
        let json = r#"{"pid":42,"node_id":"n","token_short":"ab12","relay":null,"config_path":"/x/host.toml",
            "uptime_secs":7,"future_field":{"a":1},
            "agents":[{"name":"codex","display_name":"Codex","wire":"some_new_wire","available":true,"extra":1}]}"#;
        let s = parse_status(json).unwrap();
        assert_eq!(s.pid, 42);
        assert!(s.is_running());
        assert_eq!(s.agents[0].wire, serde_json::Value::String("some_new_wire".into()));
    }

    #[test]
    fn parse_status_rejects_garbage() {
        let err = parse_status("not json").unwrap_err();
        assert!(matches!(err.kind, crate::error::HostErrorKind::ParseFailed));
    }

    #[test]
    fn parse_pair_reads_real_cli_output() {
        let p = parse_pair(PAIR).unwrap();
        assert!(!p.node_id.is_empty());
        assert!(!p.token.is_empty());
        assert!(p.raw.starts_with('{'));
        let round: serde_json::Value = serde_json::from_str(&p.raw).unwrap();
        assert_eq!(round["node_id"], p.node_id);
    }

    #[test]
    fn parse_pair_skips_non_json_lines() {
        let out = "2026-09-23T10:00:00Z WARN alleycat: generated new host key\n{\"v\":1,\"node_id\":\"abc\",\"token\":\"tok\",\"host_name\":\"studio\"}\n";
        let p = parse_pair(out).unwrap();
        assert_eq!(p.node_id, "abc");
        assert_eq!(p.host_name.as_deref(), Some("studio"));
        assert_eq!(p.raw, "{\"v\":1,\"node_id\":\"abc\",\"token\":\"tok\",\"host_name\":\"studio\"}");
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
cd src-tauri && cargo test status 2>&1 | tail -3
# Expected: error[E0583]: file not found for module `status`
```

- [ ] **Step 4: 实现 status.rs**

```rust
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::error::HostError;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct AgentInfo {
    pub name: String,
    pub display_name: String,
    pub wire: Value,
    pub available: bool,
    #[serde(default)]
    pub presentation: Option<Value>,
    #[serde(default)]
    pub capabilities: Option<Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct StatusInfo {
    pub pid: u32,
    pub node_id: String,
    pub token_short: String,
    #[serde(default)]
    pub relay: Option<String>,
    pub config_path: String,
    pub uptime_secs: u64,
    #[serde(default)]
    pub agents: Vec<AgentInfo>,
}

impl StatusInfo {
    pub fn is_running(&self) -> bool {
        self.pid > 0
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PairPayload {
    /// The exact JSON line the daemon printed; this is what the phone scans.
    pub raw: String,
    pub node_id: String,
    pub token: String,
    #[serde(default)]
    pub host_name: Option<String>,
    #[serde(default)]
    pub relay: Option<String>,
}

#[derive(Deserialize)]
struct PairWire {
    node_id: String,
    token: String,
    #[serde(default)]
    host_name: Option<String>,
    #[serde(default)]
    relay: Option<String>,
}

pub fn parse_status(stdout: &str) -> Result<StatusInfo, HostError> {
    serde_json::from_str(stdout.trim())
        .map_err(|e| HostError::parse_failed(format!("status --json: {e}")))
}

pub fn parse_pair(stdout: &str) -> Result<PairPayload, HostError> {
    for line in stdout.lines() {
        let line = line.trim();
        if !line.starts_with('{') {
            continue;
        }
        if let Ok(w) = serde_json::from_str::<PairWire>(line) {
            return Ok(PairPayload {
                raw: line.to_string(),
                node_id: w.node_id,
                token: w.token,
                host_name: w.host_name,
                relay: w.relay,
            });
        }
    }
    Err(HostError::parse_failed("pair: no JSON payload line in output"))
}
```

在 `lib.rs` 加 `mod status;`。

- [ ] **Step 5: 运行确认通过**

```bash
cargo test status 2>&1 | tail -3
# Expected: test result: ok. 5 passed
```

- [ ] **Step 6: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri/src apps/desktop/src-tauri/tests/fixtures
git commit -m "desktop: parse daemon status and pair payload from the sidecar CLI

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: LaunchAgent plist 读取与安装状态机

**Files:**
- Create: `apps/desktop/src-tauri/src/launchd.rs`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（`mod launchd;`）

**Interfaces:**
- Produces:
  - `launchd::LABEL: &str = "com.akashark.agentbuddycli"`
  - `fn launchd::plist_path() -> Result<PathBuf, HostError>`（`~/Library/LaunchAgents/com.akashark.agentbuddycli.plist`）
  - `fn launchd::read_program_path(plist: &Path) -> Result<Option<PathBuf>, HostError>`（文件不存在 → `Ok(None)`）
  - `enum launchd::InstallState { NotInstalled, Installed, PathMismatch { plist_exe: String } }`（`Serialize`，`#[serde(tag = "kind", rename_all = "snake_case")]`）
  - `fn launchd::derive_install_state(plist_exe: Option<&Path>, sidecar: &Path) -> InstallState`（先 canonicalize 两边再比较，失败则按字面比较）
  - `struct launchd::HostState { install: InstallState, running: bool, status: Option<StatusInfo>, app_version: String, sidecar_path: String }`（`Serialize`）

- [ ] **Step 1: 写失败测试**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn missing_plist_means_not_installed() {
        let sidecar = std::path::Path::new("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy");
        assert_eq!(derive_install_state(None, sidecar), InstallState::NotInstalled);
    }

    #[test]
    fn same_path_means_installed() {
        let dir = tempfile::tempdir().unwrap();
        let exe = dir.path().join("agentbuddy");
        fs::write(&exe, b"").unwrap();
        assert_eq!(derive_install_state(Some(&exe), &exe), InstallState::Installed);
    }

    #[test]
    fn different_path_means_mismatch_with_plist_path_reported() {
        let dir = tempfile::tempdir().unwrap();
        let ours = dir.path().join("new/agentbuddy");
        fs::create_dir_all(ours.parent().unwrap()).unwrap();
        fs::write(&ours, b"").unwrap();
        let theirs = dir.path().join("old/agentbuddy");
        let state = derive_install_state(Some(&theirs), &ours);
        assert_eq!(state, InstallState::PathMismatch { plist_exe: theirs.to_string_lossy().into_owned() });
    }

    #[test]
    fn derive_install_state_treats_symlink_as_match() {
        let dir = tempfile::tempdir().unwrap();
        let real = dir.path().join("Applications/AgentBuddy.app/Contents/MacOS/agentbuddy");
        fs::create_dir_all(real.parent().unwrap()).unwrap();
        fs::write(&real, b"").unwrap();
        let link = dir.path().join("link-agentbuddy");
        std::os::unix::fs::symlink(&real, &link).unwrap();
        assert_eq!(derive_install_state(Some(&link), &real), InstallState::Installed);
    }

    #[test]
    fn read_program_path_extracts_first_program_argument() {
        let dir = tempfile::tempdir().unwrap();
        let plist = dir.path().join("x.plist");
        fs::write(&plist, r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.akashark.agentbuddycli</string>
  <key>ProgramArguments</key><array><string>/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy</string><string>serve</string></array>
</dict></plist>"#).unwrap();
        let p = read_program_path(&plist).unwrap().unwrap();
        assert_eq!(p, std::path::PathBuf::from("/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy"));
    }

    #[test]
    fn read_program_path_returns_none_when_file_missing() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_program_path(&dir.path().join("nope.plist")).unwrap(), None);
    }

    #[test]
    fn plist_path_is_under_library_launchagents() {
        let p = plist_path().unwrap();
        assert!(p.ends_with("Library/LaunchAgents/com.akashark.agentbuddycli.plist"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop/src-tauri && cargo test launchd 2>&1 | tail -3
# Expected: error[E0583]: file not found for module `launchd`
```

- [ ] **Step 3: 实现 launchd.rs**

```rust
use std::path::{Path, PathBuf};

use serde::Serialize;

use crate::error::HostError;
use crate::status::StatusInfo;

pub const LABEL: &str = "com.akashark.agentbuddycli";

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum InstallState {
    NotInstalled,
    Installed,
    PathMismatch { plist_exe: String },
}

#[derive(Debug, Clone, Serialize)]
pub struct HostState {
    pub install: InstallState,
    pub running: bool,
    pub status: Option<StatusInfo>,
    pub app_version: String,
    pub sidecar_path: String,
}

pub fn plist_path() -> Result<PathBuf, HostError> {
    let home = dirs::home_dir().ok_or_else(|| HostError::config_invalid("no home directory"))?;
    Ok(home.join("Library").join("LaunchAgents").join(format!("{LABEL}.plist")))
}

pub fn read_program_path(plist: &Path) -> Result<Option<PathBuf>, HostError> {
    if !plist.exists() {
        return Ok(None);
    }
    let value = plist::Value::from_file(plist)
        .map_err(|e| HostError::parse_failed(format!("{}: {e}", plist.display())))?;
    let program = value
        .as_dictionary()
        .and_then(|d| d.get("ProgramArguments"))
        .and_then(|v| v.as_array())
        .and_then(|a| a.first())
        .and_then(|v| v.as_string())
        .ok_or_else(|| HostError::parse_failed(format!("{}: ProgramArguments missing", plist.display())))?;
    Ok(Some(PathBuf::from(program)))
}

fn normalize(p: &Path) -> PathBuf {
    std::fs::canonicalize(p).unwrap_or_else(|_| p.to_path_buf())
}

pub fn derive_install_state(plist_exe: Option<&Path>, sidecar: &Path) -> InstallState {
    match plist_exe {
        None => InstallState::NotInstalled,
        Some(exe) if normalize(exe) == normalize(sidecar) => InstallState::Installed,
        Some(exe) => InstallState::PathMismatch { plist_exe: exe.to_string_lossy().into_owned() },
    }
}
```

在 `lib.rs` 加 `mod launchd;`。

- [ ] **Step 4: 运行确认通过**

```bash
cargo test launchd 2>&1 | tail -3
# Expected: test result: ok. 7 passed
```

- [ ] **Step 5: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri/src
git commit -m "desktop: LaunchAgent plist reader and install state machine

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: host.toml 编辑与原子写入

**Files:**
- Create: `apps/desktop/src-tauri/src/config.rs`、`apps/desktop/src-tauri/tests/fixtures/host.toml`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（`mod config;`）

**Interfaces:**
- Produces:
  - `fn config::set_agent_enabled(text: &str, agent: &str, enabled: bool) -> Result<String, HostError>`
  - `fn config::set_agent_bin(text: &str, agent: &str, bin: &str) -> Result<String, HostError>`
  - `fn config::write_atomic(path: &Path, contents: &str) -> Result<(), HostError>`（首次写前把原文件复制为 `<path>.bak`；写 `<path>.tmp` 再 `rename`）
  - `fn config::read_or_empty(path: &Path) -> Result<String, HostError>`（文件不存在返回空串）

- [ ] **Step 1: fixture**

`apps/desktop/src-tauri/tests/fixtures/host.toml`：

```toml
# AgentBuddy host config
token = "abc123"

[agents.codex]
enabled = true   # keep codex on
bin = "codex"
host = "127.0.0.1"
port = 8390

[agents.claude]
enabled = false
bin = "claude"
```

- [ ] **Step 2: 写失败测试（config.rs 底部）**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    const HOST: &str = include_str!("../tests/fixtures/host.toml");

    #[test]
    fn set_agent_enabled_flips_only_that_agent_and_keeps_comments() {
        let out = set_agent_enabled(HOST, "claude", true).unwrap();
        assert!(out.contains("# AgentBuddy host config"));
        assert!(out.contains("enabled = true   # keep codex on"));
        assert!(out.contains("token = \"abc123\""));
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["claude"]["enabled"].as_bool(), Some(true));
        assert_eq!(doc["agents"]["codex"]["enabled"].as_bool(), Some(true));
    }

    #[test]
    fn set_agent_bin_replaces_path() {
        let out = set_agent_bin(HOST, "codex", "/opt/homebrew/bin/codex").unwrap();
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["codex"]["bin"].as_str(), Some("/opt/homebrew/bin/codex"));
        assert_eq!(doc["agents"]["codex"]["port"].as_integer(), Some(8390));
    }

    #[test]
    fn set_agent_enabled_creates_missing_table() {
        let out = set_agent_enabled("token = \"t\"\n", "pi", true).unwrap();
        let doc: toml_edit::DocumentMut = out.parse().unwrap();
        assert_eq!(doc["agents"]["pi"]["enabled"].as_bool(), Some(true));
        assert_eq!(doc["token"].as_str(), Some("t"));
        let out2 = set_agent_enabled("", "pi", false).unwrap();
        let doc2: toml_edit::DocumentMut = out2.parse().unwrap();
        assert_eq!(doc2["agents"]["pi"]["enabled"].as_bool(), Some(false));
    }

    #[test]
    fn invalid_toml_is_rejected() {
        let err = set_agent_enabled("[agents.codex\nenabled = ", "codex", true).unwrap_err();
        assert!(matches!(err.kind, crate::error::HostErrorKind::ConfigInvalid));
    }

    #[test]
    fn write_atomic_backs_up_once_and_replaces_content() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("host.toml");
        std::fs::write(&path, "v1").unwrap();
        write_atomic(&path, "v2").unwrap();
        write_atomic(&path, "v3").unwrap();
        assert_eq!(std::fs::read_to_string(&path).unwrap(), "v3");
        assert_eq!(std::fs::read_to_string(dir.path().join("host.toml.bak")).unwrap(), "v1");
        assert!(!dir.path().join("host.toml.tmp").exists());
    }

    #[test]
    fn read_or_empty_handles_missing_file() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_or_empty(&dir.path().join("nope.toml")).unwrap(), "");
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
cargo test config 2>&1 | tail -3
# Expected: error[E0583]: file not found for module `config`
```

- [ ] **Step 4: 实现 config.rs**

```rust
use std::path::Path;

use toml_edit::{value, DocumentMut, Item, Table};

use crate::error::HostError;

fn parse(text: &str) -> Result<DocumentMut, HostError> {
    text.parse::<DocumentMut>()
        .map_err(|e| HostError::config_invalid(format!("host.toml: {e}")))
}

fn agent_table<'a>(doc: &'a mut DocumentMut, agent: &str) -> Result<&'a mut Table, HostError> {
    let agents = doc
        .as_table_mut()
        .entry("agents")
        .or_insert_with(|| {
            let mut t = Table::new();
            t.set_implicit(true);
            Item::Table(t)
        })
        .as_table_mut()
        .ok_or_else(|| HostError::config_invalid("host.toml: `agents` is not a table"))?;
    agents
        .entry(agent)
        .or_insert_with(|| Item::Table(Table::new()))
        .as_table_mut()
        .ok_or_else(|| HostError::config_invalid(format!("host.toml: `agents.{agent}` is not a table")))
}

pub fn set_agent_enabled(text: &str, agent: &str, enabled: bool) -> Result<String, HostError> {
    let mut doc = parse(text)?;
    agent_table(&mut doc, agent)?["enabled"] = value(enabled);
    Ok(doc.to_string())
}

pub fn set_agent_bin(text: &str, agent: &str, bin: &str) -> Result<String, HostError> {
    let mut doc = parse(text)?;
    agent_table(&mut doc, agent)?["bin"] = value(bin);
    Ok(doc.to_string())
}

pub fn read_or_empty(path: &Path) -> Result<String, HostError> {
    match std::fs::read_to_string(path) {
        Ok(s) => Ok(s),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(String::new()),
        Err(e) => Err(e.into()),
    }
}

pub fn write_atomic(path: &Path, contents: &str) -> Result<(), HostError> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let bak = path.with_extension("toml.bak");
    if path.exists() && !bak.exists() {
        std::fs::copy(path, &bak)?;
    }
    let tmp = path.with_extension("toml.tmp");
    std::fs::write(&tmp, contents)?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}
```

在 `lib.rs` 加 `mod config;`。

- [ ] **Step 5: 运行确认通过**

```bash
cargo test config 2>&1 | tail -3
# Expected: test result: ok. 6 passed
```

- [ ] **Step 6: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri/src apps/desktop/src-tauri/tests/fixtures/host.toml
git commit -m "desktop: edit host.toml agent settings with toml_edit and atomic writes

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: AppState、日志流与 Tauri 命令

**Files:**
- Create: `apps/desktop/src-tauri/src/state.rs`、`apps/desktop/src-tauri/src/logs.rs`、`apps/desktop/src-tauri/src/commands.rs`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（注册模块、`manage(AppState)`、`invoke_handler`）

**Interfaces:**
- Consumes: Task 4 `sidecar::{run, Subcommand, sidecar_path}`、Task 5 `status::{parse_status, parse_pair, StatusInfo, PairPayload}`、Task 6 `launchd::{plist_path, read_program_path, derive_install_state, HostState}`、Task 7 `config::*`。
- Produces（前端 `invoke` 名称与返回类型，Task 10 依赖）：
  - `host_state() -> HostState`
  - `host_install()`, `host_uninstall()`, `host_start()`, `host_stop()`, `host_restart()`, `host_reload()`, `host_upgrade()` → `()`
  - `pair_payload() -> PairPayload`；`rotate_token() -> ()`
  - `agent_set_enabled(name: String, enabled: bool) -> ()`；`agent_set_bin(name: String, path: String) -> ()`
  - `logs_tail(lines: u32) -> Vec<String>`；`logs_follow_start() -> ()`；`logs_follow_stop() -> ()`（事件 `log-line`，payload `{ line: String }`）
  - `reveal_path(kind: String) -> ()`，`kind ∈ {"config","logs"}`
  - `state::AppState { mutation: tokio::sync::Mutex<()>, follow: std::sync::Mutex<Option<CommandChild>> }`；`state::Settings { last_seen_version: Option<String>, quit_notice_shown: bool }` 及 `Settings::load(app) / save(app)`（`app_data_dir()/settings.json`）。
  - `state::run_mutating(app, state, cmd) -> Result<(), HostError>`：持锁执行一个 mutating 子命令。

- [ ] **Step 1: 写失败测试（state.rs 底部）**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn mutating_commands_are_serialized() {
        // Two tasks race for the mutation lock; the second must observe the first finished.
        let state = std::sync::Arc::new(AppState::default());
        let order = std::sync::Arc::new(std::sync::Mutex::new(Vec::<&'static str>::new()));
        let (s1, o1) = (state.clone(), order.clone());
        let t1 = tokio::spawn(async move {
            let _g = s1.mutation.lock().await;
            o1.lock().unwrap().push("a-start");
            tokio::time::sleep(std::time::Duration::from_millis(50)).await;
            o1.lock().unwrap().push("a-end");
        });
        tokio::time::sleep(std::time::Duration::from_millis(5)).await;
        let (s2, o2) = (state.clone(), order.clone());
        let t2 = tokio::spawn(async move {
            let _g = s2.mutation.lock().await;
            o2.lock().unwrap().push("b-start");
        });
        let _ = tokio::join!(t1, t2);
        assert_eq!(*order.lock().unwrap(), vec!["a-start", "a-end", "b-start"]);
    }

    #[test]
    fn settings_round_trip_through_json() {
        let s = Settings { last_seen_version: Some("0.1.0".into()), quit_notice_shown: true };
        let json = serde_json::to_string(&s).unwrap();
        let back: Settings = serde_json::from_str(&json).unwrap();
        assert_eq!(back, s);
        let empty: Settings = serde_json::from_str("{}").unwrap();
        assert_eq!(empty, Settings::default());
    }
}
```

`Cargo.toml` 的 `tokio` features 加上 `"macros", "rt"`（测试用）：

```toml
tokio = { version = "1", features = ["sync", "time", "macros", "rt"] }
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop/src-tauri && cargo test state 2>&1 | tail -3
# Expected: error[E0583]: file not found for module `state`
```

- [ ] **Step 3: 实现 state.rs**

```rust
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};
use tauri_plugin_shell::process::CommandChild;

use crate::error::HostError;
use crate::sidecar::{self, Subcommand};

#[derive(Default)]
pub struct AppState {
    /// Serializes install/uninstall/stop/restart/reload/rotate/upgrade.
    pub mutation: tokio::sync::Mutex<()>,
    /// Running `logs -f` child, if any.
    pub follow: Mutex<Option<CommandChild>>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub last_seen_version: Option<String>,
    pub quit_notice_shown: bool,
}

impl Settings {
    fn path(app: &AppHandle) -> Result<std::path::PathBuf, HostError> {
        let dir = app.path().app_data_dir().map_err(|e| HostError::config_invalid(e.to_string()))?;
        std::fs::create_dir_all(&dir)?;
        Ok(dir.join("settings.json"))
    }

    pub fn load(app: &AppHandle) -> Settings {
        Self::path(app)
            .and_then(|p| std::fs::read_to_string(p).map_err(HostError::from))
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self, app: &AppHandle) -> Result<(), HostError> {
        let p = Self::path(app)?;
        std::fs::write(p, serde_json::to_string_pretty(self).map_err(|e| HostError::config_invalid(e.to_string()))?)?;
        Ok(())
    }
}

pub async fn run_mutating(app: &AppHandle, state: &AppState, cmd: Subcommand) -> Result<(), HostError> {
    debug_assert!(cmd.is_mutating());
    let _guard = state.mutation.lock().await;
    sidecar::run(app, cmd).await.map(|_| ())
}
```

- [ ] **Step 4: 实现 logs.rs**

```rust
use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_shell::process::CommandEvent;
use tauri_plugin_shell::ShellExt;

use crate::error::HostError;
use crate::sidecar::{self, Subcommand, SIDECAR_NAME};
use crate::state::AppState;

pub const LOG_LINE_EVENT: &str = "log-line";

#[derive(Clone, Serialize)]
struct LogLine {
    line: String,
}

pub async fn tail(app: &AppHandle, lines: u32) -> Result<Vec<String>, HostError> {
    let out = sidecar::run(app, Subcommand::LogsTail(lines as usize)).await?;
    Ok(out.lines().map(str::to_owned).collect())
}

pub fn follow_start(app: &AppHandle) -> Result<(), HostError> {
    let state = app.state::<AppState>();
    let mut slot = state.follow.lock().map_err(|_| HostError::config_invalid("follow lock poisoned"))?;
    if slot.is_some() {
        return Ok(());
    }
    let (mut rx, child) = app
        .shell()
        .sidecar(SIDECAR_NAME)
        .map_err(|e| HostError::sidecar_missing(e.to_string()))?
        .args(Subcommand::LogsFollow(200).args())
        .spawn()
        .map_err(|e| HostError::sidecar_missing(format!("spawn logs -f: {e}")))?;
    *slot = Some(child);
    let handle = app.clone();
    tauri::async_runtime::spawn(async move {
        while let Some(event) = rx.recv().await {
            match event {
                CommandEvent::Stdout(bytes) | CommandEvent::Stderr(bytes) => {
                    let line = String::from_utf8_lossy(&bytes).trim_end().to_string();
                    let _ = handle.emit(LOG_LINE_EVENT, LogLine { line });
                }
                CommandEvent::Terminated(_) => break,
                _ => {}
            }
        }
        if let Ok(mut slot) = handle.state::<AppState>().follow.lock() {
            *slot = None;
        }
    });
    Ok(())
}

pub fn follow_stop(app: &AppHandle) -> Result<(), HostError> {
    let state = app.state::<AppState>();
    let mut slot = state.follow.lock().map_err(|_| HostError::config_invalid("follow lock poisoned"))?;
    if let Some(child) = slot.take() {
        child.kill().map_err(|e| HostError::config_invalid(format!("kill logs -f: {e}")))?;
    }
    Ok(())
}
```

- [ ] **Step 5: 实现 commands.rs**

```rust
use std::path::Path;

use tauri::{AppHandle, State};
use tauri_plugin_opener::OpenerExt;

use crate::config;
use crate::error::HostError;
use crate::launchd::{self, HostState};
use crate::logs;
use crate::sidecar::{self, Subcommand};
use crate::state::{run_mutating, AppState};
use crate::status::{self, PairPayload};

pub async fn compute_host_state(app: &AppHandle) -> Result<HostState, HostError> {
    let sidecar = sidecar::sidecar_path()?;
    let plist = launchd::plist_path()?;
    let plist_exe = launchd::read_program_path(&plist)?;
    let install = launchd::derive_install_state(plist_exe.as_deref(), &sidecar);
    let status = match sidecar::run(app, Subcommand::StatusJson).await {
        Ok(out) => Some(status::parse_status(&out)?),
        Err(e) if matches!(e.kind, crate::error::HostErrorKind::CommandFailed { .. }) => None,
        Err(e) => return Err(e),
    };
    Ok(HostState {
        install,
        running: status.as_ref().map(|s| s.is_running()).unwrap_or(false),
        status,
        app_version: app.package_info().version.to_string(),
        sidecar_path: sidecar.to_string_lossy().into_owned(),
    })
}

#[tauri::command]
pub async fn host_state(app: AppHandle) -> Result<HostState, HostError> {
    compute_host_state(&app).await
}

#[tauri::command]
pub async fn host_install(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Install).await
}

#[tauri::command]
pub async fn host_uninstall(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Uninstall).await
}

#[tauri::command]
pub async fn host_start(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Restart).await
}

#[tauri::command]
pub async fn host_stop(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Stop).await
}

#[tauri::command]
pub async fn host_restart(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Restart).await
}

#[tauri::command]
pub async fn host_reload(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Reload).await
}

#[tauri::command]
pub async fn host_upgrade(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Upgrade).await
}

#[tauri::command]
pub async fn pair_payload(app: AppHandle) -> Result<PairPayload, HostError> {
    let out = sidecar::run(&app, Subcommand::Pair).await?;
    status::parse_pair(&out)
}

#[tauri::command]
pub async fn rotate_token(app: AppHandle, state: State<'_, AppState>) -> Result<(), HostError> {
    run_mutating(&app, &state, Subcommand::Rotate).await
}

async fn config_path(app: &AppHandle) -> Result<std::path::PathBuf, HostError> {
    let out = sidecar::run(app, Subcommand::StatusJson).await?;
    Ok(std::path::PathBuf::from(status::parse_status(&out)?.config_path))
}

#[tauri::command]
pub async fn agent_set_enabled(app: AppHandle, state: State<'_, AppState>, name: String, enabled: bool) -> Result<(), HostError> {
    let path = config_path(&app).await?;
    let text = config::read_or_empty(&path)?;
    config::write_atomic(&path, &config::set_agent_enabled(&text, &name, enabled)?)?;
    run_mutating(&app, &state, Subcommand::Reload).await
}

#[tauri::command]
pub async fn agent_set_bin(app: AppHandle, state: State<'_, AppState>, name: String, path: String) -> Result<(), HostError> {
    let cfg = config_path(&app).await?;
    let text = config::read_or_empty(&cfg)?;
    config::write_atomic(&cfg, &config::set_agent_bin(&text, &name, &path)?)?;
    run_mutating(&app, &state, Subcommand::Reload).await
}

#[tauri::command]
pub async fn logs_tail(app: AppHandle, lines: u32) -> Result<Vec<String>, HostError> {
    logs::tail(&app, lines).await
}

#[tauri::command]
pub fn logs_follow_start(app: AppHandle) -> Result<(), HostError> {
    logs::follow_start(&app)
}

#[tauri::command]
pub fn logs_follow_stop(app: AppHandle) -> Result<(), HostError> {
    logs::follow_stop(&app)
}

#[tauri::command]
pub async fn reveal_path(app: AppHandle, kind: String) -> Result<(), HostError> {
    let target = match kind.as_str() {
        "config" => config_path(&app).await?,
        "logs" => {
            let home = dirs::home_dir().ok_or_else(|| HostError::config_invalid("no home directory"))?;
            home.join("Library/Logs").join(launchd::LABEL)
        }
        other => return Err(HostError::config_invalid(format!("unknown path kind `{other}`"))),
    };
    let target: &Path = &target;
    app.opener()
        .reveal_item_in_dir(target)
        .map_err(|e| HostError::config_invalid(e.to_string()))
}
```

- [ ] **Step 6: 在 lib.rs 里接线**

`lib.rs` 完整内容改为：

```rust
mod commands;
mod config;
mod error;
mod launchd;
mod logs;
mod sidecar;
mod state;
mod status;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_positioner::init())
        .manage(state::AppState::default())
        .setup(|app| {
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            commands::host_state,
            commands::host_install,
            commands::host_uninstall,
            commands::host_start,
            commands::host_stop,
            commands::host_restart,
            commands::host_reload,
            commands::host_upgrade,
            commands::pair_payload,
            commands::rotate_token,
            commands::agent_set_enabled,
            commands::agent_set_bin,
            commands::logs_tail,
            commands::logs_follow_start,
            commands::logs_follow_stop,
            commands::reveal_path,
        ])
        .run(tauri::generate_context!())
        .expect("error while running AgentBuddy");
}
```

- [ ] **Step 7: 运行确认通过**

```bash
cargo test 2>&1 | tail -4
# Expected: 所有测试 ok（sidecar 5 + status 5 + launchd 7 + config 6 + state 2）
cargo check
# Expected: Finished，无 warning 以外的输出
```

- [ ] **Step 8: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri
git commit -m "desktop: Tauri commands for host state, service control, pairing, agents and logs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: 托盘菜单、窗口显隐、后台刷新与退出提示

**Files:**
- Create: `apps/desktop/src-tauri/src/tray.rs`
- Modify: `apps/desktop/src-tauri/src/lib.rs`（`mod tray;`，setup 里调用 `tray::install` 与 `tray::spawn_refresher`；`commands::host_state` 之后调用 `tray::apply`）

**Interfaces:**
- Consumes: Task 8 `commands::compute_host_state`、`state::{AppState, Settings, run_mutating}`、Task 6 `HostState/InstallState`。
- Produces:
  - `fn tray::install(app: &AppHandle) -> tauri::Result<()>`：建菜单、托盘，`app.manage(TrayHandles)`。
  - `fn tray::apply(app: &AppHandle, state: &HostState)`：更新状态行文本、启停项文本、自启勾选。
  - `fn tray::spawn_refresher(app: AppHandle)`：每 30 秒 `compute_host_state` 并 `apply`。
  - `fn tray::show_window(app: &AppHandle, page: Option<&str>)`：显示并聚焦主窗口，`page` 非空时 `emit("navigate", page)`。
  - 事件 `navigate`（payload 字符串 `"overview" | "agents" | "pairing" | "logs"`），Task 10 前端监听。

- [ ] **Step 1: 写失败测试（tray.rs 底部，纯函数部分）**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use crate::launchd::{HostState, InstallState};

    fn state(install: InstallState, running: bool) -> HostState {
        HostState { install, running, status: None, app_version: "0.1.0".into(), sidecar_path: "/x".into() }
    }

    #[test]
    fn labels_follow_the_state_machine() {
        let s = state(InstallState::NotInstalled, false);
        assert_eq!(status_label(&s), "状态：未安装");
        assert_eq!(start_stop_label(&s), "启动主机服务");
        assert!(!autostart_checked(&s));

        let s = state(InstallState::Installed, true);
        assert_eq!(status_label(&s), "状态：运行中");
        assert_eq!(start_stop_label(&s), "停止主机服务");
        assert!(autostart_checked(&s));

        let s = state(InstallState::Installed, false);
        assert_eq!(status_label(&s), "状态：已停止");
        assert_eq!(start_stop_label(&s), "启动主机服务");

        let s = state(InstallState::PathMismatch { plist_exe: "/old".into() }, true);
        assert_eq!(status_label(&s), "状态：需要修复");
        assert!(autostart_checked(&s));
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cargo test tray 2>&1 | tail -3
# Expected: error[E0583]: file not found for module `tray`
```

- [ ] **Step 3: 实现 tray.rs**

```rust
use tauri::menu::{CheckMenuItem, Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, Wry};
use tauri_plugin_dialog::{DialogExt, MessageDialogButtons, MessageDialogKind};
use tauri_plugin_positioner::{Position, WindowExt};

use crate::commands::compute_host_state;
use crate::launchd::{HostState, InstallState};
use crate::sidecar::Subcommand;
use crate::state::{run_mutating, AppState, Settings};

pub struct TrayHandles {
    status: MenuItem<Wry>,
    start_stop: MenuItem<Wry>,
    autostart: CheckMenuItem<Wry>,
}

pub fn status_label(s: &HostState) -> &'static str {
    match (&s.install, s.running) {
        (InstallState::NotInstalled, _) => "状态：未安装",
        (InstallState::PathMismatch { .. }, _) => "状态：需要修复",
        (InstallState::Installed, true) => "状态：运行中",
        (InstallState::Installed, false) => "状态：已停止",
    }
}

pub fn start_stop_label(s: &HostState) -> &'static str {
    if s.running { "停止主机服务" } else { "启动主机服务" }
}

pub fn autostart_checked(s: &HostState) -> bool {
    !matches!(s.install, InstallState::NotInstalled)
}

pub fn show_window(app: &AppHandle, page: Option<&str>) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.as_ref().window().move_window(Position::TrayCenter);
        let _ = w.show();
        let _ = w.set_focus();
        if let Some(p) = page {
            let _ = app.emit("navigate", p);
        }
    }
}

fn toggle_window(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        if w.is_visible().unwrap_or(false) {
            let _ = w.hide();
        } else {
            show_window(app, None);
        }
    }
}

pub fn apply(app: &AppHandle, state: &HostState) {
    if let Some(h) = app.try_state::<TrayHandles>() {
        let _ = h.status.set_text(status_label(state));
        let _ = h.start_stop.set_text(start_stop_label(state));
        let _ = h.autostart.set_checked(autostart_checked(state));
    }
}

async fn refresh(app: &AppHandle) {
    if let Ok(s) = compute_host_state(app).await {
        apply(app, &s);
    }
}

fn spawn_action(app: &AppHandle, pick: impl Fn(&HostState) -> Option<Subcommand> + Send + 'static) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let Ok(current) = compute_host_state(&app).await else { return };
        if let Some(cmd) = pick(&current) {
            let state = app.state::<AppState>();
            let _ = run_mutating(&app, &state, cmd).await;
        }
        refresh(&app).await;
    });
}

fn confirm(app: &AppHandle, title: &str, message: &str) -> bool {
    app.dialog()
        .message(message)
        .title(title)
        .kind(MessageDialogKind::Warning)
        .buttons(MessageDialogButtons::OkCancelCustom("继续".into(), "取消".into()))
        .blocking_show()
}

fn quit(app: &AppHandle) {
    let mut settings = Settings::load(app);
    if !settings.quit_notice_shown {
        app.dialog()
            .message("退出 AgentBuddy 不会停止主机服务，手机仍然可以连接这台 Mac。要停止服务请使用菜单里的「停止主机服务」。")
            .title("退出 AgentBuddy")
            .kind(MessageDialogKind::Info)
            .blocking_show();
        settings.quit_notice_shown = true;
        let _ = settings.save(app);
    }
    app.exit(0);
}

pub fn spawn_refresher(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        loop {
            refresh(&app).await;
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
        }
    });
}

pub fn install(app: &AppHandle) -> tauri::Result<()> {
    let status = MenuItem::with_id(app, "status", "状态：正在检测…", false, None::<&str>)?;
    let open = MenuItem::with_id(app, "open", "打开控制台", true, None::<&str>)?;
    let pair = MenuItem::with_id(app, "pair", "显示配对二维码", true, None::<&str>)?;
    let start_stop = MenuItem::with_id(app, "start_stop", "启动主机服务", true, None::<&str>)?;
    let autostart = CheckMenuItem::with_id(app, "autostart", "开机自启", true, false, None::<&str>)?;
    let quit_item = MenuItem::with_id(app, "quit", "退出 AgentBuddy", true, None::<&str>)?;
    let menu = Menu::with_items(
        app,
        &[
            &status,
            &PredefinedMenuItem::separator(app)?,
            &open,
            &pair,
            &start_stop,
            &autostart,
            &PredefinedMenuItem::separator(app)?,
            &quit_item,
        ],
    )?;

    let icon = app.default_window_icon().cloned().expect("bundle icon configured");
    TrayIconBuilder::with_id("main")
        .icon(icon)
        .icon_as_template(true)
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "open" => show_window(app, Some("overview")),
            "pair" => show_window(app, Some("pairing")),
            "start_stop" => {
                let stop_ok = confirm(app, "停止主机服务", "停止后手机将无法连接这台 Mac，直到再次启动。");
                spawn_action(app, move |s| {
                    if s.running { if stop_ok { Some(Subcommand::Stop) } else { None } } else { Some(Subcommand::Restart) }
                });
            }
            "autostart" => {
                spawn_action(app, |s| match s.install {
                    InstallState::NotInstalled => Some(Subcommand::Install),
                    _ => Some(Subcommand::Uninstall),
                });
            }
            "quit" => quit(app),
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            tauri_plugin_positioner::on_tray_event(tray.app_handle(), &event);
            if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                toggle_window(tray.app_handle());
            }
        })
        .build(app)?;

    app.manage(TrayHandles { status, start_stop, autostart });
    Ok(())
}
```

注意：`autostart` 取消勾选前也应确认（会停止服务）。把 `"autostart"` 分支改成：

```rust
            "autostart" => {
                let uninstall_ok = confirm(app, "关闭开机自启", "这会卸载后台服务并停止它，手机将无法连接。");
                spawn_action(app, move |s| match s.install {
                    InstallState::NotInstalled => Some(Subcommand::Install),
                    _ => if uninstall_ok { Some(Subcommand::Uninstall) } else { None },
                });
            }
```

- [ ] **Step 3b: 启动时的版本升级检查（spec §4）**

在 `tray.rs` 追加：

```rust
/// After the app bundle was replaced, the LaunchAgent still runs the old
/// binary. Compare our version with the last one we saw and hand the daemon
/// over to this binary via `agentbuddy upgrade`.
pub fn spawn_upgrade_check(app: AppHandle) {
    tauri::async_runtime::spawn(async move {
        let current = app.package_info().version.to_string();
        let mut settings = Settings::load(&app);
        if settings.last_seen_version.as_deref() == Some(current.as_str()) {
            return;
        }
        if let Ok(s) = compute_host_state(&app).await {
            if matches!(s.install, InstallState::Installed) && settings.last_seen_version.is_some() {
                let state = app.state::<AppState>();
                let _ = run_mutating(&app, &state, Subcommand::Upgrade).await;
            }
        }
        settings.last_seen_version = Some(current);
        let _ = settings.save(&app);
        refresh(&app).await;
    });
}
```

并在 `tests` 模块里加一个纯逻辑测试，先写、看它失败、再实现下面的辅助函数：

```rust
    #[test]
    fn upgrade_is_needed_only_when_installed_and_version_changed() {
        assert!(!needs_upgrade(None, "0.2.0", &InstallState::Installed));
        assert!(needs_upgrade(Some("0.1.0"), "0.2.0", &InstallState::Installed));
        assert!(!needs_upgrade(Some("0.2.0"), "0.2.0", &InstallState::Installed));
        assert!(!needs_upgrade(Some("0.1.0"), "0.2.0", &InstallState::NotInstalled));
    }
```

```rust
pub fn needs_upgrade(last_seen: Option<&str>, current: &str, install: &InstallState) -> bool {
    matches!(install, InstallState::Installed) && matches!(last_seen, Some(v) if v != current)
}
```

并让 `spawn_upgrade_check` 用 `needs_upgrade(settings.last_seen_version.as_deref(), &current, &s.install)` 代替内联条件。

- [ ] **Step 4: lib.rs 接线**

`mod tray;`；`setup` 闭包改为：

```rust
        .setup(|app| {
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);
            tray::install(app.handle())?;
            tray::spawn_refresher(app.handle().clone());
            tray::spawn_upgrade_check(app.handle().clone());
            Ok(())
        })
```

`commands::host_state` 改为在返回前刷新托盘：

```rust
#[tauri::command]
pub async fn host_state(app: AppHandle) -> Result<HostState, HostError> {
    let s = compute_host_state(&app).await?;
    crate::tray::apply(&app, &s);
    Ok(s)
}
```

- [ ] **Step 5: 运行确认通过并手动冒烟**

```bash
cargo test tray 2>&1 | tail -3
# Expected: test result: ok. 2 passed
cd .. && npm run tauri dev
# Expected: 菜单栏出现图标，无 Dock 图标；点菜单「打开控制台」弹出窗口（内容仍是 Task 2 占位）；关窗口 App 不退出；「退出」弹出一次提示后退出
```

- [ ] **Step 6: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri
git commit -m "desktop: tray menu, hide-on-close window, background refresh and quit notice

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: 前端 host 客户端与轮询 hook

**Files:**
- Create: `apps/desktop/src/lib/host.ts`、`apps/desktop/src/lib/host.test.ts`

**Interfaces:**
- Consumes: Task 8 的命令名与 JSON 形状。
- Produces（Task 11、12 依赖）：
  - 类型 `InstallState`、`AgentInfo`、`StatusInfo`、`HostState`、`PairPayload`、`HostError`（与 Rust `Serialize` 输出一致）。
  - `host.state()`, `host.install()`, `host.uninstall()`, `host.start()`, `host.stop()`, `host.restart()`, `host.reload()`, `host.upgrade()`, `host.pairPayload()`, `host.rotateToken()`, `host.setAgentEnabled(name, enabled)`, `host.setAgentBin(name, path)`, `host.logsTail(lines)`, `host.logsFollowStart()`, `host.logsFollowStop()`, `host.revealPath(kind)`。
  - `isHostError(e: unknown): e is HostError`；`describeError(e: unknown): string`。
  - `pollIntervalMs(visible: boolean, consecutiveFailures = 0): number`（2000 / 30000；连续失败 ≥ 3 次退避到 30000）。
  - `useHostState(): { state: HostState | null; error: HostError | null; refresh: () => Promise<void>; busy: boolean; run: (fn: () => Promise<void>) => Promise<void>; unreachable: boolean }` —— `run` 执行一个动作后自动 `refresh`，并把错误放进 `error`；`unreachable` 在连续 3 次 `host_state` 失败后为 true（spec §7）。

- [ ] **Step 1: 写失败测试**

`apps/desktop/src/lib/host.test.ts`：

```ts
import { describe, it, expect, vi, beforeEach } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));

import { host, isHostError, describeError, pollIntervalMs } from "./host";

beforeEach(() => invoke.mockReset());

describe("host client", () => {
  it("maps methods to command names and argument keys", async () => {
    invoke.mockResolvedValue(undefined);
    await host.setAgentEnabled("codex", false);
    expect(invoke).toHaveBeenCalledWith("agent_set_enabled", { name: "codex", enabled: false });
    await host.setAgentBin("claude", "/usr/local/bin/claude");
    expect(invoke).toHaveBeenCalledWith("agent_set_bin", { name: "claude", path: "/usr/local/bin/claude" });
    await host.logsTail(500);
    expect(invoke).toHaveBeenCalledWith("logs_tail", { lines: 500 });
    await host.revealPath("logs");
    expect(invoke).toHaveBeenCalledWith("reveal_path", { kind: "logs" });
    await host.state();
    expect(invoke).toHaveBeenCalledWith("host_state");
  });

  it("recognises HostError payloads and describes them", () => {
    const err = { kind: { type: "command_failed", code: 1, stderr: "boom" }, detail: "`agentbuddy stop` exited with Some(1): boom" };
    expect(isHostError(err)).toBe(true);
    expect(describeError(err)).toContain("boom");
    expect(isHostError(new Error("x"))).toBe(false);
    expect(describeError(new Error("x"))).toBe("x");
    expect(describeError("plain")).toBe("plain");
  });

  it("polls fast when visible, slow when hidden, and backs off after 3 failures", () => {
    expect(pollIntervalMs(true, 0)).toBe(2000);
    expect(pollIntervalMs(false, 0)).toBe(30000);
    expect(pollIntervalMs(true, 2)).toBe(2000);
    expect(pollIntervalMs(true, 3)).toBe(30000);
  });
});
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop && npm test -- src/lib/host.test.ts
# Expected: FAIL — Failed to resolve import "./host"
```

- [ ] **Step 3: 实现 host.ts**

```ts
import { invoke } from "@tauri-apps/api/core";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { useCallback, useEffect, useRef, useState } from "react";

export type InstallState =
  | { kind: "not_installed" }
  | { kind: "installed" }
  | { kind: "path_mismatch"; plist_exe: string };

export interface AgentInfo {
  name: string;
  display_name: string;
  wire: unknown;
  available: boolean;
  presentation?: unknown;
  capabilities?: unknown;
}

export interface StatusInfo {
  pid: number;
  node_id: string;
  token_short: string;
  relay: string | null;
  config_path: string;
  uptime_secs: number;
  agents: AgentInfo[];
}

export interface HostState {
  install: InstallState;
  running: boolean;
  status: StatusInfo | null;
  app_version: string;
  sidecar_path: string;
}

export interface PairPayload {
  raw: string;
  node_id: string;
  token: string;
  host_name: string | null;
  relay: string | null;
}

export type HostErrorKind =
  | { type: "sidecar_missing" }
  | { type: "command_failed"; code: number | null; stderr: string }
  | { type: "parse_failed" }
  | { type: "permission_denied" }
  | { type: "config_invalid" };

export interface HostError {
  kind: HostErrorKind;
  detail: string;
}

export type PathKind = "config" | "logs";

export const host = {
  state: () => invoke<HostState>("host_state"),
  install: () => invoke<void>("host_install"),
  uninstall: () => invoke<void>("host_uninstall"),
  start: () => invoke<void>("host_start"),
  stop: () => invoke<void>("host_stop"),
  restart: () => invoke<void>("host_restart"),
  reload: () => invoke<void>("host_reload"),
  upgrade: () => invoke<void>("host_upgrade"),
  pairPayload: () => invoke<PairPayload>("pair_payload"),
  rotateToken: () => invoke<void>("rotate_token"),
  setAgentEnabled: (name: string, enabled: boolean) =>
    invoke<void>("agent_set_enabled", { name, enabled }),
  setAgentBin: (name: string, path: string) => invoke<void>("agent_set_bin", { name, path }),
  logsTail: (lines: number) => invoke<string[]>("logs_tail", { lines }),
  logsFollowStart: () => invoke<void>("logs_follow_start"),
  logsFollowStop: () => invoke<void>("logs_follow_stop"),
  revealPath: (kind: PathKind) => invoke<void>("reveal_path", { kind }),
};

export function isHostError(e: unknown): e is HostError {
  return (
    typeof e === "object" &&
    e !== null &&
    "detail" in e &&
    "kind" in e &&
    typeof (e as HostError).detail === "string"
  );
}

export function describeError(e: unknown): string {
  if (isHostError(e)) return e.detail;
  if (e instanceof Error) return e.message;
  return String(e);
}

export function pollIntervalMs(visible: boolean, consecutiveFailures = 0): number {
  if (consecutiveFailures >= 3) return 30000;
  return visible ? 2000 : 30000;
}

async function windowVisible(): Promise<boolean> {
  try {
    return await getCurrentWindow().isVisible();
  } catch {
    return true;
  }
}

export function useHostState() {
  const [state, setState] = useState<HostState | null>(null);
  const [error, setError] = useState<HostError | null>(null);
  const [busy, setBusy] = useState(false);
  const [unreachable, setUnreachable] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const failures = useRef(0);

  const refresh = useCallback(async () => {
    try {
      setState(await host.state());
      setError(null);
      failures.current = 0;
      setUnreachable(false);
    } catch (e) {
      failures.current += 1;
      if (failures.current >= 3) setUnreachable(true);
      setError(isHostError(e) ? e : { kind: { type: "parse_failed" }, detail: describeError(e) });
    }
  }, []);

  const run = useCallback(
    async (fn: () => Promise<void>) => {
      setBusy(true);
      try {
        await fn();
        setError(null);
      } catch (e) {
        setError(isHostError(e) ? e : { kind: { type: "parse_failed" }, detail: describeError(e) });
      } finally {
        setBusy(false);
        await refresh();
      }
    },
    [refresh],
  );

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      await refresh();
      if (cancelled) return;
      const visible = await windowVisible();
      timer.current = setTimeout(tick, pollIntervalMs(visible, failures.current));
    };
    void tick();
    return () => {
      cancelled = true;
      if (timer.current) clearTimeout(timer.current);
    };
  }, [refresh]);

  return { state, error, refresh, busy, run, unreachable };
}
```

- [ ] **Step 4: 运行确认通过**

```bash
npm test -- src/lib/host.test.ts
# Expected: 3 passed
```

- [ ] **Step 5: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src/lib
git commit -m "desktop: typed invoke client and host state polling hook

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: 应用外壳、主题、概览页、配对页

**Files:**
- Create: `apps/desktop/src/theme.css`、`apps/desktop/src/App.css`、`apps/desktop/src/components/ErrorBanner.tsx`、`apps/desktop/src/components/StatusBadge.tsx`、`apps/desktop/src/components/Nav.tsx`、`apps/desktop/src/pages/Overview.tsx`、`apps/desktop/src/pages/Overview.test.tsx`、`apps/desktop/src/pages/Pairing.tsx`、`apps/desktop/src/pages/Pairing.test.tsx`
- Modify: `apps/desktop/src/App.tsx`（替换占位）、`apps/desktop/src/main.tsx`（引入 css）

**Interfaces:**
- Consumes: Task 10 `host`、`useHostState`、类型；Task 9 事件 `navigate`。
- Produces：`type Page = "overview" | "agents" | "pairing" | "logs"`（`App.tsx` 导出）；`Overview({ state, busy, run })`、`Pairing({ running })` 组件 props；Task 12 在 `App.tsx` 的 `switch` 里接入 `Agents`、`Logs`。

- [ ] **Step 1: 写失败测试**

`apps/desktop/src/pages/Overview.test.tsx`：

```tsx
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { Overview } from "./Overview";
import type { HostState } from "../lib/host";

const base: HostState = {
  install: { kind: "installed" },
  running: true,
  status: {
    pid: 4242, node_id: "abcdef1234567890", token_short: "ab12", relay: "https://relay.example",
    config_path: "/Users/me/Library/Application Support/com.akashark.agentbuddycli/host.toml",
    uptime_secs: 3725, agents: [],
  },
  app_version: "0.1.0",
  sidecar_path: "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy",
};

describe("Overview", () => {
  it("shows running state, node id and formatted uptime", () => {
    render(<Overview state={base} busy={false} run={async (f) => f()} />);
    expect(screen.getByText("运行中")).toBeInTheDocument();
    expect(screen.getByText("abcdef1234567890")).toBeInTheDocument();
    expect(screen.getByText("1 小时 2 分")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "停止" })).toBeInTheDocument();
  });

  it("shows the onboarding card when not installed and installs on click", async () => {
    const run = vi.fn(async (f: () => Promise<void>) => f());
    const install = vi.fn(async () => {});
    render(<Overview state={{ ...base, install: { kind: "not_installed" }, running: false, status: null }} busy={false} run={run} installAction={install} />);
    fireEvent.click(screen.getByRole("button", { name: "安装后台服务" }));
    expect(run).toHaveBeenCalled();
    expect(install).toHaveBeenCalled();
  });

  it("offers repair on path mismatch", () => {
    render(<Overview state={{ ...base, install: { kind: "path_mismatch", plist_exe: "/old/agentbuddy" } }} busy={false} run={async (f) => f()} />);
    expect(screen.getByText(/服务指向旧版本或旧位置/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "修复" })).toBeInTheDocument();
  });
});
```

`apps/desktop/src/pages/Pairing.test.tsx`：

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/window", () => ({ getCurrentWindow: () => ({ isVisible: async () => true }) }));
vi.mock("@tauri-apps/plugin-clipboard-manager", () => ({ writeText: vi.fn(async () => {}) }));

import { Pairing } from "./Pairing";

describe("Pairing", () => {
  it("renders a QR code from the raw payload and shows host details", async () => {
    invoke.mockImplementation(async (cmd: string) => {
      if (cmd === "pair_payload") {
        return { raw: '{"v":1,"node_id":"node123","token":"tok"}', node_id: "node123", token: "tok", host_name: "studio", relay: null };
      }
      return undefined;
    });
    const { container } = render(<Pairing running={true} />);
    await waitFor(() => expect(container.querySelector("svg")).not.toBeNull());
    expect(screen.getByText("studio")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "复制 payload" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "轮换 token" })).toBeInTheDocument();
  });

  it("tells the user to start the service first when not running", () => {
    render(<Pairing running={false} />);
    expect(screen.getByText(/先启动主机服务/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行确认失败**

```bash
npm test -- src/pages
# Expected: FAIL — Failed to resolve import "./Overview" / "./Pairing"
```

- [ ] **Step 3: 主题与外壳**

`apps/desktop/src/theme.css`：

```css
:root {
  --bg: #000000;
  --fg: #e6e6e6;
  --accent: #00ff9c;
  --muted: #8a8a8a;
  --danger: #ff5f57;
  --surface: #111111;
  --border: #262626;
  --mono: "SF Mono", ui-monospace, Menlo, monospace;
  color-scheme: dark;
}
html, body, #root { height: 100%; margin: 0; background: var(--bg); color: var(--fg); font-family: var(--mono); font-size: 13px; }
button { font: inherit; color: var(--fg); background: var(--surface); border: 1px solid var(--border); border-radius: 6px; padding: 6px 12px; cursor: pointer; }
button:hover { border-color: var(--accent); }
button.primary { background: var(--accent); color: #000; border-color: var(--accent); }
button.danger { border-color: var(--danger); color: var(--danger); }
button:disabled { opacity: 0.5; cursor: default; }
input { font: inherit; color: var(--fg); background: var(--bg); border: 1px solid var(--border); border-radius: 6px; padding: 6px 8px; }
code, .mono { font-family: var(--mono); }
.muted { color: var(--muted); }
```

`apps/desktop/src/App.css`：

```css
.shell { display: grid; grid-template-columns: 160px 1fr; height: 100%; }
.nav { border-right: 1px solid var(--border); padding: 12px 8px; display: flex; flex-direction: column; gap: 4px; }
.nav button { text-align: left; background: transparent; border-color: transparent; }
.nav button.active { color: var(--accent); border-color: var(--border); }
.page { padding: 16px 20px; overflow: auto; display: flex; flex-direction: column; gap: 14px; }
.row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.kv { display: grid; grid-template-columns: 120px 1fr; row-gap: 8px; column-gap: 12px; align-items: baseline; }
.card { background: var(--surface); border: 1px solid var(--border); border-radius: 10px; padding: 16px; }
.badge { display: inline-block; padding: 2px 10px; border-radius: 999px; border: 1px solid var(--border); }
.badge.ok { color: var(--accent); border-color: var(--accent); }
.badge.warn { color: var(--danger); border-color: var(--danger); }
.banner { background: #1a0d0d; border: 1px solid var(--danger); border-radius: 8px; padding: 10px 12px; display: flex; gap: 12px; align-items: flex-start; }
.banner pre { margin: 0; white-space: pre-wrap; flex: 1; }
table { border-collapse: collapse; width: 100%; }
td, th { border-bottom: 1px solid var(--border); padding: 8px 6px; text-align: left; vertical-align: middle; }
.log { background: var(--surface); border: 1px solid var(--border); border-radius: 8px; padding: 8px; height: 100%; overflow: auto; white-space: pre; font-size: 12px; }
```

`main.tsx` 顶部加 `import "./theme.css"; import "./App.css";`。

`apps/desktop/src/components/StatusBadge.tsx`：

```tsx
import type { HostState } from "../lib/host";

export function statusText(s: HostState): { text: string; ok: boolean } {
  if (s.install.kind === "not_installed") return { text: "未安装", ok: false };
  if (s.install.kind === "path_mismatch") return { text: "需要修复", ok: false };
  return s.running ? { text: "运行中", ok: true } : { text: "已停止", ok: false };
}

export function StatusBadge({ state }: { state: HostState }) {
  const { text, ok } = statusText(state);
  return <span className={`badge ${ok ? "ok" : "warn"}`}>{text}</span>;
}
```

`apps/desktop/src/components/ErrorBanner.tsx`：

```tsx
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import type { HostError } from "../lib/host";

export function ErrorBanner({ error, appVersion }: { error: HostError | null; appVersion?: string }) {
  if (!error) return null;
  const diag = JSON.stringify({ app_version: appVersion, ...error }, null, 2);
  return (
    <div className="banner" role="alert">
      <pre>{error.detail}</pre>
      <button onClick={() => void writeText(diag)}>复制诊断信息</button>
    </div>
  );
}
```

`apps/desktop/src/components/Nav.tsx`：

```tsx
import type { Page } from "../App";

const items: { id: Page; label: string }[] = [
  { id: "overview", label: "概览" },
  { id: "agents", label: "Agents" },
  { id: "pairing", label: "配对" },
  { id: "logs", label: "日志" },
];

export function Nav({ page, onSelect }: { page: Page; onSelect: (p: Page) => void }) {
  return (
    <nav className="nav">
      {items.map((it) => (
        <button key={it.id} className={it.id === page ? "active" : ""} onClick={() => onSelect(it.id)}>
          {it.label}
        </button>
      ))}
    </nav>
  );
}
```

`apps/desktop/src/App.tsx`：

```tsx
import { useEffect, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { Nav } from "./components/Nav";
import { ErrorBanner } from "./components/ErrorBanner";
import { Overview } from "./pages/Overview";
import { Pairing } from "./pages/Pairing";
import { useHostState } from "./lib/host";

export type Page = "overview" | "agents" | "pairing" | "logs";

const pages: Page[] = ["overview", "agents", "pairing", "logs"];

export default function App() {
  const [page, setPage] = useState<Page>("overview");
  const { state, error, busy, run, unreachable } = useHostState();

  useEffect(() => {
    const un = listen<string>("navigate", (e) => {
      if ((pages as string[]).includes(e.payload)) setPage(e.payload as Page);
    });
    return () => { void un.then((f) => f()); };
  }, []);

  return (
    <div className="shell">
      <Nav page={page} onSelect={setPage} />
      <main className="page">
        {unreachable && <span className="badge warn">无法连接控制通道</span>}
        <ErrorBanner error={error} appVersion={state?.app_version} />
        {state === null ? (
          <p className="muted">正在读取主机状态…</p>
        ) : page === "overview" ? (
          <Overview state={state} busy={busy} run={run} />
        ) : page === "pairing" ? (
          <Pairing running={state.running} />
        ) : (
          <p className="muted">页面建设中</p>
        )}
      </main>
    </div>
  );
}
```

- [ ] **Step 4: 概览页**

`apps/desktop/src/pages/Overview.tsx`：

```tsx
import { host, type HostState } from "../lib/host";
import { StatusBadge } from "../components/StatusBadge";

export function formatUptime(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  if (h > 0) return `${h} 小时 ${m} 分`;
  if (m > 0) return `${m} 分`;
  return `${secs} 秒`;
}

interface Props {
  state: HostState;
  busy: boolean;
  run: (fn: () => Promise<void>) => Promise<void>;
  /** injectable for tests; defaults to host.install */
  installAction?: () => Promise<void>;
}

export function Overview({ state, busy, run, installAction = host.install }: Props) {
  if (state.install.kind === "not_installed") {
    return (
      <section className="card">
        <h2>把这台 Mac 变成 AgentBuddy 主机</h2>
        <p className="muted">
          安装后台服务后，守护进程会随登录自动启动，退出本 App 也不受影响。手机 App 通过「配对」页的二维码连接这台 Mac。
        </p>
        <button className="primary" disabled={busy} onClick={() => void run(installAction)}>
          安装后台服务
        </button>
      </section>
    );
  }

  const s = state.status;
  return (
    <>
      {state.install.kind === "path_mismatch" && (
        <div className="banner">
          <pre>服务指向旧版本或旧位置：{state.install.plist_exe}</pre>
          <button disabled={busy} onClick={() => void run(async () => { await host.install(); await host.restart(); })}>
            修复
          </button>
        </div>
      )}
      <div className="row">
        <StatusBadge state={state} />
        {state.running ? (
          <button className="danger" disabled={busy} onClick={() => void run(host.stop)}>停止</button>
        ) : (
          <button className="primary" disabled={busy} onClick={() => void run(host.start)}>启动</button>
        )}
        <button disabled={busy} onClick={() => void run(host.restart)}>重启</button>
        <button disabled={busy || !state.running} onClick={() => void run(host.reload)}>重载配置</button>
      </div>
      <div className="kv">
        <span className="muted">节点 id</span>
        <span className="row">
          <code>{s?.node_id ?? "—"}</code>
          {s && <button onClick={() => void navigator.clipboard?.writeText(s.node_id)}>复制</button>}
        </span>
        <span className="muted">relay</span><code>{s?.relay ?? "默认"}</code>
        <span className="muted">运行时长</span><span>{state.running && s ? formatUptime(s.uptime_secs) : "—"}</span>
        <span className="muted">守护进程</span><code>{state.sidecar_path}</code>
        <span className="muted">App 版本</span><code>{state.app_version}</code>
        <span className="muted">配置</span>
        <span className="row"><code>{s?.config_path ?? "—"}</code><button onClick={() => void host.revealPath("config")}>在 Finder 中显示</button></span>
        <span className="muted">日志</span>
        <span className="row"><button onClick={() => void host.revealPath("logs")}>在 Finder 中显示</button></span>
      </div>
    </>
  );
}
```

- [ ] **Step 5: 配对页**

`apps/desktop/src/pages/Pairing.tsx`：

```tsx
import { useCallback, useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { writeText } from "@tauri-apps/plugin-clipboard-manager";
import { host, describeError, type PairPayload } from "../lib/host";

export function Pairing({ running }: { running: boolean }) {
  const [payload, setPayload] = useState<PairPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  const load = useCallback(async () => {
    try {
      setPayload(await host.pairPayload());
      setError(null);
    } catch (e) {
      setError(describeError(e));
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const rotate = async () => {
    setConfirming(false);
    try {
      await host.rotateToken();
      await load();
    } catch (e) {
      setError(describeError(e));
    }
  };

  return (
    <>
      {!running && <p className="banner">请先启动主机服务，手机扫码后才能连上。</p>}
      {error && <p className="banner">{error}</p>}
      {payload && (
        <div className="row" style={{ alignItems: "flex-start", gap: 24 }}>
          <div className="card" style={{ background: "#000" }}>
            <QRCodeSVG value={payload.raw} size={240} bgColor="#000000" fgColor="#00ff9c" level="M" />
          </div>
          <div className="kv">
            <span className="muted">主机名</span><span>{payload.host_name ?? "—"}</span>
            <span className="muted">节点 id</span><code>{payload.node_id.slice(0, 12)}…</code>
            <span className="muted">token 指纹</span><code>{payload.token.slice(0, 6)}…{payload.token.slice(-4)}</code>
            <span className="muted">操作</span>
            <span className="row">
              <button onClick={() => void writeText(payload.raw)}>复制 payload</button>
              <button className="danger" onClick={() => setConfirming(true)}>轮换 token</button>
            </span>
          </div>
        </div>
      )}
      {confirming && (
        <div className="card">
          <p>轮换后所有已配对的手机都需要重新扫码。继续？</p>
          <div className="row">
            <button className="danger" onClick={() => void rotate()}>继续轮换</button>
            <button onClick={() => setConfirming(false)}>取消</button>
          </div>
        </div>
      )}
      <p className="muted">手机 App → 添加服务器 → 扫码。</p>
    </>
  );
}
```

- [ ] **Step 6: 运行确认通过**

```bash
npm test -- src/pages
# Expected: 5 passed
npm run build
# Expected: tsc 无错误
```

- [ ] **Step 7: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src
git commit -m "desktop: app shell, dark theme, overview and pairing pages

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12: Agents 页与日志页

**Files:**
- Create: `apps/desktop/src/pages/Agents.tsx`、`apps/desktop/src/pages/Agents.test.tsx`、`apps/desktop/src/pages/Logs.tsx`、`apps/desktop/src/pages/Logs.test.tsx`
- Modify: `apps/desktop/src/App.tsx`（接入两页）

**Interfaces:**
- Consumes: Task 10 `host`、`AgentInfo`、`StatusInfo`；Task 8 事件 `log-line { line }`。
- Produces: `Agents({ agents, busy, run })`、`Logs({ active })` 组件。

- [ ] **Step 1: 写失败测试**

`apps/desktop/src/pages/Agents.test.tsx`：

```tsx
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn(async () => undefined);
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/plugin-dialog", () => ({ open: vi.fn(async () => "/opt/homebrew/bin/claude") }));

import { Agents } from "./Agents";
import type { AgentInfo } from "../lib/host";

const agents: AgentInfo[] = [
  { name: "codex", display_name: "Codex", wire: "codex", available: true },
  { name: "claude", display_name: "Claude Code", wire: "acp", available: false },
];

describe("Agents", () => {
  it("lists agents with availability and toggles enabled through the host client", async () => {
    render(<Agents agents={agents} busy={false} run={async (f) => f()} />);
    expect(screen.getByText("Codex")).toBeInTheDocument();
    expect(screen.getAllByText("未找到")).toHaveLength(1);
    fireEvent.click(screen.getByLabelText("启用 Claude Code"));
    expect(invoke).toHaveBeenCalledWith("agent_set_enabled", { name: "claude", enabled: true });
  });

  it("lets the user pick a binary with the file dialog", async () => {
    render(<Agents agents={agents} busy={false} run={async (f) => f()} />);
    fireEvent.click(screen.getAllByRole("button", { name: "选择…" })[1]);
    await Promise.resolve();
    expect(invoke).toHaveBeenCalledWith("agent_set_bin", { name: "claude", path: "/opt/homebrew/bin/claude" });
  });
});
```

`apps/desktop/src/pages/Logs.test.tsx`：

```tsx
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";

const invoke = vi.fn();
const listeners: Array<(e: { payload: { line: string } }) => void> = [];
vi.mock("@tauri-apps/api/core", () => ({ invoke: (...a: unknown[]) => invoke(...a) }));
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(async (_name: string, cb: (e: { payload: { line: string } }) => void) => { listeners.push(cb); return () => {}; }),
}));

import { Logs } from "./Logs";

describe("Logs", () => {
  it("loads the tail, filters lines and appends streamed lines when following", async () => {
    invoke.mockImplementation(async (cmd: string) => (cmd === "logs_tail" ? ["INFO started", "WARN slow"] : undefined));
    render(<Logs active={true} />);
    await waitFor(() => expect(screen.getByText(/INFO started/)).toBeInTheDocument());
    fireEvent.change(screen.getByPlaceholderText("过滤"), { target: { value: "WARN" } });
    expect(screen.queryByText(/INFO started/)).toBeNull();
    fireEvent.click(screen.getByLabelText("跟随"));
    await waitFor(() => expect(invoke).toHaveBeenCalledWith("logs_follow_start"));
    listeners.forEach((cb) => cb({ payload: { line: "WARN streamed" } }));
    await waitFor(() => expect(screen.getByText(/WARN streamed/)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: 运行确认失败**

```bash
cd apps/desktop && npm test -- src/pages/Agents.test.tsx src/pages/Logs.test.tsx
# Expected: FAIL — Failed to resolve import "./Agents" / "./Logs"
```

- [ ] **Step 3: 实现 Agents.tsx**

```tsx
import { useState } from "react";
import { open } from "@tauri-apps/plugin-dialog";
import { host, type AgentInfo } from "../lib/host";

interface Props {
  agents: AgentInfo[];
  busy: boolean;
  run: (fn: () => Promise<void>) => Promise<void>;
}

/** The daemon reports availability; `enabled` lives in host.toml and is not
 *  echoed back by `status --json`, so we track the last value the user set. */
export function Agents({ agents, busy, run }: Props) {
  const [enabled, setEnabled] = useState<Record<string, boolean>>({});
  const [bins, setBins] = useState<Record<string, string>>({});

  const pick = async (a: AgentInfo) => {
    const chosen = await open({ multiple: false, directory: false, title: `选择 ${a.display_name} 可执行文件` });
    if (typeof chosen === "string") {
      setBins((b) => ({ ...b, [a.name]: chosen }));
      await run(() => host.setAgentBin(a.name, chosen));
    }
  };

  return (
    <table>
      <thead>
        <tr><th>Agent</th><th>可执行文件</th><th>启用</th><th>路径</th></tr>
      </thead>
      <tbody>
        {agents.map((a) => (
          <tr key={a.name}>
            <td>{a.display_name}</td>
            <td>{a.available ? <span className="badge ok">可用</span> : <span className="badge warn">未找到</span>}</td>
            <td>
              <input
                type="checkbox"
                aria-label={`启用 ${a.display_name}`}
                disabled={busy}
                checked={enabled[a.name] ?? a.available}
                onChange={(e) => {
                  const next = e.target.checked;
                  setEnabled((m) => ({ ...m, [a.name]: next }));
                  void run(() => host.setAgentEnabled(a.name, next));
                }}
              />
            </td>
            <td className="row">
              <code>{bins[a.name] ?? a.name}</code>
              <button disabled={busy} onClick={() => void pick(a)}>选择…</button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
```

- [ ] **Step 4: 实现 Logs.tsx**

```tsx
import { useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import { host } from "../lib/host";

const MAX_LINES = 2000;

export function Logs({ active }: { active: boolean }) {
  const [lines, setLines] = useState<string[]>([]);
  const [filter, setFilter] = useState("");
  const [follow, setFollow] = useState(false);
  const box = useRef<HTMLPreElement>(null);

  useEffect(() => {
    if (!active) return;
    void host.logsTail(500).then(setLines).catch(() => setLines(["(无法读取日志)"]));
  }, [active]);

  useEffect(() => {
    if (!active || !follow) return;
    let un: (() => void) | undefined;
    void host.logsFollowStart();
    void listen<{ line: string }>("log-line", (e) => {
      setLines((prev) => [...prev, e.payload.line].slice(-MAX_LINES));
    }).then((f) => { un = f; });
    return () => {
      un?.();
      void host.logsFollowStop();
    };
  }, [active, follow]);

  useEffect(() => {
    if (follow && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [lines, follow]);

  const shown = filter ? lines.filter((l) => l.includes(filter)) : lines;

  return (
    <>
      <div className="row">
        <input placeholder="过滤" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <label className="row">
          <input type="checkbox" aria-label="跟随" checked={follow} onChange={(e) => setFollow(e.target.checked)} />
          跟随
        </label>
        <button onClick={() => void host.revealPath("logs")}>打开日志目录</button>
      </div>
      <pre className="log" ref={box}>{shown.join("\n")}</pre>
    </>
  );
}
```

- [ ] **Step 5: 接入 App.tsx**

把 `App.tsx` 里 `page === "pairing" ? … : (<p className="muted">页面建设中</p>)` 改为：

```tsx
        ) : page === "pairing" ? (
          <Pairing running={state.running} />
        ) : page === "agents" ? (
          <Agents agents={state.status?.agents ?? []} busy={busy} run={run} />
        ) : (
          <Logs active={page === "logs"} />
        )}
```

并在顶部加 `import { Agents } from "./pages/Agents"; import { Logs } from "./pages/Logs";`。

- [ ] **Step 6: 运行确认通过**

```bash
npm test
# Expected: 所有前端测试通过（host 3 + Overview 3 + Pairing 2 + Agents 2 + Logs 1）
npm run build
npm run tauri dev
# 手动：四页可切换，托盘「显示配对二维码」直接跳到配对页
```

- [ ] **Step 7: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src
git commit -m "desktop: agents and logs pages

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13: 手机端配对文案改为桌面 App（iOS + Android 同步）

**Files:**
- Modify: `apps/ios/Sources/AgentBuddy/Views/DiscoveryView.swift:258`、`apps/ios/Sources/AgentBuddy/Views/AlleycatAddServerSheet.swift:221,542`、`apps/ios/Sources/AgentBuddy/zh-Hans.lproj/Localizable.strings:153`、`apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/discovery/DiscoveryScreen.kt:568`、`apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/discovery/AlleycatAddServerSheet.kt:616`

**Interfaces:** 无代码接口；只改字面量。

- [ ] **Step 1: iOS**

`DiscoveryView.swift:258` 的 subtitle 改为：

```swift
                    subtitle: "Install the AgentBuddy desktop app on your Mac, open its Pairing page, then scan the QR code.",
```

`AlleycatAddServerSheet.swift` 的两处命令常量改为包内 CLI 路径（供终端用户复制）：

```swift
    private static let pairCommandLabel = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"
```

```swift
    private static let pairCommand = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"
```

`zh-Hans.lproj/Localizable.strings:153` 整行替换为：

```
"Install the AgentBuddy desktop app on your Mac, open its Pairing page, then scan the QR code." = "在 Mac 上安装 AgentBuddy 桌面版，打开「配对」页后扫码。";
```

- [ ] **Step 2: Android**

`DiscoveryScreen.kt:568`：

```kotlin
                subtitle = "在 Mac 上安装 AgentBuddy 桌面版，打开「配对」页后扫码。",
```

`AlleycatAddServerSheet.kt:616`：

```kotlin
private const val PAIR_COMMAND = "/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr"
```

- [ ] **Step 3: 验证**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git grep -n 'npx agentbuddycli' -- apps/ios apps/android
# Expected: no output
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
(cd apps/android && ./gradlew :app:testDebugUnitTest -Plitter.enableGhosttyAndroid=false --console=plain | tail -3)
# Expected: BUILD SUCCESSFUL
xcodebuild build -project apps/ios/AgentBuddy.xcodeproj -scheme AgentBuddy -configuration Debug -destination 'generic/platform=iOS Simulator' -derivedDataPath /tmp/agentbuddy-dd CODE_SIGNING_ALLOWED=NO 2>&1 | grep -E 'error:' | grep -v 'linker command failed' || echo "no compile errors"
# Expected: no compile errors（仅缺模拟器静态库的链接错误属预期）
```

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Sources apps/android/app/src/main
git commit -m "mobile: point pairing instructions at the AgentBuddy desktop app

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 14: sidecar 契约测试

**Files:**
- Create: `apps/desktop/src-tauri/tests/sidecar_contract.rs`

**Interfaces:**
- Consumes: Task 3 产出的 `binaries/agentbuddy-<triple>`；Task 5 `parse_status` / `parse_pair`（通过 `agentbuddy_desktop_lib` 的 `pub mod status`，本任务把 `status` 模块改为 `pub`）。

- [ ] **Step 1: 把 status 模块公开**

`lib.rs` 里 `mod status;` 改为 `pub mod status;`，`mod error;` 改为 `pub mod error;`。

- [ ] **Step 2: 写测试**

`apps/desktop/src-tauri/tests/sidecar_contract.rs`：

```rust
//! Runs the real daemon binary and checks that its JSON still matches what
//! the app parses. Gated by AGENTBUDDY_SIDECAR so `cargo test` stays offline
//! and fast by default; CI sets it to the freshly built sidecar.

use std::process::Command;

use agentbuddy_desktop_lib::status::{parse_pair, parse_status};

fn sidecar() -> Option<String> {
    std::env::var("AGENTBUDDY_SIDECAR").ok()
}

fn run(args: &[&str]) -> String {
    let bin = sidecar().unwrap();
    let out = Command::new(&bin).args(args).output().expect("spawn sidecar");
    String::from_utf8_lossy(&out.stdout).into_owned()
}

#[test]
fn status_json_matches_app_types() {
    if sidecar().is_none() {
        eprintln!("AGENTBUDDY_SIDECAR not set; skipping");
        return;
    }
    let s = parse_status(&run(&["status", "--json"])).expect("status --json parses");
    assert!(!s.node_id.is_empty());
    assert!(s.config_path.ends_with("host.toml"));
    assert!(s.agents.iter().any(|a| a.name == "codex"));
}

#[test]
fn pair_output_matches_app_types() {
    if sidecar().is_none() {
        eprintln!("AGENTBUDDY_SIDECAR not set; skipping");
        return;
    }
    let p = parse_pair(&run(&["pair"])).expect("pair parses");
    assert!(!p.node_id.is_empty());
    assert!(!p.token.is_empty());
}
```

- [ ] **Step 3: 运行（未设环境变量应跳过，设了应通过）**

```bash
cd apps/desktop/src-tauri
cargo test --test sidecar_contract 2>&1 | tail -3
# Expected: 2 passed（内部打印 skipping）
AGENTBUDDY_SIDECAR="$PWD/binaries/agentbuddy-$(rustc --print host-tuple)" cargo test --test sidecar_contract 2>&1 | tail -3
# Expected: 2 passed，且没有 skipping
```

- [ ] **Step 4: Commit**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git add apps/desktop/src-tauri
git commit -m "desktop: contract test against the real sidecar CLI

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 15: CI 发布 workflow 与文档

**Files:**
- Create: `.github/workflows/desktop-release.yml`、`apps/desktop/README.md`、`apps/desktop/docs/qa.md`
- Modify: `README.md`（「Mac 端守护进程」段与仓库布局）、`AGENTS.md`（结构、放置规则、前置条件）

**Interfaces:** 无代码接口。

- [ ] **Step 1: workflow**

`.github/workflows/desktop-release.yml`：

```yaml
name: Desktop release

on:
  push:
    tags: ["desktop-v*"]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    strategy:
      fail-fast: false
      matrix:
        include:
          - target: aarch64-apple-darwin
          - target: x86_64-apple-darwin
    runs-on: macos-15
    env:
      CARGO_TERM_COLOR: always
    steps:
      - uses: actions/checkout@v6
        with:
          persist-credentials: false
          submodules: false

      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: apps/desktop/package-lock.json

      - uses: dtolnay/rust-toolchain@stable
        with:
          targets: ${{ matrix.target }}

      - name: Install frontend deps
        working-directory: apps/desktop
        run: npm ci

      - name: Run Rust unit tests
        working-directory: apps/desktop/src-tauri
        run: cargo test

      - name: Run frontend tests
        working-directory: apps/desktop
        run: npm test

      - name: Build sidecar
        run: ./apps/desktop/scripts/build-sidecar.sh ${{ matrix.target }}

      - name: Sidecar contract test
        working-directory: apps/desktop/src-tauri
        env:
          AGENTBUDDY_SIDECAR: ${{ github.workspace }}/apps/desktop/src-tauri/binaries/agentbuddy-${{ matrix.target }}
        run: cargo test --test sidecar_contract

      - name: Sync version
        run: node apps/desktop/scripts/sync-version.mjs

      - name: Import Developer ID certificate
        env:
          CERT_P12_B64: ${{ secrets.MAC_DEVELOPER_ID_CERT_P12_B64 }}
          CERT_PASSWORD: ${{ secrets.MAC_DEVELOPER_ID_CERT_PASSWORD }}
        run: |
          KEYCHAIN_PASSWORD="$(uuidgen)"
          echo "$CERT_P12_B64" | base64 --decode > "$RUNNER_TEMP/cert.p12"
          security create-keychain -p "$KEYCHAIN_PASSWORD" build.keychain
          security default-keychain -s build.keychain
          security unlock-keychain -p "$KEYCHAIN_PASSWORD" build.keychain
          security set-keychain-settings -t 3600 -u build.keychain
          security import "$RUNNER_TEMP/cert.p12" -k build.keychain -P "$CERT_PASSWORD" -T /usr/bin/codesign
          security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" build.keychain
          IDENTITY="$(security find-identity -v -p codesigning build.keychain | grep 'Developer ID Application' | head -1 | awk -F'"' '{print $2}')"
          test -n "$IDENTITY"
          echo "APPLE_SIGNING_IDENTITY=$IDENTITY" >> "$GITHUB_ENV"

      - name: Write App Store Connect API key
        env:
          ASC_PRIVATE_KEY_P8_B64: ${{ secrets.ASC_PRIVATE_KEY_P8_B64 }}
        run: |
          echo "$ASC_PRIVATE_KEY_P8_B64" | base64 --decode > "$RUNNER_TEMP/AuthKey.p8"
          echo "APPLE_API_KEY_PATH=$RUNNER_TEMP/AuthKey.p8" >> "$GITHUB_ENV"

      - uses: tauri-apps/tauri-action@v0
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          APPLE_SIGNING_IDENTITY: ${{ env.APPLE_SIGNING_IDENTITY }}
          APPLE_API_KEY: ${{ secrets.ASC_KEY_ID }}
          APPLE_API_ISSUER: ${{ secrets.ASC_ISSUER_ID }}
          APPLE_API_KEY_PATH: ${{ env.APPLE_API_KEY_PATH }}
        with:
          projectPath: apps/desktop
          tagName: ${{ github.ref_name }}
          releaseName: "AgentBuddy Desktop ${{ github.ref_name }}"
          releaseDraft: true
          prerelease: false
          args: --target ${{ matrix.target }} --bundles dmg
```

验证 YAML：

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/desktop-release.yml')); print('yaml ok')"
```

- [ ] **Step 2: apps/desktop/README.md**

```markdown
# AgentBuddy 桌面主机 App

macOS 菜单栏应用（Tauri v2 + React/TS）。把 `agentbuddy` 守护进程作为 sidecar 打包，
以 LaunchAgent `com.akashark.agentbuddycli` 安装运行，并提供概览 / Agents / 配对 / 日志四页控制台。
设计见 `docs/superpowers/specs/2026-09-23-desktop-host-app-design.md`。

## 开发

```bash
make desktop-sidecar      # 编 services/kittylitter 并放进 src-tauri/binaries/
make desktop-dev          # tauri dev（前端 http://localhost:1420）
cd apps/desktop && npm test && (cd src-tauri && cargo test)
```

前置：Node 22、rustup 的 stable 工具链、`npm ci`。首次 `make desktop-sidecar` 会编译 alleycat，约 5-10 分钟。

## 发布

```bash
APPLE_SIGNING_IDENTITY="Developer ID Application: … (HNKUYWPBVC)" \
APPLE_API_KEY=<key id> APPLE_API_ISSUER=<issuer id> APPLE_API_KEY_PATH=~/.appstoreconnect/private_keys/AuthKey_<key id>.p8 \
make desktop-dist
```

CI：打 `desktop-vX.Y.Z` tag 触发 `.github/workflows/desktop-release.yml`，产出 arm64 与 x86_64 两个已签名公证的 dmg 到 GitHub Releases 草稿。

## 目录

- `src/` React 前端；`src/lib/host.ts` 是唯一的 `invoke` 入口。
- `src-tauri/src/` Rust 后端；`sidecar.rs` 是唯一执行守护进程 CLI 的地方，子命令白名单在 `Subcommand`。
- `scripts/build-sidecar.sh`、`scripts/sync-version.mjs`。
- `docs/qa.md` 手工验收清单。
```

- [ ] **Step 3: apps/desktop/docs/qa.md**

```markdown
# 桌面主机 App 手工 QA

每次发版前在一台干净的 Mac（或新建用户）上按顺序执行，全部通过才算过。

1. 安装 dmg，拖进 Applications，首次打开：菜单栏出现图标，无 Dock 图标，Gatekeeper 无警告。
2. 打开控制台 → 概览显示引导卡片 → 点「安装后台服务」→ 状态变为「运行中」，`launchctl print gui/$(id -u)/com.akashark.agentbuddycli` 有输出。
3. 配对页出现二维码；手机 App「添加服务器 → 扫码」连上并能列出 agent。
4. 退出 App（首次弹提示）→ 手机仍能新建会话。
5. 重启 Mac，不打开 App → 手机仍能连接。
6. 把 App 移到 ~/Desktop 再打开 → 概览出现「服务指向旧版本或旧位置」→ 点修复 → 状态恢复「运行中」。
7. 配对页「轮换 token」→ 旧手机被拒绝，重新扫码后恢复。
8. Agents 页关闭一个 agent → 手机端该 agent 消失；重新开启后恢复。
9. 日志页「跟随」打开后能看到新日志实时滚动。
10. 托盘「停止主机服务」→ 确认 → 状态「已停止」，手机连接失败；「启动主机服务」恢复。
```

- [ ] **Step 4: README.md 与 AGENTS.md**

`README.md`：把「### Mac 端守护进程」整段替换为：

```markdown
### Mac 端

下载 [Releases](https://github.com/AkaShark/AgentBuddy/releases/latest) 里的 `AgentBuddy_<版本>_<架构>.dmg`，拖进 Applications 后打开。
首次启动在菜单栏图标 → 打开控制台 → 「安装后台服务」，随后在「配对」页用手机 App 扫码。守护进程作为
LaunchAgent 独立运行，退出 App 或重启 Mac 后手机仍能连接。终端用户也可以直接调用包内的守护进程：

```bash
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr
```
```

仓库布局树里 `services/kittylitter/` 一行改为 `services/kittylitter/          Mac 守护进程二进制（alleycat 封装，作为桌面 App 的 sidecar）`，并在 `apps/android/` 后加一行 `apps/desktop/                  macOS 菜单栏主机 App（Tauri v2 + React/TS）`。README 架构图中 `Mac 守护进程  agentbuddycli（alleycat 的品牌封装，services/kittylitter）` 改为 `Mac 桌面 App 内置的守护进程 agentbuddy（alleycat 的品牌封装，services/kittylitter）`。

`AGENTS.md`：
- 「Project Structure」加：`- \`apps/desktop/\` is the Tauri v2 macOS menu-bar host app: \`src/\` React + TS console, \`src-tauri/\` Rust backend that drives the bundled \`agentbuddy\` sidecar through its CLI (\`sidecar.rs\` holds the subcommand allowlist). It never links alleycat and never touches the mobile Rust crate.`
- 「Feature Placement Rules」加：`- Desktop host app: anything the console needs from the daemon must come from an existing sidecar subcommand (\`status --json\`, \`pair\`, …). If the CLI lacks it, add it to the alleycat fork first; do not scrape log files or reimplement daemon logic in the app.`
- 「Fresh Checkout Prerequisites」加第 5 条：`Node 22 and \`npm ci\` in \`apps/desktop\` for the desktop app; \`make desktop-sidecar\` needs the rustup toolchain.`
- Fork Notes 的 **Branding** 里 `Mac daemon npm package: agentbuddycli (binary agentbuddy, …)` 改为 `Mac daemon binary \`agentbuddy\` (crate \`agentbuddycli\` in \`services/kittylitter\`, shipped as the desktop app's sidecar; no npm publishing)`。

- [ ] **Step 5: 验证与提交**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
git grep -n 'npx agentbuddycli\|npm install -g agentbuddycli' -- . ':!docs/superpowers'
# Expected: no output
git add .github/workflows/desktop-release.yml apps/desktop/README.md apps/desktop/docs/qa.md README.md AGENTS.md
git commit -m "desktop: release workflow, docs and QA checklist

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## 完成后

- 阶段 1.5（删 Catalyst target 与靠近配对）和阶段 2（alleycat 公共 API、设备/会话页、updater）各写单独的 spec 与 plan，不在本计划内。
- 全部任务完成后按 superpowers:executing-plans / subagent-driven-development 的收尾流程做一次整分支复审。
