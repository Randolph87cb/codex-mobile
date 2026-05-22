# codex-mobile 图片上传失败排查

- 日期：2026-05-22
- 目标：排查聊天详情里 4 张图片中有 2 张持续上传失败、重复重试无效的原因。
- 范围：只读排查 Android 图片预上传链路、bridge 接收链路与现有日志；本次不改业务代码。

## 结论

- 主要原因判断为：失败图片体积过大，触发了 bridge 上传请求体限制；在当前远程接入链路下，还叠加了 Android 侧默认 HTTP 超时，导致用户端只看到“失败/重试”，看不到明确原因。
- bridge 默认把整站 `bodyLimit` 和 multipart `fileSize` 都绑定到 `resolveBridgeBodyLimitBytes()`，默认值为 `32MB`。
- 历史 LAN 日志已经直接记录到多次 `413 Request body is too large`，请求路径同样是 `POST /api/attachment/image`。
- 当前 2026-05-22 的 bridge 日志显示前两张上传成功返回 `201`，后续多次失败重试只有 `incoming request` 没有对应完成行，形态更像大图在远程链路中被超时/中断；虽然本轮日志没有直接落出 413，但与旧日志模式、默认限制和现象一致，仍优先判断为“大图超过链路/服务端可承受范围”。

## 证据

- `bridge/src/app.ts`
  - `bodyLimit: resolveBridgeBodyLimitBytes()`
  - multipart `fileSize: resolveBridgeBodyLimitBytes()`
  - `resolveBridgeBodyLimitBytes()` 默认 `32MB`
- `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - 图片上传使用 `OkHttpClient.Builder()` 默认超时配置
  - 普通 JSON 请求显式 `readTimeout = 10_000`
  - 上传失败时只把 HTTP 状态和响应体写入应用日志，卡片本身不展示细节
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 失败卡片只显示“失败”和“重试”，没有把 `uploadError` 文案直接展示在卡片上
- `.logs/bridge-lan.out.log`
  - 多处 `413 Request body is too large`
  - 同样命中 `POST /api/attachment/image`
- `.logs/bridge/bridge-stdout.log`
  - 本轮有 2 次 `POST /api/attachment/image -> 201`
  - 后续多次失败重试只有 `incoming request`，没有对应完成行
- `mobile_uploads/1000016090.jpg`
  - 当前成功保存样本约 `612220` bytes，说明 bridge 保存逻辑本身可用

## 本次命令

- 读取 `record-and-reflect-review` 与 `delegation-orchestrator` skill 说明
- 查看 `git status --short`
- 搜索 Android / bridge 上传相关代码与测试
- 读取：
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/ImageAttachmentPreparer.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `bridge/src/app.ts`
  - `bridge/src/attachment-store.ts`
  - `android/app/src/main/java/com/openai/codexmobile/diagnostics/AppLogger.kt`
- 检查日志：
  - `.logs/bridge/bridge-stdout.log`
  - `.logs/bridge/bridge-stderr.log`
  - `.logs/bridge-lan.out.log`
  - `.logs/bridge-lan.err.log`
- 检查远程入口响应头与 DNS，确认 `codex.randolph87.top` 当前走 Cloudflare

## 后续建议

- 如果要修根因，优先级建议：
  1. Android 端在选图后先记录字节大小，超过阈值直接给出中文提示，不进入盲目重试。
  2. bridge 为 `FST_ERR_CTP_BODY_TOO_LARGE` 补自定义错误响应，返回明确的“图片过大/当前限制 xx MB”。
  3. UI 在失败卡片或顶部提示中展示 `uploadError` 关键信息，而不只显示“失败”。
  4. 评估是否需要提高 `BRIDGE_BODY_LIMIT_MB`，或在 Android 端先压缩/缩放图片再上传。
  5. 如果继续经 Cloudflare/公网入口上传大图，补大图链路专项验证，确认超时和代理限制。

## 验证状态

- 已完成：代码链路与现有日志交叉排查。
- 未执行：业务代码修改、自动化测试、真机复现。
- 风险：当前这一轮日志没有直接打印 413，因此“当前这两张图是否正好超过 32MB”仍需配合手机端应用日志或原图文件大小做最终确认。

## 已实施修复

- Android：
  - `RealBridgeDataProvider` 为图片上传改用独立 `OkHttpClient`，拉长 `connect/write/read/call timeout`，降低慢网路或大图上传时的客户端超时概率。
  - 图片上传失败时不再直接抛出原始 HTTP 异常文本，而是转换成中文可读错误，例如：
    - `图片过大，当前上限 xx MB。`
    - `图片上传超时，请检查当前网络后重试。`
    - `图片上传连接中断，请稍后重试。`
  - 详情页失败卡片直接展示 `uploadError`，不再只有“失败/重试”。
  - `SessionDetailScreen` 的 goal 回调补默认值，恢复现有 `androidTest` 编译。
- bridge：
  - `/api/attachment/image` 超限时统一返回 `413 image-too-large`，附带 `maxBytes`、`maxMegabytes` 和中文提示。
  - 默认 `bodyLimit` / multipart `fileSize` 从 `32MB` 提高到 `64MB`，降低大图被 bridge 直接拒绝的概率。
- 测试：
  - 补了 bridge 侧“超限返回友好 413”的测试。
  - 补了 Android 侧上传失败消息转换的单元测试。
  - 补了详情页失败卡片展示错误文案的仪表测试断言。

## 实施后验证

- bridge：
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过
- Android：
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - `cd android`
  - `gradlew.bat testDebugUnitTest`
  - `gradlew.bat compileDebugAndroidTestKotlin`
  - 结果：通过
- 未完成：
  - 真机或模拟器执行 `connectedDebugAndroidTest` 未做，本次只验证到 `androidTest` 可编译。
