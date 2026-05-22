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
  - 第六轮把目标卡首行的重复状态胶囊移到底部指标区，首行收成更接近参考稿的“标签 + 状态 + 展开”关系。
  - 第七轮把状态条改成单外框四列指标 + 细分隔线，缩小外层与列间边距，继续贴近参考稿。
  - 第八轮继续压缩目标卡外形和屏内纵向节奏，让目标卡与下方消息卡的关系更接近参考稿。
  - 第九轮继续缩小目标卡指标 chip 的尺寸和间距，让整行更接近参考稿的轻量状态标签。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/ConnectionScreen.kt`
  - 按参考稿改成浅色背景 + 蓝色主状态卡 + 单一主按钮结构。
  - 桥接地址输入区改成更接近参考稿的图标前缀和圆角描边框。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - 改成“顶部标题 + 会话统计 + 目录分组标题 + 轻量卡片列表 + 右下角新建”的结构。
  - 压缩会话卡信息层级，减少每条会话对垂直空间的占用。
  - 第二轮继续压缩卡片高度、缩小归档动作权重，并把视觉重心拉回到标题 / 状态 / 时间三要素。
  - 第三轮继续把右侧归档动作弱化成更轻的 icon，进一步贴近参考稿里“卡片主体优先、动作退后”的视觉关系。
  - 第四轮把“当前 / 已归档”改成真正的分段胶囊，缩小高宽比并统一选中态层级，继续往参考稿靠。
  - 第六轮继续收紧目录头，把右侧“新建 / 展开”区改成更轻的文字+箭头关系，减少按钮感。
  - 第七轮继续收右侧信息列，把状态、时间、进入箭头和归档动作拆成更接近参考稿的上下节奏，减轻右侧纵向拥挤。
  - 第八轮缩小会话卡左侧图标块并重新校正文字起点，继续向参考稿的横向比例靠拢。
  - 第九轮继续收紧标题统计区和顶部分段胶囊，让横向关系更接近参考稿。
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - 连接页和会话列表页移除重复顶栏。
  - 会话详情顶栏改成更接近参考图的标题 + 在线状态样式。
  - 继续把会话详情顶栏 action 区补齐为更接近参考图的多图标排布。
- `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
  - showcase 顶栏同步补齐参考图风格的 action 排布，用于截图验证。
- `android/app/src/main/java/com/openai/codexmobile/ui/theme/Theme.kt`
  - 主题支持显式传入浅色模式，方便截图回放页稳定复现。
- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 保留托管策略，并修正仅在托管审批模式下才自动放行待审批项。
- `android/app/src/main/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
  - 新增/完善确定性 UI 回放入口，回放数据改为当前托管策略默认值。
- `android/app/src/debug/AndroidManifest.xml`
  - 注册截图回放 Activity，便于用 `adb am start` 直接拉起展示页。
- `android/app/src/debug/java/com/openai/codexmobile/PrimaryScreensShowcaseActivity.kt`
  - 新增连接页 / 会话列表 showcase 入口，支持真实截图验证。
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
- `./.tmp/ui-screenshots/connection-showcase-v2.png`
  - 展示连接页重构后的主视觉和输入区布局。
- `./.tmp/ui-screenshots/connection-showcase-v3.png`
  - 连接页第三轮截图，右上角设置图标更接近参考稿轻量样式。
- `./.tmp/ui-screenshots/sessions-showcase-v3.png`
  - 展示会话列表的分组结构、轻量会话卡和浮动新建按钮。
- `./.tmp/ui-screenshots/sessions-showcase-v5.png`
  - 会话列表第二轮截图，卡片密度进一步压缩，更接近参考稿。
- `./.tmp/ui-screenshots/sessions-showcase-v6.png`
  - 会话列表第三轮截图，顶栏工具区改成更接近参考稿的搜索 / 筛选 / 更多布局。
- `./.tmp/ui-screenshots/sessions-showcase-v7.png`
  - 会话列表第四轮截图，右侧归档动作进一步弱化，更接近参考稿。
- `./.tmp/ui-screenshots/sessions-showcase-v8.png`
  - 会话列表第五轮截图，顶部“当前 / 已归档”已改成更接近参考稿的分段胶囊。
- `./.tmp/ui-screenshots/sessions-showcase-v9.png`
  - 会话列表第六轮截图，目录头右侧动作区进一步轻量化。
- `./.tmp/ui-screenshots/sessions-showcase-v10.png`
  - 会话列表第七轮截图，卡片右侧状态列进一步收紧。
- `./.tmp/ui-screenshots/sessions-showcase-v11.png`
  - 会话列表第八轮截图，左侧图标块与文本区比例继续向参考稿靠拢。
- `./.tmp/ui-screenshots/sessions-showcase-v12.png`
  - 会话列表第九轮截图，标题区和分段胶囊的横向关系进一步收紧。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v6.png`
  - 会话详情页第二轮截图，包含新的顶栏样式。
- `./.tmp/ui-screenshots/session-detail-showcase-transcript-v2.png`
  - 会话详情页第二轮消息图窗截图。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v7.png`
  - 会话详情页第三轮截图，顶部状态条和目标卡进一步收瘦。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v8.png`
  - 会话详情页第四轮截图，顶栏 action 排布更接近参考稿。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v9.png`
  - 会话详情页第五轮截图，状态条四个指标块进一步统一，目标卡与图片托盘继续保持紧凑布局。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v10.png`
  - 会话详情页第六轮截图，目标卡首行比例继续向参考稿收拢。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v11.png`
  - 会话详情页第七轮截图，状态条改成更接近参考稿的单外框四列结构。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v12.png`
  - 会话详情页第八轮截图，目标卡和下方消息卡之间的纵向节奏进一步压紧。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v13.png`
  - 会话详情页第九轮截图，目标卡指标 chip 更小更紧凑。

## 验证结果

- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
- `cd android; .\gradlew.bat testDebugUnitTest`：通过
- `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailConfigIsolationTest'`：通过
- `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 第二轮 UI 调整后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailConfigIsolationTest'`：通过
- 第三轮密度微调后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 顶栏与工具区拟真度调整后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 列表页动作弱化后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
- 分段胶囊与状态条收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat installDebugAndroidTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 目录头与目标卡首行微调后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 右侧状态列与状态条外框进一步拟真后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 左侧图标比例与目标卡纵向节奏微调后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 标题区 / 分段胶囊与目标 chip 进一步收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过

## 备注

- 工作区里原有的 `README.md`、`docs/api.md`、`AI工作记录/records/2026/05/2026-05-22-codex-mobile-goal模式接入可行性分析.md` 和 `mobile_uploads/` 不是本次改动内容，未纳入提交。
