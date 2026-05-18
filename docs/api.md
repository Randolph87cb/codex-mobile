# Bridge API 草案

## HTTP

### `GET /health`

公开诊断接口，不要求 token。

响应示例：

```json
{
  "ok": true,
  "service": "codex-mobile-bridge",
  "runnerMode": "mock",
  "security": {
    "tokenAuthEnabled": false,
    "cwdWhitelistEnabled": false
  }
}
```

### 认证

除 `/health` 外，所有 `/api/*` 接口在配置 `CODEX_MOBILE_AUTH_TOKEN` 后都要求：

```http
Authorization: Bearer <token>
```

未带 token 或 token 不匹配时返回：

```json
{
  "error": "unauthorized",
  "message": "missing bearer token"
}
```

### `POST /api/session`

创建一个新会话。

当配置 `CODEX_MOBILE_ALLOWED_CWDS` 后，`cwd` 必须是绝对路径，并且位于白名单目录内。

请求体：

```json
{
  "cwd": "D:\\workspace\\codex-mobile",
  "model": "gpt-5.5",
  "approvalMode": "manual"
}
```

响应：

```json
{
  "id": "sess_xxx",
  "status": "idle",
  "cwd": "D:\\workspace\\codex-mobile",
  "model": "gpt-5.5",
  "approvalMode": "manual",
  "createdAt": "2026-05-18T15:00:00.000Z",
  "updatedAt": "2026-05-18T15:00:00.000Z"
}
```

白名单错误示例：

```json
{
  "error": "cwd-not-allowed",
  "message": "cwd is outside the allowed directories"
}
```

### `GET /api/session/:id`

查询会话状态。

### `POST /api/session/:id/input`

向会话发送一条用户输入。

请求体：

```json
{
  "text": "检查当前项目结构并总结下一步"
}
```

### `POST /api/session/:id/interrupt`

中断当前任务。

### `POST /api/session/:id/approve`

审批一次待确认操作。

## WebSocket

### `GET /api/session/:id/ws`

连接后接收事件流。

事件结构：

```json
{
  "type": "assistant.delta",
  "sessionId": "sess_xxx",
  "timestamp": "2026-05-18T15:00:10.000Z",
  "data": {
    "text": "正在检查项目结构..."
  }
}
```

## 当前事件类型

- `session.started`
- `assistant.delta`
- `assistant.done`
- `tool.request`
- `tool.result`
- `run.status`
- `run.interrupted`
- `error`

## 说明

- `tool.request` 目前只是 bridge 侧占位事件，用于暴露真实 `app-server` 的 server request。
- `/api/session/:id/approve` 当前尚未接通真实响应逻辑。
- Android 当前已经实际调用 `GET /health`、`GET /api/sessions`、`POST /api/session`、`GET /api/session/:id`、`POST /api/session/:id/input`。
- 默认不启用 token 认证；只有在设置 `CODEX_MOBILE_AUTH_TOKEN` 后才对 `/api/*` 生效。
- 默认不启用 `cwd` 白名单；只有在设置 `CODEX_MOBILE_ALLOWED_CWDS` 后才限制创建会话目录。
