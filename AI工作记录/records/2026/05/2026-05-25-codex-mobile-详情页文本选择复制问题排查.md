# 详情页文本选择复制问题排查

- 日期：2026-05-25
- 目标：排查 Android 会话详情页文本选择异常，明确“只能复制一个段落”和“点其他位置不会取消选中”的原因，并给出修改方案。
- 当前阶段：已完成实现与验证。
- 约束：遵守项目规则，先给出中文需求理解、根因和修改方案，待用户确认后再进入实现。

## 当前发现

- 会话详情正文的 Markdown 文本在 `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt` 中按块拆分渲染。
- `MarkdownTextBlock()` 会把 heading、paragraph、quote、list item 分别交给 `MarkdownAnnotatedText()`；而 `MarkdownAnnotatedText()` 在 `selectable = true` 时会各自包一层 `SelectionContainer`。
- 结果是同一条消息里的多个段落、引用块、列表项各自形成独立选择域，拖选无法跨域扩展，所以用户体感就是“只能复制一个段落”。
- 会话气泡的“选择文本”模式在 `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt` 中由 `selectionMode` 本地状态控制：
  - 通过长按菜单把 `selectionMode` 设为 `true`；
  - 当前代码没有任何路径把它重新设回 `false`。
- 因为没有退出路径，选中文本后即使点到其他位置，业务层仍保持该气泡处于 selection mode；这也是“点其他位置不会取消选中”的直接原因。
- 代码块 `CodeBlockCard()` 也单独包了一层 `SelectionContainer`，后续如果要统一消息内选择体验，需要一并考虑，避免继续形成嵌套/分裂的选择域。

## 根因定位

### 1. 选择域被切碎

- 文件：`android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
- 现状：
  - `MarkdownTextBlock()` 遍历 block。
  - 每个 block 最终都可能进入 `MarkdownAnnotatedText()`。
  - `MarkdownAnnotatedText()` 自己决定是否包 `SelectionContainer`。
- 影响：
  - 一条消息里的多个自然段不是一个统一选择上下文。
  - 长按后可以进入选中态，但拖选跨不过下一个段落/列表项/引用块。

### 2. 选择模式没有退出机制

- 文件：`android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 现状：
  - `selectionMode` 是 `TranscriptBubbleBodyCard()` 内部状态。
  - 菜单“选择文本”只会开启，不会关闭。
  - 目前没有“点空白处取消”“点其他消息取消”“收起消息时取消”“复制后取消”等回收逻辑。
- 影响：
  - 选区视觉和交互状态容易滞留。
  - 长按菜单在 selection mode 下还会被停用，导致恢复路径更少。

## 实施结果

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
  - 去掉每个 Markdown 子块内部各自包裹的 `SelectionContainer`。
  - 保留链接点击能力，但不再把段落、引用、列表项切成独立选区。

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 把选择态提升为详情页级别的 `activeSelectionBubbleTag`，同一时间只允许一条消息进入文本选择态。
  - 把消息正文和代码块统一包进 `TranscriptPartsColumn()` 外层的单个 `SelectionContainer`，允许同一条消息内跨段落、列表、引用和代码块连续选择。
  - 在详情页内容根容器增加点击命中判断：点击当前激活消息外部时，自动退出选择态。
  - 在折叠消息、普通“复制”菜单项执行后，也会主动清理选择态。
  - 增加测试兼容默认参数，修复现有 `androidTest` 中针对旧 `SessionDetailScreen` 签名的编译失败。

- `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - 新增消息正文容器 tag，供选择态测试定位。

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptSelection.kt`
  - 新增纯函数选择命中判断，便于单元测试覆盖。

- `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptSelectionTest.kt`
  - 覆盖“点击当前消息内部不退出”“点击外部退出”“缺少布局边界时不误清理”三类选择态命中逻辑。

- `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailSelectionModeTest.kt`
  - 新增 Compose 仪器测试源码，覆盖“长按打开菜单进入选择态”“点击输入框退出选择态”。

## 建议验证

- 已执行：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat compileDebugAndroidTestKotlin`
- 结果：
  - 均通过。
  - `compileDebugAndroidTestKotlin` 首次失败暴露的是仓库内旧测试调用旧签名的问题，已通过补兼容默认参数恢复到可编译状态。

## 后续动作

- 建议在真机或模拟器上手动补一轮交互确认：
  - 同一条消息内跨两个段落拖选复制。
  - 点击输入框、顶部状态区域、另一条消息，确认旧选区都会退出。
  - 含链接消息在非选择态仍可正常点击打开。

## 文档同步

- 已更新：
  - `README.md`
  - `docs/session-detail-ui-notes.md`
  - `docs/android-ui-collaboration.md`
- 新文档口径：
  - 详情页“选择文本”现在是消息级统一选择态，不再按段落切碎。
  - 同一条消息内可跨正文段落、列表、引用和代码块连续选择。
  - 点击输入框、消息外区域、执行普通复制或收起当前消息后，会退出选择态。
