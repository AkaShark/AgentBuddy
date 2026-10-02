# AgentBuddy Android（搭子）

<p align="center">
  <img src="app/src/main/play/listings/en-US/graphics/icon/1.png" alt="搭子 Android App 图标" width="112" height="112" />
</p>

Android 原生客户端使用 Kotlin + Jetpack Compose，与 iOS 共用 Rust 客户端核心。
应用包名为 `com.akashark.agentbuddy.android`，支持 Android 8.0（API 26）及以上版本。

## 目录与架构

| 位置 | 职责 |
|---|---|
| [app](app/) | `MainActivity`、Compose 界面、平台权限、通知和音频 |
| [state/AppModel.kt](app/src/main/java/com/akashark/agentbuddy/android/state/AppModel.kt) | 观察 Rust `AppStore` 快照与更新的薄封装 |
| [ui/designsystem](app/src/main/java/com/akashark/agentbuddy/android/ui/designsystem/) | Mint 语义颜色、排版、尺寸与通用组件 |
| [core/bridge](core/bridge/) | Android 原生库初始化、Ghostty JNI 与按 ABI 打包的动态库 |
| [codex-mobile-client](../../shared/rust-bridge/codex-mobile-client/) | 两端共享的 UniFFI 接口、状态、RPC、发现、SSH 和会话逻辑 |

会话和线程状态由 Rust `AppStore` 管理，直接服务端操作走 `AppClient`。
Android NSD 只提供发现种子，合并与探测策略属于 Rust `DiscoveryBridge`；SSH 由 Rust `SshBridge` 处理。
实时语音使用原生 WebRTC，Rust 负责信令、会话状态、转写和任务交接。

UniFFI Kotlin 绑定生成到仓库的 `shared/rust-bridge/generated/kotlin/`，由
[:app 的 sourceSets](app/build.gradle.kts) 直接编译；不在 Android 源码目录维护副本。
[UniffiInit](core/bridge/src/main/java/com/akashark/agentbuddy/android/core/bridge/UniffiInit.kt)
加载 `libcodex_mobile_client.so`，并加载旧 `libcodex_bridge.so` 完成 Android JNI 初始化。
新共享功能应放在 `codex-mobile-client`，不扩展旧 C-FFI 层。

## 开发环境

- Android Studio、JDK 17；项目使用自带的 Gradle Wrapper。
- Android SDK Platform 36、Platform Tools 和 NDK；当前 Gradle 默认 NDK 为 `30.0.14904198`。
- 通过 rustup 安装的 Rust 工具链，以及 `cargo-ndk`（`cargo install cargo-ndk`）。
- 构建 Ghostty 需要 Zig 0.15.2。

macOS 终端可按本机安装位置设置：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/30.0.14904198"
```

Android Studio / Gradle 的 SDK 路径也可写入本地 `local.properties`。如使用其他 NDK，需同时对齐
`ANDROID_NDK_HOME` 与 Gradle 的 `ANDROID_NDK_VERSION`。

## 构建与运行

以下命令在**仓库根目录**执行，按目标设备选择构建：

```bash
make android                  # arm64-v8a 真机调试构建
make android-emulator-fast    # 按宿主架构选择模拟器 ABI
```

Make 会准备 Kotlin 绑定、Rust JNI、Ghostty、Android CLI 工具、Alpine rootfs 和 proot，再执行
`:app:assembleDebug`。APK 输出到 `apps/android/app/build/outputs/apk/debug/app-debug.apk`。
当前只有一个应用配置，没有 on-device / remote-only 产品变体。

安装到已连接的真机并启动：

```bash
adb -d install -r apps/android/app/build/outputs/apk/debug/app-debug.apk
adb -d shell am start -n com.akashark.agentbuddy.android/.MainActivity
```

模拟器将 `-d` 改为 `-e`；多台设备连接时使用 `-s <serial>`。
在 macOS 打开工程：

```bash
open -a "Android Studio" apps/android
```

已有匹配的 JNI 库和生成绑定、仅修改 Kotlin 或资源时，可在 `apps/android` 目录快速迭代：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

修改公共 Rust 接口后，在仓库根目录运行 `make bindings` 重新生成两端绑定，再运行 `make android`。
单独重建 Android 原生库用 `make rust-android`；产物位于
`apps/android/core/bridge/src/main/jniLibs/<abi>/`。Gradle 优先使用 `ANDROID_ABIS`，未指定时按已有
`libcodex_mobile_client.so` 选择打包 ABI。

## 品牌与界面预览

启动图与应用图标使用统一的连接标志，底部保留代理名称轮播。系统启动界面与 Compose 启动页共用
默认 Mint 深浅色资源；新界面应使用 Mint 设计系统组件和语义 token。

DEBUG 画廊可独立预览启动页，不启动运行时或连接主机：

```bash
adb -d shell am force-stop com.akashark.agentbuddy.android
adb -d shell am start -n com.akashark.agentbuddy.android/.MainActivity \
  --es mint_gallery splash --ez mint_dark true
```

省略 `mint_dark` 可预览浅色；其他页面见 [画廊列表](app/src/main/java/com/akashark/agentbuddy/android/ui/gallery/MintGalleryPages.kt)。
回归场景见 [QA 矩阵](docs/qa-matrix.md)，发布流程见 [Android 发布自动化](docs/release-automation.md)，
全仓库约定见 [AGENTS.md](../../AGENTS.md)。
