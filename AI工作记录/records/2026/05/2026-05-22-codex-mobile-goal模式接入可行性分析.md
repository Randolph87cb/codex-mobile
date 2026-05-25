# codex-mobile goal 模式接入可行性分析

- 日期：2026-05-22
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：`bridge/`、`android/`、`docs/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`codex`、`goal mode`、`app-server`、`可行性分析`

## 本次目标

- 判断 Codex 新发布的 `goal mode` 是否适合接入当前 `codex-mobile` 项目。
- 结合官方最新信息和当前仓库实现，给出接入边界、改动面和优先级建议。

## 当前状态

- 已核对官方最新发布说明：`goal mode` 于 2026-05-21 在 Codex app、IDE extension、CLI 普遍开放。
- 已核对项目内已提交的上游 `app-server` 文档和协议生成物。
- 本次仅做只读检查与分析，没有改业务代码。

## 关键事实

- `goal mode` 在当前 `app-server` 语义里不是 `thread/start` 或 `turn/start` 的简单布尔开关。
- 项目内上游文档明确存在线程级目标接口：`thread/goal/set`、`thread/goal/get`、`thread/goal/clear`，并会发出 `thread/goal/updated`、`thread/goal/cleared` 通知。
- 已提交的协议生成物也包含 `ThreadGoal`、`ThreadGoalStatus`、`ThreadGoalUpdatedNotification` 类型，说明当前仓库参考的协议版本已经认识这套能力。
- 当前 bridge 会话创建、配置更新、详情读取和 WebSocket 首帧都只覆盖 `cwd`、`model`、`approvalMode`、`reasoningEffort`、`serviceTier`、`sandboxMode`，没有 `goal` 字段。
- 当前 `AppServerRunner` 的通知处理只覆盖线程状态、turn 生命周期、消息增量、审批和错误，没有处理 `thread/goal/updated` 或 `thread/goal/cleared`。
- Android 侧 `SessionDetail`、`SessionSummary` 也没有目标相关模型字段；设置与创建流程仍以常规会话参数为主。

## 结论

- 可以接，而且从产品方向上是匹配的。
- 但正确接法应是“在线程上补目标管理能力”，不是把它当成现有 `approvalMode` / `sandboxMode` 那样的会话枚举选项硬塞进去。
- 这属于中等规模接入，不需要重构链路；核心是 bridge 补协议透传与状态聚合，Android 补目标设置、展示和状态刷新。

## 推荐接入方式

- bridge：
  - 在 runner 增加 `thread/goal/set|get|clear` 调用封装。
  - 处理 `thread/goal/updated`、`thread/goal/cleared` 通知，并把目标状态映射进 bridge 自定义会话视图和实时事件。
  - 视需要新增 REST API，例如：
    - `GET /api/session/:id/goal`
    - `PUT /api/session/:id/goal`
    - `DELETE /api/session/:id/goal`
- Android：
  - 在会话详情页提供“目标/成功标准”入口，而不是放到连接设置页。
  - 展示 `objective`、`status`、`tokenBudget`、`tokensUsed`、`timeUsedSeconds`。
  - 在实时流中接收目标更新，避免用户只能靠刷新详情看变化。

## 不建议的做法

- 不建议把 `goal mode` 误建模成创建会话时的一个固定枚举字段。
- 不建议只做“发送一段提示词当目标”的伪接入；这样拿不到目标状态、预算和后续清理能力，也无法和上游客户端对齐。

## 主要改动面

- bridge：
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/types.ts`
  - `bridge/src/app.ts`
  - `bridge/src/session-view.ts`
  - 对应测试
- android：
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/model/SessionDetail.kt`
  - `android/app/src/main/java/com/openai/codexmobile/model/SessionSummary.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 会话详情页及对应测试

## 风险与注意点

- 目标是线程级持久状态，不是单轮输入；要处理已有线程恢复、归档、重新附加时的同步问题。
- 当前 Android 端对部分权限相关配置做了托管收敛，接入 goal 时要避免继续沿用“看似可配、实际强制覆盖”的模式，免得 UI 语义失真。
- 如果后续想真正发挥 `goal mode` 的长任务价值，可能还要继续完善后台实时流、断线恢复和可能的通知提醒。

## 后续建议

- 如果目标是“尽快支持并验证价值”，建议先做最小闭环：
  - bridge 增加 goal 的 get/set/clear 与 WS 透传；
  - Android 详情页支持编辑和查看 goal；
  - 先不做更复杂的本地通知、目标模板和统计页。
- 如果用户确认要做，再按 bridge 优先、Android 跟进的顺序实施。

## 证据

- 官方发布说明：2026-05-21 Codex release notes 提到 `goal mode` 已在 app、IDE extension、CLI 普遍开放。
- 上游文档：`docs/upstream/codex-app-server/README.md` 已明确 `thread/goal/set|get|clear` 及示例。
- 协议生成物：`docs/generated/ts/v2/ThreadGoal.ts`、`ThreadGoalStatus.ts`、`ThreadGoalUpdatedNotification.ts`
- 当前 bridge 会话与通知实现：`bridge/src/app.ts`、`bridge/src/app-server-runner.ts`、`bridge/src/types.ts`
- 当前 Android 会话模型与创建逻辑：`android/app/src/main/java/com/openai/codexmobile/model/SessionDetail.kt`、`SessionSummary.kt`、`AppViewModel.kt`

## 实际落地

- 已按“最小闭环 v1”完成 goal 能力接入。
- bridge：
  - 在 `AppServerRunner` 增加 `thread/goal/get`、`thread/goal/set`、`thread/goal/clear` 调用封装。
  - 新增 `goal.updated`、`goal.cleared` 实时事件。
  - `GET /api/session/:id` 与 `GET /api/session/:id/ws` 首帧现在会携带 `goal` 与 `goalCapability`。
  - 新增 REST 接口：
    - `GET /api/session/:id/goal`
    - `PUT /api/session/:id/goal`
    - `DELETE /api/session/:id/goal`
- Android：
  - `SessionDetail` 增加 `goal` 和 `goalCapability`。
  - `BridgeApi` / `RealBridgeDataProvider` 增加目标读取、更新、清除接口。
  - `SessionStreamEvent` 增加 `GoalUpdated` 与 `GoalCleared`。
  - `AppViewModel` 增加开始目标、暂停、恢复、清除目标的处理逻辑，并能在实时流事件到达时更新详情状态。
  - `SessionDetailScreen` 增加目标卡片与目标编辑弹窗，可查看目标状态、预算、已用 token 和耗时。
- 文档：
  - `docs/api.md` 已补充 goal REST 接口、详情字段与实时流事件。
  - `README.md` 已补充手机端 goal 模式能力说明。

## 主要改动文件

- bridge：
  - `bridge/src/types.ts`
  - `bridge/src/bridge-runner.ts`
  - `bridge/src/session-view.ts`
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/app.ts`
  - `bridge/tests/app-server-runner.test.ts`
  - `bridge/tests/app.test.ts`
- android：
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/SessionStreamEvent.kt`
  - `android/app/src/main/java/com/openai/codexmobile/model/SessionDetail.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FakeCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FallbackCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
  - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`

## 验证结果

- 已执行：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
- 结果：
  - `bridge` 类型检查通过
  - `bridge` 单测通过
  - Android debug 构建通过
  - Android `testDebugUnitTest` 通过
- 中途问题：
  - Android 初次构建暴露出 `RealBridgeDataProvider.kt` 两处 Kotlin `return` 写法错误，已修复。
  - Android 初次单测暴露出一个 `SessionStarted` 测试构造参数未同步，已修复。

## 升级后连接故障排查与修复

- 背景：
  - 用户将本机升级到 `codex-cli 0.133.0` 后，手机通过 `https://codex.randolph87.top` 连接 bridge 仍然失败。
  - Android 日志表面显示失败点在 `GET /health` 超时。
- 实际定位：
  - bridge 日志显示公网请求已到达本机，`GET /health` 实际已返回 `200`，随后立刻进入 `GET /api/sessions`。
  - Android `connect()` 后的 `AppViewModel.connect()` 会继续拉会话列表和首个会话详情，所以最终“连接失败”并不等于第一跳 `/health` 真失败。
  - 当前后台 bridge 的 `stderr` 明确报错：
    - `Error: spawn codex.exe ENOENT`
  - 根因是 `bridge/src/app-server-client.ts` 的可执行文件解析逻辑仍优先依赖旧 npm vendor 路径，找不到后直接退回裸 `codex.exe`；后台 Node 进程启动 `app-server` 时没有稳定命中真实可执行路径。
- 本次修复：
  - `bridge/src/app-server-client.ts`
    - 新增 `resolveCodexLaunchSpec()`，改为返回 `{ command, args }` 启动规格。
    - 优先使用 `CODEX_EXECUTABLE`。
    - 若存在全局 npm 安装的 `@openai/codex/bin/codex.js`，则直接复用当前 Node 进程执行 `node <codex.js> app-server`，绕开 PATH/WindowsApps 别名不稳定问题。
    - 旧 vendor exe 仍保留为次级回退。
    - 最后才退回裸 `codex.exe`。
  - `bridge/src/app-server-client.ts`
    - 为子进程补充 `error` 事件处理，把 `spawn ... ENOENT` 转换成可诊断的 transport 退出，而不是直接以未处理异常打崩 bridge。
  - `bridge/src/app-server-runner.ts`
    - 扩大 goal 不支持判定，把 `no such table: thread_goals` 也视作不支持，避免旧 schema 或未迁移状态导致会话详情直接 500。
  - `scripts/restart-bridge-background.ps1`
    - 新增 `Resolve-CodexExecutable`。
    - 后台启动前会显式解析 `codex.exe` 的真实路径，并把它写入 `CODEX_EXECUTABLE`。
    - 当前会把实际解析出的路径写入 `.tmp/bridge/bridge-process.json` 的 `CodexExecutable` 字段，便于排查。
- 新增测试：
  - `bridge/tests/app-server-client.test.ts`
    - 覆盖 transport 在 initialize 前异常退出时，`AppServerClient.start()` 会正确失败。
    - 覆盖 `resolveCodexLaunchSpec()` 的三条路径：
      - 显式 `CODEX_EXECUTABLE`
      - 全局 npm `codex.js`
      - 最终回退 `codex.exe`
  - `bridge/tests/app-server-runner.test.ts`
    - 覆盖 `thread_goals` 表缺失时：
      - `getSessionGoal()` 返回 `unsupported`
      - `getSessionView()` 不再抛错，而是回传 `goalCapability: unsupported`
      - `updateSessionGoal()` / `clearSessionGoal()` 返回 `goal-not-supported`
- 本轮验证：
  - 已执行：
    - `cd bridge`
    - `npm run check`
    - `npm test`
    - `npm run build`
    - 临时启动 `app-server` bridge（`127.0.0.1:8790`）
    - 验证 `GET /health`
    - 验证 `GET /api/sessions?archived=false`
    - 创建临时会话并验证：
      - `PUT /api/session/:id/goal`
      - `GET /api/session/:id/goal`
      - `DELETE /api/session/:id/goal`
  - 结果：
    - 类型检查通过
    - 单测通过，当前 `bridge` 测试共 `61` 项全部通过
    - 运行态验证通过：
      - `healthOk = true`
      - `runnerMode = app-server`
      - goal `set/get/clear` 全部返回 `capability = supported`
  - 限制：
    - 在当前 Codex 工具环境里，命令结束后由工具拉起的后台子进程会被回收，因此无法在这里完成“用户机器上的长期常驻”最终验收。
    - 但在单个命令窗口内已经验证：修复后的 bridge 能正常列会话、创建会话，并顺利使用 goal 接口。

## 用户主机实机复验

- 用户在主机上执行：
  - `cd bridge && npm run build`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\restart-bridge-background.ps1 -SkipBuild`
  - `Invoke-RestMethod http://127.0.0.1:8787/health`
  - `Invoke-RestMethod http://127.0.0.1:8787/api/sessions?archived=false`
- 当时结果：
  - `/health` 返回 `200`
  - `/api/sessions` 返回成功
  - 手机已确认“现在通了，手机连上了”
- 随后追加定位到一个残余问题：
  - `restart-bridge-background.ps1` 虽然能显式解析 `CODEX_EXECUTABLE`，但优先拿到的是 WindowsApps 的 `codex.exe`
  - 常驻 bridge 在这个路径上执行新建会话时会报 `spawn EPERM`
  - 将脚本改为优先选择 npm 的 `codex.js` / `codex.cmd` 后，状态文件中的 `CodexExecutable` 已稳定变为：
    - `C:\Users\Administrator\AppData\Roaming\npm\node_modules\@openai\codex\bin\codex.js`
- 再次追加定位到最后一个兼容缺口：
  - `bridge/src/app-server-client.ts` 对 `CODEX_EXECUTABLE` 仍一律按“可直接执行文件”处理
  - 当脚本传入 `codex.js` 时，bridge 会直接 `spawn codex.js`，导致 `spawn EFTYPE`
- 最终修复：
  - `bridge/src/app-server-client.ts`
    - `CODEX_EXECUTABLE` 指向 `.js` 时，改为 `node <script> app-server`
    - `CODEX_EXECUTABLE` 指向 `.cmd` / `.bat` 时，改为 `cmd.exe /d /s /c`
  - `bridge/tests/app-server-client.test.ts`
    - 新增 `CODEX_EXECUTABLE=.js`
    - 新增 `CODEX_EXECUTABLE=.cmd`
- 最终验证：
  - 已重新执行：
    - `cd bridge`
    - `npm run check`
    - `npm test`
    - `npm run build`
    - `powershell -ExecutionPolicy Bypass -File .\scripts\restart-bridge-background.ps1 -SkipBuild`
  - 结果：
    - `bridge` 测试更新为 `63` 项全部通过
    - 常驻 bridge 状态文件中的 `CodexExecutable` 为 npm `codex.js`
    - 实机 `POST /api/session` 成功创建临时会话
    - 实机 `PUT /api/session/:id/goal`、`GET /api/session/:id/goal`、`DELETE /api/session/:id/goal` 全部成功，`capability = supported`
  - 备注：
    - 用于验证的本地临时会话为 `sess_f60ea507-f209-4e5c-afe7-7a8d5a51261d`
    - 该会话调用 `/archive` 返回 `session-not-archivable`，说明当前本地新建测试会话不能通过现有归档接口隐藏

## Android 客户端与常驻 bridge 收口验证

- 已执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - `Invoke-RestMethod http://127.0.0.1:8787/health`
  - `Invoke-RestMethod http://127.0.0.1:8787/api/sessions?archived=false`
- 结果：
  - Android debug 构建通过
  - Android `testDebugUnitTest` 通过
  - 常驻 bridge 当前仍返回：
    - `runnerMode = app-server`
    - `phase = running`
  - 常驻 bridge 当前仍能正常返回会话列表
- Android 侧与 goal 相关的单测覆盖点：
  - `AppViewModelTest.kt`
    - `goalActionsUpdateSelectedSessionAndCallBridge`
    - `goalStreamEventsRefreshSelectedSessionGoal`
  - `RealBridgeDataProviderTest.kt`
    - `parseGoalUpdatedEvent`
    - `parseSessionDetailReadsGoalFields`
- 当前状态总结：
  - 用户已确认手机“现在通了，手机连上了”
  - bridge 常驻实例已能稳定列会话
  - goal 后端真实接口已实机验证通过
  - Android 数据层与 ViewModel 的 goal 读写、流事件刷新均有单测覆盖并通过

## 文档同步

- 已更新：
  - `README.md`
  - `docs/api.md`
- 本次补充内容：
  - Cloudflare Tunnel 部署在局域网另一台机器时的推荐链路说明
  - 后台脚本与 bridge 当前对 `CODEX_EXECUTABLE`、`codex.js`、`codex.cmd` 的实际启动行为
  - `spawn ENOENT`、`spawn EPERM`、`spawn EFTYPE` 与 `goal-not-supported` 的排障说明
  - `POST /api/session` 的 `session-initialize-failed` 错误语义
  - goal 接口在旧 host / 缺少 `thread_goals` 表时的兼容降级语义
  - Android 当前实际使用的 goal 接口列表
- 验证：
  - 本轮为文档同步，没有新增代码路径，因此未额外执行构建或测试
  - 文档内容基于本轮已经完成的 bridge、Android 与常驻实例实机验证结果同步
