# codex-mobile 会话详情目标区与图片预览 UI 重设计

- 日期：2026-05-23
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：`android/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`、`codex-mobile-android-ui`
- 标签：`android`、`compose`、`session detail`、`goal`、`image preview`

## 本次目标

- 重新设计会话详情页的目标区域，减少对消息区的遮挡。
- 把图片附件改成固定大小的预览窗口，不再随原图长宽比把列表撑高。
- 保留点击查看原图的能力，并补一张新的 UI 参考图。

## 当前状态

- 已完成 Android Compose 详情页重构、回放页截图验证和相关测试修正。
- 当前改动已通过调试构建、单元测试和两组定向 `connectedDebugAndroidTest`。

## 关键事实

- 目标区原先默认展开，占用高度明显，容易把消息区首屏挤出。
- 发送区附件托盘和消息内图片都容易被竖图拉长，视觉重心过度落在图片本身。
- 原图预览能力已存在，只需要把缩略预览改成固定窗口即可。

## 实际改动

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 将状态区改成紧凑的 4 宫格摘要条，默认更薄，展开后再显示连接文案和配置按钮。
  - 将目标卡改成“标签 + 状态 + 目标摘要 + 展开详情”的紧凑结构，默认收起。
  - 将消息内图片改成统一尺寸缩略窗，固定窗口裁切预览，点击继续查看原图。
  - 将发送区附件托盘改成统一卡片样式和固定预览窗，避免竖图撑高。
  - 调整输入区、卡片圆角和留白，让整体更接近用户给出的浅色参考风格。
- `android/app/src/main/java/com/openai/codexmobile/ui/theme/Theme.kt`
  - 主题支持显式传入浅色模式，方便截图回放页稳定复现。
- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 保留托管策略，并修正仅在托管审批模式下才自动放行待审批项。
- `android/app/src/main/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
  - 新增/完善确定性 UI 回放入口，回放数据改为当前托管策略默认值。
- `android/app/src/debug/AndroidManifest.xml`
  - 注册截图回放 Activity，便于用 `adb am start` 直接拉起展示页。
- `android/app/src/main/AndroidManifest.xml`
  - 补上 `ReplayHarnessActivity` 声明，修正回放测试无法启动的问题。
- `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailScreenshotTest.kt`
  - 增加 Compose 截图测试入口。
- `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailConfigIsolationTest.kt`
- `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailReplayTest.kt`
  - 将旧的手动审批/工作区可写断言更新为当前托管策略下的真实行为。

## 截图产物

- `./.tmp/ui-screenshots/session-detail-showcase-full-v5.png`
  - 展示会话详情页主参考图，包含紧凑目标区和发送区固定图片预览窗。
- `./.tmp/ui-screenshots/session-detail-showcase-transcript.png`
  - 展示消息区内联图片的固定尺寸缩略窗效果。

## 验证结果

- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
- `cd android; .\gradlew.bat testDebugUnitTest`：通过
- `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailConfigIsolationTest'`：通过
- `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过

## 备注

- 工作区里原有的 `README.md`、`docs/api.md`、`AI工作记录/records/2026/05/2026-05-22-codex-mobile-goal模式接入可行性分析.md` 和 `mobile_uploads/` 不是本次改动内容，未纳入提交。
