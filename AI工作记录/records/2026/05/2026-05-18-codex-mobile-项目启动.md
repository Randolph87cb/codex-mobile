# codex-mobile 项目启动

- 日期：2026-05-18
- 来源：Codex
- 类型：记录
- 相关目录：`D:\workspace\codex-mobile`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`Android`、`Windows`、`bridge`

## 任务输入摘要

- 最终结果：在 `D:\workspace` 新建一个项目目录，启动 `Windows bridge + Android 原生客户端` 的开发。
- 现有素材：前序调研已确定目标架构是 `Android App -> Windows Bridge -> codex app-server`。
- 明确约束：优先轻量、低延迟、可控；先做 MVP；不要走网页前端路线。
- 完成标准：完成第一版仓库脚手架，并形成 bridge 与 Android 的最小开发骨架。
- 产出后动作：继续把 mock bridge 替换为真实 `codex app-server` 集成，并让 Android 接入真实事件流。

## 关键过程

- 已在 `D:\workspace` 下创建 `codex-mobile` 目录。
- 已建立项目内工作记录目录。
- 已将任务拆成两条并行线：主线程负责 `bridge` 与文档；subagent 负责 `android` 目录骨架。
- 已创建 `README.md`、`docs/architecture.md`、`docs/api.md`。
- 已创建 `bridge` TypeScript MVP 骨架，包含会话管理、输入接口、中断接口和 WebSocket 事件流占位。
- 已在 `bridge` 目录执行 `npm install` 与 `npm run check`，静态检查通过。
- 已实际启动 `bridge` 开发服务，并验证 `GET /health` 与 `POST /api/session` 可返回正确结果。
- 已收取并整合 `android` 目录的 Compose 工程骨架。
- 已补充 Android 网络权限、连接地址语义说明，并新增 `android/README.md`。
- 用户追加要求：必须补测试，保证核心链路正确性。
- 已生成本地 `codex app-server` JSON schema 与 TypeScript 类型到 `docs/generated/`。
- 已确认本地 `codex app-server` 的 `stdio` 协议是 JSON Lines，而不是 `Content-Length` 头格式。
- 已把 `bridge` 重构为 `runner` 可替换架构，新增 `mock` 与 `app-server` 两种 runner。
- 已为 `bridge` 增加自动化测试，覆盖 HTTP 契约、mock runner 事件流、以及 app-server 客户端的请求/通知分发。
- 已执行 `npm test`，当前 3 个测试文件、5 个测试用例全部通过。
- 已在真实 `app-server` 模式下验证：
  - `GET /health` 返回 `runnerMode=app-server`
  - `POST /api/session` 能创建会话并拿到真实 `threadId`
  - `POST /api/session/:id/input` 返回 `{"accepted":true}`
- 时间推进到 2026-05-19，继续在同一项目记录中追加。
- 已为本项目在 `D:\workspace\codex-mobile\.tools\` 下下载并配置本地构建工具链：
  - Microsoft JDK 17
  - Gradle 8.7
  - Android SDK command-line tools
  - Android platform/build-tools 34 与 35
- 已生成 `android/gradlew.bat` 与 `gradle wrapper`。
- 已修复 Android 构建阻塞：
  - Kotlin 2.0 + Compose 缺少 `org.jetbrains.kotlin.plugin.compose`
  - 缺少本地 `sdk.dir`
- 已把 Android 客户端从“仅骨架”推进到“真实 HTTP bridge + fake fallback”模式。
- 已补 Android 最小交互入口：
  - 新建会话
  - 打开会话
  - 发送一条输入
- 已把默认 endpoint 调整为当前本机局域网地址 `http://192.168.31.66:8787`，方便真机直接测试；同时明确该值在网络变化后需要手动修改。
- 已新增脚本：
  - `scripts/start-bridge-lan.ps1`
  - `scripts/build-android-debug.ps1`
- 已执行 Android `assembleDebug`，构建成功。
- 已确认调试包存在：
  - `D:\workspace\codex-mobile\android\app\build\outputs\apk\debug\app-debug.apk`
- 用户在真机试用中反馈：
  - 可以连上 bridge
  - 看不到之前的历史对话
  - 新建会话发送“你是谁”后看不到回复
  - 需要把可见界面文案改成中文
- 已在 `bridge` 增加历史会话能力：
  - `/api/sessions` 现在会读取 `thread/list`
  - `/api/session/:id` 现在会读取 `thread/read(includeTurns=true)`
  - 历史线程会合成为可直接给 Android 使用的会话视图
  - 已支持把历史线程按需附着到本地 `SessionStore` 后继续发送输入
- 已新增 `bridge/src/session-view.ts`，统一把 `SessionRecord` 和 `thread/read` 结果转换为会话摘要/详情视图。
- 已新增 bridge 自动化测试：
  - `tests/session-view.test.ts`
  - `tests/app.test.ts` 新增历史会话读取与附着后发消息覆盖
- 已把 Android 客户端的主要可见文案改为中文：
  - 顶栏
  - 连接页
  - 会话列表
  - 会话详情
  - 设置页
  - 应用名称
- 已调整 Android 导航行为：连接成功后再跳转，不再在点击“连接”时无条件进入会话页。
- 已把 Android 的发送后刷新逻辑改为短轮询：
  - 轮询 `GET /api/session/:id`
  - 最长等待 30 秒
  - 一旦捕获到 `Codex：` 回复片段或会话进入非 `running` 状态，就立刻刷新详情
- 已新增 Android 单元测试 `android/app/src/test/java/com/openai/codexmobile/AppViewModelTest.kt`，覆盖“发送输入后持续轮询直到出现回复”的关键交互。
- 已完成本轮验证：
  - `bridge`：`npm run check`
  - `bridge`：`npm test`
  - `android`：`gradlew testDebugUnitTest`
  - `android`：`gradlew assembleDebug`
- 已重启局域网 bridge，当前仍监听 `0.0.0.0:8787`，本机健康检查通过。
- 已手工验证 `GET /api/sessions` 可返回历史线程，且 `GET /api/session/<threadId>` 能返回包含用户消息和 `Codex` 回复的详情内容。

## 当前结果

- `bridge` 已可作为独立 Node 项目继续安装依赖并启动。
- `bridge` 已支持真实 `codex app-server` runner，但当前真实审批链路仍未接通。
- `bridge` 已支持把历史会话暴露给 Android，并支持对历史线程继续发送消息。
- Android 客户端已具备基础真机试用能力，不再只是静态骨架。
- Android 客户端的主要界面文案已经切换为中文。
- 已完成本机构建验证，当前已有可安装 `debug APK`。
- 当前目录不是 Git 仓库，不涉及提交与推送。

## 后续事项

- [x] 创建项目目录
- [x] 创建基础文档
- [x] 创建 bridge MVP 骨架
- [x] 集成 Android 客户端骨架
- [x] 完成 bridge 基础运行验证
- [x] 为 bridge 接入真实 `codex app-server` 最短链路
- [ ] 为 Android 接入真实 `BridgeApi` 与 WebSocket
- [x] 为 Android 接入真实 `BridgeApi` 的 HTTP 基础链路
- [x] 为 Android 补齐 Gradle wrapper 与本机构建环境
- [x] 产出可安装 debug APK
- [x] 增加 bridge 自动化测试
- [x] 增加 Android 单元测试
- [x] 让 Android 显示历史会话
- [x] 让 Android 发送后可看到回复
- [x] 将 Android 主要界面文案切换为中文
- [ ] 增加认证、目录白名单和审批链路
- [ ] 接通真实审批响应与 `/api/session/:id/approve`
- [ ] 将 Android 详情页从 HTTP 轮询升级为 WebSocket 实时流
