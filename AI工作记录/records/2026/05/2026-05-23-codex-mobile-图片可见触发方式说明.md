# codex-mobile 图片可见触发方式说明

- 日期：2026-05-23
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：`android/`
- 相关 skill：`record-and-reflect-review`
- 标签：`android`、`image`、`transcript`、`usage`

## 本次目标

- 说明当前 Android 客户端里，怎样让 Codex 回复后把图片以内联预览方式显示出来。

## 结论

- 当前客户端只会把符合 Markdown 图片语法的内容渲染成图片，即：`![说明](图片来源)`。
- 仅返回纯文件路径、普通文本链接或“我生成了一张图”这类文字，不会触发内联图片预览。
- 支持的图片来源包括：
  - `http://` / `https://`
  - 相对 bridge 路径，如 `/api/image/file?path=...`
  - `bridge-attachment://<id>`
  - `bridge-file://<url-encoded-path>`
  - `data:` URL

## 关键依据

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptBubble.kt`
  - 解析消息内容时，只对 `![alt](source)` 识别为 `TranscriptPart.Image`。
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptImageSupport.kt`
  - 图片加载支持 `data:`、`bridge-attachment://`、`bridge-file://`、相对 `/...` 路径，以及绝对 `http(s)` 地址。

## 备注

- 如果是“你上传给 Codex 的图片”，按当前实现本来就应在发送侧或消息区显示预览，不需要等 Codex 回复后才可见。
- 如果是“Codex 返回的图片”，则需要它把图片地址按 Markdown 图片语法写进回复正文。
