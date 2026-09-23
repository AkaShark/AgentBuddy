# 桌面主机 App 实现决策记录

执行 `docs/superpowers/plans/2026-09-23-desktop-host-app.md` 过程中，凡是计划与实际（编译器、crate API、运行结果）不一致的地方，都按下面格式记录：做了什么决定、为什么、如果决定错了代价是什么。

格式：`Task N: Ruling: <做了什么> — <为什么> — <错了的代价>`

- Task 0: Ruling: commit 末尾署名使用 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`，不用计划里写的 Fable 5.1 — 计划写完后会话模型已切换为 Opus 5.5 — 代价：只是署名文字。
- Task 2: Ruling: `tauri icon` 生成后删掉 `icons/android`、`icons/ios` 和 Windows Store 的 `Square*Logo.png` / `StoreLogo.png`，只保留 macOS 需要的 png / icns 以及 icon.ico — 本 App 只发 macOS，这些文件只会让仓库变大 — 代价：以后做 Windows 版要重新跑一次 `npm run tauri icon`。
- Task 2: Ruling: `vite.config.ts` 顶部加 `/// <reference types="vitest/config" />` — 让 `test` 配置项在编辑器里有类型，计划里没写 — 代价：无，只是类型提示。
- Task 5: Ruling: `tests/fixtures/status.json` 和 `pair.txt` 用真实 CLI 输出生成，但把 node_id、token、token_short、用户名路径、主机名换成假值后再提交 — 仓库是公开的，真实 pair payload 就是这台 Mac 的配对凭据 — 代价：fixture 不再是逐字节真实输出，结构仍是真实的；契约测试（Task 14）会对真二进制再验一次。
- Task 5: Ruling: `StatusInfo` 增加 `version: Option<String>`（`#[serde(default)]`） — 实际 `status --json` 带有守护进程版本字段，spec §6 概览页要显示守护进程版本，计划的结构体里漏了 — 代价：老版本守护进程不带该字段时显示为空。
- Task 5: Ruling: 抓 fixture 时运行 `agentbuddy status --json` / `pair`，在本机 `~/Library/Application Support/com.akashark.agentbuddycli/` 生成了守护进程身份文件 — 这是 App 首次使用时本来就会做的事 — 代价：若不想要，删除该目录即可，下次启动会重新生成新身份（已配对手机需重扫）。
