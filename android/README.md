# Android 客户端说明

当前目录是 `codex-mobile` 的 Android 原生客户端骨架。

## 当前状态

- 使用 `Kotlin + Jetpack Compose`
- 当前默认使用 `RealBridgeDataProvider`，失败时回退到 fake provider
- 页面骨架已具备：连接页、会话列表、会话详情、设置页
- 已接入第一版真实 `bridge` HTTP API
- 已生成 `gradle wrapper`

## 设计约定

- `endpointInput` 当前按 `bridge base URL` 理解，例如：

```text
http://192.168.31.66:8787
```

- 后续真实接入时，Android 端应调用：
  - `POST /api/session`
  - `GET /api/session/:id`
  - `POST /api/session/:id/input`
  - `POST /api/session/:id/interrupt`
  - `GET /api/session/:id/ws`

## 当前缺口

- 详情页还没有接 WebSocket 流式输出
- 审批还没接通
- 认证还没接通
- 默认 endpoint 是当前这台机器的局域网地址，如果网络变化，需要手动修改

## 建议下一步

1. 接 WebSocket 流，把会话详情页替换成实时输出。
2. 接通审批请求与 `/api/session/:id/approve`。
3. 把 endpoint 持久化到 `DataStore`，避免每次重填。
