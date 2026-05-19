# codex-mobile 全局设置保存与线程保持方案讨论

- 日期：2026-05-19
- 来源：Codex
- 类型：记录
- 相关目录：android/
- 相关 skill：record-and-reflect-review, delegation-orchestrator
- 标签：Android, 设置, 会话配置, 方案讨论

## 任务输入摘要

- 最终结果：讨论“全局设置正常保存，进入线程时保持全局设置”的修改方式。
- 现有素材：Android 端 `AppViewModel.kt`、`AppSettingsStore.kt`、`SettingsScreen.kt`、`SessionDetailScreen.kt`、`CodexMobileApp.kt`。
- 明确约束：先讨论方案，用户确认后再实施；记录保存在项目内，不混入无关临时文件。
- 完成标准：全局默认设置可继续持久化；线程/草稿配置不再覆盖全局默认设置；补上对应单测并完成 Android 验证。
- 产出后动作：完成实现、验证、提交与推送。

## 背景

用户希望全局设置可以稳定保存，并且进入线程后不要丢失或被线程内配置覆盖。本轮先完成只读排查，用户确认“全局默认设置与线程配置解耦”后继续实施。

## 关键过程

- 阅读项目 `AGENTS.md`，确认本轮需要先讨论方案，不直接修改业务代码。
- 读取 `record-and-reflect-review` 与 `delegation-orchestrator` skill 说明，按项目内记录与控制平面方式组织本轮工作。
- 排查 Android 设置与会话链路，重点查看：
  - `android/app/src/main/java/com/openai/codexmobile/data/AppSettingsStore.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- 关键判断：
  - 全局设置当前确实会保存到 `SharedPreferencesAppSettingsStore`。
  - 线程详情/草稿线程中的配置修改会回写 `cwdInput`、`modelInput`、`approvalModeInput`、`reasoningEffortInput`、`serviceTierInput`、`sandboxModeInput`，随后调用 `persistSettings`。
  - 这意味着“线程配置”和“全局默认设置”当前共用一套状态，线程内改动会污染全局默认值，是主要根因。
- 用户确认后实施 Android 端解耦：
  - 草稿线程改配置时，只更新 `selectedDraftSession`。
  - 已有线程改配置时，只更新 `selectedSession` 并调用 bridge 会话配置更新。
  - 不再从线程配置回写全局输入状态，也不再因此调用全局设置持久化。
- 单测同步调整：
  - 原有“线程配置会持久化到全局设置”的预期改为“线程配置不会污染全局设置”。
  - 新增草稿配置变更不覆盖全局设置的回归测试，并验证真正创建会话时仍使用草稿自己的配置。

## 结果

- 已修改文件：
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
  - `bridge/src/app.ts`
  - `bridge/tests/app.test.ts`
  - `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailConfigIsolationTest.kt`
  - `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailConfigDialogRoutingTest.kt`
- 结果：
  - 线程详情和草稿线程的配置编辑不再覆盖全局默认设置。
  - 新草稿线程仍然从全局默认设置初始化。
  - 草稿线程在局部改配后，真正创建远端会话时会使用草稿自己的配置。
  - 已修复线程详情页“修改推理强度/文件权限后互相串值”的问题。
  - 根因最终确认有两层：
    - bridge `PATCH /api/session/:id/config` 对已存在会话会携带 `undefined` 字段写回 store，未修改字段会被清空；
    - Android 在处理配置更新响应时，会把响应里未修改但已过时的字段和运行状态合并回当前会话。
  - 本次修复：
    - bridge 侧对已存在会话的配置 PATCH 不再先 attach/refresh，会话配置 patch 只写入有定义的字段；
    - Android 侧新增“配置更新响应专用合并逻辑”，只接受当前请求真正修改的字段，并保留当前运行状态。
- 验证：
  - `cd bridge; npm run check`
  - `cd bridge; npm test`
  - `cd android; .\gradlew.bat testDebugUnitTest`
  - `cd android; .\gradlew.bat app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailConfigIsolationTest'`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 上述验证均通过。

## 可复用经验

- 当用户反馈“全局设置保存异常”时，除了检查持久化存储，还要检查线程/草稿页是否复用了同一组输入状态并在局部编辑时回写全局状态。
- 这类问题优先沿“设置页状态 -> 新建线程默认值 -> 线程详情编辑 -> 持久化”链路排查，定位速度较快。
- 对“默认配置”和“会话覆盖配置”要在状态模型里明确分层，否则 UI 很容易在局部编辑时误伤全局默认值。

## Skill 观察

- 是否出现新 skill 候选：否
- 是否应该优化已有 skill：否
- 触发条件或典型用户说法：全局设置保存、线程进入后保持默认配置、线程配置不要污染全局配置

## 后续事项

- [x] 与用户确认“全局默认设置”和“线程配置”是否需要彻底解耦。
- [x] 若确认实施，调整 `AppViewModel.kt` 状态结构与回写逻辑。
- [x] 补充 `AppViewModelTest.kt`，覆盖“线程配置不污染全局设置”和“新草稿仍使用全局默认值”。
- [x] 使用模拟器和 Android instrumentation test 复现“线程详情页配置按钮显示串位/不刷新”的问题。
- [x] 确认 `SessionDetailScreen` 独立交互路径正常：直接渲染页面时，推理强度与文件权限弹窗回调都指向正确字段，且外部 `SessionDetail` 变化能刷新按钮文案。
- [x] 确认 `AppViewModel` 入参路径正常：日志显示修改推理强度时收到 `reasoningEffort=high`，修改文件权限时收到 `sandboxMode=danger-full-access`。
- [x] 确认问题不在 bridge 字段名映射：Android PATCH payload、bridge `/api/session/:id/config` schema 与 store 更新字段均为 `reasoningEffort` / `sandboxMode`。
- [x] 最终定位到真实根因不是按钮回调串位，而是“配置更新响应带回旧字段并被重新合并”：bridge PATCH 会把未定义字段写回 store，Android 又会把过时响应覆盖到当前会话。
- [x] 已完成 bridge 与 Android 双侧修复，并通过模拟器回归用例验证。
