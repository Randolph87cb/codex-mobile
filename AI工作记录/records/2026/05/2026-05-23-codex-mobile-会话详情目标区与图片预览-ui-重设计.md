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
  - 增加 `autoScrollTranscript` 开关，保留真实详情页自动滚动，同时允许 showcase 固定在首屏构图，避免截图被滚动位置污染。
  - 最新一轮继续压缩发送图片区：移除冗余说明胶囊，把标题下说明收成单行状态摘要，文件名改成单行截断，失败态压成更短的单行错误提示，整体更接近参考图的轻量附件托盘。
  - 修正失败态图片卡的动作 test tag，失败卡明确暴露 `retry` 按钮 tag，保持截图拟真和交互测试一致。
  - 再继续压缩详情页首屏纵向密度：外层纵向 padding、状态条指标块、目标卡、transcript 卡和输入区的留白都收小一层，让首屏更多地露出消息与附件区。
  - 最新一轮继续压缩消息气泡本体：缩小气泡圆角和内边距，收紧标签 chip、复制按钮和气泡间距，让消息流更接近参考图的轻薄卡片感。
  - 最新一轮继续压缩底部输入区：缩小加图按钮、输入框和发送按钮的高度与圆角，降低附件卡和输入区之间的视觉厚度，让底部比例更接近参考图。
  - 最新一轮继续压缩底部输入区比例：在保持整体外层留白不变的前提下，收小加图按钮、输入框和发送按钮，让附件区和输入区的上下节奏更接近参考图。
  - 调整输入区、卡片圆角和留白，让整体更接近用户给出的浅色参考风格。
  - 第六轮把目标卡首行的重复状态胶囊移到底部指标区，首行收成更接近参考稿的“标签 + 状态 + 展开”关系。
  - 第七轮把状态条改成单外框四列指标 + 细分隔线，缩小外层与列间边距，继续贴近参考稿。
  - 第八轮继续压缩目标卡外形和屏内纵向节奏，让目标卡与下方消息卡的关系更接近参考稿。
  - 第九轮继续缩小目标卡指标 chip 的尺寸和间距，让整行更接近参考稿的轻量状态标签。
  - 第十轮继续弱化标题行里的“目标”标签，让标签和状态文字的相对比例更接近参考稿。
  - 第十一轮继续调整目标卡标题行右侧展开箭头的留白，让右侧呼吸感更接近参考稿。
  - 第十二轮继续压缩目标卡正文和 chip 行之间的节奏，让正文结束和状态摘要开始之间的过渡更接近参考稿。
  - 第十三轮继续压缩标题行到正文首行的距离，让正文起始位置更贴近参考稿。
  - 第十四轮继续微调 chip 行整体的水平起点，让状态摘要与正文块的左边界关系更接近参考稿。
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
  - 第十轮继续缩小目录头里的文件夹图标块，让分组头部更接近参考稿的轻量感。
  - 第十一轮继续收紧会话卡右侧状态 badge 的尺寸，减少与参考稿之间的厚重感差异。
  - 第十二轮把目录头右侧“新建 + 箭头”收成更紧的 trailing 组，调整标题和动作的相对位置。
  - 第十三轮继续收紧目录名和计数圆点之间的距离，让目录头视觉关系更接近参考稿。
  - 第十四轮继续收紧 trailing 组中“新建”和箭头的距离，让目录头动作区更接近参考稿。
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - 连接页和会话列表页移除重复顶栏。
  - 会话详情顶栏改成更接近参考图的标题 + 在线状态样式。
  - 继续把会话详情顶栏 action 区补齐为更接近参考图的多图标排布。
  - 最新一轮继续压缩详情页顶栏：标题字级、在线状态行、返回键和右侧 action 图标整体缩小一层，进一步向参考图靠近。
- `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
  - showcase 顶栏同步补齐参考图风格的 action 排布，用于截图验证。
  - 固定关闭 transcript 自动滚动，保证详情页参考图稳定停在首屏。
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
- `./.tmp/ui-screenshots/sessions-showcase-v13.png`
  - 会话列表第十轮截图，目录头文件夹图标块继续收小。
- `./.tmp/ui-screenshots/sessions-showcase-v14.png`
  - 会话列表第十一轮截图，右侧状态 badge 继续收小。
- `./.tmp/ui-screenshots/sessions-showcase-v15.png`
  - 会话列表第十二轮截图，目录头文字与右侧“新建”关系继续收口。
- `./.tmp/ui-screenshots/sessions-showcase-v16.png`
  - 会话列表第十三轮截图，目录名和计数圆点继续收紧。
- `./.tmp/ui-screenshots/sessions-showcase-v17.png`
  - 会话列表第十四轮截图，目录头 trailing 组继续收紧。
- `./.tmp/ui-screenshots/sessions-showcase-v18.png`
  - 会话列表第十五轮截图，目录头左侧文件夹块、目录名与 `新建` trailing 组进一步弱化并压紧。
- `./.tmp/ui-screenshots/sessions-showcase-v19.png`
  - 会话列表第十六轮截图，会话卡左侧图标块与标题首行高度关系继续向参考稿收口。
- `./.tmp/ui-screenshots/sessions-showcase-v20.png`
  - 会话列表第十七轮截图，右侧状态 badge 与时间行的纵向关系继续向参考稿收口。
- `./.tmp/ui-screenshots/sessions-showcase-v21.png`
  - 会话列表第十八轮截图，中间标题块与右侧状态列的横向比例继续收口。
- `./.tmp/ui-screenshots/sessions-showcase-v22.png`
  - 会话列表第十九轮截图，标题字级和副标题灰度继续向参考稿收口。
- `./.tmp/ui-screenshots/sessions-showcase-v23.png`
  - 会话列表第二十轮截图，目录头和首张会话卡之间的纵向落点继续向参考稿收口。
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
- `./.tmp/ui-screenshots/session-detail-showcase-full-v14.png`
  - 会话详情页第十轮截图，目标卡标题行里标签与状态文字的比例继续向参考稿靠拢。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v15.png`
  - 会话详情页第十一轮截图，目标卡标题行右侧箭头留白继续收口。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v16.png`
  - 会话详情页第十二轮截图，目标卡正文与 chip 行之间的节奏继续压紧。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v17.png`
  - 会话详情页第十三轮截图，标题行到正文首行的距离继续收口。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v18.png`
  - 会话详情页第十四轮截图，chip 行起点继续向正文块左边界靠拢。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v19.png`
  - 会话详情页第十五轮截图，目标卡标题行和正文/chip 节奏继续向参考稿收口。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v20.png`
  - 会话详情页第十六轮截图，目标卡第一行 `目标` 标签与状态文字占比继续向参考稿收口。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v21.png`
  - 会话详情页第十七轮截图，目标卡正文和 chip 行的右边界继续收整。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v22.png`
  - 会话详情页第十八轮截图，目标卡首行到正文首行的纵向距离继续压紧。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v23.png`
  - 会话详情页第十九轮截图，目标卡 chip 行与下方消息卡之间的节奏继续收薄。
- `./.tmp/ui-screenshots/session-detail-showcase-full-v24.png`
  - 会话详情页第二十轮截图，状态条与目标卡之间的间距继续向参考稿收口。

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
- 目录头图标块与目标标题行比例继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 状态 badge 与目标标题行右侧留白继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 目录头 trailing 组与正文/chip 节奏继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 目录计数圆点与目标正文首段节奏继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- trailing 组与 chip 行起点继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 目录头左侧块与目标标题行继续收口后再次执行：
  - `cd android; .\gradlew.bat compileDebugKotlin`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
- 会话卡左侧图标块与目标标签比例继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：由于并发运行 Gradle 任务会踩到同一份 Kotlin 增量缓存，这轮最终验证改为串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'` 后稳定通过。
- 右侧状态区与目标卡右边界继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`，避免再次触发 Kotlin 增量缓存冲突。
- 标题块横向比例与目标卡首行节奏继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。
- 标题字级 / 副标题灰度与目标卡下沿节奏继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。
- 目录头落点与状态条/目标卡间距继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。
- 分段胶囊 / 目录头距离与目标标题行 baseline 继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 本轮截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v24.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v25.png`
- 目录头 / 会话卡比例与图片预览卡底部节奏继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：最终仍按串行执行 Gradle 验证，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 本轮截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v25.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v27.png`
- 顶部统计区 / 分段胶囊与消息卡/图片卡节奏继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 本轮截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v26.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v28.png`
- 顶栏 action 与四指标状态条比例继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 本轮截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v27.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v29.png`
- 首屏留白 / 断开按钮降权与详情页 transcript 精简后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 本轮截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v34.png`

## 当前残余

- 详情页 showcase 的 transcript 卡顶部浅色顶边问题已定位并修复，根因不是卡片样式本身，而是 showcase 首屏仍沿用真实页面的 transcript 自动滚动逻辑，导致截图落点被带偏。
- 当前真实页面继续保留 transcript 自动滚动；仅 `SessionDetailShowcaseActivity` 关闭自动滚动，用于稳定输出首屏参考图，不影响正式会话体验。

## 最新补充

- transcript 首屏残留修正后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v35b.png`

- 发送图片区继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailImageRenderingTest'`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v36.png`

- 顶栏 / 状态条 / 首屏密度继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v37.png`

- 消息气泡字级 / 内边距 / 头部控件继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v38.png`

- 底部输入区高度与附件区比例继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v39.png`

- 底部输入区高度与附件区比例继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 备注

- 工作区里原有的 `README.md`、`docs/api.md`、`AI工作记录/records/2026/05/2026-05-22-codex-mobile-goal模式接入可行性分析.md` 和 `mobile_uploads/` 不是本次改动内容，未纳入提交。

- 消息区横向比例继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v40.png`

## 本轮说明

- 本轮只继续收紧详情页消息区横向比例：普通消息气泡宽度由用户 `0.9f / 助手 0.95f` 收成 `0.84f / 0.9f`，执行过程卡宽度由 `0.95f` 收成 `0.9f`。
- 目的不是再压缩整页外边距，而是让消息区和顶部状态卡、目标卡形成更接近参考图的宽度层次。

- 消息区外层层级继续收口后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v41.png`

## 本轮说明

- 本轮继续收的是消息区外层层级，不再让 transcript 被一整块独立白卡包住；改成直接在页面背景上承载消息流，只保留极薄的内部留白。
- 目标是把详情页首屏更接近参考图那种“状态区之后直接进入消息内容”的结构，同时继续保留固定尺寸发送图片预览窗和现有点击看原图行为。

- 发送图片区继续压缩高度后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailImageRenderingTest'`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v42.png`

## 本轮说明

- 本轮继续压发送图片区本身，不改固定预览窗和点开原图能力：缩略卡宽高从 `106x92dp` 收成 `98x84dp`，头部图标块、容器圆角和内边距同步收小。
- 失败态去掉了单独占一行的重复“上传失败”文案，只保留行内 `失败 / 重试`，并把原测试 tag 保留在失败状态文字上，兼顾首屏高度和现有 UI 测试稳定性。

- 目标卡默认态继续压缩后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v43.png`

## 本轮说明

- 本轮继续压缩目标卡默认态，不再默认显示 `token / 预算 / 耗时` 这排指标，只保留标题状态和一行目标内容；完整指标仍保留在展开态。
- 同时把目标卡正文从 `bodyMedium` 收成 `bodySmall`，整体 padding 和行间距再压一层，目的就是进一步减少它对首屏消息区的遮挡。

- 顶部状态条继续压缩默认态后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v44.png`

## 本轮说明

- 本轮继续压的是顶部四指标状态条：外层圆角、上下 padding、每个指标块的 `minHeight`、图标尺寸、分隔线高度和右侧展开按钮都再收了一层。
- 目标是不改信息结构，只把默认态整体厚度压到更接近参考图的水平，让会话标题区和目标区之间的纵向节奏更顺。

- 底部输入区继续压缩默认态后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v45.png`

## 本轮说明

- 本轮继续压的是底部输入条：外层容器圆角、上下 padding、加图按钮、文本框最小高度和发送按钮都从 `52dp` 体系收到了 `48dp` 体系。
- 发送、加图、test tag 和现有交互都没变，只是把默认态做得更薄，让底部视觉重量更接近参考图。

- 底部输入区继续压缩默认态后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v45.png`

## 本轮说明

- 本轮继续压缩首屏纵向节奏，重点是底部输入条默认态；当前截图已验证输入条更接近参考图的薄型比例。
- 这轮没有改发送逻辑、图片能力和测试标记，只继续收紧了默认态尺寸。

- 顶栏标题区继续压缩后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v46.png`

## 本轮说明

- 本轮继续压缩详情页顶栏：`TopAppBar` 高度收成 `58dp`，会话标题从 `headlineSmall` 收成 `titleLarge`，在线状态点、返回键和右侧 action 图标也都同步缩小了一层。
- 目的是把首屏顶部做得更像参考图那种轻量工具栏，而不改任何操作位和页面结构。

- 消息气泡头部继续压缩后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v47.png`

## 本轮说明

- 本轮继续收的是消息头部：`你 / Codex` 标签 chip 改成紧凑尺寸，标题字级从 `titleMedium` 收成 `titleSmall`，右侧复制按钮和展开箭头也同步缩小。
- 目标是让消息气泡更接近参考图那种“内容优先、头部退后”的感觉，同时保留复制和展开能力，以及现有 test tag。

- 消息头部动作继续弱化后再次执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
  - `cd android; .\gradlew.bat testDebugUnitTest`：通过
  - `cd android; .\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.openai.codexmobile.SessionDetailReplayTest'`：通过
  - `cd android; .\gradlew.bat installDebug`：通过
  - 说明：继续保持 Gradle 串行执行，并设置 `GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'`。

## 最新截图

- 会话列表：`.tmp/ui-screenshots/sessions-showcase-v28.png`
- 会话详情：`.tmp/ui-screenshots/session-detail-showcase-full-v47.png`

## 本轮说明

- 本轮没有再动消息正文宽度和字号，只继续弱化复制按钮本身：从带底色的 `FilledTonalIconButton` 收成了更轻的透明 `IconButton`。
- 这样能继续保留复制能力和测试标记，但右上角动作不再像一个显眼按钮，更接近参考图里“动作退后”的感觉。
