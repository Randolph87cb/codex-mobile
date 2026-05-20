# codex-mobile Create custom pet 实时流断连排查

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`android/`、`bridge/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`bridge`、`WebSocket`、`实时流`、`排障`

## 任务输入摘要

- 最终结果：定位 Android 端在 `Create custom pet` 线程中出现的“实时流错误 / Reconnecting... / os error 10053”原因，判断是在客户端、bridge 还是上游 Codex 会话流断开。
- 现有素材：用户提供的 Android 应用日志、报错截图、项目内 Android/bridge 代码、已有实时流改造记录。
- 明确约束：当前先做只读排查，不修改代码；重点围绕线程 `019e3eb2-3589-7983-a1d0-eb8f2a4bf68d` 与对应实时流链路；结论需要能解释为什么界面出现错误 toast 以及为什么日志里同时有 `Socket closed`。
- 完成标准：给出可复核的报错链路、根因判断、涉及代码位置、是否属于预期重连/误报，以及后续修复建议。
- 产出后动作：更新工作记录；如果结论指向代码缺陷，再和用户确认修复方案。

## 背景

用户在 Android 客户端打开 `Create custom pet` 线程后，发送消息并通过实时流查看回复过程中，界面弹出“实时流错误”，日志包含 `Reconnecting... 2/5`、`responseStreamDisconnected` 和 Windows `os error 10053`。需要判断真实断点在 Android WebSocket、bridge 到 codex app-server 的上游流，还是线程本身的运行状态处理。

## 关键过程

- 已确认本次排查涉及 Android `AppViewModel`、`BridgeApi`、`RealBridgeDataProvider`、`SessionRepository` 与 bridge `app.ts` 等实时流实现入口。
- 已发现项目内已有 `Android 详情页实时流改造` 记录，后续会结合其中记录的已知限制和当前代码实现一起复核。
- 已还原 Android 实时流 UI 更新链：`RealBridgeDataProvider.observeSessionEvents()` 把 WebSocket 事件转成 `SessionStreamEvent`，`AppViewModel.handleSessionStreamEvent()` 在 `Error` 分支中无条件写入 `message = "实时流错误：..."`，最终由 `CodexMobileApp.kt` 的 snackbar 展示给用户。
- 已还原 bridge 事件来源：`bridge/src/app.ts` 的 `/api/session/:id/ws` 只负责 `runner.subscribe()` 后 `socket.send(JSON.stringify(event))` 原样转发；`AppServerRunner` 在收到上游 `codex app-server` 的 `error` notification 时，会把 `notification.params` 包进 `data.error` 并下发给当前订阅者。
- 已结合用户日志确认：`2026-05-20T00:52:31Z` Android 收到的是带 `willRetry=true` 的 `responseStreamDisconnected` 错误，`2026-05-20T00:52:35Z` 紧接着 HTTP 详情仍显示该线程“进行中”，说明这不是会话真正失败，而是上游响应流断开后进入重试。
- 已确认 `2026-05-20T00:52:40Z` 的 `java.net.SocketException: Socket closed` 出现在 `AppViewModel` 主动停止实时流监听、`RealBridgeDataProvider.awaitClose { webSocket.cancel() }` 之后，属于本地主动取消连接时的关闭噪声，不是这次根因。

## 结果

- 根因判断：
  - 这次报错的真实断点最可能在 `codex app-server -> 上游模型响应 SSE 流`，对应上游错误类型 `responseStreamDisconnected`，并非 Android 到 bridge 的 WebSocket 自身断线。
  - 上游在响应流断开后进入重试，因此错误正文里出现 `Reconnecting... 2/5`、`willRetry=true` 和 Windows `os error 10053`；bridge 当前会把这类“仍会重试”的错误直接作为会话 `error` 事件转发给 Android。
  - Android 当前把所有 `SessionStreamEvent.Error` 一律视为用户可见错误，直接写入 snackbar 文案和详情页 `error` 状态，造成用户看到“实时流错误”，即使同一时间该线程其实仍在继续运行。
- 关键证据：
  - 用户日志中 `2026-05-20T00:52:31.218933Z` 收到 `responseStreamDisconnected`，并带 `willRetry=true`。
  - 同一线程在 `2026-05-20T00:52:35.316848Z` 通过 HTTP 刷新后仍返回“`gpt-5.4 • 进行中`”，与真正终态错误不一致。
  - `2026-05-20T00:52:40.727770Z` 先出现“停止实时流监听 / 结束实时流订阅”，随后才出现 `Socket closed`，符合本地主动取消而非上游失败。
- 涉及代码位置：
  - Android：`android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`、`android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`、`android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - bridge：`bridge/src/app.ts`、`bridge/src/app-server-runner.ts`、`bridge/src/app-server-client.ts`

## 可复用经验

- 排查“实时流错误”时，先看错误事件里是否带 `willRetry=true`、`responseStreamDisconnected`、`threadId/turnId` 等上游字段；若有，优先怀疑上游响应流抖动，而不是 Android WebSocket 本身。
- 区分两类异常很重要：
  - 上游可恢复错误：应更多作为状态提示或诊断信息，而不是直接标成终态失败。
  - 本地主动停流产生的 `Socket closed`：应视为取消噪声，避免误报给用户。

## Skill 观察

- 是否出现新 skill 候选：暂无。
- 是否应该优化已有 skill：暂无，本次更像 Android/bridge 实时流错误分层问题。
- 触发条件或典型用户说法：`实时流错误为什么会弹出来`、`Reconnecting... 2/5 是哪里断了`、`Socket closed 是不是桥挂了`。

## 后续事项

- [x] 结合代码与日志还原完整断链路径。
- [x] 判断这次异常是否属于上游连接重试而非 Android 主 WebSocket 故障。
- [x] 形成根因结论与修复建议。
- [ ] 如果用户确认修复，再把“可重试错误”和“本地主动取消噪声”从用户可见终态错误里拆开处理。
