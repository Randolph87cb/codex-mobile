# 2026-05-25 codex-mobile 中断与排队状态设计梳理

## 目标

- 讨论会话详情页增加“中断当前对话”能力时，如何避免牺牲现有排队发送能力。
- 梳理“进行中 / 空闲”和“在线 / 空闲”在当前 UI 中语义不一致的问题。

## 现状检查

- Android 详情页输入区当前只有单一 `发送` 动作，入口来自 `SessionDetailScreen.kt` 的 `DetailInputDock`。
- Android 已经支持文本排队发送，排队数据保存在 `AppViewModel.kt` 的 `queuedInputsBySession`，并在会话回到 `idle` 后通过 `flushNextQueuedInputIfIdle` 自动发送下一条。
- Android 当前把会话运行态主要压缩成单个 `status` 字符串，核心值为 `running`、`awaiting_approval`、`idle`、`error`。
- Android 顶部 `StatusStrip` 里的“连接状态”实际展示的是“实时流 / 快照 / 未创建”，并不是 bridge 在线状态本身。
- bridge 已经提供独立中断接口 `POST /api/session/:id/interrupt`，并会发出 `run.interrupted` 事件。
- Android `BridgeApi` 目前还没有 `interruptSession` 接口，UI 也没有“主动中断当前轮次”的独立入口。

## 关键判断

- “发送按钮在运行中直接切换成终止按钮”会让主入口在同一位置承载两种互斥语义：
  - 发送新输入
  - 终止当前运行
- 现有产品又已经有排队发送能力，因此如果主按钮完全变成“终止”，用户就失去了“继续输入并排队”的主路径。
- 这不是单纯按钮文案问题，而是三个状态层级混用：
  - bridge 连接层
  - 会话运行层
  - 输入动作层

## 建议方案

### 1. 拆三层状态，不再混成一个“空闲”

- 连接层：
  - `未连接`
  - `已连接`
  - `实时流已连接`
- 会话运行层：
  - `草稿`
  - `空闲`
  - `执行中`
  - `等待审批`
  - `出错`
  - `已中断（短暂提示态，可回落到空闲）`
- 输入层：
  - `可直接发送`
  - `将进入排队`
  - `图片上传中`
  - `图片上传失败`
  - `可中断当前轮`

### 2. 不建议把“发送”完全替换成“终止”

- 保留主按钮语义为“提交当前输入”。
- 当会话正在运行时：
  - 输入框仍可编辑；
  - 发送主按钮仍然可用，但其结果是“加入排队”；
  - 另外提供一个独立的次级“中断”动作。

### 3. 推荐交互

- 输入区右侧保留圆形主按钮：
  - 空闲时是“发送”
  - 运行中仍可点，但文案/辅助提示明确为“排队发送”
- 在主按钮旁增加一个较小但明确的文本次级按钮或图标按钮：
  - `中断当前轮`
- 运行中时在输入区上方或状态条中显示：
  - `正在执行`
  - `已排队 2 条`
  - `可中断当前轮`

### 4. 状态文案建议

- 顶部第一列不要再叫“连接状态”但显示“实时流 / 快照”：
  - 改成 `同步方式` 或 `会话同步`
- 如果要保留真正的连接语义，单独显示：
  - `bridge：已连接 / 未连接`
- 会话状态单独显示：
  - `会话：空闲 / 执行中 / 待审批 / 出错`

## 后续实现建议

- 若进入实现，优先顺序应为：
  1. Android 新增 `interruptSession` 数据接口并接 bridge `/api/session/:id/interrupt`
  2. AppViewModel 拆分“连接层状态”和“运行层状态”的展示模型
  3. SessionDetailScreen 输入区增加独立“中断当前轮”入口
  4. 调整 StatusStrip 命名与展示，避免“在线 / 空闲”混义
  5. 补 Android 单元测试与 UI 测试

## 本次未执行

- 未修改功能代码。
- 未运行构建或测试。

## 补充

- 用户随后要求先看界面大致样子，计划生成一张 Android 会话详情页概念图，用于确认“发送/排队/中断”与状态条分层展示方案。
- 用户确认按钮位置方向可接受，但补充要求：右侧按钮区不能过宽，避免明显压缩输入框宽度；下一版概念图需把“中断当前轮”收窄为更轻的次级控件。
- 用户进一步确认新版方向可用，并要求再简化为仅保留按钮本体：去掉右侧文字说明，只保留发送按钮和终止按钮。
- 用户要求基于确认后的概念稿，整理一版可实施的修改方案，暂不直接改代码。

## 可实施修改方案

### 方案目标

- 在会话执行中时，保留“继续输入并排队”的能力。
- 增加“中断当前轮”的独立入口，但不挤占输入框主要宽度。
- 解决“在线 / 实时流 / 空闲 / 进行中”混在一起展示的问题。

### 交互方案

- 输入区左侧保持图片按钮。
- 中间保持主输入框，并继续优先保证宽度。
- 右侧动作区：
  - 空闲时仅显示发送按钮。
  - 执行中时显示两个窄按钮：发送按钮、终止按钮。
  - 两个按钮都不带文字，只保留图标。
- 执行中点击发送：
  - 如果当前输入仅文本，则进入排队。
  - 如果包含图片，保持现有限制，不允许在执行中把图片加入排队。
- 点击终止：
  - 调用 bridge `/api/session/:id/interrupt`
  - 当前轮结束后回到空闲
  - 若存在排队消息，则沿用现有逻辑继续自动发送下一条

### 状态展示调整

- 顶部状态条从“连接状态 / 排队消息 / 目标状态”调整为更明确的三层：
  - `bridge`：已连接 / 未连接
  - `同步`：实时流 / 快照 / 未创建
  - `会话`：空闲 / 执行中 / 待审批 / 出错 / 草稿
- “排队消息”不再占顶部固定三栏之一，改为：
  - 有排队时，在转录区顶部显示一张更轻的排队卡片
  - 点击后仍可展开查看完整排队列表

### Android 改动点

- `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - 新增 `interruptSession(sessionId: String)`
- `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - 接 bridge `POST /api/session/:id/interrupt`
- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 新增中断动作入口
  - 发送按钮在 `running / awaiting_approval` 下继续走现有排队逻辑
  - 终止成功后复用已有 `run.interrupted` / `idle` 收尾逻辑
  - 补一层更清晰的展示态，避免把 bridge 连接态和会话运行态混为一个 `statusText`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 调整 `StatusStrip`
  - 调整 `DetailInputDock`
  - 执行中显示两个窄图标按钮；空闲时只显示发送按钮

### bridge 侧

- 现有接口已满足本次需求：
  - `POST /api/session/:id/interrupt`
  - `run.interrupted`
- 因此 bridge 本轮原则上不需要改协议，只需要 Android 接入。

### 测试建议

- Android 单元测试：
  - 执行中点击发送时进入排队
  - 执行中点击终止时调用 interrupt
  - interrupt 后若有排队消息，会在空闲后继续发送下一条
- Android UI / instrumentation：
  - 输入区在空闲态只显示一个发送按钮
  - 输入区在执行态显示发送 + 终止两个窄按钮
  - 状态条文案改为 bridge / 同步 / 会话

### 实施顺序

1. 先接 Android `interruptSession`
2. 再改 `AppViewModel` 的显示态与中断动作
3. 再改 `SessionDetailScreen` 输入区和状态条
4. 最后补测试并跑 Android 构建与单测

## 修正说明

- 用户指出上一版方案错误地弱化了 `目标` 和 `排队` 的可见性，这两类信息不能消失，也不应退化成不明显的次级信息。
- 下一版方案需要同时满足四类信息持续可见：
  - bridge / 同步 / 会话状态
  - 排队
  - 目标
  - 输入区发送 / 终止

## 修正后的展示方案

### 顶部区域改为两层而不是单条替换

- 第一层：运行状态条
  - `bridge`
  - `同步`
  - `会话`
- 第二层：业务状态条
  - `排队`
  - `目标`

### 具体布局

- 第一层继续用当前紧凑指标条样式，显示：
  - `bridge：已连接 / 未连接`
  - `同步：实时流 / 快照 / 未创建`
  - `会话：空闲 / 执行中 / 待审批 / 出错 / 草稿`
- 第二层紧接其下，保留两个可点击状态卡或 chip 组：
  - `排队：无排队 / 2 条`
  - `目标：未设置 / 进行中 / 已暂停 / 已完成`

### 交互要求

- `排队` 保持强可见：
  - 有排队时必须一眼能看到数量
  - 点击仍可展开完整排队列表
- `目标` 保持强可见：
  - 当前状态必须直接可见
  - 点击仍进入当前已有目标管理弹窗
- 不再用“排队卡片替代顶部排队状态”的思路
- 如需展示更多排队详情，可在转录区顶部补充列表卡，但顶部汇总入口必须保留

### 输入区修正版

- 左侧图片按钮不变
- 中间输入框优先保宽
- 右侧动作区保持窄
- 空闲时：
  - 只显示发送按钮
- 执行中时：
  - 显示发送按钮和终止按钮
  - 发送按钮继续负责“提交并进入排队”
  - 终止按钮只负责中断当前轮

### Android UI 实施修正版

- `StatusStrip` 不做“排队/目标移除”，而是拆成：
  - 一行 `连接/同步/会话`
  - 一行 `排队/目标`
- `QueuedInputsDialog`、`GoalManagerDialog` 继续保留，入口位置调整但功能不删

## 基于截图确认的实施方向

- 用户确认了基于现有截图改造的视觉方向：
  - 顶部状态区改为两行
  - 第一行显示 `bridge状态 / 同步方式 / 会话状态`
  - 第二行继续保留 `排队消息 / 目标状态`
  - 底部输入区保留宽输入框
  - 右侧改为窄版双图标按钮：发送 / 终止
- 用户随后要求输出一版修改方案，暂不直接开始代码实现。

## 最终实施方案

### 范围

- 仅修改 Android 端会话详情页状态展示与输入区交互。
- bridge 协议本轮不新增字段，只接已有 interrupt 能力。

### 交互目标

- 运行中时，用户既能继续输入并加入排队，也能主动中断当前轮。
- `排队消息` 和 `目标状态` 在详情页顶部持续可见，不退化为隐藏入口。
- 输入框宽度优先，不被右侧动作区明显压缩。

### 页面结构

- 顶部栏：保持现有返回、标题、刷新、更多设置。
- 状态区：
  - 第一行：`bridge状态 / 同步方式 / 会话状态`
  - 第二行：`排队消息 / 目标状态`
- 转录区：保持现有消息流与审批卡片。
- 输入区：
  - 左侧图片按钮
  - 中间输入框
  - 右侧窄版发送按钮
  - 运行中额外显示窄版终止按钮

### 行为规则

- 空闲态：
  - 发送按钮直接发送
  - 不显示终止按钮
- 运行态：
  - 发送按钮仍可点击，文本进入排队
  - 终止按钮调用中断当前轮
  - 图片仍不支持在运行中排队
- 中断完成后：
  - 会话回到空闲
  - 若排队中还有消息，继续复用现有自动发送下一条逻辑

### 文件修改建议

- `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - 增加 `interruptSession(sessionId: String)`
- `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - 接 `/api/session/:id/interrupt`
- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 新增 `interruptSelectedSession()` 或同等动作入口
  - 复用已有排队逻辑
  - 明确区分 bridge 连接态、同步态、会话运行态的展示数据
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 重构 `StatusStrip`
  - 重构 `DetailInputDock`
  - 运行态显示双按钮，空闲态显示单按钮

### 组件拆分建议

- `StatusStrip`
  - 拆成 `PrimaryStatusStrip` 和 `SecondaryStatusStrip`
  - 或保留一个 composable，内部改成两行布局
- `DetailInputDock`
  - 增加 `showInterruptButton`
  - 增加 `onInterrupt`
  - 按运行态切换右侧按钮数量

### 测试

- ViewModel：
  - 运行中发送文本进入排队
  - 点击终止调用 interrupt
  - interrupt 后自动尝试发送下一条排队消息
- UI：
  - 空闲态只显示发送按钮
  - 运行态显示发送 + 终止
  - 顶部两行状态区仍显示排队和目标入口

## 本次实现

- 已按确认方案直接修改 Android：
  - 顶部状态区改为两行：
    - 第一行 `bridge 状态 / 同步方式 / 会话状态`
    - 第二行 `排队消息 / 目标状态`
  - 底部输入区改为窄版图标动作区：
    - 空闲态只显示发送
    - 运行态显示发送 + 终止
- 已在 Android 数据层接入 bridge 现有中断接口：
  - `BridgeApi.kt`
  - `RealBridgeDataProvider.kt`
  - `FallbackCodexDataProvider.kt`
  - `FakeCodexDataProvider.kt`
  - `ReplayHarnessActivity.kt` 的回放 provider
- 已在 `AppViewModel.kt` 增加：
  - `interruptSelectedSession()`
  - `SessionRealtimeUiState.isInterrupting`
  - 中断请求发出、实时流恢复、任务结束和 `run.interrupted` 的状态收尾
- 已在 `SessionDetailScreen.kt` 增加：
  - `connectionState`
  - `onInterrupt`
  - 窄版终止按钮
  - 两行 `StatusStrip`
- 已在 `TestTags.kt` 增加 `SessionDetailInterruptButton`
- 已补单测：
  - `interruptSelectedSessionCallsBridgeInterruptApi`

## 验证结果

- 已执行 Android 单测：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- 已执行 Android debug 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过

## 额外处理

- 由于 `SessionDetailScreen` 新增了 `connectionState` 和 `onInterrupt` 参数，debug 源集里的 `SessionDetailShowcaseActivity.kt` 也同步补了展示态占位参数，避免 debug 编译失败。

## 文档更新

- 已按用户要求同步更新正式文档：
  - `README.md`
  - `docs/api.md`
- `README.md` 已补充：
  - 会话详情页独立终止按钮
  - 运行中发送进入排队
  - 顶部两行状态区的可见结构
- `docs/api.md` 已补充：
  - Android 对 `POST /api/session/:id/interrupt` 的实际使用语义
  - `bridge.lifecycle` 已实现事件类型
  - Android 当前实际依赖链路包含 `interrupt`
- 本轮仅更新文档，未额外重跑构建或测试。

## 线程列表状态完整版实现

- 用户随后提出：线程列表中应明确显示会话状态，至少区分 `进行中 / 空闲`，并希望实现“完整版”，而不是只改 badge 文案。
- 最终实现范围包含两部分：
  - 线程列表 badge 文案与状态映射统一为中文：`进行中 / 待审批 / 出错 / 草稿 / 空闲`
  - 列表状态刷新链路增强：
    - 进入线程列表页时主动刷新当前筛选的会话摘要
    - 详情页实时流事件会在详情页活跃时把当前会话状态本地回写到列表，避免详情页显示进行中而列表还停在空闲
    - 保留整表刷新，但只在详情页活跃时才用本地详情覆盖列表项，避免离开详情页后把列表刷新得到的新状态又覆盖回旧值

## 本次代码修改

- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 新增 `refreshSessionList()`
  - `refreshSessions()` 改为按需叠加当前活跃详情页的本地状态
  - 在 `SessionStarted / AssistantDelta / AssistantDone / RunStatus / RunInterrupted / ToolRequest / ToolResult` 等状态变化点同步更新可见列表项
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - 进入 `Routes.Sessions` 时，如果 bridge 已连接，主动刷新线程列表
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - 列表 badge 文案从旧的“在线 / 异常”统一成“进行中 / 待审批 / 出错”
- `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
  - 增补线程列表状态同步与主动刷新单测
  - 修正测试前置条件：涉及列表断言的用例先建立连接，让线程列表真实加载

## 本次验证

- 已执行 Android 单测：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- 已执行 Android debug 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过
