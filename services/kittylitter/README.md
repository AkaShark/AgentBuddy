# agentbuddycli（搭子 Mac 端）

Distribution wrapper for the [alleycat](https://github.com/AkaShark/alleycat) daemon.
Ships the daemon to npm as **`agentbuddycli`**; the installed command is **`agentbuddy`**.

把手机端「搭子」（AgentBuddy）App 连到这台 Mac 的守护进程。安装后用户跑：

```sh
npm install -g agentbuddycli   # 或 npx agentbuddycli
agentbuddy serve               # 启动守护进程
agentbuddy pair                # 打印配对二维码，用「搭子」App 扫
```

The wrapper itself is a tiny `main()` that re-exports the alleycat daemon with
AgentBuddy branding (`binary_name = "agentbuddy"`, identity `com.akashark.agentbuddycli`). All
daemon behavior lives in the alleycat crate; this crate exists so cargo-dist
sees an `agentbuddycli` package name and produces correctly-named artifacts.

## Cutting a release

1. Push the alleycat changes to `AkaShark/alleycat`.
2. Keep the `alleycat` dependency pinned to a commit. To move the pin to the fork's
   latest `main`, run `AGENTBUDDY_REFRESH_ALLEYCAT=1 ./tools/scripts/update-alleycat-main.sh --kittylitter`
   (or bump the rev by hand); without that variable the script is a no-op.
3. Bump `version` in this crate's `Cargo.toml`.
4. Tag `vX.Y.Z` on the `AkaShark/AgentBuddy` repo. The `release.yml` workflow at
   the repo root builds and publishes `agentbuddycli` to npm via trusted publishing.
