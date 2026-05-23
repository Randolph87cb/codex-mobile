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

## 当前支持的链接目标格式

- `bridge-file://D%3A%5Cworkspace%5Ccodex-mobile%5Creport.md`
- `file:///D:/workspace/codex-mobile/report.md`
- `D:\workspace\codex-mobile\report.md`
- `\\server\share\report.md`
- `/api/file/download?path=D%3A%5Cworkspace%5Ccodex-mobile%5Creport.md`
- `http://10.0.2.2:8787/api/file/download?path=D%3A%5Cworkspace%5Ccodex-mobile%5Creport.md`

## 当前不会触发下载的常见格式

- 相对路径：`./report.md`
- 相对路径：`docs/report.md`
- 普通 bridge 相对 URL：`/docs/report.md`
- 带行号的文件目标：`D:\workspace\codex-mobile\README.md:12`
- Markdown 渲染器点击后才解析的项目内文件链接，如果目标最终不是以上几类格式

## Bridge 端当前会包装成 bridge-file 的来源

- `bridge/src/session-view.ts` 里的 `buildBridgeFileMarkdown(...)` 当前只在两个转录场景被调用：
- `item.type === "imageView"` 时，读取 `item.path`，生成 `[下载原图](bridge-file://...)`
- `item.type === "imageGeneration"` 时，优先读取 `item.savedPath`，否则回退到 `item.path`，生成 `已保存：[文件名](bridge-file://...)`

## Bridge 端当前不会包装成 bridge-file 的来源

- `userMessage` 里的 `localImage` 只会转成 `![xxx](/api/image/file?path=...)`，不是 `bridge-file://...`
- `fileChange` 目前只展示变更路径文本和 diff，不会生成下载链接
- `commandExecution` 输出中的普通路径文本不会自动转成下载链接
- 其他如 `agentMessage`、`plan`、`reasoning`、`webSearch`、`mcpToolCall`、`dynamicToolCall`、`collabAgentToolCall` 也不会自动把路径包装成 `bridge-file://...`

## 额外边界

- 即使 bridge 已经输出了 `bridge-file://...`，Android 端点击后也还要经过 `/api/file/download`
- `/api/file/download` 只允许下载 `attachmentStore` 中的路径，或安全配置 `allowedCwds` 覆盖到的绝对路径
- 路径为空、缺少 `savedPath/path`、或命中安全限制时，最终也无法下载

## 推荐修改方案

- 优先只改 Android 端 Markdown 点击解析，不先扩大 bridge 端 `bridge-file://...` 生成范围
- 在 `TranscriptFileSupport.kt` 里把“可下载文件链接”识别从少数白名单格式，扩展为“所有本地文件型 Markdown 链接”
- 推荐规则：
  - `http://` / `https://` 默认仍按外部链接打开
  - 只有命中 `/api/file/download` 的 `http(s)` 链接继续走下载
  - 其他无 scheme 的链接、`file://`、`bridge-file://`、绝对路径、UNC 路径、会话目录下的相对路径，都解析为本地文件下载
- 需要补充的兼容点：
  - 支持相对路径，如 `docs/a.md`、`./a.md`、`../a.md`
  - 支持编辑器定位后缀，如 `README.md:12`、`README.md:12:3`、`README.md#L12`
  - 支持 `/D:/workspace/...` 这类前导 `/` 的 Windows 绝对路径
  - 支持 `<...>` 包裹过的 Markdown 目标
- 需要把当前会话 `cwd` 继续向下传到 Markdown 点击解析层，供相对路径解析
- 同步补 Android 单测，覆盖绝对路径、相对路径、带行号路径、外部链接不下载等场景

## 后续可选修法

- 在 Android 端补充对更多本地路径目标格式的识别。
- 或统一让 bridge / 上游消息只输出 `bridge-file://...` 这类已支持格式。
