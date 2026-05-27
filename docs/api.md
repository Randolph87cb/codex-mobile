# Bridge API

本文档描述当前 `codex-mobile bridge` 已实现、且 Android 客户端实际依赖的接口行为。

## 总览

- 公开健康检查：`GET /health`
- 全局额度：`GET /api/account/quota`
- 会话列表与详情：`GET /api/sessions`、`GET /api/session/:id`
- 会话创建、改名、配置、目标与归档：`POST /api/session`、`PATCH /api/session/:id/title`、`PATCH /api/session/:id/config`、`GET /api/session/:id/goal`、`PUT /api/session/:id/goal`、`DELETE /api/session/:id/goal`、`POST /api/session/:id/archive`、`POST /api/session/:id/unarchive`
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

## 全局额度接口

### `GET /api/account/quota`

返回当前账号的全局额度快照。bridge 内部会调用上游 `account/rateLimits/read`，并把结果规范化成 Android 稳定依赖的结构。

当前约定：

- 优先读取主 `codex` 限额桶。
- 不把 upstream 的 `primary / secondary` 顺序直接暴露给客户端。
- 使用 `windowDurationMins` 识别窗口：
  - `300` => `fiveHours`
  - `10080` => `oneWeek`
- 这条接口属于只读状态接口，即使 bridge 处于 `drain / restarting` 窗口，也不会像写接口那样返回 `503`。

响应示例：

```json
{
  "limitId": "codex",
  "planType": "prolite",
  "rateLimitReachedType": null,
  "fiveHours": {
    "usedPercent": 10,
    "windowDurationMins": 300,
    "resetsAt": "2026-05-25T11:51:54.000Z"
  },
  "oneWeek": {
    "usedPercent": 16,
    "windowDurationMins": 10080,
    "resetsAt": "2026-05-31T00:41:21.000Z"
  },
  "credits": {
    "hasCredits": false,
    "unlimited": false,
    "balance": "0"
  }
}
```

Android 当前消费方式：

- UI 不直接显示 `usedPercent`，而是转换为“剩余额度”。
- 线程列表页和会话详情页都只显示 `5 小时 / 1 周` 两个窗口。
- 顶栏先用两个颜色点表达大致使用量，点击后展开剩余百分比和重置时间。
- 如果刷新失败，客户端会保留上次成功快照，并额外展示错误提示。

可能返回：

```json
{
  "error": "quota-not-supported"
}
```

或

```json
{
  "error": "account-quota-failed",
  "message": "..."
}
```

## 会话接口

### `GET /api/sessions`

返回会话摘要列表，既可能来自当前 bridge 已 attach 的正式线程，也可能来自历史线程视图。

当前约定：

- 对已 materialize 的正式线程，响应里的 `id` 与 `threadId` 相同；
- 其他以 `:id` 为参数的正式线程接口，也直接使用这个 `threadId`；
- 草稿不属于正式线程，不会出现在这里。

查询参数：

- `archived=true|false`
- 默认不传时等同于 `false`
- `false` 返回当前活跃列表
- `true` 返回已归档线程列表

响应示例：

```json
{
  "items": [
    {
      "id": "thr_123",
      "title": "现在有个 bug",
      "subtitle": "gpt-5.5 • 空闲 • D:\\workspace\\codex-mobile",
      "lastUpdated": "2026-05-19T07:24:08.000Z",
      "transcriptPreview": "你：现在有个 bug...",
      "archived": false,
      "source": "local",
      "cwd": "D:\\workspace\\codex-mobile",
      "model": "gpt-5.5",
      "approvalMode": "manual",
      "reasoningEffort": "medium",
      "serviceTier": "default",
      "sandboxMode": "workspace-write",
      "status": "idle",
      "threadId": "thr_123",
      "activeTurnId": null,
      "lastError": null,
      "createdAt": "2026-05-19T07:23:00.000Z",
      "updatedAt": "2026-05-19T07:24:08.000Z"
    }
  ]
}
```

### `POST /api/session`

创建正式线程并初始化 `codex app-server` 会话。

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
  "id": "thr_123",
  "cwd": "D:\\workspace\\codex-mobile",
  "model": "gpt-5.5",
  "approvalMode": "manual",
  "reasoningEffort": "medium",
  "serviceTier": "default",
  "sandboxMode": "workspace-write",
  "status": "idle",
  "threadId": "thr_123",
  "activeTurnId": null,
  "lastError": null,
  "createdAt": "2026-05-19T15:00:00.000Z",
  "updatedAt": "2026-05-19T15:00:00.000Z"
}
```

说明：

- 成功创建后，bridge 直接以上游 `threadId` 作为正式线程主键返回；
- 不再单独生成一层本地正式会话 ID；
- 草稿阶段如果尚未真正创建线程，也不会拿到这里的 `id`。

初始化底层线程失败时返回：

```json
{
  "error": "session-initialize-failed",
  "message": "spawn codex.exe ENOENT"
}
```

这类错误通常表示 bridge 所在主机未能正确拉起本地 `codex app-server`，而不是 Android 到 bridge 的网络本身有问题。

### `PATCH /api/session/:id/config`

更新会话配置。所有字段均可选，但至少需要提交一个有效字段。

### `PATCH /api/session/:id/title`

修改线程展示名称。bridge 会把这条请求映射到上游 `thread/name/set`，并返回刷新后的会话详情视图。

请求体：

```json
{
  "title": "添加线程改名功能"
}
```

当前约定：

- `title` 必填，bridge 会先做 `trim()`；
- 只有真实线程支持改名，草稿线程不支持；
- 成功后返回的就是最新详情视图，Android 可直接用来刷新详情页和线程列表。

### `POST /api/session/:id/archive`

归档一个已 materialize 的线程。

当前行为：

- 草稿线程不支持归档；
- 历史 thread 与当前 bridge 已 attach 的 thread 都支持；
- 成功后线程会从默认会话列表移到 `GET /api/sessions?archived=true`；
- 当前 bridge 不暴露物理删除接口。

成功响应：

```json
{
  "ok": true
}
```

### `POST /api/session/:id/unarchive`

把一个已归档线程恢复到当前会话列表。

成功响应：

```json
{
  "ok": true
}
```

### `GET /api/session/:id`

返回会话详情视图，包含：

- 标题与副标题
- 最新更新时间
- 可直接渲染的 `transcriptPreview`
- 当前配置与状态

Android 当前依赖这条接口读取完整历史 transcript、图片 Markdown 和详情页状态。

Android 当前会把详情页顶部状态拆成两层来消费：

- 第一行：`bridge 状态 / 同步方式 / 会话状态`
- 第二行：`排队消息 / 目标状态`
- 顶栏右侧：全局额度双点指示器，点击展开详情
- 消息流里的非对话过程不会作为独立系统卡片展示，而是归属到 Codex 回复布局中
- 执行过程只按连续片段合并；如果中间出现 `Codex：...` 文字回复，后续过程会开启新的执行过程分组

如果底层 runner 支持目标能力，详情响应还会带上：

- `goal`：当前线程目标快照；为空表示当前没有目标
- `goalCapability`：`supported | unsupported | unknown`

`goal` 响应示例：

```json
{
  "goal": {
    "objective": "把详情页实时流稳定下来",
    "status": "active",
    "tokenBudget": 200000,
    "tokensUsed": 3400,
    "timeUsedSeconds": 180,
    "createdAt": "2026-05-22T09:00:00.000Z",
    "updatedAt": "2026-05-22T09:03:00.000Z"
  },
  "goalCapability": "supported"
}
```

### `GET /api/session/:id/goal`

读取当前线程的目标状态。

成功响应示例：

```json
{
  "capability": "supported",
  "goal": {
    "objective": "把详情页实时流稳定下来",
    "status": "active",
    "tokenBudget": 200000,
    "tokensUsed": 3400,
    "timeUsedSeconds": 180,
    "createdAt": "2026-05-22T09:00:00.000Z",
    "updatedAt": "2026-05-22T09:03:00.000Z"
  }
}
```

如果当前 host 不支持目标能力，会返回：

```json
{
  "error": "goal-not-supported"
}
```

当前 bridge 会把以下情况统一视为不支持目标能力：

- 底层 runner 没有 goal 方法
- host 的 Codex 数据库还没有 `thread_goals` 相关表

也就是说，旧 host 不会再因为 goal 直接把详情接口打成 `500`。

### `PUT /api/session/:id/goal`

创建或更新当前线程目标。

请求体示例：

```json
{
  "objective": "把详情页实时流稳定下来",
  "tokenBudget": 200000
}
```

也支持只改状态：

```json
{
  "status": "paused"
}
```

如果当前 host 不支持目标能力，返回：

```json
{
  "error": "goal-not-supported"
}
```

### `DELETE /api/session/:id/goal`

清除当前线程目标。

成功响应示例：

```json
{
  "ok": true,
  "capability": "supported",
  "cleared": true
}
```

如果当前 host 不支持目标能力，返回：

```json
{
  "error": "goal-not-supported"
}
```

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
- 如果该附件此前已经被 bridge 正式保存过，bridge 会优先把 `savedPath` 提交给 runner，即使客户端仍传的是旧的暂存路径

成功时返回 `202 Accepted`。

### `POST /api/session/:id/interrupt`

中断当前运行。成功时返回 `200`。

请求体：

```json
{}
```

请求约定：

- 当前没有业务字段，但客户端仍应显式发送空 JSON body `{}`
- `Content-Type` 应为 `application/json; charset=utf-8`
- 不要把这条接口当成“完全无 body 的空 POST”

兼容性说明：

- 2026-05-26 的 Android 真机联调中，空 `POST` 在进入 bridge 路由前就被 Fastify `content-type-parser` 拒绝，返回 `415 Unsupported Media Type`
- 因此移动端、脚本端和后续新增客户端都统一按“显式空 JSON”调用，避免不同 HTTP 实现对空 body `POST` 的默认行为不一致

Android 当前把它作为独立的“终止当前轮”动作使用，和发送按钮分离：

- 运行中继续点击发送，纯文本会进入客户端排队
- 点击终止，调用这条接口请求中断当前轮
- 后续以 `run.interrupted` 或新的 `run.status=idle` 收尾，再继续发送队列中的下一条文本

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

如果请求里带上 `sessionId`，bridge 会在暂存成功后，继续把图片保存到该会话 `cwd` 下的 `mobile_uploads/` 目录，并在响应里返回正式保存路径。

这里的字段名目前仍叫 `sessionId`，是为了兼容已有 Android/bridge 请求结构；对正式线程来说，传入的值就是该线程的 `threadId`，不是另一套独立主键。

当前支持两种上传格式：

1. 推荐：`multipart/form-data`
   - `displayName`
   - `mimeType`
   - `file`
   - `sessionId`（可选）
2. 兼容旧链路：`application/json`
   - `displayName`
   - `mimeType`
   - `contentBase64`
   - `sessionId`（可选）

Android 当前实际使用的是 `multipart/form-data`，这是为了避免 `JSON + Base64` 在 Cloudflare 等代理链路上把大图请求体放大。

bridge 当前默认 `bodyLimit` 为 `32MB`，可用环境变量 `BRIDGE_BODY_LIMIT_MB` 覆盖。

保存规则：

- 不带 `sessionId`：只暂存到 bridge 附件目录，兼容旧客户端。
- 带 `sessionId`：在暂存后继续保存到 `<cwd>/mobile_uploads/`。
- 正式保存目录由 bridge 根据会话 `cwd` 固定推导，客户端不能直接指定主机路径。
- 如果有同名文件，bridge 会自动去重，不覆盖现有文件。
- 如果 `sessionId` 对应的会话不存在，返回 `404`：

```json
{
  "error": "session-not-found"
}
```

链路补充：

- Android 在已选中的正式会话里上传图片时，会直接附带 `sessionId`，其值实际就是当前线程的 `threadId`，因此通常会一次上传同时完成正式保存。
- Android 在草稿会话里先选图、再发送首条消息时，首次预上传还拿不到 `sessionId`；等真实线程创建完成、拿到 `threadId` 后，会再补一次带 `sessionId` 的上传，以拿到正式保存路径。
- 旧 bridge 只返回 `path` 时，Android 会继续回退到原有暂存路径链路，不影响兼容。

成功响应：

```json
{
  "id": "att_xxx",
  "path": "C:\\Users\\...\\Temp\\codex-mobile-bridge\\attachments\\att_xxx.png",
  "savedPath": "D:\\workspace\\project\\mobile_uploads\\sample.png",
  "savedRelativePath": "mobile_uploads/sample.png",
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

对正式线程来说，这里的 `:id` 也是 `threadId`。

事件基础结构：

```json
{
  "type": "assistant.delta",
  "sessionId": "thr_123",
  "timestamp": "2026-05-19T15:00:10.000Z",
  "data": {}
}
```

当前已实现的事件类型：

- `session.started`
- `goal.updated`
- `goal.cleared`
- `bridge.lifecycle`
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
- Android 当前展示执行过程时，还会和历史 transcript 中解析出的非对话过程一起做“连续片段”分组。
- 这层分组不会跨过 Codex 文字回复：文字回复是分隔符，前后两段执行过程会分别展示。

## 当前 Android 客户端实际依赖

Android 当前已经实际使用这些接口与行为：

- `GET /health`
- `GET /api/account/quota`
- `GET /api/sessions`
- `GET /api/sessions?archived=true`
- `POST /api/session`
- `PATCH /api/session/:id/config`
- `GET /api/session/:id`
- `GET /api/session/:id/goal`
- `PUT /api/session/:id/goal`
- `DELETE /api/session/:id/goal`
- `POST /api/session/:id/input`
- `POST /api/session/:id/interrupt`
- `POST /api/session/:id/archive`
- `POST /api/session/:id/unarchive`
- `POST /api/attachment/image`（multipart）
- `GET /api/image/file`
- `GET /api/session/:id/ws`

这几条链路改动时，需要同步检查 Android 数据层与详情页渲染逻辑。
