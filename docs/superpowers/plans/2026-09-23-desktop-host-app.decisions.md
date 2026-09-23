# 桌面主机 App 实现决策记录

执行 `docs/superpowers/plans/2026-09-23-desktop-host-app.md` 过程中，凡是计划与实际（编译器、crate API、运行结果）不一致的地方，都按下面格式记录：做了什么决定、为什么、如果决定错了代价是什么。

格式：`Task N: Ruling: <做了什么> — <为什么> — <错了的代价>`

- Task 0: Ruling: commit 末尾署名使用 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`，不用计划里写的 Fable 5.1 — 计划写完后会话模型已切换为 Opus 5.5 — 代价：只是署名文字。
- Task 2: Ruling: `tauri icon` 生成后删掉 `icons/android`、`icons/ios` 和 Windows Store 的 `Square*Logo.png` / `StoreLogo.png`，只保留 macOS 需要的 png / icns 以及 icon.ico — 本 App 只发 macOS，这些文件只会让仓库变大 — 代价：以后做 Windows 版要重新跑一次 `npm run tauri icon`。
- Task 2: Ruling: `vite.config.ts` 顶部加 `/// <reference types="vitest/config" />` — 让 `test` 配置项在编辑器里有类型，计划里没写 — 代价：无，只是类型提示。
