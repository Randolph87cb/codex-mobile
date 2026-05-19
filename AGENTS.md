# codex-mobile 协作说明

## 项目定位

- 本项目是 `Windows host + Android client` 的 Codex 轻量远控方案。
- 目标链路固定为：`Android App -> Windows Bridge -> codex app-server`。
- 不做桌面 UI 控制，不做远程桌面，不让手机端直接连原始 `codex app-server`。

## 当前代码面

- `bridge/`：Windows 侧 companion service，TypeScript + Fastify。
- `android/`：Android 原生客户端，Kotlin + Jetpack Compose。
- `docs/`：架构、接口文档，以及已提交的 `app-server` 协议生成物。
- `scripts/`：常用启动和构建脚本。
- `AI工作记录/`：项目内工作记录。

## 当前目录结构

```text
codex-mobile/
├── AGENTS.md
├── README.md
├── .git/
├── .gitignore
├── .logs/
├── .tmp/
├── .tools/
├── AI工作记录/
├── android/
├── bridge/
├── docs/
└── scripts/
```

- 如果后续新增、删除、重命名顶层目录，或目录职责发生明显变化，修改代码时要同步更新本节。
- 如果某个目录从“本地资产/缓存”变成“需要协作维护的正式目录”，也要同步更新“当前代码面”和本节目录结构。

## 总体原则

- 优先保持“链路短、响应快、可调试”，不要为了完整性引入笨重抽象。
- 对外只暴露自定义 bridge API，不直接暴露原始 `codex app-server`。
- 默认按内网或 Tailscale 使用，不默认裸露公网入口。
- 默认保留审批点，不要未经说明把高风险执行改成全自动。
- 用户可见文案默认使用中文，除非用户明确要求英文。

## Bridge 规则

- bridge 只负责协议整形、会话管理、审批控制、目录边界和事件转发。
- bridge 与 `codex app-server` 的通信优先走 `stdio` JSON Lines，不要擅自切到未验证的传输方式。
- 修改 bridge API 时，必须同时检查：
  - `bridge/src/app.ts`
  - `bridge/src/types.ts`
  - Android 侧 `RealBridgeDataProvider.kt`
  - 对应测试是否需要同步更新
- 如果改动影响历史会话、会话详情或消息展示，优先保证：
  - `/api/sessions`
  - `/api/session/:id`
  - `/api/session/:id/input`
  这三条链路仍然兼容当前 Android 客户端。

## Android 规则

- Android 端优先做轻客户端，不要朝通用聊天应用方向膨胀。
- 第一优先级始终是：
  - 连接 bridge
  - 会话列表
  - 会话详情
  - 发消息
  - 看回复
- 修改 UI 时优先保持低认知负担，不引入不必要的复杂导航。
- 涉及用户可见状态、按钮、错误提示、设置项时，默认用中文。
- 如果 bridge 协议变化，必须同步检查：
  - `AppViewModel.kt`
  - `BridgeApi.kt`
  - `SessionRepository.kt`
  - `RealBridgeDataProvider.kt`

## 协议与生成文件

- `docs/generated/` 下是已提交的协议生成物，默认视为“生成结果”，不要手工做零散修补。
- 只有在明确刷新协议快照时，才整体更新 `docs/generated/`。
- 如果只是 bridge/Android 本地适配，不要顺手改生成文件来“对齐”代码。

## 测试与验证

- 新增功能或调整现有功能时，必须同步补上对应测试；如果暂时无法补测试，必须先说明缺口、风险和补测计划，不能默认跳过。
- 只要改了 `bridge/`，至少执行：
  ```powershell
  cd bridge
  npm run check
  npm test
  ```
- 只要改了 `android/`，至少执行：
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1
  ```
- 涉及 Android 逻辑、状态流或数据层改动时，还要执行：
  ```powershell
  cd android
  $env:JAVA_HOME = "D:\workspace\codex-mobile\.tools\jdk\jdk-17.0.19+10"
  $env:ANDROID_SDK_ROOT = "D:\workspace\codex-mobile\.tools\android-sdk"
  .\gradlew.bat testDebugUnitTest
  ```
- 涉及真机联调或局域网连接问题时，优先使用：
  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\start-bridge-lan.ps1
  ```

## 本地工具链

- `.tools/`、`.logs/`、`.tmp/`、构建产物和缓存目录都是本地资产，不要提交。
- Android 构建默认依赖仓库内 `.tools/` 的 JDK/SDK；如果系统全局环境缺失，优先复用项目内工具链。

## 记录要求

- 每个线程都在 `AI工作记录/records/YYYY/MM/` 下维护一条摘要记录。
- 当目标、方案、关键改动、验证结果、后续事项变化时，持续更新同一条记录。
- 不在记录里保存不必要的完整对话原文，不记录密钥、令牌和敏感信息。

## Git 规则

- Git 命令串行执行。
- 每次准备提交前先看 `git status`，确认没有把缓存、日志、构建产物带进去。
- 完成可交付修改后：
  - 更新工作记录
  - `git status`
  - 使用中文提交信息提交
  - 推送到远端 `main`

## 常见改动检查清单

- 改 bridge API：
  - 同步检查 Android 数据层
  - 补 bridge 测试
  - 必要时补 Android 单元测试
- 改会话显示逻辑：
  - 检查历史会话是否还能显示
  - 检查新建会话发送后是否还能看到回复
- 改中文文案：
  - 检查连接页、会话列表、会话详情、设置页和顶栏是否一致
- 改启动脚本或端口：
  - 检查 `README.md`、脚本和默认 endpoint 是否一致

## 当前已知后续重点

- Android 详情页从 HTTP 轮询升级到 WebSocket 实时流。
- bridge 增加 token 认证、目录白名单和审批响应链路。
- 接通 `/api/session/:id/approve` 的真实审批流程。
