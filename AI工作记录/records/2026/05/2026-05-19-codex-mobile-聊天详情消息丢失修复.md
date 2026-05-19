# codex-mobile-聊天详情消息丢失修复

- 日期：2026-05-19
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 目标
- 修复 Android 端会话详情在切换会话配置选项后聊天消息消失的问题。
- 修复会话详情历史消息显示不完整、看不到前面消息的问题。

## 当前判断
- Android `mergeSessionDetail` 会用 bridge 返回的简化快照覆盖本地已累积的实时消息。
- bridge `buildThreadTranscriptPreview` 仅保留最后 12 段、最多 4000 字符，导致详情页天然截断历史。

## 计划
- 调整 Android 详情合并逻辑，优先保留更完整的 transcript。
- 调整 bridge 线程详情快照，返回完整可展示 transcript。
- 为 Android 和 bridge 分别补测试并执行验证。

## 实际修改
- Android `AppViewModel.kt`
  - 为 `mergeSessionDetail` 增加 transcript 完整度比较，避免切换模型、推理强度、权限等配置后，被 bridge 返回的简化快照覆盖掉已经看到的聊天内容。
- Android `AppViewModelTest.kt`
  - 新增“切换会话配置不应丢失更完整 transcript”的单测。
- bridge `session-view.ts`
  - `buildThreadTranscriptPreview` 改为返回完整线程 transcript，不再裁剪为“最后 12 段 / 4000 字符”。
- bridge `session-view.test.ts`
  - 新增历史前段消息仍保留的测试。

## 日志补充判断
- 用户补充的 Android 日志显示，`2026-05-19T04:25:38Z` 该会话已收到 `session.started` 且 `status=idle`，说明 bridge 视角下当时并没有继续运行。
- `2026-05-19T04:25:46Z` 先出现 `停止实时流监听`，随后才出现 `Socket closed`，再往后有 `用户主动断开桥接连接`，更像是客户端主动结束订阅或切换页面后的正常收尾，而不是服务端卡死。

## 验证结果
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复
- 用户继续反馈：某些场景下会话实际已经空闲，但手机端仍显示 `running`，导致发送被错误拦截。
- Android `AppViewModel.kt`
  - 在实时流 `StreamClosed` 后，如果当前仍是 `running/awaiting_approval` 且没有活动审批，会立刻走一次 HTTP 快照纠正状态。
  - 在用户点发送前，如果本地仍是忙状态但实时流未连接，会先向 bridge 做一次状态同步；若实际已空闲，则直接发送，不再误判为排队或阻塞。
  - `refreshSessionSnapshot` 现在会同步更新 `sessionRealtimeState.statusText`，避免只改 `selectedSession.status` 而顶部状态条不刷新。
- Android `AppViewModelTest.kt`
  - 新增 `streamClosedRefreshesBusySessionSnapshotToIdle`
  - 新增 `sendInputRefreshesStaleRunningStatusBeforeSubmitting`

## 本次验证补充
- Android 验证需串行执行；并行跑 `testDebugUnitTest` 与 `build-android-debug.ps1` 会争用同一 `app/build` 目录，引发 Kotlin 增量编译缓存锁冲突。
- 已串行执行：
  - `cd android && .\gradlew.bat --stop && .\gradlew.bat testDebugUnitTest`：通过
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复（二）
- 用户继续反馈：手机端消息类型显示不完整，缺少 Codex app 中常见的操作消息，例如运行命令、编辑文件、工具调用进度等，因此难以判断线程是否仍在执行。
- 当前判断：
  - bridge 实时通知只转发了 `assistant.delta`、`turn.started/completed` 等少量事件，没有把 `item/started`、`item/completed`、文件修改进度、工具调用进度、推理摘要等操作项转给 Android。
  - bridge 历史 transcript 只识别 `userMessage` 和 `agentMessage`，会直接丢弃 `commandExecution`、`fileChange`、`mcpToolCall`、`reasoning` 等线程项。
- 实际修改：
  - bridge `session-view.ts`
    - 新增 `formatThreadItemAsTranscriptBlock`，把 `commandExecution`、`fileChange`、`mcpToolCall`、`dynamicToolCall`、`collabAgentToolCall`、`reasoning` 等线程项格式化为 `系统：...` transcript 块。
    - 历史详情页现在会保留这些操作项，而不只显示用户/助手自然语言消息。
  - bridge `app-server-runner.ts`
    - 新增 bridge WebSocket 事件类型 `activity`。
    - 转发 `item/started`、`item/completed`、`item/fileChange/patchUpdated`、`item/mcpToolCall/progress`、`item/reasoning/summaryTextDelta`，让 Android 能实时看到执行中的操作消息。
  - Android `SessionStreamEvent.kt`
    - 新增 `Activity` 事件类型。
  - Android `RealBridgeDataProvider.kt`
    - 解析 bridge 返回的 `activity` 事件。
  - Android `AppViewModel.kt`
    - 收到 `activity` 后，将 bridge 已格式化的 `transcriptBlock` 追加到当前会话 transcript。
    - 同步更新顶部 `lastEventText`，让用户能看到“命令执行”“文件修改进度”等实时摘要。
  - 测试：
    - bridge `session-view.test.ts` 新增操作消息历史展示测试。
    - bridge `app-server-runner.test.ts` 新增 `activity` 事件转发测试。
    - Android `AppViewModelTest.kt` 新增 `activity` 事件追加 transcript 和更新状态摘要测试。

## 本次验证补充（二）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 模拟器联调补充
- 用户要求补做 Android 模拟器真实联调，而不是只看单测和构建。
- 联调环境：
  - Android Emulator：`emulator-5554`
  - bridge 地址：`http://10.0.2.2:8787`
  - 安装方式：`scripts/install-android-debug-emulator.ps1`
- 联调过程中先暴露出一个真实回归：
  - 连接成功后，App 会自动打开会话列表第一条详情。
  - 某些历史线程详情在 bridge `/api/session/:id` 上返回 `500`，错误为 `value?.trim is not a function`。
  - 根因是 `bridge/src/session-view.ts` 的 `normalizeText` 假定字段一定是字符串；历史线程中存在非字符串字段时会在 `trim()` 处崩溃。
- 追加修复：
  - bridge `session-view.ts`
    - `normalizeText` 改为只对字符串执行 `trim()`，对数字/布尔值做安全字符串化，其它类型直接回退，不再因为历史脏数据打爆详情页。
  - bridge `session-view.test.ts`
    - 新增“历史线程项字段不是字符串时也不应崩溃”的测试。
- 模拟器联调结果：
  - 修复后，连接页可以正常进入会话列表。
  - 在 `codex-mobile` 目录下新建草稿线程并发送首条消息后，详情页能实时展示 `推理摘要`、`命令执行`、`文件修改`、`assistant.delta`，最后状态能回到 `idle`。
  - 这说明“操作消息补全”在真实模拟器链路上已经生效，不只是单元测试通过。
- 本次模拟器联调的局限：
  - 由于 `adb input text` 会把空格输入成 `%20` 文本，测试提示词被截断，未完整走到用户原本想要的文件内容写入路径。
  - 但这不影响验证目标：操作消息、状态流转和历史详情崩溃修复都已在模拟器上得到确认。

## 本次验证补充（三）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
- Android 模拟器真实联调：通过
  - 连接 bridge 成功
  - 会话列表和历史详情可打开
  - 详情页可实时看到 `reasoning` / `commandExecution` / `fileChange`
  - 最终状态由 `running` 回到 `idle`

## 后续补充修复（三）
- 用户继续反馈：现在除了文字回复外，其他消息类型应默认折叠，交互上要和 Codex app 对齐。
- 当前判断：
  - Android 详情页已经能显示 `命令执行`、`推理摘要`、`审批结果` 等结构化消息，但默认全部展开，长操作块会占满屏幕。
  - 现有测试只覆盖 transcript 解析，没有直接验证“默认折叠、点击展开”的 Compose 行为。
- 实际修改：
  - Android `TranscriptBubble.kt`
    - 为 transcript bubble 增加 `prefersExpandedByDefault` 和 `summaryLine` 规则。
    - 用户消息、助手普通文字消息默认展开；系统/工具/状态类消息默认折叠。
    - `系统：...` 与首段兜底系统块现在会把第一行拆成标题，后续内容作为可折叠详情。
  - Android `SessionDetailScreen.kt`
    - 在 transcript 列表中为非文字消息增加折叠头，默认只显示标签、摘要和展开/收起箭头。
    - 点击后再显示正文和代码块。
  - Android `TestTags.kt`
    - 为 transcript 折叠头补充稳定 test tag，便于 UI 自动化测试定位。
  - Android `TranscriptBubbleTest.kt`
    - 新增系统操作块标题拆分与默认折叠规则单测。
  - Android `SessionDetailReplayTest.kt`
    - 调整回放断言，验证结构化工具结果标题仍可见，详情默认隐藏。
  - Android `SessionDetailTranscriptCollapseTest.kt`
    - 新增模拟器仪表测试，直接验证“非文字消息默认折叠，点击后展开详情”。

## 本次验证补充（四）
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - 回放链路测试通过
  - 组件级折叠展开仪表测试通过
- 额外说明：
  - 中途尝试用旧历史会话做手动抓图时，抓到过一次旧进程残留页面；强制停进程后重新进入，确认需要以新进程和仪表测试结果为准。

## 后续补充修复（四）
- 用户继续反馈：会话列表里的标题和路径信息过多，要求标题最多显示两行，并且默认把最近回复的会话排在前面。
- 当前判断：
  - Android `SessionListScreen.kt` 当前按工作目录名字母排序目录分组，因此即使某个目录下有最新会话，也可能被排到较后位置。
  - 会话卡片标题和副标题都没有做行数限制，长提示词和长路径会把列表项高度撑得很大。
- 实际修改：
  - Android `SessionListScreen.kt`
    - `groupSessionsByDirectory` 改为先保持组内会话按 `lastUpdated` 倒序，再按“每个目录组里最新会话的 `lastUpdated`”整体倒序排列目录组。
    - 会话标题限制为最多两行，超出使用省略号。
    - 会话副标题限制为单行，且当目录组头已经展示同一 `cwd` 时，列表项副标题不再重复附带目录路径。
    - 时间行限制为单行，避免极端情况下继续把卡片撑高。
  - Android `SessionListGroupingTest.kt`
    - 新增目录组按最新会话时间倒序的单测。
    - 新增列表副标题去掉重复目录路径的单测。

## 本次验证补充（五）
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复（五）
- 用户继续反馈：
  - 对话之间的编辑文件、命令执行等操作消息不应各自平铺，而要合并成一条“执行过程”，内部步骤再单独展开。
  - 当用户上滑查看历史消息时，不应因为新事件到达就自动跳回底部；只有停留在最新消息附近时才自动跟随滚动。
- 当前判断：
  - Android `SessionDetailScreen.kt` 当前直接平铺 `TranscriptBubble`，没有把连续操作消息归并成过程级展示。
  - 详情页滚动状态现在在 transcript 或实时状态变化时无条件 `animateScrollTo(maxValue)`，因此只要有新事件就会抢回底部。
- 实际修改：
  - Android `TranscriptBubble.kt`
    - 新增 `TranscriptDisplayItem` 和 `buildTranscriptDisplayItems`。
    - 将连续的系统操作消息归并为 `ExecutionGroup`，对外显示为一条“执行过程”。
    - 保留每一步的独立标题摘要，用于组内单独展开。
  - Android `SessionDetailScreen.kt`
    - transcript 列表从“单层 bubble”改为“普通对话 bubble + 执行过程组”两层结构。
    - “执行过程”默认折叠；展开后组内每一步依然单独折叠，适配命令执行、文件编辑、审批结果等连续过程消息。
    - 为 transcript 滚动容器增加稳定 test tag。
    - 自动滚动逻辑改为基于“更新前是否仍停留在底部附近”决定；用户上滑查看历史后，新消息到达不会强制跳到底部。
  - Android `TestTags.kt`
    - 新增执行过程组、组内步骤和 transcript scroll 的测试标签。
  - Android `TranscriptBubbleTest.kt`
    - 新增连续操作消息会被合并成一条执行过程的单测。
  - Android `SessionDetailTranscriptCollapseTest.kt`
    - 更新为验证“执行过程”外层折叠和组内单步展开。
  - Android `SessionDetailAutoScrollTest.kt`
    - 新增仪表测试，分别验证“上滑后不自动抢回底部”和“停在底部时继续跟随最新消息”。
  - Android `SessionDetailReplayTest.kt`
    - 调整回放断言，使其与新的“执行过程”结构保持一致。

## 本次验证补充（六）
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - 回放链路测试通过
  - 执行过程折叠/展开测试通过
  - 自动滚动行为测试通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复（六）
- 用户新增图片链路需求：
  - 输入区支持一次选择多张图片。
  - 上传和展示都保留原图，不做压缩。
  - 已选图片要在输入框上方显示缩略图，可点开查看大图。
  - transcript 中的图片也要能显示，并提供保存选项。
- 当前判断：
  - Android 端原先只有单图状态 `pendingImageAttachment`，且 `ImageAttachmentPreparer.kt` 会缩放并转成 JPEG。
  - transcript 只支持文本和代码块，无法把 bridge 返回的图片 Markdown 渲染成缩略图或大图。
  - bridge 已支持附件上传，但历史 transcript 的本地图片还需要输出成 Android 可直接访问的地址。
- 实际修改：
  - Android `ImageAttachmentPreparer.kt`
    - 改为保留原始图片字节和原始 MIME，只做图片有效性校验，不再缩放、不再转 JPEG。
  - Android `AppViewModel.kt`
    - 图片附件状态从单图改为多图列表。
    - 发送时按顺序上传多张原图，并把所有附件 ID 一并发给 bridge。
    - 本地 transcript 追加用户消息时，改为写入 `![name](bridge-attachment://id)` 图片标记，供 UI 渲染。
  - Android `CodexMobileApp.kt`
    - 图片选择器从单选改为多选。
    - 批量处理选中的图片，成功项直接附加，失败项通过 Snackbar 提示。
  - Android `TranscriptBubble.kt`
    - transcript 解析新增 `Image` part，支持识别 Markdown 图片标记。
  - Android `TranscriptImageSupport.kt`
    - 新增图片加载与保存辅助能力。
    - 支持 `data:`、`bridge-attachment://`、bridge `/api/image/file` 与普通 `http/https` 图片来源。
  - Android `SessionDetailScreen.kt`
    - 输入区上方改为多图缩略图托盘，可逐张移除。
    - transcript 中的图片会显示缩略图，点击后弹出大图预览。
    - 预览弹窗增加保存按钮，并通过 Snackbar 返回保存结果。
  - Android 测试：
    - `AppViewModelTest.kt` 覆盖多图上传与 transcript 图片标记。
    - `TranscriptBubbleTest.kt` 覆盖图片 Markdown 解析。
    - `SessionDetailImageRenderingTest.kt` 新增模拟器仪表测试，覆盖多图缩略图、预览和保存。
    - 现有 `SessionDetailAutoScrollTest.kt`、`SessionDetailTranscriptCollapseTest.kt` 同步适配新的 `SessionDetailScreen` 参数。
  - bridge `attachment-store.ts`
    - 收紧图片 MIME 与 Base64 校验，并根据 MIME 自动补扩展名。
  - bridge `app.ts`
    - 增加 `/api/attachment/image/:id/content`，用于返回已上传原图。
    - 增加 `/api/image/file?path=...`，用于返回历史线程里的本地图片文件。
    - 修正图片响应 MIME 映射。
  - bridge `session-view.ts`
    - 历史 `userMessage`、`imageView`、`imageGeneration` 等项现在会输出可渲染的图片 Markdown。
    - 历史本地图片改为使用 bridge `/api/image/file` 地址，而不是 Android 端无法直接取用的自定义协议。
  - bridge 测试：
    - `app.test.ts` 覆盖取回上传图片、非法 MIME / Base64 拒绝、文件白名单访问控制。
    - `session-view.test.ts` 覆盖本地图片与远程图片 Markdown 生成。

## 本次验证补充（七）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
  - `5` 个测试文件通过
  - `35` 个测试用例通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - `SessionDetailImageRenderingTest` 覆盖多图缩略图、预览和保存路径，通过
  - 既有 transcript 折叠、自动滚动、回放链路测试继续通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 模拟器联调补充（二）
- 联调环境：
  - Android Emulator：`emulator-5554`
  - Android 仪表环境：`codex-mobile-api35(AVD) - 15`
- 本轮真实模拟器验证聚焦图片链路：
  - 多图缩略图会显示在输入区上方，而不是只保留单个文件名。
  - transcript 中的图片可点开进入大图预览。
  - 预览弹窗的保存按钮在模拟器里实际可用，相关仪表测试已通过。

## 后续补充修复（七）
- 用户反馈：`Create custom pet` 这类含多张生成图的大线程，在手机端查看图片会闪退。
- 结合日志和真实会话详情，当前判断有两条叠加问题：
  - bridge `session-view.ts` 在处理 `imageGeneration` 线程项时，把协议里的 `result` 原样塞进 transcript；而该字段真实内容是整段 PNG Base64，导致详情页出现超长文本。
  - Android `TranscriptImageSupport.kt` 会把图片完整读入内存，再按原始尺寸直接 `decodeByteArray` 成 `Bitmap`，并把 `bytes + bitmap` 一起放进全局缓存。对大 PNG、spritesheet 或连续多张图，这是明显的 OOM 风险。
- 实际修改：
  - bridge `session-view.ts`
    - 适配协议里的 `savedPath` 字段，`imageGeneration` 现在优先展示“图片内容已生成”“已保存路径”和可点击图片 Markdown。
    - 对疑似 Base64 图片载荷不再原样输出到 transcript，避免把大块二进制文本渲染到详情页。
  - bridge `session-view.test.ts`
    - 新增 `imageGeneration` 使用 `savedPath`、且不会把 Base64 原文泄漏进 transcript 的测试。
  - Android `TranscriptImageSupport.kt`
    - 图片缓存从“原始 bytes + 完整 bitmap”改成“缓存原始 bytes，按展示场景采样解码 bitmap”。
    - 新增缩略图和预览两档最大边长，避免为 220dp 缩略图解完整大图。
    - 支持把 bridge 返回的相对地址 `/api/image/file?...` 自动补成完整 bridge URL。
  - Android `TranscriptImageSupportTest.kt`
    - 新增相对 URL 解析和采样倍率计算单测。
- 影响面说明：
  - 这次优先修的是“历史/生成图片查看闪退”主链路。
  - 待发送附件状态目前仍保留 Base64 于 ViewModel 中，后续如果用户继续反馈“大量待发送原图”场景的内存压力，再单独做第二轮收敛。

## 本次验证补充（八）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
  - `5` 个测试文件通过
  - `36` 个测试用例通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 模拟器与真实会话回归补充
- 对用户给出的真实会话 `019e3eb2-3589-7983-a1d0-eb8f2a4bf68d` 重新抓取 `/api/session/:id`：
  - 原先 `系统：图片生成` 后面的超长 PNG Base64 片段已经消失。
  - 现在返回的是：
    - `图片内容已生成。`
    - `已保存：...generated_images...png`
    - `![...png](/api/image/file?path=...)`
- 模拟器回归继续通过，说明修复后至少没有把新增的相对 URL 支持、采样解码和预览保存路径打坏。

## 后续补充修复（八）
- 用户新增两点图片发送体验问题：
  - 发送前的附件预览卡片太大，图片一多就只能看到前几个。
  - 多图发送时在第二张附件上传阶段报 `java.net.SocketException: Broken pipe`，导致首条消息发不出去。
- 当前判断：
  - 旧链路是“点发送后才顺序上传所有原图”，同时草稿线程还会立即打开详情页、拉快照并建立实时流，首轮会把大文件上传、详情请求和 WebSocket 建连挤在一起。
  - `Broken pipe` 出现在 Android `RealBridgeDataProvider.request()` 写请求体阶段，更像是大请求体上传时连接被对端提前关闭，而不是 Codex 线程本身卡住。
  - 既然发送时真正需要的只是 bridge 侧可访问图片路径，就没必要在点击发送后再做大文件上传。
- 实际修改：
  - bridge `app.ts`、`types.ts`、`attachment-store.ts`
    - `/api/attachment/image` 返回暂存文件 `path`。
    - `/api/session/:id/input` 兼容旧 `attachment.id`，并新增支持直接传 `attachment.path`。
    - 仅允许引用 bridge 已暂存过的图片路径，避免 Android 侧伪造任意主机路径。
  - bridge `app.test.ts`
    - 覆盖旧 `id` 兼容、按 `path` 发送和非法未暂存路径拒绝。
  - Android `AppViewModel.kt`
    - 选图后立刻开始预上传，`pendingImageAttachments` 改为维护 `previewSource / uploadState / stagedPath / uploadError`。
    - 发送前如果仍有 `Uploading` 或 `Failed` 图片，直接阻止发送并提示重试或移除。
    - 草稿线程首条消息改为“先创建会话并完成发送，再刷新列表/进入实时流”，不再与多图上传并发打架。
    - transcript 中本地回显的图片改为写入 `bridge-file://<encoded path>`，直接对应 bridge 暂存文件。
  - Android `RealBridgeDataProvider.kt`
    - 上传响应解析 `path` 字段。
    - 发送消息时附件只提交 `stagedPath`，不再逐张重传图片。
    - 图片上传请求增加 `Connection: close`，并对 `SocketException` 做一次重试，降低 `Broken pipe` 概率。
  - Android `SessionDetailScreen.kt`
    - 待发送图片托盘改为单行横向 `LazyRow` 小缩略图。
    - 每张图显示上传状态；失败项支持单独重试；发送按钮会在上传中/失败时禁用。
  - Android `CodexMobileApp.kt`、`TestTags.kt`、`ReplayHarnessActivity.kt`、`FakeCodexDataProvider.kt`
    - 同步新回调和 staged path 数据结构。
  - Android 测试：
    - `AppViewModelTest.kt` 覆盖“预上传先发生”“上传中阻止发送”“失败后重试再发送”“发送只带 stagedPath”。
    - `SessionDetailImageRenderingTest.kt` 覆盖横向缩略图滚动、失败态重试按钮和发送按钮禁用。
    - 既有 `SessionDetailAutoScrollTest.kt`、`SessionDetailTranscriptCollapseTest.kt` 同步适配新的详情页参数。

## 本次验证补充（九）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
  - `5` 个测试文件通过
  - `38` 个测试用例通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - 真实模拟器：`codex-mobile-api35(AVD) - 15`
  - 覆盖图片横向滚动、预览、失败重试入口、发送按钮禁用
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

## 后续补充修复（十）
- 用户把 bridge 暴露到 Cloudflare 域名后，手机端多图预上传失败，日志显示：
  - `POST /api/attachment/image -> 413 FST_ERR_CTP_BODY_TOO_LARGE`
  - 少量重试后还会出现 `502 Bad Gateway`（Cloudflare 页面）
- 当前判断：
  - 真正的主故障是 bridge 仍在用 `JSON + Base64` 接收原图，Fastify 默认请求体限制偏小，经 Cloudflare 代理后更容易命中限制。
  - 日志里的图片体积约为 `2.5MB ~ 5.4MB` 的 Base64 字符串；Base64 比原始二进制更大，再包一层 JSON，会显著放大请求体。
  - `Socket closed` 的 WebSocket 日志与本次图片失败不是同一条链路，不是主因。
- 实际修改：
  - bridge `package.json`
    - 引入 `@fastify/multipart`，让图片上传走更适合代理链路的 multipart。
  - bridge `app.ts`
    - Fastify 初始化增加 `bodyLimit`，默认提升到 `32MB`，并允许通过 `BRIDGE_BODY_LIMIT_MB` 覆盖。
    - `/api/attachment/image` 新增 multipart 解析，同时保留旧 JSON Base64 兼容。
    - multipart 上传限制与 `bodyLimit` 对齐，减少 Cloudflare 下的大图片 413。
  - bridge `attachment-store.ts`
    - 图片存储入口改为直接接收原始 `Buffer`；旧 Base64 路径通过兼容包装转换。
  - bridge `app.test.ts`
    - 新增 multipart 上传测试，确认新链路可用，旧 JSON 上传继续兼容。
  - Android `BridgeApi.kt`
    - `UploadImageAttachmentRequest` 从 `contentBase64` 改为 `contentBytes`。
  - Android `ImageAttachmentPreparer.kt`
    - 选图后直接保留原始字节，不再把原图编码成 Base64 作为上传载荷。
  - Android `RealBridgeDataProvider.kt`
    - 图片预上传改为 `multipart/form-data`，直接发送原始字节。
    - 仍保留一次 `SocketException` 重试，但主要收益来自不再传 Base64 大 JSON。
  - Android `AppViewModel.kt`
    - 本地预览仍可用，但上传链路改为基于原始字节构造 multipart。
  - Android `AppViewModelTest.kt`
    - 同步更新图片请求构造，覆盖新的 `contentBytes` 路径。

## 本次验证补充（十）
- `cd bridge && npm run check`：通过
- `cd bridge && npm test`：通过
  - `5` 个测试文件通过
  - `39` 个测试用例通过
- `cd android && .\gradlew.bat testDebugUnitTest`：通过
- `cd android && .\gradlew.bat connectedDebugAndroidTest`：通过
  - 真实模拟器：`codex-mobile-api35(AVD) - 15`
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过

