# codex-mobile 下载文件确认与进度

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`android/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`下载`、`UI`、`进度`

## 任务输入摘要

- 最终结果：把对话里的文件下载改成“先确认，再下载，并显示进度状态”。
- 现有素材：已存在的本地文件下载能力、会话详情页消息渲染、下载 helper。
- 明确约束：只改 Android 客户端，不动 bridge；保留现有本地文件链接识别能力。
- 完成标准：点击文件链接先弹确认框；确认后显示下载进度与状态；构建和单测通过。
- 产出后动作：更新记录、检查 git 状态、提交并推送。

## 关键修改

- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptMarkdown.kt`
  - 文件链接点击不再直接保存
  - 改为把 `TranscriptFileDownloadRequest` 抛给上层 UI 处理
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - 新增文件下载确认框
  - 新增下载进度对话框
  - 显示准备中 / 下载中 / 保存中 / 完成 / 失败状态
  - 显示已下载字节、总字节和百分比；未知总大小时退化为不确定进度
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptFileSupport.kt`
  - 下载实现从一次性 `bytes()` 改成流式读取
  - 增加下载进度回调
  - 增加字节格式化和进度比例辅助函数
- `android/app/src/main/java/com/openai/codexmobile/ui/TestTags.kt`
  - 为下载确认框和进度框补测试标签
- `android/app/src/test/java/com/openai/codexmobile/ui/screen/TranscriptFileSupportTest.kt`
  - 补充字节格式化和进度比例测试

## 验证结果

- 已执行：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：通过
- APK 产物：
  - `android/app/build/outputs/apk/debug/app-debug.apk`
  - 最新时间：`2026-05-20 17:23:12`

## 结果

- 文件下载现在会先弹窗确认，用户可取消。
- 确认后会显示单独的下载状态窗口，不再是无反馈的直接保存。
- 下载过程中可看到进度和阶段状态，失败时也能直接在窗口里看到错误。

## 风险与后续

- 当前下载中对话框只负责显示状态，没有做“中途取消下载”的控制；如后续需要，可继续接入协程取消和 OkHttp call cancel。
- Markdown 链接点击仍然使用 `ClickableText`，编译会有弃用警告，后续可迁到新的 LinkAnnotation API。
