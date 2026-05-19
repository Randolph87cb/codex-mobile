# Bridge API

本文档描述当前 `codex-mobile bridge` 已实现、且 Android 客户端实际依赖的接口行为。

## 总览

- 公开健康检查：`GET /health`
- 会话列表与详情：`GET /api/sessions`、`GET /api/session/:id`
- 会话创建与配置：`POST /api/session`、`PATCH /api/session/:id/config`
- 会话输入与审批：`POST /api/session/:id/input`、`POST /api/session/:id/approve`、`POST /api/session/:id/interrupt`
- 图片上传与访问：
  - `POST /api/attachment/image`
  - `GET /api/attachment/image/:id/content`
  - `GET /api/image/file?path=...`
- 实时流：`GET /api/session/:id/ws`

## 认证

当配置 `CODEX_MOBILE_AUTH_TOKEN` 后，除 `GET /health` 外的所有 `/api/*` 都要求：

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

Android 客户端当前已经支持在设置页填写并携带 Bearer token。

## 健康检查

### `GET /health`

公开诊断接口，不要求 token。

响应示例：

```json
{
  "ok": true,
  "service": "codex-mobile-bridge",
  "runnerMode": "app-server",
  "security": {
    "tokenAuthEnabled": false,
    "cwdWhitelistEnabled": false
  }
}
```

## 会话接口

### `GET /api/sessions`

返回会话摘要列表，既可能来自当前 bridge 维护的本地会话，也可能来自历史线程视图。

响应示例：

```json
{
  "items": [
    {
      "id": "sess_xxx",
      "title": "现在有个 bug",
      "subtitle": "gpt-5.5 • 空闲 • D:\\workspace\\codex-mobile",
      "lastUpdated": "2026-05-19T07:24:08.000Z",
      "transcriptPreview": "你：现在有个 bug...",
      "source": "local",
      "cwd": "D:\\workspace\\codex-mobile",
      "model": "gpt-5.5",
      "approvalMode": "manual",
      "reasoningEffort": "medium",
      "serviceTier": "default",
      "sandboxMode": "workspace-write",
      "status": "idle",
      "threadId": "019e...",
      "activeTurnId": null,
      "lastError": null,
      "createdAt": "2026-05-19T07:23:00.000Z",
      "updatedAt": "2026-05-19T07:24:08.000Z"
    }
  ]
}
```

### `POST /api/session`

创建本地会话并初始化 `codex app-server` 线程。

请求体：

```json
{
  "cwd": "D:\\workspace\\codex-mobile",
  "model": "gpt-5.5",
  "approvalMode": "manual",
  "reasoningEffort": "medium",
  "serviceTier": "default",
  "sandboxMode": "workspace-write"
}
```

字段说明：

- `cwd`：必填
- `model`：必填
- `approvalMode`：`manual | auto`
- `reasoningEffort`：`minimal | low | medium | high | xhigh`
- `serviceTier`：`default | fast`
- `sandboxMode`：`read-only | workspace-write | danger-full-access`

响应示例：

```json
{
  "id": "sess_xxx",
  "cwd": "D:\\workspace\\codex-mobile",
  "model": "gpt-5.5",
  "approvalMode": "manual",
  "reasoningEffort": "medium",
  "serviceTier": "default",
  "sandboxMode": "workspace-write",
  "status": "idle",
  "threadId": "019e...",
  "activeTurnId": null,
  "lastError": null,
  "createdAt": "2026-05-19T15:00:00.000Z",
  "updatedAt": "2026-05-19T15:00:00.000Z"
}
```

### `PATCH /api/session/:id/config`

更新会话配置。所有字段均可选，但至少需要提交一个有效字段。

### `GET /api/session/:id`

返回会话详情视图，包含：

- 标题与副标题
- 最新更新时间
- 可直接渲染的 `transcriptPreview`
- 当前配置与状态

Android 当前依赖这条接口读取完整历史 transcript、图片 Markdown 和详情页状态。

### `POST /api/session/:id/input`

向会话发送输入。支持纯文本、纯附件、或两者同时存在。

请求体示例：

```json
{
  "text": "帮我看这几张图",
  "attachments": [
    {
      "path": "C:\\Users\\...\\Temp\\codex-mobile-bridge\\attachments\\att_xxx.png"
    }
  ]
}
```

附件说明：

- 当前 Android 客户端发送的是 `attachments[].path`
- bridge 仍兼容旧格式 `attachments[].id`
- `path` 只能引用 bridge 已暂存过的附件路径，不能伪造任意主机文件

成功时返回 `202 Accepted`。

### `POST /api/session/:id/interrupt`

中断当前运行。成功时返回 `200`。

### `POST /api/session/:id/approve`

审批一次待确认操作。

请求体：

```json
{
  "requestId": "req_123",
  "decision": "approve"
}
```

支持的 `decision`：

- `approve`
- `approve_for_session`
- `reject`
- `reject_and_interrupt`

响应示例：

```json
{
  "requestId": "req_123",
  "decision": "approve",
  "method": "item/commandExecution/requestApproval",
  "status": "running"
}
```

## 图片接口

### `POST /api/attachment/image`

向 bridge 预上传一张图片，供后续会话输入引用。

当前支持两种上传格式：

1. 推荐：`multipart/form-data`
   - `displayName`
   - `mimeType`
   - `file`
2. 兼容旧链路：`application/json`
   - `displayName`
   - `mimeType`
   - `contentBase64`

Android 当前实际使用的是 `multipart/form-data`，这是为了避免 `JSON + Base64` 在 Cloudflare 等代理链路上把大图请求体放大。

bridge 当前默认 `bodyLimit` 为 `32MB`，可用环境变量 `BRIDGE_BODY_LIMIT_MB` 覆盖。

成功响应：

```json
{
  "id": "att_xxx",
  "path": "C:\\Users\\...\\Temp\\codex-mobile-bridge\\attachments\\att_xxx.png",
  "kind": "image",
  "displayName": "sample.png",
  "mimeType": "image/png",
  "createdAt": "2026-05-19T15:00:00.000Z"
}
```

### `GET /api/attachment/image/:id/content`

按附件 ID 取回 bridge 已上传图片的原始内容。

### `GET /api/image/file?path=...`

读取 bridge 允许暴露的本地图片文件。

当前主要用于：

- 历史 transcript 中的本地图片
- 生成图片结果
- Android 详情页里的图片缩略图和大图预览

如果 `path` 不在允许范围内，返回：

```json
{
  "error": "image-path-not-allowed"
}
```

## 实时流

### `GET /api/session/:id/ws`

建立 WebSocket 实时流。

事件基础结构：

```json
{
  "type": "assistant.delta",
  "sessionId": "sess_xxx",
  "timestamp": "2026-05-19T15:00:10.000Z",
  "data": {}
}
```

当前已实现的事件类型：

- `session.started`
- `assistant.delta`
- `assistant.done`
- `activity`
- `tool.request`
- `tool.result`
- `run.status`
- `run.interrupted`
- `error`

### `activity`

`activity` 是当前 Android 详情页展示“执行过程”所依赖的关键事件。`data` 中通常包含：

- `itemType`
- `itemId`
- `title`
- `body`
- `summary`
- `transcriptBlock`

当前 bridge 会把下列过程类输出转成 `activity`：

- 命令执行进度
- 文件修改进度
- MCP / 工具调用进度
- 推理摘要

补充说明：

- `title`、`body`、`summary` 是当前推荐给 UI 直接消费的结构化字段。
- `transcriptBlock` 仍然保留，主要用于兼容旧的文本拼接链路。
- `reasoning` 不再按每个 `summaryTextDelta` 直接变成一张新卡片；bridge 会按 `itemId` 聚合同一条推理活动，并持续更新其 `body/summary`。
- Android 当前会优先按 `itemId` 合并这些 `activity`，再把它们并入“执行过程”展示，而不是单纯依赖 transcript 文本反推结构。

## 当前 Android 客户端实际依赖

Android 当前已经实际使用这些接口与行为：

- `GET /health`
- `GET /api/sessions`
- `POST /api/session`
- `PATCH /api/session/:id/config`
- `GET /api/session/:id`
- `POST /api/session/:id/input`
- `POST /api/attachment/image`（multipart）
- `GET /api/image/file`
- `GET /api/session/:id/ws`

这几条链路改动时，需要同步检查 Android 数据层与详情页渲染逻辑。
