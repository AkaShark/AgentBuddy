# mfcli（MyFlicker）ACP Agent 接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让配对了 Mac 的 Android 手机能选择 MyFlicker（本机 `mfcli acp`），新建任务、多轮对话、切换模型和思考强度、回看和续接历史（含终端里开的会话）。

**Architecture:** 在 AkaShark/alleycat fork 里修通用 `acp-bridge`（握手、进程生命周期、模型与思考强度、辅助进程、模型列表），新增薄包装 crate `mfcli-bridge`（多项目 `thread/list` 和 cwd 索引），在 daemon 注册 `mfcli` agent。AgentBuddy 只改 alleycat pin、Android 选择器显示项和文档。手机侧 agent 列表本来就由主机数据驱动。

**Tech Stack:** Rust 2024（tokio、serde_json、async-trait、dashmap）、ACP（Agent Client Protocol，stdio JSON-RPC）、Codex app-server 协议（`alleycat-codex-proto`）、Kotlin / Compose（Android）、GNU make。

**Spec:** `docs/superpowers/specs/2026-09-30-mfcli-acp-agent-design.md`（先读它，第 2 节是本机探测得到的 mfcli 实际行为）。

## Global Constraints

- alleycat 工作区：`~/Desktop/Project/Person/Project/alleycat-mfcli`，分支 `feat/mfcli-agent`，基于 `a5bdd83f1dedc9169610efe5a245f82bb0198f13`（`feat/host-push-notifications`）。永远不要运行 `AGENTBUDDY_REFRESH_ALLEYCAT=1`。
- 推送到 `AkaShark/alleycat` 或 AgentBuddy 远端前必须单独征得用户同意；不要推到 `dnakov/alleycat`。
- agent id `mfcli`；显示名 / title `MyFlicker`；aliases `myflicker`、`codeflicker`；description `Kuaishou MyFlicker coding agent (mfcli acp).`；`is_beta: true`；`sort_order: 9`（shell 改为 10）；`supports_ssh_bridge: false`；其余能力位 false。
- 配置 `[agents.mfcli]`：`enabled = true`、`bin = "mfcli"`。
- mfcli 的 ACP 客户端能力：`{"fs":{"readTextFile":false,"writeTextFile":false},"terminal":false}`；通用默认值不变（fs 读写和 terminal 都是 true）。
- effort → `thought_level`：none / minimal / low → `low`；medium → `medium`；high → `high`；xhigh / max → `xhigh`。
- 池：通用默认容量 4、闲置 300 秒不变；mfcli 容量 8；辅助进程 key 为 `<agent>:<node_id>:aux`。
- 权限请求继续自动允许（不改 `handle_permission_request`）。
- Android 只用 make 目标（`make android`、`make android-install`、`make test-android`），不直接跑 `./gradlew`，不用模拟器，设备是 Pixel 9。本轮不构建 iOS，任何情况下都不用 iOS 模拟器。
- 每个 Kotlin / Swift 文件不超过 500 行。
- AgentBuddy 提交只暂存本计划改动的文件或改动块；提交信息用祈使句加 scope，末尾加 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- Shell 注意：用户的 zsh profile 每次都会打印 `setValueForKeyFakeAssocArray … _encode` 噪声，忽略即可；zsh 下 glob 要加引号（如 `--include='*.rs'`）。

## Review Focus

1. 手机在 daemon 重启后直接对老任务发 turn（没先 `thread/resume`），bridge 不知道 cwd：不能让 mfcli 在 `/` 下执行工具。应当依次用 bridge 记下的 cwd、请求里的 `cwd`、mfcli-bridge 索引补上（Task 2 的 `turn_start_uses_request_cwd_when_session_unknown`、Task 6 的 `seeds_inner_cwd_from_index`）。
2. mfcli 进程中途崩溃或被杀：下一轮应透明重建进程、重新初始化并恢复会话，而不是一直报 `connection closed`（Task 2 的 `crashed_process_is_replaced`）。
3. 手机发来 agent 不提供的 model / effort（占位模型 `fake`、`minimal`、`max`）：turn 照常进行，不报错（Task 3 的 `unknown_model_is_skipped_and_max_effort_maps_to_xhigh`）。
4. 同事机器上没有 `~/.codeflicker/data.json` 或文件损坏：列表退回 cwd 索引，不报错（Task 6 的 `project_dirs_*`、`collect_sessions_skips_failing_cwd`）。
5. daemon 的 PATH 里找不到 `mfcli`：daemon 正常启动，mfcli 报为不可用；bridge 构建是惰性的，不能因为缺二进制失败（Task 6 的 `build_succeeds_when_binary_is_missing`）。

---

## 文件结构

**alleycat（`~/Desktop/Project/Person/Project/alleycat-mfcli`）**

| 文件 | 职责 |
|---|---|
| `crates/acp-bridge/tests/support/fake_acp_agent.rs`（新，bin `fake-acp-agent`） | 模拟 mfcli 行为的测试 ACP agent |
| `crates/acp-bridge/tests/support/mod.rs`（新） | 测试 harness：构建 bridge、Conn、读取 agent 收到的帧 |
| `crates/acp-bridge/tests/{handshake,lifecycle,config,aux,models}.rs`（新） | 各任务的集成测试 |
| `crates/acp-bridge/src/config_options.rs`（新） | ACP `configOptions` 纯函数：查找、当前值、effort 映射、待发变更 |
| `crates/acp-bridge/src/translate.rs` | `protocolVersion: 1`、客户端能力参数化 |
| `crates/acp-bridge/src/acp_client.rs` | 错误 details、关闭标记、`ensure_initialized`、已加载会话集合 |
| `crates/acp-bridge/src/pool.rs` | 重建进程时重新初始化、丢弃已关闭 client |
| `crates/acp-bridge/src/bridge.rs` | 新状态（能力、cwd、configOptions）、builder 开关、辅助 client、`recycle_process` |
| `crates/acp-bridge/src/handlers.rs` | initialize、thread/start、thread/resume、turn/start、thread/list、model/list |
| `crates/acp-bridge/README.md` | 按现状重写 |
| `crates/mfcli-bridge/`（新 crate） | `MfcliBridge`、`projects.rs`、`index.rs`、`listing.rs` |
| `crates/alleycat/src/{config.rs,agent_manifest.rs,agents.rs,push/mod.rs}`、`Cargo.toml`、`crates/alleycat/Cargo.toml` | 注册 `mfcli` |

**AgentBuddy（本仓库，分支 `feat/mfcli-agent`）**

| 文件 | 职责 |
|---|---|
| `apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/discovery/DiscoveryChooser.kt` | 配对前预览列表加 `mfcli` |
| `apps/android/docs/qa-matrix.md` | mfcli 手工验收行 |
| `services/kittylitter/Cargo.toml`、`shared/rust-bridge/Cargo.toml`（及各自 `Cargo.lock`） | alleycat pin |
| `CLAUDE.md` | alleycat fork 说明 |

---

### Task 1: 握手修复与测试 harness

**Files:**
- Create: `crates/acp-bridge/tests/support/fake_acp_agent.rs`
- Create: `crates/acp-bridge/tests/support/mod.rs`
- Create: `crates/acp-bridge/tests/handshake.rs`
- Modify: `crates/acp-bridge/Cargo.toml`
- Modify: `crates/acp-bridge/src/translate.rs:6-28`
- Modify: `crates/acp-bridge/tests/translation_test.rs:3-22`
- Modify: `crates/acp-bridge/src/acp_client.rs`（`send_request_inner` 的错误分支）
- Modify: `crates/acp-bridge/src/bridge.rs`（builder、`AcpBridge` 字段、`build()`、`Bridge::initialize`）
- Modify: `crates/acp-bridge/src/handlers.rs:26-50`

**Interfaces:**
- Produces: `translate::codex_to_acp_initialize(codex_params: &Value, client_capabilities: &Value) -> anyhow::Result<Value>`；`translate::default_client_capabilities() -> Value`；`AcpBridgeBuilder::client_capabilities(self, Value) -> Self`；`AcpBridge::client_capabilities(&self) -> &Value`；`AcpBridge::session_key(ctx: &Conn) -> String`（关联函数）；测试 `support::Harness { bridge, session, dir }`，方法 `new`、`with`、`conn`、`initialize`、`call`、`frames`、`methods`、`project_dir`。
- fake agent 的 argv：`--log <path> --config <path>`。config 键：`sessions_by_cwd`、`page_size`、`prompt_delay_ms`、`exit_after_prompts`、`capabilities`、`fail`（method → details）。

- [ ] **Step 1: 建 worktree 并确认基线**

```bash
git -C ~/Desktop/Project/Person/Project/alleycat worktree add \
  ~/Desktop/Project/Person/Project/alleycat-mfcli \
  -b feat/mfcli-agent a5bdd83f1dedc9169610efe5a245f82bb0198f13
cd ~/Desktop/Project/Person/Project/alleycat-mfcli
cargo test -p alleycat-acp-bridge 2>&1 | tail -5
```

Expected: worktree 创建成功；测试全部 PASS（只有 `translation_test.rs` 和模块内测试）。

- [ ] **Step 2: 写 fake ACP agent**

Create `crates/acp-bridge/tests/support/fake_acp_agent.rs`:

```rust
//! Test-only ACP agent for acp-bridge integration tests.
//!
//! Mirrors the mfcli (`mfcli acp`) behaviour verified on 2026-09-30:
//! integer `protocolVersion`, `Agent not initialized` before
//! `initialize`, a per-process session table (`Session X not found`),
//! `configOptions` with `currentValue`, and `session/list` filtered by cwd.
//!
//! argv: `--log <path>` appends every inbound line; `--config <path>` is a
//! JSON object with optional keys:
//! - `sessions_by_cwd`: `{ "/abs": [ {sessionId, cwd, title, updatedAt} ] }`
//! - `page_size`: session/list page size (default: everything in one page)
//! - `prompt_delay_ms`: sleep inside session/prompt
//! - `exit_after_prompts`: exit after this many completed prompts
//! - `capabilities`: replaces the default `agentCapabilities`
//! - `fail`: `{ "<method>": "<details>" }` answers that method with an error

use std::collections::{HashMap, HashSet};
use std::io::{self, BufRead, Write};
use std::time::Duration;

use serde_json::{Value, json};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let log = arg(&args, "--log");
    let config = arg(&args, "--config")
        .and_then(|p| std::fs::read_to_string(p).ok())
        .and_then(|s| serde_json::from_str::<Value>(&s).ok())
        .unwrap_or_else(|| json!({}));
    let mut agent = FakeAgent::new(config);
    for line in io::stdin().lock().lines() {
        let Ok(line) = line else { break };
        if line.trim().is_empty() {
            continue;
        }
        if let Some(path) = &log {
            append(path, &line);
        }
        let Ok(frame) = serde_json::from_str::<Value>(&line) else {
            continue;
        };
        agent.handle(frame);
        if agent.should_exit {
            break;
        }
    }
}

fn arg(args: &[String], name: &str) -> Option<String> {
    args.iter()
        .position(|a| a == name)
        .and_then(|i| args.get(i + 1).cloned())
}

fn append(path: &str, line: &str) {
    let mut file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .expect("open fake agent log");
    writeln!(file, "{line}").expect("write fake agent log");
}

fn out(value: Value) {
    let mut stdout = io::stdout().lock();
    writeln!(stdout, "{value}").expect("write stdout");
    stdout.flush().expect("flush stdout");
}

fn ok(id: &Value, result: Value) {
    out(json!({"jsonrpc": "2.0", "id": id, "result": result}));
}

fn err(id: &Value, code: i64, details: &str) {
    let message = if code == -32602 { "Invalid params" } else { "Internal error" };
    out(json!({
        "jsonrpc": "2.0",
        "id": id,
        "error": {"code": code, "message": message, "data": {"details": details}},
    }));
}

fn update(session_id: &str, update: Value) {
    out(json!({
        "jsonrpc": "2.0",
        "method": "session/update",
        "params": {"sessionId": session_id, "update": update},
    }));
}

struct FakeAgent {
    config: Value,
    initialized: bool,
    sessions: HashSet<String>,
    session_config: HashMap<String, HashMap<String, String>>,
    next_session: u64,
    prompts_done: u64,
    should_exit: bool,
}

impl FakeAgent {
    fn new(config: Value) -> Self {
        Self {
            config,
            initialized: false,
            sessions: HashSet::new(),
            session_config: HashMap::new(),
            next_session: 0,
            prompts_done: 0,
            should_exit: false,
        }
    }

    fn current(&self, session_id: &str, config_id: &str, default: &str) -> String {
        self.session_config
            .get(session_id)
            .and_then(|c| c.get(config_id))
            .cloned()
            .unwrap_or_else(|| default.to_string())
    }

    fn config_options(&self, session_id: &str) -> Value {
        json!([
            {
                "id": "model", "name": "Model", "category": "model", "type": "select",
                "currentValue": self.current(session_id, "model", "fake/alpha"),
                "options": [
                    {"name": "Fake Alpha", "value": "fake/alpha"},
                    {"name": "Fake Beta", "value": "fake/beta"}
                ]
            },
            {
                "id": "thought_level", "name": "Thinking", "category": "thought_level", "type": "select",
                "currentValue": self.current(session_id, "thought_level", "low"),
                "options": [
                    {"name": "Low", "value": "low"},
                    {"name": "Medium", "value": "medium"},
                    {"name": "High", "value": "high"},
                    {"name": "Xhigh", "value": "xhigh"}
                ]
            }
        ])
    }

    fn handle(&mut self, frame: Value) {
        let Some(id) = frame.get("id").cloned() else {
            return; // notification (e.g. session/cancel): logged only
        };
        let Some(method) = frame.get("method").and_then(Value::as_str) else {
            return;
        };
        let method = method.to_string();
        let params = frame.get("params").cloned().unwrap_or_else(|| json!({}));

        if method == "initialize" {
            if !params.get("protocolVersion").is_some_and(Value::is_u64) {
                return err(&id, -32602, "protocolVersion: expected number, received string");
            }
            self.initialized = true;
            let caps = self.config.get("capabilities").cloned().unwrap_or_else(|| {
                json!({
                    "loadSession": true,
                    "sessionCapabilities": {"list": {}, "resume": {}},
                    "promptCapabilities": {"image": true, "embeddedContext": true}
                })
            });
            return ok(&id, json!({"protocolVersion": 1, "agentCapabilities": caps}));
        }
        if !self.initialized {
            return err(&id, -32603, "Agent not initialized");
        }
        if let Some(details) = self.config.pointer(&format!("/fail/{}", method.replace('/', "~1"))) {
            return err(&id, -32603, details.as_str().unwrap_or("failure"));
        }
        let session_id = params
            .get("sessionId")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string();
        match method.as_str() {
            "session/new" => {
                self.next_session += 1;
                let sid = format!("fake-{}-{}", std::process::id(), self.next_session);
                self.sessions.insert(sid.clone());
                ok(&id, json!({"sessionId": sid, "configOptions": self.config_options(&sid)}));
            }
            "session/load" => {
                self.sessions.insert(session_id.clone());
                update(&session_id, json!({"sessionUpdate": "user_message_chunk", "content": {"type": "text", "text": "earlier question"}}));
                update(&session_id, json!({"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": "earlier answer"}}));
                ok(&id, json!({"configOptions": self.config_options(&session_id)}));
            }
            "session/resume" => {
                self.sessions.insert(session_id.clone());
                ok(&id, json!({"configOptions": self.config_options(&session_id)}));
            }
            "session/list" => {
                let cwd = params.get("cwd").and_then(Value::as_str).unwrap_or("");
                let all: Vec<Value> = self
                    .config
                    .pointer("/sessions_by_cwd")
                    .and_then(|m| m.get(cwd))
                    .and_then(Value::as_array)
                    .cloned()
                    .unwrap_or_default();
                let start: usize = params
                    .get("cursor")
                    .and_then(Value::as_str)
                    .and_then(|c| c.parse().ok())
                    .unwrap_or(0);
                let size = self
                    .config
                    .get("page_size")
                    .and_then(Value::as_u64)
                    .map(|n| n as usize)
                    .unwrap_or(usize::MAX);
                let end = start.saturating_add(size).min(all.len());
                let next = (end < all.len()).then(|| end.to_string());
                ok(&id, json!({"sessions": all[start.min(all.len())..end], "nextCursor": next}));
            }
            "session/set_config_option" => {
                let config_id = params.get("configId").and_then(Value::as_str).unwrap_or("");
                let value = params.get("value").and_then(Value::as_str).unwrap_or("");
                let options = self.config_options(&session_id);
                let allowed = options
                    .as_array()
                    .into_iter()
                    .flatten()
                    .find(|o| o["id"] == config_id)
                    .and_then(|o| o["options"].as_array())
                    .is_some_and(|opts| opts.iter().any(|o| o["value"] == value));
                if !allowed {
                    return err(&id, -32602, &format!("invalid value {value} for {config_id}"));
                }
                self.session_config
                    .entry(session_id.clone())
                    .or_default()
                    .insert(config_id.to_string(), value.to_string());
                ok(&id, json!({"configOptions": self.config_options(&session_id)}));
            }
            "session/prompt" => {
                if !self.sessions.contains(&session_id) {
                    return err(&id, -32603, &format!("Session {session_id} not found"));
                }
                if let Some(ms) = self.config.get("prompt_delay_ms").and_then(Value::as_u64) {
                    std::thread::sleep(Duration::from_millis(ms));
                }
                update(&session_id, json!({"sessionUpdate": "agent_message_chunk", "content": {"type": "text", "text": "ok"}}));
                ok(&id, json!({"stopReason": "end_turn"}));
                self.prompts_done += 1;
                if self.config.get("exit_after_prompts").and_then(Value::as_u64) == Some(self.prompts_done) {
                    self.should_exit = true;
                }
            }
            other => err(&id, -32601, &format!("Method not found: {other}")),
        }
    }
}
```

- [ ] **Step 3: 注册 bin，写 harness**

In `crates/acp-bridge/Cargo.toml`, add after the existing `[[bin]]` block:

```toml
# Test-only ACP agent used by the integration tests in `tests/`.
[[bin]]
name = "fake-acp-agent"
path = "tests/support/fake_acp_agent.rs"
```

`alleycat-bridge-core`、`serde_json` 已经是依赖，`[dev-dependencies]` 里已有 `tempfile = { workspace = true }`，其余不用改。

Create `crates/acp-bridge/tests/support/mod.rs`:

```rust
//! Shared helpers for acp-bridge integration tests.
//!
//! Each test builds its own bridge pointed at the `fake-acp-agent` binary
//! with a private log + config file, so tests in one binary can run in
//! parallel without sharing env vars.

#![allow(dead_code)]

use std::path::{Path, PathBuf};
use std::sync::Arc;

use alleycat_acp_bridge::{AcpBridge, AcpBridgeBuilder};
use alleycat_bridge_core::session::Session;
use alleycat_bridge_core::{Bridge, Conn, JsonRpcError};
use serde_json::{Value, json};

pub fn fake_agent_path() -> PathBuf {
    PathBuf::from(env!("CARGO_BIN_EXE_fake-acp-agent"))
}

pub struct Harness {
    pub bridge: Arc<AcpBridge>,
    pub session: Arc<Session>,
    pub dir: tempfile::TempDir,
}

impl Harness {
    pub async fn new(config: Value) -> Self {
        Self::with(config, |b| b).await
    }

    pub async fn with(
        config: Value,
        customize: impl FnOnce(AcpBridgeBuilder) -> AcpBridgeBuilder,
    ) -> Self {
        let dir = tempfile::tempdir().expect("tempdir");
        let config_path = dir.path().join("config.json");
        std::fs::write(&config_path, config.to_string()).expect("write config");
        std::fs::create_dir_all(dir.path().join("project")).expect("project dir");
        let log_path = dir.path().join("frames.jsonl");
        let builder = AcpBridge::builder().agent_bin(fake_agent_path()).agent_args(vec![
            "--log".into(),
            log_path.display().to_string(),
            "--config".into(),
            config_path.display().to_string(),
        ]);
        let bridge = customize(builder).build().await.expect("build bridge");
        let session = Arc::new(Session::new("fake", "test-node".into(), 64, 1 << 20));
        Self { bridge, session, dir }
    }

    pub fn conn(&self) -> Conn {
        Conn::from_session(Arc::clone(&self.session))
    }

    /// Absolute project directory tests pass as `cwd`.
    pub fn project_dir(&self) -> String {
        self.dir.path().join("project").display().to_string()
    }

    pub async fn initialize(&self) -> Value {
        self.bridge
            .initialize(&self.conn(), json!({"clientInfo": {"name": "test", "version": "0"}}))
            .await
            .expect("initialize")
    }

    pub async fn call(&self, method: &str, params: Value) -> Result<Value, JsonRpcError> {
        self.bridge.dispatch(&self.conn(), method, params).await
    }

    pub async fn start_thread(&self) -> String {
        let started = self
            .call("thread/start", json!({"cwd": self.project_dir()}))
            .await
            .expect("thread/start");
        started["thread"]["id"].as_str().expect("thread id").to_string()
    }

    /// Every line the fake agent received, across all spawned processes.
    pub fn frames(&self) -> Vec<Value> {
        read_frames(&self.dir.path().join("frames.jsonl"))
    }

    pub fn methods(&self) -> Vec<String> {
        self.frames()
            .iter()
            .filter_map(|f| f.get("method").and_then(Value::as_str).map(str::to_string))
            .collect()
    }

    pub fn last_frame(&self, method: &str) -> Value {
        self.frames()
            .into_iter()
            .filter(|f| f["method"] == method)
            .last()
            .unwrap_or_else(|| panic!("agent never received {method}"))
    }
}

fn read_frames(path: &Path) -> Vec<Value> {
    std::fs::read_to_string(path)
        .unwrap_or_default()
        .lines()
        .filter_map(|l| serde_json::from_str(l).ok())
        .collect()
}

pub fn text_input(text: &str) -> Value {
    json!([{"type": "text", "text": text}])
}
```

- [ ] **Step 4: 写失败的测试**

Create `crates/acp-bridge/tests/handshake.rs`:

```rust
mod support;

use serde_json::json;
use support::Harness;

#[tokio::test]
async fn initialize_sends_integer_protocol_version() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let init = h.last_frame("initialize");
    assert_eq!(init["params"]["protocolVersion"], json!(1));
    assert_eq!(init["params"]["clientCapabilities"]["terminal"], json!(true));
    assert_eq!(init["params"]["clientCapabilities"]["fs"]["writeTextFile"], json!(true));
}

#[tokio::test]
async fn client_capabilities_are_configurable() {
    let caps = json!({"fs": {"readTextFile": false, "writeTextFile": false}, "terminal": false});
    let h = Harness::with(json!({}), |b| b.client_capabilities(caps.clone())).await;
    h.initialize().await;
    assert_eq!(h.last_frame("initialize")["params"]["clientCapabilities"], caps);
}

#[tokio::test]
async fn agent_error_details_reach_the_caller() {
    let h = Harness::new(json!({"fail": {"session/new": "quota exhausted"}})).await;
    h.initialize().await;
    let err = h
        .call("thread/start", json!({"cwd": h.project_dir()}))
        .await
        .unwrap_err();
    assert!(err.message.contains("quota exhausted"), "got: {}", err.message);
}
```

In `crates/acp-bridge/tests/translation_test.rs`, change the first test to pass capabilities and expect the integer:

```rust
    let caps = alleycat_acp_bridge::translate::default_client_capabilities();
    let acp_request = alleycat_acp_bridge::translate::codex_to_acp_initialize(&codex_params, &caps);
    assert!(acp_request.is_ok());

    let acp_request = acp_request.unwrap();
    assert_eq!(acp_request["protocolVersion"], 1);
    assert_eq!(acp_request["clientCapabilities"], caps);
```

- [ ] **Step 5: 确认测试失败**

Run: `cargo test -p alleycat-acp-bridge --test handshake --test translation_test`
Expected: 编译失败（`client_capabilities`、`default_client_capabilities` 不存在）。

- [ ] **Step 6: 实现**

`crates/acp-bridge/src/translate.rs` — replace `codex_to_acp_initialize`:

```rust
/// Client capabilities the bridge advertises when the builder does not
/// override them: the bridge can serve `fs/*` and `terminal/*` requests.
pub fn default_client_capabilities() -> Value {
    serde_json::json!({
        "fs": {"readTextFile": true, "writeTextFile": true},
        "terminal": true,
    })
}

/// Translate Codex InitializeParams to ACP InitializeRequest. ACP defines
/// `protocolVersion` as an integer (uint16); agents that validate it
/// (mfcli) reject the string form.
pub fn codex_to_acp_initialize(
    codex_params: &Value,
    client_capabilities: &Value,
) -> Result<Value, anyhow::Error> {
    let client_info = codex_params.get("clientInfo");
    let acp_request = serde_json::json!({
        "protocolVersion": 1,
        "clientCapabilities": client_capabilities,
        "clientInfo": {
            "name": client_info.and_then(|v| v.get("name")).and_then(|v| v.as_str()).unwrap_or("Alleycat"),
            "version": client_info.and_then(|v| v.get("version")).and_then(|v| v.as_str()).unwrap_or("0.1.0"),
        },
    });
    Ok(acp_request)
}
```

`crates/acp-bridge/src/acp_client.rs` — in `send_request_inner`, replace the error branch body (the block that builds `message` and bails) with:

```rust
        if let Some(error) = response.get("error") {
            error!(?error, "ACP agent returned error");
            // Surface the human-readable `message`, plus `data.details`
            // when the agent puts the real reason there (mfcli always
            // answers `Internal error` and explains in `details`).
            let message = error
                .get("message")
                .and_then(|v| v.as_str())
                .unwrap_or("ACP agent returned an error");
            match error.pointer("/data/details").and_then(|v| v.as_str()) {
                Some(details) if !details.is_empty() && details != message => {
                    anyhow::bail!("{message}: {details}")
                }
                _ => anyhow::bail!("{message}"),
            }
        }
```

`crates/acp-bridge/src/bridge.rs`:
- add field `client_capabilities: Option<Value>` to `AcpBridgeBuilder` (and `None` in `Default`), plus:

```rust
    /// ACP `clientCapabilities` to advertise in `initialize`. Defaults to
    /// `translate::default_client_capabilities()`.
    pub fn client_capabilities(mut self, caps: Value) -> Self {
        self.client_capabilities = Some(caps);
        self
    }
```

- add field `client_capabilities: Value` to `AcpBridge`; in `build()` set it to `self.client_capabilities.unwrap_or_else(crate::translate::default_client_capabilities)`.
- in `build()`, delete the trailing `.from_env()` after the `AcpBridgeConfig { … }` literal (the builder's own `from_env()` already covers the binary entry point; the extra call let `ACP_BRIDGE_*` env vars silently override every ACP agent the daemon builds).
- add to `impl AcpBridge`:

```rust
    /// Pool key for the connection's primary ACP process.
    pub fn session_key(ctx: &Conn) -> String {
        let session = ctx.session();
        format!("{}:{}", session.agent, session.node_id)
    }

    pub fn client_capabilities(&self) -> &Value {
        &self.client_capabilities
    }
```

- replace both inline `format!("{}:{}", session.agent, session.node_id)` computations in `Bridge::initialize` and `Bridge::dispatch` with `Self::session_key(ctx)`, and change `Bridge::initialize` to call `handlers::handle_initialize(&client, self.client_capabilities(), params)`.

`crates/acp-bridge/src/handlers.rs` — `handle_initialize` takes the capabilities:

```rust
pub async fn handle_initialize(
    client: &Arc<AcpClient>,
    client_capabilities: &Value,
    params: Value,
) -> Result<Value, JsonRpcError> {
    let acp_request = translate::codex_to_acp_initialize(&params, client_capabilities).map_err(|e| JsonRpcError {
        code: error_codes::INVALID_PARAMS,
        message: format!("Failed to translate initialize params: {}", e),
        data: None,
    })?;
```

(the rest of the function is unchanged).

- [ ] **Step 7: 确认通过**

Run: `cargo test -p alleycat-acp-bridge`
Expected: PASS（含 handshake 3 个、translation_test 全部）。

- [ ] **Step 8: 提交**

```bash
git add crates/acp-bridge
git commit -m "acp-bridge: integer protocolVersion, configurable client capabilities, error details

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 进程生命周期（重新初始化、死进程替换、会话恢复）

**Files:**
- Modify: `crates/acp-bridge/src/acp_client.rs`（struct、`spawn`、`reader_task`）
- Modify: `crates/acp-bridge/src/pool.rs:40-110`
- Modify: `crates/acp-bridge/src/bridge.rs`（字段、`Bridge::initialize`、新方法）
- Modify: `crates/acp-bridge/src/handlers.rs`（`handle_initialize`、`handle_thread_start`、`handle_thread_resume`、`handle_turn_start`、新 `ensure_session_ready`）
- Test: `crates/acp-bridge/tests/lifecycle.rs`

**Interfaces:**
- Consumes: Task 1 的 `translate::codex_to_acp_initialize(&Value, &Value)`、`AcpBridge::session_key`、`AcpBridge::client_capabilities`、`support::Harness`。
- Produces: `AcpClient::is_closed(&self) -> bool`、`AcpClient::ensure_initialized(&self, request: &Value) -> anyhow::Result<Value>`、`AcpClient::mark_session_loaded(&self, &str)`、`AcpClient::is_session_loaded(&self, &str) -> bool`；`AcpPool::set_init_request(&self, Value)`；`AcpBridge::agent_capabilities(&self) -> Value`、`AcpBridge::set_session_cwd(&self, &str, &str)`、`AcpBridge::session_cwd(&self, &str) -> Option<String>`、`AcpBridge::recycle_process(&self, &Conn)`；`handlers::ensure_session_ready(bridge, client, session_id, fallback_cwd: Option<&str>) -> Result<(), JsonRpcError>`。

- [ ] **Step 1: 写失败的测试**

Create `crates/acp-bridge/tests/lifecycle.rs`:

```rust
mod support;

use std::time::Duration;

use serde_json::json;
use support::{Harness, text_input};

fn tail_after_nth(methods: &[String], name: &str, n: usize) -> Vec<String> {
    let idx = methods
        .iter()
        .enumerate()
        .filter(|(_, m)| *m == name)
        .nth(n)
        .map(|(i, _)| i)
        .unwrap_or_else(|| panic!("no occurrence #{n} of {name} in {methods:?}"));
    methods[idx..].to_vec()
}

#[tokio::test]
async fn reconnect_initialize_is_not_resent_to_live_process() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    h.initialize().await;
    assert_eq!(h.methods().iter().filter(|m| *m == "initialize").count(), 1);
}

#[tokio::test]
async fn respawned_process_is_initialized_and_session_restored() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.call("turn/start", json!({"threadId": sid, "input": text_input("one")})).await.unwrap();

    h.bridge.recycle_process(&h.conn()).await;
    h.call("turn/start", json!({"threadId": sid, "input": text_input("two")})).await.unwrap();

    assert_eq!(
        tail_after_nth(&h.methods(), "initialize", 1),
        vec!["initialize", "session/resume", "session/prompt"]
    );
    assert_eq!(h.last_frame("session/resume")["params"]["cwd"], json!(h.project_dir()));
}

#[tokio::test]
async fn crashed_process_is_replaced() {
    let h = Harness::new(json!({"exit_after_prompts": 1})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.call("turn/start", json!({"threadId": sid, "input": text_input("one")})).await.unwrap();
    tokio::time::sleep(Duration::from_millis(300)).await; // let the reader see EOF

    h.call("turn/start", json!({"threadId": sid, "input": text_input("two")})).await.unwrap();
    assert_eq!(h.methods().iter().filter(|m| *m == "initialize").count(), 2);
}

#[tokio::test]
async fn thread_resume_marks_session_loaded() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    h.call("thread/resume", json!({"threadId": "old-1", "cwd": h.project_dir()})).await.unwrap();
    h.call("turn/start", json!({"threadId": "old-1", "input": text_input("hi")})).await.unwrap();
    let methods = h.methods();
    assert_eq!(tail_after_nth(&methods, "session/load", 0), vec!["session/load", "session/prompt"]);
}

#[tokio::test]
async fn turn_start_uses_request_cwd_when_session_unknown() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    h.call(
        "turn/start",
        json!({"threadId": "from-disk", "cwd": h.project_dir(), "input": text_input("hi")}),
    )
    .await
    .unwrap();
    assert_eq!(h.last_frame("session/resume")["params"]["cwd"], json!(h.project_dir()));
}

#[tokio::test]
async fn agent_without_restore_capability_prompts_directly() {
    let h = Harness::new(json!({"capabilities": {"loadSession": false}})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.bridge.recycle_process(&h.conn()).await;
    let err = h
        .call("turn/start", json!({"threadId": sid, "input": text_input("hi")}))
        .await
        .unwrap_err();
    assert!(err.message.contains("not found"), "got: {}", err.message);
    assert_eq!(tail_after_nth(&h.methods(), "initialize", 1), vec!["initialize", "session/prompt"]);
}
```

- [ ] **Step 2: 确认失败**

Run: `cargo test -p alleycat-acp-bridge --test lifecycle`
Expected: 编译失败（`recycle_process` 不存在）。

- [ ] **Step 3: `AcpClient` 状态**

In `crates/acp-bridge/src/acp_client.rs`:
- add `use std::collections::HashSet;` and `use std::sync::atomic::AtomicBool;`.
- add fields to `AcpClient`:

```rust
    /// Set by the reader task when the agent's stdout closes (the process
    /// exited or crashed). The pool drops closed clients and respawns.
    closed: Arc<AtomicBool>,
    /// Cached ACP `initialize` result for this process.
    initialized: Mutex<Option<Value>>,
    /// Sessions this process has created, loaded or resumed. ACP agents
    /// keep sessions per process, so a respawned process must restore a
    /// session before it can be prompted.
    loaded_sessions: std::sync::Mutex<HashSet<String>>,
```

- in `spawn`, create `let closed = Arc::new(AtomicBool::new(false));`, pass `Arc::clone(&closed)` into `reader_task`, and initialise the new fields (`closed`, `initialized: Mutex::new(None)`, `loaded_sessions: std::sync::Mutex::new(HashSet::new())`).
- change `reader_task` to take `closed: Arc<AtomicBool>` as its last parameter and, right after the `loop { … }` ends (before waking pending requests), add `closed.store(true, Ordering::SeqCst);`.
- add methods:

```rust
    pub fn is_closed(&self) -> bool {
        self.closed.load(Ordering::SeqCst)
    }

    /// Send ACP `initialize` once per process; later calls return the
    /// cached result (a phone reconnecting to a live process must not
    /// re-initialize it).
    pub async fn ensure_initialized(&self, request: &Value) -> Result<Value> {
        let mut guard = self.initialized.lock().await;
        if let Some(result) = guard.as_ref() {
            return Ok(result.clone());
        }
        let result = self.send_request("initialize", request.clone()).await?;
        *guard = Some(result.clone());
        Ok(result)
    }

    pub fn mark_session_loaded(&self, session_id: &str) {
        self.loaded_sessions
            .lock()
            .expect("loaded_sessions poisoned")
            .insert(session_id.to_string());
    }

    pub fn is_session_loaded(&self, session_id: &str) -> bool {
        self.loaded_sessions
            .lock()
            .expect("loaded_sessions poisoned")
            .contains(session_id)
    }
```

- [ ] **Step 4: 池重新初始化并替换死进程**

In `crates/acp-bridge/src/pool.rs`, add field `init_request: std::sync::RwLock<Option<serde_json::Value>>` to `AcpPool` (initialised to `RwLock::new(None)` in `new`), add:

```rust
    /// Remember the ACP `initialize` request so processes spawned later
    /// (after idle eviction or a crash) are initialized before first use.
    pub fn set_init_request(&self, request: serde_json::Value) {
        *self.init_request.write().expect("init_request poisoned") = Some(request);
    }
```

and replace the body of `get_client` up to (not including) the eviction step with:

```rust
        let existing = self
            .clients
            .get(session_id)
            .map(|e| (Arc::clone(&e.client), Arc::clone(&e.last_access)));
        if let Some((client, last_access)) = existing {
            if !client.is_closed() {
                *last_access.write().await = Instant::now();
                debug!("Reusing existing ACP client for session");
                return Ok(client);
            }
            warn!("ACP agent process exited; respawning");
            self.remove_client(session_id).await;
        }
```

After `let client = Arc::new(AcpClient::spawn(&self.config, &self.launcher).await?);` add:

```rust
        let init_request = self.init_request.read().expect("init_request poisoned").clone();
        if let Some(request) = init_request {
            if let Err(err) = client.ensure_initialized(&request).await {
                let _ = client.kill().await;
                return Err(err);
            }
        }
```

- [ ] **Step 5: bridge 状态与 initialize**

In `crates/acp-bridge/src/bridge.rs` add fields to `AcpBridge` (initialise in `build()`):

```rust
    /// `agentCapabilities` from the agent's `initialize` response.
    agent_capabilities: std::sync::RwLock<Value>,
    /// Absolute cwd per session, recorded on thread/start, thread/resume
    /// and turn/start; used to restore sessions in a respawned process.
    session_cwds: DashMap<String, String>,
```

(`agent_capabilities: std::sync::RwLock::new(Value::Null)`, `session_cwds: DashMap::new()`), and methods:

```rust
    pub fn agent_capabilities(&self) -> Value {
        self.agent_capabilities.read().expect("agent_capabilities poisoned").clone()
    }

    pub fn set_session_cwd(&self, session_id: &str, cwd: &str) {
        if cwd.starts_with('/') {
            self.session_cwds.insert(session_id.to_string(), cwd.to_string());
        }
    }

    pub fn session_cwd(&self, session_id: &str) -> Option<String> {
        self.session_cwds.get(session_id).map(|c| c.clone())
    }

    /// Test hook: kill this connection's primary ACP process, as idle
    /// eviction would. The next request respawns it.
    #[doc(hidden)]
    pub async fn recycle_process(&self, ctx: &Conn) {
        self.pool.remove_client(&Self::session_key(ctx)).await;
    }
```

Replace `Bridge::initialize` with:

```rust
    async fn initialize(&self, ctx: &Conn, params: Value) -> Result<Value, JsonRpcError> {
        let request = crate::translate::codex_to_acp_initialize(&params, &self.client_capabilities)
            .map_err(|e| invalid_params(format!("Failed to translate initialize params: {e}")))?;
        self.pool.set_init_request(request.clone());
        let client = self
            .ensure_client(&Self::session_key(ctx))
            .await
            .map_err(|e| internal(format!("Failed to create ACP client: {e}")))?;
        let response = handlers::handle_initialize(&client, &request).await?;
        *self.agent_capabilities.write().expect("agent_capabilities poisoned") = response
            .get("agentCapabilities")
            .cloned()
            .unwrap_or(Value::Null);
        crate::translate::acp_to_codex_initialize_result(&response)
            .map_err(|e| internal(format!("Failed to translate initialize response: {e}")))
    }
```

In `crates/acp-bridge/src/handlers.rs` replace `handle_initialize` so it returns the raw ACP response:

```rust
/// Initialize the process behind `client` (once) and return the raw ACP
/// `initialize` response.
pub async fn handle_initialize(
    client: &Arc<AcpClient>,
    acp_request: &Value,
) -> Result<Value, JsonRpcError> {
    client.ensure_initialized(acp_request).await.map_err(|e| JsonRpcError {
        code: error_codes::INTERNAL_ERROR,
        message: format!("Failed to send initialize to ACP agent: {}", e),
        data: None,
    })
}
```

- [ ] **Step 6: 记录已加载会话并在 prompt 前恢复**

In `handlers.rs` add:

```rust
/// Make sure `session_id` is live in the process behind `client` before
/// it is prompted: ACP agents keep sessions per process, so a respawned
/// process answers `Session … not found` until the session is restored.
/// Prefers `session/resume` (no replay), falls back to `session/load`
/// (replay discarded); agents that support neither are prompted as-is.
pub async fn ensure_session_ready(
    bridge: &crate::bridge::AcpBridge,
    client: &Arc<AcpClient>,
    session_id: &str,
    fallback_cwd: Option<&str>,
) -> Result<(), JsonRpcError> {
    if client.is_session_loaded(session_id) {
        return Ok(());
    }
    let caps = bridge.agent_capabilities();
    let can_resume = caps.pointer("/sessionCapabilities/resume").is_some();
    let can_load = caps.get("loadSession").and_then(Value::as_bool).unwrap_or(false);
    if !can_resume && !can_load {
        return Ok(());
    }
    let cwd = bridge
        .session_cwd(session_id)
        .or_else(|| fallback_cwd.filter(|c| c.starts_with('/')).map(str::to_string))
        .unwrap_or_else(|| "/".to_string());
    let params = json!({"sessionId": session_id, "cwd": cwd, "mcpServers": []});
    let method = if can_resume { "session/resume" } else { "session/load" };
    info!(session_id, method, cwd = %cwd, "restoring ACP session in fresh process");
    client.send_request(method, params).await.map_err(|e| JsonRpcError {
        code: error_codes::INTERNAL_ERROR,
        message: format!("Failed to restore ACP session: {}", e),
        data: None,
    })?;
    if method == "session/load" {
        let _ = client.take_pending_notifications().await;
    }
    bridge.set_session_cwd(session_id, &cwd);
    client.mark_session_loaded(session_id);
    Ok(())
}
```

Then:
- `handle_thread_start`: after extracting `session_id`, add `client.mark_session_loaded(&session_id);` and `bridge.set_session_cwd(&session_id, acp_request["cwd"].as_str().unwrap_or("/"));` (move the `acp_request` clone before `send_request` if the borrow checker requires: `let sent_cwd = acp_request["cwd"].as_str().unwrap_or("/").to_string();` before sending).
- `handle_thread_resume`: build `let cwd_sent = coerce_absolute_cwd(typed.cwd.as_deref()).to_string();`, use it in the `session/load` request, and after the successful load add `client.mark_session_loaded(&typed.thread_id);` and `bridge.set_session_cwd(&typed.thread_id, &cwd_sent);`.
- `handle_turn_start`: right after `tracing::Span::current().record(...)` and before building prompt blocks, add:

```rust
    let request_cwd = typed.cwd.as_ref().and_then(|p| p.to_str()).map(str::to_string);
    ensure_session_ready(bridge, client, &typed.thread_id, request_cwd.as_deref()).await?;
```

- [ ] **Step 7: 确认通过**

Run: `cargo test -p alleycat-acp-bridge`
Expected: PASS（lifecycle 6 个 + 之前全部）。

- [ ] **Step 8: 提交**

```bash
git add crates/acp-bridge
git commit -m "acp-bridge: re-initialize respawned agents and restore sessions before prompting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 会话配置状态与模型 / 思考强度切换

**Files:**
- Create: `crates/acp-bridge/src/config_options.rs`
- Modify: `crates/acp-bridge/src/lib.rs`（`pub mod config_options;`）
- Modify: `crates/acp-bridge/src/bridge.rs`（`session_config` 字段和方法）
- Modify: `crates/acp-bridge/src/handlers.rs`（thread/start、thread/resume、turn/start、`ensure_session_ready`、新 `apply_session_config`）
- Test: `crates/acp-bridge/tests/config.rs`（以及 `config_options.rs` 内的单元测试）

**Interfaces:**
- Consumes: Task 2 的 `ensure_session_ready`、`AcpBridge::set_session_cwd`。
- Produces: `config_options::{MODEL, THOUGHT_LEVEL, extract, find, current_value, values, thought_level_for, effort_for, pending_changes}`；`AcpBridge::record_session_config(&self, session_id: &str, response: &Value)`、`AcpBridge::session_config(&self, &str) -> Vec<Value>`、`AcpBridge::any_config_option(&self, id: &str) -> Option<Value>`；`handlers::apply_session_config(bridge, client, session_id, model: Option<&str>, effort: Option<p::ReasoningEffort>)`。

- [ ] **Step 1: 写失败的测试**

Create `crates/acp-bridge/src/config_options.rs` with only the tests first:

```rust
//! Helpers for ACP `configOptions` (`session/new`, `session/load`,
//! `session/resume` and `session/set_config_option` all return them).

#[cfg(test)]
mod tests {
    use super::*;
    use alleycat_codex_proto as p;
    use serde_json::json;

    fn options() -> Vec<serde_json::Value> {
        vec![
            json!({"id": "model", "currentValue": "m/a", "options": [{"value": "m/a"}, {"value": "m/b"}]}),
            json!({"id": "thought_level", "currentValue": "low", "options": [{"value": "low"}, {"value": "medium"}, {"value": "high"}, {"value": "xhigh"}]}),
        ]
    }

    #[test]
    fn effort_maps_to_thought_level() {
        use p::ReasoningEffort as E;
        let cases = [
            (E::None, "low"), (E::Minimal, "low"), (E::Low, "low"), (E::Medium, "medium"),
            (E::High, "high"), (E::XHigh, "xhigh"), (E::Max, "xhigh"),
        ];
        for (effort, level) in cases {
            assert_eq!(thought_level_for(effort), level, "{effort:?}");
        }
        assert_eq!(effort_for("xhigh"), Some(E::XHigh));
        assert_eq!(effort_for("turbo"), None);
    }

    #[test]
    fn pending_changes_only_includes_allowed_differences() {
        let opts = options();
        assert!(pending_changes(&opts, Some("m/a"), Some(p::ReasoningEffort::Low)).is_empty());
        assert_eq!(
            pending_changes(&opts, Some("m/b"), Some(p::ReasoningEffort::High)),
            vec![(MODEL, "m/b".to_string()), (THOUGHT_LEVEL, "high".to_string())]
        );
        assert!(pending_changes(&opts, Some("placeholder"), None).is_empty());
        assert!(pending_changes(&[], Some("m/b"), Some(p::ReasoningEffort::High)).is_empty());
    }

    #[test]
    fn current_value_and_values() {
        let opts = options();
        assert_eq!(current_value(&opts, MODEL).as_deref(), Some("m/a"));
        assert_eq!(values(&opts, THOUGHT_LEVEL), vec!["low", "medium", "high", "xhigh"]);
        assert_eq!(extract(&json!({"configOptions": opts.clone()})), opts);
        assert!(extract(&json!({})).is_empty());
    }
}
```

and add `pub mod config_options;` to `crates/acp-bridge/src/lib.rs`.

Create `crates/acp-bridge/tests/config.rs`:

```rust
mod support;

use serde_json::json;
use support::{Harness, text_input};

#[tokio::test]
async fn thread_start_reports_agent_current_model() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let started = h.call("thread/start", json!({"cwd": h.project_dir()})).await.unwrap();
    assert_eq!(started["model"], json!("fake/alpha"));
}

#[tokio::test]
async fn thread_start_applies_requested_model() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let started = h
        .call("thread/start", json!({"cwd": h.project_dir(), "model": "fake/beta"}))
        .await
        .unwrap();
    assert_eq!(started["model"], json!("fake/beta"));
}

#[tokio::test]
async fn turn_start_switches_model_and_effort_before_prompting() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.call(
        "turn/start",
        json!({"threadId": sid, "input": text_input("hi"), "model": "fake/beta", "effort": "high"}),
    )
    .await
    .unwrap();
    let frames = h.frames();
    let tail: Vec<_> = frames.iter().rev().take(3).rev().collect();
    assert_eq!(tail[0]["method"], "session/set_config_option");
    assert_eq!(tail[0]["params"]["configId"], "model");
    assert_eq!(tail[0]["params"]["value"], "fake/beta");
    assert_eq!(tail[1]["params"]["configId"], "thought_level");
    assert_eq!(tail[1]["params"]["value"], "high");
    assert_eq!(tail[2]["method"], "session/prompt");
}

#[tokio::test]
async fn unchanged_values_send_nothing() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.call(
        "turn/start",
        json!({"threadId": sid, "input": text_input("hi"), "model": "fake/alpha", "effort": "low"}),
    )
    .await
    .unwrap();
    assert!(!h.methods().iter().any(|m| m == "session/set_config_option"));
}

#[tokio::test]
async fn unknown_model_is_skipped_and_max_effort_maps_to_xhigh() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let sid = h.start_thread().await;
    h.call(
        "turn/start",
        json!({"threadId": sid, "input": text_input("hi"), "model": "fake", "effort": "max"}),
    )
    .await
    .unwrap();
    let sets: Vec<_> = h
        .frames()
        .into_iter()
        .filter(|f| f["method"] == "session/set_config_option")
        .collect();
    assert_eq!(sets.len(), 1);
    assert_eq!(sets[0]["params"]["configId"], "thought_level");
    assert_eq!(sets[0]["params"]["value"], "xhigh");
}

#[tokio::test]
async fn thread_resume_reports_agent_current_model() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let resumed = h
        .call("thread/resume", json!({"threadId": "old-1", "cwd": h.project_dir()}))
        .await
        .unwrap();
    assert_eq!(resumed["model"], json!("fake/alpha"));
}
```

- [ ] **Step 2: 确认失败**

Run: `cargo test -p alleycat-acp-bridge config`
Expected: 编译失败（`thought_level_for` 等不存在）。

- [ ] **Step 3: 实现 `config_options.rs`**

Insert above the `#[cfg(test)]` module:

```rust
use alleycat_codex_proto as p;
use serde_json::Value;
use tracing::warn;

pub const MODEL: &str = "model";
pub const THOUGHT_LEVEL: &str = "thought_level";

/// `configOptions` array of an ACP response (empty when absent).
pub fn extract(response: &Value) -> Vec<Value> {
    response
        .get("configOptions")
        .and_then(Value::as_array)
        .cloned()
        .unwrap_or_default()
}

pub fn find<'a>(options: &'a [Value], id: &str) -> Option<&'a Value> {
    options
        .iter()
        .find(|o| o.get("id").and_then(Value::as_str) == Some(id))
}

pub fn current_value(options: &[Value], id: &str) -> Option<String> {
    find(options, id)?
        .get("currentValue")?
        .as_str()
        .map(str::to_string)
}

pub fn values(options: &[Value], id: &str) -> Vec<String> {
    find(options, id)
        .and_then(|o| o.get("options"))
        .and_then(Value::as_array)
        .map(|opts| {
            opts.iter()
                .filter_map(|o| o.get("value").and_then(Value::as_str).map(str::to_string))
                .collect()
        })
        .unwrap_or_default()
}

/// Codex reasoning effort → ACP `thought_level` value.
pub fn thought_level_for(effort: p::ReasoningEffort) -> &'static str {
    match effort {
        p::ReasoningEffort::None | p::ReasoningEffort::Minimal | p::ReasoningEffort::Low => "low",
        p::ReasoningEffort::Medium => "medium",
        p::ReasoningEffort::High => "high",
        p::ReasoningEffort::XHigh | p::ReasoningEffort::Max => "xhigh",
    }
}

/// ACP `thought_level` value → codex reasoning effort.
pub fn effort_for(level: &str) -> Option<p::ReasoningEffort> {
    match level {
        "low" => Some(p::ReasoningEffort::Low),
        "medium" => Some(p::ReasoningEffort::Medium),
        "high" => Some(p::ReasoningEffort::High),
        "xhigh" => Some(p::ReasoningEffort::XHigh),
        _ => None,
    }
}

/// `(configId, value)` pairs to send so the session matches the request.
/// Values the agent does not offer are skipped (and logged): the turn
/// runs with the agent's current value instead of failing.
pub fn pending_changes(
    options: &[Value],
    model: Option<&str>,
    effort: Option<p::ReasoningEffort>,
) -> Vec<(&'static str, String)> {
    let mut out = Vec::new();
    if let Some(model) = model {
        if wants(options, MODEL, model) {
            out.push((MODEL, model.to_string()));
        }
    }
    if let Some(effort) = effort {
        let level = thought_level_for(effort);
        if wants(options, THOUGHT_LEVEL, level) {
            out.push((THOUGHT_LEVEL, level.to_string()));
        }
    }
    out
}

fn wants(options: &[Value], id: &str, value: &str) -> bool {
    let allowed = values(options, id);
    if allowed.is_empty() {
        return false;
    }
    if !allowed.iter().any(|v| v == value) {
        warn!(config_id = id, value, "agent does not offer this value; keeping its current one");
        return false;
    }
    current_value(options, id).as_deref() != Some(value)
}
```

- [ ] **Step 4: bridge 记录 configOptions**

In `bridge.rs` add field `session_config: DashMap<String, Vec<Value>>` (init `DashMap::new()`) and:

```rust
    /// Remember a session's ACP `configOptions` (and the model catalog in
    /// them) from any response that carries them.
    pub fn record_session_config(&self, session_id: &str, response: &Value) {
        let options = crate::config_options::extract(response);
        if options.is_empty() {
            return;
        }
        let models = crate::config_options::find(&options, crate::config_options::MODEL)
            .and_then(|o| o.get("options"))
            .and_then(Value::as_array)
            .cloned()
            .unwrap_or_default();
        if !models.is_empty() {
            self.set_models(session_id, models);
        }
        self.session_config.insert(session_id.to_string(), options);
    }

    pub fn session_config(&self, session_id: &str) -> Vec<Value> {
        self.session_config
            .get(session_id)
            .map(|o| o.clone())
            .unwrap_or_default()
    }

    /// First session's option with this id (agent-wide traits such as the
    /// thinking levels are the same for every session).
    pub fn any_config_option(&self, id: &str) -> Option<Value> {
        self.session_config
            .iter()
            .find_map(|entry| crate::config_options::find(entry.value(), id).cloned())
    }
```

- [ ] **Step 5: handlers 应用配置**

In `handlers.rs` add `use crate::config_options;` and:

```rust
/// Bring the session's model / thinking level in line with the request
/// via `session/set_config_option`. Failures are logged, never fatal.
pub async fn apply_session_config(
    bridge: &crate::bridge::AcpBridge,
    client: &Arc<AcpClient>,
    session_id: &str,
    model: Option<&str>,
    effort: Option<p::ReasoningEffort>,
) {
    let options = bridge.session_config(session_id);
    for (config_id, value) in config_options::pending_changes(&options, model, effort) {
        let params = json!({"sessionId": session_id, "configId": config_id, "value": value});
        match client.send_request("session/set_config_option", params).await {
            Ok(response) => bridge.record_session_config(session_id, &response),
            Err(err) => tracing::warn!(session_id, config_id, value, error = %err,
                "set_config_option failed; continuing with the agent's current value"),
        }
    }
}

fn reported_model(bridge: &crate::bridge::AcpBridge, session_id: &str, fallback: &str) -> String {
    config_options::current_value(&bridge.session_config(session_id), config_options::MODEL)
        .unwrap_or_else(|| fallback.to_string())
}
```

- `handle_thread_start`: replace the `extract_models_from_config_options` / `set_models` block with `bridge.record_session_config(&session_id, &acp_response);`, then add `apply_session_config(bridge, client, &session_id, typed.model.as_deref(), None).await;` and change the response's top-level `"model": &agent_id` to `"model": reported_model(bridge, &session_id, &agent_id)`.
- `handle_thread_resume`: rename `_acp_response` to `acp_response`, add `bridge.record_session_config(&typed.thread_id, &acp_response);` after the load, and set `let model = reported_model(bridge, &typed.thread_id, typed.model.as_deref().unwrap_or(&agent_id));`.
- `ensure_session_ready`: capture the restore response (`let response = client.send_request(method, params).await.map_err(...)?;`) and call `bridge.record_session_config(session_id, &response);` before marking loaded.
- `handle_turn_start`: after the `ensure_session_ready(...)` line add `apply_session_config(bridge, client, &typed.thread_id, typed.model.as_deref(), typed.effort).await;`.
- If `extract_models_from_config_options` is now unused, delete it (and its call sites) rather than leaving dead code; keep `extract_modes_from_session_new`.

- [ ] **Step 6: 确认通过**

Run: `cargo test -p alleycat-acp-bridge`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add crates/acp-bridge
git commit -m "acp-bridge: apply model and thinking level through set_config_option

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 辅助进程与带 cwd 的线程列表

**Files:**
- Modify: `crates/acp-bridge/src/bridge.rs`（`ensure_aux_client`、dispatch 的 `thread/list`）
- Modify: `crates/acp-bridge/src/handlers.rs`（`handle_thread_list`）
- Test: `crates/acp-bridge/tests/aux.rs`

**Interfaces:**
- Consumes: Task 2 的池初始化（辅助进程也会被初始化）。
- Produces: `AcpBridge::ensure_aux_client(&self, ctx: &Conn) -> anyhow::Result<Arc<AcpClient>>`（key `<agent>:<node_id>:aux`）；`handle_thread_list(client, params)` 传 `cwd` 并在结果里保留会话 `cwd`。

- [ ] **Step 1: 写失败的测试**

Create `crates/acp-bridge/tests/aux.rs`:

```rust
mod support;

use std::sync::Arc;
use std::time::{Duration, Instant};

use alleycat_bridge_core::Bridge;
use serde_json::json;
use support::{Harness, text_input};

fn sessions_config(delay_ms: u64) -> serde_json::Value {
    json!({
        "prompt_delay_ms": delay_ms,
        "sessions_by_cwd": {
            "/tmp/proj-a": [{"sessionId": "a1", "cwd": "/tmp/proj-a", "title": "A one", "updatedAt": "2026-09-30T10:00:00Z"}]
        }
    })
}

#[tokio::test]
async fn thread_list_passes_cwd_and_keeps_it() {
    let h = Harness::new(sessions_config(0)).await;
    h.initialize().await;
    let list = h.call("thread/list", json!({"cwd": "/tmp/proj-a"})).await.unwrap();
    assert_eq!(list["data"][0]["id"], "a1");
    assert_eq!(list["data"][0]["cwd"], "/tmp/proj-a");
    assert_eq!(h.last_frame("session/list")["params"]["cwd"], "/tmp/proj-a");
}

#[tokio::test]
async fn thread_list_does_not_wait_for_running_prompt() {
    let h = Harness::new(sessions_config(1500)).await;
    h.initialize().await;
    let sid = h.start_thread().await;

    let bridge = Arc::clone(&h.bridge);
    let conn = h.conn();
    let turn = tokio::spawn(async move {
        bridge
            .dispatch(&conn, "turn/start", json!({"threadId": sid, "input": text_input("slow")}))
            .await
    });
    tokio::time::sleep(Duration::from_millis(200)).await;

    let started = Instant::now();
    let list = h.call("thread/list", json!({"cwd": "/tmp/proj-a"})).await.unwrap();
    assert!(started.elapsed() < Duration::from_millis(1000), "list waited {:?}", started.elapsed());
    assert_eq!(list["data"][0]["id"], "a1");
    turn.await.unwrap().unwrap();
}
```

- [ ] **Step 2: 确认失败**

Run: `cargo test -p alleycat-acp-bridge --test aux`
Expected: FAIL（`cwd` 为空串；第二个测试列表等待超过 1 秒）。

- [ ] **Step 3: 实现**

In `bridge.rs` `impl AcpBridge`:

```rust
    /// Secondary ACP process for this connection. Read-only calls
    /// (`session/list`, model discovery) go here so they never queue
    /// behind a streaming `session/prompt` on the primary process.
    pub async fn ensure_aux_client(
        &self,
        ctx: &Conn,
    ) -> Result<Arc<crate::acp_client::AcpClient>> {
        self.pool
            .get_client(&format!("{}:aux", Self::session_key(ctx)))
            .await
    }
```

In `Bridge::dispatch`, change the `"thread/list"` arm to:

```rust
            "thread/list" => {
                let typed: p::ThreadListParams = if params.is_null() {
                    Default::default()
                } else {
                    decode(params)?
                };
                let aux = self
                    .ensure_aux_client(ctx)
                    .await
                    .map_err(|e| internal(format!("Failed to get ACP client: {e}")))?;
                handlers::handle_thread_list(&aux, typed).await
            }
```

In `handlers.rs` `handle_thread_list`: rename `_params` to `params`, build the request as

```rust
    let request = match params.cwd.as_ref().and_then(Value::as_str) {
        Some(cwd) if cwd.starts_with('/') => json!({"cwd": cwd}),
        _ => json!({}),
    };
    match client.send_request("session/list", request).await {
```

and in the per-session `json!` replace `"cwd": "",` with `"cwd": session.get("cwd").and_then(|v| v.as_str()).unwrap_or(""),`.

- [ ] **Step 4: 确认通过**

Run: `cargo test -p alleycat-acp-bridge`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add crates/acp-bridge
git commit -m "acp-bridge: serve thread/list from a secondary process and keep session cwd

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 模型列表（真实思考强度、图片能力、模型发现）

**Files:**
- Modify: `crates/acp-bridge/src/handlers.rs:80-175`（`handle_model_list`、`acp_model_to_codex`）
- Modify: `crates/acp-bridge/src/bridge.rs`（builder `discover_models`、字段、dispatch 的 `model/list` 变成 `.await`）
- Test: `crates/acp-bridge/tests/models.rs`

**Interfaces:**
- Consumes: Task 3 的 `any_config_option`、`record_session_config`、`config_options::effort_for`；Task 4 的 `ensure_aux_client`。
- Produces: `AcpBridgeBuilder::discover_models(self, bool) -> Self`；`AcpBridge::discover_models_enabled(&self) -> bool`；`handlers::handle_model_list(bridge, ctx, params) -> p::ModelListResponse`（async）。

- [ ] **Step 1: 写失败的测试**

Create `crates/acp-bridge/tests/models.rs`:

```rust
mod support;

use serde_json::{Value, json};
use support::Harness;

fn ids(list: &Value) -> Vec<String> {
    list["data"].as_array().unwrap().iter().map(|m| m["id"].as_str().unwrap().to_string()).collect()
}

#[tokio::test]
async fn placeholder_model_when_discovery_is_off() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    let list = h.call("model/list", json!({})).await.unwrap();
    assert_eq!(ids(&list), vec!["fake"]);
}

#[tokio::test]
async fn discovery_fetches_catalog_before_any_thread() {
    let h = Harness::with(json!({}), |b| b.discover_models(true)).await;
    h.initialize().await;
    let list = h.call("model/list", json!({})).await.unwrap();
    assert_eq!(ids(&list), vec!["fake/alpha", "fake/beta"]);
    assert_eq!(list["data"][0]["isDefault"], json!(true));
    let home = std::env::var("HOME").unwrap();
    assert_eq!(h.last_frame("session/new")["params"]["cwd"], json!(home));
}

#[tokio::test]
async fn efforts_and_image_follow_the_agent() {
    let h = Harness::new(json!({})).await;
    h.initialize().await;
    h.start_thread().await;
    let list = h.call("model/list", json!({})).await.unwrap();
    let model = &list["data"][0];
    let efforts: Vec<_> = model["supportedReasoningEfforts"]
        .as_array()
        .unwrap()
        .iter()
        .map(|e| e["reasoningEffort"].as_str().unwrap().to_string())
        .collect();
    assert_eq!(efforts, vec!["low", "medium", "high", "xhigh"]);
    assert_eq!(model["defaultReasoningEffort"], "low");
    assert!(model["inputModalities"].as_array().unwrap().contains(&json!("image")));
}

#[tokio::test]
async fn no_image_modality_without_capability() {
    let h = Harness::new(json!({"capabilities": {"loadSession": true, "promptCapabilities": {"image": false}}})).await;
    h.initialize().await;
    h.start_thread().await;
    let list = h.call("model/list", json!({})).await.unwrap();
    assert_eq!(list["data"][0]["inputModalities"], json!(["text"]));
}
```

- [ ] **Step 2: 确认失败**

Run: `cargo test -p alleycat-acp-bridge --test models`
Expected: 编译失败（`discover_models` 不存在）。

- [ ] **Step 3: 实现**

`bridge.rs`: add builder field `discover_models: bool` (default `false`), setter:

```rust
    /// When the model catalog is empty, `model/list` creates a throwaway
    /// session in `$HOME` on the secondary process to fetch it. Only for
    /// agents that do not persist prompt-less sessions (mfcli verified).
    pub fn discover_models(mut self, enabled: bool) -> Self {
        self.discover_models = enabled;
        self
    }
```

a matching `discover_models: bool` field on `AcpBridge`, `pub fn discover_models_enabled(&self) -> bool { self.discover_models }`, and change the `"model/list"` dispatch arm to `to_value(handlers::handle_model_list(self, ctx, typed).await)`.

`handlers.rs`: replace `handle_model_list` and `acp_model_to_codex` with:

```rust
/// Traits shared by every model of an ACP agent: thinking levels from the
/// `thought_level` config option and image input from `initialize`.
struct ModelTraits {
    efforts: Vec<p::ReasoningEffortOption>,
    default_effort: p::ReasoningEffort,
    modalities: Vec<Value>,
    default_model: Option<String>,
}

impl ModelTraits {
    fn from_bridge(bridge: &crate::bridge::AcpBridge) -> Self {
        let thought = bridge.any_config_option(config_options::THOUGHT_LEVEL);
        let mut efforts = Vec::new();
        if let Some(opts) = thought.as_ref().and_then(|t| t.get("options")).and_then(Value::as_array) {
            for opt in opts {
                let value = opt.get("value").and_then(Value::as_str).unwrap_or("");
                if let Some(effort) = config_options::effort_for(value) {
                    efforts.push(p::ReasoningEffortOption {
                        reasoning_effort: effort,
                        description: opt.get("name").and_then(Value::as_str).unwrap_or(value).to_string(),
                    });
                }
            }
        }
        let default_effort = thought
            .as_ref()
            .and_then(|t| t.get("currentValue"))
            .and_then(Value::as_str)
            .and_then(config_options::effort_for)
            .unwrap_or(p::ReasoningEffort::Medium);
        if efforts.is_empty() {
            efforts.push(p::ReasoningEffortOption {
                reasoning_effort: p::ReasoningEffort::Medium,
                description: "Default".to_string(),
            });
        }
        let image = bridge
            .agent_capabilities()
            .pointer("/promptCapabilities/image")
            .and_then(Value::as_bool)
            .unwrap_or(false);
        let mut modalities = vec![json!("text")];
        if image {
            modalities.push(json!("image"));
        }
        let default_model = bridge
            .any_config_option(config_options::MODEL)
            .and_then(|m| m.get("currentValue").and_then(Value::as_str).map(str::to_string));
        Self { efforts, default_effort, modalities, default_model }
    }
}

/// Handle model/list. With discovery enabled and no catalog yet, fetch it
/// from a throwaway session first; otherwise fall back to one placeholder
/// named after the agent so the phone can pin a thread to something.
pub async fn handle_model_list(
    bridge: &crate::bridge::AcpBridge,
    ctx: &alleycat_bridge_core::Conn,
    _params: p::ModelListParams,
) -> p::ModelListResponse {
    if bridge.all_models().is_empty() && bridge.discover_models_enabled() {
        if let Err(err) = discover_models(bridge, ctx).await {
            tracing::warn!(error = %err, "model discovery failed; returning placeholder");
        }
    }
    let traits = ModelTraits::from_bridge(bridge);
    let cached = bridge.all_models();
    let data: Vec<p::Model> = if cached.is_empty() {
        let agent_id = ctx.session().agent.to_string();
        let id = if agent_id.is_empty() { "acp-default".to_string() } else { agent_id };
        vec![model_record(&id, &title_case(&id), "Default model", &traits, true)]
    } else {
        cached
            .iter()
            .map(|entry| {
                let id = entry.get("value").or_else(|| entry.get("id")).and_then(Value::as_str).unwrap_or("");
                let name = entry.get("name").and_then(Value::as_str).unwrap_or(id);
                let description = entry.get("description").and_then(Value::as_str).unwrap_or("");
                let is_default = traits.default_model.as_deref() == Some(id);
                model_record(id, name, description, &traits, is_default)
            })
            .collect()
    };
    p::ModelListResponse { data, next_cursor: None }
}

async fn discover_models(
    bridge: &crate::bridge::AcpBridge,
    ctx: &alleycat_bridge_core::Conn,
) -> anyhow::Result<()> {
    let client = bridge.ensure_aux_client(ctx).await?;
    let cwd = std::env::var("HOME").unwrap_or_else(|_| "/".to_string());
    let response = client
        .send_request("session/new", json!({"cwd": cwd, "mcpServers": []}))
        .await?;
    let session_id = response
        .get("sessionId")
        .and_then(Value::as_str)
        .ok_or_else(|| anyhow::anyhow!("session/new response missing sessionId"))?;
    bridge.record_session_config(session_id, &response);
    Ok(())
}

fn model_record(id: &str, name: &str, description: &str, traits: &ModelTraits, is_default: bool) -> p::Model {
    p::Model {
        id: id.to_string(),
        model: id.to_string(),
        upgrade: None,
        upgrade_info: None,
        availability_nux: None,
        display_name: name.to_string(),
        description: description.to_string(),
        hidden: false,
        supported_reasoning_efforts: traits.efforts.clone(),
        default_reasoning_effort: traits.default_effort,
        input_modalities: traits.modalities.clone(),
        supports_personality: false,
        additional_speed_tiers: vec![],
        service_tiers: vec![p::ModelServiceTier {
            id: "standard".to_string(),
            name: "Standard".to_string(),
            description: "Standard service tier".to_string(),
        }],
        is_default,
    }
}
```


- [ ] **Step 4: 确认通过**

Run: `cargo test -p alleycat-acp-bridge`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add crates/acp-bridge
git commit -m "acp-bridge: model list with real thinking levels, image input and optional discovery

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `mfcli-bridge` crate

**Files:**
- Create: `crates/mfcli-bridge/Cargo.toml`
- Create: `crates/mfcli-bridge/src/lib.rs`、`src/projects.rs`、`src/index.rs`、`src/listing.rs`
- Modify: `Cargo.toml`（workspace members + `alleycat-mfcli-bridge` workspace dep）

**Interfaces:**
- Consumes: `AcpBridge::builder()` 及 Task 1–5 的 `client_capabilities`、`discover_models`、`pool_capacity`、`ensure_aux_client`、`session_cwd`、`set_session_cwd`；`alleycat_acp_bridge::acp_client::AcpClient::send_request`。
- Produces: `MfcliBridge::build(bin: impl Into<PathBuf>, paths: MfcliPaths, launcher: Arc<dyn ProcessLauncher>) -> anyhow::Result<Arc<MfcliBridge>>`；`MfcliPaths { data_json: PathBuf, index_file: PathBuf }`、`MfcliPaths::default_for(state_dir: &Path) -> MfcliPaths`；`MfcliBridge` 实现 `Bridge`。

- [ ] **Step 1: crate 骨架**

Create `crates/mfcli-bridge/Cargo.toml`:

```toml
[package]
name = "alleycat-mfcli-bridge"
version.workspace = true
edition.workspace = true
license.workspace = true
authors.workspace = true
repository.workspace = true
homepage.workspace = true
readme.workspace = true
description = "MyFlicker (mfcli) bridge: ACP over `mfcli acp` with multi-project thread/list."

[package.metadata.dist]
dist = false

[lib]
name = "alleycat_mfcli_bridge"
path = "src/lib.rs"

[dependencies]
alleycat-acp-bridge = { workspace = true }
alleycat-bridge-core = { workspace = true }
anyhow = { workspace = true }
async-trait = { workspace = true }
chrono = { workspace = true }
serde = { workspace = true }
serde_json = { workspace = true }
tokio = { workspace = true }
tracing = { workspace = true }

[dev-dependencies]
tempfile = { workspace = true }
```

In the workspace `Cargo.toml` add `"crates/mfcli-bridge",` to `members` (after `"crates/grok-bridge",`) and `alleycat-mfcli-bridge = { path = "crates/mfcli-bridge", version = "0.1" }` to `[workspace.dependencies]` (after the grok entry).

- [ ] **Step 2: `projects.rs`（先写测试）**

```rust
//! Project directories mfcli has seen, from `~/.codeflicker/data.json`
//! (`{"projects": {"/abs/path": {...}}}`). Missing or unreadable files
//! yield no directories: listing then relies on the cwd index.

use std::path::Path;

use serde_json::Value;
use tracing::warn;

pub fn project_dirs(data_json: &Path) -> Vec<String> {
    let text = match std::fs::read_to_string(data_json) {
        Ok(text) => text,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Vec::new(),
        Err(err) => {
            warn!(path = %data_json.display(), error = %err, "cannot read mfcli data.json");
            return Vec::new();
        }
    };
    let value: Value = match serde_json::from_str(&text) {
        Ok(value) => value,
        Err(err) => {
            warn!(path = %data_json.display(), error = %err, "mfcli data.json is not valid JSON");
            return Vec::new();
        }
    };
    value
        .get("projects")
        .and_then(Value::as_object)
        .map(|projects| projects.keys().filter(|k| k.starts_with('/')).cloned().collect())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn project_dirs_reads_absolute_keys() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("data.json");
        std::fs::write(&path, r#"{"projects": {"/a": {}, "relative": {}, "/b/c": {}}, "recentModels": []}"#).unwrap();
        let mut dirs = project_dirs(&path);
        dirs.sort();
        assert_eq!(dirs, vec!["/a", "/b/c"]);
    }

    #[test]
    fn project_dirs_missing_file_is_empty() {
        let dir = tempfile::tempdir().unwrap();
        assert!(project_dirs(&dir.path().join("nope.json")).is_empty());
    }

    #[test]
    fn project_dirs_corrupt_file_is_empty() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("data.json");
        std::fs::write(&path, "{not json").unwrap();
        assert!(project_dirs(&path).is_empty());
        std::fs::write(&path, r#"{"projects": []}"#).unwrap();
        assert!(project_dirs(&path).is_empty());
    }
}
```

- [ ] **Step 3: `index.rs`**

```rust
//! Persistent `sessionId → cwd` index for sessions the bridge has seen.
//! mfcli's own session files do not record the real cwd, so this index is
//! how resume and listing find the project of a session after a restart.

use std::collections::{BTreeMap, BTreeSet};
use std::path::PathBuf;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tracing::warn;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct IndexEntry {
    pub cwd: String,
    pub updated_at_ms: i64,
}

pub struct CwdIndex {
    path: PathBuf,
    entries: Mutex<BTreeMap<String, IndexEntry>>,
}

impl CwdIndex {
    /// Load the index; a missing or corrupt file starts empty.
    pub fn load(path: PathBuf) -> Self {
        let entries = match std::fs::read_to_string(&path) {
            Ok(text) => serde_json::from_str(&text).unwrap_or_else(|err| {
                warn!(path = %path.display(), error = %err, "mfcli cwd index is corrupt; starting empty");
                BTreeMap::new()
            }),
            Err(_) => BTreeMap::new(),
        };
        Self { path, entries: Mutex::new(entries) }
    }

    pub fn cwd_for(&self, session_id: &str) -> Option<String> {
        self.entries.lock().expect("index poisoned").get(session_id).map(|e| e.cwd.clone())
    }

    pub fn cwds(&self) -> BTreeSet<String> {
        self.entries.lock().expect("index poisoned").values().map(|e| e.cwd.clone()).collect()
    }

    /// Record `(sessionId, cwd, updatedAtMs)` triples; relative cwds are
    /// ignored. Writes the file (atomically) only when something changed.
    pub fn record_all(&self, items: impl IntoIterator<Item = (String, String, i64)>) {
        let snapshot = {
            let mut entries = self.entries.lock().expect("index poisoned");
            let mut changed = false;
            for (session_id, cwd, updated_at_ms) in items {
                if !cwd.starts_with('/') {
                    continue;
                }
                let entry = IndexEntry { cwd, updated_at_ms };
                if entries.get(&session_id) != Some(&entry) {
                    entries.insert(session_id, entry);
                    changed = true;
                }
            }
            if !changed {
                return;
            }
            entries.clone()
        };
        if let Err(err) = self.persist(&snapshot) {
            warn!(path = %self.path.display(), error = %err, "failed to write mfcli cwd index");
        }
    }

    fn persist(&self, snapshot: &BTreeMap<String, IndexEntry>) -> std::io::Result<()> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let tmp = self.path.with_extension("json.tmp");
        std::fs::write(&tmp, serde_json::to_vec_pretty(snapshot)?)?;
        std::fs::rename(&tmp, &self.path)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn records_persist_and_reload() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("state/mfcli-sessions.json");
        let index = CwdIndex::load(path.clone());
        index.record_all([
            ("s1".to_string(), "/p/one".to_string(), 10),
            ("s2".to_string(), "relative".to_string(), 11),
        ]);
        let reloaded = CwdIndex::load(path.clone());
        assert_eq!(reloaded.cwd_for("s1").as_deref(), Some("/p/one"));
        assert_eq!(reloaded.cwd_for("s2"), None);
        assert!(!path.with_extension("json.tmp").exists());
    }

    #[test]
    fn corrupt_index_starts_empty() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("idx.json");
        std::fs::write(&path, "garbage").unwrap();
        assert!(CwdIndex::load(path).cwds().is_empty());
    }
}
```

- [ ] **Step 4: `listing.rs`**

```rust
//! Multi-project `thread/list` for mfcli: its `session/list` only returns
//! sessions when given a `cwd`, so the bridge asks once per known project.

use std::collections::{BTreeSet, HashSet};

use alleycat_acp_bridge::acp_client::AcpClient;
use async_trait::async_trait;
use serde_json::{Value, json};
use tracing::warn;

/// Upper bound on `session/list` pages per project (guards against an
/// agent that keeps returning a cursor).
pub const MAX_PAGES: usize = 20;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AcpSession {
    pub session_id: String,
    pub cwd: String,
    pub title: String,
    pub updated_at_ms: i64,
}

#[async_trait]
pub trait SessionLister: Send + Sync {
    async fn list_page(&self, cwd: &str, cursor: Option<&str>) -> anyhow::Result<Value>;
}

#[async_trait]
impl SessionLister for AcpClient {
    async fn list_page(&self, cwd: &str, cursor: Option<&str>) -> anyhow::Result<Value> {
        let mut params = json!({"cwd": cwd});
        if let Some(cursor) = cursor {
            params["cursor"] = json!(cursor);
        }
        self.send_request("session/list", params).await
    }
}

/// `cwd` filter of a codex `thread/list` request (string or array).
pub fn requested_cwds(params: &Value) -> Vec<String> {
    match params.get("cwd") {
        Some(Value::String(cwd)) if cwd.starts_with('/') => vec![cwd.clone()],
        Some(Value::Array(items)) => items
            .iter()
            .filter_map(Value::as_str)
            .filter(|c| c.starts_with('/'))
            .map(str::to_string)
            .collect(),
        _ => Vec::new(),
    }
}

pub fn parse_page(page: &Value, requested_cwd: &str) -> (Vec<AcpSession>, Option<String>) {
    let sessions = page
        .get("sessions")
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(|s| {
                    let session_id = s.get("sessionId")?.as_str()?.to_string();
                    let cwd = s
                        .get("cwd")
                        .and_then(Value::as_str)
                        .filter(|c| c.starts_with('/'))
                        .unwrap_or(requested_cwd)
                        .to_string();
                    let title = s
                        .get("title")
                        .and_then(Value::as_str)
                        .filter(|t| !t.is_empty())
                        .map(str::to_string)
                        .unwrap_or_else(|| format!("Session {session_id}"));
                    let updated_at_ms = match s.get("updatedAt") {
                        Some(Value::String(text)) => chrono::DateTime::parse_from_rfc3339(text)
                            .map(|d| d.timestamp_millis())
                            .unwrap_or(0),
                        Some(Value::Number(n)) => n.as_i64().unwrap_or(0),
                        _ => 0,
                    };
                    Some(AcpSession { session_id, cwd, title, updated_at_ms })
                })
                .collect()
        })
        .unwrap_or_default();
    let next = page
        .get("nextCursor")
        .and_then(Value::as_str)
        .filter(|c| !c.is_empty())
        .map(str::to_string);
    (sessions, next)
}

/// Newest first, one entry per session id, truncated to `limit`.
pub fn merge(mut sessions: Vec<AcpSession>, limit: Option<usize>) -> Vec<AcpSession> {
    sessions.sort_by(|a, b| {
        b.updated_at_ms
            .cmp(&a.updated_at_ms)
            .then_with(|| a.session_id.cmp(&b.session_id))
    });
    let mut seen = HashSet::new();
    sessions.retain(|s| seen.insert(s.session_id.clone()));
    if let Some(limit) = limit {
        sessions.truncate(limit);
    }
    sessions
}

/// Ask `lister` for every page of every cwd; a failing cwd is skipped.
pub async fn collect_sessions(lister: &dyn SessionLister, cwds: &BTreeSet<String>) -> Vec<AcpSession> {
    let mut out = Vec::new();
    for cwd in cwds {
        let mut cursor: Option<String> = None;
        for _ in 0..MAX_PAGES {
            let page = match lister.list_page(cwd, cursor.as_deref()).await {
                Ok(page) => page,
                Err(err) => {
                    warn!(cwd, error = %err, "mfcli session/list failed for project; skipping");
                    break;
                }
            };
            let (mut items, next) = parse_page(&page, cwd);
            out.append(&mut items);
            match next {
                Some(next) if cursor.as_deref() != Some(next.as_str()) => cursor = Some(next),
                _ => break,
            }
        }
    }
    out
}

pub fn to_thread(s: &AcpSession, agent: &str) -> Value {
    json!({
        "id": s.session_id,
        "sessionId": s.session_id,
        "forkedFromId": null,
        "preview": s.title,
        "ephemeral": false,
        "modelProvider": agent,
        "createdAt": s.updated_at_ms,
        "updatedAt": s.updated_at_ms,
        "status": {"type": "idle"},
        "path": "",
        "cwd": s.cwd,
        "cliVersion": "",
        "source": "appServer",
        "threadSource": null,
        "agentNickname": null,
        "agentRole": null,
        "gitInfo": null,
        "name": s.title,
        "turns": [],
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use std::sync::Mutex;

    struct FakeLister {
        pages: HashMap<String, Vec<Value>>,
        calls: Mutex<usize>,
    }

    #[async_trait]
    impl SessionLister for FakeLister {
        async fn list_page(&self, cwd: &str, cursor: Option<&str>) -> anyhow::Result<Value> {
            *self.calls.lock().unwrap() += 1;
            if cwd == "/broken" {
                anyhow::bail!("Internal error: boom");
            }
            if cwd == "/loop" {
                return Ok(json!({"sessions": [], "nextCursor": "same"}));
            }
            let idx: usize = cursor.map(|c| c.parse().unwrap()).unwrap_or(0);
            Ok(self.pages.get(cwd).and_then(|p| p.get(idx)).cloned().unwrap_or(json!({"sessions": []})))
        }
    }

    fn s(id: &str, cwd: &str, updated: &str) -> Value {
        json!({"sessionId": id, "cwd": cwd, "title": format!("t-{id}"), "updatedAt": updated})
    }

    #[test]
    fn requested_cwds_accepts_string_and_array() {
        assert_eq!(requested_cwds(&json!({"cwd": "/a"})), vec!["/a"]);
        assert_eq!(requested_cwds(&json!({"cwd": ["/a", "rel", "/b"]})), vec!["/a", "/b"]);
        assert!(requested_cwds(&json!({"cwd": "rel"})).is_empty());
        assert!(requested_cwds(&json!({})).is_empty());
    }

    #[test]
    fn parse_page_falls_back_to_requested_cwd_and_title() {
        let page = json!({"sessions": [{"sessionId": "x", "updatedAt": "2026-09-30T10:00:00Z"}], "nextCursor": null});
        let (items, next) = parse_page(&page, "/p");
        assert_eq!(items[0].cwd, "/p");
        assert_eq!(items[0].title, "Session x");
        assert_eq!(items[0].updated_at_ms, 1_790_762_400_000);
        assert_eq!(next, None);
    }

    #[test]
    fn merge_dedupes_sorts_and_limits() {
        let a = AcpSession { session_id: "a".into(), cwd: "/p".into(), title: "a".into(), updated_at_ms: 1 };
        let b = AcpSession { session_id: "b".into(), cwd: "/p".into(), title: "b".into(), updated_at_ms: 3 };
        let a_newer = AcpSession { updated_at_ms: 5, ..a.clone() };
        let merged = merge(vec![a, b.clone(), a_newer.clone()], None);
        assert_eq!(merged, vec![a_newer.clone(), b]);
        assert_eq!(merge(merged, Some(1)), vec![a_newer]);
    }

    #[tokio::test]
    async fn collect_sessions_follows_pages() {
        let lister = FakeLister {
            pages: HashMap::from([(
                "/p".to_string(),
                vec![
                    json!({"sessions": [s("1", "/p", "2026-09-30T10:00:00Z")], "nextCursor": "1"}),
                    json!({"sessions": [s("2", "/p", "2026-09-30T11:00:00Z")], "nextCursor": null}),
                ],
            )]),
            calls: Mutex::new(0),
        };
        let cwds = BTreeSet::from(["/p".to_string()]);
        let ids: Vec<_> = collect_sessions(&lister, &cwds).await.into_iter().map(|s| s.session_id).collect();
        assert_eq!(ids, vec!["1", "2"]);
    }

    #[tokio::test]
    async fn collect_sessions_skips_failing_cwd() {
        let lister = FakeLister {
            pages: HashMap::from([(
                "/ok".to_string(),
                vec![json!({"sessions": [s("1", "/ok", "2026-09-30T10:00:00Z")]})],
            )]),
            calls: Mutex::new(0),
        };
        let cwds = BTreeSet::from(["/broken".to_string(), "/ok".to_string()]);
        let ids: Vec<_> = collect_sessions(&lister, &cwds).await.into_iter().map(|s| s.session_id).collect();
        assert_eq!(ids, vec!["1"]);
    }

    #[tokio::test]
    async fn collect_sessions_stops_on_repeated_cursor() {
        let lister = FakeLister { pages: HashMap::new(), calls: Mutex::new(0) };
        let cwds = BTreeSet::from(["/loop".to_string()]);
        collect_sessions(&lister, &cwds).await;
        assert_eq!(*lister.calls.lock().unwrap(), 2);
    }
}
```

- [ ] **Step 5: `lib.rs`（bridge 本体与测试）**

```rust
//! MyFlicker (`mfcli`) bridge: ACP over `mfcli acp`.
//!
//! All mfcli-specific behavior lives here; `acp-bridge` stays generic.
//! - launch: `<bin> acp`, with the client advertising no fs/terminal
//!   support (mfcli runs its own tools on this machine) and model
//!   discovery enabled (mfcli does not persist prompt-less sessions);
//! - `thread/list`: mfcli's `session/list` needs a `cwd`, so the bridge
//!   lists every known project (`~/.codeflicker/data.json` + its own cwd
//!   index + the request's cwd) on the secondary process and merges;
//! - `thread/start` / `thread/resume` / `turn/start`: keep the cwd index
//!   current and seed the generic bridge with a session's real cwd.

pub mod index;
pub mod listing;
pub mod projects;

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use alleycat_acp_bridge::AcpBridge;
use alleycat_bridge_core::{Bridge, Conn, JsonRpcError, ProcessLauncher, error_codes};
use anyhow::{Context, Result};
use async_trait::async_trait;
use serde_json::{Value, json};

use crate::index::CwdIndex;

#[derive(Debug, Clone)]
pub struct MfcliPaths {
    /// mfcli's `data.json` (project directories).
    pub data_json: PathBuf,
    /// Where the bridge keeps its `sessionId → cwd` index.
    pub index_file: PathBuf,
}

impl MfcliPaths {
    pub fn default_for(state_dir: &Path) -> Self {
        let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("/tmp"));
        Self {
            data_json: home.join(".codeflicker/data.json"),
            index_file: state_dir.join("mfcli-sessions.json"),
        }
    }
}

pub struct MfcliBridge {
    inner: Arc<AcpBridge>,
    index: CwdIndex,
    data_json: PathBuf,
}

fn internal(message: impl Into<String>) -> JsonRpcError {
    JsonRpcError { code: error_codes::INTERNAL_ERROR, message: message.into(), data: None }
}

fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

fn thread_id(params: &Value) -> Option<String> {
    params.get("threadId").and_then(Value::as_str).map(str::to_string)
}

impl MfcliBridge {
    pub fn new(inner: Arc<AcpBridge>, paths: MfcliPaths) -> Self {
        Self { inner, index: CwdIndex::load(paths.index_file), data_json: paths.data_json }
    }

    /// Build the bridge. Nothing is spawned until the first phone connects,
    /// so a missing `mfcli` binary does not fail the daemon.
    pub async fn build(
        bin: impl Into<PathBuf>,
        paths: MfcliPaths,
        launcher: Arc<dyn ProcessLauncher>,
    ) -> Result<Arc<Self>> {
        let inner = AcpBridge::builder()
            .agent_bin(bin)
            .agent_args(vec!["acp".to_string()])
            .client_capabilities(json!({"fs": {"readTextFile": false, "writeTextFile": false}, "terminal": false}))
            .discover_models(true)
            .pool_capacity(8)
            .launcher(launcher)
            .build()
            .await
            .context("building inner AcpBridge for mfcli")?;
        Ok(Arc::new(Self::new(inner, paths)))
    }

    /// Give the generic bridge a session's real cwd before it restores the
    /// session in a fresh process (daemon restart, first turn on a thread
    /// the phone never resumed in this process).
    fn seed_session_cwd(&self, params: &Value) {
        if let Some(id) = thread_id(params) {
            if self.inner.session_cwd(&id).is_none() {
                if let Some(cwd) = self.index.cwd_for(&id) {
                    self.inner.set_session_cwd(&id, &cwd);
                }
            }
        }
    }

    async fn thread_list(&self, ctx: &Conn, params: Value) -> Result<Value, JsonRpcError> {
        let requested = listing::requested_cwds(&params);
        let limit = params.get("limit").and_then(Value::as_u64).map(|n| n as usize);
        let cwds: BTreeSet<String> = if requested.is_empty() {
            let data_json = self.data_json.clone();
            let mut cwds: BTreeSet<String> =
                tokio::task::spawn_blocking(move || projects::project_dirs(&data_json))
                    .await
                    .unwrap_or_default()
                    .into_iter()
                    .collect();
            cwds.extend(self.index.cwds());
            cwds
        } else {
            requested.into_iter().collect()
        };
        let client = self
            .inner
            .ensure_aux_client(ctx)
            .await
            .map_err(|e| internal(format!("Failed to get mfcli ACP client: {e}")))?;
        let sessions = listing::merge(listing::collect_sessions(client.as_ref(), &cwds).await, limit);
        self.index.record_all(
            sessions
                .iter()
                .map(|s| (s.session_id.clone(), s.cwd.clone(), s.updated_at_ms)),
        );
        let agent = ctx.session().agent.to_string();
        let data: Vec<Value> = sessions.iter().map(|s| listing::to_thread(s, &agent)).collect();
        Ok(json!({"data": data, "nextCursor": null, "backwardsCursor": null}))
    }

    async fn thread_resume(&self, ctx: &Conn, mut params: Value) -> Result<Value, JsonRpcError> {
        let id = thread_id(&params);
        let has_cwd = params.get("cwd").and_then(Value::as_str).is_some_and(|c| c.starts_with('/'));
        if let (Some(id), false) = (&id, has_cwd) {
            if let (Some(cwd), Some(obj)) = (self.index.cwd_for(id), params.as_object_mut()) {
                obj.insert("cwd".to_string(), json!(cwd));
            }
        }
        let cwd = params.get("cwd").and_then(Value::as_str).map(str::to_string);
        let response = self.inner.dispatch(ctx, "thread/resume", params).await?;
        if let (Some(id), Some(cwd)) = (id, cwd) {
            self.index.record_all([(id, cwd, now_ms())]);
        }
        Ok(response)
    }

    async fn thread_start(&self, ctx: &Conn, params: Value) -> Result<Value, JsonRpcError> {
        let cwd = params.get("cwd").and_then(Value::as_str).map(str::to_string);
        let response = self.inner.dispatch(ctx, "thread/start", params).await?;
        if let (Some(id), Some(cwd)) = (response.pointer("/thread/id").and_then(Value::as_str), cwd) {
            self.index.record_all([(id.to_string(), cwd, now_ms())]);
        }
        Ok(response)
    }
}

#[async_trait]
impl Bridge for MfcliBridge {
    async fn initialize(&self, ctx: &Conn, params: Value) -> Result<Value, JsonRpcError> {
        self.inner.initialize(ctx, params).await
    }

    async fn dispatch(&self, ctx: &Conn, method: &str, params: Value) -> Result<Value, JsonRpcError> {
        match method {
            "thread/list" => self.thread_list(ctx, params).await,
            "thread/resume" => self.thread_resume(ctx, params).await,
            "thread/start" => self.thread_start(ctx, params).await,
            "turn/start" => {
                self.seed_session_cwd(&params);
                self.inner.dispatch(ctx, method, params).await
            }
            _ => self.inner.dispatch(ctx, method, params).await,
        }
    }

    async fn notification(&self, ctx: &Conn, method: &str, params: Value) {
        self.inner.notification(ctx, method, params).await;
    }

    async fn shutdown(&self) {
        self.inner.shutdown().await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use alleycat_bridge_core::LocalLauncher;

    fn paths(dir: &Path) -> MfcliPaths {
        MfcliPaths { data_json: dir.join("data.json"), index_file: dir.join("idx.json") }
    }

    #[tokio::test]
    async fn build_succeeds_when_binary_is_missing() {
        let dir = tempfile::tempdir().unwrap();
        let bridge = MfcliBridge::build("/nonexistent/mfcli", paths(dir.path()), Arc::new(LocalLauncher)).await;
        assert!(bridge.is_ok());
    }

    #[tokio::test]
    async fn seeds_inner_cwd_from_index() {
        let dir = tempfile::tempdir().unwrap();
        let p = paths(dir.path());
        CwdIndex::load(p.index_file.clone()).record_all([("s1".to_string(), "/proj".to_string(), 1)]);
        let bridge = MfcliBridge::build("/nonexistent/mfcli", p, Arc::new(LocalLauncher)).await.unwrap();
        bridge.seed_session_cwd(&json!({"threadId": "s1"}));
        assert_eq!(bridge.inner.session_cwd("s1").as_deref(), Some("/proj"));
        bridge.seed_session_cwd(&json!({"threadId": "unknown"}));
        assert_eq!(bridge.inner.session_cwd("unknown"), None);
    }
}
```

- [ ] **Step 6: 跑测试**

Run: `cargo test -p alleycat-mfcli-bridge`
Expected: PASS（projects 3、index 2、listing 6、lib 2）。

- [ ] **Step 7: 提交**

```bash
git add Cargo.toml Cargo.lock crates/mfcli-bridge
git commit -m "mfcli-bridge: ACP bridge for mfcli with multi-project thread list

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: daemon 注册 `mfcli`

**Files:**
- Modify: `crates/alleycat/Cargo.toml`（加 `alleycat-mfcli-bridge = { workspace = true }`）
- Modify: `crates/alleycat/src/config.rs:80-115`、`:270-310`、tests
- Modify: `crates/alleycat/src/agent_manifest.rs:186-220`
- Modify: `crates/alleycat/src/agents.rs`（enum、构建、`list_agents`、`agent_id`、映射函数、`is_enabled`、`mfcli_available`、tests）
- Modify: `crates/alleycat/src/push/mod.rs:48-50`
- Test: `crates/alleycat/src/push/tests.rs`

**Interfaces:**
- Consumes: Task 6 的 `MfcliBridge::build`、`MfcliPaths::default_for`。
- Produces: `AgentKind::Mfcli`、`MfcliAgentConfig { enabled: bool, bin: String }`、`AgentsConfig.mfcli`、manifest 条目 `mfcli`。

- [ ] **Step 1: 写失败的测试**

In `config.rs` tests, extend `default_config_routes_codex_to_local_app_server` with:

```rust
        assert!(config.agents.mfcli.enabled);
        assert_eq!(config.agents.mfcli.bin, "mfcli");
```

and add:

```rust
    #[test]
    fn mfcli_config_parses_partial_table() {
        let config: HostConfig =
            toml::from_str("token = \"abc\"\n[agents.mfcli]\nbin = \"/opt/mf/mfcli\"\n").unwrap();
        assert!(config.agents.mfcli.enabled);
        assert_eq!(config.agents.mfcli.bin, "/opt/mf/mfcli");
        assert!(config.agents.enabled_by_name("mfcli"));
    }
```

At the end of `agent_manifest.rs` add:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mfcli_manifest_entry() {
        let m = manifest_for("mfcli").expect("mfcli manifest");
        assert_eq!(m.display_name, "MyFlicker");
        assert_eq!(m.title, Some("MyFlicker"));
        assert_eq!(m.aliases, &["myflicker", "codeflicker"]);
        assert!(m.is_beta);
        assert_eq!(m.sort_order, 9);
        assert!(!m.supports_ssh_bridge);
        assert_eq!(manifest_for("shell").unwrap().sort_order, 10);
    }
}
```


In the `agents.rs` tests module add:

```rust
    #[test]
    fn mfcli_agent_kind_round_trips() {
        assert_eq!(agent_kind_from_str("mfcli"), Some(AgentKind::Mfcli));
        assert_eq!(agent_kind_str(AgentKind::Mfcli), "mfcli");
        assert_eq!(AgentManager::agent_id("mfcli"), Some("mfcli"));
    }
```

In `crates/alleycat/src/push/tests.rs` (the `mod tests;` file of `push/mod.rs`) add:

```rust
#[test]
fn mfcli_turns_are_push_eligible() {
    assert!(super::BRIDGE_PUSH_AGENTS.contains(&"mfcli"));
}
```

- [ ] **Step 2: 确认失败**

Run: `cargo test -p alleycat mfcli`
Expected: 编译失败（`mfcli` 字段、`AgentKind::Mfcli` 不存在）。

- [ ] **Step 3: 实现**

`crates/alleycat/Cargo.toml` `[dependencies]`: add `alleycat-mfcli-bridge = { workspace = true }` next to `alleycat-grok-bridge`.

`config.rs`: add `pub mfcli: MfcliAgentConfig,` to `AgentsConfig` after `grok`; add `"mfcli" => self.mfcli.enabled,` to `enabled_by_name`; add after `GrokAgentConfig`'s `Default` impl:

```rust
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct MfcliAgentConfig {
    pub enabled: bool,
    /// `mfcli` executable (an npm/nvm install is a `#!/usr/bin/env node`
    /// script; give an absolute path if the login shell does not load nvm).
    pub bin: String,
}

impl Default for MfcliAgentConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            bin: "mfcli".to_string(),
        }
    }
}
```

`agent_manifest.rs`: insert before the `shell` entry, and change shell's `sort_order` to `10`:

```rust
    AgentManifest {
        name: "mfcli",
        display_name: "MyFlicker",
        wire: AgentWire::Jsonl,
        title: Some("MyFlicker"),
        is_beta: true,
        sort_order: 9,
        description: Some("Kuaishou MyFlicker coding agent (mfcli acp)."),
        aliases: &["myflicker", "codeflicker"],
        locks_reasoning_effort_after_activity: false,
        visible_modes: None,
        supports_ssh_bridge: false,
        uses_direct_codex_port: false,
        supports_thread_permission_overrides: false,
        reports_effective_thread_permissions: false,
    },
```

`agents.rs`:
- import: `use alleycat_mfcli_bridge::{MfcliBridge, MfcliPaths};`
- `AgentKind`: add `Mfcli,` before `Shell`.
- after the Grok bridge is built:

```rust
        // MyFlicker (`mfcli acp`) is another ACP agent; all mfcli launch
        // and listing knowledge lives in `mfcli-bridge`.
        let mfcli_state = crate::paths::state_dir()
            .map(|dir| dir.join("mfcli"))
            .unwrap_or_else(|_| std::env::temp_dir().join("alleycat-mfcli"));
        let mfcli_bridge = MfcliBridge::build(
            PathBuf::from(&snapshot.agents.mfcli.bin),
            MfcliPaths::default_for(&mfcli_state),
            Arc::clone(&launcher),
        )
        .await
        .context("building mfcli bridge")?;
```

- `bridges.insert(AgentKind::Mfcli, mfcli_bridge);` after the Grok insert.
- `list_agents`: `"mfcli" => self.mfcli_available(&launch_env),`
- `agent_id`: `"mfcli" => Some("mfcli"),`
- `agent_kind_from_str`: `"mfcli" => Some(AgentKind::Mfcli),`; `agent_kind_str`: `AgentKind::Mfcli => "mfcli",`; `is_enabled`: `AgentKind::Mfcli => self.mfcli.enabled,`
- next to `grok_available`:

```rust
    fn mfcli_available(&self, env: &LaunchEnvironment) -> bool {
        let cfg = self.config.load();
        cfg.agents.mfcli.enabled && program_available(env, &cfg.agents.mfcli.bin)
    }
```

`push/mod.rs`: `"claude", "pi", "opencode", "amp", "droid", "hermes", "devin", "grok", "mfcli",`

Fix any other non-exhaustive `match` on `AgentKind` the compiler reports by adding the `Mfcli` arm alongside `Grok`.

- [ ] **Step 4: 确认通过**

Run: `cargo test -p alleycat`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add crates/alleycat Cargo.lock
git commit -m "alleycat: register the mfcli (MyFlicker) agent

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 文档、全量检查与真实 mfcli 一致性

**Files:**
- Modify: `crates/acp-bridge/README.md`（整篇替换）

- [ ] **Step 1: 重写 acp-bridge README**

Replace `crates/acp-bridge/README.md` with:

````markdown
# alleycat-acp-bridge

Codex app-server façade over ACP (Agent Client Protocol) agents. The
alleycat daemon wraps it per agent: `devin-bridge` (Devin), `grok-bridge`
(Grok) and `mfcli-bridge` (MyFlicker). Agent-specific launch and listing
logic stays in those crates.

## Building one

```rust
let bridge = AcpBridge::builder()
    .agent_bin("mfcli")
    .agent_args(vec!["acp".into()])
    .client_capabilities(json!({"fs": {"readTextFile": false, "writeTextFile": false}, "terminal": false}))
    .discover_models(true)
    .pool_capacity(8)
    .launcher(launcher)
    .build()
    .await?;
```

`AcpBridge::builder().from_env()` reads `ACP_BRIDGE_AGENT_BIN`,
`ACP_BRIDGE_AGENT_ARGS`, `ACP_BRIDGE_STATE_DIR`, `ACP_BRIDGE_POOL_CAPACITY`
and `ACP_BRIDGE_IDLE_TTL_SECS` (used by the standalone binary and the
conformance suite; the daemon sets everything explicitly).

## Processes

- One primary ACP process per phone connection (`<agent>:<node_id>`) and one
  secondary process (`…:aux`) for read-only calls (`session/list`, model
  discovery), so listing never waits behind a streaming prompt.
- Processes idle for 300 s are killed. A respawned or crashed-and-replaced
  process is re-sent `initialize`, and a session is restored with
  `session/resume` (or `session/load`) before its next prompt.
- Requests on one process are serialized: two turns on one phone's primary
  process run one after the other.

## Method mapping

| Codex | ACP |
|---|---|
| `initialize` | `initialize` (`protocolVersion: 1`) |
| `thread/start` | `session/new`, then `session/set_config_option` for a requested model |
| `thread/resume` | `session/load` (history rebuilt from the replay) |
| `thread/list` | `session/list` (with the request's `cwd`) on the secondary process |
| `turn/start` | restore if needed, `session/set_config_option` for model / thinking level, streaming `session/prompt` |
| `turn/interrupt` | `session/cancel` |
| `model/list` | models from `configOptions[id=model]`, thinking levels from `configOptions[id=thought_level]` |

Permission requests (`session/request_permission`) are approved
automatically. `turn/steer`, rollback, archive and review are not supported.
````

- [ ] **Step 2: 全量测试与 clippy**

```bash
cd ~/Desktop/Project/Person/Project/alleycat-mfcli
cargo test --workspace 2>&1 | tail -20
cargo clippy -p alleycat-acp-bridge -p alleycat-mfcli-bridge -p alleycat --all-targets 2>&1 \
  | grep -E -A 3 "^(warning|error)" | grep -E "crates/(acp-bridge|mfcli-bridge|alleycat)/" | sort -u
```

Expected: 测试全部 PASS（`conformance_acp` 在没有 `devin` / `ACP_BRIDGE_AGENT_BIN` 时自行跳过）。clippy：修掉落在本计划改动过的行上的 warning；这些文件里原有的 warning 不动。

- [ ] **Step 3: 真实 mfcli 一致性（会消耗少量模型调用）**

```bash
ACP_BRIDGE_AGENT_BIN="$(command -v mfcli)" ACP_BRIDGE_AGENT_ARGS=acp \
  cargo test -p alleycat-bridge-conformance --test conformance conformance_acp -- --nocapture 2>&1 | tail -40
```

Expected: PASS。失败时把失败的断言和 transcript 摘要记下来；属于本计划范围内的问题（握手、恢复、列表、模型）就修，属于 mfcli 自身协议差异的记进 PR 描述，不在这里硬改。

- [ ] **Step 4: 提交**

```bash
git add crates/acp-bridge/README.md
git commit -m "acp-bridge: document processes, method mapping and wrappers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: 本机主机部署（开发构建）

不提交代码，只把用本地 alleycat 构建的 daemon 装进 `/Applications/AgentBuddy.app` 并确认 mfcli 可用。

- [ ] **Step 1: 用本地 alleycat 构建 daemon**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
rm -f .build-stamps/kittylitter-dev/Cargo.toml
make .build-stamps/kittylitter-dev/Cargo.toml ALLEYCAT_DEV_DIR="$HOME/Desktop/Project/Person/Project/alleycat-mfcli"
grep alleycat .build-stamps/kittylitter-dev/Cargo.toml
PATH="$HOME/.cargo/bin:$PATH" cargo build --release \
  --manifest-path .build-stamps/kittylitter-dev/Cargo.toml --bin agentbuddy
ls -la .build-stamps/kittylitter-dev/target/release/agentbuddy
```

Expected: 生成的 manifest（`STAMPS := $(ROOT)/.build-stamps`）指向 `alleycat-mfcli/crates/alleycat`；release 构建成功。

- [ ] **Step 2: 备份并替换 sidecar，重签，重启**

```bash
APP=/Applications/AgentBuddy.app
mkdir -p ~/.agentbuddy/backups
cp "$APP/Contents/MacOS/agentbuddy" ~/.agentbuddy/backups/agentbuddy-sidecar-$(date +%Y%m%d%H%M%S)
cp .build-stamps/kittylitter-dev/target/release/agentbuddy "$APP/Contents/MacOS/agentbuddy"
codesign --force --sign - "$APP/Contents/MacOS/agentbuddy"
codesign --force --sign - --options runtime \
  --entitlements apps/desktop/src-tauri/entitlements.plist "$APP"
launchctl kickstart -k "gui/$(id -u)/com.akashark.agentbuddycli"
sleep 3
"$APP/Contents/MacOS/agentbuddy" status --json | python3 -c \
  "import json,sys; d=json.load(sys.stdin); [print(a['name'], a['available'], a['display_name']) for a in d['agents']]"
```

Expected: 列表里有 `mfcli True MyFlicker`，排在 `grok` 之后、`shell` 之前；其余 agent 的可用性和部署前一致（部署前只有 codex 为 True）。回滚：把备份拷回、重复两次 `codesign` 和 `kickstart`。

- [ ] **Step 3: 主机日志冒烟**

```bash
tail -n 50 ~/Library/Logs/com.akashark.agentbuddycli/daemon.log | grep -i -E "mfcli|error" || true
```

Expected: 没有 `building mfcli bridge` 相关错误。

---

### Task 10: Android 选择器与 QA 矩阵

**Files:**
- Modify: `apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/discovery/DiscoveryChooser.kt:115-125`
- Modify: `apps/android/docs/qa-matrix.md`

- [ ] **Step 1: 选择器加 mfcli**

In `AgentBuddyAgents` add `"mfcli",` after `"grok",`:

```kotlin
private val AgentBuddyAgents: List<AgentRuntimeKind> = listOf(
    "codex",
    "pi",
    "amp",
    "opencode",
    "claude",
    "droid",
    "hermes",
    "devin",
    "grok",
    "mfcli",
)
```

(`SplashProviders` 不改：它的每一项都需要 drawable，官方图标到位后再加。)

- [ ] **Step 2: QA 矩阵**

Append to `apps/android/docs/qa-matrix.md`:

```markdown
## MyFlicker (mfcli) agent (design `docs/superpowers/specs/2026-09-30-mfcli-acp-agent-design.md`)

| Area | Expected (Android) |
|---|---|
| Agent picker | Paired Mac with `mfcli` on the login-shell PATH shows MyFlicker (beta, letter icon) |
| Model list | 万擎 models before the first message; thinking levels low / medium / high / xhigh |
| New task | Streaming reply; tool calls (command, file edit with diff) render |
| Model / thinking switch | Next turn uses the new value (session file `model` field) |
| History | Task survives leaving the screen and force-stopping the app; history replays on open |
| Idle > 5 min | Next message still works (process respawn + session resume) |
| Terminal sessions | Session started with `mfcli` in a project from `~/.codeflicker/data.json` is listed and can be continued |
| Approvals | Not shown on the phone (auto-approved on the host; phase 2) |
```

- [ ] **Step 3: 构建、单测、安装**

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
make test-android 2>&1 | tail -15
make android android-install 2>&1 | tail -15
```

Expected: 单测 PASS；APK 安装到 Pixel 9（`adb devices -l` 能看到 `Pixel_9`）。

- [ ] **Step 4: 提交（只暂存这两个文件）**

```bash
git add apps/android/app/src/main/java/com/akashark/agentbuddy/android/ui/discovery/DiscoveryChooser.kt \
        apps/android/docs/qa-matrix.md
git status --short
git commit -m "android: list MyFlicker in the agent chooser and QA matrix

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: `git status --short` 在提交前只显示这两个文件为已暂存。

---

### Task 11: Android 端到端验收（Pixel 9）

需要用户在手机上操作的步骤，直接请用户做并回报；其余证据由执行者采集。日志：`adb logcat -d`，daemon `~/Library/Logs/com.akashark.agentbuddycli/daemon.log*`。

- [ ] **Step 1: agent 出现**：App 里打开已配对的 Mac，选择 agent 处有 MyFlicker（beta）。证据：截图或用户确认。
- [ ] **Step 2: 模型列表**：发消息前打开模型选择，是万擎模型列表而不是只有一个 `mfcli`。
- [ ] **Step 3: 新任务**：在 `/Users/sharker/Desktop/Project/Person/Project/AgentBuddy` 项目里新建 MyFlicker 任务，发「列出当前目录的文件，然后在 /tmp 下创建 mfcli-e2e.txt 写入 hi」。回复流式出现，命令和文件编辑卡片可见；`cat /tmp/mfcli-e2e.txt` 输出 `hi`。
- [ ] **Step 4: 多轮**：再发两轮追问，都正常完成。
- [ ] **Step 5: 切换模型 / 思考强度**：切到另一个模型和 high，再发一轮。证据：

```bash
f=$(ls -t ~/.codeflicker/projects/*/*.jsonl | head -1)
python3 -c "import json,sys; [print(m.get('model')) for m in map(json.loads, open('$f')) if m.get('role')=='assistant']" | tail -3
```

最后一条 assistant 的 `model` 是新选的模型。

- [ ] **Step 6: 历史**：返回首页再进入任务，然后 `adb shell am force-stop com.akashark.agentbuddy.android` 后重新打开，任务仍在列表里，进入后历史完整。
- [ ] **Step 7: 闲置恢复**：闲置 6 分钟以上后再发一轮，正常回复。证据：daemon 日志里有 `Evicting` 以及之后的 `restoring ACP session in fresh process`。
- [ ] **Step 8: 终端会话**：在项目目录里运行 `mfcli`，交互式发一句话后退出；确认 `~/.codeflicker/data.json` 的 `projects` 里有这个目录；手机刷新列表后能看到这条会话并能续接。
- [ ] **Step 9（附加）: 推送**：App 切到后台后让一轮任务跑完，收到完成通知。
- [ ] **Step 10: 记录结果**：把每一项的结果（通过 / 失败 + 证据位置）写进 `docs/superpowers/plans/2026-09-30-mfcli-acp-agent.md` 末尾的「验收记录」小节并提交（只暂存该文件）。失败项先用 superpowers:systematic-debugging 定位，修复落在对应 Task 的文件里，再回到本任务复验。

---

### Task 12: 推送 alleycat、更新 pin、正式 sidecar

- [ ] **Step 1: 征得同意后推送 alleycat**

先问用户：「可以把 `feat/mfcli-agent` 推到 AkaShark/alleycat 吗？」得到明确同意后：

```bash
cd ~/Desktop/Project/Person/Project/alleycat-mfcli
git push -u origin feat/mfcli-agent
git rev-parse HEAD
```

有 `gh` 时开 draft PR（base `feat/host-push-notifications`）；没有就把 `https://github.com/AkaShark/alleycat/compare/feat/host-push-notifications...feat/mfcli-agent?expand=1` 给用户。PR 描述写明：通用修复清单、Devin / Grok 未做真机回归（本机没有），以及第 10 节的已知限制。

- [ ] **Step 2: 更新两处 pin**

用 Step 1 的完整 SHA（记为 `NEW`）替换 `a5bdd83f1dedc9169610efe5a245f82bb0198f13`：

```bash
cd /Users/sharker/Desktop/Project/Person/Project/AgentBuddy
NEW=<Step 1 输出的完整 SHA>
sed -i '' "s/a5bdd83f1dedc9169610efe5a245f82bb0198f13/$NEW/g" services/kittylitter/Cargo.toml shared/rust-bridge/Cargo.toml
grep -n "$NEW" services/kittylitter/Cargo.toml shared/rust-bridge/Cargo.toml
```

同时把 `services/kittylitter/Cargo.toml:20-25` 注释里的分支说明改为：pin 在 `feat/mfcli-agent`（叠在 `feat/host-push-notifications` / AkaShark/alleycat#1 之上）。

- [ ] **Step 3: 更新 lock 并检查**

```bash
PATH="$HOME/.cargo/bin:$PATH" cargo check --manifest-path services/kittylitter/Cargo.toml 2>&1 | tail -3
make rust-check 2>&1 | tail -5
```

Expected: 两个都成功，`services/kittylitter/Cargo.lock`、`shared/rust-bridge/Cargo.lock` 里 alleycat 源变为 `NEW`。

- [ ] **Step 4: CLAUDE.md**

把 `CLAUDE.md` 里「**alleycat fork**」一条中的 `pinned to commit a5bdd83f… on the fork branch feat/host-push-notifications (upstream 3c6dfe2 plus host push notifications, draft PR AkaShark/alleycat#1; not on the fork's main yet)` 改为：pinned to `NEW` on the fork branch `feat/mfcli-agent` (host push notifications from draft PR AkaShark/alleycat#1 plus the MyFlicker/mfcli ACP agent and acp-bridge fixes; not on the fork's `main` yet)。其余句子不动。

- [ ] **Step 5: 正式 sidecar 部署**

```bash
make desktop-sidecar 2>&1 | tail -3
APP=/Applications/AgentBuddy.app
cp apps/desktop/src-tauri/binaries/agentbuddy-$(rustc --print host-tuple) "$APP/Contents/MacOS/agentbuddy"
codesign --force --sign - "$APP/Contents/MacOS/agentbuddy"
codesign --force --sign - --options runtime --entitlements apps/desktop/src-tauri/entitlements.plist "$APP"
launchctl kickstart -k "gui/$(id -u)/com.akashark.agentbuddycli"
sleep 3
"$APP/Contents/MacOS/agentbuddy" status --json | python3 -c \
  "import json,sys; d=json.load(sys.stdin); print([a['name'] for a in d['agents'] if a['available']])"
```

Expected: 输出包含 `mfcli`。

- [ ] **Step 6: Android 用新 pin 重建并复验**

```bash
make android android-install 2>&1 | tail -5
```

在手机上复验 Task 11 的 Step 1、3、6。

- [ ] **Step 7: 提交**

```bash
git add services/kittylitter/Cargo.toml services/kittylitter/Cargo.lock \
        shared/rust-bridge/Cargo.toml shared/rust-bridge/Cargo.lock CLAUDE.md
git status --short
git commit -m "deps: pin alleycat with the mfcli agent

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

AgentBuddy 分支推送同样先征得用户同意。

---

## 验收记录

2026-10-01，Pixel 9（Android dev 版），Mac 上是用 alleycat `feat/mfcli-agent` 本地构建的 daemon（`/Applications/AgentBuddy.app` 内替换后 ad-hoc 重签）。日志：`~/Library/Logs/com.akashark.agentbuddycli/daemon.log.2026-10-01`。

| Step | 结果 | 证据 |
|---|---|---|
| 1 agent 出现 | 通过 | 已有配对要重新配对才会出现新 agent（见后续事项）；重新配对后选择 agent 处有 MyFlicker。 |
| 2 模型列表 | 通过 | 模型菜单是万擎模型列表（含 Claude Haiku 4.5、DeepSeek V4 Flash 等），不是单个 `mfcli`。 |
| 3 新任务 | 通过（换了项目目录） | `~/Desktop` 下的项目由 daemon 启动的 mfcli 会卡住（macOS「桌面」文件夹权限，见下）；改在 scratchpad 的 `acp-probe/ws` 项目验收。会话 `adacd055…`：`pwd`、`ls` 命令卡片和「新增 probe.txt」文件卡片都在；09:14:27 `cat /tmp/probe.txt` 输出 `hi`。 |
| 4 多轮 | 通过（修复后） | 第一次验收时实时视图把后面几轮的回复叠到第一轮的位置上：流式文字条目的 id 每轮从 `acp-agent-0` 重新编号，手机按 id 覆盖。alleycat `c8ed938` 把 id 改成按轮次区分（`tests/item_ids.rs`）。重新部署后同一任务连续两轮（apple / banana）各自显示思考和回复，顺序正确。复查发现历史回放里每轮也重复使用 `acp-user-0` / `acp-agent-1`，`d16ee21` 改为按轮次区分；重开任务后全部轮次完整，再发一轮（fig）也没有覆盖之前的回复。 |
| 5 切换模型 / 思考强度 | 通过（修复后） | 对话中切到 DeepSeek V4 Flash 后，会话文件里上一轮 `model` 是 `wanqing/claude-haiku-4.5`，这一轮是 `wanqing/deepseek-v4-flash`，回复「DeepSeek V4 Flash」。思考强度第一次验收不对：mfcli 的档位随模型变化（Claude Sonnet 5 是 low / medium / high / max，DeepSeek V4 Flash 只有 high / max，Auto、Kimi K2.6 等没有），bridge 却给所有模型套用同一组。alleycat `56de235` 改为按模型记录档位（首次 `model/list` 把临时会话逐个切过所有模型，再切回起始模型和档位）；手机没有 `max`，所以 `max` 以「极高」（`xhigh`）出现。重新部署后 Claude Sonnet 5 显示 低 / 中 / 高 / 极高；在 DeepSeek V4 Flash 的会话里选 Sonnet 5 + 低 发一轮，会话文件里这一轮是 `wanqing/claude-5-sonnet`，mfcli 保存的档位是 `low`（先切模型再切档位）。`d16ee21` 另外让新建会话和切换模型 / 档位在模型发现期间等待（发现最长 60 秒），并且当前档位是 `max` 时手机发来的 `xhigh` 不再把它降下来。已知行为：`session/set_config_option` 会改写 `~/.codeflicker/config.json` 的全局默认，手机上选模型或档位也会改变终端 mfcli 的默认值（已写进 mfcli-bridge 文档，并需反馈给 mfcli 维护方）。 |
| 6 历史 | 通过 | `am force-stop` 后重开，进入任务：三轮的用户消息、命令卡片和回复都在，顺序正确（历史来自 `session/load` 回放）。 |
| 7 闲置恢复 | 通过 | 09:22:22 `Evicting 1 idle clients`（ws 进程）；09:22:35 发消息，`spawning ACP agent process`（工作目录 ws）→ `restoring ACP session in fresh process` → `finalized turn`。 |
| 8 终端会话 | 通过（有限制） | 在 ws 运行 `mfcli -q "Terminal check: …"`（会话 `b5ec2152`），手机「项目 → ws」列表第一条就是它，进入后历史完整（问题和 `cherry`）。从手机续接：09:55:56 在 ws 起进程、`session/resume` 恢复，回复 `date`，并写回同一个会话文件。限制： `mfcli -q` 不会把目录写进 `~/.codeflicker/data.json` 的 `projects`（目前那里只有 `~/Downloads`），ws 是靠 bridge 自己的索引（`mfcli/mfcli-sessions.json`）找到的；只在终端用 `-q` 跑过、手机从没打开过的项目不会出现在列表里。 |
| 9 推送 | 未验 | 附加项，本轮没做。 |

`~/Desktop` 项目卡住：同样的环境变量和目录，手动运行 mfcli 不到 5 秒就好；daemon 在 `/tmp`、`$HOME`、`/` 下启动都正常。判断是 macOS 隐私保护拦了「桌面」文件夹：ad-hoc 签名的 AgentBuddy.app 没有授权，每次重签都会让授权失效。需要在「系统设置 → 隐私与安全性 → 完全磁盘访问权限（或文件与文件夹 → 桌面）」给 AgentBuddy 授权，换上正式 sidecar 之后再验一次。

2026-10-01 收尾：alleycat `feat/mfcli-agent` 推到 AkaShark/alleycat（`d16ee21`，draft PR AkaShark/alleycat#2，叠在 #1 上），两处 pin 改为 `d16ee21`，`cargo check`（kittylitter）和 `make rust-check` 通过；`make desktop-sidecar` 用 pin 的源码构建正式 sidecar，替换进 `/Applications/AgentBuddy.app` 并重签；`make android` + `make android-install` 用新 pin 重建 JNI 库并装到 Pixel 9。用户给 AgentBuddy 开了完全磁盘访问权限后，`~/Desktop/Project/Person/Project/AgentBuddy` 项目里的 MyFlicker 任务（会话 `4fe043fb…`）在该目录起进程、约 2.5 秒恢复会话，`pwd` 输出该目录，回复 `grape`，不再卡住。注意：每次 ad-hoc 重签都会让这个授权失效，需要重新授权。

### iOS（iPhone 16）

2026-10-01 至 10-02（UTC 10-02 02:30–02:51），iPhone 16（iOS 26.5），分支 `feat/ios-mfcli` 的 Debug 版：用 Xcode MCP 编译，`devicectl` 安装；手机界面用 WebDriverAgent 操作和截图，手机端 Rust 日志用 `devicectl device process launch --console` 抓取。Mac 上的 daemon 先是 alleycat `ac8b1d1`，修复命令卡片后换成 `4e95eff` 的正式 sidecar（`make desktop-sidecar`，替换进 `/Applications/AgentBuddy.app` 并重签，用户重新开了完全磁盘访问权限）。项目目录：`~/Desktop/Project/Person/Project/mfcli-ios-qa`（验收专用，验收后已删除），任务会话 `8b13171c…`，终端会话 `bfd290a4`。

| Step | 结果 | 证据 |
|---|---|---|
| 1 agent 出现 | 通过 | 这台 iPhone 的配对本来就选了 mfcli，不用重新配对：daemon 日志 `connect: dispatching to agent agent=mfcli`。新建任务的「搭档、模型与权限」里有 Codex 8 / MyFlicker 29 两个搭档；配对选择页的「兼容」图标条末尾是 MyFlicker 的「M」字母头像。 |
| 2 模型列表 | 通过 | 万擎模型列表比 Android 验收时多了 DeepSeek V4.1 Flash、Kimi K3 等。档位和 mfcli 实际报告的一致（用 ACP 直接问 mfcli 核对）：Claude Sonnet 5 是 低 / 中 / 高 / 极高（mfcli 为 low / medium / high / max）；DeepSeek V4 Flash 是 高 / 极高（high / max）；DeepSeek V4.1 Flash 和 Kimi K3 是 低 / 高 / 极高（low / high / max）；Auto 和 Kimi K2.6 没有推理强度区。iOS 原来直接显示 `low` / `xhigh`，`b0db9b8` 改成和 Android 一样的中文名。 |
| 3 新任务 | 通过（修复后） | DeepSeek V4 Flash 新建任务，回复流式出现，「Added hello-ios.txt」文件卡片在，磁盘上的文件内容是 `hello from iPhone 16`。第一次验收时 `pwd` / `ls` 的命令卡片只在重新打开任务后才出现，实时视图里没有，原因和修复见下。换上 `4e95eff` 后，第四轮的 `date && ls` 和第六轮的 `pwd` 命令卡片都实时出现。手机日志里能看到 `acp-tool-call_…` 的 `item/started` / `item/completed`，还有 `turn/completed`。 |
| 4 多轮 | 通过 | 同一任务共 7 轮（hello / apple / banana / cherry / Claude 的回复 / lemon / kiwi）。每轮的思考、命令和回复都在自己的位置，没有覆盖前面的回复。 |
| 5 切换模型 / 思考强度 | 通过 | 在 DeepSeek V4 Flash 的任务里切到 Claude Sonnet 5 + 低，第五轮会话文件里是 `model: wanqing/claude-5-sonnet`（前四轮是 `wanqing/deepseek-v4-flash`），`~/.codeflicker/config.json` 是 `thinkingLevel: low`。验收结束后已改回 `wanqing/gpt-6-astra` / `high`。 |
| 6 历史 | 通过 | 强杀后重开任务（WDA terminate / launch），前五轮的用户消息、`pwd` / `ls` / `ls -la` / `echo` / `cat` / `date && ls` 命令卡片、文件卡片和回复都在，顺序正确。 |
| 7 闲置恢复 | 通过 | 02:49:12 `Evicting 2 idle clients`（包括 mfcli-ios-qa 进程）；02:49:32 打开任务，在 mfcli-ios-qa 目录 `spawning ACP agent process`；02:49:55 发消息，`restoring ACP session in fresh process`（`session/resume`），02:50:06 `finalized turn`，回复 `lemon`。 |
| 8 终端会话 | 通过（有限制） | 在 mfcli-ios-qa 运行 `mfcli -q "reply with only the word cherry"`（会话 `bfd290a4`）。刷新任务列表后，「项目 → mfcli-ios-qa」的最近任务第一条就是它；打开后历史完整（问题和 `cherry`）。从手机回复，回复 `plum`，写回同一个会话文件。限制：iOS 冷启动不会调用 `thread/list`，终端新建的任务要等列表刷新（例如打开「全部任务」）后才出现。 |
| 9 推送 | 未通过（环境） | 手机订阅成功（`push subscription accepted`），回合结束后主机也排队了推送（`turn terminal queued for push`），但通知没有发出。原因是这台 Mac 直连 `workers.dev` 会超时，只能走代理，而 launchd 启动的 daemon 没有代理，Worker 的 `register` 一直失败。 |

**命令卡片只在重开后出现（第 3 步）**
- mfcli 的 `execute` 工具调用既没有 `rawInput.cwd`，也没有 `locations`，acp-bridge 就把 `commandExecution` 的 `cwd` 写成了 `""`。
- 上游 codex 把 `cwd` 定义为 `AbsolutePathBuf`，手机端的 app-server client 解析这条 `item/started` / `item/completed` 时失败。整条通知会被静默丢掉（`app_server_event_from_notification` 里的 `Err(_) => None`）。这种回合的 `turn/completed` 里带着同一条命令，也一起被丢掉。
- 历史能显示，是因为响应是带 base path 解析的。
- Android 用同一套 Rust 和同一个 client，同样受影响。
- alleycat 自己的 proto 把 `cwd` 定义为 `String`，所以主机测试一直没有发现。
- 修复是 alleycat `4e95eff`（在 `feat/host-push-notifications` 上）：实时流和 `session/load` 回放都改用会话目录（不知道时用 `/`），并加了测试。
- AgentBuddy `1657b41` 把两处 pin 改为 `4e95eff`；`cargo check`（kittylitter）和 `make rust-check` 都通过。

**「Mfcli」标签**
- iOS 没有这个问题。`AppModel.init` 把 `AgentRuntimeMetadataProvider` 接到了 `AppClient` 的 `AgentMetadataStore`。会话头部副标题是「MyFlicker · Sharkers-MacBook-Pro.local」，回复标签是「搭子 · MyFlicker」，输入框的搭档芯片是「MyFlicker」。iOS 的模型行没有副标题，图标的无障碍标签是 MyFlicker。
- Android 的 `AgentRuntimeMetadataProvider.lookup` / `.all` 从来没有被赋值，所有标签都退回到首字母大写的 id。BETA、排序和能力开关也都用的是默认值。
- `6a7e78e` 在 Android 的 `AppModel` 里把它接上了。审查发现，接上之后 Android 的 SSH 登录会把 Codex 送进 SSH 桥接选择器，所以又按 iOS 的规则排除了 `codex`。
- `make android-debug` 编译通过；Pixel 9 这次连不上，Android 真机还没复验。

**看到但没有处理（与 MyFlicker 无关）**
- 新任务的标题在 iOS 上是英文「Untitled session」。
- 新建项目的目录选择器有三个问题：切换主机后标签要过一会儿才更新；在 `/` 下「前往路径」输入绝对路径会得到 `//Users/…`；主目录显示为 `~` 时列目录失败过一次。
- 「全部任务」的分组副标题显示主机的 node id，而不是主机名。

## 修订任务（2026-09-30，见 spec 第 12 节）

### Task 6b: acp-bridge 按项目目录分进程（`process_per_cwd`）

- fake agent 新增 `--spawn-log <path>`（启动时追加进程目录）和配置 `echo_cwd`（prompt 回复里带进程目录）。
- 测试（`tests/per_cwd.rs`）：两个不同 `cwd` 的 `thread/start` 各自启动一个以该目录为工作目录的进程；某会话的 `turn/start` 进入它所属目录的进程（回复里的目录正确）；`process_per_cwd` 关闭时行为不变（仍是一个进程，工作目录为 daemon 目录）；没有任何目录时用 `$HOME`，不用 `/`；`recycle_process` 回收该连接的全部进程。
- 实现：`AcpClient::spawn` 接受工作目录；池 `get_client(key, cwd)`；`AcpBridge` 按方法与会话目录选 client；mfcli 构建时开启并把池容量设为 16。

### Task 6c: mfcli-bridge 从会话文件列任务、解析目录

- 新模块 `storage.rs`：`project_slug(path)`（用本机三个已知目录和 `/` 做测试）、`sessions_in(projects_dir, cwd)`（标题、更新时间、跳过没有用户消息的会话、损坏行跳过）、`find_session(projects_dir, cwds, id)`。
- `thread/list` 与 `resolve_cwd` 改用 `storage`；删除不再使用的 ACP `SessionLister` 列表路径。
- 测试覆盖：slug 规则（短路径、超长路径、`/`）、文件缺失或损坏、标题截断、按候选目录定位会话、未知会话报错。

完成后重新部署主机（Task 9 Step 2），从 Task 11 Step 3 重新开始端到端。
