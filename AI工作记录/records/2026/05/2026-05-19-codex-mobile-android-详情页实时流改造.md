# codex-mobile Android 详情页实时流改造

- 日期：2026-05-19
- 来源：Codex
- 类型：记录
- 相关目录：`android/`、`docs/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`WebSocket`、`bridge`、`实时流`

## 任务输入摘要

- 最终结果：把 Android 会话详情页从 HTTP 轮询升级为基于 bridge WebSocket 的实时流，实时显示运行状态、assistant 增量文本和结束状态。
- 现有素材：`BridgeApi.kt`、`RealBridgeDataProvider.kt`、`AppViewModel.kt`、`SessionDetailScreen.kt`、`SessionRepository.kt`、`docs/api.md`、`AGENTS.md`。
- 明确约束：只改 Android 实时流和详情页展示；优先复用 `/api/session/:id/ws`；不做 bridge token 认证；不做 bridge 审批后端实现；如果 bridge 语义受限，只记录阻塞点，不顺手大改 bridge。
- 完成标准：详情页进入后建立 WebSocket；`assistant.delta` 实时追加；`assistant.done`、`run.status`、`run.interrupted`、`error` 有清晰 UI 反馈；弱化现有 30 秒轮询；补 Android 单元测试覆盖实时状态管理核心逻辑。
- 产出后动作：执行 Android 构建与单测，更新记录，检查 Git 状态并按规则提交推送。

## 当前分析

- Android 当前只支持 HTTP：连接、列会话、查详情、创建会话、发送输入。
- `AppViewModel.sendInput()` 发送后依赖 `awaitSessionRefresh()` 做最多 30 次、每秒 1 次轮询。
- bridge `/api/session/:id/ws` 会先发 `session.started`，再按运行过程推送 `run.status`、`assistant.delta`、`assistant.done`、`run.interrupted`、`error` 等事件。
- bridge 当前 WebSocket 只接受 `store.get(sessionId)` 命中的会话；历史线程如果尚未 attach 到 store，直接订阅可能收到 `session-not-found` 并关闭。

## 预期实现方向

- 为 Android 数据层新增会话实时事件订阅接口。
- 在详情页进入时启动订阅，退出时取消订阅。
- 用 ViewModel 聚合 HTTP 快照与 WebSocket 增量，优先靠实时流推进状态，终态时做一次 HTTP 对账刷新。
- 删除 `sendInput()` 中的 30 秒轮询，必要时只保留单次 HTTP 快照对账，不保留长轮询。

## 实际改动

- 数据层：
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/SessionStreamEvent.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FakeCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FallbackCodexDataProvider.kt`
- 状态管理与 UI：
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 测试与依赖：
  - `android/app/build.gradle.kts`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`

## 结果摘要

- 详情页进入后会调用 `openSessionDetail(sessionId)`，先拉一份 HTTP 快照，再建立 `/api/session/:id/ws` 订阅。
- `assistant.delta` 会在 ViewModel 中按 turn 拼接到当前详情文本，首次 chunk 自动补 `Codex：` 前缀。
- `assistant.done`、`run.status`、`run.interrupted`、`error` 会更新独立的实时状态卡片，并在终态时执行一次 HTTP 对账刷新。
- 原来的 `awaitSessionRefresh()` 30 秒轮询已删除；当前只保留详情页进入时的 HTTP 快照和终态单次对账，不保留长轮询。

## 验证结果

- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：脚本内部把 `JAVA_HOME` 强制写成当前 worktree 下不存在的 `.tools` 路径，实际输出为 `JAVA_HOME is set to an invalid directory`，脚本自身未能完成构建。
- 已执行等价手动构建：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat assembleDebug`
  - 结果：`BUILD SUCCESSFUL`
- 已执行单元测试：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：`BUILD SUCCESSFUL`

## 仍存在的 bridge 限制

- `/api/session/:id/ws` 当前只接受 `store.get(sessionId)` 命中的会话；历史线程如果尚未 attach 到 store，Android 打开详情页可能拿到 HTTP 历史快照，但 WebSocket 直接返回 `session-not-found`。
- WebSocket 连接建立后只先发 `session.started` 元数据，不会补发当前 turn 已输出的历史 delta；如果用户在运行中途才进入详情页，只能从进入后的增量开始实时看，完整内容仍要靠 HTTP 详情对账。
- `tool.request` 目前只能提示“等待批准”，Android 端和 bridge 后端都还没有真实审批动作闭环，这次改动没有扩展该范围。
