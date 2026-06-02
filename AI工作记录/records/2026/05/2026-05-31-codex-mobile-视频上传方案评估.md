# codex-mobile 视频上传方案评估

- 日期：2026-05-31
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
  - `android/`
  - `bridge/`
  - `docs/`
- 相关 skill：
- 标签：
  - 视频上传
  - 附件链路
  - 方案评估

## 本次目标

- 查看当前项目附件上传现状。
- 分析“仅支持图片”扩展到“支持视频”需要改哪些层。
- 按确认后的第一阶段方案完成实际实现与验证。

## 当前状态

- 当前已支持图片附件上传、会话引用、图片预览与图片保存。
- 已新增第一阶段视频附件链路：
  - Android 可选择并预上传视频；
  - bridge 可接收视频并保存到会话 `cwd/mobile_uploads/`；
  - 会话 transcript 会追加视频文件链接；
  - Android 可沿用现有 transcript 文件下载能力下载视频。
- 当前仍未把视频作为 `codex app-server` 的模型输入块提交，上游视频理解能力仍待第二阶段确认。

## 检查结果

- Android 端图片附件链路是专用实现，不是通用媒体实现：
  - `ImageAttachmentPreparer.kt` 只接受图片，并用 `BitmapFactory` 校验宽高。
  - `BridgeApi.kt`、`RealBridgeDataProvider.kt` 只有 `UploadImageAttachmentRequest` / `uploadImageAttachment()`。
  - `AppViewModel.kt` 的待上传状态、文案、补传逻辑都绑定在 `pendingImageAttachments`。
  - `CodexMobileApp.kt` 通过 `GetMultipleContents("image/*")` 选择图片。
  - 草稿页和详情页的附件 UI 也都是图片专用命名和状态。
- bridge 端上传接口也是图片专用：
  - `POST /api/attachment/image`
  - `AttachmentStore` 只存 `UploadedImageAttachment`
  - MIME 白名单只接受 `image/jpeg|png|webp|gif|bmp`
  - 会话输入里的 `ResolvedSessionInputAttachment.kind` 只有 `"image"`
- runner 当前只会把附件转成 `localImage` 输入块：
  - `app-server-runner.ts` 中非图片附件会被忽略。
- transcript 与下载侧已有可复用能力：
  - Android 已支持点击 transcript 中的本地文件链接并下载到手机。
  - bridge 已有 `/api/file/download`，可下载允许范围内的本地文件。

## 已完成实现

- bridge：
  - 新增 `POST /api/attachment/video`；
  - 附件存储从图片专用扩到图片/视频双类型；
  - 会话输入附件解析支持 `kind = "video"`；
  - 非图片附件不会送入 runner 的 `localImage` 块，而是转成 transcript 文件链接；
  - 已补对应测试。
- Android：
  - 新增 `VideoAttachmentPreparer.kt`，通过临时文件准备上传，避免整文件读入内存；
  - `BridgeApi` / `RealBridgeDataProvider` 新增视频上传请求与响应模型；
  - `AppViewModel` 新增待上传视频状态、重试、移除、草稿补传与发送整合；
  - 草稿页、详情页、debug showcase 页面新增“选视频”入口与待上传视频展示；
  - 已补单元测试与回放/展示环境接线。
- 文档：
  - 更新 `README.md`
  - 更新 `docs/api.md`

## 验证结果

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过
- Android：
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过

## 预期改动面

- Android：
  - `CodexMobileApp.kt`
  - 新增视频准备器，参考 `ImageAttachmentPreparer.kt`
  - `BridgeApi.kt`
  - `RealBridgeDataProvider.kt`
  - `AppViewModel.kt`
  - `SessionDraftScreen.kt`
  - `SessionDetailScreen.kt`
  - 对应单元测试
- bridge：
  - `bridge/src/types.ts`
  - `bridge/src/attachment-store.ts`
  - `bridge/src/app.ts`
  - `bridge/src/session-view.ts`
  - `bridge/tests/app.test.ts`
  - 视是否进入第二阶段，再决定是否改 `bridge/src/app-server-runner.ts`
- 文档：
  - `docs/api.md`
  - `README.md`

## 风险与约束

- 目前最关键的不确定性在上游：尚未确认 `codex app-server` 是否支持本地视频附件输入。
- 视频体积通常远大于图片，现有 `bodyLimit`、上传超时、手机内存占用策略都需要重估。
- Android 当前图片准备器是一次性读入完整字节；若视频继续沿用，会有明显 OOM 风险。
- 安全边界应保持不变，正式保存目录仍应固定为会话 `cwd/mobile_uploads/`，不允许手机端任意指定主机路径。

## 后续建议

- 如果目标仍停留在第一阶段，现在已经具备可交付闭环。
- 如果要进入第二阶段，需要先确认 `codex app-server` 对本地视频输入的真实协议要求，再决定：
  - runner 是否扩到 `localVideo`；
  - transcript 是否保留“文件链接 + 文本提示”的兼容模式；
  - 是否要增加手机端内联视频预览。
