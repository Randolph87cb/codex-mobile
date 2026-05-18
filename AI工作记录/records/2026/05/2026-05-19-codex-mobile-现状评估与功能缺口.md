# codex-mobile 现状评估与功能缺口

- 日期：2026-05-19
- 来源：Codex
- 类型：记录
- 相关目录：`D:\workspace\codex-mobile`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`Windows`、`bridge`、`评估`

## 任务输入摘要

- 最终结果：基于当前仓库实现，判断项目现状并整理缺失的功能方向。
- 现有素材：`README.md`、`docs/architecture.md`、`docs/api.md`、`bridge/`、`android/`、既有测试与工作记录。
- 明确约束：按项目当前定位评估，不把非目标能力误判为缺失功能。
- 完成标准：输出一份按优先级整理的功能缺口清单，并给出代码级依据。
- 产出后动作：作为后续 bridge / Android 迭代的 backlog 输入。

## 评估范围

- 阅读项目定位、当前状态和“下一步”说明。
- 对照 bridge API、app-server runner、Android 数据层与 UI 层实现。
- 检查现有测试覆盖范围是否支撑关键能力闭环。

## 关键发现

- 当前项目已经具备可运行 MVP 基础链路：
  - Android 可连接 bridge。
  - 可列出会话。
  - 可创建会话。
  - 可发送一条输入。
  - 可通过详情页看到一次轮询后的文本结果。
- 当前最核心缺口仍集中在三条主线：
  - 审批闭环未接通。
  - Android 真实实时流未接通。
  - bridge 安全控制面仍是空壳。
- bridge 的 `/api/session/:id/approve` 仍直接返回 `approval workflow is not wired yet`。
- `AppServerRunner` 创建线程时写死 `approvalPolicy: "never"`，说明“默认保留审批点”的产品原则还没有落到真实运行链路。
- `AppServerRunner` 收到 server request 后，只发一个 `tool.request` 占位事件，然后立刻向 app-server 回 `not supported` 错误，没有真实审批响应。
- Android 当前 `BridgeApi` 只暴露 `connect / disconnect / createSession / sendInput`，没有 `interrupt`、`approve`、实时订阅等控制能力。
- Android 详情页仍依赖 `AppViewModel.awaitSessionRefresh()` 的 30 秒 HTTP 轮询，而不是 `ws` 实时流。
- 会话详情 UI 目前只是“文本预览 + 输入框”，没有工具请求、执行状态、审批动作、终止动作等移动端核心控制组件。
- 设置页明确写着“后续再补完整设置项”，说明 token、白名单、目录、默认模型等配置还未产品化。
- 现有测试主要覆盖：
  - bridge 基础 HTTP 契约；
  - session view 映射；
  - mock runner 事件；
  - app-server client 请求/通知分发；
  - Android 发送后轮询。
- 现有测试尚未覆盖审批、WebSocket 会话流、Android 真实 bridge 实现、认证与目录边界。

## 功能缺口清单

### P0：运行闭环

- 真实审批链路：
  - 缺 `/api/session/:id/approve` 到 app-server 的真实响应映射。
  - 缺移动端审批 UI 与动作按钮。
  - 缺审批完成后的状态回写与事件刷新。
- Android 实时消息流：
  - 缺会话级 WebSocket 连接管理。
  - 缺增量消息渲染与断线重连。
  - 缺实时运行状态、工具事件、错误事件展示。
- 中断能力落地：
  - bridge 已有 `/interrupt`，但 Android 没有接口、ViewModel 动作和 UI 入口。

### P1：安全与控制面

- token 认证：
  - 当前 bridge 对外没有任何鉴权。
  - Android 也没有 token 输入、保存和附带请求头能力。
- 目录白名单：
  - 当前创建会话时直接接受传入 `cwd`，没有目录边界检查。
  - 缺 Android 可见的工作目录选择或只读展示规则。
- 审批策略配置：
  - 目前 UI 虽有 `approvalMode` 字段，但真实 runner 没按该字段驱动后端行为。

### P2：移动端可用性

- 会话详情交互不足：
  - 缺运行中状态、等待批准状态、错误态的显式卡片或时间线。
  - 缺工具调用展示、命令输出展示、审批请求展示。
- 设置页未产品化：
  - 缺 bridge token、默认地址、默认工作目录、默认模型、诊断与日志相关设置。
- 连接管理较弱：
  - 默认 endpoint 仍写死为一条局域网地址，缺更稳妥的首次配置与保存机制。

### P3：工程完备性

- 测试缺口：
  - 缺 bridge WebSocket 行为测试。
  - 缺审批事件与 approve API 测试。
  - 缺 Android `RealBridgeDataProvider` 集成测试。
  - 缺 Android 实时流状态管理测试。
- 运维/诊断能力：
  - 缺 bridge 认证失败、app-server 异常退出、网络抖动等场景的明确诊断接口。

## 建议优先顺序

1. 先接通真实审批链路，避免 bridge 当前把高风险动作直接判成 unsupported。
2. 再把 Android 从轮询切到 WebSocket 实时流，补齐会话详情页的事件展示。
3. 同步补 token 认证和目录白名单，避免 LAN 模式下 bridge 成为裸开放口。
4. 最后再扩展设置页、连接管理和更细的移动端体验。

## 当前结果

- 已完成一次基于文档、实现和测试的现状评估。
- 已确认项目当前仍处于“基础文本远控 MVP”阶段。
- 已整理出可直接转成 backlog 的功能缺口与优先级。
- 已确认后续开发可以采用多 `worktree` 并行，但必须按分支隔离，并先划清 bridge / Android / 安全控制面的 ownership，避免交叉修改同一协议文件。
- 已确定首批并行切分为 3 个 `worktree`：
  - `codex/bridge-approval`
  - `codex/android-realtime`
  - `codex/bridge-security`
- 已确定对应工作目录：
  - `D:\workspace\codex-mobile-bridge-approval`
  - `D:\workspace\codex-mobile-android-realtime`
  - `D:\workspace\codex-mobile-bridge-security`
- 已约束并行边界：
  - `bridge-approval` 负责审批链路，不负责 token 和白名单。
  - `android-realtime` 负责移动端实时流与详情页展示，不改 bridge 安全策略。
  - `bridge-security` 负责认证、目录白名单与配置入口，不接管审批协议设计。
- 已收到 3 条并行分支回报：
  - `codex/android-realtime`：已完成 Android 详情页实时流改造，并推送到远端分支。
  - `codex/bridge-approval`：已完成 bridge 审批闭环，并推送到远端分支。
  - `codex/bridge-security`：已完成 token 认证与 cwd 白名单，并推送到远端分支。
- 已判断当前最优先事项不是继续各自扩写，而是先做一次 bridge 侧集成收口：
  - `bridge-approval` 与 `bridge-security` 都会影响 bridge 契约和联调方式。
  - Android 分支虽然当前范围已完成，但是否需要补 token、审批 UI，取决于 bridge 两条线合并后的最终契约。
- 已形成建议收敛顺序：
  1. 先集成 `bridge-approval` 与 `bridge-security`
  2. 冻结最终 HTTP / WebSocket / approve / 鉴权契约
  3. 再决定 Android 是否需要一个小的同步补丁分支
  4. 最后再合并到 `main`

## 当前集成判断

- `android-realtime` 现在可以视为“功能完成但契约待确认”的状态。
- 当前最大不确定性不在 Android，而在两个 bridge 分支的重叠文件与最终接口定义。
- 需要重点确认的集成点：
  - `/api/session/:id/approve` 的请求体最终结构
  - `tool.request` / `tool.result` / `run.status` 的最终事件字段
  - `/api/session/:id/ws` 在启用 token 后的鉴权方式
  - `/api/*` 鉴权开启时 Android 是否必须同时补 HTTP 与 WebSocket token

## 推荐下一步

- 新建一个临时 bridge 集成分支或 worktree，先合并：
  - `codex/bridge-approval`
  - `codex/bridge-security`
- 在 bridge 集成分支里解决这些高风险重叠文件的冲突：
  - `bridge/src/app.ts`
  - `bridge/src/app-server-runner.ts`
  - `docs/api.md`
  - `README.md`
- 集成完成后先跑 bridge 验证：
  - `cd bridge`
  - `npm run check`
  - `npm test`
- bridge 契约冻结后，再判断 Android 是否只需要一个“小同步分支”：
  - 如果 token 默认关闭，Android 可先不补 token 设置 UI，只补底层 header/WS 支持即可。
  - 如果本轮就要让手机端处理审批，则再补 Android 审批 UI。
  - 如果本轮只要求 bridge 审批可用，Android 只需显示 `awaiting_approval` 和相关事件，不一定马上做批准/拒绝按钮。

## 主线程集成执行结果

- 主线程已新建临时集成分支：`codex/integration-main`
- 已依次合并：
  - `codex/bridge-approval`
  - `codex/bridge-security`
  - `codex/android-realtime`
- `bridge-approval` 与 `bridge-security` 的主要冲突已在主线程裁决：
  - `bridge/src/types.ts`
  - `bridge/tests/app.test.ts`
- 当前集成判断：
  - bridge 审批闭环与安全控制面已同时落地
  - Android 实时流分支已并入且不阻塞当前 bridge 集成结果
  - token 认证默认仍是“配置启用”，未配置时不会阻断现有 Android 基础流程

## 本轮验证结果

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过，5 个测试文件、14 个测试用例全部通过
- Android：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过，debug APK 构建成功，单元测试成功

## 当前结论

- 三个并行分支已完成主线程集成，并通过当前项目要求的最小验证。
- 当前可以将集成结果正式合回 `main`。
- 仍需后续关注但不阻塞本次合并的事项：
  - Android 若要在启用 `CODEX_MOBILE_AUTH_TOKEN` 时正常访问，还需要补 token 透传能力
  - Android 目前已能实时展示状态和事件，但审批按钮是否立即补做，可以放到下一轮
- 主线程随后已将 `codex/integration-main` 正式合回 `main`。
- 已在 `main` 上重新执行一轮最终验证：
  - `bridge`：`npm run check`、`npm test`
  - `android`：`build-android-debug.ps1`、`gradlew testDebugUnitTest`
- 最终验证结果仍然全部通过。

## 合并后缺口判断

- 当前项目已经从“基础文本 MVP”推进到“可实时查看会话 + bridge 侧可审批 + 可选安全控制面”的阶段。
- 合并后最主要缺口已经不再是基础链路，而是“移动端把 bridge 新能力真正消费起来”。
- 当前优先级最高的未完成功能包括：
  - Android token 透传：
    - bridge 已支持 token 鉴权
    - Android 仍缺 HTTP / WebSocket Bearer token 支持
  - Android 审批 UI：
    - bridge 已支持 `/api/session/:id/approve`
    - Android 目前主要是实时展示状态和事件，还没有批准 / 拒绝操作入口
  - Android 对 `tool.request` / `tool.result` 的可读展示：
    - 已有实时流基础
    - 但审批请求、工具执行结果的移动端交互还不完整
  - 联调配置体验：
    - token、默认工作目录、白名单说明、连接配置还未产品化
  - 真实端到端联调验证：
    - 当前自动化验证已通过
    - 但还需要一次“启用 token + 真 bridge + 真 Android 实时流 + 审批操作”的完整联调

## 当前推荐下一轮

1. 先补 Android token 透传能力
2. 再补 Android 审批 UI 和审批事件展示
3. 最后做一轮带 token 的端到端联调

## 本轮补齐结果

- 主线程已完成 Android 侧剩余缺口的实现：
  - 为 HTTP 请求与 WebSocket 实时流增加 Bearer token 透传
  - 在设置页增加 token 输入入口
  - 为实时流 `tool.request` / `tool.result` 增加可读展示
  - 为待审批请求增加移动端批准 / 本会话批准 / 拒绝 / 拒绝并中断操作入口
- 本轮主要修改文件：
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApproval.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
- 本轮新增或更新测试覆盖：
  - token 配置后连接时的 ViewModel 行为
  - `tool.request -> approveSession` 的审批状态流
- 本轮验证结果：
  - `bridge`：`npm run check` 通过
  - `bridge`：`npm test` 通过
  - `android`：`powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过
  - `android`：`.\gradlew.bat testDebugUnitTest` 通过

## 当前剩余事项

- 仍缺一次“启用真实 token 后的真机端到端联调”，但代码与自动化验证已具备条件。

## 后续修正记录

## 本轮模拟器联调收尾

- 目标：
  - 使用本地 Android 模拟器对当前 `main` 做一次真实联调。
  - 把“看起来功能有了，但模拟器里一用就暴露”的剩余缺口收尾。
- 本轮环境：
  - bridge：`http://127.0.0.1:8787`
  - 模拟器访问地址：`http://10.0.2.2:8787`
  - bridge 开启 `CODEX_MOBILE_AUTH_TOKEN`
  - 使用现有本地 AVD `codex-mobile-api35`

### 模拟器联调时暴露的真实问题

- Android 首次默认地址仍是硬编码的局域网 IP，不适合模拟器直接联调。
- 设置页只能改 token，桥接地址、默认工作目录、默认模型、审批模式都没有真正开放编辑。
- 新建会话仍把 `cwd / model / approvalMode` 写死在 `AppViewModel` 里，用户改不了。
- 真实 bridge 连接失败时，Android 仍可能静默切到 `FakeCodexDataProvider`，容易误导成“真服务已连通”。
- 会话详情打开不存在的会话时，会保留上一条旧快照，存在内容误导。
- 详情页实时内容没有自动滚到底，长回复时看起来容易像“卡住”。
- 会话列表的“新建会话 / 设置 / 断开连接”放在长列表底部，历史会话较多时几乎不可达。
- 设置项变多后，设置页在模拟器小屏上出现内容挤压，需要可滚动布局。

### 本轮收尾改动

- Android 改为只连真实 bridge，不再在连接失败时静默回退到本地假数据：
  - `android/app/src/main/java/com/openai/codexmobile/MainActivity.kt`
- 新增本地设置存储，持久化这些关键输入：
  - bridge 地址
  - token
  - 默认工作目录
  - 默认模型
  - 审批模式
  - 文件：`android/app/src/main/java/com/openai/codexmobile/data/AppSettingsStore.kt`
- `AppViewModel` 改为从设置加载初始值，并在用户修改时立即保存；新建会话改为真正消费这些设置值，不再写死参数：
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
- 连接页默认在模拟器环境下使用 `http://10.0.2.2:8787`，并把说明文案改成通用表述：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/ConnectionScreen.kt`
- 设置页增加 bridge 地址、默认工作目录、默认模型、审批模式编辑能力，并改为可滚动布局：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
- 会话详情页增加“跟随最新内容”自动滚动：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 会话列表页改为“列表滚动 + 底部固定操作区”，避免关键按钮被长列表埋住：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`

### 本轮新增测试覆盖

- `connectUsesConfiguredTokenAndUpdatesSettings`
  - 确认 token 会传给连接层并写回设置存储
- `createSessionUsesEditableSavedSettings`
  - 确认新建会话使用用户编辑后的 `cwd / model / approvalMode`
- `missingSessionClearsSelectedSessionWithoutStartingRealtimeStream`
  - 确认会话不存在时不再残留旧内容，也不会继续接实时流

### 本轮验证结果

- Android 单元测试：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- Android 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过
- 模拟器联调事实：
  - 最新 APK 已重新安装到 `codex-mobile-api35`
  - 已确认连接页默认地址变为 `http://10.0.2.2:8787`
  - 已确认带 token 的真实 bridge 可以在模拟器内看到真实会话列表，不再掉到假数据源

### 当前结论

- 这轮用户最容易撞到的“未收尾功能”已经基本收干净，剩余重点不再是基础配置或明显占位逻辑，而是更细的移动端体验增强。

## 本轮继续修正

- 用户反馈：
  - 实时流仍会失败
  - 工具结果 / 代码块还没有真正渲染出来

### 本轮定位结论

- bridge 的历史会话链路存在语义不一致：
  - `/api/sessions` 和 `/api/session/:id` 可以返回历史线程
  - `/api/session/:id/input` 也会先 `attachSession()`
  - 但 `/api/session/:id/ws`、`/interrupt`、`/approve` 之前只查 `store.get()`，不会附着历史线程
- 这会导致 Android 在会话列表里点开历史线程时，详情能看到，但实时流会因为 `session-not-found` 直接断开。
- Android 的 transcript 渲染之前仍是“整段文本 -> 单一气泡”，不会把：
  - fenced code block
  - `tool.request`
  - `tool.result`
  解析成更易读的展示结构。
- 同时，`tool.request / tool.result / error` 等实时事件虽然会更新状态栏，但之前没有稳定写回 `transcriptPreview`，所以即便 UI 支持，也可能在正文里看不到。

### 本轮改动

- bridge：
  - 为这 3 条路由补齐历史线程附着：
    - `/api/session/:id/ws`
    - `/api/session/:id/interrupt`
    - `/api/session/:id/approve`
  - 文件：
    - `bridge/src/app.ts`
  - 测试补充：
    - `bridge/tests/app.test.ts`
    - 增加历史会话的 `interrupt / approve / websocket` 行为验证

- Android：
  - transcript 解析升级为富文本块模型：
    - 普通文本段
    - fenced code block
    - 工具请求 / 工具结果气泡
  - 会话详情页按块渲染：
    - 代码块单独卡片 + 等宽字体
    - 工具结果单独标题和正文
  - `tool.request / tool.result / error / interrupted` 现在会补写进 transcript 正文，不只停在顶部状态区
  - 审批提交结果写入正文时统一改为 `审批结果：...`，方便结构化渲染识别
  - 文件：
    - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
    - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
    - `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptBubbleTest.kt`

### 本轮验证

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过，5 个测试文件、16 个测试全部通过
- Android：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过

### 当前剩余风险

- 我已经通过 bridge 自动化测试覆盖了“历史会话可订阅 ws”的根因修复，但这轮模拟器自动点击在系统返回路径上不够稳定，没有形成一条完整的无人工详情页录制回放。
- 所以当前最关键的剩余确认是：
  - 你在最新版 APK 里点开一个历史线程时，实时流是否不再直接报错
  - 一条带代码块的回复、一次工具请求/工具结果，是否已经按新样式显示

- 真机反馈后，已额外修正两项 Android 问题：
  - WebSocket `onClosing` 不再直接回发保留关闭码 `1005`，改为使用安全关闭码规整处理
  - 会话详情页不再把整段对话塞进单个文本块，而是按“你 / Codex / 系统”拆成对话气泡
- 已新增对话气泡解析测试：
  - `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptBubbleTest.kt`
- 修正后的验证结果：
  - `android`：`gradlew testDebugUnitTest` 通过
  - `android`：`build-android-debug.ps1` 通过

## 本地 Android 测试环境

- 已在项目内补齐 Android 本地模拟器环境，避免每次都依赖真机手工安装。
- 已安装：
  - `emulator`
  - `system-images;android-35;google_apis;x86_64`
- 已创建项目内 AVD：
  - `codex-mobile-api35`
- AVD 数据目录：
  - `.tmp/android-avd/`
- 已新增脚本：
  - `scripts/start-android-emulator.ps1`
  - `scripts/install-android-debug-emulator.ps1`
  - `scripts/run-local-android-test.ps1`
- 已完成实际验证：
  - 模拟器已可启动并被 `adb devices` 识别为 `emulator-5554`
  - `install-android-debug-emulator.ps1` 已成功把 debug APK 安装到模拟器并拉起应用
- 模拟器侧连接 bridge 时，应使用：
  - `http://10.0.2.2:8787`

## 本轮补齐稳定自动回放

- 目标：
  - 不再依赖 `adb` 坐标点击做半自动联调。
  - 为 Android 页面补稳定定位锚点，并在模拟器上跑通一条可重复的详情页自动回放。

### 本轮问题判断

- 之前之所以“能启动模拟器，但没有形成稳定的全自动详情页回放”，核心不是模拟器不行，而是：
  - Compose 页面缺少稳定 `testTag`
  - 关键控件只能靠文本或坐标猜测
  - 真 bridge / 历史会话 / 异步实时流会让黑盒脚本的状态非常漂移
- 所以本轮改法不是继续堆 `adb input tap`，而是补正式的 instrumentation 测试入口。

### 本轮改动

- 为关键页面和操作补稳定 `testTag`：
  - `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - `ConnectionScreen.kt`
  - `SessionListScreen.kt`
  - `SessionDetailScreen.kt`
  - `SettingsScreen.kt`
- 增加 debug 专用回放宿主，不污染 release：
  - `android/app/src/debug/AndroidManifest.xml`
  - `android/app/src/debug/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
- 回放宿主接入确定性测试数据：
  - 固定连接成功
  - 固定会话列表
  - 固定详情 transcript
  - 固定 `tool.request -> approve -> tool.result` 流程
- 新增 instrumentation 用例：
  - `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailReplayTest.kt`
  - 覆盖流程：
    - 连接
    - 打开会话列表
    - 进入会话详情
    - 校验代码块与工具结果文本存在
    - 校验待审批卡片存在
    - 点击批准并确认审批结果写回页面
- 为 instrumentation 增加依赖：
  - `android/app/build.gradle.kts`

### 本轮验证

- Android 单元测试：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- Android debug 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过
- Android 模拟器 instrumentation：
  - `cd android`
  - `.\gradlew.bat connectedDebugAndroidTest`
  - 结果：通过

### 当前结论

- “没有稳定自动详情页回放”这个缺口本轮已经补上。
- 现在项目里已经有一条基于模拟器、可重复执行的最小 UI 回放链路，可作为后续详情页和实时流交互改动的回归基础。

## 后续事项

- [ ] 接通 `/api/session/:id/approve` 与 app-server 审批响应
- [ ] 将 Android 详情页从轮询升级为 WebSocket 实时流
- [ ] 为 Android 增加中断、审批、运行状态展示
- [ ] 为 bridge 增加 token 认证与目录白名单
- [ ] 为上述能力补齐 bridge / Android 测试

## 追加记录：草稿线程、目录分组与同线程状态同步

- 时间：2026-05-19
- 用户反馈：
  - 新建线程时希望先选目录，不要立刻创建远端会话。
  - 会话列表希望按目录分组，并能在目录分组内直接新建线程。
  - 模型、推理强度、速度希望做成常驻配置按钮。
  - 顶部状态区太占空间，希望压缩成图标并可点开查看详情。
  - 同时在手机端和 Codex app 查看同一线程时，状态会突然停止或被打乱。

### 本轮实现

- Android：
  - 会话列表按 `cwd` 分组展示，并在每个目录卡片上提供“在此新建”：
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - 新建线程改成“草稿线程”：
    - 先选目录，进入草稿详情页；
    - 首条消息发送时才真正调用 `POST /api/session`；
    - 文件：
      - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
      - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - 顶部配置改成常驻操作条：
    - 目录
    - 模型
    - 推理强度
    - 速度档位
    - 文件：
      - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
      - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
  - 顶部实时状态改成紧凑图标条，可展开查看完整细节，并提供“立即同步”入口：
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 设置持久化扩展到：
    - `reasoningEffort`
    - `serviceTier`
    - 文件：
      - `android/app/src/main/java/com/openai/codexmobile/data/AppSettingsStore.kt`
  - 回放宿主和 UI 测试同步更新：
    - `android/app/src/debug/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
    - `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailReplayTest.kt`
    - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
    - `android/app/src/test/java/com/openai/codexmobile/ui/screen/SessionListGroupingTest.kt`

- bridge：
  - `POST /api/session` 与 `PATCH /api/session/:id/config` 全链路支持：
    - `cwd`
    - `model`
    - `approvalMode`
    - `reasoningEffort`
    - `serviceTier`
  - 历史线程 / 外部线程状态刷新更积极：
    - 进入历史线程时优先 `thread/resume` / `thread/read`
    - 优先用真实线程状态覆盖旧本地状态
  - 同线程被别的客户端占用时，bridge 会返回 `thread-busy` 并映射为 HTTP `409`，避免手机端继续盲发输入把状态打乱。
  - `interrupt` 会优先根据真实线程内容寻找活跃 turn，因此外部客户端启动的当前轮也能被识别和同步。

### 本轮验证

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过，5 个测试文件、23 个测试全部通过
- Android：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat connectedDebugAndroidTest`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：全部通过

### 当前交付结论

- 新线程现在是“先选目录、先写首条消息、发送时才真正创建”。
- 会话列表现在按目录分组，并支持从目录上下文直接开草稿线程。
- 模型 / 推理 / 速度现在既能持久化，也能在详情页顶部直接改。
- 顶部大状态卡已压缩成图标条，详细状态可展开查看。
- 与桌面 Codex app 同时看同一线程时，bridge 现在会更积极地同步真实线程状态，并在忙碌冲突时明确返回 `409`，不再默默把状态打乱。

## 追加记录：详情页顶部配置按钮在小屏回放中不可见

- 时间：2026-05-19
- 问题现象：
  - `connectedDebugAndroidTest` 回放里，详情页顶部 `service tier` 按钮在模拟器小屏上不可见，导致自动化断言失败。
- 根因判断：
  - 详情页配置条使用单行横向滚动布局。
  - 在 API 35 模拟器竖屏宽度下，目录 / 模型 / 推理 / 速度 4 个按钮不能同时落在可视区域。
- 修复：
  - 将配置条从单行 `Row + horizontalScroll` 改成可换行的 `FlowRow`。
  - 文件：
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 修复后验证：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat connectedDebugAndroidTest`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：全部通过

---

## 追加记录：bridge 审批闭环接通

- 时间：2026-05-19
- 工作目录：`D:\workspace\codex-mobile-bridge-approval`
- 分支：`codex/bridge-approval`

### 本次目标

- 只接通 bridge 审批链路，不处理 token、目录白名单和 Android WebSocket。
- 保持现有移动端协议尽量稳定，优先继续使用 `/api/session/:id/approve` 和 `tool.request` 事件模型。

### 关键实现

- `AppServerRunner` 不再在收到可审批的 server request 后立刻回 `not supported`。
- 新增 bridge 内部 pending approval 管理：
  - 按 `sessionId + requestId` 暂存待审批请求；
  - 收到请求时把会话状态改为 `awaiting_approval`；
  - 向事件流发出 `run.status(awaiting_approval)` 和 `tool.request`。
- 接通 `/api/session/:id/approve`：
  - 支持 `requestId` 选择待审批项；
  - 支持 `decision=approve|approve_for_session|reject|reject_and_interrupt`；
  - 回写 app-server 后发出 `tool.result` 和新的 `run.status`。
- `thread/start` 与 `turn/start` 现在会根据 `approvalMode` 传递真实 `approvalPolicy`：
  - `manual -> on-request`
  - `auto -> never`
- `AppServerRunner` 对附加历史线程也能补齐 `threadId -> sessionId` 映射，避免通知和审批事件丢失。

### 修改文件

- `bridge/src/app-server-client.ts`
- `bridge/src/app-server-runner.ts`
- `bridge/src/app.ts`
- `bridge/src/bridge-runner.ts`
- `bridge/src/mock-runner.ts`
- `bridge/src/types.ts`
- `bridge/tests/app.test.ts`
- `bridge/tests/app-server-runner.test.ts`
- `docs/api.md`

### 验证

- 初次执行 `cd bridge && npm run check` 失败，原因是本地缺少 `node_modules`，`tsc` 不存在。
- 执行 `cd bridge && npm install` 补齐依赖，仅用于本地验证。
- 执行通过：
  - `cd bridge && npm run check`
  - `cd bridge && npm test`

### Android 同步影响

- `/api/session/:id/approve` 现在需要可选请求体：
  - `requestId`
  - `decision`
- 如果 Android 只做“批准当前唯一待审批项”，可以继续只调用同一路径并发送空体或 `{ "decision": "approve" }`。
- 如果 Android 要支持拒绝或同会话多待审批项，需要同步传 `decision`，并在多待审批场景传 `requestId`。

## 追加记录：历史线程继续对话时 thread not found

- 时间：2026-05-19
- 问题现象：
  - Android 新建线程可以多轮继续。
  - 进入之前已经存在的线程后，详情页可打开，但再次发送消息会返回：
    - `HTTP 502`
    - `turn-start-failed`
    - `thread not found: <threadId>`

### 根因判断

- bridge 对历史线程的“查看”和“继续对话”走了两套不完整流程：
  - 查看详情时可以通过 `thread/read` 读取历史线程内容。
  - 但真正发送输入时，runner 直接对旧 `threadId` 调 `turn/start`，没有先做 `thread/resume`。
- 根据已提交的 app-server 协议类型，`thread/resume` 才是“按 thread_id 从磁盘加载并恢复线程”的正式入口。
- 所以旧线程会出现：
  - `thread/read` 能读
  - `turn/start` 却报 `thread not found`

### 本轮修复

- 文件：
  - `bridge/src/app-server-runner.ts`
  - `bridge/tests/app-server-runner.test.ts`
- 修改内容：
  - 历史线程 attach 时优先尝试 `thread/resume`
  - 对 `turn/start` 增加一次“缺线程自动恢复并重试”的兜底逻辑：
    - 第一次 `turn/start`
    - 若报 `thread not found`
    - 自动执行 `thread/resume`
    - 然后再次 `turn/start`
- 这次修复不需要改 Android，现有 APK/客户端请求路径可直接受益。

### 本轮验证

- `cd bridge`
- `npm run check`
- `npm test`
- 结果：
  - 通过
  - 当前 bridge 测试为 5 个测试文件、17 个用例全部通过
- 新增测试覆盖：
  - 历史线程首次 `turn/start` 报 `thread not found`
  - runner 自动 `thread/resume`
  - 随后重试 `turn/start` 成功

## 追加记录：长审批内容遮挡按钮

- 时间：2026-05-19
- 问题现象：
  - 待审批卡片里的请求内容过长时，会把底部审批按钮整体挤出可见区域。
  - 卡片本身不能下滑，用户会卡在“等待审批”状态，无法继续操作。

### 根因判断

- `SessionDetailScreen` 里的 `ApprovalActionCard` 之前使用普通 `Column` 直接顺序铺开：
  - 方法名
  - `paramsSummary`
  - 4 个审批按钮
- 当 `paramsSummary` 很长时，会无限占用垂直空间，按钮虽然仍在布局里，但已经被挤出屏幕下方。
- 卡片自身没有独立滚动区域，所以用户无法把按钮滑出来。

### 本轮修复

- 文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
- 修改内容：
  - 将审批卡片上半部分改成独立可滚区域，限制最大高度。
  - 审批按钮固定保留在卡片底部，不再被长文案顶出可见区域。
  - 4 个审批按钮统一改为 `fillMaxWidth()`，提升小屏可点击性。
  - 补充新的 `testTag`，便于后续自动化定位审批摘要区。
- 回归验证强化：
  - instrumentation 的回放宿主改成超长审批文案。
  - 用例继续要求“批准”按钮可见且可点击，覆盖这次回归点。

### 本轮验证

- Android 单元测试：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- Android 模拟器 instrumentation：
  - `cd android`
  - `.\gradlew.bat connectedDebugAndroidTest`
  - 结果：通过
- Android debug 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：最终通过
  - 说明：第一次构建曾出现一次 `dexBuilderDebug/desugar_graph` 临时目录竞态，重跑后恢复正常，判断为 Gradle 构建环境偶发问题，不是代码回归。

## 追加记录：审批中发送消息与重载状态不刷新

- 时间：2026-05-19
- 用户反馈：
  - 审批卡住时又发送了一条消息，这条消息应该进入排队，但当前看不到，也没有后续自动发送。
  - 重新载入线程时，状态有时不会按真实线程刷新，明明已经停止，界面还显示“进行中”。

### 根因判断

- Android 端：
  - `sendInput()` 之前没有“当前轮仍在运行/等待审批”的专门分支。
  - 所以审批中再次发消息时，不会形成可见队列，也不会在当前轮结束后自动补发。
- bridge 端：
  - `buildSessionViewFromThread()` 会在本地 `session.status != idle` 时优先沿用本地状态。
  - 当本地还保留旧的 `running`，但 `thread/read` 返回的真实线程状态已经是 `inactive` 时，旧状态会把真实状态覆盖掉。

### 本轮修复

- Android：
  - 文件：
    - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
    - `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - 修改内容：
    - 当前轮处于 `running / awaiting_approval` 时，再次发送的消息改为本地排队。
    - 详情页新增“排队中的消息”卡片，明确显示待发送内容和数量。
    - 当当前轮回到 `idle` 后，自动按顺序补发第一条排队消息。
    - 排队消息真正发出前，不会伪装成已经进入 transcript。
- bridge：
  - 文件：
    - `bridge/src/session-view.ts`
    - `bridge/tests/session-view.test.ts`
  - 修改内容：
    - 线程详情状态改为优先信任 `thread/read` 中更新更晚的线程状态。
    - 防止旧的本地 `running` 把已经停止的真实线程覆盖掉。

### 本轮新增测试覆盖

- bridge：
  - 当 `thread.updatedAt` 晚于本地 session 时，状态应以真实线程状态为准。
- Android：
  - 审批中再次发送消息会进入本地队列。
  - 当前轮回到 `idle` 后会自动补发队列中的消息。
  - 排队消息发送前不会提前混入 transcript，发送后才写入对话。

### 本轮验证

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过，5 个测试文件、18 个用例全部通过
- Android：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat connectedDebugAndroidTest`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过
