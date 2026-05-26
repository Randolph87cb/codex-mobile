# codex-mobile-线程改名能力核查与UI改图

- 日期：2026-05-26
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
  - `bridge/`
  - `android/`
  - `docs/generated/`
- 相关 skill：
  - `record-and-reflect-review`
  - `codex-mobile-android-ui`
  - `imagegen`
- 标签：
  - app-server
  - 线程改名
  - Android UI

## 任务输入摘要

- 最终结果：核查 `codex app-server` 是否支持修改线程名，并基于现有 Android UI 给出一版改图。
- 现有素材：仓库内 `docs/generated/` 协议生成物、bridge/Android 现有代码、`.tmp/ui-screenshots/` 中的最近页面截图。
- 明确约束：本轮先做只读核查和方案输出，不开始功能实现；用户可见文案保持中文；改图需贴近现有视觉语言。
- 完成标准：明确说明上游是否有能力、当前 bridge 是否已透出、推荐把入口放在哪里，并产出一版修改图。
- 产出后动作：等待用户确认是否进入正式实现。

## 背景

用户希望增加“修改线程名字”能力，先确认上游 `app-server` 是否支持，再基于当前 Android 界面给出一版交互改图。

补充变化：

- 用户随后提供了真实手机截图，要求后续效果图必须基于真机当前 UI，而不是基于仓库里的历史展示图或抽象化界面。

## 关键过程

- 阅读项目 `AGENTS.md`、`record-and-reflect-review` 和 `codex-mobile-android-ui` 规则，确认本轮只做现状核查与方案输出。
- 检查 `docs/generated/ts/v2/Thread.ts`，确认线程结构本身已有可选 `name` 字段，且该字段是“用户可见 thread title”。
- 检查 `docs/generated/ts/v2/ThreadSetNameParams.ts`、`ThreadSetNameResponse.ts`、`ThreadNameUpdatedNotification.ts`，确认上游协议已定义：
  - 请求：`thread/name/set`
  - 参数：`{ threadId: string, name: string }`
  - 通知：`thread/name/updated`
- 检查 `bridge/src/session-view.ts`，确认 bridge 当前会优先用 `thread.name` 组装 `SessionView.title`，说明只要上游 name 变化，bridge 读取到后理论上可以透传给 Android。
- 检查 `bridge/src/app.ts`、`bridge/src/app-server-runner.ts`、`bridge/src/bridge-runner.ts`，确认当前 bridge 只暴露了创建、配置、归档、输入、审批、中断等接口，还没有“改线程名”的 API，也没有消费 `thread/name/updated` 通知。
- 检查 `android/.../RealBridgeDataProvider.kt` 与 `SessionDetail.kt`，确认 Android 数据层已经把 bridge 返回的 `title` 作为 `SessionDetail.title` 和 `SessionSummary.title` 消费，不需要新增展示字段。
- 查看 `.tmp/ui-screenshots/session-detail-showcase-full.png`、`.tmp/ui-screenshots/sessions-showcase-v28.png`、`.tmp/session-detail-model-settings-menu.png`，确认当前详情页顶部为“返回 + 标题 + 刷新 + 更多”，列表页卡片较紧凑，不适合再堆一层主操作按钮。
- 在用户提供真实手机截图后，改为以真机当前页面为唯一视觉基底，保留顶部标题、状态面板、消息气泡和底部输入栏的真实间距、边框和圆角比例，只叠加“改名”交互。

## 结果

- 已确认：`codex app-server` 本身支持修改线程名字。
- 当前结论：
  - 上游已支持，且支持通过通知广播名称变更。
  - 本项目 bridge 尚未暴露 rename 接口，因此 Android 现在还不能直接调用。
  - Android 现有 UI 最合适的首版入口应放在线程详情页顶部，而不是线程列表卡片。
- 推荐首版交互：
  - 在线程详情页标题区增加轻量“编辑名称”入口。
  - 点击后弹出底部弹层或对话框，预填当前线程名。
  - 弹层文案包含“仅修改展示名称，不影响线程 ID”。
  - 操作按钮使用“取消 / 保存”。
  - 列表页保持只读展示，等首版上线后再决定是否补“列表内快捷改名”。
- 已生成一版基于当前详情页的高保真改图预览，表现形式为：
  - 顶部标题右侧增加轻量编辑入口；
  - 底部弹层承载“修改线程名称”表单；
  - 保持当前暖灰背景、白卡片、深蓝主按钮的视觉体系。
- 后续改图基准已切换为“用户提供的真机截图”，需要更贴近当前线上可见 UI，而不是参考仓库旧版样张。

## 建议的正式实现落点

- bridge：
  - `bridge/src/bridge-runner.ts`
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/app.ts`
  - 相关测试
- Android：
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/SessionRepository.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 相关单元测试 / UI 测试

## 验证

- 已完成只读核查：
  - 协议生成物检查
  - bridge 代码路径检查
  - Android 标题消费链路检查
  - 现有 UI 截图检查
- 未执行构建或测试：
  - 原因：本轮未修改业务代码，仅输出能力核查与改图方案。

## Skill 观察

- 是否出现新 skill 候选：否。
- 是否应该优化已有 skill：暂无。
- 触发条件或典型用户说法：想给线程加改名功能，先判断上游是否支持，再出一版基于现状 UI 的改图。

## 后续事项

- [x] 确认 `app-server` 是否支持线程改名。
- [x] 找到 bridge 当前未透出的缺口。
- [x] 产出一版 UI 改图。
- [x] 用户确认后，补 bridge + Android 联动接口与测试。

## 实现更新

- 用户后续确认按“真实手机 UI + 顶栏标题右侧入口”的方案正式实现，并要求完成后同步更新文档。
- bridge 已新增线程改名能力：
  - `bridge/src/bridge-runner.ts` 增加 `renameSessionTitle`
  - `bridge/src/app-server-runner.ts` 调用上游 `thread/name/set`，随后读取最新 thread 详情并返回刷新后的 `SessionView`
  - `bridge/src/app.ts` 新增 `PATCH /api/session/:id/title`
- Android 已接通线程改名链路：
  - `BridgeApi.kt` / `RealBridgeDataProvider.kt` 增加改名请求
  - `AppViewModel.kt` 增加 `renameSelectedSessionTitle`
  - `CodexMobileApp.kt` 把改名动作接到详情页
  - `SessionDetailScreen.kt` 在顶栏标题右侧加入编辑入口，并新增“修改线程名称”对话框
  - `TestTags.kt` 补充改名交互测试标签
- 测试已补：
  - `bridge/tests/app.test.ts`
  - `bridge/tests/app-server-runner.test.ts`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
- 文档已更新：
  - `README.md`
  - `docs/api.md`
  - `docs/session-detail-ui-notes.md`

## 验证更新

- 已执行：
  - `cd bridge && npm run check`
  - `cd bridge && npm test`
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
- 结果：
  - bridge TypeScript 检查通过
  - bridge 单元测试通过
  - Android debug 构建通过
  - Android 单元测试通过
- 备注：
  - Android 构建日志里仍有既有的 `ClickableText` 弃用 warning，本轮未处理
