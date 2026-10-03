# Physical-device history regression

`mobile-history-fixture.py` supplies synthetic app-server history to the real
mobile Rust library and generated Swift/Kotlin bindings. It never accesses a
real task or executes an agent. Each WebSocket gets independent histories.
The fixture deliberately rejects non-full pagination and any rollback after
a modern revert failure. It is complementary to live-host/UI acceptance.

Run the fixture with Python `websockets`, placing downloaded dependencies and
logs outside the repository. Bind to loopback for Android (`adb -s <Pixel-9>
reverse tcp:18765 tcp:18765`); bind to the Mac's reachable interface for iPhone.
Stop it and remove the temporary reverse mapping when finished.

Android: build the current arm64 Rust library and generated Kotlin bindings,
then `:app:assembleDebug :app:assembleDebugAndroidTest`. Install both APKs on
Pixel 9 and run the instrumentation class `MobileHistoryDeviceTest` with
`-e historyFixtureUrl ws://127.0.0.1:18765`.

iOS: create `DeviceRegression.local.json` with `{"url":"ws://<Mac-IP>:18765"}`
in the external work directory, and symlink it into
`apps/ios/Tests/AgentBuddyTests/`. Regenerate the Xcode project using the
repository script. Build the current device Rust library and run
`MobileHistoryDeviceTests/testSleepPaginationForkEditAndFailedRevert` through
Apple's Xcode MCP on **回森 iPhone 16**. If MCP is unavailable, the repository
allows `xcodebuild test` with the physical device UDID (obtain `hardwareProperties.udid`
from `devicectl device info details`; its CoreDevice UUID is a different identifier).
Place DerivedData, cloned packages and the xcresult under `~/.agentBuddy`.
No simulator is used. Remove the
symlink and regenerate the project after this run; the local endpoint must
not enter source control.

Both tests are opt-in and skip without their fixture configuration. Their
acceptance assertions cover full cursor pagination, Sleep hydration, unique
message identities, a boundary fork that preserves the source, edited draft
restoration, reverting the first message to empty history, and a failed
modern revert that must not fall back to a second mutation.
