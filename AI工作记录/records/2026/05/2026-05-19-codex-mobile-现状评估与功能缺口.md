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

## 后续事项

- [ ] 接通 `/api/session/:id/approve` 与 app-server 审批响应
- [ ] 将 Android 详情页从轮询升级为 WebSocket 实时流
- [ ] 为 Android 增加中断、审批、运行状态展示
- [ ] 为 bridge 增加 token 认证与目录白名单
- [ ] 为上述能力补齐 bridge / Android 测试
