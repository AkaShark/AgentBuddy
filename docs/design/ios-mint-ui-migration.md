# iOS Mint UI 重构：功能迁移与验收记录

日期：2026-09-27。分支：`ios-ui-mint-rebuild`（基于 `main` 的 `b271dc9`）。依据：`docs/design/mobile-ui-rebuild-handoff.md` 与 `artifacts/design/agentbuddy-ui-v1/`。

本次只改 iOS。Android 未动，需按同一清单跟进。所有构建都经 Xcode MCP 编到真机（回森 iPhone 16，iOS 26.5），没有使用模拟器。

## 1. 代码结构

单文件不超过 500 行（生成的 UniFFI 绑定除外）。重构前有 40 个 Swift 文件超过 500 行，最大的 `ConversationView.swift` 有 3844 行；现在为 0 个。

| 目录 | 职责 |
|---|---|
| `DesignSystem/Tokens/` | 语义颜色（`BuddyColors`）、间距 / 圆角 / 尺寸 / 动效（`BuddyMetrics`）、字号（`BuddyTypography`） |
| `DesignSystem/Components/` | 按钮、图标按钮、状态胶囊、卡片与 chip、品牌、列表行、空态 / 横幅 / sheet |
| `App/` | `AgentBuddyApp`、`AppDelegate`（通知、启动页拆分）、`ContentView` |
| `App/HomeNavigation/` | 根导航：路由、目的页、任务 / 主机 / 会话动作，按职责拆成扩展 |
| `Views/HomeShell/` | 手机首页壳：任务 / 项目 / 主机三个标签、底栏、新建任务 sheet |
| `Views/Approvals/` | 审批协调器、审批卡、文案、会话内审批栈与顶部横幅 |
| `Views/Conversation/` | 会话：Header、Composer、Timeline、Info，按组件拆分 |

业务状态仍由 Rust `AppStore` / `AppClient` 持有。Swift 只新增了 UI 状态：审批的提交中 / 失败 / 结果（`ApprovalCoordinator`）、停止中标记、当前可见会话。

## 2. 主题与偏好

- 新增 `agentbuddy-mint-light` / `agentbuddy-mint-dark` 两套主题，并在 `theme-manifest.json` 登记（Android 资产共用此清单）。
- Mint 只在用户没有显式选过主题时成为默认。已选的主题、字体、壁纸都保留。
- 字体默认值改为系统字体，同样只影响未设置过的用户。
- 旧主题通过 `ResolvedTheme` 的回退映射得到全部语义色，所以仍可正常使用。

## 3. 功能去向

| 现有能力 | 新位置 | 说明 |
|---|---|---|
| 任务列表、固定、隐藏、删除、派生、快捷回复、停止、画中画 | 任务页卡片的「…」菜单、左右滑动、上下文菜单 | 长按不是唯一入口 |
| 任务搜索（服务端搜索，250 ms 防抖） | 任务页搜索按钮 | 结果行与任务页一致 |
| 全部任务（原来只能经 `/resume` 进入） | 任务页「全部任务」 | 过滤、排序、派生关系、分组折叠都保留；滑动操作此前在滚动视图里无效，现在可用 |
| 主机过滤 | 任务页顶部主机胶囊菜单 | 附「管理主机」入口 |
| 项目与工作目录 | 项目页、新建任务 sheet 的项目 chip | 每行显示所属主机 |
| 新建任务 | 底部「有个想法？交给搭子…」胶囊、首页 + 按钮、主机卡「在此主机开始任务」 | iPad / Mac 分栏仍在详情区打开 |
| 主机发现、扫码、SSH / 地址、重连、重启服务、重命名、移除、挂载目录 | 主机页卡片与「…」菜单 | 「扫码连接」直接打开扫码配对 |
| 设置 | 任务页右上角齿轮 | 首页可直接发现 |
| Saved Apps、实验终端 | 任务页「…」菜单（仅在有内容或开关打开时显示） | 沿用原 feature flag |
| 实时语音 | 首页底部胶囊的语音按钮；iPad / Mac 分栏保留浮动按钮 | 沿用 `realtimeVoice` 开关 |
| 模型、推理强度、权限、协作模式 | 会话 Header「…」菜单与输入框搭档 chip | 面板把「搭档」与「模型 / 推理强度」分开 |
| 会话详情（统计、用量、重命名、派生、外观） | Header「…」→「任务信息」 | |
| 通知进入任务、通知抑制 | 未改动 | `visibleConversationKey` 同时写入 `AppState` |
| iPad 分栏 | 保留 | 侧栏换成同一个首页壳的 sidebar 布局 |

## 4. 移除或替换的内容

| 旧内容 | 处理 |
|---|---|
| 首页双指缩放（1–4 级详情） | 改为设置里的「首页显示任务详情」开关，沿用 `homeZoomLevel` 存储 |
| 首页新手引导气泡、空态小猫、动画 Logo | 删除 |
| 首页示例建议（点了没有行为） | 删除 |
| 旧首页（`HomeDashboardView`、`HomeSessionsScroll` 等约 3900 行） | 删除；`SessionCanvasLine` 保留给画中画 |
| 全局审批弹层 `ApprovalPromptView` | 换成审批卡：会话内显示在输入框上方，其他页面显示顶部横幅 |
| 会话 Header 中的模型 / 推理 / 目录标签 | 改为任务标题加「搭档 · 主机」；模型设置移到「…」菜单 |

`VoiceCallView`、`InlineVoiceStatusStrip`、`InlineVoiceButton` 在代码中没有调用方，本次未改动也未删除。

## 5. 按交接文档实现的行为

**输入框**
- 空闲：无输入时发送不可用。
- 执行中：显示「停止」；有输入时发送变为「排队」。
- 停止中：显示「正在停止…」，不能重复停止；远端确认结束后才恢复。
- 断线：显示横幅并禁用发送，草稿与附件保留。全屏编辑器、回车键和 Mac 快捷键走同一条检查。
- 创建中：发送按钮显示进度，防止重复提交。
- 排队列表：每条可「干预」（发往当前任务）或移除。

**审批**
- 默认只给「拒绝 / 允许一次」。会话范围授权放在次级入口，需要二次确认。
- 状态覆盖待处理、提交中、已允许、已拒绝、提交失败（可重试）和已在别处处理。
- 多个请求显示「第 N 个，共 M 个」。关闭横幅不等于拒绝。
- 长命令最多显示 6 行，可展开，只在代码区横向滚动。复制不会触发执行。

**其他**
- 所有可点控件的命中区至少 44 pt。状态同时用图标或文字表示，不只靠颜色。
- 减少动态效果打开时，脉冲点、闪烁和滚动动画都会关闭。
- 滑动操作的底色固定取浅色主题的强调色，深色模式下白色文字仍然清楚。
- 代码高亮跟随 App 实际使用的颜色模式，而不是 SwiftUI 环境值。此前 App 外观与系统外观不同时，代码块会沿用错误配色。

## 6. 验证

- 每个分支合并后都用 Xcode MCP 做了真机构建，并安装到回森 iPhone 16。
- 截图来自 DEBUG 状态画廊，使用固定数据，不连接主机。启动参数为 `--mint-gallery=<页面>`，加 `--mint-dark` 切到深色。页面包括 `home`、`projects`、`hosts`、`newtask`、`conversation`、`approvals`、`composer`、`accessories`、`prompts`、`addhost`、`pair`、`tasks`、`info`、`models`、`settings`。
- 浅色和深色都检查过首页、会话、审批、输入框和设置。
- 大字号：用 `-UIPreferredContentSizeCategoryName` 启动参数检查了最大标准字号（XXXL）和辅助功能 L。首页、会话、输入框没有控件被挤出屏幕。标签栏、输入框按钮行、首页顶栏和会话标题栏的字号上限为 XXXL；正文仍可继续放大。审批按钮放不下时改为上下排列；审批卡最高占屏幕 45%，超出部分在卡内滚动。
- 单元测试在真机上运行，结果见第 7 节。

尚未验证：连接真实主机的端到端流程、320 pt 宽度、VoiceOver 朗读顺序、横屏与 iPad。

## 7. 已知问题与后续

- **Android**：未做，需要按本文件同步实现。
- **Mac Catalyst**：构建在本次开始前就已失败（`HomeNavigationView` 类型检查超时，在原始代码上可复现）。
- **MCP 工具询问**：iOS 仍不显示这类审批，因为 `PendingApproval` 不含表单结构。该行为与重构前相同，需要先在 Rust 暴露字段。
- **未翻译文字**：子代理状态短语（如 "is thinking"）和「全部任务」的日期分组标题仍是英文，它们在 `SessionsDerivation` 等逻辑文件里拼成英文句子。
- **组件待上收**：以下私有辅助组件可以提到 `DesignSystem/`：`DiscoveryFormControls`、`SessionsFilterChip`、`SettingsMintStyle`、`WallpaperMintChrome`、`TimelineDetailStyle`、`ModelPickerComponents`。
- **单元测试**：真机全量 237 个，224 个通过，7 个失败，6 个 UI 测试没有运行。失败的 7 个分布在 `HomeDashboardSupportTests`、`AppSnapshotRuntimeTests` 与 `SavedAppsStoreTests`。在未改动的 `main`（`b271dc9`）上用同一台手机单独运行这 7 个，失败信息完全相同，属于重构前就存在的问题。
- **测试签名**：`AgentBuddyTests` 不再设置 `CODE_SIGNING_ALLOWED: false`，否则无法在真机上运行。

## 8. 品牌更新（2026-10-02）

- App 图标、首页品牌标记与启动页统一为「圆角双括号 + 斜向连接」；移除品牌小猫和启动页里未使用的小猫绘制代码。矢量母版为 `assets/brand/agentbuddy-mark.svg`，跨平台图标由 `tools/scripts/generate-brand-assets.cjs` 生成，包含 iOS 浅色、深色、着色图标与 Watch Icon Composer 资源。
- 启动页使用 Mint 语义色、112 pt 原生品牌 tile 与本地化名称。保留底部 9 个代理名称的纵向轮播，裁切并淡出到 3 行；系统「减少动态效果」开启时显示静态名称。启动页的显示时长与消失逻辑保持不变。
- DEBUG 画廊新增 `--mint-gallery=splash`，加 `--mint-dark` 可检查深色；`BrandLogo` 和 `LaunchView` 复用同一原生标记与启动页。
- Apple Xcode MCP 在本次环境中不可用。复用未改动的设备 Rust 静态库执行 `make -o rust-ios-device-fast ios-device-fast`，真机架构编译成功；未构建或运行 iOS 模拟器。
- 已在 iPhone 16 检查浅色、深色的中文启动页，品牌标记、名称和 3 行滚动代理名称均正常显示。减少动态效果的静态分支已做代码检查，未在真机切换辅助功能设置。
