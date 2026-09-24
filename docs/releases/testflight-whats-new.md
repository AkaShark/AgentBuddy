Summary

- Turn completion notifications now come from your paired Mac: when a task finishes or fails on an AgentBuddy host, you get a notification even if the app is closed. Tapping it opens that conversation. The old background keep-alive is gone.
- Hosts on an older desktop version show a hint that completion notifications need a desktop update.
- SSH connections now verify host keys on every connect path and ask before trusting a changed key.
- Added a file/dir mount picker for local iPhone runtime mounts: press and hold the server pill to mount.
- Added Real Time voice API-key fallback when OAuth realtime auth is unavailable.
- Fixed active-turn composer text entry so Send is available while a turn is running.
- Fixed a CarPlay voice crash when reopening or resuming an active voice session.
- Improved Real Time voice error reporting for unexpected session closes.
- Fixed OpenCode/Pi model catalog loading through Alleycat.
- Fixed Pi/alleycat remote project browsing when directory-picker commands were rejected.

What to test

- Completion notifications: pair with a Mac running the updated desktop app, start a task, switch away from AgentBuddy, and confirm a 任务已完成 notification arrives; tap it and confirm the right conversation opens with the full result. While viewing that conversation, confirm no banner appears.
- Older host: connect to a host without the update and confirm the hint appears instead of notifications.
- SSH host keys: reconnect to an SSH host whose key changed and confirm the confirmation dialog appears and Trust New Key reconnects.
- Local iPhone mounts: connect to the local iPhone runtime, press and hold the server pill, pick a file or directory, and confirm it mounts.
- Real Time auth fallback: configure OAuth and an API key, start voice, and confirm fallback auth can connect.
- Active-turn composer: type while a turn is running, confirm Send appears, then clear text and confirm Cancel returns.
- CarPlay voice: start or resume CarPlay voice and confirm Now Playing opens without crashing.
- Real Time errors: confirm an unexpected session close shows a specific error.
- OpenCode/Pi models: connect to an Alleycat host, open the model picker, and confirm models load.
- Remote project picker: connect to a Pi/alleycat host, open the new-project directory picker, and confirm folders load.
