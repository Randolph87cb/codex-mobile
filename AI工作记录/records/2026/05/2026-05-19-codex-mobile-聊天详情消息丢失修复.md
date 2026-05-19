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

