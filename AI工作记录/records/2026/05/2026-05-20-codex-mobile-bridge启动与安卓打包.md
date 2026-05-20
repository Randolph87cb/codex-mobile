# codex-mobile bridge 启动与安卓打包

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`scripts/`、`bridge/`、`android/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`bridge`、`自启动`、`Android`、`APK`、`依赖安装`

## 任务输入摘要

- 最终结果：启动 `bridge` 服务，注册成真正的开机自启动，并打包出 Android 安装包。
- 现有素材：仓库现有 `scripts/` 启停与打包脚本、README 说明、项目内工作记录。
- 明确约束：按项目规则先说明执行方案再实施；如果存在缺失依赖或脚本损坏，需要一并修复；如需系统级修改，已得到用户确认。
- 完成标准：`bridge` 后台运行且健康检查通过；计划任务注册完成；Android 构建成功并产出 APK；记录验证结果。
- 产出后动作：更新工作记录；如涉及仓库文件修复，补 git 状态、提交并推送。

## 背景

用户希望把当前仓库在 Windows 主机上直接拉起 `bridge`，注册成开机自启动，并完成 Android 安装包打包。如果构建或启动中存在本地依赖缺失，需要协助下载安装。执行前，用户补充说明此前被杀毒软件查杀过，并已把 `D:\workspace` 设为信任区，允许继续执行和安装依赖。

## 关键过程

- 已确认 `scripts/install-bridge-autostart.ps1` 依赖 `scripts/restart-bridge-background.ps1`。
- 已确认当前仓库 `git status` 显示 `scripts/restart-bridge-background.ps1` 处于删除状态，且 `start-bridge-lan.ps1` 仍直接调用该脚本。
- 已确认当前仓库不存在 `.tools/` 目录，Android 打包脚本预期的 JDK/SDK 依赖很可能需要补装。
- 已从当前 `HEAD` 恢复 `scripts/restart-bridge-background.ps1`，恢复 bridge 后台启动链路。
- 已在 `bridge/` 执行 `npm install`，补齐 Node 依赖，并确认 `node`、`npm`、`codex` 均可用。
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\restart-bridge-background.ps1 -HostAddress 0.0.0.0 -Port 8787 -Runner app-server`，bridge 后台启动成功。
- 已确认当前终端无管理员权限，`install-bridge-autostart.ps1` 无论 `-AtStartup` 还是默认当前用户计划任务模式都返回 `Access is denied`。
- 作为当前权限下的可落地替代方案，已在当前用户 Startup 目录创建 `CodexMobileBridge.cmd`，登录后自动调用项目内 `scripts/restart-bridge-background.ps1`。
- 已下载安装并恢复本地 Android 工具链：
  - `.tools\jdk\jdk-17.0.19+10`
  - `.tools\android-sdk\cmdline-tools\latest`
  - `.tools\android-sdk\platform-tools`
  - `.tools\android-sdk\build-tools\34.0.0`
  - `.tools\android-sdk\build-tools\35.0.0`
  - `.tools\android-sdk\platforms\android-35-ext15`
  - `.tools\gradle\gradle-8.7`
- 已发现当前环境下 Gradle 和 sdkmanager 默认不会自动走本机 `127.0.0.1:7897` 代理；显式注入 `GRADLE_OPTS` / `JAVA_TOOL_OPTIONS` 后，远端依赖解析恢复正常。
- 为让 AGP 正确识别 `compileSdk 35`，已让 Gradle 在构建过程中自动补装 `platforms;android-35`，当前本地同时存在：
  - `.tools\android-sdk\platforms\android-35`
  - `.tools\android-sdk\platforms\android-35-ext15`
- 已分别用项目脚本和 Gradle 命令完成 Android 构建与单测验证。

## 结果

- bridge 已在后台运行，健康检查 `GET /health` 返回 `ok=true`，当前 runner 为 `app-server`。
- bridge 自动启动已在当前权限范围内落地为“当前用户登录后自启动”：
  - 启动项文件：`C:\Users\hetao\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\CodexMobileBridge.cmd`
  - 说明：这不是 `SYSTEM` 级“开机即启动”；真正的计划任务开机启动仍需要管理员权限。
- Android 调试安装包已成功生成：
  - `android/app/build/outputs/apk/debug/app-debug.apk`
- 已恢复和新增的关键文件：
  - `scripts/restart-bridge-background.ps1`
  - `C:\Users\hetao\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\CodexMobileBridge.cmd`

## 验证结果

- 已执行 `Invoke-RestMethod -Uri http://127.0.0.1:8787/health -Method Get`
  - 结果：返回 `ok=true`、`runnerMode=app-server`
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：`BUILD SUCCESSFUL`
- 已执行：
  - `cd android`
  - `$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"`
  - `$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"`
  - `$env:GRADLE_OPTS = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：`BUILD SUCCESSFUL`
- 已执行 `Get-Item android\app\build\outputs\apk\debug\app-debug.apk`
  - 结果：APK 已生成，最新修改时间为 `2026-05-20 11:04:22`
