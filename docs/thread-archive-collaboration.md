# 线程归档协作说明

本文档面向当前仓库里的协作开发者和 agent，说明 `codex-mobile` 的线程归档能力应该怎么理解、改哪里、哪些边界不能混淆。

## 目标

- 提供与 `codex app-server` 一致的线程归档语义；
- 让 Android 客户端可以整理会话，而不是直接删除；
- 保持 bridge 只做协议整形和列表适配，不自建一套独立归档系统。

## 语义基线

当前实现直接对齐 `app-server`：

- `thread/archive`
- `thread/unarchive`
- `thread/list archived=true|false`

这意味着：

- 归档对象是 `thread`，不是 Android 本地卡片；
- 归档后的线程不应再出现在默认会话列表；
- 恢复归档后线程重新回到当前会话列表；
- 历史 transcript、thread id、cwd 等底层信息保持原样。

当前额外约定：

- 对已 materialize 的正式线程，bridge API 暴露的 `id` 就是上游 `threadId`；
- bridge 不再为正式线程额外包装另一层稳定 ID；
- 草稿不属于 thread 体系，因此没有可归档的正式线程 ID。

## 当前 bridge 行为

相关入口：

- `GET /api/sessions?archived=true|false`
- `POST /api/session/:id/archive`
- `POST /api/session/:id/unarchive`

当前实现约束：

- `archived=false` 时，bridge 仍可能把本地 `SessionStore` 中尚未出现在 `thread/list` 的正式线程补进列表；
- 但会额外排除已经出现在 archived thread 集合里的 `id/threadId`，避免已归档线程被本地残留 attach 记录错误混回当前列表；
- `archived=true` 时，只返回已归档线程视图，不再混入本地草稿或未 materialize 会话。

## 当前 Android 行为

当前会话列表提供两态：

- `当前`
- `已归档`

当前交互：

- 在“当前”列表里，每个会话卡片支持单条归档；
- 归档前弹确认框；
- 在“已归档”列表里，每个会话卡片支持恢复归档；
- 归档列表下不提供“新建草稿线程”入口。

## 一期边界

当前明确不做：

- 物理删除 thread
- 草稿线程归档
- 批量归档
- 详情页内的归档状态管理面板

如果后续要扩展，优先在本文件补清边界再实现。

## 协作时重点文件

### Bridge

- `bridge/src/app.ts`
- `bridge/src/app-server-runner.ts`
- `bridge/src/bridge-runner.ts`
- `bridge/src/session-view.ts`
- `bridge/src/types.ts`

### Android

- `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
- `android/app/src/main/java/com/openai/codexmobile/data/SessionRepository.kt`
- `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`

## 易错点

### 不要把归档当删除

如果需求写的是“隐藏旧线程”，默认应理解为归档，而不是删除。

### 不要把本地草稿塞进归档流

草稿在线程真正创建前不属于正式 thread，当前实现不支持归档。

### 不要让 archived thread 回流到当前列表

只要改了 bridge 列表合并逻辑，都要重新检查：

- 当前列表是否还混入 archived thread
- 恢复归档后是否出现重复卡片
- attach 过的历史 thread 是否因为本地 store 残留再次漏出

## 验证要求

只要改了 bridge：

```powershell
cd bridge
npm run check
npm test
```

只要改了 Android：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1
cd android
$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"
$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"
.\gradlew.bat testDebugUnitTest
```

## 协作建议

如果只是补文档、按钮文案或图标，不要顺手改 bridge 语义。

如果改的是归档协议、列表合并或恢复逻辑，至少同步检查：

- `README.md`
- `docs/api.md`
- 本文档
- 当前线程工作记录
