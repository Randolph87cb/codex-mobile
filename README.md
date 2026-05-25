# codex-mobile

面向 `Windows host + Android client` 的轻量 Codex 远程控制项目。

当前仓库目标：

- 在 Windows 本机运行一个 `bridge` 服务；
- `bridge` 负责拉起并管理本地 `codex app-server`；
- Android 原生客户端通过受控接口连接 `bridge`；
- 优先做低延迟、可调试的 Codex 远控链路，不追求完整 ChatGPT mobile 体验。

## 目录

- `bridge/`: Windows 侧 companion service
- `android/`: Android 原生客户端
- `docs/`: 架构与协议文档
- `.codex/skills/`: 项目内 Codex skill
- `DESIGN.md`: Android UI 设计规范入口，当前指向项目内 skill reference
- `mobile_uploads/`: 手机上传图片的本地保存目录，默认被 Git 忽略
- `AI工作记录/`: 项目内工作记录

## 当前状态

当前主链路已经不是纯文本 MVP，而是具备以下能力：

- `bridge` 同时支持 `mock` 与 `app-server` 两种 runner；
- Android 客户端已接通会话列表、会话详情、实时流、文本发送和状态恢复；
- Android UI 已完成一轮主题与页面层次重构，并对会话详情页做了第二轮视觉细化；
- 已支持图片预上传、图片展示、预览和保存；
- 手机端图片在上传到 bridge 后，可按会话 `cwd` 自动保存到电脑侧 `mobile_uploads/`；
- 已支持历史线程详情、结构化执行过程展示、待审批恢复与自动审批兜底；
- 会话详情里的执行过程现在作为 Codex 回复的一部分展示；只有相邻连续的执行过程会合并，中间插入 Codex 文字回复后会重新开始下一段执行过程；
- 已支持线程级 goal 模式：手机端可开始目标、查看状态、暂停 / 恢复、清除目标；
- Android 会话详情已支持 Markdown 展示、复制整条消息、复制代码块，以及长按进入统一文本选择态；同一条消息内可跨段落、列表、引用和代码块连续选择；
- 会话详情页底部发送按钮当前使用小飞机图标，不再显示“发送”文字；
- 会话详情页已支持独立“终止当前轮”按钮；运行中继续点击发送会进入排队，而不是替换成终止；
- 会话详情页顶部状态区已拆成两行，分别展示 `bridge 状态 / 同步方式 / 会话状态` 与 `排队消息 / 目标状态`；
- 会话详情页的连接层断联已改为顶部轻通知，不再把 `Broken pipe` 之类传输层错误写进消息流；
- Android 线程列表页和会话详情页顶栏已支持全局剩余额度指示器：用 `5 小时 / 1 周` 两个颜色点表示大致使用量，点击后展开查看剩余百分比与重置时间；
- Android 会话列表已支持“当前 / 已归档”切换，以及单条归档 / 恢复归档；
- 上游响应流的可重试断流会显示为“正在重试”，不再直接误报成终态失败；
- `bridge` 已支持平滑重启窗口，Android 会在重启后自动重连并刷新快照；
- `bridge` 已支持后台常驻启动与登录自启动脚本；
- `bridge` 已提供 `GET /api/account/quota`，对上游 `account/rateLimits/read` 做稳定化封装；
- 已生成本地 `app-server` schema 到 `docs/generated/`，用于后续协议对齐；
- 已镜像上游 `codex-rs/app-server/README.md` 到 `docs/upstream/codex-app-server/README.md`，方便本地查看协议说明。

## 设计原则

- 只暴露自定义 `bridge` API，不直接把原始 `codex app-server` 暴露给手机；
- 默认按内网 / Tailscale 使用，不默认公网暴露；
- `bridge` 设计上保留审批与权限控制能力；当前 Android 客户端为了减少移动端卡在审批/权限设置上的中断，会把会话统一同步为自动审批和完全权限；
- 线程删除当前仍不暴露；会话整理优先使用归档，而不是物理删除；
- 首先优化“连得快、看得快、控制快”，而不是功能堆叠。

## 图片上传即保存

当前手机端提交图片后的实际链路是：

1. Android 先把真实图片字节上传到 `bridge`。
2. `bridge` 先把图片写入临时附件目录。
3. 如果上传时已经带了真实 `sessionId`，`bridge` 会继续把图片正式保存到当前会话 `cwd/mobile_uploads/`。
4. 后续会话输入优先提交正式保存后的本地图片路径，而不是再次上传图片二进制。

当前规则：

- 正式保存目录固定为当前会话 `cwd` 下的 `mobile_uploads/`。
- `mobile_uploads/` 视为本地运行资产，仓库默认忽略，不提交到 Git。
- 客户端不能直接指定 Windows 主机上的任意保存路径。
- 同名文件会自动去重，不覆盖已有文件。
- 如果 Android 还处于草稿会话，首张消息发送前会先创建真实会话，再补一次带 `sessionId` 的图片上传，以拿到正式保存路径。
- 如果是旧客户端或旧 bridge，没有 `savedPath` 时仍会回退到原有暂存路径链路。

## 下一步

1. 继续收紧公网暴露场景下的安全、鉴权和运维文档。
2. 继续提升移动端状态识别、恢复能力与会话恢复一致性。
3. 视需要把 bridge 关键运行态进一步外置，降低重启时的状态损失面。

## 协作开发入口

如果当前任务和 Android UI 有关，优先读：

- `docs/android-ui-collaboration.md`
- `docs/thread-archive-collaboration.md`
- `DESIGN.md`
- `.codex/skills/codex-mobile-android-ui/SKILL.md`
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/`
- `android/app/src/main/java/com/openai/codexmobile/ui/theme/`

这几处说明了当前 UI 基线、设计规范入口、线程归档语义、协作边界、项目级 skill 和验证方式，适合作为多人或多 agent 协作的统一入口。

## Bridge 启动

### Mock 模式

```powershell
cd D:\workspace\codex-mobile\bridge
npm install
npm run dev
```

### app-server 模式（开发）

```powershell
cd D:\workspace\codex-mobile\bridge
$env:CODEX_MOBILE_RUNNER = 'app-server'
npm run dev
```

默认监听 `http://127.0.0.1:8787`。

说明：

- 这里的 `npm run dev` 更适合本地开发和调试 TypeScript 代码。
- 如果是长期常驻、真机联调、Cloudflare 反代或登录自启动，优先使用后面的后台脚本，而不是长期挂 `dev` 模式。

## Bridge 安全控制面

bridge 当前提供两项最小安全控制，默认都关闭，便于保持 Android MVP 现有联调方式：

- `CODEX_MOBILE_AUTH_TOKEN`：配置后，除 `GET /health` 外的所有 `/api/*` 都要求 `Authorization: Bearer <token>`。
- `CODEX_MOBILE_ALLOWED_CWDS`：配置后，`POST /api/session` 的 `cwd` 必须是白名单内的绝对路径。多个目录使用分号 `;` 分隔。

PowerShell 示例：

```powershell
cd D:\workspace\codex-mobile\bridge
$env:CODEX_MOBILE_AUTH_TOKEN = 'replace-with-a-long-random-token'
$env:CODEX_MOBILE_ALLOWED_CWDS = 'D:\workspace\codex-mobile;D:\workspace\other-safe-root'
$env:CODEX_MOBILE_RUNNER = 'app-server'
npm run dev
```

默认行为：

- 不设置 `CODEX_MOBILE_AUTH_TOKEN`：`/api/*` 不做 token 鉴权。
- 不设置 `CODEX_MOBILE_ALLOWED_CWDS`：bridge 不限制创建会话时的 `cwd`，当前 Android 默认请求体里的相对路径行为保持不变。
- `/health` 始终可访问，并返回当前是否启用了 token 与白名单，便于局域网诊断。

当前 Android 客户端已经支持填写并携带 Bearer token。

### 手机联调

如果要让真机直接连接 Windows 主机，请不要只监听 `127.0.0.1`。直接运行：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\start-bridge-lan.ps1
```

这会用 `app-server` 模式把 bridge 在后台重启到 `0.0.0.0:8787`，并把日志写到：

```text
D:\workspace\codex-mobile\.logs\bridge\bridge-stdout.log
D:\workspace\codex-mobile\.logs\bridge\bridge-stderr.log
```

如果要手动停止后台 bridge：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\stop-bridge-background.ps1
```

如果要后台重启并重新构建 bridge：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\restart-bridge-background.ps1
```

说明：

- 当前后台重启脚本会先请求旧 bridge 进入短暂 `drain` 窗口，再停止旧进程并拉起新进程。
- `drain` 窗口内，bridge 会向当前实时流订阅广播生命周期事件，并拒绝新的写操作请求，避免切换中途写入丢失。
- `GET /api/account/quota` 属于只读状态接口，当前不会因为 `drain` 窗口被拦截；这样 Android 仍能保留和刷新额度快照。
- Android 端收到这段窗口内的生命周期事件后，会把它识别为“bridge 正在重启”，并在旧连接断开后自动重连和刷新快照。
- 当前后台脚本会优先把 `CODEX_EXECUTABLE` 解析到全局 npm 的 `@openai/codex/bin/codex.js` 或 `codex.cmd`，再由 bridge 按正确方式拉起 `app-server`；这样可以绕开 WindowsApps 别名导致的 `spawn ENOENT`、`spawn EPERM`、`spawn EFTYPE`。

如果 bridge 配置了 `CODEX_MOBILE_AUTH_TOKEN`，Android 端需要先进入“设置”页填写 token，再发起连接并进入会话详情页实时流。

bridge 后台模式会在需要时直接拉起本地 Codex CLI 的 `app-server`，不依赖桌面版 Codex UI 先手动打开；但它依赖当前用户环境下的 Codex 本地登录态可用。

如果你的 Cloudflare Tunnel 部署在局域网另一台机器上，也没问题。当前推荐链路是：

1. Windows host 上的 bridge 常驻监听 `0.0.0.0:8787`
2. Tunnel 所在机器把公网请求转发到这台 Windows host 的 `8787`
3. Android 可以走内网地址，也可以走你的公网域名

### 常见排障

- `/health` 返回 `200`，但 `GET /api/sessions` 失败：
  - 优先看 `D:\workspace\codex-mobile\.logs\bridge\bridge-stderr.log`
  - 常见原因不是手机网络，而是 host 上 bridge 拉起 `codex app-server` 失败
- 看到 `spawn codex.exe ENOENT`：
  - 说明后台进程没找到可用的 Codex CLI
  - 先执行一次 `powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\restart-bridge-background.ps1`
- 看到 `spawn EPERM` 或 `spawn EFTYPE`：
  - 通常是把 WindowsApps 里的 `codex.exe` 或 npm 的 `codex.js` 当成了错误的启动目标
  - 当前仓库里的后台脚本和 bridge 已修复这个问题；重新构建并重启后台 bridge 即可
- 手机已经能连 `/health`，但打开会话详情时报 `goal-not-supported`：
  - 说明当前 host 的 Codex 运行环境没有可用的 goal 能力
  - bridge 会自动降级，不再把详情接口打成 `500`

### Android 会话详情行为

当前 Android 详情页已经不是纯文本展示，常用交互包括：

- 图片附件预览与保存；
- 手机端上传图片后，bridge 可把原图正式保存到当前会话工作目录下的 `mobile_uploads/`；
- 常见 Markdown 展示：标题、列表、引用、粗体、斜体、删除线、行内代码；
- “复制消息”与“复制代码”快捷入口；
- 顶部两行状态区：
  - 第一行显示 `bridge 状态 / 同步方式 / 会话状态`
  - 第二行显示 `排队消息 / 目标状态`
- 顶栏右侧的全局额度指示器：
  - 左点表示 `5 小时`
  - 右点表示 `1 周`
  - 颜色表示当前使用量区间，展开后显示“剩余百分比 + 重置时间”
  - 刷新失败时保留上次成功快照，并提示当前 bridge / 网络状态
- 线程目标卡片：可开始目标、查看 token 预算与已用量、暂停 / 恢复、清除；
- 运行中保留“继续输入并排队”的能力，同时提供独立“终止当前轮”按钮；
- 执行过程以 Codex 回复样式显示在左侧消息流中，而不是独立系统卡片；
- 只有连续出现的执行过程才会合并；如果中间出现一条 `Codex：...` 文字回复，前一段执行过程会结束，后续过程会单独形成下一条执行过程；
- 长按后可让当前消息正文进入统一选择态；同一条消息内可跨正文段落、列表、引用和代码块连续拖选，再使用系统菜单复制局部文本；
- 进入选择态后，点击输入框、其他区域或收起当前消息会退出选择态；
- 上游响应流短暂中断时显示“正在重试”，而不是直接标成终态错误。
- 如果详情页实时流断开后又成功恢复，客户端会在重新接入后主动补一次会话详情快照，把断线窗口里漏掉的正文补回来；连接层异常本身只显示在顶部状态区轻通知里，不再追加到 transcript。

当前取舍：

- 为了优先满足“长按选择部分文本”，正文里的 Markdown 链接当前保留样式标注，但不作为点击优先控件处理。
- 设置页当前保留 `approvalMode` 与 `sandboxMode` 的显式配置入口；详情页仍以目录、模型、推理强度和速度的快速调整为主。
- `goal` 目前只放在会话详情页，不放到全局设置页；因为它是线程级状态，不是默认启动参数。

### Android UI 协作约定

当前推荐把 Android UI 改动分成两类：

- 视觉层改动：
  - 优先改 `ui/theme/`、`ConnectionScreen.kt`、`SessionListScreen.kt`、`SessionDetailScreen.kt`、`SettingsScreen.kt`
  - 不要顺手改数据层、bridge 协议或 `docs/generated/`
- 行为层改动：
  - 先确认是否会影响 `AppViewModel.kt`、`BridgeApi.kt`、`SessionRepository.kt`、`RealBridgeDataProvider.kt`
  - 需要同步检查测试与文档

当前项目内已经提供一个面向本仓库的 UI skill：

- `.codex/skills/codex-mobile-android-ui/`

它的作用不是替代设计判断，而是统一协作开发时的改动范围、页面优先级、图标化边界和验证习惯。

### bridge 自启动

默认提供“登录后自启动”计划任务安装脚本：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\install-bridge-autostart.ps1
```

如果需要更接近“开机即启动”的方式，可用 `SYSTEM` 账户注册启动任务：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\install-bridge-autostart.ps1 -AtStartup
```

说明：

- 默认模式是“当前用户登录后自启动”，更适合本项目依赖当前用户环境的场景。
- `-AtStartup` 会注册为 `SYSTEM` 计划任务，通常需要管理员权限。
- 任务实际执行的是后台重启脚本 `scripts/restart-bridge-background.ps1`。

移除自启动任务：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\uninstall-bridge-autostart.ps1
```

## 测试

```powershell
cd D:\workspace\codex-mobile\bridge
npm run check
npm test
```

## Android 调试包

本地调试包可用下面的脚本构建：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\build-android-debug.ps1
```

如果本机 Kotlin daemon 或增量缓存状态异常，当前仓库验证时可临时追加：

```powershell
$env:GRADLE_OPTS = "-Dkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process"
```

必要时可先执行：

```powershell
cd D:\workspace\codex-mobile\android
.\gradlew.bat --stop
```

当前调试包输出路径：

```text
D:\workspace\codex-mobile\android\app\build\outputs\apk\debug\app-debug.apk
```

调试包签名说明：

- 仓库现在内置固定的 debug keystore：`android/signing/debug.keystore`
- 不同电脑执行同一份 `debug` 构建时，会得到同一套签名，可直接覆盖安装到手机
- 如果手机里之前装的是“旧机器默认 debug keystore”签出来的版本，首次切换到当前固定签名时仍需要先卸载一次；切换完成后，后续跨机器更新就不需要再卸载

## 本地 Android 模拟器测试

如果不想每次都手工把 APK 装到真机，可以直接使用仓库内的本地模拟器环境。

### 当前约定

- 项目内 Android SDK 已补齐 `emulator`
- 启动脚本默认使用 AVD 名称：`codex-mobile-api35`
- 模拟器数据目录通过 `ANDROID_AVD_HOME` 指向仓库本地资产：`.tmp/android-avd/`
- 如果当前机器下还没有 `codex-mobile-api35`，需要先在上述目录中创建或导入同名 AVD

### 常用脚本

启动模拟器：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\start-android-emulator.ps1
```

把当前 debug APK 安装到模拟器并拉起应用：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\install-android-debug-emulator.ps1
```

一键启动本地 bridge、构建 APK、启动模拟器并安装：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\run-local-android-test.ps1
```

说明：

- `start-android-emulator.ps1` 只负责启动，不会自动创建 AVD。
- 如果要复用当前约定，请确保 `codex-mobile-api35` 已经存在，并且 AVD home 指向 `.tmp/android-avd/`。

### 模拟器连接 bridge

- 模拟器访问 Windows 主机本地服务时，bridge 地址使用：`http://10.0.2.2:8787`
- 如果 bridge 启用了 `CODEX_MOBILE_AUTH_TOKEN`，先在 App 的“设置”页填写 token，再连接 bridge
