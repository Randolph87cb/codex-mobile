# codex-mobile 手机端提交图片电脑端直接保存可行性分析

- 日期：2026-05-22
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 本次目标

- 分析当前 codex-mobile 链路里，手机端提交的图片是否能让电脑端直接保存，以及应落在哪一层实现。

## 当前状态

- 已完成 bridge 与 Android 端到端改造，并完成主要验证。

## 初步计划

- 检查 Android 发图入口与提交载荷。
- 检查 bridge 输入处理、host 侧文件能力与目录边界。
- 输出可行方案、所需改动面、风险与建议。

## 检查结果

- Android 端当前会读取用户选中的图片原始字节，并通过 `multipart/form-data` 上传到 `POST /api/attachment/image`，不是只发图片 URL。
- bridge 收到图片后，会先把内容写入 Windows 主机临时目录 `%TEMP%\codex-mobile-bridge\attachments\`。
- 后续 `POST /api/session/:id/input` 提交给会话的并不是二进制，而是 bridge 已暂存图片的 `attachments[].path`。
- `app-server` runner 会把该路径转成 `localImage` 输入块，作为模型可读图片，不是“保存到电脑指定目录”的业务动作。
- 当前仓库已经支持“图片预上传、展示、手机侧预览/保存”，但没有“把手机提交图片直接保存到电脑正式目录”的独立 bridge API 或保存策略。

## 结论

- 严格说，当前不是“电脑完全不能保存”，而是“只能先暂存在 bridge 临时目录，不能按业务语义直接保存到用户指定或约定的正式目录”。
- 这件事可以实现，而且最合适落在 `bridge/`，不建议落在 Android，也不建议直接把原始 `app-server` 文件系统能力暴露给手机端。

## 推荐实现方向

- 方案 A：上传即保存
- 在 `POST /api/attachment/image` 增加可选保存策略，例如保存到当前会话 `cwd` 下的 `mobile_uploads/` 或 bridge 配置的固定目录。
- 方案 B：显式保存
- 保持现有“先暂存再引用”，新增独立接口，例如 `POST /api/attachment/image/:id/save`，由手机端在需要时触发保存。
- 如果需要最小风险和更清晰语义，优先方案 B；如果目标是“发图后电脑端总能立刻看到正式文件”，优先方案 A。

## 用户选择

- 用户已选择方案 A：上传即保存。

## 方案 A 改造建议

- 保持现有上传入口 `POST /api/attachment/image` 不变，但扩展请求字段，允许客户端携带会话上下文，例如 `sessionId`。
- bridge 在接收图片后继续先写临时附件目录，随后基于 `sessionId` 找到会话 `cwd`，再把文件复制到 `<cwd>/mobile_uploads/`。
- `mobile_uploads/` 由 bridge 自动创建；同名文件采用去重规则，避免覆盖。
- 上传接口响应增加 `savedPath`、`savedRelativePath`，让 Android 后续优先引用正式保存路径，而不是临时路径。
- `POST /api/session/:id/input` 保持兼容；Android 改为优先提交 `savedPath`，没有时再回退到临时 `stagedPath`。
- 为了降低风险，保存目标不允许由手机端任意指定，只允许 bridge 根据会话 `cwd` 固定推导。

## 预期改动面

- bridge:
  - `bridge/src/types.ts`
  - `bridge/src/app.ts`
  - `bridge/src/attachment-store.ts`
  - `bridge/src/session-store.ts` 或会话查询相关代码
  - `bridge/tests/app.test.ts`
- android:
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - 对应单元测试

## 关键约束与风险

- 保存目标目录不能让手机端任意指定，必须由 bridge 校验并限制在 `allowedCwds`、会话 `cwd` 子目录或显式配置白名单内。
- 如果允许覆盖、重名处理、子目录创建，需要在 bridge 层统一定义规则。
- 若把“直接保存到电脑”视为高风险主机写入，建议补审批点；如果仅允许落到当前工作区固定子目录，也可以作为低风险自动动作处理。
- 相关改动会同时影响 bridge API 文档、bridge 测试，以及 Android 数据层请求模型。

## 证据

- Android 读取图片字节并准备上传：`android/app/src/main/java/com/openai/codexmobile/ui/ImageAttachmentPreparer.kt`
- Android 先上传图片再发送 `attachments[].path`：`android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`、`android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
- bridge 上传与暂存：`bridge/src/app.ts`、`bridge/src/attachment-store.ts`
- session input 只引用附件 `id/path`：`bridge/src/types.ts`、`bridge/src/app.ts`
- runner 将图片转成 `localImage`：`bridge/src/app-server-runner.ts`
- 协议文档已明确“预上传后引用暂存路径”：`docs/api.md`

## 实际改动

- bridge 上传接口 `POST /api/attachment/image` 新增可选 `sessionId`；如果能解析到会话，就把图片在暂存后正式保存到 `<cwd>/mobile_uploads/`。
- bridge 上传响应新增 `savedPath`、`savedRelativePath`，同时保留旧字段 `path` 兼容旧客户端。
- bridge 在处理 `/api/session/:id/input` 的附件路径时，会优先把已知附件的 `savedPath` 送给 runner，避免 Android 仍传暂存路径时丢失正式保存语义。
- `AttachmentStore` 新增正式保存能力，负责建目录、同名去重、复制文件，以及把暂存路径和正式保存路径都映射回同一附件元数据。
- Android 侧上传图片时会在已选正式会话下附带 `sessionId`。
- Android 侧解析上传响应时兼容三种返回：新 bridge 的 `savedPath` / `stagedPath`，以及旧 bridge 的 `path`。
- Android 侧发送会话输入、图片预览和 transcript 回显，都会优先使用 `savedPath`，没有时再回退 `stagedPath`。
- 补了草稿会话首条消息带图的缺口：如果图片是在还没有真实 `sessionId` 时先上传的，创建会话后会补一次带 `sessionId` 的上传，以拿到正式保存路径。
- 文档与 README 已同步说明自动保存到 `mobile_uploads/` 的新行为。

## 主要改动文件

- bridge:
  - `bridge/src/app.ts`
  - `bridge/src/attachment-store.ts`
  - `bridge/src/types.ts`
  - `bridge/tests/app.test.ts`
- android:
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
- 文档：
  - `docs/api.md`
  - `README.md`

## 验证结果

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过。
- android 单测：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过。
- Android 构建：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 首次执行遇到 Gradle / Kotlin 增量缓存与资源中间目录异常；停止 daemon、清理 `android/app/build` 后重试通过。
  - 最终结果：标准脚本入口通过。

## 当前结论

- 现在的链路已经变成：
  - 手机 -> bridge：上传真实图片二进制；
  - bridge -> 电脑工作区：按会话 `cwd` 自动正式保存到 `mobile_uploads/`；
  - 对话输入 -> runner：优先提交正式保存后的图片路径；
  - UI / transcript：仍然表现为“有图”，不是裸路径文本。

