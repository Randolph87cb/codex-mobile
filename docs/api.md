# Bridge API 草案

## HTTP

### `POST /api/session`

创建一个新会话。

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

请求体：

```json
{
  "requestId": "req_123",
  "decision": "approve"
}
```

- `requestId` 可选；当同一会话同时存在多个待审批请求时必须传。
- `decision` 可选，默认 `approve`。
- 当前支持的 `decision`：
  - `approve`
  - `approve_for_session`
  - `reject`
  - `reject_and_interrupt`

响应：

```json
{
  "ok": true,
  "requestId": "req_123",
  "decision": "approve",
  "method": "item/commandExecution/requestApproval",
  "status": "running"
}
```

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

- `tool.request` 现在用于透传真实可审批的 `app-server` server request，事件数据至少包含：
  - `requestId`
  - `method`
  - `params`
- `tool.result` 会在 bridge 向 `app-server` 回写审批结果后发出，事件数据至少包含：
  - `requestId`
  - `method`
  - `decision`
  - `result`
- 当前仅接通可映射到 bridge 审批动作的 server request：
  - `item/commandExecution/requestApproval`
  - `item/fileChange/requestApproval`
  - `item/permissions/requestApproval`
  - `applyPatchApproval`
  - `execCommandApproval`
- Android 当前已经实际调用 `GET /health`、`GET /api/sessions`、`POST /api/session`、`GET /api/session/:id`、`POST /api/session/:id/input`。
