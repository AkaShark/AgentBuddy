# AgentBuddy 发版收尾记录（2026-09-24）

当前仍未完成发布验收；所有 GitHub Release 保持草稿。

## 账号与发布配置

- Developer ID Application 已签发，验证证书 Team 与本地私钥匹配，导出加密 p12。密钥只在本机私有目录和 GitHub Secrets，不进入仓库。
- ASC App Manager API key 已建立并通过 notarytool 验证。
- App Store Connect 已建立 **AgentBuddy 搭子**，Apple ID `6815571588`，Bundle ID `com.akashark.agentbuddy`。
- iOS 主 App、Live Activity、Watch、Watch Complications 四个标识已注册并关联 `group.com.akashark.agentbuddy`；前三个启用 Push。
- APNs Key 已注册并下载：AgentBuddy Push，Sandbox & Production、Team Scoped，Key ID `686X55267F`。私钥已在本机私有目录校验并设置 0600，未进入 Git。
- Firebase 项目 `agentbuddy-45403` 已建立（Spark 免费方案），Android 应用 `com.akashark.agentbuddy.android` 已注册；配置已下载、核对并写入忽略的本地文件和 GOOGLE_SERVICES_JSON_B64。Admin SDK 私钥已由用户下载，核对项目并通过 OpenSSL 私钥校验；已保存到本机私有目录（目录 0700、文件 0600），未进入 Git。
- GitHub 已核对七个 Apple Secret 名：MAC_DEVELOPER_ID_CERT_P12_B64、MAC_DEVELOPER_ID_CERT_PASSWORD、ASC_KEY_ID、ASC_ISSUER_ID、ASC_PRIVATE_KEY_P8_B64、IOS_TEAM_ID、IOS_APP_STORE_APP_ID。

## 桌面发布 CI

运行：https://github.com/AkaShark/AgentBuddy/actions/runs/35973836528

- 首次运行基于 `9f8d928`。最新修复版本 `3dc92ff` 已再次触发构建：https://github.com/AkaShark/AgentBuddy/actions/runs/35979742072 ，仍保持草稿。该运行现已结束，两个架构构建均为 cancelled，未完成签名包验收。
- 两个架构的 sidecar 编译已成功。
- 草稿 `desktop-dev-1` 已创建，未发布。
- 两份 AgentBuddy.zip 已提交 Apple 公证，检查时均为 In Progress。CI 尚未结束。
- 尚未确认两个 DMG 同在草稿，尚未执行 spctl/stapler 验收，尚未安装到 /Applications 做 LaunchAgent 与手机 QA。

## 已提交的小问题修复

| 原任务项 | 提交 | 结果 |
|---|---|---|
| 9.1 修复按钮重复重启 | 9f8d928 | install 已包含重启，前端去掉第二次调用 |
| 9.2 登录 shell stdout 阻塞 | 9d9b95b | 并发读取 stdout，保留超时 |
| 9.3 配对 token 指纹 | 8ec52e1 | 使用 status.token_short |
| 9.4 互斥测试 | a36020f | 注入 runner 驱动实际序列，验证安装/重启不被轮换打断 |
| 9.5 内联 TOML | 8068a12 | 支持 inline agent/agents 表 |
| 9.6 Cmd+Q 首次提示 | 73e052e | 自定义应用菜单，主窗口附着提示；确认后才记录已提示 |
| 9.7 停止时轮询 | 98211e1 | 可见窗口停止时 5 秒；隐藏与失败退避仍 30 秒 |
| 9.8 升级失败重试 | 7e8ab34 | 失败不保存新版本，后续启动重试 |
| 9.9 缺少设置与诊断 | bf03606 | 手动 bin、Codex host/port、登录项提示、诊断守护进程版本 |
| 9.10 文档失效条目 | c6a24dc | 校正 alpine-fs 与 ffi/shared.rs |
| 9.11 alleycat 刷新 | 9cfddc3 | 先更新所选 manifest rev 再 cargo update；默认仍不刷新 |

代码修复均经历失败回归与通过验证；互斥测试用移除锁的变异版本确认能检测失去串行化。

## 验证

- `npm ci` 成功；存在 2 个 moderate audit 提示，未执行强制升级。
- `cd apps/desktop && npm test && npx tsc --noEmit`：最新 25 个 Vitest + 2 个 Node 测试成功；TypeScript 成功。
- `cargo test --lib --manifest-path apps/desktop/src-tauri/Cargo.toml`：最新 52 项成功。
- `make desktop-sidecar` 与 sidecar_contract（2 项）基线成功；测试 daemon 已停止。
- shell 输出测试全套运行中曾出现一次 2 秒超时；单独与后续全套重跑通过，原因未确认，保留为测试稳定性风险。
- `python3 -m unittest discover -s tools/scripts/tests -p test_update_alleycat.py`：1 项成功；`bash -n tools/scripts/update-alleycat-main.sh` 成功；默认 no-op 已验证。
- `cd apps/android && ./gradlew :app:testDebugUnitTest -Plitter.enableGhosttyAndroid=false`：成功，41 项任务 up-to-date，使用 Android Studio JBR 与本地 SDK。
- `make ios-sim-fast` 未通过：首次 Zig 解包缓存报 FileNotFound；隔离缓存重试后进程被 SIGTERM 终止（Error 143），并非编译成功。日志在 `/tmp/agentbuddy-ios-sim-fast.log` 和 `/tmp/agentbuddy-ios-sim-fast-retry.log`。
- 现有 React act 警告未导致失败。新设置仍需桌面视觉/交互 QA。

## 缺失 workflow secrets

- Android：ANDROID_UPLOAD_KEYSTORE_B64、LITTER_UPLOAD_STORE_PASSWORD、LITTER_UPLOAD_KEY_ALIAS、LITTER_UPLOAD_KEY_PASSWORD、LITTER_PLAY_SERVICE_ACCOUNT_JSON_B64。
- iOS 商店签名：IOS_DIST_CERT_P12_B64、IOS_DIST_CERT_PASSWORD、IOS_APP_STORE_PROFILE_B64、IOS_LIVE_ACTIVITY_APP_STORE_PROFILE_B64、IOS_WATCH_APP_STORE_PROFILE_B64、IOS_WATCH_COMPLICATIONS_APP_STORE_PROFILE_B64。
- 旧 Mac 发布通道：MAC_APP_STORE_PROFILE_B64、MAC_DIST_CERT_P12_B64、MAC_DIST_CERT_PASSWORD、MAC_DEVELOPER_ID_PROFILE_B64。
- 构建缓存：SCCACHE_R2_ACCESS_KEY_ID、SCCACHE_R2_ENDPOINT、SCCACHE_R2_SECRET_ACCESS_KEY。
- GITHUB_TOKEN 由 Actions 提供，不需要手动创建。

## 尚未完成

1. 等待 Apple 公证和 CI，验收两个 DMG；包含最新修复后重建，再完成 Mac/手机 QA。
2. Cmd+Q 已通过 computer use 实际 RED/GREEN 验证：旧包直接退出，新包显示提示，确认后退出，再次退出不重复提示。调试包构建通过；尚需正式签名包 QA。
3. 9.12 原文被截断；当前两个指定文件无 `clich`，需要确认实际意图。build-rust.sh 中已不存在的 uniffi_shared.rs 输入已在 `3dc92ff` 移除，bash -n 通过；未更改实际递归源码哈希逻辑。
4. APNs、Firebase、Cloudflare 授权已完成，六项 APNs/FCM 凭据已上传为 Worker secrets，未进入 Git。
5. Cloudflare Worker 已部署至 `https://agentbuddy-push-proxy.aaksharker.workers.dev`，版本 `6a28792f-5051-47a8-b820-1433f9087703`；iOS/Android 推送 URL 同步切换。两平台假 token 注册均返回有效 ID，注销返回 JSON 并清理测试注册；尚未做真机 APNs/FCM 送达验收。
6. Firebase Android 配置已完成并通过 `:app:processDebugGoogleServices :app:testDebugUnitTest -Plitter.enableGhosttyAndroid=false`（42 项任务，13 执行、29 缓存）。Play 签名及服务账号仍待准备。
7. Android rootfs 仓库选择、品牌素材目录、正式隐私和支持页面 URL 等待用户回复；未替换美术或商店链接。
8. 工作区原有/并行产生的 Ghostty 脚本与 patches 改动和两个脏子模块保留，未纳入本次提交，也未推子模块。

- 推送注销原先返回纯文本 `ok`，Android 会按 JSON 解析失败；`e6bc9ea` 修复为 JSON 并透传响应头。新增回归测试先复现失败后通过；Worker `npm test`、`tsc --noEmit` 均通过。Android URL 切换后单元测试通过（42 项任务，5 执行、37 缓存）。初始公开域名 TLS 连接失败，域名生效后在线注册/注销验收通过。

## iPhone 推送实测准备

- 已连接 iPhone 15 Pro Max，确认调试包使用 AgentBuddy 新 Worker 地址。
- 首次启动复现 APNs 注册失败：应用缺少 `aps-environment`。在 `project.yml` 补齐推送 entitlement 后重新生成工程，设备构建成功，签名包含 `aps-environment=development`。
- 修复包已安装并启动，设备回调成功取得 32 字节 APNs token（不在文档中记录 token）。真机后台接收与业务刷新仍待实测；token 注册成功不代表推送送达。

- 用户选择模拟测试：Mac 使用真机 token 注册 30 秒间隔、150 秒 TTL 的 sandbox 推送。Worker 记录三次 `APNs sandbox → 200 OK`；iPhone 在 `applicationState=background` 收到静默推送，执行 runtime handler 并完成 `newData`。测试注册已主动注销。
- 完整业务流程仍未验收：手机进入后台时，自行调用 Worker `/register` 报“无法连接服务器”；Mac 代注册仅验证了服务端到 APNs 到真机后台接收，未证明手机到 Worker 的网络路径，也未验证有运行任务时的状态刷新。后续应检查手机网络对 workers.dev 的可达性，必要时配置可访问的自有域名。

- 再测手机直连：使用仅本地临时 Debug 入口调用实际 `PushProxyClient.register`，90 秒 TTL、30 秒间隔；手机返回 `NSURLErrorDomain -1005`（网络连接已中断），未取得注册 ID，不能判定直连通过。临时入口测试后已从源码移除，正常包重新构建成功。未再次用 Mac 代注册来掩盖该失败。
