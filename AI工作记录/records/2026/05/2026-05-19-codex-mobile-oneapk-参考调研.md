# codex-mobile-oneapk 参考调研

- 日期：2026-05-19
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
  - `.tmp/codex-mobile-oneapk-ref`
  - `android/`
  - `bridge/`
  - `docs/`
- 相关 skill：
  - `record-and-reflect-review`
  - `delegation-orchestrator`
- 标签：
  - 对标调研
  - Android
  - bridge
  - 产品化

## 目标

分析 `aeewws/codex-mobile-oneapk` 当前实现，提炼可借鉴做法，重点记录它的实现方式，供 `codex-mobile` 后续设计参考。

## 对方项目路线判断

`codex-mobile-oneapk` 走的是“单 APK + Android 本地 runtime + App 内管理 app-server”的路线，不依赖单独的桌面 bridge，也不把 Android 端当纯远程客户端。

核心链路：

1. 构建时把 arm64 runtime 注入 APK 资源。
2. 首次启动时解压到 App 私有目录。
3. App 私有目录下维护 `CODEX_HOME`、认证文件、日志、session index。
4. App 内发起登录或设备码授权。
5. App 自己探测、启动、重启本地 `codex app-server`。
6. 前端 UI 再通过本地 websocket 直接和 `app-server` 交互。

## 对方实现拆解

### 1. Runtime 打包与版本化

- `app/build.gradle.kts` 提供 `codexMobile.runtime.archive` / `CODEX_MOBILE_RUNTIME_ARCHIVE` 输入。
- 支持目录、`.zip`、`.tar.gz`、`.tgz` 作为 runtime 源。
- 构建阶段统一重新打成 `runtime/codex-runtime-arm64.zip` 再放入 APK assets。
- 用 `BuildConfig.CODEX_RUNTIME_PACKAGED`、`CODEX_RUNTIME_VERSION`、`CODEX_RUNTIME_ASSET` 驱动运行时加载逻辑。
- 还做了 `legacy` / `oss` 两个 flavor，分别对应兼容安装和开源分发。

结论：它把“运行时是否存在、版本是什么、如何注入”做成了明确的构建输入，而不是临时脚本。

### 2. App 私有 runtime 管理层

`EmbeddedRuntimeManager.kt` 负责：

- 判断 ABI 是否为 `arm64-v8a`。
- 检查 runtime 是否已经打包进 APK。
- 首次解压 runtime 到私有目录。
- 维护独立的 `codex-home` 与 `.codex` 目录。
- 为 `auth.json`、日志、pid、session index、附件缓存等提供固定路径。
- 尝试 root 镜像运行时到 `/data/local/tmp/...`，在 root 可用时改善执行稳定性。

结论：它把路径、文件、运行环境变量和 root fallback 全部收口到一个 manager 里，避免 UI 和业务层散落文件路径常量。

### 3. App 内认证管理

`CodexAuthManager.kt` 负责：

- 直接读取 `auth.json` 推断当前登录态。
- 启动浏览器 OAuth 登录流程。
- 启动设备码登录流程。
- 解析登录输出里的浏览器地址、设备码和结果。
- 处理登录中进程的生命周期与日志。
- 本地登出时删除认证文件和登录日志。

结论：它把“登录”当成产品内一等能力，而不是外部前置步骤。

### 4. app-server 生命周期管理

`CodexBackendManager.kt` 负责：

- 用固定端口探测本地 `app-server` 是否已监听。
- 按需启动、停止、重启 `codex app-server`。
- 维护 backend log 和 pid 文件。
- 在失败时回传日志 tail，提供最小诊断信息。

`BackendForegroundService.kt` 负责：

- 在 Android 前台服务里做保活。
- 周期性检查 backend 是否掉线。
- 已登录且 runtime 就绪但 backend 不在时自动拉起。
- 联动 keepalive hardening。

结论：它不只是“能启动”，而是已经考虑了移动端常见的恢复和保活问题。

### 5. 控制器与 UI 状态收口

`CodexRuntimeController.kt` 统一向 UI 暴露：

- runtime 状态
- 登录状态
- backend 状态
- keepalive 状态
- 默认项目根目录建议
- 本地线程索引和历史清理

结论：它把 UI 需要的状态聚合成“控制面响应”，减少 Compose 层直连多个底层 manager。

### 6. 移动端功能面

从 README 和 `CodexMobileViewModel.kt` 看，对方已经把下面这些当成移动端核心能力：

- 历史会话、归档、恢复、重命名、删除
- 模型、reasoning、permission、fast mode 切换
- 长线程恢复
- 附件入口
- 登录和设备码在 App 内闭环
- 中文优先的移动端提示文案

注意：仓库里仍保留了早期 WebView/`assets/web` 桥接遗留，但主线 UI 已经转为 Compose。

### 7. 工程化外壳

对方仓库虽是原型，但已经补了：

- GitHub Actions Android CI
- issue / PR template
- Dependabot
- CODEOWNERS
- SECURITY.md
- setup / roadmap 文档

测试面目前不算强，仓库内单元测试仍很薄，主要优势在“产品边界说明”和“分发构建流程”更完整。

## 和我们当前架构的差异

我们当前项目是：

`Android client -> Windows bridge -> codex app-server`

对方项目是：

`Android app -> embedded runtime -> local codex app-server`

因此以下能力不能直接照搬：

- APK 内置 runtime
- App 私有 `CODEX_HOME`
- root 保活与 `/data/local/tmp` 镜像执行
- Android 本地 app-server 探测与后台守护
- 设备侧 runtime 构建注入流程

这些都属于它那条“单机本地运行”路线的特定成本。

## 对我们可直接借鉴的部分

### 优先级高

1. 把状态面做成明确的“控制面摘要”
   - 我们现在 `bridge /health` 只返回很薄的健康信息。
   - 可以新增更面向 Android 的 bridge 诊断接口，例如：
     - app-server 是否已启动
     - 当前 runner 模式
     - 是否已登录
     - 最近错误摘要
     - 当前审批是否阻塞
     - 安全配置是否开启
   - 这会明显降低 Android 侧对多个接口和隐含状态的拼接成本。

2. 把“恢复”和“诊断”当一等能力
   - 对方的 backend manager 会直接暴露日志 tail 和失败 detail。
   - 我们 bridge 已经有 app-server runner，但 Android 目前仍以会话接口为主，诊断面偏薄。
   - 可以补：
     - `GET /api/runtime-status` 或类似聚合接口
     - 最近 app-server 错误摘要
     - 当前是否存在待审批请求
     - 当前活动 turn / thread 状态

3. README / docs 从“脚手架说明”升级到“真实使用路径”
   - 对方 README 很明确地区分“为什么做、怎么装、当前能力、边界、限制、路线图”。
   - 我们 README 目前更像开发进展说明，适合自己看，不太适合作为对外参考文档。
   - 可补：
     - 一张稳定架构图
     - Android 真机连接路径
     - token / 白名单 / 审批链路当前支持矩阵
     - 已实现 / 未实现清单

4. Android 侧把连接恢复和会话恢复做成显式状态机
   - 对方 ViewModel 对连接、登录、恢复、队列输入、实时流有比较完整的状态收口。
   - 我们虽然已接 WebSocket 流，但整体仍偏 MVP。
   - 后续可整理成：
     - bridge 连接状态
     - 会话流连接状态
     - 后端阻塞状态
     - 审批等待状态
     - 可重试动作

### 优先级中

5. 默认工作目录建议与目录说明
   - 对方会给用户建议项目根目录入口。
   - 我们可在 Android 创建会话页提供：
     - 最近工作目录
     - bridge 返回的允许目录
     - 示例工作区
   - 这样比手填 `cwd` 更适合移动端。

6. 历史会话“产品化”
   - 对方把会话历史、归档、恢复、重命名、删除都当作主流程。
   - 我们 bridge 已经能列历史，但 Android 侧可以继续加强：
     - 最近活跃会话优先
     - 错误态会话标记
     - 可恢复 / 运行中 / 等审批状态标签

7. 支持矩阵和限制说明文档
   - 对方非常明确写了 ABI、root、runtime 注入、浏览器兼容性等限制。
   - 我们也应明确写出：
     - 当前只支持 Windows host
     - Android 当前仅文本为主
     - 审批支持范围
     - 实时流覆盖范围
     - 尚不支持附件 / 图片 / 语音

### 优先级低

8. 仓库治理外壳
   - 我们当前缺少顶层 `.github` 工作流和模板。
   - 等 bridge / Android 主链路再稳一点后，可以补：
     - CI
     - issue / PR template
     - SECURITY.md
     - CODEOWNERS

## 目前最值得参考的实现方式

如果只保留三条最值得参考的“方法论”，是：

1. 用 manager / controller 明确分层，而不是让 UI 直接拼底层逻辑。
2. 把失败恢复、日志摘要和可诊断性做成产品能力，而不是只在开发期看控制台。
3. 把 README / setup / roadmap 当成产品边界的一部分持续维护。

## 按功能拆解 app-server 用法

### 1. 对话

对话主链路是直接走 `app-server` 的 JSON-RPC：

1. `thread/start`
   - 新建线程时传：
     - `model`
     - `modelProvider = "openai"`
     - `cwd`
     - `approvalPolicy`
     - `sandbox`
     - `persistExtendedHistory = true`
     - 可选 `serviceTier`
2. `thread/resume`
   - 重新进入旧线程时恢复写入上下文。
3. `turn/start`
   - 发送本轮输入时传：
     - `threadId`
     - `input`
     - `model`
     - `modelReasoningEffort`
     - `approvalPolicy`
     - `sandboxPolicy`
     - `cwd`
     - 可选 `serviceTier`
4. `thread/read`
   - 拉完整线程和 turns，带 `includeTurns = true`
5. `thread/list`
   - 拉历史线程列表
6. `turn/interrupt`
   - 中断当前输出
7. `turn/steer`
   - “继续上一段输出”时发 steer

事件消费主要靠 websocket 通知：

- `thread/started`
- `turn/started`
- `turn/completed`
- `item/started`
- `item/completed`
- `item/agentMessage/delta`
- `item/commandExecution/outputDelta`
- `item/fileChange/outputDelta`
- `turn/plan/updated`

结论：它的聊天不是自己定义桥接协议，而是 App 直接消费 `app-server` 的 thread / turn / item 语义。

### 2. 审批

审批是通过 `app-server` 发来的 server request 做的，不是 Android 自己推断。

当前实际接住的只有两类：

- `item/commandExecution/requestApproval`
- `item/fileChange/requestApproval`

处理方式：

- `FULL_ACCESS`：直接 `respond(id, { decision: "accept" })`
- `DEFAULT_DENY`：直接 `respond(id, { decision: "decline" })`
- `ASK_EACH_TIME`：把请求转成 UI 上的 `ApprovalRequestUi`，由用户点同意或拒绝，再 `respond(...)`

限制：

- `item/permissions/requestApproval` 当前没有专门处理。
- 未适配的 server request 走默认 `respond(id, {})`。

结论：它已经把“命令审批”和“文件改动审批”接通了，但审批覆盖面还不是完整的 app-server request 集合。

### 3. 传模型参数

模型参数传递比较直接，核心字段包括：

- `model`
- `modelProvider = "openai"`
- `modelReasoningEffort`
- `approvalPolicy`
- `sandbox` 或 `sandboxPolicy`
- `serviceTier`

## 本轮落地

### 已实现

1. 删除 `flex`
   - bridge `ServiceTier` 收敛为 `default | fast`
   - Android 设置页和会话详情页只保留“普通 / 快速”
   - `AppViewModel`、`AppSettingsStore`、测试数据全部去掉 `flex`

2. 接通图片发送链路
   - bridge 新增 `POST /api/attachment/image`
   - bridge 新增附件暂存层 `AttachmentStore`
   - `/api/session/:id/input` 支持 `text + attachments`
   - `app-server` 输入组装改为把图片附件转换成 `localImage`
   - Android 新增图片选择、压缩、转 JPEG/base64、上传 bridge、发送附件引用
   - 会话输入区新增“图片”按钮和待发送图片卡片

3. 补齐兼容实现
   - `ReplayHarnessActivity`、`FakeCodexDataProvider`、`FallbackCodexDataProvider` 同步适配新接口
   - bridge runner / app / mock 相关测试同步更新
   - Android `AppViewModelTest` 补了一条“先上传图片、再发送输入”的单测

### 关键实现方式

- 模型速度：
  - `default` 不传 `serviceTier`
  - `fast` 透传 `serviceTier = "fast"`
- 图片发送：
  1. Android 选图
  2. 本地缩放并压成 JPEG
  3. base64 上传到 bridge
  4. bridge 落到 Windows 临时目录
  5. 发送消息时只传附件 `id`
  6. bridge 转成 `localImage path` 给 `app-server`

### 验证

- 已通过
  - `cd bridge && npm run check`
  - `cd bridge && npm test`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`

- 未通过
  - `cd android && .\gradlew.bat testDebugUnitTest`

### 单测失败说明

`testDebugUnitTest` 当前不是因为图片链路运行时报错，而是在 Kotlin unit test 源集里暴露出一批既有问题：

- 多个测试直接依赖主源码里的顶层函数或 UI 解析函数
- 测试编译阶段对这些符号的解析本身就不稳定
- 这轮新增的 `AppViewModelTest` 已同步到新接口，但整个 unit test 源集仍未恢复到可稳定通过状态

后续如果要把 Android 单测恢复为绿灯，建议单独开一轮处理：

1. 先修 `src/test` 对主源码顶层函数的可见性和引用方式
2. 再清理 `SessionListGroupingTest` / `TranscriptBubbleTest` 这类 UI 解析测试
3. 最后再补图片发送相关更多覆盖

其中：

- `model/list` 用来拉可选模型列表。
- `supportedReasoningLevels` / `reasoningEfforts` 用来生成每个模型支持的 reasoning 选项。
- `Fast mode` 本质上是把 `serviceTier` 设成 `fast`。
- 权限模式同时映射到：
  - `approvalPolicy`
  - `sandboxCliValue`
  - `sandboxPolicyType`

权限模式映射：

- `DEFAULT_DENY`
  - `approvalPolicy = "never"`
  - `sandbox = "read-only"`
- `ASK_EACH_TIME`
  - `approvalPolicy = "untrusted"`
  - `sandbox = "workspace-write"`
- `FULL_ACCESS`
  - `approvalPolicy = "never"`
  - `sandbox = "danger-full-access"`

结论：它不是只传 model 名，而是把模型、reasoning、审批策略、sandbox、service tier 一起作为每次线程创建和 turn 启动的参数面。

### 4. 传图片和接收图片

#### 传图片

图片发送是直接利用 `app-server` 输入块里的 `localImage`：

1. App 先读系统图片或相机 bitmap。
2. 本地缩放并压成 JPEG。
3. 复制到 backend 可读目录。
4. 在 `turn/start` 的 `input` 数组里追加：
   - `{ "type": "localImage", "path": "<backendPath>" }`

如果用户只发图片不写文字，会额外补一句：

- `请查看这个图片附件。`

结论：图片是“原生多模态输入块”，不是 base64，不是 HTTP 上传，也不是先 OCR 再转文本。

#### 传文档

文档不是原生文件输入。

做法是：

1. App 读取 PDF / DOCX / XLSX / 文本。
2. 先在手机端解析出纯文本。
3. 用私有标记把文档元信息和抽取文本拼进普通文本 prompt。

结论：这是 App 自己的适配层，不是 `app-server` 的原生文件附件能力。

#### 接收图片

当前代码里能解析的“附件返回”基本只有用户消息里的 `localImage`。

UI 渲染层对图片的展示来源主要是：

- 自己发送过的图片附件
- 从 user content 里解析出的 `localImage`

没有看到它对以下输出做正式适配：

- `agentMessage` 图片内容块
- `output_image`
- `image_url`
- 其他 agent 生成图片结果

`agentMessage` 当前主要按文本处理。

结论：这个项目“发送图片”已经接通，但“接收模型生成图片”至少从当前代码看还没有完整实现。

## 针对当前项目最值得直接参考的两点

### A. 模型速度档位：快速 / 普通

对方实现并没有做复杂调度，核心就是把 UI 开关映射为 `serviceTier`：

- 普通：
  - 不传 `serviceTier`
  - 或等价为 `default`
- 快速：
  - 传 `serviceTier = "fast"`

具体做法：

- `fastModeEnabled` 只是一个布尔开关。
- `currentServiceTierOrNull()` 在开关打开时返回 `"fast"`，否则返回 `null`。
- `thread/start`
- `thread/resume`
- `turn/start`
  这三处统一在参数里按需追加 `serviceTier`

这说明“快速 / 普通”在它那里不是新的模型名，也不是额外 runner 逻辑，而是一个很薄的参数层。

对照我们当前项目：

- Android 已经有 `serviceTier` 输入和持久化。
- `CreateSessionRequest`、`SessionConfigUpdate`、`SessionDetail`、`SessionSummary` 都已经带 `serviceTier`。
- bridge 里的 `AppServerRunner` 也已经支持把 `serviceTier` 传给 `thread/start` 和 `turn/start`。

因此我们真正可以参考的不是“补能力”，而是“简化交互”：

1. 把当前通用 `serviceTier` 选择收敛成明显的两档：
   - 普通 = `default`
   - 快速 = `fast`
2. 在 UI 上优先做一个“快速模式”开关，而不是让用户理解 `default / fast / flex`
3. `flex` 保留为隐藏或高级配置，不必先暴露给主流程

结论：这块我们已经有底层能力，参考对方的价值主要在于产品交互收敛，而不是底层协议补齐。

### B. 图片发送

对方的图片发送是当前最值得直接借鉴的实现。

主链路：

1. 从系统相册或相机拿到图片。
2. 在 App 侧解码成 bitmap。
3. 缩放到安全尺寸。
4. 压成 JPEG 缓存到 App 私有目录。
5. 再复制一份到 backend 可读目录。
6. 在 `turn/start.input` 里追加：
   - `{ "type": "localImage", "path": "<backendPath>" }`

注意：

- 图片不是作为普通文本提示词内嵌。
- 也不是 base64。
- 也不是 multipart 上传。
- 它依赖的是 `app-server` 能读取本地文件路径这一点。

对照我们当前项目：

- 我们现在是 `Android -> Windows bridge -> Windows app-server`
- Android 和 app-server 不在同一文件系统
- 所以不能直接复用它的 `localImage + 本地 path` 方案

这意味着我们只能参考它的“前半段”，不能照搬“后半段”：

可直接参考的部分：

1. Android 侧附件 UI 入口
2. 读取图片
3. 缩放与压缩
4. 附件元信息结构
5. 把图片和文本合并成一次发送动作

不能直接照搬的部分：

1. `backendPath`
2. `localImage` 直接传 Android 本地绝对路径

我们更适合的改法是：

1. Android 选图后先压缩图片
2. 通过 bridge 新增上传接口把图片传到 Windows 临时目录
3. bridge 返回 Windows 侧安全路径或附件 id
4. bridge 再把它转换成 app-server 可读的 `localImage` 输入块

建议接口方向：

- `POST /api/session/:id/attachment/image`
  - Android 上传压缩后的图片
  - bridge 落盘到受控临时目录
  - 返回 `attachmentId` 和服务端路径
- `POST /api/session/:id/input`
  - 从只发 `text` 扩成：
    - `text`
    - `attachments`
  - 由 bridge 组装为 app-server `turn/start.input`

结论：图片发送要参考对方的“输入块设计”和“移动端前处理”，但由于我们是跨设备链路，真正的落地点应该在 bridge 上传和桥接转换，而不是 Android 直接传 `localImage path`。

## 对我们下一步最现实的改进建议

建议按下面顺序推进：

1. 给 bridge 增加面向 Android 的聚合状态接口。
2. Android 侧把“连接中 / 已连接 / 后端异常 / 等审批 / 可重试”做成显式状态。
3. 更新 README 和 `docs/architecture.md` / `docs/api.md`，加入当前支持矩阵与限制。
4. 再考虑会话历史产品化和更细的错误诊断页。

## 本次检查范围

- 读取了对方仓库 README、setup、roadmap、security、CI。
- 本地克隆到 `.tmp/codex-mobile-oneapk-ref` 做只读代码检查。
- 重点查看了 runtime、auth、backend、foreground service、ViewModel、Gradle 构建注入逻辑。
- 对照了本项目当前的 `README.md`、`docs/architecture.md`、`docs/api.md`、`bridge/src/app.ts`、`bridge/src/app-server-runner.ts`、Android data/viewmodel 层。

## 验证与结果

- 本次只做只读调研，没有改业务代码。
- 没有运行构建或测试。
- 当前仓库存在用户未提交改动，已避免触碰相关业务文件。
