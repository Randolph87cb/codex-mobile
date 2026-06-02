# codex-mobile code-review-graph 接入

- 日期：2026-06-02
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
  - `bridge/`
  - `android/`
  - `scripts/`
  - `AI工作记录/`
- 相关 skill：`openai-docs`
- 标签：
  - `code-review-graph`
  - `mcp`
  - `codex`
  - `windows`
  - `code-intelligence`

## 本次目标

- 在当前 Windows + Codex 环境中安装 `code-review-graph`。
- 以最小侵入方式接入当前 `codex-mobile` 项目，不改仓库业务代码和项目规则文件。
- 完成实际建图与可用性验证。

## 执行结果

- 已通过 `pip install code-review-graph` 安装 `code-review-graph 2.3.5`。
- 已备份用户级 Codex 配置：`C:\Users\Administrator\.codex\config.toml.bak-20260602-163324`。
- 未使用 `code-review-graph install` 的默认写仓库模式；改为手工追加用户级 MCP 配置，避免它顺手改仓库 `.gitignore`、`AGENTS.md`、hooks 或 skills。
- 已在 `C:\Users\Administrator\.codex\config.toml` 中新增 `mcp_servers.code_review_graph`，固定仓库为 `D:\workspace\codex-mobile`。
- 图数据目录改为仓库已忽略的本地资产目录：`D:\workspace\codex-mobile\.tmp\code-review-graph`。

## 验证结果

- `codex mcp list`
  - 结果：已识别 `code_review_graph` MCP，状态为 `enabled`
- `codex mcp get code_review_graph`
  - 结果：确认以 stdio 方式启动，命令为 `code-review-graph.exe serve --repo D:\workspace\codex-mobile`
- `code-review-graph build`
  - 结果：完成全量建图，扫描 `590` 个文件
- `code-review-graph status`
  - 结果：
    - `Nodes: 1762`
    - `Edges: 15969`
    - `Files: 590`
    - `Languages: kotlin, bash, typescript, powershell`
- `code-review-graph detect-changes --brief`
  - 结果：可正常返回变更分析与 Token Savings 面板

## 当前接入方式

- 用户级 Codex MCP 配置：
  - `command = C:\Users\Administrator\AppData\Local\Programs\Python\Python310\Scripts\code-review-graph.exe`
  - `args = ["serve", "--repo", "D:\workspace\codex-mobile"]`
  - `env.CRG_DATA_DIR = D:\workspace\codex-mobile\.tmp\code-review-graph`
- 当前方案特点：
  - 不改项目级 `.codex/config.toml`
  - 不启用自动 instructions 注入
  - 不启用自动 hooks
  - 不把图数据库落到仓库根目录

## 风险与注意事项

- 当前安装走的是现有 Python 3.10 环境，不是隔离虚拟环境。
- `pip install` 过程中出现了依赖冲突提示：
  - `fastapi` 与 `starlette 1.2.1` 不兼容
  - `gradio-client` 与 `websockets 16.0` 不兼容
- 这不影响本次 `code-review-graph` 使用，但说明同一 Python 环境中的其他工具后续可能受影响；如果后面出现 Python 本地工具异常，优先考虑改成 `pipx` 或独立虚拟环境重装。

## 后续建议

- 先在当前形态下试用一段时间，只把它当本地分析工具和 Codex MCP。
- 如果确认高频使用，再考虑：
  - 是否提交项目级 `.codex/config.toml`
  - 是否增加 `scripts/code-review-graph-refresh.ps1`
  - 是否在 `AGENTS.md` 中补充“影响面分析优先用 graph”的协作约定

## 2026-06-02 架构分析补充

- 使用方式：
  - `code-review-graph build`
  - `code-review-graph status`
  - `code-review-graph wiki`
- 当前图谱结果：
  - `590` files
  - `1762` nodes
  - `15969` edges
  - 识别语言：`kotlin / typescript / powershell / bash`
- 图谱自动聚类与手工阅读结果基本一致，当前仓库可归纳为三大主区：
  - `bridge/src`：Windows host 侧 Fastify bridge + runner 适配层
  - `android/.../data`：Android 数据访问与 bridge 协议消费层
  - `android/.../ui`：Compose UI 与 transcript 展示层

### 当前实际运行架构

- Android 端并不是直接操作多个分散 service，而是以 `AppViewModel` 为中心，统一驱动：
  - 连接状态
  - 会话列表
  - 会话详情
  - 实时流
  - 图片/视频上传
  - 归档、审批、goal、interrupt
- Android 数据层通过 `CodexDataProvider` 抽象桥接：
  - `CodexDataProvider : BridgeApi + SessionRepository`
  - 真实实现是 `RealBridgeDataProvider`
  - 还保留 `FakeCodexDataProvider` / `FallbackCodexDataProvider`
- bridge 侧不是简单转发器，而是一个“协议整形 + 会话映射 + 安全边界 + 附件落盘 + 事件转发”层：
  - HTTP / WebSocket API 暴露给 Android
  - `SessionStore` 维护当前 attach 的本地会话状态
  - `AttachmentStore` 维护暂存附件与正式保存路径映射
  - `security.ts` 负责 token 鉴权与 cwd 白名单
  - `BridgeRunner` 抽象 runner；默认支持 `mock` 与 `app-server`
- 真正的上游执行核心仍是本机 `codex app-server`：
  - bridge 通过 `AppServerClient` 用 stdio JSON-RPC 通信
  - `AppServerRunner` 把 thread / turn / approval / goal / quota / realtime event 转成移动端稳定协议

### 架构上的关键结论

- 当前项目的“真实核心边界”不是 `Android -> Codex CLI`，而是：
  - `Android -> bridge API -> AppServerRunner -> codex app-server`
- bridge 状态来源是“混合态”：
  - 当前 attach 的活跃线程状态主要在内存 `SessionStore`
  - 历史线程详情、会话列表、quota、goal 等能力主要来自上游 `app-server`
- Android 客户端当前做了明显的“受管策略收口”：
  - `approvalMode` 在本地被统一拉到 `auto`
  - `sandboxMode` 在本地被统一拉到 `danger-full-access`
  - 即使 bridge / upstream 还有更通用能力，移动端默认走更少中断的轻客户端策略
- 媒体链路是两段式：
  - 先上传到 bridge 暂存目录
  - 如果有真实 `sessionId/threadId`，再保存到会话 `cwd/mobile_uploads/`
- 实时流已经不是纯文本流：
  - bridge 会把 assistant delta、activity、tool request/result、goal、lifecycle 等事件结构化推给 Android
  - Android 再把这些结构化事件合并为详情页中的 transcript / 执行过程 / 顶部状态

## 2026-06-02 bridge 模块化重构

- 目标：
  - 只重构 `bridge` 层
  - 将过胖的 `bridge/src/app.ts` 收敛为装配入口
  - 保持 Android 现有调用方式、HTTP API、WebSocket 事件语义、附件链路和上游 `codex app-server` 契约不变

### 本次改动

- 新增 `bridge/src/app-context.ts`
  - 统一内部依赖容器类型
  - 定义 `BridgeLifecycleController`
  - 定义内部服务错误类型 `BridgeServiceError`
- 新增 `bridge/src/lifecycle-service.ts`
  - 收口 drain/restarting 状态
  - 收口 session websocket 跟踪
  - 提供 `bridge.lifecycle` 事件构造
- 新增 `bridge/src/attachment-service.ts`
  - 承接图片/视频上传
  - 承接附件暂存与会话目录保存
  - 承接本地文件访问授权判断
  - 承接输入附件引用解析
- 新增 `bridge/src/session-service.ts`
  - 承接会话创建、配置、标题、goal、archive、interrupt、approve、input 提交
  - 保留原有错误码和错误结构映射
- 新增 `bridge/src/routes.ts`
  - 按能力分组注册路由：
    - health + internal lifecycle
    - account quota
    - attachments + file/image access
    - session CRUD/config/title
    - session goal/archive/control
    - session realtime websocket
- 重写 `bridge/src/app.ts`
  - 仅保留：
    - Fastify 初始化
    - multipart / websocket 注册
    - 鉴权 hook
    - body-too-large 错误处理
    - 依赖组装
    - `registerBridgeRoutes(...)`

### 兼容性结果

- 保留了现有视频上传相关改动，没有回退：
  - `POST /api/attachment/video`
  - 输入附件支持 `image` / `video`
  - 非图片附件仍会注入 `bridge-file://...` markdown 链接给 runner
- 保持现有外部 API 路径和状态码不变：
  - `/api/sessions`
  - `/api/session/:id`
  - `/api/session/:id/input`
  - `/api/session/:id/interrupt`
  - `/api/session/:id/approve`
  - `/api/session/:id/goal`
  - `/api/attachment/image`
  - `/api/attachment/video`
  - `/api/image/file`
  - `/api/file/download`
  - `/api/session/:id/ws`
- `drain` 后写接口仍返回 `503`
- `quota` 在 `drain` 状态下仍可读
- WebSocket 在 bridge drain 后仍会收到 `bridge.lifecycle`

### 验证结果

- `cd bridge && npm run check`
  - 结果：通过
- `cd bridge && npm test`
  - 结果：通过
  - 汇总：`5` 个测试文件，`74` 个测试全部通过

### 当前结论

- 本次重构完成后，bridge 的一级职责边界已经明显清晰：
  - `app.ts`：装配
  - `routes.ts`：HTTP / WS 暴露
  - `session-service.ts`：会话业务
  - `attachment-service.ts`：附件与本地文件访问
  - `lifecycle-service.ts`：生命周期与 socket 广播
- 这轮没有改 Android，也没有调整 `AppServerRunner` 的外部行为。
- 后续如果继续做第二阶段重构，优先级仍然是：
  - Android `AppViewModel`
  - Android `RealBridgeDataProvider`

## 2026-06-02 补充：视频附件功能补提交

- 背景：
  - bridge 重构提交完成后，仓库里仍残留一组未提交的“视频附件第一阶段实现”改动。
  - 已确认这组改动与本次 bridge 模块化重构不同，属于独立功能面。
- 归属文件：
  - Android：`AppViewModel`、`BridgeApi`、`RealBridgeDataProvider`、`CodexMobileApp`、`SessionDraftScreen`、`SessionDetailScreen`、`VideoAttachmentPreparer`、debug showcase / replay / tests
  - bridge：`attachment-store`、`types`、`app-server-runner`、`mock-runner`、对应测试
  - 文档：`README.md`、`docs/api.md`
  - 记录：`AI工作记录/records/2026/05/2026-05-31-codex-mobile-视频上传方案评估.md`
- 排除项：
  - `AI工作记录/records/2026/05/2026-05-23-codex-mobile-安装包路径定位.md` 不属于视频功能，未纳入本次提交
  - `spritesheet.webp` 与 `spritesheet (1).webp` 未纳入本次提交
- 验证：
  - `cd bridge && npm run check`：通过
  - `cd bridge && npm test`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android && .\gradlew.bat testDebugUnitTest`：通过
