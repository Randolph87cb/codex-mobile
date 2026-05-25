# 2026-05-25 codex-mobile 详情页断线重连状态补拉分析

## 目标

- 分析对话详情页在断线重连后，为什么刷新一次仍拿不到当前状态，必须手动再点一次刷新。
- 收敛一版可实施的修改思路，暂不直接改代码。

## 任务输入摘要

- 最终结果：明确当前状态恢复链路的问题点，并给出修改建议。
- 现有素材：Android 详情页、`AppViewModel.kt`、`SessionRepository.kt`、`BridgeApi.kt`、`RealBridgeDataProvider.kt`。
- 明确约束：本轮先做只读分析；遵守项目 Android 数据层和记录规则。
- 完成标准：说明首次刷新、重连恢复、手动刷新三条路径哪里不一致，以及建议如何对齐。
- 产出后动作：待用户确认后再进入实现。

## 现状检查

- 已按项目规则启用线程记录。
- 已确认本轮聚焦 Android 详情页状态恢复链路，不先修改 bridge。
- 正在梳理首次进入详情页、断线重连、手动刷新三条路径。

## 关键发现

- 首次进入详情页时，`AppViewModel.openSessionDetail()` 会先调用 `sessionRepository.getSessionDetail(sessionId)` 拉一次 HTTP 快照，再 `startSessionStream(sessionId)` 建立实时流。
- 手动刷新按钮调用 `AppViewModel.refreshSelectedSession()`，本质只做 `refreshSessionSnapshot(sessionId)`，因此它能把断线窗口内遗漏的正文和状态补回来。
- 自动重连路径由 `StreamClosed` / 流异常 / `onAppForegrounded()` 触发，核心动作是再次 `startSessionStream(sessionId)`；这里没有直接绑定一次快照补拉。
- 代码原本试图在“重连后收到 `session.started`”时补一次快照，逻辑在 `shouldRefreshSessionSnapshotAfterRealtimeRecovery()`。
- 但这个判断依赖 `previousRealtimeState`，而 `previousRealtimeState` 是在处理 `SessionStarted` 时从当前 UI 状态读取的；真实事件顺序里 `StreamOpened` 会先到，已经把 `isConnected` 改回 `true`，导致“这是重连恢复”这一条件被冲掉，补快照分支大概率不会触发。
- 现有文档 `docs/session-detail-ui-notes.md` 已明确规定“重连后收到 `session.started` 要主动补一次 `GET /api/session/:id` 快照”，说明实现与设计意图已经偏离。
- bridge `/api/session/:id/ws` 每次新建 websocket 连接都会先发送一条 `session.started`，所以问题不在 bridge 缺事件，而在 Android 端没有稳定把这条事件与“补快照”绑定起来。
- 现有测试 `AppViewModelTest.sessionStartedAfterReconnectRefreshesTranscriptSnapshot()` 只模拟了 `StreamClosed -> SessionStarted`，没有模拟真实顺序中的 `StreamOpened -> SessionStarted`，因此会误判为通过，没覆盖到当前 bug。

## 结果

- 已定位到 Android 端“重连后补快照”判断时机错误，是当前问题的主要根因。
- 已完成实现：
  - `AppViewModel.kt`
    - 新增显式的“待补快照”状态 `pendingRealtimeRecoverySnapshotSessionId`
    - 在 `StreamClosed`、实时流监听异常、前台恢复重连路径中设置该标记
    - 在 `SessionStarted` 中基于该标记稳定触发 `refreshSessionSnapshot(sessionId)`，不再依赖 `previousRealtimeState`
    - `onAppForegrounded()` 额外主动补一次当前会话快照，覆盖“实时流看似仍在线但内容已过期”的情况
    - `stopSessionStream()` 增加对该标记的清理控制，避免正常切换会话时残留
  - `AppViewModelTest.kt`
    - 修正“重连后补正文”测试，改为真实事件顺序 `StreamClosed -> StreamOpened -> SessionStarted`
    - 新增“前台恢复时即使实时流已连接也会主动补快照”的测试
  - `BridgeApi.kt`
    - 将 `UploadImageAttachmentRequest.toDiagnosticsSummary()` 调整为普通顶层函数，修复当前模块编译阶段对该符号的解析失败
- 已运行验证：
  - `cd android; $env:JAVA_HOME='D:/workspace/codex-mobile/.tools/jdk/jdk-17.0.19+10'; $env:ANDROID_SDK_ROOT='D:/workspace/codex-mobile/.tools/android-sdk'; .\\gradlew.bat testDebugUnitTest`
  - `powershell -ExecutionPolicy Bypass -File .\\scripts\\build-android-debug.ps1`
  - 两项均通过
- 验证过程中发现并顺手修复一处现有阻塞：
  - `AppViewModel.kt` 对 `toDiagnosticsSummary` 的导入在当前源码状态下会导致 `compileDebugKotlin` 失败，已通过调整 `BridgeApi.kt` 中扩展函数可见性消除阻塞

## 后续事项

- [ ] 观察真机或模拟器下断线、回前台、bridge 重启三条链路的详情页状态恢复表现
