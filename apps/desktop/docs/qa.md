# 桌面主机 App 手工 QA

每次发版前在一台干净的 Mac（或新建用户）上按顺序执行，全部通过才算过。

1. 安装 dmg，拖进 Applications，首次打开：菜单栏出现图标，无 Dock 图标，Gatekeeper 无警告（签名公证版）。
2. 打开控制台 → 概览显示引导卡片 → 点「安装后台服务」→ 状态变为「运行中」，
   `launchctl print gui/$(id -u)/com.akashark.agentbuddycli` 有输出，且 `ps` 里只有一个 `agentbuddy serve`。
3. Agents 页：用 Homebrew / npm / nvm 装的 agent（如 claude、codex）显示「可用」——验证守护进程拿到了登录 shell 的 PATH。
4. 配对页出现二维码；手机 App「添加服务器 → 扫码」连上并能列出 agent。
5. 退出 App（首次弹提示）→ 手机仍能新建会话。
6. 重启 Mac，不打开 App → 手机仍能连接。
7. 把 App 移到 ~/Desktop 再打开 → 概览出现「服务指向旧版本或旧位置」→ 点修复 → 状态恢复「运行中」。
8. 配对页「轮换 token」→ 旧手机被拒绝，重新扫码后恢复。
9. Agents 页关闭一个 agent → 手机端该 agent 消失；重新开启后恢复；`host.toml` 权限仍是 `-rw-------`。
10. 日志页「跟随」打开后能看到新日志实时滚动。
11. 托盘「停止主机服务」→ 确认 → 状态「已停止」，手机连接失败；「启动主机服务」恢复。
12. 托盘取消勾选「开机自启」→ 确认 → LaunchAgent 被移除；再勾选 → 重新安装并运行。
