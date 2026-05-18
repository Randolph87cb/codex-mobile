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

## 后续事项

- [ ] 接通 `/api/session/:id/approve` 与 app-server 审批响应
- [ ] 将 Android 详情页从轮询升级为 WebSocket 实时流
- [ ] 为 Android 增加中断、审批、运行状态展示
- [ ] 为 bridge 增加 token 认证与目录白名单
- [ ] 为上述能力补齐 bridge / Android 测试

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
