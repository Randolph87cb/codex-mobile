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
- `AI工作记录/`: 项目内工作记录

## 当前状态

当前主链路已经不是纯文本 MVP，而是具备以下能力：

- `bridge` 同时支持 `mock` 与 `app-server` 两种 runner；
- Android 客户端已接通会话列表、会话详情、实时流、文本发送和状态恢复；
- 已支持图片预上传、图片展示、预览和保存；
- 已支持历史线程详情、结构化执行过程展示、待审批恢复与自动审批兜底；
- Android 会话详情已支持 Markdown 展示、复制整条消息、复制代码块，以及长按选择部分文本复制；
- 上游响应流的可重试断流会显示为“正在重试”，不再直接误报成终态失败；
- `bridge` 已支持平滑重启窗口，Android 会在重启后自动重连并刷新快照；
- `bridge` 已支持后台常驻启动与登录自启动脚本；
- 已生成本地 `app-server` schema 到 `docs/generated/`，用于后续协议对齐；
- 已镜像上游 `codex-rs/app-server/README.md` 到 `docs/upstream/codex-app-server/README.md`，方便本地查看协议说明。

## 设计原则

- 只暴露自定义 `bridge` API，不直接把原始 `codex app-server` 暴露给手机；
- 默认按内网 / Tailscale 使用，不默认公网暴露；
- `bridge` 设计上保留审批与权限控制能力；当前 Android 客户端为了减少移动端卡在审批/权限设置上的中断，会把会话统一同步为自动审批和完全权限；
- 首先优化“连得快、看得快、控制快”，而不是功能堆叠。

## 下一步

1. 继续收紧公网暴露场景下的安全、鉴权和运维文档。
2. 继续提升移动端状态识别、恢复能力与会话恢复一致性。
3. 视需要把 bridge 关键运行态进一步外置，降低重启时的状态损失面。

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
- Android 端收到这段窗口内的生命周期事件后，会把它识别为“bridge 正在重启”，并在旧连接断开后自动重连和刷新快照。

如果 bridge 配置了 `CODEX_MOBILE_AUTH_TOKEN`，Android 端需要先进入“设置”页填写 token，再发起连接并进入会话详情页实时流。

bridge 后台模式会在需要时直接拉起 `codex.exe app-server`，不依赖桌面版 Codex UI 先手动打开；但它依赖当前用户环境里能找到 `codex.exe`，并且该用户下的 Codex 本地登录态可用。

### Android 会话详情行为

当前 Android 详情页已经不是纯文本展示，常用交互包括：

- 图片附件预览与保存；
- 常见 Markdown 展示：标题、列表、引用、粗体、斜体、删除线、行内代码；
- “复制消息”与“复制代码”快捷入口；
- 长按选择正文、代码块或审批摘要中的部分文本后复制；
- 上游响应流短暂中断时显示“正在重试”，而不是直接标成终态错误。

当前取舍：

- 为了优先满足“长按选择部分文本”，正文里的 Markdown 链接当前保留样式标注，但不作为点击优先控件处理。
- 手机端会把所有会话统一收敛为 `approvalMode=auto` 与 `sandboxMode=danger-full-access`，设置页和详情页不再提供这两个项的编辑入口。

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
