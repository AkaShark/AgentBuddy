# 2026-10-07 App Review 整改

## 审核事实

- Submission：`6d9703a8-27f7-4c56-9c36-78633ceea90c`。
- 实际送审包：iOS `1.5.0 (202609251324)`；2026-10-02 提交，2026-10-07 拒绝。
- 审核设备：iPad Air 11-inch (M3)。
- 三项问题：2.5.4 后台音频；4.3(a) 与其他开发者应用的 binary、metadata 和／或 concept 相似；5 中国大陆 OpenAI 相关功能与资料。
- Apple 未提供二进制相似度百分比，也未点名对比应用。不能把拒审解释为某个可通过混淆或改符号规避的检测阈值。
- 在线英文介绍已经比仓库 fastlane 文案更新；已保存线上中英文介绍，移除供应商品牌举例及 OpenAI/Anthropic 引用；本地文案同步更新。

审核原页：https://appstoreconnect.apple.com/apps/6815571588/distribution/reviewsubmissions/details/6d9703a8-27f7-4c56-9c36-78633ceea90c

## 4.3(a)：用可体验的差异回应

AgentBuddy 基于 Baozi/litter 开源代码发展，必须保留许可证、版权声明并诚实说明来源。开源许可允许复用，并不等于 App Store 必须接受功能相同的 fork。不能声称整个代码库完全原创。

先构建当前源码，再与送审包逐项对照；Git 中的修改日期不证明某个功能已进入归档。9 月 25 日的构建早于 10 月 2 日的品牌更新，必须更换构建才可能展示后续修改。

| 体验 | 当前源码证据 | 重新提交前的真机证据 |
| --- | --- | --- |
| 任务按需决策、运行中、最近会话分区 | `Views/HomeShell/Tasks/TasksHomeView.swift` | 展示真实运行任务、待审批任务和已完成任务；操作一项审批 |
| 项目关联主机、目录与最近任务 | `Views/HomeShell/Projects/ProjectsHomeView.swift` | 从项目恢复已有任务，再在同一目录创建任务 |
| 多主机与运行时选择 | `Views/HomeShell/Hosts/`、`HomeNavigationView+HomeShell.swift` | 展示实际可连接主机和已配置的运行时，不能只展示不可用选项 |
| 主机推送完成通知并返回会话 | `Models/AppLifecycleController+BackgroundTurns.swift`、通知路由 | 退到主屏幕，等待真实主机任务完成，点通知恢复结果 |
| 新品牌资产 | `assets/brand/agentbuddy-mark.svg` | 当前归档中的 iOS/Watch 图标、启动页和商店截图一致 |

这些是候选差异证据，不是“已证明独有”的结论。需与继承的上游体验对照，并向审核员提供完整可用的访问路径。单独换颜色、图标、包名、类名或增加无用代码都不能证明差异化。

按用户要求撤回 B 字标。已核对 project.yml：iOS 主应用使用 Assets.xcassets/AppIcon.appiconset，并排除 AppIcon.icon；Watch 使用 AppIcon.icon。以当前 iOS 薄荷绿连接标记重新生成全部平台资源，结果与 HEAD 一致。商店图标仍来自旧包，需更换上传构建才能更新。

## 2.5.4：后台音频

已删除文字画中画入口、控制器、渲染器及 `SilentAudioKeeper` 循环静音。真实 WebRTC 语音和 `UIBackgroundModes.audio` 保留。录音启动失败会释放输入 tap、引擎及音频会话，重复开始录音会被忽略。后台有声音的真机录屏仍待补充。

本轮采用保留真实语音、清理文字 PiP 的路径。原方案比较如下：

1. 保留真实后台语音：清理静音保活及依赖它的文字 PiP 路径；保持仅用户主动语音会话使用音频；用实体设备录屏证明退到 Home Screen 后仍有可听见的双向音频，并证明结束通话后释放音频会话。提供可复现的测试主机、语音账户和入口步骤。录屏需包含声音，不可拿静音视频或模拟器替代。
2. 仅保留前台语音：移除 `UIBackgroundModes.audio`，同步处理退后台的语音停止/恢复、CarPlay 和文字 PiP，避免只删 plist 导致产品挂起后状态错误。

在选定并完成路径前，不应在审核回复中宣称 2.5.4 已解决。

## 中国大陆发行

Apple 明确给出两条路径：取消中国大陆 storefront，或实际停用大陆版本相关功能并移除对应商店引用。仅删除 OpenAI 名称不能解决功能本身的问题。中文本地化和中国大陆发行是不同概念。

用户决定保留中国大陆发行，本次仅删除介绍中的品牌引用。不改变实际 AI 功能，不宣称已停用相关功能或已解决第 5 条。

## 提交前必须完成

- [ ] 选择并完成后台音频整改，附真实可听见音频的录屏（若保留后台语音）。
- [x] 用户决定保留中国大陆发行；未修改地区配置。
- [ ] 当前源码真机编译、安装与端到端验证；iPad 特别检查分栏、任务审批和语音入口。
- [ ] 生成新归档并使用唯一递增 build number；核对归档版本、提交 SHA 和图标。
- [ ] 上传并等待处理，替换 `202609251324`，确认 App Store 版本关联的是新包。
- [ ] 更新商店截图为当前真机界面；DEBUG gallery 可以内部检视，不能冒充真实可用产品流程。
- 审核环境：用户明确要求不处理，保留原审核备注，不创建主机或账户。
- [ ] 审核回复只写已完成并验证的内容，附逐步复现路径和证据；不要反复提交仍存在相同问题的包。

## 回复草稿（完成证据后再补全发送）

Thank you for the review of submission 6d9703a8-27f7-4c56-9c36-78633ceea90c.

Regarding Guideline 4.3(a), AgentBuddy is an independent project built on open-source foundations. We acknowledge that a branding change alone would not address your concern. We would like to demonstrate its task-oriented workflow: decisions requiring attention, active tasks, project-scoped history, and host management. The existing review notes describe the host setup and navigation. A replacement build is still required before claiming these changes are available for review.

Before sending, replace this paragraph with the verified build number, the specific differences established against the inherited application. Add the completed 2.5.4 remedy and the saved China mainland availability decision. Do not send this draft with placeholders or planned changes described as completed.

参考：https://developer.apple.com/app-store/review/guidelines/#spam

## 验证状态

恢复原图标、移除文字 PiP 和静音保活、修复录音失败清理后，`make ios-device-fast` 成功（真机 SDK，未使用模拟器）。未归档、上传或重新提交审核。真实后台语音录屏尚未完成。

基线差异见 `app-review-change-evidence.txt`，未发送的审核回复草稿见 `app-review-response-draft.txt`。用户明确选择暂不限制中国大陆功能；不声称第 5 条已解决。
