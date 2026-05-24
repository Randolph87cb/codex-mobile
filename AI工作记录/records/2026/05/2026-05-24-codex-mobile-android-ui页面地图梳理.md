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
