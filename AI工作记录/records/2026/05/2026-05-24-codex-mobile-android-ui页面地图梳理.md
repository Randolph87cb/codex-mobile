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
