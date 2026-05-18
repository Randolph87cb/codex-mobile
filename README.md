# codex-mobile

面向 `Windows host + Android client` 的轻量 Codex 远程控制项目。

当前仓库目标：

- 在 Windows 本机运行一个 `bridge` 服务；
- `bridge` 负责拉起并管理本地 `codex app-server`；
- Android 原生客户端通过受控接口连接 `bridge`；
- 第一阶段先做低延迟文本控制，不追求完整 ChatGPT mobile 体验。

## 目录

- `bridge/`: Windows 侧 companion service
- `android/`: Android 原生客户端
- `docs/`: 架构与协议文档
- `AI工作记录/`: 项目内工作记录

## 当前状态

第一版仓库脚手架已创建：

- `bridge` 已有 TypeScript MVP 骨架；
- `android` 已有最小 Compose 客户端骨架；
- `bridge` 同时支持 `mock` 与 `app-server` 两种 runner；
- 已生成本地 `app-server` schema 到 `docs/generated/`，用于后续协议对齐。
- Android 当前默认把输入地址理解为 `bridge base URL`，不是直接连 `codex app-server`。

## 设计原则

- 只暴露自定义 `bridge` API，不直接把原始 `codex app-server` 暴露给手机；
- 默认按内网 / Tailscale 使用，不默认公网暴露；
- 默认保留审批点，不直接放开高风险自动执行；
- 首先优化“连得快、看得快、控制快”，而不是功能堆叠。

## 下一步

1. 扩展真实 `codex app-server` runner，补齐审批与更多事件映射。
2. 给 Android 客户端接入真实 WebSocket 消息流。
3. 补 Android 对 token 认证的请求头支持，并继续推进审批事件链路。

## Bridge 启动

### Mock 模式

```powershell
cd D:\workspace\codex-mobile\bridge
npm install
npm run dev
```

### app-server 模式

```powershell
cd D:\workspace\codex-mobile\bridge
$env:CODEX_MOBILE_RUNNER = 'app-server'
npm run dev
```

默认监听 `http://127.0.0.1:8787`。

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

当前 Android MVP 还没有携带 Bearer token 的能力，因此一旦启用 `CODEX_MOBILE_AUTH_TOKEN`，Android 现有版本只能继续访问 `/health`，其余 `/api/*` 会收到 `401 unauthorized`，需要后续客户端补请求头支持。

### 手机联调

如果要让真机直接连接 Windows 主机，请不要只监听 `127.0.0.1`。直接运行：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\start-bridge-lan.ps1
```

这会用 `app-server` 模式把 bridge 监听到 `0.0.0.0:8787`。

如果 bridge 配置了 `CODEX_MOBILE_AUTH_TOKEN`，Android 端需要先进入“设置”页填写 token，再发起连接并进入会话详情页实时流。

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

## 本地 Android 模拟器测试

如果不想每次都手工把 APK 装到真机，可以直接使用仓库内的本地模拟器环境。

### 已准备好的能力

- 项目内 Android SDK 已补齐 `emulator`
- 已创建项目内 AVD：`codex-mobile-api35`
- 模拟器数据目录放在仓库本地资产：`.tmp/android-avd/`

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

### 模拟器连接 bridge

- 模拟器访问 Windows 主机本地服务时，bridge 地址使用：`http://10.0.2.2:8787`
- 如果 bridge 启用了 `CODEX_MOBILE_AUTH_TOKEN`，先在 App 的“设置”页填写 token，再连接 bridge
