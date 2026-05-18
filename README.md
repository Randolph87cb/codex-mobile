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
3. 增加 token 认证、目录白名单和审批事件链路。

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

### 手机联调

如果要让真机直接连接 Windows 主机，请不要只监听 `127.0.0.1`。直接运行：

```powershell
powershell -ExecutionPolicy Bypass -File D:\workspace\codex-mobile\scripts\start-bridge-lan.ps1
```

这会用 `app-server` 模式把 bridge 监听到 `0.0.0.0:8787`。

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
