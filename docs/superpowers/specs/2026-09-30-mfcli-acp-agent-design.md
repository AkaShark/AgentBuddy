# mfcli（MyFlicker）接入：alleycat ACP agent 适配

日期：2026-09-30
状态：设计待评审

## 1. 目标与非目标

**目标**

- 配对了 Mac 主机的手机上能选择 MyFlicker（本机 `mfcli`，快手内部 npm 包 `@myflicker/cli`），在项目里新建任务、多轮对话、切换模型与思考强度、回看和续接历史，其中包括 Mac 终端里用 `mfcli` 开的会话。
- 同事零配置可用：装了 AgentBuddy 桌面端和 `mfcli`（登录 shell 的 PATH 里能找到）的 Mac，主机自动把 MyFlicker 报为可用。
- 本期先在 Android（Pixel 9）上跑通端到端；共享 Rust 与主机逻辑对 iOS 同样生效，iOS 装饰项与真机验证放到下一轮。

**非目标（本期）**

- 手机端审批：ACP `session/request_permission` 继续由 bridge 自动允许，和 Devin / Grok 一致。转成手机审批卡片是二期（见第 11 节）。
- mfcli 的 plan / brainstorm 等 mode（它通过 `configOptions[id=mode]` 暴露，不是 bridge 读取的 ACP `modes`）。
- 手机 SSH 直连模式启动 mfcli（只走 alleycat 配对）。
- `turn/steer`、archive、rollback（通用 ACP bridge 本来就不支持）。
- 同一台手机上两个 mfcli 任务同时跑 turn（见第 10 节已知限制）。

**成功标准**：第 9.4 节的 Android 端到端清单全部通过，并留有 logcat、daemon 日志或 mfcli 会话文件作证据。

## 2. 背景事实（2026-09-30 本机探测）

用临时脚本直接驱动 `mfcli acp`（mfcli 0.3.26，内置 Neovate ACP agent 0.28.5）得到：

- stdio 上是干净的 JSON-RPC，日志全部走 stderr。
- `initialize` 返回 `loadSession: true`、`sessionCapabilities {list, fork, resume}`、`promptCapabilities {image, embeddedContext}`，没有 `authMethods`，直接复用本机登录态。
- **`protocolVersion` 必须是整数。** 传字符串 `"1.0.0"` 会被拒：`Invalid params … expected number, received string`（ACP 规范里它本来就是 uint16）。
- `session/new {cwd, mcpServers: []}` 同时返回 `models`（29 个万擎模型，`currentModelId`）和 `configOptions`：`model`、`thought_level`（low / medium / high / xhigh）、`mode`（default / plan / brainstorm / autoEdit / auto）。没有 `modes` 字段。
- `session/prompt` 流出 `agent_message_chunk`、`tool_call`、`tool_call_update`（带 `diff`）、`usage_update`、`available_commands_update`、`session_info_update`，结束于 `stopReason: end_turn`。
- 写文件会发标准 `session/request_permission`（`allow_once` / `allow_always` / `reject_once` / `reject_always`）；default mode 下 `echo` 不需要审批。
- `session/load` 在新进程里完整回放历史（user / tool / agent 片段）。
- `session/set_config_option` 对 `model` / `thought_level` / `mode` 都生效，`session/set_model` 生效，`session/set_mode` 返回 `Method not implemented`。
- **`session/list` 不带 `cwd` 返回空**；带 `cwd` 返回该目录下的会话（`sessionId`、`cwd`、`title`、`updatedAt`）。只 `session/new` 不发 prompt 的会话不会落盘，也不会被列出。
- 会话文件在 `~/.codeflicker/projects/<目录 slug>/<sessionId>.jsonl`，slug 有损，文件里没有结构化 cwd。`~/.codeflicker/data.json` 的 `projects` 键是真实项目路径（终端交互用过的项目）。

alleycat 现状（fork `AkaShark/alleycat`，当前 pin `a5bdd83`，分支 `feat/host-push-notifications`）：

- `crates/acp-bridge` 是通用的「ACP agent → Codex app-server」桥，Devin（`devin-bridge` 包装）和 Grok（`grok-bridge` 包装）都跑在上面。
- `translate.rs` 发 `protocolVersion: "1.0.0"`，对 mfcli 是硬阻塞。
- 进程池按 `agent:node_id` 分配进程（每台手机一个），闲置 300 秒淘汰；重新拉起的进程不会重发 `initialize`，也不知道之前加载过的会话。
- 每个 `AcpClient` 同一时刻只有一个在途请求（`request_lock`），`session/cancel` 是通知，不受这个锁影响。
- `turn/start` 忽略 `model` / `effort`；`thread/start` 回包里的 `model` 固定是 agent 名；`model/list` 的思考强度固定为 Medium、`input_modalities` 只有 text；还没有会话时只返回一个以 agent 名命名的占位模型。
- `thread/list` 发 `session/list {}`，出错返回空列表，结果里的 `cwd` 固定为空串；`thread/resume` 不知道 cwd 时传 `/`。
- 权限请求自动选第一个 allow 选项，不转给手机。
- 启动 agent 用 `UserEnvironmentLauncher`：daemon 环境叠加 `$SHELL -lic env`，本机 PATH 里有 nvm 的 `mfcli` 和 `node`（已验证）。

移动端与桌面端现状：

- 共享 Rust 和 Android 的 agent 列表、显示名、beta 标、排序、图标都由主机 `list_agents` 的 `AgentInfo` 驱动；未知 agent 名原样透传，图标按 `R.drawable.agent_<id>` 查找，缺失时回退为字母徽标。
- 模型按 agent 调 `model/list`，线程按模型所属 agent 路由。
- 桌面端 Agents 页按 daemon 返回的列表逐行渲染，编辑 `host.toml` 的 `agents.<name>`，对新 agent 无需改动。

## 3. 架构与数据流

```
Android App ──iroh──▶ alleycat daemon (Mac)
                        └─ AgentKind::Mfcli → MfcliBridge（mfcli-bridge）
                               ├─ thread/list、thread/resume：多目录 session/list + cwd 索引
                               └─ 其余方法 → AcpBridge（acp-bridge，通用修复）
                                      └─ 进程池：每手机一个主进程 + 一个辅助进程
                                             └─ `mfcli acp`（stdio ACP）
```

手机侧不需要新增协议或业务逻辑，改动只有显示层。

## 4. alleycat：通用 acp-bridge 修复

以下修复都符合 ACP 规范，对 Devin / Grok 同样有益。

1. **握手**
   - `codex_to_acp_initialize` 发 `protocolVersion: 1`（整数）。
   - 缓存 ACP `initialize` 回包里的 `agentCapabilities`（`loadSession`、`sessionCapabilities.resume/list`、`promptCapabilities.image`），供后续决策。
2. **进程生命周期**
   - 池里新拉起的进程先用缓存的初始化参数发 ACP `initialize`，再处理任何请求。
   - 每个 `AcpClient` 记录本进程已加载的会话集合（`session/new`、`session/load`、`session/resume` 成功后加入）。
   - `turn/start` 发现会话不在当前进程里时先恢复：agent 支持 `sessionCapabilities.resume` 就发 `session/resume`（不回放），否则 `loadSession` 为真时发 `session/load` 并丢弃回放，都不支持就报错。恢复需要的 cwd 取自 bridge 在内存里记下的会话 cwd（`thread/start`、`thread/resume` 时记录），通用层不依赖第 5 节的索引；daemon 重启后手机重连会先走 `thread/resume`，那时由 `mfcli-bridge` 补 cwd。
   - `session/resume` 的方法名和参数在实现第一步用真实 mfcli 确认（它声明了 `sessionCapabilities.resume`，但本次探测没有调用它）。
3. **模型与思考强度**
   - bridge 按会话记录 `configOptions` 的 `currentValue`（来自 `session/new`、`session/load`、`session/set_config_option` 的回包）。
   - `thread/start` 回包的 `model` 返回真实的 `currentValue`（没有时才回退到 agent 名）。
   - `thread/start` 和 `turn/start` 带了与当前值不同的 `model` 时，在发 prompt 前调 `session/set_config_option {configId: "model", value}`。
   - `effort` 同理，映射到 `thought_level`：

     | codex `ReasoningEffort` | mfcli `thought_level` |
     |---|---|
     | none / minimal / low | low |
     | medium | medium |
     | high | high |
     | xhigh / max | xhigh |

   - 目标值不在 agent 给出的选项里就跳过并记 warn，不让 turn 失败。
   - agent 没有 `configOptions` 时整段跳过，保持现状。
4. **模型列表**
   - `supported_reasoning_efforts` 按 `thought_level` 的真实选项反向映射（low→Low、medium→Medium、high→High、xhigh→XHigh），默认值取其 `currentValue`；没有该选项时保持现在的 Medium。
   - `input_modalities`：`promptCapabilities.image` 为真时加上 image。
   - 新增构建开关 `discover_models`（默认关）：模型目录为空时用辅助进程在 `$HOME` 发一次 `session/new` 拿目录并缓存，避免手机只看到占位模型。只在已确认「未发 prompt 的会话不落盘」的 agent 上打开（本期只有 mfcli）。
5. **线程列表**：`session/list` 结果保留真实 `cwd`，不再固定为空串。
6. **辅助进程**
   - 池里按 `agent:node_id:aux` 为每台手机额外分配一个进程，专门处理只读的 `session/list` 和模型发现。
   - 这样首页刷新列表不用排在正在流式输出的 `session/prompt` 后面。
   - 池容量 4 不变（每手机最多 2 个进程）。

## 5. alleycat：新 crate `mfcli-bridge`

照 `grok-bridge` 的写法：包装 `Arc<AcpBridge>`，只覆盖两个方法，其余透传。

- **启动**：`<bin> acp`（`bin` 来自配置，默认 `mfcli`），开启 `discover_models`。
- **cwd 索引**
  - bridge 状态目录下的 JSON 文件，记录 `sessionId → {cwd, updatedAt}`。
  - 写入时机：经 bridge 的 `thread/start`、`thread/resume` 成功后，以及 `thread/list` 读到带 cwd 的会话时。
  - 写入方式：原子写（临时文件 + rename）。
- **`thread/list`**
  1. 候选目录取并集：`~/.codeflicker/data.json` 的 `projects` 键、cwd 索引里出现过的目录、请求里的 `cwd`（字符串或数组）。
  2. 通过辅助进程对每个目录发 `session/list {cwd}`，跟随 `nextCursor` 翻页。
  3. 按 `sessionId` 去重，按 `updatedAt` 倒序，再按请求的 `limit` 截断。
  4. 输出 codex `Thread`（`cwd` 为真实目录，`name` 取 `title`，`modelProvider` 为 `acp`）。
  - `data.json` 缺失、不可读或结构不对时记 warn 并跳过；单个目录的 `session/list` 失败时记 warn，继续处理其他目录。
- **`thread/resume`**：参数里没有 cwd 时从 cwd 索引补上真实目录，再交给 `AcpBridge`；索引里也没有才用现有回退。
- 源码里不写 `~/.codeflicker` 以外的内部路径；`data.json` 路径作为可覆盖的构建参数（便于测试）。

## 6. alleycat：daemon 注册

| 项 | 值 |
|---|---|
| agent id | `mfcli` |
| 显示名 / title | `MyFlicker` |
| aliases | `myflicker`、`codeflicker` |
| description | `Kuaishou MyFlicker coding agent (mfcli acp).` |
| manifest | `wire: Jsonl`、`is_beta: true`、`sort_order: 9`（shell 改为 10）、`supports_ssh_bridge: false`，其余能力位同 Grok（false） |
| 配置 | `[agents.mfcli] enabled = true`、`bin = "mfcli"` |
| 可用性 | `enabled` 且 `program_available(bin)` |
| push | 加入 `BRIDGE_PUSH_AGENTS` |

要改的位置和 Grok 当初一致：workspace `Cargo.toml`（member 和 workspace dep）、`crates/alleycat/Cargo.toml`、`agents.rs`（`AgentKind::Mfcli`、构建与插入、`list_agents` 可用性、`agent_id`、`agent_kind_from_str` / `agent_kind_str`、`is_enabled`、`mfcli_available`）、`config.rs`（`MfcliAgentConfig`、`enabled_by_name`、默认值测试）、`agent_manifest.rs`、`push/mod.rs`。`cli/agents.rs` 和 onboarding 本来就通用，不用改。

## 7. AgentBuddy 仓库

- **alleycat pin**：`services/kittylitter/Cargo.toml` 和 `shared/rust-bridge/Cargo.toml` 同步切到新 commit，更新 `Cargo.lock`。开发期 `services/kittylitter` 临时用本地 path 依赖，推送后改回 git rev。
- **Android（仅显示层）**
  - `ui/AnimatedSplashScreen.kt` 的 `SplashProviders` 和 `ui/discovery/DiscoveryChooser.kt` 的 `AgentBuddyAgents` 加 MyFlicker。
  - 图标用字母徽标回退；拿到官方图标后再加 `res/drawable/agent_mfcli.xml`。
  - `apps/android/docs/qa-matrix.md` 加 mfcli 行。
- **iOS**：功能自动继承。splash / chooser 装饰项和真机验证放到 iOS 轮次，并在总结里记为后续。
- **桌面端**：无代码改动（Agents 页通用）。
- **文档**：CLAUDE.md 的 alleycat fork 说明更新 pin commit 和分支；alleycat 的 `crates/acp-bridge/README.md` 修正过时内容并加上 mfcli。

## 8. 开发流程与发布顺序

1. alleycat：在 `feat/host-push-notifications`（`a5bdd83`）之上建 worktree `~/Desktop/Project/Person/Project/alleycat-mfcli`，分支 `feat/mfcli-agent`，按第 4–6 节实现（TDD）。
2. AgentBuddy：主工作区建分支 `feat/mfcli-agent`。用户未提交的 mint-dark 改动留在工作区，不进任何 commit；`qa-matrix.md` 只暂存本次那一块改动。
3. 本机主机部署：`make desktop-sidecar` → 替换 `/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy` → ad-hoc 重签（`codesign --force --sign -`，再签 bundle）→ `launchctl kickstart` 重启 daemon → `status --json` 核对。
4. Android：`make android android-install` 装到 Pixel 9，跑第 9.4 节清单。
5. 推送（每次推送前单独确认）：alleycat `feat/mfcli-agent` 推到 `AkaShark/alleycat`，开一个叠在 #1 上的 draft PR；AgentBuddy pin 改为该 commit 后提交。

## 9. 测试与验收

### 9.1 alleycat 单元测试（先写测试）

用 fake ACP agent（脚本化 stdio 进程）覆盖：

- `initialize` 发出的 `protocolVersion` 是整数 1。
- 进程被淘汰后，下一次 `turn/start` 先 `initialize`，再 `session/resume`（或 `session/load`），然后才 `session/prompt`。
- `model` / `effort` 与当前值不同时先发 `set_config_option`，映射表每一行都有断言；目标值不在选项里时跳过，turn 照常进行。
- `thread/start` 回包的 `model` 是真实 `currentValue`。
- `model/list` 的思考强度和 image 能力来自 agent；`discover_models` 只在开启时触发。
- 辅助进程：`session/prompt` 进行中时 `thread/list` 不被阻塞。
- `mfcli-bridge`：多目录合并、翻页、去重、排序、`limit`；`data.json` 缺失或损坏；单目录失败；`thread/resume` 补 cwd；索引原子写。
- daemon：配置默认值、manifest 条目、名称映射、`is_enabled`。

之后跑全 workspace `cargo test` 和 `cargo clippy --all-targets`。

### 9.2 真实 agent 一致性

`bridge-conformance` 的 ACP target 设 `ACP_BRIDGE_AGENT_BIN=mfcli`、`ACP_BRIDGE_AGENT_ARGS=acp` 跑一遍，会消耗少量模型调用。

### 9.3 主机

`/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy status --json` 里 `mfcli` 为 `available: true`，其他 agent 的可用性和部署前一致。

### 9.4 Android 端到端（Pixel 9）

1. 已配对 Mac 的 agent 选择里出现 MyFlicker（beta）。
2. 发第一条消息前，模型列表已经是万擎模型。
3. 在项目里新建任务，回复流式出现，工具调用（执行命令、编辑文件）可见。
4. 连续多轮对话正常。
5. 中途切换模型或思考强度，下一轮生效（mfcli 会话文件里 assistant 消息的 `model` 字段作证）。
6. 离开任务、强杀 App 再打开，任务在历史列表里，进入后内容回放完整。
7. 闲置超过 5 分钟后再发一轮仍然正常（进程重建路径）。
8. 终端里在 `data.json` 已有的项目中用 `mfcli` 开的会话出现在手机上，并能续接。
9. （附加）App 在后台时 turn 完成，收到完成通知。

### 9.5 AgentBuddy 仓库

`make rust-check`、`make test-android`、`make android` 通过；提交内容不含用户未提交的文件或改动块。

## 10. 风险与已知限制

- **Devin / Grok 对整数 `protocolVersion` 的兼容性无法在本机验证**（两者都没安装）。ACP 规范定义的就是整数，官方 SDK 也按整数解析，风险低；PR 描述里注明未做真机回归。
- **同一手机的主进程串行**：两个 mfcli 任务同时发 turn 时，后一个要等前一个结束。辅助进程只解决列表刷新。真正的请求多路复用（按请求 id 路由通知）是二期。
- `data.json` 只包含终端交互用过的项目；只在手机上建过任务的目录靠 cwd 索引覆盖。两者都没有记录的目录里的终端会话不会出现。
- `discover_models` 会在 `$HOME` 起一个不发 prompt 的会话；已验证 mfcli 不会落盘，未来 mfcli 行为变化时可以关掉这个开关。
- 权限自动允许：拿到配对信息的人本来就能通过 shell agent 执行命令（见 CLAUDE.md 配对安全说明），本期不引入新的暴露面，但同事使用前要知道这一点。
- 同事机器的 nvm 如果不是由登录 shell 加载，就需要在桌面端 Agents 页给 `mfcli` 填绝对路径。

## 11. 后续（二期）

- 手机端审批：`session/request_permission` 转成 Codex 的 `item/commandExecution/requestApproval` / `item/fileChange/requestApproval`，复用手机现有审批卡片；Devin / Grok 一并受益。
- `AcpClient` 请求多路复用，支持同一手机并行多个 turn。
- 暴露 mfcli 的 mode（plan 等），映射到手机的协作模式。
- iOS 装饰项（splash / chooser / 图标）与真机验证；官方图标。
