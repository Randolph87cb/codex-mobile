# codex-mobile-黑暗模式消息可读性方案

- 日期：2026-05-25
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：android/app/src/main/java/com/openai/codexmobile/ui
- 相关 skill：record-and-reflect-review, codex-mobile-android-ui
- 标签：Android, UI, 深色模式, 会话详情

# 目标
- 评估 Android 黑暗模式下会话详情页回复消息的可读性问题。
- 结合现有主题与消息组件，给出一版配色与层级调整方案，暂不直接改代码。

# 现状判断
- 已读取 DESIGN.md、深浅色 theme、SessionDetailScreen 与 TranscriptMarkdown。
- 主要问题不是单个文本样式，而是暗色主题下 transcript 仍使用固定浅色气泡、固定浅色代码块和固定内联高亮底色。
- 当前暗色主题的文字 token 为浅色，叠加浅色消息底后，对比度明显不足。

# 关键证据
- SessionDetailScreen 中 assistant / system / code block 容器色为写死浅色值，没有随暗色主题切换。
- TranscriptMarkdown 中内联 code 背景使用固定 `Color(0x1F000000)`，链接色也没有单独的暗色 token。
- 对比度粗测：
  - 当前暗色 Assistant 气泡正文约 1.08
  - 当前暗色 执行过程标题约 1.50
  - 当前暗色 代码块约 1.01

# 推荐方向
- 推荐改为“消息层 theme-aware”，而不是只在暗色模式下强行把字改深。
- assistant、system、tool、code block 采用暗色专用容器和边框，正文使用浅色前景，次级说明降一级但保持足够对比。
- 内联 code、链接、折叠执行过程标题单独给暗色 token，不复用普通正文色。

# 本轮产出
- 完成可读性问题定位。
- 形成一版可落地的暗色配色调整方案，并按“稳”的方式完成实现。

# 实际修改
- 新增 `ui/theme/TranscriptColors.kt`，集中维护 transcript 深浅色 token。
- `ui/theme/Theme.kt` 注入 transcript 颜色 CompositionLocal。
- `ui/screen/SessionDetailScreen.kt` 改为按 transcript token 渲染 assistant、system、tool、user、code block 的容器与文字颜色。
- `ui/screen/TranscriptMarkdown.kt` 为链接、内联 code 改用主题感知颜色。
- `ui/screen/TranscriptMarkdownTest.kt` 同步更新测试入参。

# 验证
- `powershell -ExecutionPolicy Bypass -File .\\scripts\\build-android-debug.ps1` 通过。
- `cd android; .\\gradlew.bat testDebugUnitTest` 通过。
- 单测过程中发现 `TranscriptMarkdownTest` 仍使用旧函数签名，已同步修复后重跑通过。

# 后续文档更新
- 用户要求只更新两份 UI 文档，不扩散到 README。
- 已更新：
  - `docs/session-detail-ui-notes.md`
  - `docs/android-ui-collaboration.md`
- 文档补充内容：
  - transcript 颜色入口改为 `ui/theme/TranscriptColors.kt`
  - 浅色 / 深色 token 说明
  - 后续协作时不要再在 `SessionDetailScreen.kt` 里写死 transcript 颜色
