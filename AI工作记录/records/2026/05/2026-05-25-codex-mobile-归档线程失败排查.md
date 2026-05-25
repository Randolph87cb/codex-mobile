# codex-mobile 归档线程失败排查

- 时间：2026-05-25
- 目标：排查 Android 端“归档线程失败”的根因，先做只读检查，不修改业务代码。
- 范围：`android/`、`bridge/`、`docs/`、`.logs/bridge/`，以及当前公网 bridge / 局域网 bridge 的只读接口响应。

## 已检查事实

- Android 日志里的失败链路是：`POST /api/session/:id/archive` 返回 `HTTP 502`，客户端把它包装成 `BridgeRequestException`。
- 当前机器上同时跑着两条 bridge：
  - `127.0.0.1:8787` -> `node ... tsx ... src/index.ts`，开发实例；
  - `0.0.0.0:8787` -> `node dist/index.js`，后台实例。
- 公网域名 `https://codex.randolph87.top` 命中的是后台实例，不是本地 `127.0.0.1` 开发实例。
- 后台实例日志 `.logs/bridge/bridge-stdout.log` 显示多个归档请求都由源站自己快速返回了 `502`，bridge 进程没有崩溃：
  - `sess_0d1f76bf-f4af-4bfa-8f7e-33b1d5db2e5d`
  - `sess_f578aa5a-029f-4f4e-9a18-b9617ca8903f`
- 直连后台实例 `http://192.168.31.66:8787/api/session/sess_f578aa5a-029f-4f4e-9a18-b9617ca8903f/archive`，拿到真实错误：

```json
{"error":"session-archive-failed","message":"no rollout found for thread id 019e5d79-ae14-7a70-ad24-ac4f63276029"}
```

- 同一会话详情返回：
  - `id = sess_f578aa5a-029f-4f4e-9a18-b9617ca8903f`
  - `threadId = 019e5d79-ae14-7a70-ad24-ac4f63276029`
  - 标题仍为“新会话”
  - `transcriptPreview` 只有工作目录、线程 ID、当前轮次、最近错误，没有用户消息或历史 turn 内容。
- `bridge/src/app-server-runner.ts` 当前实现里，`archiveSession()` 只要本地 `SessionStore` 里有 `threadId`，就直接调用 `thread/archive`，没有先判断该 thread 是否已经 materialize 成可归档 rollout。
- `bridge/src/app-server-runner.ts` 的 `listSessionViews(false)` 会把 `thread/list archived=false` 里没有出现、但仍保存在 `SessionStore` 里的本地会话补回当前列表。
- `docs/thread-archive-collaboration.md` 已明确“一期边界”不支持“草稿线程归档”。

## 判断

- 这次失败不是 Android 参数问题，也不是 Cloudflare 自身故障；根因在 bridge 调用了上游不接受的 `thread/archive`。
- 更准确地说，当前列表里混入了“已创建 threadId、但还没有持久化 rollout 的空会话 / 未 materialize 会话”，而 Android 仍给它展示了“归档”入口。
- 对这类会话，`codex app-server` 的 `thread/archive` 会返回 `no rollout found for thread id ...`，bridge 目前把它原样包装成 `502 session-archive-failed`。

## 建议修复方向

1. bridge 侧先做可归档判断：
   - 只允许归档已出现在 `thread/list` / `thread/read` 的可持久化 thread；
   - 对未 materialize 的本地会话返回更明确的 `409`，例如 `session-not-archivable`。
2. Android 侧收紧入口：
   - 对这类“仅本地存在、尚无实际历史内容”的会话隐藏“归档”按钮，或给出“先发送首条消息后才能归档”的提示。
3. 测试补齐：
   - bridge runner / app route 增加“无 rollout thread 归档失败”的回归测试；
   - Android 如果做入口限制，需要补列表按钮可见性或交互测试。

## 本次未执行

- 未修改 bridge 或 Android 代码。
- 未执行构建、单测或集成测试，因为本次只做只读排查。
