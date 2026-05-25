# 归档线程误恢复历史线程

- 日期：2026-05-25
- 当前阶段：已完成 bridge 侧修复、文档同步和 bridge 校验。
- 用户现象：归档一个线程后，该线程会正常归档，但之前查看过的某个历史线程会重新出现在当前列表，看起来像“新对话/当前对话”；实际点进详情后又是历史对话内容。

## 已检查范围

- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
- `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
- `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`
- `bridge/src/app.ts`
- `bridge/src/app-server-runner.ts`
- `bridge/src/bridge-runner.ts`
- `bridge/src/mock-runner.ts`
- `bridge/src/session-store.ts`
- `bridge/src/session-view.ts`
- `bridge/tests/app-server-runner.test.ts`
- `bridge/tests/app.test.ts`
- `docs/api.md`
- `docs/thread-archive-collaboration.md`

## 根因判断

- Android 归档动作本身会在完成后刷新当前列表：`archiveSession -> loadManagedSessionSummaries(showArchivedSessions)`。
- 更可疑的是 bridge 的历史线程 attach/resume 逻辑：打开某些历史线程详情后，WebSocket 链路会走 `resolveSessionRecord -> historyRunner.attachSession(sessionId)`。
- `attachSession` 在找不到本地 session 时，会尝试 `thread/resume`，并把恢复出的 thread 绑定进本地 `SessionStore`。
- 旧实现同时保留 bridge 自己的 `session.id` 和上游 `threadId`，导致历史线程恢复后可能出现“列表项主键还是旧 session.id，但内部 threadId 指向真实线程”的混合状态。
- 后续 `listSessionViews(false)` 会把这些本地残留 session 当成“当前列表候选”返回，因此会把原本历史线程重新暴露到当前列表。

## 对 threadId 稳定性的确认

- 仓库内 `docs/upstream/codex-app-server/README.md` 的 `thread/resume` 示例明确显示：请求 `threadId=thr_123`，响应里的 `thread.id` 仍是 `thr_123`。
- 同一份 README 明确说明：会创建新 `threadId` 的是 `thread/fork`，以及 detached review 这类 fork 语义，不是 `thread/resume`。
- `thread/archive` / `thread/unarchive` 也是直接以同一个 `threadId` 操作，并说明归档对象对应磁盘上的持久化 rollout JSONL。
- 结论：对已经 materialize 的真实 Codex 线程，`threadId` 可以视为稳定主键；正式线程不需要再额外映射成另一套 bridge 稳定 ID。

## 已实施修复

- `bridge/src/bridge-runner.ts`
  - 把 runner 创建入口从 `initializeSession(sessionId)` 改成 `createSession(input)`，要求直接返回正式线程记录。
- `bridge/src/app.ts`
  - `POST /api/session` 不再先创建本地占位 session，再初始化上游线程；改为直接创建上游线程并返回正式记录。
- `bridge/src/app-server-runner.ts`
  - 删除 `threadToSession` 映射。
  - 正式线程创建后，直接以真实 `threadId` 作为 `id` 落入 `SessionStore`。
  - 历史线程 `attach/resume` 后，也直接以恢复出的 `threadId` 作为记录主键，不再保留旧的包装 `session.id`。
  - 当前列表补本地记录时，按 `id/threadId` 与 archived 集合对齐，避免已归档线程回流到当前列表。
  - `getSessionView`、`attachSession`、`archiveSession`、`resolveArchivableThreadId` 等路径统一收敛到“正式线程只认 `threadId`”。
- `bridge/src/session-store.ts`
  - store 内的正式线程记录默认保持 `id == threadId`。
- `bridge/src/mock-runner.ts`
  - mock 创建会话时也返回 `id == threadId` 的正式线程记录。
- `bridge/tests/app-server-runner.test.ts`
  - 用例统一改成线程主键语义，覆盖创建、详情、归档等路径。
- `bridge/tests/app.test.ts`
  - app 层测试改为校验 `createSession` 路径。
- `docs/api.md`
  - 明确正式线程的 `id` 与 `threadId` 相同，`/api/session/:id` 的 `:id` 直接使用上游 `threadId`。
- `docs/thread-archive-collaboration.md`
  - 明确正式线程不再额外包装另一层稳定 ID，草稿不属于 thread 体系。

## 验证结果

已执行：

```powershell
cd bridge
npm run check
npm test
```

结果：

- `npm run check` 通过。
- `npm test` 通过，当前 bridge 测试共 67 项通过。
- 测试过程中有一条既有 stderr：`spawn codex.exe ENOENT`，对应失败场景断言，用例本身通过，不是本次改动引入的新错误。

## 结论

- 正式线程现在按单一 `threadId` 语义收敛，历史线程 attach/resume 不再把旧包装 ID 混进当前列表。
- 本次只修改了 bridge 和相关文档、测试，没有调整 Android 业务逻辑。

## 后续关注

- `AppViewModel.synchronizeManagedSessionPolicies()` 在 `archived=true` 时如果发生策略修正，仍可能误拉当前列表；这是独立问题，未包含在本次修复内，后续可单独处理。
