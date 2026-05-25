# codex-mobile-执行过程消息归类排查方案

- 日期：2026-05-25
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 目标

- 排查 Android 会话详情页中“非对话回复类型”的归类与分组逻辑。
- 输出修改方案，使文件修改、程序执行等连续事件统一归类为“执行过程”，并作为 Codex 回复内容挂在同一回复框内展示。

## 边界

- 本轮仅做只读排查与方案设计，不直接修改功能代码。
- 重点检查 Android 详情页渲染链路、消息模型，以及 bridge 到 Android 的事件映射。

## 当前状态

- 已读取项目与全局 AGENTS 规则。
- 已确认本次任务采用主线程编排，不下发 subagent；原因是问题范围仍在收敛，先需要串起事件模型与 UI 映射链路。
- 尚未运行构建或测试。

## 排查结论

- Android 详情页已经存在“执行过程”容器，但它当前是顶层独立展示项，不属于 Codex 回复气泡的一部分。
- 当前历史 transcript 的“执行过程”判定依赖 Android 本地标题白名单，而不是稳定的结构化类型。
- 这份标题白名单与 bridge 实际输出并不完全一致，导致部分非对话项无法进入“执行过程”分组。

## 关键定位

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
  - `ExecutionActivityTitles` 里包含 `文件编辑`，但 bridge 历史 transcript 实际输出的是 `文件修改`。
  - 历史 transcript 里的 `工具调用 ...`、`动态工具 ...`、`协作调用 ...` 也不在白名单里。
  - `buildTranscriptDisplayItems()` 只会把 `belongsToExecutionProcess == true` 的 bubble 合并为 `ExecutionGroup`。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `ExecutionGroup` 会走独立的 `ExecutionProcessCard()` 渲染，而不是放进 Codex 的对话气泡里。
- `bridge/src/session-view.ts`
  - 历史 transcript 会把 `commandExecution`、`fileChange`、`mcpToolCall`、`dynamicToolCall`、`collabAgentToolCall` 等类型格式化为 `系统：...` 文本块。
- `bridge/src/app-server-runner.ts`
  - 实时流已经会把过程类事件结构化成 `activity`，其中带有 `itemType/title/body/summary/transcriptBlock`。

## 直接成因

1. 历史链路靠标题白名单分类，存在标题不一致问题。
2. UI 顶层 item 结构只有“普通 bubble”与“独立执行过程卡片”，没有“Codex 回复容器 + 内嵌执行过程”的模型。

## 推荐修改方案

### 方案目标

- 所有非对话类回复统一进入“执行过程”。
- 连续执行过程继续合并为一个可展开容器。
- 该容器归属 Codex 回复，而不是作为独立系统卡片悬在消息流里。

### 第一层：先修正归类来源

- 优先把 Android 侧“是否属于执行过程”的判断从纯标题白名单，收敛成统一分类函数。
- 最低可行做法：
  - 先补齐现有 bridge 已输出的标题：
    - `文件修改`
    - `工具调用 `
    - `动态工具 `
    - `协作调用 `
- 更稳妥的做法：
  - 给历史 transcript 解析结果增加一个“执行过程类别”判断器，按前缀或映射归类，而不是依赖完全相等的标题字符串。

### 第二层：调整 UI item 结构

- 把当前顶层 `TranscriptDisplayItem` 从：
  - `BubbleItem`
  - `ExecutionGroup`
- 调整为更接近“会话轮次”的结构，例如：
  - `UserTurn`
  - `CodexTurn(messages, executionGroup)`
- 这样连续执行过程可以挂到 `CodexTurn` 内部显示，而不是作为顶层独立项。

### 第三层：调整详情页渲染

- `SessionDetailScreen.kt` 中不再直接把 `ExecutionGroup` 渲染成独立 `ExecutionProcessCard()`。
- 改为：
  - 外层仍使用 Codex 左侧头像和回复布局；
  - 内层把执行过程容器作为 Codex 回复内容的一部分插入；
  - 如果该轮只有执行过程、没有最终文本回复，也仍然显示为一条 Codex 回复。

### 第四层：保留 bridge 不动，优先 Android 落地

- 现有 bridge 已经提供：
  - 历史 transcript 文本块
  - 实时 `activity`
- 因此第一版修复可以只改 Android。
- 如果后续要彻底去掉标题猜测，再考虑扩展 `SessionView`，直接返回结构化 transcript item。

## 建议改动文件

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
  - 重写执行过程分类逻辑。
  - 重构顶层展示模型，支持 Codex 回复内嵌执行过程。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 重构消息列表渲染与 `ExecutionProcessCard()` 的挂载位置。
- 可选：
  - `android/app/src/main/java/com/openai/codexmobile/model/SessionActivityEntry.kt`
  - 若需要可增加更明确的执行过程类别字段。
- 可选长期项：
  - `bridge/src/types.ts`
  - `bridge/src/session-view.ts`
  - 为历史详情接口补结构化 transcript item，替代标题推断。

## 验证建议

- 历史会话里包含 `命令执行 + 文件修改 + 工具调用` 时，应全部进入同一“执行过程”容器。
- 连续多个过程项之间不应拆成多个独立顶层卡片。
- 执行过程容器应显示在 Codex 一侧回复布局中，而不是独立系统卡片。
- 纯文本助手回复、用户消息、审批请求/结果的现有顺序不应回归。

## 本次实现

- 已按确认规则直接修改 Android 详情页：
  - 连续执行过程继续合并。
  - 中间一旦插入 `Codex：...` 文字回复，就结束前一段执行过程，后续重新开始第二段。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
  - 把执行过程归类从纯标题白名单收敛为统一判断函数。
  - 修正 `文件修改` 标题识别。
  - 新增前缀识别：`工具调用 `、`动态工具 `、`协作调用 `。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 保留执行过程的连续分组逻辑。
  - 把执行过程卡片改为按 Codex 回复样式显示：左侧 Codex 头像、Codex 标签、右侧回复框内展示执行过程容器。
- `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptBubbleTest.kt`
  - 更新旧测试里的 `文件编辑` 为 `文件修改`。
  - 新增“执行过程被中间文字回复切断后分成两段”的单测。
  - 新增“动态工具/工具前缀也算执行过程”的单测。

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

## 文档同步

- 已按用户确认同步更新：
  - `README.md`
  - `docs/architecture.md`
  - `docs/api.md`
- 文档新增说明重点：
  - 执行过程现在作为 Codex 回复的一部分展示，而不是独立系统卡片
  - 只有连续执行过程才合并
  - 中间遇到 `Codex：...` 文字回复时，会切断前一段执行过程并开始新的执行过程分组
- 本轮仅更新文档，未额外执行构建或测试；沿用上一轮已通过的 Android 单测与 debug 构建结果

