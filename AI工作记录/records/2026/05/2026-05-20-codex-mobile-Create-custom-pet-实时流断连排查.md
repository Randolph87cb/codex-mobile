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
- 已完成修复实现：
  - `android/app/src/main/java/com/openai/codexmobile/data/SessionStreamEvent.kt`
    - 为 `SessionStreamEvent.Error` 增加 `isRetryable` 字段，用于区分“可恢复错误”和“终态错误”。
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
    - 为 WebSocket 订阅增加 `closedByClient` 标记。
    - `awaitClose { webSocket.cancel() }` 后如果走到 `onFailure()`，不再把本地主动停流的 `Socket closed` 当作用户错误上报。
    - 解析 bridge `type=error` 事件时，不再把整个嵌套 JSON 当消息正文；会抽取真实错误消息，并识别 `willRetry=true`，映射为 `SessionStreamEvent.Error(isRetryable = true)`。
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
    - 对 `isRetryable=true` 的实时流错误，不再把会话状态打成 `error`，也不再弹 snackbar。
    - 可重试错误会在实时状态卡片里显示“上游响应流暂时中断，bridge 正在重试”，并保留提示直到收到新的正向实时事件或终态刷新。
    - 终态错误仍保留原有的用户可见错误提示。
  - 测试：
    - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
    - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
- 当前用户可见行为：
  - 上游 `Reconnecting... 2/5` 这类可重试错误会转成“正在重试”提示，不再误报成终态失败。
  - 用户离开详情页或主动停止监听时，本地 `Socket closed` 不再额外弹出实时流错误。
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

## 补充进展：待审批恢复与强制权限策略

- 新增问题：
  - 用户补充反馈：如果某个会话已经进入“待审批”，先退出会话详情页再重新进入，页面会保持“待审批”状态，但手机端无法继续审批，形成卡死。
  - 用户进一步要求：不是只改“默认值”，而是让手机端把所有会话都统一收敛为 `approvalMode=auto` 和 `sandboxMode=danger-full-access`，同时不再保留这两个设置的可编辑入口。
- 根因排查：
  - Android 端在原实现里只会在实时流收到 `tool.request` 时，把待审批请求的 `requestId / method / paramsSummary` 保存在内存中的 `sessionRealtimeState.pendingApproval`。
  - 用户离开详情页后，这部分内存状态被清空；重新进入时，HTTP `/api/session/:id` 只会返回 `status=awaiting_approval`，却不会附带对应的 pending approval 元数据，所以详情页虽然知道“在等审批”，但不知道该审批哪一条请求。
  - bridge 端其实在 `AppServerRunner` 内存里保留了 `pendingApprovals` 和 `sessionApprovalKeys`，只是之前 `/api/session/:id` 和实时流初始 `session.started` 都没有把这份上下文回传给 Android。
- 已完成修复实现：
  - bridge：
    - `bridge/src/types.ts`
      - 新增 `PendingApprovalView`，并为 `SessionView` 增加 `pendingApproval` 字段。
    - `bridge/src/session-view.ts`
      - `buildSessionViewFromRecord()`、`buildSessionViewFromThread()` 增加 `pendingApproval` 透传。
    - `bridge/src/app-server-runner.ts`
      - `listSessionViews()`、`getSessionView()` 会把当前会话的最新 pending approval 元数据带回。
      - 新增 pending approval 摘要格式化逻辑，统一输出 `requestId / method / paramsSummary`。
    - `bridge/src/app.ts`
      - `/api/session/:id/ws` 初始 `session.started` 事件会补发 `pendingApproval`，保证重新订阅实时流时也能恢复待审批上下文。
    - 测试：
      - `bridge/tests/app-server-runner.test.ts`
      - `bridge/tests/app.test.ts`
  - Android：
    - `android/app/src/main/java/com/openai/codexmobile/model/SessionDetail.kt`
      - 新增 `PendingApprovalSnapshot`，并把 `pendingApproval` 纳入 `SessionDetail`。
    - `android/app/src/main/java/com/openai/codexmobile/data/SessionStreamEvent.kt`
      - `SessionStarted` 增加 `pendingApproval` 字段，用于实时流恢复。
    - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
      - HTTP 会话详情和 `session.started` 实时事件都会解析 `pendingApproval`。
      - 为 JVM 单测补充兼容性，避免 `JSONObject` 的 Android stub 影响解析测试。
    - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
      - 重新进入详情页时，会先从 `SessionDetail.pendingApproval` 恢复待审批上下文，再根据当前会话状态自动尝试审批。
      - 收到 `tool.request` 时，待审批状态会同步写入 `selectedSession.pendingApproval` 和 `sessionRealtimeState.pendingApproval`，避免只存在于瞬时 UI 状态。
      - 对所有会话强制施加手机端托管策略：
        - `approvalMode=auto`
        - `sandboxMode=danger-full-access`
      - 连接会话列表、打开会话详情、创建会话、刷新快照时，都会同步把现有会话修正到这组托管策略。
      - 已处于 `awaiting_approval` 的旧会话，在恢复到待审批上下文后也会自动调用审批接口，避免继续卡死。
    - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
    - `android/app/src/main/java/com/openai/codexmobile/data/AppSettingsStore.kt`
      - 新建会话与持久化设置的默认审批/沙箱值同步改为 `auto` 与 `danger-full-access`。
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
      - 移除“审批模式 / 文件权限”在详情页和设置页的可编辑入口，避免 UI 与强制策略冲突。
    - 测试：
      - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
      - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
- 当前用户可见行为：
  - 手机端会把所有会话统一视为自动审批和完全权限，不再提供手工切换入口。
  - 如果某个会话在离开详情页前已经进入待审批，重新进入时不会再停留在“看得到待审批、但无法审批”的僵死状态；待审批上下文会恢复，并由手机端自动批准。
  - 已经在等待审批的旧会话也会在重新进入后自动尝试放行，不只影响新会话。
- 后续补充修正：
  - 用户在同步 `main` 后补充反馈：手机上仍能看到旧的“权限 工作区可写”配置项，且某些会话即使已经出现一条助手回复，状态仍会停留在“待审批”，之后没有继续流式更新。
  - 已确认“权限”项残留不是当前 `main` 代码的问题，而是手机上安装的仍是旧 debug 包；当前代码里的详情配置区只保留目录、模型、推理、速度四项。
  - 进一步定位到一个真实状态机缺口：自动批准成功后，如果 bridge 再从 `thread/read` 或实时流收到一个滞后的 `waitingOnApproval` 状态，但此时内存里的 pending approval 已经清空，Android/bridge 仍可能把会话重新刷回 `awaiting_approval`，表现为“看起来还在待审批，但实际上没有待审批请求，也没有后续输出”。
  - 已完成二次修复：
    - `bridge/src/app-server-runner.ts`
      - 对本地会话新增 `normalizeThreadStatusForSession()`：如果线程快照仍是 `waitingOnApproval`，但 bridge 内存里已经没有 pending approval，则对外统一按 `running` 暴露，避免旧线程状态把会话重新打回待审批。
    - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
      - 新增 `normalizeSessionStatus()`，在 `openSessionDetail`、`run.status`、`session.started`、HTTP 快照合并时统一过滤“没有 pending approval 的 awaiting_approval”。
      - 如果自动批准已经成功、待审批上下文已清空，后续滞后的快照/实时事件不再把详情页状态写回“待审批”。
    - 测试：
      - `bridge/tests/app-server-runner.test.ts` 增加“无 pending approval 时 stale waitingOnApproval 视为 running”的回归用例。
      - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt` 增加“批准后 stale snapshot 不会把会话刷回 awaiting_approval”的回归用例。

## 后续事项

- [x] 结合代码与日志还原完整断链路径。
- [x] 判断这次异常是否属于上游连接重试而非 Android 主 WebSocket 故障。
- [x] 形成根因结论与修复建议。
- [x] 如果用户确认修复，再把“可重试错误”和“本地主动取消噪声”从用户可见终态错误里拆开处理。
- [x] 运行 Android 构建与单测验证修复。
- [x] 排查“退出详情后重进，待审批但无法审批”的根因。
- [x] 实现待审批上下文恢复，避免重进详情后无法审批。
- [x] 把手机端所有会话统一收敛为自动审批与完全权限。
- [x] 移除审批模式与文件权限的设置/编辑入口。
- [x] 执行 bridge 与 Android 的全量验证。
- [x] 修正自动批准后被滞后快照刷回“待审批”的状态回退问题。
- [x] 重新构建最新 debug APK，确保不再携带旧的权限配置 UI。

## 补充说明：不同机器打包 APK 需要卸载的原因

- 用户后续反馈：之前在另一台电脑打过 APK，安装时已经卸载过一次；这次重新安装当前机器打出的包时，又被系统提示“与已安装应用签名不同”，必须再次卸载。
- 已确认当前工程 `android/app/build.gradle.kts` 没有自定义 `signingConfig`，`scripts/build-android-debug.ps1` 也只是执行 `assembleDebug`。
- 这意味着项目当前使用的是 Android 默认 debug 签名，而默认 debug keystore 通常保存在各自机器的用户目录下，属于“每台机器一把”。
- 只要两次安装的 APK 使用了不同的签名证书，即使 `applicationId` 和 `versionName` 都一样，Android 也不会允许覆盖安装，只能先卸载旧包。
- 因此“换机器打包后需要再次卸载”不是代码差异本身导致的，更直接的原因是两台机器生成/持有的 debug keystore 不一致。
- 如果后续希望避免重复卸载，应该改成固定签名来源，例如：
  - 在仓库/CI 中使用统一的 debug keystore；
  - 或者为可分发安装包配置固定 release keystore，并始终由同一套密钥签名。

## 补充进展：固定 debug 签名

- 用户随后确认：把项目改成固定签名，避免后续在不同机器打包时再次因为签名不一致而必须卸载。
- 已完成实现：
  - 新增仓库内固定 debug keystore：`android/signing/debug.keystore`
  - 修改 `android/app/build.gradle.kts`
    - 为 `debug` signing config 显式指定：
      - `storeFile = rootProject.file("signing/debug.keystore")`
      - `storePassword = "android"`
      - `keyAlias = "codexmobiledebug"`
      - `keyPassword = "android"`
    - `debug` buildType 明确使用上述固定 signing config
  - 修改 `README.md`
    - 补充当前 debug 包使用仓库固定签名的说明
    - 明确指出：从旧的“每机默认 debug keystore”切换到这套固定签名时，仍需要最后再卸载一次；之后跨机器更新即可直接覆盖安装
- 当前行为：
  - 以后从本仓库任意机器执行 `scripts/build-android-debug.ps1`，只要代码相同，产出的 debug APK 都会使用同一套签名证书。
  - 手机上如果已经装的是这套固定签名的版本，后续来自其他机器的同仓库 debug 包可直接覆盖安装，不再因为签名不同被拦截。

## 补充判断：bridge 平滑更新方向

- 用户进一步提出：能否把 bridge 做成“热更新”，避免每次重启影响现有连接。
- 已结合当前实现判断：
  - 当前后台 bridge 通过 `scripts/restart-bridge-background.ps1` 直接停旧进程、重新 `npm run build` 并启动新的 `dist/index.js`，不具备热替换或双实例切换能力。
  - `bridge` 当前既负责 HTTP/API，也持有运行时内存状态，包括会话映射、pending approval、实时流订阅与上游 `app-server` 交互，因此单进程内的“无感热更新”不现实。
  - Android 端已经具备实时流自动重连和会话快照刷新能力，更适合沿着“平滑重启 + 自动恢复”演进，而不是强行做真正热更新。
- 当前建议方向：
  - 不做“源码热更新 / 进程内热替换”
  - 优先做“bridge 平滑重启”，目标是：
    - 短时间内切换新版本
    - Android 自动感知断流并重连
    - 通过 `/api/session/:id` 快照与 `session.started` 重建详情页状态
    - 避免用户手工回退、重进、重发
- 初步落地重点：
  - bridge 增加版本与维护状态暴露
  - bridge 增加“drain”模式，停止接收新输入但保留短暂存活窗口
  - Android 区分“bridge 正在重启”与“普通实时流失败”
  - Android 在重连成功后主动刷新当前会话与列表快照
  - 中长期再考虑把关键会话运行态外置，减少重启丢失面

## 补充进展：bridge 平滑重启与 Android 自动恢复

- 用户确认继续，不做“真正热更新”，先实现第一阶段的“平滑重启 + 自动恢复”。
- 已完成实现：
  - bridge：
    - `bridge/src/types.ts`
      - 新增 `BridgeLifecycleState`
      - 为实时流事件类型增加 `bridge.lifecycle`
    - `bridge/src/app.ts`
      - `/health` 新增 `bridgeVersion`、`startedAt`、`lifecycle`
      - 新增仅允许 loopback 调用的 `POST /internal/lifecycle/drain`
      - bridge 进入 drain 后，会向当前会话 WebSocket 订阅广播 `bridge.lifecycle`
      - drain 期间拒绝新的变更类请求：
        - 创建会话
        - 上传附件
        - 更新配置
        - 发送输入
        - 中断
        - 审批
      - 新进入的详情页实时流如果恰逢 drain，也会在 `session.started` 后立即收到 `bridge.lifecycle`
    - `scripts/restart-bridge-background.ps1`
      - 后台重启前先请求旧 bridge 进入 drain 窗口
      - 默认等待 `2000 ms` 给移动端收到生命周期事件，再停旧进程、起新进程
  - Android：
    - `android/app/src/main/java/com/openai/codexmobile/data/SessionStreamEvent.kt`
      - 新增 `BridgeLifecycle`
    - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
      - 解析 `bridge.lifecycle`
      - 日志摘要里区分 bridge 生命周期事件
    - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
      - 识别 `bridge.lifecycle(restarting)`
      - 在旧连接关闭前显示“bridge 即将重启 / bridge 正在进入平滑重启窗口”
      - 旧连接断开后，对这类事件触发的断流走“bridge 重启恢复”路径，优先立即重连
      - 重连失败时再回退到原有指数退避
      - 重连成功后通过现有实时流 + 快照刷新恢复状态
    - `README.md`
      - 补充后台重启脚本会先 drain，再切换新 bridge 的说明
- 当前用户可见行为：
  - bridge 重启前，详情页会先收到一条“bridge 即将重启”的生命周期提示，而不是直接表现成普通错误。
  - 旧连接关闭后，手机端会优先立即重连；若新 bridge 尚未完全就绪，再自动退回现有重连退避。
  - bridge drain 期间不会再接受新的写操作，避免在切换窗口接受请求后又因为进程退出而丢失。

## 补充进展：Android Markdown 显示与复制

- 用户后续反馈：详情页里的回复仍按纯文本显示，Markdown 没有渲染；同时消息内容无法直接复制，希望补这两项能力。
- 现状排查结论：
  - 详情页外层已经有自己的 transcript 结构化逻辑，不是单个大文本框：
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 当前只识别两种特殊内容：
    - fenced code block
    - `![alt](src)` 图片
  - 其他正文都会被当作 `TranscriptPart.Text` 用普通 `Text` 渲染，因此粗体、列表、引用、标题、行内代码、链接都不会生效。
  - 当前剪贴板能力只在设置页“复制日志”用过，没有接到会话消息与代码块。
- 已完成实现：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
    - 为 `TranscriptBubble` 增加 `rawBody`
    - 调整 transcript 分段逻辑：先按会话消息头分组，再把同一条消息内的空行段落重新并回 `rawBody`
    - 避免 Markdown 段落、列表、标题因为空行被提前切碎
  - 新增 `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
    - 新增轻量 Markdown 块级解析：
      - 标题
      - 段落
      - 引用
      - 有序/无序列表
    - 新增轻量行内解析：
      - 粗体
      - 斜体
      - 删除线
      - 行内代码
      - Markdown 链接
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
    - `TranscriptPart.Text` 改为走 `MarkdownTextBlock`
    - 每条消息气泡头部增加“复制消息”
    - 每个代码块增加“复制代码”
    - 执行过程分组和执行步骤条目也共享同一套复制入口
  - `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
    - 补充消息复制与代码块复制的测试标签
- 当前用户可见行为：
  - 助手正文里的常见 Markdown 现在会直接按格式显示，而不是全部裸文本输出。
  - 单条消息可以一键复制原始正文。
  - 代码块可以单独复制代码内容，不需要手动选中。

## 验证结果

- 已执行：
  - `cd bridge`
  - `npm run check`
  - 结果：`通过`
- 已执行：
  - `cd bridge`
  - `npm test`
  - 结果：`44 passed`
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：`BUILD SUCCESSFUL`
- 已执行：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：`BUILD SUCCESSFUL`
- 已新增并通过的 Android Markdown 回归验证：
  - `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptBubbleTest.kt`
    - 验证同一条消息里的 Markdown 空行段落不会被 transcript 外层切碎
  - `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptMarkdownTest.kt`
    - 验证标题、列表、引用的块级解析
    - 验证粗体、行内代码、链接的行内解析结果
- 最新 debug APK 产物：
  - `android/app/build/outputs/apk/debug/app-debug.apk`
- 已新增并通过的回归验证：
  - bridge：
    - `bridge/tests/app.test.ts`
      - 验证 `/health` 返回生命周期状态
      - 验证 `POST /internal/lifecycle/drain` 会广播 `bridge.lifecycle`
      - 验证 drain 期间写请求返回 `503 bridge-restarting`
  - Android：
    - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
      - 验证 `bridge.lifecycle` 事件解析
    - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
      - 验证 bridge 生命周期事件后，旧连接关闭会立即触发重连恢复
- 已执行：
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `keytool -list -v -keystore android/signing/debug.keystore -storepass android`
  - 结果：固定 keystore SHA-256 指纹为 `DE:96:CE:BC:5E:CE:A6:8E:F1:7E:51:D3:F8:4D:FD:E3:55:31:ED:0F:EE:0E:F0:A0:49:B8:A9:ED:2F:B9:F0:70`
- 已执行：
  - `apksigner verify --print-certs android/app/build/outputs/apk/debug/app-debug.apk`
  - 结果：APK 签名 SHA-256 指纹为 `de96cebc5ecea68ef17e51d3f84dfde35531ed0fee0ef0a049b8a9ed2fb9f070`，与仓库固定 keystore 一致
- 排查过程中曾把构建脚本和单测并发触发，导致 Gradle/Kotlin daemon 产生一轮不可信的级联 `Unresolved reference`；停止 daemon 后串行重跑，编译与测试均恢复正常，本次以串行验证结果为准。
