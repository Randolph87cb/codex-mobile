# Android UI 协作开发说明

本文档面向当前仓库里的协作开发者、后续维护者和 agent。目标不是讲设计理论，而是快速说明：当前 UI 到了什么状态、改哪里、别碰哪里、改完怎么验。

## 适用范围

适用于这些目录下的 Android UI 改动：

- `android/app/src/main/java/com/openai/codexmobile/ui/`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/`
- `android/app/src/main/java/com/openai/codexmobile/ui/theme/`

不适用于：

- `bridge/` 协议和服务逻辑
- `docs/generated/` 协议生成物刷新
- Android 数据层、bridge 数据适配层的大范围行为重构

## 当前 UI 基线

截至 `2026-05-20`，Android UI 已完成这些调整：

- 主题层不再使用默认 `Typography()`，已改为更紧凑的层级排版；
- 浅色 / 深色配色改为更偏“轻量工作台”的低饱和方案；
- `ConnectionScreen`、`SessionListScreen`、`SettingsScreen` 已完成第一轮视觉重构；
- `SessionDetailScreen` 已完成两轮细化：
  - 顶部状态条更紧凑
  - 消息气泡、执行过程、审批卡片的视觉语言更统一
  - 发送按钮已改成小飞机图标

这意味着后续协作时，默认不是“从零设计”，而是在现有基线上继续打磨。

## 项目级 UI Skill

当前仓库内已经有一个项目级 skill：

- `.codex/skills/codex-mobile-android-ui/SKILL.md`

配套参考：

- `.codex/skills/codex-mobile-android-ui/references/ui-rules.md`

建议用途：

- 新人快速理解当前 UI 重构边界
- 多 agent 协作时统一“先看哪里、先改哪里、怎么验”
- 减少把 Android UI 改成通用聊天 App 的风险

这个 skill 的重点不是生成页面，而是约束协作方式。

## 协作边界

### 可以优先改的

- `ui/theme/`
- `ConnectionScreen.kt`
- `SessionListScreen.kt`
- `SessionDetailScreen.kt`
- `SettingsScreen.kt`

### 改之前先确认影响面的

- `CodexMobileApp.kt`
- `AppViewModel.kt`
- `BridgeApi.kt`
- `SessionRepository.kt`
- `RealBridgeDataProvider.kt`

### 不要顺手改的

- `docs/generated/`
- bridge 协议字段命名
- 会影响 Android 既有链路的请求体结构
- 与本次 UI 目标无关的历史记录或脚本

## 当前视觉与交互约定

### 产品气质

- 这是“轻量控制台 / 工作台”，不是社交聊天应用。
- 优先：低认知负担、状态清晰、可调试、反应快。
- 不优先：大面积装饰、复杂动画、拟态或玻璃质感。

### 图标化边界

适合图标优先：

- 设置
- 刷新
- 复制
- 附件
- 展开 / 收起
- 发送

不建议只保留图标：

- 连接 bridge
- 审批通过 / 拒绝
- 删除保存连接
- 断开连接

### 文案

- 用户可见文案默认中文。
- 技术字段名可以保留英文，例如 `Bridge Token`、模型名、`sandboxMode` 选项值。

## 当前重点文件

### 顶层

- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`

作用：

- 顶层路由
- 顶栏
- 页面装配
- 图片选择与消息提示

### 页面

- `ConnectionScreen.kt`
  - 连接状态、入口地址、设置入口
- `SessionListScreen.kt`
  - 目录分组、草稿新建、会话卡片
- `SessionDetailScreen.kt`
  - 状态条、消息气泡、执行过程、审批卡片、输入区
- `SettingsScreen.kt`
  - 已保存连接、默认参数、日志

### 主题

- `Color.kt`
- `Theme.kt`
- `Type.kt`

如果要做统一视觉调整，先看 theme，再看页面。

## 测试与验证

只要改了 `android/`，至少执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1
```

涉及 Android 逻辑、状态流或 UI 数据层时，再执行：

```powershell
cd android
$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"
$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"
.\gradlew.bat testDebugUnitTest
```

## Kotlin 构建缓存异常的当前处理

当前仓库在部分机器上可能遇到 Kotlin daemon / incremental cache 的本地状态异常。出现这类现象时：

- `build-android-debug.ps1` 明明代码没错却在 `compileDebugKotlin` 阶段异常；
- 日志里出现 `Could not close incremental caches`、`Storage ... is already registered`；
- 同样代码在重新跑时可能又恢复。

当前已验证可用的处理方式：

```powershell
cd android
$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"
.\gradlew.bat --stop
```

然后在当前终端临时设置：

```powershell
$env:GRADLE_OPTS = "-Dkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process"
```

再重新执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1
cd android
.\gradlew.bat testDebugUnitTest
```

如果仍然卡在工作区内的 Kotlin 编译缓存，可谨慎清理：

```powershell
Remove-Item -LiteralPath D:\workspace\codex-mobile\android\app\build\kotlin -Recurse -Force
```

这只清理当前仓库的本地编译缓存，不涉及源码文件。

## 推荐协作顺序

1. 先读 `README.md` 和本文件。
2. 看 `.codex/skills/codex-mobile-android-ui/`。
3. 明确本次是“视觉层改动”还是“行为层改动”。
4. 视觉层改动优先改 theme 和 `ui/screen/`。
5. 改完先跑 Android 验证，再更新工作记录和文档。

## 交付时至少要同步的内容

如果本次改动改变了视觉结构或协作方式，至少同步这些内容：

- `README.md`
- 本文档
- 当前线程记录 `AI工作记录/records/YYYY/MM/*.md`

如果本次改动还影响 API 或数据契约，再额外检查：

- `docs/api.md`
- `docs/architecture.md`
