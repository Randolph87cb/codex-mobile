## 目标

- 排查 Android 端“给一个 markdown 链接后不能下载文件”的原因。

## 结论

- Android 端当前只把少数几类 Markdown 链接当作“可下载本地文件”：
  - `bridge-file://...`
  - `file://...`
  - Windows 绝对路径
  - `/api/file/download?...`
  - 含 `/api/file/download?...` 的完整 `http(s)` 地址
- 对于以 `/` 开头但不是 `/api/file/download?...` 的链接，当前逻辑会把它当作 bridge 相对 URL，而不是本地文件路径。
- 因此像 Codex 常见的文件链接目标（例如绝对路径或带 `:line` 的路径目标）如果不符合上述规则，就不会进入下载分支。

## 关键定位

- Android Markdown 点击处理：`android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
- Android 文件下载识别与保存：`android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptFileSupport.kt`
- Bridge 文件下载接口：`bridge/src/app.ts`
- Bridge 生成 `bridge-file://` 链接：`bridge/src/session-view.ts`

## 关键判断

- 文件下载能力本身还在，`/api/file/download` 接口也还在。
- 问题核心是 Markdown 链接目标格式和 Android 侧识别规则不匹配，不是最近详情页视觉收口类提交导致的。
- 本次只做只读排查，未修改代码，未执行构建或测试。

## 后续可选修法

- 在 Android 端补充对更多本地路径目标格式的识别。
- 或统一让 bridge / 上游消息只输出 `bridge-file://...` 这类已支持格式。
