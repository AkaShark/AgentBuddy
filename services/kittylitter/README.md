# agentbuddycli（搭子 Mac 端）

Distribution wrapper for the [alleycat](https://github.com/AkaShark/alleycat) daemon.
Ships the daemon as the desktop app's sidecar; the binary is **`agentbuddy`** (crate `agentbuddycli`).

把手机端「搭子」（AgentBuddy）App 连到这台 Mac 的守护进程。桌面 App 会替用户安装和管理它；手动调用方式：

```sh
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy serve   # 由桌面 App 以 LaunchAgent 方式运行
/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr
```

The wrapper itself is a tiny `main()` that re-exports the alleycat daemon with
AgentBuddy branding (`binary_name = "agentbuddy"`, identity `com.akashark.agentbuddycli`). All
daemon behavior lives in the alleycat crate; this crate exists so the
sidecar build produces a correctly-named `agentbuddy` binary.

## How it ships

This binary is not published to npm anymore. It is built by
`apps/desktop/scripts/build-sidecar.sh` and bundled inside the desktop app
(`AgentBuddy.app/Contents/MacOS/agentbuddy`), which installs it as the
`com.akashark.agentbuddycli` LaunchAgent. Power users can still call it
directly: `/Applications/AgentBuddy.app/Contents/MacOS/agentbuddy pair --qr`.
