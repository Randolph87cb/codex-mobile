# codex-mobile Android UI 页面地图梳理

- 时间：2026-05-24
- 目标：梳理当前手机应用所有功能页面、功能职责、页面切换逻辑和详情页内子界面，为后续 UI 重设计提供信息架构输入。
- 范围：只读检查 Android 端 UI 与 ViewModel 状态流，不修改应用源码。

## 已检查文件

- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/ConnectionScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
- `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
- `android/app/src/main/java/com/openai/codexmobile/data/SessionRepository.kt`

## 关键结论

- 当前显式路由包括：连接页、会话列表页、草稿线程页、会话详情页、设置页。
- 草稿线程页和会话详情页复用同一个 `SessionDetailScreen`，通过 `draftSession` 与 `sessionDetail` 区分模式。
- 详情页包含多个非路由子界面：状态展开区、目标卡片、审批卡片、排队消息卡片、附件托盘、转录气泡、图片预览、文件下载、配置编辑和目标编辑弹窗。
- 导航由 `CodexMobileApp.kt` 内部 `NavHost` 管理；连接成功后会自动从连接页跳到会话列表页；草稿发送成功后自动跳到正式会话详情。

## 验证

- 本次为只读梳理与记录更新，未运行 Android 构建或单元测试。

## 后续判断：Stitch 与 Google AI Studio UI 生成流程

- 用户希望判断是否可以用 `stitch.withgoogle.com` 和 Google AI Studio 做 UI 重设计，以及是否能达到所见即所得。
- 结论：Stitch 更适合高保真 UI 方案探索和设计系统沉淀；Google AI Studio Android Build 更接近可运行预览，可在浏览器 Android 模拟器中看到真实 Compose 生成结果。
- 对当前 `codex-mobile` 项目，不建议期待一键替换现有 UI；更稳妥流程是先用页面地图生成设计稿/原型，再把设计规则、截图和交互说明带回项目内按现有 ViewModel、导航、测试标签和 bridge 数据流实现。
- 参考官方资料：Google Stitch 2026-03-18 “vibe design”介绍、Stitch DESIGN.md 2026-04-21 介绍、Google AI Studio Build mode 文档、Google AI Studio Android Build 文档。

## Stitch 首版输入策略

- 用户继续询问如何让 Stitch 一开始准确理解需求并生成较准初版。
- 建议把输入拆成：产品定位、用户与场景、页面/路由清单、每页功能、关键状态、设计约束、不要做的事、首版验收标准。
- 推荐先让 Stitch 输出“信息架构确认稿”，再生成高保真界面；这样能降低它直接美化但误解功能的概率。
- 对当前项目，首版输入应强调：Android 轻客户端、不是通用聊天应用、固定链路 `Android App -> Windows Bridge -> codex app-server`、中文文案、保留审批点、重点页面为连接、会话列表、草稿/详情、设置。

## AI Studio 生成仓库合并可行性初查

- 时间：2026-05-25
- 用户提供仓库：`https://github.com/Randolph87cb/codex-mobile-ui`
- 操作：克隆到 `.tmp/codex-mobile-ui` 并只读检查远端分支、提交历史和文件树。
- 结果：远端 `main` 分支当前只有 `README.md`，没有 Android 源码、Gradle 配置、Compose 页面或 AI Studio 生成项目文件。
- 判断：当前无法尝试代码级合并；需要先从 AI Studio 导出完整项目或源码 ZIP，并提交至少 `settings.gradle.kts`、`build.gradle.kts`、`app/` 源码后，才能做实际迁移评估。

## AI Studio 生成仓库重新拉取后的合并判断

- 时间：2026-05-25
- 操作：在用户同步代码后，对 `.tmp/codex-mobile-ui` 执行 `git pull --ff-only`，更新到 `f1d0dbd feat: initialize Android project for Codex Mobile`。
- 新增内容：完整 Android 项目、Compose 页面、`CodexViewModel`、Room 本地数据层、AI Studio 生成 Gradle 配置、已构建 APK。
- 关键发现：
  - 生成项目是独立 demo：包名 `com.example`，应用 ID `com.aistudio.codexmobile.bcrvpx`，路由为 `connect/sessions/draft/chat/settings`。
  - 数据层不接当前 bridge API，使用 Room mock 数据；消息回复走 Gemini REST 或本地模拟。
  - 默认连接状态、会话、endpoint、token、cwd、模型等均为 mock，且有英文文案和 GPT-4o/GPT-3.5 等不符合当前项目默认值。
  - 构建栈为 AGP 9.1.1、Kotlin 2.2.10、compileSdk 36；当前项目为 AGP 8.5.2、Kotlin 2.0.21、compileSdk 35。
  - 生成仓库没有 Gradle wrapper，未运行其构建；已有 `.build-outputs/app-debug.apk`。
- 判断：不建议项目级合并或整页直接替换。建议把生成项目作为视觉参考，组件级吸收布局、颜色、分组、详情页状态指标等，保留当前项目 `AppViewModel`、bridge 数据流、`TestTags`、导航和测试。

## 详情页合并尝试

- 时间：2026-05-25
- 分支：`ui-detail-ai-studio-merge`
- 目标：先尝试把 AI Studio 生成版消息详情页中较有价值的视觉结构合入当前项目。
- 改动文件：`android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 改动内容：
  - 将详情页状态条调整为更接近 AI Studio 版的紧凑指标样式，显示会话、连接、排队、审批四段标签和值。
  - 保留 `SessionDetailStatusButton` 与展开详情逻辑。
  - 在展开配置区补齐工作目录和文件权限入口，保留 `SessionDetailConfigCwdButton`、`SessionDetailConfigSandboxButton` 等测试标签。
  - 未引入 AI Studio 版 mock ViewModel、Room、Gemini REST、Gradle 配置或新依赖。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `git diff --check` 无空白错误，仅有既有 CRLF 转换 warning。

## 详情页整页替换

- 时间：2026-05-25
- 用户确认不再小步迁移，希望详情页整体切到 AI Studio 版形态，再逐个修复问题。
- 改动文件：`android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 改动内容：
  - 将详情页主骨架改为 AI Studio 风格：顶部固定紧凑状态指标、日期/模式 chip、中部滚动消息视口、底部悬浮 pill 输入栏。
  - 目标卡片、审批卡片、排队卡片和真实转录内容进入中部滚动视口。
  - 图片附件托盘贴近底部输入栏，作为发送前上下文区域。
  - 消息气泡改为更宽的大圆角卡片、较大的说话人标签/头像、较大的复制按钮和更接近生成版的间距。
  - 继续保留当前项目真实数据流、审批、附件上传、文件下载、Markdown/代码/图片渲染、测试标签和 ViewModel 接口；未引入 AI Studio 的 mock 数据层或依赖。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\start-android-emulator.ps1` 检测模拟器已运行。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 使用 `adb shell am start -n "com.openai.codexmobile/.SessionDetailShowcaseActivity"` 打开详情页 showcase，并用 `adb exec-out screencap` 进行截图检查；布局可见，底部附件托盘和输入栏没有完全遮挡消息区。
  - `git diff --check` 无空白错误，仅有既有 CRLF 转换 warning。

## 详情页按 AI Studio 前端基准继续对齐

- 时间：2026-05-25
- 用户进一步明确：目标不是借鉴视觉，而是尽量做到 AI Studio 生成版的显示效果和交互效果，只替换背后的后端调用。
- 重新拉取 `.tmp/codex-mobile-ui`，结果为 `Already up to date`。
- 分析原因：
  - 之前详情页主体已改，但全局 `AppTopBar` 仍保留旧的右上角三点空点击，导致模型设置没有落到用户期望位置。
  - 顶部状态仍沿用旧结构保留“会话/连接/排队/审批”四段，和 AI Studio 版“连接状态/排队消息/目标状态”三段不一致。
  - 目标仍作为独立卡片展示，未进入顶部指标和弹窗交互。
  - 全局主题仍是旧的 Sand/Coral/SlateBlue/Pine 配色，未切到 AI Studio 生成版的冷灰蓝 palette。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/theme/Color.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/theme/Theme.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
- 改动内容：
  - 详情页改为内部自带 AI Studio 风格顶栏，右上角三点打开“模型设置”，入口包含模型、推理强度、速度、工作目录和文件权限，回调仍接当前 `AppViewModel`/bridge 数据流。
  - 全局 `AppTopBar` 在详情页和草稿详情页退场，避免双顶栏。
  - 顶部状态条改成三段：连接状态、排队消息、目标状态；审批继续作为消息视口内的审批卡片，不再作为顶部状态。
  - 排队消息和目标状态改为点击弹窗交互；目标弹窗保留开始/编辑、暂停/恢复、清除等目标操作。
  - 移除详情页常驻目标卡片和常驻排队卡片，让顶部状态承担入口角色。
  - 主题切到 AI Studio 生成版冷灰蓝 palette；debug showcase 改为复用详情页内部顶栏，便于模拟器验证三点设置菜单。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - `adb shell am start -n "com.openai.codexmobile/.SessionDetailShowcaseActivity"` 打开 showcase，并截图确认单顶栏、三段状态条、底部输入栏和附件托盘可见。
  - `adb shell input tap` 验证目标状态弹窗可打开；验证右上角三点可打开“模型设置”菜单并显示模型、推理、速度、目录和权限。

## 线程列表页按 AI Studio 前端基准对齐

- 时间：2026-05-25
- 用户继续要求处理线程列表界面，并确认以 AI Studio 生成版的显示效果和交互效果为前端基准，只替换背后后端调用。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
- 实现边界：
  - 保留当前项目 `SessionSummary`、bridge 连接状态、按 `cwd` 分组、打开详情、归档/恢复、草稿创建、设置入口和断开连接。
  - 不引入 AI Studio 生成项目里的 mock 会话、Room 数据层、Gemini REST、Gradle 配置或假统计卡。
  - 保留现有 `TestTags`，避免破坏现有测试定位。
- 改动内容：
  - 线程列表改为内嵌 `Scaffold`，顶部标题为“线程”，右上角仅保留三点更多入口并进入设置。
  - 主体切换为 AI Studio 风格的“现有 / 归档线程”页签、桥接在线连接条、目录分组和更紧凑的线程卡片。
  - 目录分组支持展开/收起，目录行保留新建草稿入口；非归档页右下角保留圆形 FAB 新建草稿。
  - 底部增加 Connect / Sessions / Settings 三项导航，当前选中线程；Connect 仍调用当前断开连接逻辑，Settings 进入当前设置页。
  - 线程卡片使用冷灰蓝主题下的浅色卡片、图标块、状态 badge、会话 ID 和更新时间，归档/恢复继续接当前 ViewModel 回调。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 通过 `adb shell input tap` 从连接页进入线程列表，并用 `adb exec-out screencap` 截图确认：顶部只剩三点入口，现有/归档页签、连接条、目录分组、线程卡片、FAB 和底部导航均可见。

## 连接页按 AI Studio 前端基准对齐

- 时间：2026-05-25
- 页面版本计数：连接页第 1 版迁移；未超过 5 版停下阈值。
- 目标：继续按“一个页面一个页面迁移”推进，把连接页换成 AI Studio 生成版的显示和交互结构，背后仍使用当前项目的 bridge 连接逻辑。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/ConnectionScreen.kt`
- 实现边界：
  - 保留当前项目 `endpointInput`、`BridgeConnectionState`、`onConnect`、`onEndpointChange`、设置入口和 `TestTags`。
  - 不引入 AI Studio 生成项目里的 Room 连接列表、mock toggle、延迟模拟连接或新增连接弹窗。
  - 当前连接页只展示当前选中的 bridge 连接；连接管理仍跳转到当前设置页。
- 改动内容：
  - 连接页改为内嵌 `Scaffold`，顶部为 AI Studio 风格的 `Codex Bridge` 顶栏和右上角设置入口。
  - 主体改为“连接”标题、深色连接节点卡和端点编辑卡。
  - 底部改为固定操作区：主按钮“尝试连接 / 正在连接中 / 重新连接”、次按钮“管理连接”、以及 Connect / Sessions / Settings 底部导航。
  - 深色连接节点卡和主按钮文字/图标固定为白色，修复冷灰蓝主题下 `onPrimaryContainer` 对比度偏低的问题。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - 第一次安装验证时模拟器出现 `System UI isn't responding`，排查为模拟器系统 UI 卡住：`adb devices` 一度无设备，停止卡住的 emulator/qemu 进程后重新启动 AVD 成功。
  - 重新安装并截图 `.\.tmp\connection-ai-studio-v2.png`，确认连接页顶栏、连接节点卡、端点编辑卡、固定底部动作和底部导航可见，文字对比度正常。
  - 点击“尝试连接”后仍停留在连接页，当前未证明真实 bridge 已连接；本次只确认入口仍接当前 `onConnect`，未改变后端调用链路。

## 设置页按 AI Studio 前端基准对齐

- 时间：2026-05-25
- 页面版本计数：设置页第 1 版迁移；未超过 5 版停下阈值。
- 目标：继续按“一个页面一个页面迁移”推进，把设置页换成 AI Studio 生成版的分区、卡片、分段控件和日志控制台形态，背后仍使用当前项目真实设置回调。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
- 实现边界：
  - 保留当前项目的保存连接、当前连接名称、endpoint、token、cwd、model、审批模式、推理强度、速度、文件权限和本地诊断日志回调。
  - 保留现有 `TestTags`，未引入 AI Studio 生成项目里的 mock ViewModel、Room 数据层、Toast/Clipboard 直接调用或假日志实体。
  - 因 `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt` 当前已有未提交改动，本版没有改全局导航文件，避免混入无关 hunks。
- 改动内容：
  - 设置页主体改为 AI Studio 风格的 `Connection Management`、`Default Preferences`、`Current Defaults`、`LOCAL DIAGNOSTIC LOGS` 分区。
  - 保存连接区改成 summary row、当前连接字段和紧凑连接行；新增、切换、删除仍接当前 ViewModel 回调。
  - 默认参数区改为输入块和分段选择控件，推理强度、速度、审批模式、文件权限继续使用真实设置值。
  - 诊断日志改成深色 console 卡片，保留刷新、复制、清空按钮和日志选择复制能力。
  - 底部增加 Settings 选中态导航栏；Connect/Sessions 暂时复用返回行为。
- 遇到的问题：
  - 第 1 次构建失败：`SettingsCard` 的 slot receiver 写成了 `Column.() -> Unit`，应为 `ColumnScope.() -> Unit`。已在同一版内修复，不计为新版本。
  - 顶部仍由全局 `AppTopBar` 提供，所以截图中存在全局“设置”顶栏和内容区“设置”大标题并存的问题。完整对齐 AI Studio 顶栏需要后续单独处理 `CodexMobileApp.kt` 的已有 dirty 改动或拆分 hunk。
  - 底部导航的 Connect/Sessions 暂未接独立目标路由，当前复用返回行为；完整导航对齐同样需要后续处理全局导航回调。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 从连接页点击设置进入设置页并截图 `.\.tmp\settings-ai-studio-v1.png`，确认连接管理区可见。
  - 滚动截图 `.\.tmp\settings-ai-studio-v1-scroll.png`、`.\.tmp\settings-ai-studio-v1-logs.png`、`.\.tmp\settings-ai-studio-v1-console.png`，确认默认参数、分段控件、当前默认项和日志控制台均可见。

## 设置页第 2 版：顶栏和底部导航接线

- 时间：2026-05-25
- 页面版本计数：设置页第 2 版迁移；未超过 5 版停下阈值。
- 目标：修复第 1 版遗留的全局顶栏重复和底部导航未直达目标路由问题。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- 改动内容：
  - 设置页内部增加 AI Studio 风格顶栏：返回箭头 + “设置”标题。
  - 全局 `AppTopBar` 对 `Routes.Settings` 退场，避免页面顶部出现两个“设置”标题。
  - `SettingsScreen` 增加 `onNavigateToConnect` 和 `onNavigateToSessions` 回调，底部导航分别接到连接页和线程列表页。
- 注意事项：
  - `CodexMobileApp.kt` 当前已有未提交的详情页旧按钮删除 hunk；本次提交只暂存设置页接线相关 hunk，不混入既有 dirty 改动。
  - 从设置页点“线程”可进入线程列表；在线程列表页点左下角“连接”仍会执行线程列表页现有断开连接逻辑，这是线程列表页已有行为，不属于本次设置页接线问题。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 从连接页点击设置进入设置页并截图 `.\.tmp\settings-ai-studio-v2.png`，确认只剩单个设置顶栏。
  - 点击设置页底部“线程”并截图 `.\.tmp\settings-bottom-sessions-v2.png`，确认能进入线程列表。
  - 在线程列表页点击左下角连接后截图 `.\.tmp\settings-bottom-connect-v2.png`，确认进入连接页并触发当前线程列表断开连接行为。

## 草稿页按 AI Studio 前端基准对齐

- 时间：2026-05-25
- 页面版本计数：草稿页第 1 版迁移；未超过 5 版停下阈值。
- 目标：把 `draft` 路由从复用旧详情页改为独立草稿创建页，对齐 AI Studio 生成版的首屏配置、初始提示词和启动会话布局，背后仍接当前项目真实 draft/session 创建链路。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDraftScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- 实现边界：
  - 保留当前 `startDraftSession`、`selectedDraftSession`、`draftMessage`、图片选择/上传状态和 `sendInput` 创建远端会话的真实流程。
  - 目录、模型、推理强度、速度和文件权限继续接 `AppViewModel` 的 `updateSelectedSession*` 回调。
  - 保留现有详情输入和附件相关 `TestTags`，避免破坏已有测试定位。
  - 不引入 AI Studio 生成项目里的 mock 数据层、Gemini 调用或独立导航框架。
- 改动内容：
  - 新增独立 `SessionDraftScreen`，页面包含 AI Studio 风格顶栏、只读项目/线程名、配置卡片、初始提示词卡片、图片附件托盘和 `Start Session` 主按钮。
  - `CodexMobileApp.kt` 的 `Routes.DraftSession` 改为渲染 `SessionDraftScreen`，发送仍调用 `appViewModel::sendInput`，返回时仍丢弃草稿并回退导航。
  - 右上角三点入口接当前设置页，配置项在草稿页内直接编辑并写入当前草稿状态。
- 遇到的问题：
  - 第一次构建失败：草稿页使用 `FlowRow` 的函数缺少 `ExperimentalLayoutApi` opt-in。已在同一版内补上，不计为新版本。
  - 模拟器安装成功后，连接页点击“尝试连接”仍未进入线程列表；本地 bridge `/health` 可访问但 lifecycle 显示 `restarting/draining`，因此本轮未能在模拟器里直接截图验证 `draft` 路由。后续需要先把 bridge/连接状态排通，再做草稿页实机视觉回归。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。

## 全局底部导航和主页面 Showcase 对齐

- 时间：2026-05-25
- 页面版本计数：应用外壳/导航第 1 版修补；未超过 5 版停下阈值。
- 目标：在连接、线程、设置三个主 tab 之间补齐 AI Studio 生成版的切换逻辑，并让 debug showcase 能直接打开各主页面，避免 bridge 连接状态阻塞视觉验证。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/PrimaryScreensShowcaseActivity.kt`
- 改动内容：
  - 连接页底部“线程”入口接到真实 `Routes.Sessions`，不再使用默认空回调。
  - 连接成功后的自动跳转改为只在用户点击连接按钮后触发；用户从底部导航主动回到连接页时不再被 `LaunchedEffect` 立即弹回线程列表。
  - 线程列表底部“连接”改为导航到连接页，不再断开 bridge；图标从电源改成链接图标，语义与 AI Studio 底部导航一致。
  - `PrimaryScreensShowcaseActivity` 增加 `draft` 和 `settings` 参数，现可直接打开 connection、sessions、draft、settings 四个主页面；详情页仍使用既有 `SessionDetailShowcaseActivity`。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 通过 `adb shell am start -n "com.openai.codexmobile/.PrimaryScreensShowcaseActivity" --es screen <screen>` 分别打开 connection、sessions、draft、settings 并截图：
    - `.\.tmp\showcase-connection-nav-v1.png`
    - `.\.tmp\showcase-sessions-nav-v2.png`
    - `.\.tmp\showcase-draft-nav-v1.png`
    - `.\.tmp\showcase-settings-nav-v1.png`

## 页面迁移完成审计

- 时间：2026-05-25
- 审计范围：
  - AI Studio 参考页面：`ConnectScreen.kt`、`SessionsScreen.kt`、`SessionDraftScreen.kt`、`ChatScreen.kt`、`SettingsScreen.kt`。
  - 当前 Android 真实路由：`connection`、`sessions`、`draft`、`session/{sessionId}`、`settings`。
- 审计结论：
  - 连接页已按 AI Studio 的连接管理首屏、端点卡、主操作和底部导航形态迁移，后端仍接当前 bridge `connect` 流程。
  - 线程列表页已按 AI Studio 的线程 tab、连接条、目录分组、线程卡片、FAB 和底部导航迁移，后端仍接当前 session 列表、归档和草稿创建。
  - 草稿页已独立迁移为 AI Studio 的会话启动页，配置、首条消息、附件和 `Start Session` 仍接当前 draft/session 创建链路。
  - 详情页已迁移为 AI Studio 的 chat 结构：顶栏为返回、刷新、三点；三点菜单已实机确认打开“模型设置”，包含模型、推理、速度、目录和权限；顶部状态只保留连接状态、排队消息、目标状态三段；审批留在消息视口内。
  - 设置页已迁移为 AI Studio 的连接管理、默认偏好、当前默认值和日志控制台结构，底部导航可切到连接和线程。
- 页面版本计数：
  - 连接页第 1 版。
  - 线程列表页第 1 版。
  - 草稿页第 1 版。
  - 设置页第 2 版。
  - 详情页在本轮经历多次修补，但未超过 5 版停下阈值；主要返工点是三点模型设置、三段状态和主题对齐。
- 验证证据：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - 模拟器安装成功。
  - 主页面截图：
    - `.\.tmp\showcase-connection-nav-v1.png`
    - `.\.tmp\showcase-sessions-nav-v2.png`
    - `.\.tmp\showcase-draft-nav-v1.png`
    - `.\.tmp\showcase-settings-nav-v1.png`
  - 详情页截图：
    - `.\.tmp\showcase-detail-final-audit.png`
    - `.\.tmp\showcase-detail-model-menu-final-audit.png`
- 剩余说明：
  - 当前仓库还有 README、docs、旧工作记录、`SessionDetailShowcaseActivity.kt`、`CodexMobileApp.kt` 的既有未提交改动，以及 `mobile_uploads/` 等未跟踪文件；这些不是本次页面迁移审计新增内容。
  - 真实 bridge 连接链路此前一度显示 `restarting/draining`，因此最终视觉审计主要依赖 debug showcase；页面路由、回调接线、构建和单测均已验证。

## 方案1移除外层重复 Insets

- 时间：2026-05-25
- 目标：修复迁移后所有页面顶部和底部出现额外留白的问题，采用方案 1：保留每个页面自己的 `Scaffold`，移除应用外壳和 debug showcase 的外层 `Scaffold` padding 传递。
- 原因确认：
  - `CodexMobileApp` 外层 `Scaffold` 会生成一份系统栏 `paddingValues`。
  - 连接、线程、草稿、详情、设置页面内部也各自使用 `Scaffold(topBar/bottomBar)`，并再次处理系统栏和页面栏位 inset。
  - 外层 padding 传入页面后，系统栏区域被消费两次，表现为顶部标题整体下移、底部导航或输入栏上方/下方多出空白。
- 改动文件：
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/ConnectionScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDraftScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SettingsScreen.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/PrimaryScreensShowcaseActivity.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
- 改动内容：
  - `CodexMobileApp` 外层从 `Scaffold` 改为 `Box`，只承载 `NavHost` 和全局 `SnackbarHost`。
  - 五个页面函数移除 `paddingValues` 入参，页面根 `Scaffold` 不再 `.padding(paddingValues)`。
  - 两个 debug showcase 入口不再包外层 `Scaffold`，直接渲染目标页面，避免截图验证环境再次引入重复 inset。
- 验证：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1` 通过。
  - `cd android; .\gradlew.bat testDebugUnitTest` 通过。
  - `powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug-emulator.ps1` 安装并启动成功。
  - 模拟器截图复核通过：
    - `.\.tmp\showcase-connection-insets-v1.png`
    - `.\.tmp\showcase-sessions-insets-v1.png`
    - `.\.tmp\showcase-settings-insets-v1.png`
    - `.\.tmp\showcase-detail-insets-v1.png`
- 剩余说明：顶部状态栏和底部手势条附近仍会保留系统安全区，这是 Android 系统栏正常行为，不属于重复 padding。
