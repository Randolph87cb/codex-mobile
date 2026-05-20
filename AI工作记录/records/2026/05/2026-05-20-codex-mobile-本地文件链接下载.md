# codex-mobile 本地文件链接下载

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`bridge/`、`android/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`bridge`、`下载`、`本地文件`、`Transcript`

## 任务输入摘要

- 最终结果：把对话里显式出现的本地文件链接做成可下载形式，点击后直接保存。
- 现有素材：Android 对话区 Markdown 渲染、bridge 现有图片访问接口、历史 transcript 生成逻辑。
- 明确约束：不做普通文本路径自动识别；只处理显式链接；先评估再实现；保持现有图片预览链路不回退。
- 完成标准：bridge 提供通用文件下载接口；Android 能点击本地文件链接并保存；补对应测试；完成项目验证。
- 产出后动作：更新记录、检查 git 状态、提交并推送。

## 关键决策

- 不把需求实现为“识别所有路径文本”，而是收敛为“显式本地文件链接可下载”。
- 本地文件下载复用 bridge 中转，不让 Android 直接访问宿主机文件系统。
- Android 端只对以下链接走下载保存：
  - `bridge-file://...`
  - Markdown 链接里的 Windows 绝对路径 / UNC 路径
  - bridge 的 `/api/file/download?path=...`
- 历史 transcript 中已知的本地生成文件，补成显式 Markdown 下载链接，避免只显示原始路径。
- 这次没有实际使用 subagent，原因是 `bridge` 接口、Markdown 渲染和测试写集高度耦合，主线程直接串行落地更稳。

## 关键修改

- `bridge/src/app.ts`
  - 新增 `GET /api/file/download?path=...`
  - 复用本地路径白名单判断，返回 `Content-Disposition: attachment`
  - 补通用 MIME 推断
- `bridge/src/session-view.ts`
  - 为 `imageView` / `imageGeneration` 的本地输出补显式下载链接
  - 生成的下载链接统一使用 `bridge-file://...`
- `bridge/tests/app.test.ts`
  - 新增下载接口白名单与响应头测试
- `bridge/tests/session-view.test.ts`
  - 更新已保存图片文件的 transcript 断言，验证下载链接生成
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
  - Markdown 链接改为可点击
  - 本地文件链接点击后触发保存
  - 其他 HTTP/HTTPS 链接继续按外部链接处理
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptFileSupport.kt`
  - 新增本地文件链接识别、bridge 下载请求生成、文件保存到 Downloads 的实现
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 把提示消息回调传入 transcript 文本渲染层
- `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptFileSupportTest.kt`
  - 新增本地文件下载链接解析测试

## 验证结果

- 已执行 `cd bridge; npm run check`
  - 结果：通过
- 已执行 `cd bridge; npm test`
  - 结果：通过，`45` 个测试通过
- 已执行：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过

## 结果

- 对话里的显式本地文件链接现在可以点击并保存到系统下载目录。
- bridge 已具备通用本地文件下载接口，Android 不需要直接接触宿主机文件系统。
- 历史 transcript 中图片生成和查看图片场景，已补显式文件下载链接，不再只有路径文本。

## 风险与后续

- 当前 Markdown 点击实现使用了 `ClickableText`，编译存在弃用警告；后续可升级为 Compose 新的链接注解 API。
- 这次仍然没有做“纯文本路径自动识别”，如果后续需要，应先补更严格的来源和安全边界设计。
- 当前下载路径白名单规则与图片文件访问保持一致；如果后续要进一步收紧，应把“允许下载的文件”单独建模，而不是继续只靠目录白名单。
