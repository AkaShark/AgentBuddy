# 主机上报的任务完成通知（替代 30 秒静默保活）

日期：2026-09-24
状态：设计定稿，进入实现

## 1. 目标与非目标

**目标**

- 手机为某个远程主机上的 turn 订阅完成通知；主机观察这个 turn 的权威终态；主机用签名请求上报 Cloudflare Worker；Worker 给订阅设备发可见的 APNs / FCM 通知；用户点击后打开对应主机和会话，App 通过正常连接同步权威结果。
- 取消「手机进后台时注册 → Worker 每 30 秒发静默推送 → 手机醒来轮询」这条路径。
- 一个受管理员 token 保护、默认关闭的调试接口，可以按设备 token 单次发送 alert 或 background 推送，并配一个本地脚本。

**非目标（本期）**

- 等待审批提醒、进度推送、Live Activity 远程更新。数据模型给它们留了扩展位（事件 `type` 可扩展），但不实现。
- 手机本地（"This Device"，进程内 Codex）执行的任务。App 挂起后任务本身就停了，没有外部主机能上报，不承诺后台持续执行，也不为它发远程通知。
- 严格 exactly-once 的推送投递。外部推送服务做不到，本设计只保证「尽量不重复」。

**成功标准（按证据分级，见第 10 节）**

1. 主机观察到真实 turn 终态（主机日志 + outbox 记录）。
2. Worker 收到经过鉴权的事件（Worker 日志 / 返回体）。
3. APNs / FCM 接受推送（`accepted: true` 与 provider 状态码）。
4. 手机实际展示通知，点击后打开正确会话并同步到结果。

## 2. 背景事实（决定设计的约束）

以下结论来自源码核对，引用位置见各实现 PR：

- **主机端就是 alleycat。** `services/kittylitter/src/main.rs` 只有一个 `alleycat::App { … }.run()`。守护进程、iroh 传输、配对、agent 调度都在 `AkaShark/alleycat` fork 里（AgentBuddy 固定在 `3c6dfe2`）。改动放在该 fork 的独立分支 `feat/host-push-notifications`，提交 draft PR，主仓库再把 rev 移到这个真实存在的提交。
- **alleycat 线协议不能升版本。** 主机要求请求 `v == 1`，手机也拒绝 `v != 1` 的响应。新功能只能在 v1 下加新 op。老主机收到未知 op 时首帧反序列化失败，直接关流、不写响应，手机据此判定「不支持」。
- **身份都是 Ed25519。** 主机的 `host.key` 是 iroh `SecretKey`（32 字节 seed），`node_id` 是公钥的 64 位小写 hex，`SecretKey::sign` 给出标准 RFC 8032 签名。手机也有自己的 iroh 设备密钥（iOS Keychain / Android 加密存储，经 `set_alleycat_secret_key` 交给 Rust），主机通过 `conn.remote_id()` 拿到的就是经过认证的手机 node_id。Worker 可以用 WebCrypto 的 Ed25519 验签。
- **Codex 的 turn 事件不是广播。** app-server 只把 `turn/*`、`item/*` 投递给订阅了该 thread 的连接；订阅只能经 `thread/start`、`thread/resume`、`thread/fork` 顺带产生。连接关闭时只移除订阅，turn 继续跑。`thread/status/changed` 是全局广播。
- **`thread/read` 读不出 failed。** app-server 用 Limited 持久化，`Error` 事件不写盘，failed 的 turn 在 `thread/read` 里重建成 `completed`。`interrupted` 能正确重建。所以精确终态必须来自订阅后收到的 `turn/completed { turn.status }`。
- **旁路连接可行的模式：** UnixDaemon、UnixProxy（同一个 app-server 进程，UDS 上跑 WebSocket + JSON-RPC）、Websocket（loopback）。**Stdio 模式不支持**：每条手机流一个 app-server 子进程，手机一断 turn 就被杀，主机无从观察。
- **旁路连接的副作用：** `initialize` 时 `clientInfo.name` 不在白名单里会改写进程全局的 originator 和 UA 后缀；白名单里的 `codex_app_server_daemon` 不会。审批请求会发给所有订阅者且先到先得，watcher 永远不能应答 ServerRequest。对 notLoaded 的 thread 做冷 resume 会加载 thread、启动 MCP，必须避免；开了 Goals 的 thread 在空闲时 resume 可能启动新 turn，所以只对 active 的 thread 做 resume。
- **bridge 类 agent（claude / pi / opencode / amp / droid / hermes / devin / grok）**的出站帧都经过 `bridge-core` 的 `Session::enqueue`。手机断开后 Session 还在（idle TTL 600 秒），帧继续进 replay ring。要在 Session 实例上挂观察者（不能靠 registry 查找，claude/pi/amp 会缓存旧 Session）。
- **手机直连 workers.dev 不可靠**（发版就绪文档记录了 `NSURLErrorDomain -1005` 与「无法连接服务器」）。所以订阅经手机 → 主机（已有的加密 iroh 通道）→ Worker 这条路，手机不直接依赖 Worker 可达。
- **现有代码里的问题**会在本次顺手修掉：iOS 把 TestFlight 包判成 sandbox；Android 完成通知渠道是 `IMPORTANCE_LOW`、共用 ID、10 秒自动消失；Android `onNewToken` 的新 token 本进程用不上；冷启动点通知只等约 1.25 秒连接。

## 3. 架构

```
手机 App（前台，turn 开始时）
  └─ Rust PushManager ──iroh op "push_subscribe"（带设备签名的 grant）──▶ 主机 alleycat
                                                                     ├─ 订阅存储（0600 JSON）
                                                                     ├─ Codex watcher（旁路 app-server 连接）
                                                                     ├─ bridge 观察者（Session::enqueue tap）
                                                                     └─ outbox（持久化、退避重试）
                                                                           │ 主机签名 HTTP
                                                                           ▼
                                             Cloudflare Worker（HostChannel DO，每主机一个）
                                                ├─ 验签 / 防重放 / 限流
                                                ├─ 订阅存储、事件去重、到期清理、发送重试
                                                └─ APNs alert / FCM 通知
                                                           │
                                                           ▼
                                         手机展示通知 → 点击 → 打开 alleycat:<hostId> 的线程 → 正常同步
```

Worker 不运行 AI 任务、不轮询主机、不接收对话内容。事件里只有 ID 和终态类型。

## 4. 标识与数据模型

| 字段 | 含义 | 格式 |
|---|---|---|
| `hostId` | 主机 iroh node_id（Ed25519 公钥） | 64 位小写 hex |
| `deviceId` | 手机 iroh node_id（Ed25519 公钥） | 64 位小写 hex |
| `serverId` | 手机侧该主机的服务器 id | `alleycat:<hostId>` |
| `agent` | alleycat agent 名 | `codex`、`claude`、`pi`… |
| `threadId` / `turnId` | agent 内的 thread / turn id | 字符串，≤128 字节（Codex 为 UUIDv7） |
| `subscriptionId` | Worker 生成 | `sub_` + 32 位 hex |
| `eventId` | 主机生成，同一终态重复上报时不变 | `evt_` + 32 位 hex |

订阅的唯一键是 `(hostId, deviceId, agent, threadId, turnId)`。多设备各自订阅，互不影响；不同主机的相同 threadId 因为 `hostId` 不同天然隔离。

**订阅记录（Worker 侧）**：`subscriptionId, hostId, deviceId, platform (ios|android), pushToken, apnsEnvironment (sandbox|production，仅 iOS), agent, threadId, turnId, issuedAt, expiresAt, grantNonce, createdAt`。

**主机事件**：`eventId, hostId, agent, threadId, turnId, type (completed|failed), occurredAt`。`interrupted` 归入 `failed`，附可选 `reason: "interrupted" | "error"`，只用于选择通知文案。

## 5. 信任关系

目标：主机必须经授权才能登记订阅和上报事件；主机只能给自己名下的订阅发通知；App 里不打包任何全局秘密；知道 threadId 不等于有权限；支持撤销和防重放。

### 5.1 设备授权（grant）

手机为每个订阅生成一份 grant，用**设备 iroh 私钥**签名。待签字符串（UTF-8，`\n` 分隔，最后一行后没有换行）：

```
agentbuddy-push-grant-v1
host=<hostId>
device=<deviceId>
platform=<ios|android>
environment=<sandbox|production|none>
token_sha256=<sha256(pushToken) 小写 hex>
agent=<agent>
thread=<threadId>
turn=<turnId>
issued=<unix 秒>
expires=<unix 秒>
nonce=<32 位 hex>
```

签名是 64 字节 Ed25519，编码为 128 位小写 hex。Worker 用 `deviceId` 当公钥验签，并检查：`host` 等于请求签名方；`sha256(pushToken)` 一致；`issued` 不早于该设备的撤销时间点；`expires - issued ≤ 48h` 且未过期；`nonce` 未被用过（用过的 grant nonce 保存到过期为止）。

效果：主机拿不到设备签名就登记不了订阅，也就不能把任意 token 拿来推送；设备私钥是每台手机自己的，不是共享秘密。

### 5.2 主机请求签名

主机对 Worker 的每个请求都用 **host 私钥**签名：

```
agentbuddy-push-host-v1
<METHOD>
<path，不含 query>
<unix 秒>
<nonce，32 位 hex>
<sha256(body) 小写 hex>
```

请求头：`X-AgentBuddy-Host: <hostId>`、`X-AgentBuddy-Timestamp`、`X-AgentBuddy-Nonce`、`X-AgentBuddy-Signature: <128 位 hex>`。Worker 拒绝时间偏差超过 300 秒的请求，nonce 在主机 DO 里保存 10 分钟防重放。

### 5.3 设备撤销

手机可以不经过主机，直接用设备私钥撤销（尽力而为，网络不通时由到期兜底）：

```
agentbuddy-push-revoke-v1
host=<hostId>
device=<deviceId>
scope=all
timestamp=<unix 秒>
nonce=<32 位 hex>
```

`scope=all` 删除该设备在该主机下的全部订阅，并记录撤销时间点，此前签发的 grant 不能再用来登记。

### 5.4 其他

- **重放**：主机请求有时间窗 + nonce；grant nonce 一次性；事件按 `eventId + subscriptionId` 去重。
- **主机凭据撤销**：管理员可以把 hostId 加进 Worker 变量 `BLOCKED_HOST_IDS`（逗号分隔）立即封禁；手机可以按上面的方式撤销对某主机的全部订阅。主机换身份（重新配对）后，旧 hostId 的订阅自然失效。
- **通知里的 ID 只用于定位**：App 打开后仍然通过正常的 alleycat 连接和 token 鉴权读取数据。
- **威胁模型边界**：任何人都能生成一对密钥冒充「主机」调用 Worker，但没有设备 grant 就登记不了订阅，也就推送不到任何设备。

## 6. alleycat（主机端）

### 6.1 协议（v1 增量）

- `ListAgents` 响应新增可选字段 `host: { features: ["push.v1"], push: { enabled, agents: ["codex", …] } }`。`agents` 只列出当前运行模式下能观察终态的 agent（Codex 在 Stdio 模式下不列出）。老主机没有这个字段 → 手机判定不支持。
- 新 op `push_subscribe { v, token, agent, thread_id, turn_id, target: { platform, push_token, apns_environment? }, grant: { device_id, issued, expires, nonce, signature } }`。主机检查 `grant.device_id == 连接的 remote_id`、agent 支持推送、字段长度；然后持久化订阅，交给 watcher，并排入「登记订阅」outbox 项。响应 `push: { subscription: "pending" | "registered", terminal: null | { type, occurred_at } }`。如果订阅时 turn 已经结束，主机立即生成事件。
- 新 op `push_unsubscribe { v, token, agent?, thread_id?, turn_id?, all? }`：只作用于这个设备（按连接 remote_id）自己的订阅，排入撤销 outbox 项。
- 所有新 op 都像 `restart_agent` 一样：写一个响应帧后关流。

### 6.2 终态观察

- **Codex（UnixDaemon / UnixProxy / Websocket）**：每个 app-server 实例一条长连接。用 `clientInfo.name = "codex_app_server_daemon"`、`experimentalApi: true`，在 `optOutNotificationMethods` 里屏蔽高频 delta，然后发 `initialized`。收到订阅时：
  1. `thread/read {threadId, includeTurns: true}`：如果目标 turn 已经是终态，`interrupted` → failed；`completed` → completed，除非被动层观察到过该 thread 的 `systemError`，那样判 failed。
  2. thread 是 active 且目标 turn 还在进行 → `thread/resume {threadId}`，不带任何 override；等 `turn/completed` 且 `turn.id == turnId`；拿 `turn.status` 作终态；然后 `thread/unsubscribe`。resume 快照里出现的 `interrupted` 要再用 `thread/read` 复核一次。
  3. thread 是 notLoaded → 不做冷 resume，只按第 1 步判断；判断不了就保留订阅等到期。
  - watcher 永远不应答 ServerRequest。
  - 连接断开（app-server 重启）→ 退避重连，重连后对所有待定订阅重新走第 1、2 步。
  - 被动层：消费全局 `thread/status/changed`，记录 thread 最近的 `systemError`，用来区分 completed 和 failed，也用来发现错过的终态。
- **bridge 类 agent**：`bridge-core` 新增 `SessionObserver`，在 `Session::enqueue` 入队后回调；alleycat 的观察者只看 `method == "turn/completed"`，提取 `(agent, threadId, turn.id, turn.status)`。为了处理「先结束后订阅」的竞态，保留最近 15 分钟的终态记录，订阅时先查这份记录。
- **不支持的情况**：Codex 在 Stdio 模式、agent 不在 `host.push.agents` 里 → `push_subscribe` 返回 `ok: false, error: "push_unsupported"`。
- **不把断连当完成**：只有 `turn/completed` 事件或 `thread/read` 里确定的终态才生成事件；流断开、watcher 断连都不会。

### 6.3 outbox 与持久化

- 文件放在 alleycat state 目录，0600，原子写：`push-subscriptions.json`、`push-outbox.json`、`push-recent-terminals.json`（可选）。
- outbox 项有三种：`register`（登记订阅）、`event`、`revoke`。同一订阅的项按顺序处理，`event` 之前必须先 `register` 成功。
- 重试：网络错误、408、429、5xx → 指数退避（初始 5 秒，上限 10 分钟，加抖动），遵守 `Retry-After`；其他 4xx 是永久错误，丢弃并记日志。单项最多保留 24 小时，队列上限 1000 项（超出时淘汰最旧的）。
- 重启后加载 outbox 继续发送；订阅存储里未终结的订阅重新交给 watcher。
- 订阅在事件成功送达 Worker 后删除，或者到期删除。

### 6.4 配置与状态

- `host.toml` 新增 `[push]`：`enabled`（默认 true）、`worker_url`（默认取 `App` 提供的值，alleycat 自身默认 None；两者都没有时推送整体禁用）、`request_timeout_secs = 10`。
- `alleycat::App` 新增可选字段 `push_worker_url`。AgentBuddy 的包装 crate 填 `https://agentbuddy-push-proxy.aaksharker.workers.dev`。
- 控制 socket 新增 `push_status`（启用状态、worker 主机、订阅数、outbox 深度、最近一次成功或错误）；`status --json` 增加可选字段 `push`。

## 7. Cloudflare Worker

### 7.1 接口

所有 v2 请求体都是 JSON，上限 16 KB。

**`POST /v2/subscriptions`**（主机签名）

```json
{
  "deviceId": "<64 hex>", "platform": "ios", "pushToken": "<hex>",
  "apnsEnvironment": "production", "agent": "codex",
  "threadId": "0199…", "turnId": "0199…",
  "issuedAt": 1790300000, "expiresAt": 1790386400,
  "grantNonce": "<32 hex>", "grantSignature": "<128 hex>"
}
```
→ `201 {"subscriptionId":"sub_…","expiresAt":1790386400}`。同一唯一键重复登记时返回已有订阅（`200`）。

**`DELETE /v2/subscriptions/{subscriptionId}`**（主机签名，只能删自己的）→ `200 {"ok":true}`，幂等。

**`POST /v2/subscriptions/revoke`**（设备签名，不需要主机签名）

```json
{ "hostId": "…", "deviceId": "…", "scope": "all", "timestamp": 1790300000,
  "nonce": "<32 hex>", "signature": "<128 hex>" }
```
→ `200 {"revoked":3}`。

**`POST /v2/events`**（主机签名）

```json
{ "eventId": "evt_…", "agent": "codex", "threadId": "…", "turnId": "…",
  "type": "completed", "reason": null, "occurredAt": 1790300123 }
```
→ `202 {"eventId":"evt_…","matched":2,"results":[{"subscriptionId":"sub_…","state":"sent"}, …]}`

`state` 取值：`sent`（provider 已接受）、`retrying`（Worker 会自己重试）、`duplicate`（之前已发出）、`invalid_token`（订阅已删除）、`failed`（永久失败）。

**`GET /v2/health`** → `{"ok":true,"features":["subscriptions.v2","events.v2"]}`。

错误统一返回 `{"error":"<code>"}`，code 取值：`bad_request`、`payload_too_large`、`unauthorized`、`forbidden`、`not_found`、`rate_limited`（带 `Retry-After`）、`internal`。

### 7.2 Durable Object：`HostChannel`（每个 hostId 一个，`idFromName(hostId)`）

- 存储键：`sub:<id>`、`idx:<agent>|<threadId>|<turnId>|<deviceId>`、`grant:<nonce>`、`nonce:<nonce>`、`revoked:<deviceId>`、`evt:<eventId>|<subscriptionId>`、`retry:<eventId>|<subscriptionId>`。
- alarm 只做两件事：发送待重试的投递、清理过期记录。**不再有周期推送。**
- 投递去重：同一 `eventId + subscriptionId` 只要记录为 `sent` 就不再发送。provider 接受之后、记录落盘之前如果崩溃，可能重复发一次，这时由 APNs `apns-collapse-id` 和 Android 通知 tag 合并显示。
- 事件成功发出后删除对应订阅；invalid token 删除该 DO 里所有使用这个 token 的订阅。
- 限流：每主机每分钟 60 次订阅登记、120 次事件；设备撤销和调试接口按 IP 单独限流。

### 7.3 APNs（完成 / 失败）

- 请求头：`apns-push-type: alert`、`apns-priority: 10`、`apns-expiration: now + 86400`、`apns-collapse-id: t-<sha256(hostId|threadId|turnId) 前 32 位 hex>`、`apns-topic`（变量 `APNS_TOPIC`，默认 `com.akashark.agentbuddy`）。
- 按订阅的 `apnsEnvironment` 选 `api.push.apple.com` 或 `api.sandbox.push.apple.com`。
- 载荷：

```json
{
  "aps": { "alert": { "title": "任务已完成", "body": "点击查看结果" },
           "sound": "default", "thread-id": "<threadId>", "category": "agentbuddy.task.complete" },
  "agentbuddy.notification.serverId": "alleycat:<hostId>",
  "agentbuddy.notification.threadId": "<threadId>",
  "agentbuddy.notification.turnId": "<turnId>",
  "agentbuddy.notification.kind": "completed",
  "agentbuddy.notification.eventId": "evt_…"
}
```
  失败时文案为「任务未完成」/「任务失败或已中断，点击查看详情」。通知里不包含代码、工作目录或任务内容。
- 错误处理：`410` 或 `400 BadDeviceToken / DeviceTokenNotForTopic / Unregistered` → `invalid_token`；`403 ExpiredProviderToken / InvalidProviderToken` → 清掉 JWT 缓存并重试一次；`429`、`5xx`、网络错误 → `retrying`；其他 → `failed`。

### 7.4 FCM（完成 / 失败）

- 发送 notification + data 消息：`android.priority: HIGH`、`android.ttl: 86400s`、`android.collapse_key`，`android.notification: { channel_id: "turn_complete", tag: <与 APNs collapse-id 相同的 key>, title, body }`，`data` 使用与 APNs 相同的 `agentbuddy.notification.*` 键。
- App 在**后台**时由系统直接展示，点击后启动 MainActivity，data 键作为 intent extras 传入。App 在**前台**时进入 `onMessageReceived`：如果用户正在看这个线程就不展示，否则用同一个 tag 发本地通知。
- 错误处理：`UNREGISTERED` / `NOT_FOUND` / `INVALID_ARGUMENT`（token 相关）→ `invalid_token`；`401` 清缓存重试；`429`、`5xx` → `retrying`，遵守 `Retry-After`。保留现有 OAuth 失败不缓存等加固逻辑。

### 7.5 调试接口 `POST /debug/push`

- 只有 `DEBUG_PUSH_ENABLED == "true"` **并且**配置了至少 32 字符的 Secret `DEBUG_PUSH_ADMIN_TOKEN` 时才启用；否则一律返回 `404 {"error":"not_found"}`，不调用 APNs 或 FCM。
- `Authorization: Bearer <token>`，比较两边的 SHA-256 摘要，用 `crypto.subtle.timingSafeEqual`。错误 token 返回 `401`，不调用 provider。
- 请求体：`platform (ios|android)`、`pushToken`（iOS 为 hex，≤200；Android ≤4096）、`apnsEnvironment (sandbox|production，仅 iOS 必填)`、`mode (alert|background)`、`title`（≤64 字符）、`body`（≤200 字符，alert 必填）、可选 `hostId`（64 hex）/`threadId`/`turnId`（≤128），用于点击路由测试。不接受 URL、topic、凭据或原始 provider payload。
- 每次请求只发一次，不创建任何注册或 alarm。全局每分钟 20 次限流。
- 返回：`{"requestId":"dbg_…","provider":"apns|fcm","accepted":true,"providerStatus":200,"error":null}`。`accepted` 只表示平台接受，不表示手机已收到。
- 日志只记录 requestId、平台、provider 状态码和 token 的 SHA-256 前 8 位；不记录完整 token、Authorization、正文或私钥。
- 关闭：删除变量或设为 `false` 并重新部署，或者 `wrangler secret delete DEBUG_PUSH_ADMIN_TOKEN`。轮换：`openssl rand -hex 32` 生成新 token，`wrangler secret put DEBUG_PUSH_ADMIN_TOKEN`，再更新操作者本机的私有文件。

### 7.6 旧协议兼容

- 旧的 `POST /register` 和 `POST /:id/deregister` 继续保留，由变量 `LEGACY_KEEPALIVE_ENABLED` 控制（默认 `true`）。
- 设为 `false` 后：`/register` 返回 `410 {"error":"legacy_push_retired"}`（旧客户端每次进后台只注册一次，不会进入无限重试）；`/deregister` 仍返回 `200`；旧 `PushRegistration` DO 在下一次 alarm 时发现开关已关，执行 `deleteAll` 并且不再 arm alarm。

## 8. 移动端

### 8.1 共享 Rust（`codex-mobile-client/src/push/`）

- 平台只提供 token 和环境：`AppClient.set_push_registration(Option<AppPushRegistration { platform, token, apns_environment, worker_base_url }>)`。传 `None` 表示没有 token、通知权限被拒或用户退出。
- `PushManager` 负责：
  - 在 `TurnStarted` 时为满足条件的 turn 订阅：服务器是 alleycat 远程主机；主机广告了 `push.v1` 且 agent 在 `host.push.agents` 里；存在 registration。
  - App 进后台时，重试订阅之前失败的活跃 turn。
  - token 变化时，重新订阅活跃 turn，并撤销旧订阅。
  - registration 被清空、服务器断开或解除配对时撤销：优先经主机 `push_unsubscribe`，主机不可达时直接对 Worker 发设备签名的 revoke（尽力而为）。
  - 按 `(serverId, threadId, turnId)` 去重。
- 主机能力按 serverId 保存（ListAgents 响应里的 `host` 字段），不放进全局 `agent_metadata`。
- 暴露给平台：
  - `AppClient.turn_push_state(key) -> AppTurnPushState { NotApplicable, Unsupported, Pending, Subscribed, Failed }`：平台用它决定要不要发本地完成通知、要不要提示用户。
  - `AppClient.await_server_connected(server_id, timeout_ms) -> bool`：冷启动点通知时等待连接就绪。

### 8.2 iOS

- APNs 环境：读取 `embedded.mobileprovision` 里的 `aps-environment`。没有这个文件（App Store / TestFlight）→ production；有 → 以文件为准（开发包是 sandbox，Ad Hoc 是 production）。
- token 交给 Rust，不再打印完整 token。
- 删除 `registerPushProxy`、`deregisterPushProxy`、`handleBackgroundPush` 这条链路；`didReceiveRemoteNotification` 保留为空操作。
- 新增 `userNotificationCenter(_:willPresent:)`：用户正在看这个线程时返回 `[]`，否则返回 `[.banner, .list, .sound]`。
- 点击：处理冷启动，先 `await_server_connected`，再打开线程；之后照常同步，不根据推送伪造完成状态。
- 本地完成通知：只在 `turn_push_state` 不是 `Subscribed` 时才发；identifier 改为按 turn 生成（`agentbuddy.turn.<serverId>.<threadId>.<turnId>`），标题本地化。
- Live Activity 第一阶段：进后台时结束或交给回前台时 `sync`。

### 8.3 Android

- 新渠道 `turn_complete`（`IMPORTANCE_HIGH`，名称「任务完成通知」），启动时创建。旧的 `turn_status` 渠道保留给已有逻辑，不再使用。
- `onNewToken` 立即交给 Rust（通过 `AppModel`），不再只写 prefs。
- `onMessageReceived` 处理 `agentbuddy.notification.kind` 为 `completed` / `failed` 的消息（只有前台会走到这里）：正在看这个线程就跳过，否则用同一个 tag 发通知。删除 `turn_keepalive` 的 `runBlocking` 刷新。
- 删除 `PushProxyClient` 和 `AppLifecycleController` 里的注册逻辑。
- 点击：MainActivity 读取 `agentbuddy.notification.serverId` / `threadId` extras，先等待连接再打开线程。
- 权限被拒：不注册 token 订阅（registration 传 None），聊天和任务流程不受影响。

## 9. 迁移与发布顺序

1. **Worker 先行**：部署 v2 接口、调试接口（关闭状态）、`HostChannel` DO（wrangler migration `v3`，`new_sqlite_classes = ["HostChannel"]`）。旧接口保持开启。对现有用户没有影响。
2. **主机**：alleycat 分支合入后，桌面 App 的 sidecar 带上 `push.v1`。
3. **App**：新版 App 只走 v2，不再调用旧 `/register`。连到不支持 `push.v1` 的老主机时，明确显示「该主机版本不支持完成通知，升级桌面 App 后可用」，**不退回静默保活**。
4. **验证通过后停旧链路**：设 `LEGACY_KEEPALIVE_ENABLED=false` 并部署；旧 DO 在下一次 alarm 时自清理（TTL 最长 6 小时）。
5. **最后删旧类**：确认没有旧注册之后，用 wrangler migration `v4` `deleted_classes = ["PushRegistration"]`。

**回滚**：`wrangler rollback` 到上一版本；或者把 `LEGACY_KEEPALIVE_ENABLED` 改回 `true`。第 4 步之前旧 App 一直可用。不自动开通任何付费套餐，不自动发布 Release 或提交商店审核。

## 10. 测试与验收

- **Worker**：未授权的事件或调试请求被拒；跨主机访问订阅被拒；输入校验、请求体大小、限流；completed / failed 的 APNs 和 FCM 载荷；相同事件不重复派发；发送失败正确重试；invalid token 清理；调试接口一次请求只发一次；到期和旧注册清理。
- **主机**：手机断线后仍能观察到终态；快速 turn 的订阅竞态；重启后恢复待发送事件；暂时断网后重试；断连不被误报成完成；签名向量与 Worker 测试共用。
- **移动端**：前台、后台、冷启动的点击路由；权限拒绝；token 变更；多主机相同 threadId 的隔离；远程通知与本地通知去重；iOS 和 Android 行为一致。
- **真机验收**必须分别记录第 1 节的四级证据。调试接口成功不能代替正式事件链路的验收；模拟器结果不能写成真机结果；验证不了的部分如实记录。

## 11. Secrets 与变量

| 名称 | 类型 | 用途 |
|---|---|---|
| `APNS_TEAM_ID`、`APNS_KEY_ID`、`APNS_PRIVATE_KEY` | Secret（已有） | APNs JWT |
| `FCM_PROJECT_ID`、`FCM_CLIENT_EMAIL`、`FCM_PRIVATE_KEY` | Secret（已有） | FCM OAuth |
| `DEBUG_PUSH_ADMIN_TOKEN` | Secret（新增，可选） | 调试接口管理员 token |
| `APNS_TOPIC` | 变量 | 默认 `com.akashark.agentbuddy` |
| `DEBUG_PUSH_ENABLED` | 变量 | 默认 `false` |
| `LEGACY_KEEPALIVE_ENABLED` | 变量 | 默认 `true`，停旧链路时改为 `false` |
| `BLOCKED_HOST_IDS` | 变量 | 逗号分隔的封禁 hostId，默认空 |

## 12. 签名测试向量

主机（alleycat Rust）、Worker（TypeScript）、手机（Rust）三处实现都必须用下面这组向量写单元测试。Ed25519 是确定性签名，三边应当算出完全相同的结果。字符串里的 `\n` 表示换行，末尾没有换行。

- 主机 seed：`0101010101010101010101010101010101010101010101010101010101010101` → hostId `8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c`
- 设备 seed：`0202020202020202020202020202020202020202020202020202020202020202` → deviceId `8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394`

**主机请求**（`POST /v2/events`，timestamp `1790300000`，nonce `00112233445566778899aabbccddeeff`）

- body：`{"eventId":"evt_0123456789abcdef0123456789abcdef","agent":"codex","threadId":"thread-1","turnId":"turn-1","type":"completed","reason":null,"occurredAt":1790300000}`
- sha256(body)：`b0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3`
- 待签字符串：`agentbuddy-push-host-v1\nPOST\n/v2/events\n1790300000\n00112233445566778899aabbccddeeff\nb0afc6ab15f7ae40138b3efcecfd209cb704f96abcb25f14807ed800b72732b3`
- 签名：`d5ce9808b8bc3c1a19fefa8da077fd42b53180b2e9eb1e7d4672beb0cefeb169af22c08cc88a46ac9d57df17ca8b21a359390598ea96bd9d1f6fd1aad9077f03`

**设备 grant**（pushToken `a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1`）

- 待签字符串：`agentbuddy-push-grant-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nplatform=ios\nenvironment=production\ntoken_sha256=3490f80401886d12f3861ec190f5c16419b26345497a92bc4530e22f5d8b4295\nagent=codex\nthread=thread-1\nturn=turn-1\nissued=1790300000\nexpires=1790386400\nnonce=ffeeddccbbaa99887766554433221100`
- 签名：`c0bb8f6643304b84ad574ee0797e698f53a6b3eb9abcb4a08d98f5bc691530f705e36f138f28dcf2f8df271c59372d475d8d17171ec03dda91b0e62cd721200e`

**设备撤销**

- 待签字符串：`agentbuddy-push-revoke-v1\nhost=8a88e3dd7409f195fd52db2d3cba5d72ca6709bf1d94121bf3748801b40f6f5c\ndevice=8139770ea87d175f56a35466c34c7ecccb8d8a91b4ee37a25df60f5b8fc9b394\nscope=all\ntimestamp=1790300000\nnonce=0f0e0d0c0b0a09080706050403020100`
- 签名：`8a989a6ca6e9d3cd37bdbfd3ff3b4aa94482e27c0613a03d51a27e51a13f072e1ae39c460e1651601846b43d4a4c6d1cc6923fddaac36ad776892e598b12bf03`
