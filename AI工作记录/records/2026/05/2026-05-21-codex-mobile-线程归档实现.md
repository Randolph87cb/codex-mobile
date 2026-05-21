# 2026-05-21 线程归档实现

## 目标

- 为 `codex-mobile` 增加与 `codex app-server` 语义一致的线程归档能力。
- 第一版范围限定为：
  - bridge 暴露归档/恢复归档接口；
  - Android 会话列表支持“当前 / 已归档”切换；
  - 单条归档、单条恢复；
  - 不做物理删除，不支持草稿归档。

## 关键决策

- 底层直接对齐 `app-server` 的 `thread/archive`、`thread/unarchive`、`thread/list archived`，不自建归档存储。
- `bridge` 在未归档列表查询时同时读取 archived thread id，用来避免已归档线程因本地 `SessionStore` 残留被错误混回当前列表。
- Android 端只在列表页提供归档/恢复入口，归档前弹确认框，恢复直接执行。
- 详情页不新增归档状态 UI，先保持一期范围收敛。

## 主要修改

### Bridge

- `bridge/src/types.ts`
  - `SessionView` 新增 `archived` 字段。
- `bridge/src/bridge-runner.ts`
  - `HistoryCapableBridgeRunner` 新增：
    - `listSessionViews(archived?: boolean)`
    - `archiveSession(sessionId: string)`
    - `unarchiveSession(sessionId: string)`
- `bridge/src/session-view.ts`
  - 线程/本地会话视图补充 `archived` 输出。
- `bridge/src/app-server-runner.ts`
  - 支持按 `archived` 过滤 `thread/list`；
  - 当前列表查询时排除已归档 thread id，避免本地 store 残留混入；
  - 实现 `thread/archive` / `thread/unarchive`；
  - 归档 thread 后移除对应本地 attach session。
- `bridge/src/app.ts`
  - `GET /api/sessions?archived=true|false`
  - `POST /api/session/:id/archive`
  - `POST /api/session/:id/unarchive`

### Android

- `android/.../model/SessionSummary.kt`
  - 新增 `archived` 字段。
- `android/.../data/SessionRepository.kt`
  - `listSessions(archived: Boolean = false)`
  - `archiveSession(sessionId: String)`
  - `unarchiveSession(sessionId: String)`
- `android/.../data/RealBridgeDataProvider.kt`
  - 对接归档列表和归档/恢复归档接口；
  - 解析 `archived` 字段。
- `android/.../AppViewModel.kt`
  - 新增 `showArchivedSessions` 状态；
  - 支持切换当前/已归档列表；
  - 支持归档/恢复归档动作；
  - 刷新列表时按当前筛选条件重新拉取。
- `android/.../ui/screen/SessionListScreen.kt`
  - 新增“当前 / 已归档”切换；
  - 会话卡片新增归档/恢复图标按钮；
  - 归档确认弹窗；
  - 归档列表下禁用新建草稿入口。
- `android/.../ui/CodexMobileApp.kt`
  - 将筛选和归档动作接到 `AppViewModel`。
- `android/.../ui/TestTags.kt`
  - 补充归档相关测试标签。
- `android/.../ReplayHarnessActivity.kt`
  - 回放数据 provider 同步补全新接口。
- `android/.../data/FakeCodexDataProvider.kt`
- `android/.../data/FallbackCodexDataProvider.kt`
  - 同步补全新接口。

## 测试

### Bridge

```powershell
cd bridge
npm run check
npm test
```

结果：通过。

### Android

```powershell
cd android
$env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"
$env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"
.\gradlew.bat testDebugUnitTest
```

结果：通过。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1
```

结果：通过。

## 备注

- 这次没有改详情页归档态展示，也没有做批量归档。
- 仍保留你工作区内原有未跟踪记录 `AI工作记录/records/2026/05/2026-05-19-codex-mobile-bridge启动方式说明.md`，未触碰。
