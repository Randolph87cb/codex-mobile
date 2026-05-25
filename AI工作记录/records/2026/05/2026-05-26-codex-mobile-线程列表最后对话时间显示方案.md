# codex-mobile-线程列表最后对话时间显示方案

- 日期：2026-05-26
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 任务输入摘要

- 最终结果：实施线程列表最后活动时间改造，让排序按最后一次对话活动倒序，列表时间显示改成聊天式相对时间，并同步更新文档。
- 现有素材：用户截图、Android 线程列表现状代码、bridge 会话视图组装代码。
- 明确约束：遵守项目 Android 轻客户端与中文文案规则；bridge 和 Android 变更后都要跑规定验证。
- 完成标准：最后活动时间不再被普通刷新污染；列表显示为 `HH:mm / N天前 / N周前`；文档同步；验证通过。
- 产出后动作：提交并推送实现。

## 背景

用户反馈线程列表显示的时间像刷新时间，不像最后一句对话时间，且 ISO 时间过长不易读，希望改成类似聊天记录的相对时间，并按最后回复时间倒序排列。

## 关键过程

- 检查 `SessionListScreen.kt`，确认列表直接显示 `session.lastUpdated`，分组内和分组间排序也都基于该字段。
- 检查 `SessionSummary.kt`，确认当前只有一个 `lastUpdated: String` 字段，既承担排序源也承担展示文案。
- 检查 `RealBridgeDataProvider.kt` 与 `bridge/src/session-view.ts`，确认 Android 侧把 bridge 返回的 `updatedAt` 直接映射到 `lastUpdated`，bridge 侧也按 `updatedAt` 排序。
- 继续核对 thread/turn 数据结构，确认本地 `SessionStore.update()` 会刷新 `updatedAt`，这是“看起来像刷新时间”的主要来源。
- 在 bridge 侧为 `SessionRecord` 增加可选 `lastActivityAt`，仅在真实会话活动发生时更新；列表视图改为优先使用 `lastActivityAt`，不再用本地刷新时间覆盖 thread 的最后活动时间。
- 在 `AppServerRunner` 和 `MockRunner` 中，把用户输入、assistant delta、turn 完成和执行过程消息都视为“会话活动”，同步更新 `lastActivityAt`。
- Android 列表页保留原始 ISO 时间用于排序，只在 `SessionListScreen.kt` 内做相对时间格式化；bridge JSON 解析改为优先读取 `lastUpdated`，避免被 `updatedAt` 回退覆盖。
- 更新 `FakeCodexDataProvider.kt`、`ReplayHarnessActivity.kt` 的示例时间为 ISO 字符串，避免假数据把排序和显示行为带偏。
- 补 bridge 和 Android 单元测试，覆盖“忽略本地刷新时间污染”和“聊天式时间格式化”。

## 结果

- 已完成 bridge、Android 和 README 的联动修改。
- 主要修改文件：
  - `bridge/src/types.ts`
  - `bridge/src/session-store.ts`
  - `bridge/src/session-view.ts`
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/mock-runner.ts`
  - `bridge/tests/session-view.test.ts`
  - `bridge/tests/app-server-runner.test.ts`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FakeCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
  - `android/app/src/test/java/com/openai/codexmobile/ui/screen/SessionListGroupingTest.kt`
  - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
  - `README.md`
- 已执行验证：
  - `cd bridge && npm run check`
  - `cd bridge && npm test`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android && .\gradlew.bat testDebugUnitTest`
- 验证结果：全部通过。

## 可复用经验

- 这类“既要排序又要友好展示”的时间字段，不应只保留一个字符串字段；通常需要分离“原始可排序时间”和“UI 格式化文案”。
- 当上游 thread 元数据与本地状态存储共存时，不要复用同一个 `updatedAt` 承担“状态刷新时间”和“最后会话活动时间”两种语义。

## Skill 观察

- 是否出现新 skill 候选：否。
- 是否应该优化已有 skill：暂无。
- 触发条件或典型用户说法：线程列表时间显示不对、列表排序应按最后回复时间。

## 后续事项

- [x] 实施 Android/bridge 联动改造并补测试。
- [x] 更新 README 中的线程列表行为说明。

