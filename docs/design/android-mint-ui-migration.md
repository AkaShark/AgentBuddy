# Android Mint UI 重构：功能迁移与验收记录

日期：2026-09-27。分支：`android-ui-mint-rebuild`（基于 `main` 的 `b7f41ac`）。依据：`docs/design/mobile-ui-rebuild-handoff.md`、`docs/design/ios-mint-ui-migration.md` 与 `artifacts/design/agentbuddy-ui-v1/`。

本次只改 Android，另有一处共享 Rust 修复（第 6 节）。iOS 代码未动。所有构建和截图都在 Pixel 9（Android 16，arm64）真机上完成，没有使用模拟器。

## 1. 代码结构

单文件不超过 500 行（生成的 UniFFI 绑定除外）。重构前有 26 个 Kotlin 文件超过 500 行，最大的 `ConversationTimeline.kt` 有 2771 行；现在为 0 个，最大的文件 466 行。

| 目录（`apps/android/app/src/main/java/com/akashark/agentbuddy/android/`） | 职责 |
|---|---|
| `ui/designsystem/tokens/` | 间距 / 圆角 / 形状 / 尺寸（`BuddyMetrics`）、动效与减少动态（`BuddyMotion`）、字号（`BuddyTypography`） |
| `ui/designsystem/components/` | 按钮、图标按钮、状态胶囊、卡片与 chip、上下文 chip、图标方块、分区标题、列表行、空态 / 横幅 / 底部面板、品牌标 |
| `ui/AgentBuddyResolvedTheme.kt`、`ui/AgentBuddyTheme.kt` | 主题解析（Mint 语义角色与回退映射）、Compose 主题、Material 角色映射 |
| `ui/AgentBuddyApp.kt`、`ui/App*.kt` | 根布局、返回栈状态（`AppShellState`）、路由内容、根级 sheet、导航动作 |
| `ui/homeshell/` | 首页壳：底部导航、输入胶囊；`tasks/`、`projects/`、`hosts/`、`newtask/` 四个子包 |
| `ui/approvals/` | 审批协调器、文案、审批卡、结果卡、会话内审批栈、顶部横幅 |
| `ui/conversation/` | 会话：标题栏、输入框（按状态、附件、斜杠命令、排队等拆分）、时间线各类行、工具卡、任务信息 |
| `ui/gallery/` | DEBUG 状态画廊（51 个页面，固定数据） |
| `state/` | `AppModel` 按职责拆成快照、更新、会话列表、线程生命周期、水合等协作文件，公开 API 不变 |

业务状态仍由 Rust `AppStore` / `AppClient` 持有。Kotlin 只新增了 UI 状态：审批的提交中 / 失败 / 结果（`ApprovalCoordinator`）、输入框和首页卡片的「正在停止」标记、当前标签页。

## 2. 主题与偏好

- 主题解析读取 Mint JSON 的 `agentbuddy.*` 键（brand、onBrand、onAction、borderControl、focus、success / warning / error 及对应 surface、disabled / onDisabled、textBody、textSystem、codeBackground）。其他主题按 iOS `ResolvedTheme` 的同一套公式回退，所以原有主题外观不变。
- `#RRGGBBAA` 颜色像 iOS 一样丢弃末尾透明度。此前 `Color.parseColor` 把它当成 `#AARRGGBB`，约 28 个内置主题的部分颜色读错；这些主题的相关颜色现在与 iOS 一致。
- 默认主题改为 `agentbuddy-mint-light` / `agentbuddy-mint-dark`，默认字体改为系统字体。两者都只影响从未选过的用户，已选的主题、字体、字号、壁纸都保留。代码始终使用 Berkeley Mono。
- Material 3 角色映射到 Mint 语义（primary = action，secondaryContainer = brand，outline = borderControl，error = danger），系统组件（底部导航、开关、输入框、对话框、菜单）自动跟随主题。Material 字号角色不再低于 12。
- Mint 字号跟随系统字体缩放和 App 字号设置，最小 12sp。底栏、按钮行、标题栏等 chrome 用 `BuddyChromeTypeLimit` 把系统缩放限制在 1.3 倍，正文继续放大。
- 减少动态：Android 以「移除动画」（动画时长缩放为 0）为准，通过 `LocalBuddyReduceMotion` 提供给界面。

## 3. 功能去向

| 现有能力 | 旧入口 | 新位置 |
|---|---|---|
| 打开任务 | 首页卡片点击 | 任务页卡片点击（行为不变） |
| 快捷回复、固定 / 取消固定、分叉、停止本轮、删除 | 首页卡片长按菜单；右滑回复 | 任务卡片可见的「…」菜单和长按菜单；右滑回复。TalkBack 通过自定义操作可用 |
| 隐藏任务 | 首页卡片左滑 | 任务卡片左滑、「…」菜单 |
| 任务搜索（服务端搜索，250 ms 防抖，运行时筛选，下拉刷新，点击切换固定） | 首页 🔍 胶囊 | 任务页顶部搜索按钮，行为不变 |
| 全部任务（筛选、排序、按项目分组、分叉、重命名、归档） | 无入口（`SessionsScreen` 未接入） | 任务页「…」→「全部任务」；`/resume` 同样进入。行增加可见的「…」菜单 |
| 主机筛选 | 首页主机胶囊点击 | 任务页顶部「N 台主机在线」菜单，附「管理主机」 |
| 主机重连、重启服务、重命名（远程）、移除 | 主机胶囊长按 | 主机页卡片「…」菜单 |
| 编辑主机连接 | 设置 → 服务器 → 编辑 | 设置 → 服务器（保留）；主机卡片「…」→「编辑连接」 |
| 添加主机：扫码配对、已连接电脑（Slingshot）、SSH / URL | 「+ 服务器」→ 选择卡片 | 主机页「扫码连接」直接打开扫码配对；右上「+」和「其他连接方式」打开完整的添加主机面板 |
| 项目选择、新建项目 | 首页输入框的项目 chip | 项目页（当前项目卡片 + 其他项目，每行显示主机和相对时间）、新建任务面板的项目 chip |
| 新建任务（模型 chip、附件、听写、全屏编辑） | 首页「+」→ 输入框 | 任务 / 项目页底部「有个想法？交给搭子…」胶囊 → 新建任务面板；主机卡「在这台主机开始任务」 |
| 实时语音 | 首页麦克风胶囊（`realtime_voice`） | 输入胶囊的语音按钮，沿用 `realtime_voice` |
| 设置 | 首页左上齿轮 | 任务页右上角齿轮 |
| Saved Apps | 首页网格图标（有保存的 App 时）、设置 | 任务页「…」菜单（有内容时）、设置 |
| 本地终端 | 首页终端图标（`terminal` 开关） | 任务页「…」菜单（沿用开关） |
| 首页缩放 1–4 级详情 | 双指缩放、顶部按钮 | 设置 →「对话」→「首页显示任务详情」，沿用 `dashboardZoomStep`（开 = 3，关 = 2） |
| 宠物、实验功能、账号、字体、外观、壁纸 | 设置 | 设置（不变，Mint 样式）；实验功能新增「调试模式」开关（默认关，iOS 同款） |
| 会话 Header：模型 / 推理 / 目录 / 计划 / 完全访问标记、重新加载、会话信息 | 会话顶部 | 标题栏显示任务标题和「搭档 · 主机」，计划 chip 与完全访问锁保持可见；模型与推理、权限、计划模式、重新加载、任务信息放进「…」菜单 |
| 输入框：斜杠命令、@文件、附件、听写、排队 / 引导、目标、计划进度、全屏编辑、限额 / 上下文 | 会话底部 | 同一位置，换成 Mint 输入框，补齐空闲 / 执行中 / 正在停止 / 断线 / 创建中状态 |
| 审批（命令、文件、权限；拒绝 / 本会话允许 / 允许） | 全局遮罩弹层 | 当前会话：输入框上方的审批卡；其他页面：顶部横幅，可就地展开或进入该任务 |
| 用户输入请求 | 输入框上方 + 全局弹层（重复显示） | 仅输入框上方的问题卡 |
| 会话信息、壁纸、终端、实时语音、Saved App 详情、目录选择、模型面板 | 各自页面 | 保留页面，换 Mint 外观 |
| 通知进入任务、前台通知抑制 | `MainActivity` + `activeThread` 路由 | 不改 |
| 平板 / 宽屏 | 没有分栏布局 | 仍没有分栏；首页内容限制最大宽度，避免拉伸 |

## 4. 移除或替换的内容

| 旧内容 | 处理 |
|---|---|
| 首页双指缩放 | 改为设置开关，存储沿用 |
| 首页新手引导气泡（`OnboardingCoachmarks.kt`）、空态小猫（`home_cat*.webp`，约 11 MB）、动画 Logo（`AnimatedLogo.kt`） | 删除，空态改为带明确下一步的 `BuddyEmptyState` |
| 无行为的示例建议（`AmbientSuggestionsList.kt`） | 删除 |
| 旧首页（`HomeDashboardScreen.kt`、`SessionCanvasRow.kt`、`ServerPillRow.kt`、`SwipeToHideRow.kt` 等） | 删除，由任务 / 项目 / 主机三页替代；每个旧动作在删除前都核对过新位置 |
| 全局审批弹层 `ApprovalOverlay` | 换成审批卡与顶部横幅 |
| 设置里未被调用的 `AccountSection`、发现页里的死代码（`ServerRow`、`RenameServerDialog` 等） | 删除（没有任何引用） |
| 运行时「BETA」角标、全大写「CODEX」语音标签 | 改为「测试版」「搭子」 |

`VoiceCallView` 在代码中没有调用方，与 iOS 一样未改动也未删除。

## 5. 按交接文档实现的行为

**输入框**
- 空闲：无输入时发送不可用。
- 执行中（`hasActiveTurn`）：始终显示「停止」；有输入时发送变为「排队」，消息进入 Rust 的排队队列。全屏编辑器使用同样的文案。
- 停止中：显示「正在停止…」，不能重复停止。轮次结束、轮次切换（排队消息开始新一轮）或主机断开时复位；停止失败时显示原因。
- 断线：显示横幅，按钮和全屏编辑器的发送都被拦下，草稿、附件和待回答的问题卡都保留。斜杠命令（包括带参数的，如 `/rename 新标题`）照常执行；需要主机的命令（`/goal`、`/fork`、`/review`、`/rename <名称>`）失败后文字保留在输入框里。
- 创建中：发送按钮显示进度，防止重复提交。发送失败时保留草稿与附件（输入框为空时才回填，不覆盖新输入），并提供「重试」和「知道了」。页面在发送途中被重建时，草稿也会回到新的输入框。
- 排队列表：每条可「干预」（仅普通消息）或移除。
- 问题卡按请求 id 保存答案，不再串到下一个请求；最高占可见高度的 45%，问题在卡内滚动，「提交」始终可见。

**审批**
- 默认只给「拒绝 / 允许一次」。会话范围授权放在次级入口「本会话都允许…」，需要二次确认。
- 状态覆盖待处理、提交中、已允许、已拒绝、提交失败（可重试）和已在别处处理。「已在别处处理」由 Rust 队列变化推断：请求离开队列而本机没有做过决定。
- 多个请求显示「第 N 个，共 M 个」。关闭横幅只隐藏横幅，不等于拒绝。MCP 工具询问与 iOS 一样不在手机上处理。
- 横幅「去处理」进入对应任务；如果那个任务已经在屏幕上却没有审批卡（实时语音页、小游戏），或请求不属于任何任务，就在横幅里直接展开审批卡，不会离开正在进行的语音通话。会话页上的横幅排在标题栏下方，不挡返回和「…」。
- 已做出决定的审批卡保持锁定，直到请求离开队列，避免重复提交。「改为停止任务」只对命令和文件请求提供（权限请求上它等同于拒绝）。
- 长命令最多显示 6 行，可展开，只在代码区横向滚动；复制不会触发执行。审批卡最高占屏幕 45%，超出部分在卡内滚动；按钮放不下时改为上下排列。

**其他**
- 所有可点控件的命中区至少 48dp，相邻命中区不重叠；状态同时用图标或文字表示。
- 代码高亮跟随 App 实际使用的主题模式，不再固定为深色；Widget 外壳样式同样跟随。
- 滑动操作的底色固定取浅色主题的强调色，深色模式下白色文字仍然清楚。
- 减少动态开启时，脉冲点、闪烁、流式光标、启动动画、小游戏骨架和滚动动画都会关闭。
- 终端页始终为深色，系统栏图标在该页保持浅色。
- 首页卡片的「正在停止…」记住被停止的轮次，与输入框同样在轮次结束、轮次切换和主机断开时复位。
- 全部任务的状态与首页一致（等待你确认 / 等待你回复 / 正在停止），从这里打开任务后返回回到列表；分叉失败显示错误而不是闪退。
- 主机卡片「编辑连接」直接打开该主机的编辑面板（本机不显示此项）；首页「扫码连接」配对后与添加主机面板一样检查连接结果，失败会提示。
- 全屏编辑器改为在对话框内请求焦点，修复了 `main` 上也存在的偶发「FocusRequester is not initialized」崩溃。
- 时间线文字从「忽略系统字号」改为跟随系统字号和 App 字号；流式回复和完成后的回复字号一致。

## 6. 共享 Rust 修复

`shared/rust-bridge/codex-mobile-client/src/project.rs` 的 `derive_projects` 把秒级 `updated_at` 直接写进了 `last_used_at_ms`，项目页因此显示「56 年前」。现在乘以 1000，并补了回归测试。`make rust-test` 868 项全部通过。Android 已用 `make android` 重新编译 Rust 库并安装到 Pixel 9；iOS 用 `make rust-ios-device-fast` 重新编译，在回森 iPhone 16 上真机构建、安装并启动成功（那台手机上没有任务，项目页无数据可看，时间换算由单元测试覆盖）。

## 7. 验证

- 构建与测试：`make android`（含 Rust）成功；`make test-android` 全部通过（见 QA 矩阵中的新增测试）；`make rust-test` 通过。
- 截图来自 DEBUG 状态画廊，使用固定数据，不连接主机。启动方式：`adb shell am start -n com.akashark.agentbuddy.android/.MainActivity --es mint_gallery <页面> [--ez mint_dark true]`（启动前先 `am force-stop`）。页面包括 `components`、`home`、`home-detail`、`home-empty`、`home-notasks`、`home-offline`、`home-search`、`projects`、`hosts`、`newtask`、`conversation`、`conversation-long`、`conversation-header`、`composer`、`approvals`、`approval-banner`、`tasks-all`、`discovery`、`pair`、`ssh-login`、`directory-picker`、`project-picker`、`settings`、`appearance`、`themes`、`wallpaper`、`account`、`server-edit`、`info`、`models`、`apps`、`voice`、`terminal-chrome` 等 50 个。画廊从不启动运行时，也不写入用户的主题设置。
- 51 个页面都截了浅色和深色；首页、会话、审批、输入框另截了系统字号 2.0 倍（浅色和深色）。截图只用于本次验证，交付后已删除；需要时用上面的画廊命令重新生成。大字号下底栏、按钮行、标题栏不出屏，审批按钮改为上下排列。
- 真机运行真实 App：首页（1 台主机在线、无任务的空态）、项目页、主机页（「This Device」显示为「本机」）。
- 独立审查：两个只读审查代理按行为丢失、P0 行为、Rust 归属、并发和无障碍审查了全部改动，共报告 2 个重要问题（实时语音页无法处理审批；从全部任务分叉可能闪退）和 23 个次要问题。全部核实并修复（第 5 节），另有一个只读验证代理逐项复核了修复。`make test-android` 全部通过（156 项）。

尚未验证：连接真实远程主机的端到端流程（手机上只有本机主机）、真实审批和流式输出、相机扫码、TalkBack 实际朗读顺序、320dp 宽度、横屏与平板、弹出键盘时的面板布局。

## 8. 已知问题与后续

- **审批 id 跨主机不唯一**：审批 id 是 JSON-RPC 请求 id，协调器和 Rust `respondToApproval` 都只按 id 区分。两台主机同时有 id 相同的请求时可能串线，需要先在 Rust 按（主机，id）区分。
- **外观 →「从相册选择」**：没有打开过任务时会失败，否则只作用于最近打开的任务（壁纸按任务 / 主机分域，重构前就是这样）。
- **编辑连接里的「This Device」**：服务器编辑的名称输入框显示原始存储值，为避免保存时覆盖共享哨兵，没有翻译。
- **Widget 主题**：切换主题后，已经打开的 Widget 保持旧样式，重新创建后才更新。
- **Saved Apps 左滑删除**：与重构前一样立即删除；iOS 会先确认。
- **桌面小组件**（Glance `ActiveTurnWidget`）：不在本次范围，字号仍有 10–11sp。
- **画廊会话页**：从 adb 启动时，可选中的 Markdown 文本会抢焦点，页面停在中部。正常使用中文本只有被触摸时才获得焦点。
- **大字号下的长命令**：展开的长命令在卡片标题区换成较窄的一列，可读但较高。
- **推理区**：与重构前一样始终展开；代码复制仍通过文本选择，没有新增复制按钮。
- **重建后的输入框**：页面在发送途中被重建时只恢复草稿，不恢复失败提示和发送中状态。
- **从全部任务分叉**：自动跳到新任务可能早于压栈，此时返回回到首页（与重构前相同）。用 `/resume` 再次打开当前任务时，返回栈里会多一层同一个会话。
- **小游戏与审批同屏**：矮屏、横屏或分屏时，标题栏、最高 45% 的审批卡和占 40% 的小游戏加起来可能超过屏幕，审批卡底部会被小游戏盖住（小游戏默认关闭）。
- **任务信息的连接状态**：主机无响应时任务信息写「无响应」，标题栏写「连接中」，颜色一致但文字不同。
- **AppModel 可见性**：拆分时部分私有成员改为 `internal`（如 `_snapshot`、`snapshotLock`），UI 层目前没有使用。
- **组件待上收**：`ui/settings/SettingsChrome.kt`、`ui/discovery/DiscoveryFormControls.kt` 中的表单控件可以提到 `ui/designsystem/`。
