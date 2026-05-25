# 2026-05-25 codex-mobile 额度显示接入方案讨论

## 目标

- 讨论在 Android 端加入“额度显示”时，在线程列表和对话详情中应如何落位。
- 区分“线程目标预算”与“账号全局额度”两种语义，避免把不同层级的数据混在一起。

## 现状检查

- 线程详情数据 `SessionDetail` 已包含 `goal.tokenBudget` 与 `goal.tokensUsed`，并且详情页目标区域已经展示预算与 token 使用量。
- 线程详情顶部 `StatusStrip` 当前固定展示两层：
  - `bridge 状态 / 同步方式 / 会话状态`
  - `排队消息 / 目标状态`
- 线程列表卡片 `SessionSummary` 当前只有基础摘要字段，没有额度或预算字段。
- Android 列表接口 `RealBridgeDataProvider.listSessions()` 只把 `/api/sessions` 返回解析为 `SessionSummary`，不会额外拉目标或额度。
- bridge `/api/session/:id` 详情链路会通过 `decorateSessionViewWithGoal()` 补 `goal`，但 `/api/sessions` 列表链路 `listSessionViews()` 当前没有补 `goal`。
- 上游 `codex app-server` 协议本身支持 `account/rateLimits/read` 与 `account/rateLimits/updated`，可读取全局 ChatGPT rate limit：
  - `usedPercent`
  - `windowDurationMins`
  - `resetsAt`
  - `rateLimitReachedType`

## 关键判断

- 如果用户说的“额度”指线程自己的 token 预算/已用量：
  - 详情页已有一半基础能力；
  - 线程列表必须新增摘要字段链路，不能只改 UI。
- 如果“额度”指账号或工作区的全局可用额度：
  - 它不应挂在某个线程模型里；
  - 更适合做成独立的“全局额度快照”，在线程列表页头和详情页状态区各显示一次。
- 两种额度都叫“额度”很容易让用户混淆：
  - `线程预算` 更像任务级约束；
  - `账号额度` 更像账户级健康状态。

## 用户进一步确认

- 本轮目标已经收敛为：
  - 只做账号全局额度；
  - UI 只展示两档窗口的使用百分比：
    - 5 小时额度
    - 1 周额度
- 不需要把线程目标预算混入这次设计。

## 协议层补充判断

- 上游协议对账号额度使用 `RateLimitSnapshot.primary / secondary` 表示两个窗口，但文档没有把它们直接命名为“5 小时”和“1 周”。
- 因此实现时不应写死：
  - `primary = 5h`
  - `secondary = 1w`
- 更稳妥的识别方式是按 `windowDurationMins` 判断：
  - `300` 识别为 `5 小时`
  - `10080` 识别为 `1 周`
- 如果未来服务端交换 primary/secondary 顺序，UI 仍能正常显示。

## 建议方案

### 推荐主方案：只做“全局额度”

- bridge 新增全局额度读取与缓存能力，对接 `account/rateLimits/read`。
- Android 增加独立 `QuotaSnapshot` 模型，不塞进 `SessionSummary`。
- 线程列表页头在 `ConnectionSummaryStrip` 下方增加一张紧凑额度卡。
- 详情页在 `StatusStrip` 下方增加同款额度卡，保持信息层级一致。
- 卡片只关心两档窗口：
  - `5 小时`
  - `1 周`
- 每档只显示使用百分比；重置时间可以先不进第一版，避免信息过密。

## UI 落位建议

- 线程列表：
  - 放在 `ConnectionSummaryStrip` 下方。
  - 样式建议与连接卡同层级，但更轻。
  - 横向两栏：
    - `5 小时 42%`
    - `1 周 18%`
  - 若某档达到限制，改为警示色并显示 `已受限`。
- 对话详情：
  - 不建议塞进每条消息区，也不建议挤进输入区。
  - 推荐放在 `StatusStrip` 下方，作为单独一行小卡。
  - 不建议硬塞进 `StatusStrip` 第二层，否则会把现有 `排队 / 目标` 两个高频信息挤得过窄。

## 涉及文件

- Android
  - `android/app/src/main/java/com/openai/codexmobile/model/`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- bridge
  - `bridge/src/app.ts`
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/types.ts`

## 本次未执行

- 未修改功能代码。
- 未运行构建或测试。

## 接口实测

- 用户随后要求先直接测试 `app-server` 额度接口。
- 本次采用 bridge 现有 `AppServerClient` 的同款 stdio 调用方式，直接发起：
  - `initialize`
  - `account/rateLimits/read`
- 实测结果：
  - 调用成功，耗时约 `2225ms`
  - 返回中确实包含两档窗口
  - 当前 `codex` 限额桶为：
    - `primary.windowDurationMins = 300`
    - `secondary.windowDurationMins = 10080`
  - 当前使用百分比为：
    - `5 小时：6%`
    - `1 周：16%`
  - 对应重置时间（Asia/Shanghai）为：
    - `5 小时：2026-05-25 19:51:54 +08:00`
    - `1 周：2026-05-31 08:41:21 +08:00`
- 补充观察：
  - `rateLimitsByLimitId` 中除了 `codex`，还返回了 `codex_bengalfox`
  - 但本轮 UI 需求只需展示主 `codex` 桶即可
  - 返回里 `credits.hasCredits = false`、`balance = "0"`，但不影响两个时间窗百分比读取

## 实施结果

- 已完成 bridge 与 Android 端到端接入。
- bridge 新增全局额度接口：
  - `GET /api/account/quota`
- bridge 侧实现要点：
  - 通过 `account/rateLimits/read` 读取上游额度
  - 优先取 `rateLimitsByLimitId.codex`
  - 用 `windowDurationMins` 识别时间窗：
    - `300` => `fiveHours`
    - `10080` => `oneWeek`
  - 对外返回稳定结构，不把 upstream 的 `primary/secondary` 直接暴露给 Android
- Android 侧实现要点：
  - 新增 `AccountQuotaSnapshot` 模型
  - `BridgeApi` / `RealBridgeDataProvider` / `FakeCodexDataProvider` / `FallbackCodexDataProvider` / `ReplayHarnessActivity` 全部补上 `getAccountQuota()`
  - `AppViewModel` 新增 `accountQuota` UI 状态
  - 刷新时机：
    - 连接成功后
    - 列表刷新后
    - 打开详情后
    - 手动刷新当前详情后
  - 列表页和详情页复用同一个 `AccountQuotaCard`
- UI 落位结果：
  - 列表页：放在 `ConnectionSummaryStrip` 下方
  - 详情页：放在 `StatusStrip` 下方
  - 展示内容：
    - `5 小时`
    - `1 周`
    - 使用百分比
  - 无数据时显示 `暂无数据` / `暂无窗口数据`
  - 达到 `100%` 时显示警示态 `已受限`

## 修改文件

- bridge
  - `bridge/src/types.ts`
  - `bridge/src/bridge-runner.ts`
  - `bridge/src/app-server-runner.ts`
  - `bridge/src/app.ts`
  - `bridge/tests/app.test.ts`
  - `bridge/tests/app-server-runner.test.ts`
- Android
  - `android/app/src/main/java/com/openai/codexmobile/model/AccountQuotaSnapshot.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/BridgeApi.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/RealBridgeDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FakeCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/data/FallbackCodexDataProvider.kt`
  - `android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/AccountQuotaCard.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionListScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
  - `android/app/src/main/java/com/openai/codexmobile/ReplayHarnessActivity.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/PrimaryScreensShowcaseActivity.kt`
  - `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
  - `android/app/src/test/java/com/openai/codexmobile/data/RealBridgeDataProviderTest.kt`
  - `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`

## 验证结果

- bridge
  - `cd bridge`
  - `npm run check`
  - `npm test`
  - 结果：通过
- Android
  - `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`
  - 结果：首次失败，定位到 debug showcase 漏传新参数；补齐后再次执行通过
  - `cd android`
  - `.\gradlew.bat testDebugUnitTest`
  - 结果：通过

## 下一步建议

- 如果进入实现，建议分三步：
  1. bridge 接 `account/rateLimits/read`，并对外提供一个稳定的全局额度接口；
  2. Android 建立 `QuotaSnapshot` 状态，并在列表页、详情页共用同一个额度卡组件；
  3. 用 `windowDurationMins` 识别 5 小时和 1 周窗口，缺任一窗口时显示 `暂无数据`。
