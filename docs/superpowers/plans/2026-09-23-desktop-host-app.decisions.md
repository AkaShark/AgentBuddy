# 桌面主机 App 实现决策记录

执行 `docs/superpowers/plans/2026-09-23-desktop-host-app.md` 过程中，凡是计划与实际（编译器、crate API、运行结果）不一致的地方，都按下面格式记录：做了什么决定、为什么、如果决定错了代价是什么。

格式：`Task N: Ruling: <做了什么> — <为什么> — <错了的代价>`

- Task 0: Ruling: commit 末尾署名使用 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`，不用计划里写的 Fable 5.1 — 计划写完后会话模型已切换为 Opus 5.5 — 代价：只是署名文字。
- Task 2: Ruling: `tauri icon` 生成后删掉 `icons/android`、`icons/ios` 和 Windows Store 的 `Square*Logo.png` / `StoreLogo.png`，只保留 macOS 需要的 png / icns 以及 icon.ico — 本 App 只发 macOS，这些文件只会让仓库变大 — 代价：以后做 Windows 版要重新跑一次 `npm run tauri icon`。
- Task 2: Ruling: `vite.config.ts` 顶部加 `/// <reference types="vitest/config" />` — 让 `test` 配置项在编辑器里有类型，计划里没写 — 代价：无，只是类型提示。
- Task 5: Ruling: `tests/fixtures/status.json` 和 `pair.txt` 用真实 CLI 输出生成，但把 node_id、token、token_short、用户名路径、主机名换成假值后再提交 — 仓库是公开的，真实 pair payload 就是这台 Mac 的配对凭据 — 代价：fixture 不再是逐字节真实输出，结构仍是真实的；契约测试（Task 14）会对真二进制再验一次。
- Task 5: Ruling: `StatusInfo` 增加 `version: Option<String>`（`#[serde(default)]`） — 实际 `status --json` 带有守护进程版本字段，spec §6 概览页要显示守护进程版本，计划的结构体里漏了 — 代价：老版本守护进程不带该字段时显示为空。
- Task 5: Ruling: 抓 fixture 时运行 `agentbuddy status --json` / `pair`，在本机 `~/Library/Application Support/com.akashark.agentbuddycli/` 生成了守护进程身份文件 — 这是 App 首次使用时本来就会做的事 — 代价：若不想要，删除该目录即可，下次启动会重新生成新身份（已配对手机需重扫）。
- Task 7: Ruling: `write_atomic` 在 rename 前把临时文件权限设成原文件的权限（新文件默认 0600）— 真实 host.toml 是 0600 且含配对 token，计划的"写临时文件再 rename"会变成 0644，其他本机用户可读；已加测试 `write_atomic_keeps_owner_only_permissions`（去掉修复时 0644≠0600 失败）— 代价：无。
- Task 7: Ruling: shell agent 的可执行文件键是 `shell_bin`，`set_agent_bin("shell", …)` 写 `shell_bin` 而不是 `bin` — 真实 host.toml 里 `[agents.shell]` 没有 `bin` 键 — 代价：以后 alleycat 改键名需要同步这里。
- Task 7: Ruling: 新增 `read_agent_settings(text) -> BTreeMap<String, AgentSettings{enabled, bin}>`，Task 8 暴露为命令 `agent_settings`，Task 12 的 Agents 页用它显示真实启用状态 — 计划里 Agents 页把 `available`（找到可执行文件）当作启用状态，这两者不是一回事，spec §6 要的是启用开关 — 代价：多一个只读命令。
- Task 7: Ruling: fixture `host.toml` 按真实文件结构（含 `[session]`、shell 的 `shell_bin`）编写，token 用假值 — 贴近真实格式才能测出"保留其余内容" — 代价：无。
- Task 9: Ruling: 托盘里所有确认/提示对话框改用非阻塞的 `show(|ok| …)` 回调，不用计划里的 `blocking_show()` — tauri-plugin-dialog 2.7.3 源码注释明确写着 blocking 版本不能在主线程调用，而托盘菜单事件就在 macOS 主线程上，会卡死 — 代价：无，行为一致。
- Task 9: Ruling: 把菜单决策抽成纯函数 `start_stop_action` / `autostart_action`，返回 `TrayAction::{Run, Confirm}`，并加了单测 — 让"何时要确认"可测试，也让回调式对话框能简单接入 — 代价：无。
- Task 9: Ruling: 未安装时点「启动主机服务」执行 `install`（计划是 `restart`）— 没有 LaunchAgent 时 `restart` 会失败；`install` 本身就会启动服务 — 代价：无。
- Task 9: Ruling: 取消确认框时重新刷新一次托盘 — 勾选型菜单项在点击时已经自动切换了勾选状态，取消后要恢复成真实状态 — 代价：无。
- Task 9: Ruling: 托盘图标先用 App 图标（彩色，非 template），计划写的是 `icon_as_template(true)` — 彩色方形图标当 template 会渲染成一块纯色方块；等新品牌美术出一张单色菜单栏图标后再改成 template — 代价：暗色/浅色菜单栏下图标不会自动反色。
- Task 9: Ruling: 操作失败时弹一个错误对话框（显示 `HostError.detail`）— 计划里托盘动作失败是静默的，spec §7 要求错误可见 — 代价：无。
- Task 9: Ruling: 冒烟验证只确认了 `tauri dev` 能启动、无 panic、启动任务成功调用了 sidecar 并写出 settings.json；菜单栏截图因为菜单栏图标过多没能直接看到托盘图标，留到 Task 12 后用 System Events 再核对 — 代价：若图标没有显示，要到那一步才发现。
- Task 10: Ruling: 修复计划 `useHostState.run()` 的一个 bug——计划在 `finally` 里调用 `refresh()`，而成功的刷新会 `setError(null)`，导致操作失败的错误一闪就消失；现在先记下操作错误，刷新后再设置回去。测试 `keeps a failed action's error visible after the follow-up refresh`（修复前失败）— 代价：无。
- Task 10: Ruling: 新增 `src/lib/useHostState.test.tsx`，覆盖上面的 bug 和"连续 3 次失败标记为无法连接" — 计划的 Task 10 只测了纯函数，hook 的行为（spec §7 的退避与横幅）没有测试 — 代价：无。
- Task 10: Ruling: `host.agentSettings()` 与 `AgentSettings` 类型、`StatusInfo.version?` — 对应 Task 7/5 的裁决 — 代价：无。
- Task 10: Ruling: Vitest 的 `include` 限定为 `src/**/*.test.{ts,tsx}`，`npm test` 改为 `vitest run && node --test scripts/sync-version.test.mjs` — 否则 Vitest 会把 node:test 写的脚本测试当成空套件报失败，而 node 测试也要进 CI — 代价：无。
- Task 11: Ruling: 概览页加一行「守护进程版本」（来自 `status.version`）— spec §6 要求同时显示守护进程版本与 App 版本，计划的概览只显示了 App 版本 — 代价：无。
- Task 11: Ruling: 复制节点 id 用 clipboard 插件的 `writeText`，不用计划里的 `navigator.clipboard` — capability 只开了 `clipboard-manager:allow-write-text`，WKWebView 里 `navigator.clipboard` 在非安全上下文可能不可用 — 代价：无。
- Task 11: Ruling: 「在 Finder 中显示」包进 `run()` — 计划直接 `void host.revealPath()`，目录不存在时错误会被吞掉；包进 `run` 后错误进顶部横幅 — 代价：点完会多一次状态刷新。
- Task 11: Ruling: 配对页多加一条测试「轮换前先确认、轮换后重新拉 payload」— 计划只测了渲染；轮换 token 会让所有手机掉线，这条路径值得钉住 — 代价：无。
- Task 12: Ruling: Agents 页的启用开关读 `agent_settings`（host.toml 的真实 `enabled`），路径显示 host.toml 里配置的可执行文件；改动后重新读取 — 对应 Task 7 裁决，计划原来用 `available` 冒充启用状态 — 代价：多一次命令调用。
- Task 12: Ruling: 真机冒烟（`tauri dev` + System Events）确认：托盘项存在（`status menu`），菜单项为 状态/打开控制台/显示配对二维码/启停/开机自启/退出，点「打开控制台」弹出 760×540 窗口，概览页暗色主题与引导卡片正常渲染 — 补上 Task 9 未完成的核对 — 代价：无。
- Task 12: Ruling: 冒烟时发现本机有一个不受 launchd 管理的守护进程（pid 70196），是 Task 5 抓 fixture 时运行 `agentbuddy pair` 自动拉起的——alleycat 的 `pair` 会在没有守护进程时 detached 启动一个。这是一个真实状态（未安装但在运行），见下一个 commit 的处理 — 代价：无。
- Task 12b: Ruling: 所有 sidecar 调用带上用户登录 shell 的 PATH 和 SHELL（新模块 `shellenv`：`$SHELL -ilc` 打印分隔符包裹的 env 并解析，5 秒超时，再并上 Homebrew / ~/.local/bin / ~/.cargo/bin 等兜底目录）— 读 alleycat 源码发现 `install` 会把调用者的 PATH、SHELL 写进 LaunchAgent plist，`pair` 拉起的守护进程也继承它们；从 Finder 启动的 App 只有 `/usr/bin:/bin:/usr/sbin:/sbin`，守护进程会找不到 codex、claude 等 agent，违反 spec 成功标准 2。用 `env -i` 模拟 launchd 环境验证：恢复出 27 个 PATH 条目并找到 claude — 代价：首次调用多约几百毫秒；用户 rc 若会卡住会在 5 秒后退回兜底 PATH。
- Task 12b: Ruling: 「安装后台服务」改为依次执行 `install` 和 `restart`（同一把锁内，`install_sequence()`，托盘与控制台共用）— alleycat 的 `install` 只 bootstrap LaunchAgent，若已有 `pair` 拉起的非托管守护进程持有 host.lock，launchd 启动的 `serve` 会因"another daemon is already running"反复退出；`restart` 会先停掉现有守护进程，再让 launchd 接管 — 代价：安装多花几秒。
- Task 14: Ruling: 契约测试里 `run()` 断言子进程退出码为 0，并额外断言 `status.version` 存在 — 计划版本忽略退出码，守护进程报错时会变成难懂的解析失败；version 字段是 Task 5 裁决新增的依赖 — 代价：无。
