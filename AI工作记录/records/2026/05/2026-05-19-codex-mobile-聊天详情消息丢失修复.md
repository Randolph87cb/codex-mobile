# codex-mobile-聊天详情消息丢失修复

- 日期：2026-05-19
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 目标
- 修复 Android 端会话详情在切换会话配置选项后聊天消息消失的问题。
- 修复会话详情历史消息显示不完整、看不到前面消息的问题。

## 当前判断
- Android `mergeSessionDetail` 会用 bridge 返回的简化快照覆盖本地已累积的实时消息。
- bridge `buildThreadTranscriptPreview` 仅保留最后 12 段、最多 4000 字符，导致详情页天然截断历史。

## 计划
- 调整 Android 详情合并逻辑，优先保留更完整的 transcript。
- 调整 bridge 线程详情快照，返回完整可展示 transcript。
- 为 Android 和 bridge 分别补测试并执行验证。

## 实际修改
- Android `AppViewModel.kt`
  - 为 `mergeSessionDetail` 增加 transcript 完整度比较，避免切换模型、推理强度、权限等配置后，被 bridge 返回的简化快照覆盖掉已经看到的聊天内容。
- Android `AppViewModelTest.kt`
  - 新增“切换会话配置不应丢失更完整 transcript”的单测。
- bridge `session-view.ts`
  - `buildThreadTranscriptPreview` 改为返回完整线程 transcript，不再裁剪为“最后 12 段 / 4000 字符”。
- bridge `session-view.test.ts`
  - 新增历史前段消息仍保留的测试。

## 日志补充判断
- 用户补充的 Android 日志显示，`2026-05-19T04:25:38Z` 该会话已收到 `session.started` 且 `status=idle`，说明 bridge 视角下当时并没有继续运行。
- `2026-05-19T04:25:46Z` 先出现 `停止实时流监听`，随后才出现 `Socket closed`，再往后有 `用户主动断开桥接连接`，更像是客户端主动结束订阅或切换页面后的正常收尾，而不是服务端卡死。

## 验证结果
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复
- 用户继续反馈：某些场景下会话实际已经空闲，但手机端仍显示 `running`，导致发送被错误拦截。
- Android `AppViewModel.kt`
  - 在实时流 `StreamClosed` 后，如果当前仍是 `running/awaiting_approval` 且没有活动审批，会立刻走一次 HTTP 快照纠正状态。
  - 在用户点发送前，如果本地仍是忙状态但实时流未连接，会先向 bridge 做一次状态同步；若实际已空闲，则直接发送，不再误判为排队或阻塞。
  - `refreshSessionSnapshot` 现在会同步更新 `sessionRealtimeState.statusText`，避免只改 `selectedSession.status` 而顶部状态条不刷新。
- Android `AppViewModelTest.kt`
  - 新增 `streamClosedRefreshesBusySessionSnapshotToIdle`
  - 新增 `sendInputRefreshesStaleRunningStatusBeforeSubmitting`

## 本次验证补充
- Android 验证需串行执行；并行跑 `testDebugUnitTest` 与 `build-android-debug.ps1` 会争用同一 `app/build` 目录，引发 Kotlin 增量编译缓存锁冲突。
- 已串行执行：
  - `cd android && .\gradlew.bat --stop && .\gradlew.bat testDebugUnitTest`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复（二）
- 用户继续反馈：手机端消息类型显示不完整，缺少 Codex app 中常见的操作消息，例如运行命令、编辑文件、工具调用进度等，因此难以判断线程是否仍在执行。
- 当前判断：
  - bridge 实时通知只转发了 `assistant.delta`、`turn.started/completed` 等少量事件，没有把 `item/started`、`item/completed`、文件修改进度、工具调用进度、推理摘要等操作项转给 Android。
  - bridge 历史 transcript 只识别 `userMessage` 和 `agentMessage`，会直接丢弃 `commandExecution`、`fileChange`、`mcpToolCall`、`reasoning` 等线程项。
- 实际修改：
  - bridge `session-view.ts`
    - 新增 `formatThreadItemAsTranscriptBlock`，把 `commandExecution`、`fileChange`、`mcpToolCall`、`dynamicToolCall`、`collabAgentToolCall`、`reasoning` 等线程项格式化为 `系统：...` transcript 块。
    - 历史详情页现在会保留这些操作项，而不只显示用户/助手自然语言消息。
  - bridge `app-server-runner.ts`
    - 新增 bridge WebSocket 事件类型 `activity`。
    - 转发 `item/started`、`item/completed`、`item/fileChange/patchUpdated`、`item/mcpToolCall/progress`、`item/reasoning/summaryTextDelta`，让 Android 能实时看到执行中的操作消息。
  - Android `SessionStreamEvent.kt`
    - 新增 `Activity` 事件类型。
  - Android `RealBridgeDataProvider.kt`
    - 解析 bridge 返回的 `activity` 事件。
  - Android `AppViewModel.kt`
    - 收到 `activity` 后，将 bridge 已格式化的 `transcriptBlock` 追加到当前会话 transcript。
    - 同步更新顶部 `lastEventText`，让用户能看到“命令执行”“文件修改进度”等实时摘要。
  - 测试：
    - bridge `session-view.test.ts` 新增操作消息历史展示测试。
    - bridge `app-server-runner.test.ts` 新增 `activity` 事件转发测试。
    - Android `AppViewModelTest.kt` 新增 `activity` 事件追加 transcript 和更新状态摘要测试。

## 本次验证补充（二）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 模拟器联调补充
- 用户要求补做 Android 模拟器真实联调，而不是只看单测和构建。
- 联调环境：
  - Android Emulator：`emulator-5554`
  - bridge 地址：`http://10.0.2.2:8787`
  - 安装方式：`scripts/install-android-debug-emulator.ps1`
- 联调过程中先暴露出一个真实回归：
  - 连接成功后，App 会自动打开会话列表第一条详情。
  - 某些历史线程详情在 bridge `/api/session/:id` 上返回 `500`，错误为 `value?.trim is not a function`。
  - 根因是 `bridge/src/session-view.ts` 的 `normalizeText` 假定字段一定是字符串；历史线程中存在非字符串字段时会在 `trim()` 处崩溃。
- 追加修复：
  - bridge `session-view.ts`
    - `normalizeText` 改为只对字符串执行 `trim()`，对数字/布尔值做安全字符串化，其它类型直接回退，不再因为历史脏数据打爆详情页。
  - bridge `session-view.test.ts`
    - 新增“历史线程项字段不是字符串时也不应崩溃”的测试。
- 模拟器联调结果：
  - 修复后，连接页可以正常进入会话列表。
  - 在 `codex-mobile` 目录下新建草稿线程并发送首条消息后，详情页能实时展示 `推理摘要`、`命令执行`、`文件修改`、`assistant.delta`，最后状态能回到 `idle`。
  - 这说明“操作消息补全”在真实模拟器链路上已经生效，不只是单元测试通过。
- 本次模拟器联调的局限：
  - 由于 `adb input text` 会把空格输入成 `%20` 文本，测试提示词被截断，未完整走到用户原本想要的文件内容写入路径。
  - 但这不影响验证目标：操作消息、状态流转和历史详情崩溃修复都已在模拟器上得到确认。

## 本次验证补充（三）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- Android 模拟器真实联调：通过
  - 连接 bridge 成功
  - 会话列表和历史详情可打开
  - 详情页可实时看到 `reasoning` / `commandExecution` / `fileChange`
  - 最终状态由 `running` 回到 `idle`

## 后续补充修复（三）
- 用户继续反馈：现在除了文字回复外，其他消息类型应默认折叠，交互上要和 Codex app 对齐。
- 当前判断：
  - Android 详情页已经能显示 `命令执行`、`推理摘要`、`审批结果` 等结构化消息，但默认全部展开，长操作块会占满屏幕。
  - 现有测试只覆盖 transcript 解析，没有直接验证“默认折叠、点击展开”的 Compose 行为。
- 实际修改：
  - Android `TranscriptBubble.kt`
    - 为 transcript bubble 增加 `prefersExpandedByDefault` 和 `summaryLine` 规则。
    - 用户消息、助手普通文字消息默认展开；系统/工具/状态类消息默认折叠。
    - `系统：...` 与首段兜底系统块现在会把第一行拆成标题，后续内容作为可折叠详情。
  - Android `SessionDetailScreen.kt`
    - 在 transcript 列表中为非文字消息增加折叠头，默认只显示标签、摘要和展开/收起箭头。
    - 点击后再显示正文和代码块。
  - Android `TestTags.kt`
    - 为 transcript 折叠头补充稳定 test tag，便于 UI 自动化测试定位。
  - Android `TranscriptBubbleTest.kt`
    - 新增系统操作块标题拆分与默认折叠规则单测。
  - Android `SessionDetailReplayTest.kt`
    - 调整回放断言，验证结构化工具结果标题仍可见，详情默认隐藏。
  - Android `SessionDetailTranscriptCollapseTest.kt`
    - 新增模拟器仪表测试，直接验证“非文字消息默认折叠，点击后展开详情”。

## 本次验证补充（四）
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - 回放链路测试通过
  - 组件级折叠展开仪表测试通过
- 额外说明：
  - 中途尝试用旧历史会话做手动抓图时，抓到过一次旧进程残留页面；强制停进程后重新进入，确认需要以新进程和仪表测试结果为准。

## 后续补充修复（四）
- 用户继续反馈：会话列表里的标题和路径信息过多，要求标题最多显示两行，并且默认把最近回复的会话排在前面。
- 当前判断：
  - Android `SessionListScreen.kt` 当前按工作目录名字母排序目录分组，因此即使某个目录下有最新会话，也可能被排到较后位置。
  - 会话卡片标题和副标题都没有做行数限制，长提示词和长路径会把列表项高度撑得很大。
- 实际修改：
  - Android `SessionListScreen.kt`
    - `groupSessionsByDirectory` 改为先保持组内会话按 `lastUpdated` 倒序，再按“每个目录组里最新会话的 `lastUpdated`”整体倒序排列目录组。
    - 会话标题限制为最多两行，超出使用省略号。
    - 会话副标题限制为单行，且当目录组头已经展示同一 `cwd` 时，列表项副标题不再重复附带目录路径。
    - 时间行限制为单行，避免极端情况下继续把卡片撑高。
  - Android `SessionListGroupingTest.kt`
    - 新增目录组按最新会话时间倒序的单测。
    - 新增列表副标题去掉重复目录路径的单测。

## 本次验证补充（五）
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

