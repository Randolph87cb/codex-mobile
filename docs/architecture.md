# 架构说明

## 目标

为 `Windows host + Android client` 提供一条比通用聊天应用更短、更快、更容易排障的 Codex 远控链路。

## 总体结构

```text
Android App
    |
    | HTTP / WebSocket
    v
Windows Bridge
    |
    | child process + stdio(JSON Lines)
    v
codex.exe app-server
    |
    +--> file system / git / shell / workspace
```

## 当前关键结论

- Android 不直接连接原始 `codex app-server`，只连接自定义 bridge。
- bridge 不依赖桌面版 Codex UI 先启动；它会在需要时直接拉起 `codex.exe app-server`。
- bridge 现在既可以前台开发运行，也支持后台常驻和登录自启动。
- 图片能力已经是当前实现的一部分，不再属于“非目标”。

## 分层职责

### Android App

- 管理 bridge 地址、token、工作目录、模型和权限配置
- 展示会话列表与详情页
- 渲染实时流、历史 transcript、执行过程和图片
- 维护结构化实时执行活动，不再只靠 transcript 文本猜系统消息边界
- 在前后台切换和实时流恢复后主动补拉详情快照，追平断线窗口内的状态
- 用户发送消息成功后启动会话后台监听前台服务；服务只观察 bridge WebSocket 事件、发送系统通知并在终态或提醒事件后自停，不成为 UI 状态真相源
- 在详情页用轻量状态指标显示后台提醒健康状态，在设置页基于现有 bridge 文件下载接口展示最新调试 APK 链接
- 在发送前预上传图片，再按 bridge 暂存路径提交输入

### Windows Bridge

- 负责启动并维护 `codex.exe app-server`
- 提供移动端稳定接口，而不是把原始 app-server 协议暴露出去
- 管理会话、配置、审批、历史线程详情和实时事件
- 把 `reasoning`、命令执行、文件修改、工具调用等过程事件整形成结构化 `activity`
- 暴露图片上传、图片文件访问和 transcript 整形能力
- 提供 token 鉴权、`cwd` 白名单和本地图片访问边界

### codex app-server

- 提供真实会话、turn、工具调用和审批请求能力
- 通过 stdio JSON Lines 与 bridge 通信
- 不直接暴露给手机端或公网

## 当前主链路

### 文本会话

1. Android 连接 bridge
2. 读取会话列表和详情
3. 建立 WebSocket 实时流
4. 发送文本输入
5. bridge 转发到 `codex.exe app-server`
6. Android 接收 `assistant.delta`、`activity`、`tool.request` 等事件
7. Android 将历史 transcript 与结构化实时活动合并显示，其中非对话类过程默认进入“执行过程”
8. 执行过程按连续片段分组：只有相邻连续的过程项会合并；一旦中间插入 Codex 文字回复，就结束前一段并开始新的执行过程分组
9. 执行过程分组在 UI 上归属于 Codex 回复本身，而不是作为独立系统卡片悬浮在消息流中

### 后台会话监听

1. Android 在 `POST /api/session/:id/input` 成功后启动 `SessionWatchService` 前台服务。
2. 服务读取与 App 相同的 bridge 地址和 token 设置，连接 bridge 后复用 `observeSessionEvents(sessionId)` 监听 `/api/session/:id/ws`。
3. 常驻通知显示“正在等待 Codex 回复”，使用低打扰通知通道。收到 `assistant.done`、`run.status=idle/error`、`run.interrupted`、`tool.request`、`error` 后，服务通过高重要级别结果提醒通道发送系统通知并自停，让线程结束、等待审批、中断和出错尽量以横幅或弹出通知出现。
4. 普通完成通知会先补拉 `GET /api/session/:id`，从最新 transcript 中提取最后一段 Codex 回复的 1-2 行摘要放进大文本通知；如果补拉或提取失败，则回退为“回复已结束，点按返回 App 查看会话。”。等待审批、中断、出错和后台监听中断继续使用专门文案。
5. 结果通知区分“回复已结束 / 等待审批 / 已中断 / 出错 / 后台监听中断”。点击通知会打开 App，并携带 `sessionId`；客户端会保留待打开目标，已连接时直接导航并补拉详情，未连接时主动使用现有 endpoint/token 连接 bridge，连接失败时保留目标并用中文提示用户，稍后连接成功后继续打开。
6. Android 13+ 会在 App 启动时请求 `POST_NOTIFICATIONS`。如果用户未授权，后台监听不因此崩溃，但系统可能不展示结果通知，详情页会显示“通知权限未开启”。
7. App 在发送成功后把输入区清焦点；当前轮运行中保留输入栏内的图标式中断按钮，避免额外提示条挤占正文空间。

### 调试包下载

Android 设置页复用 bridge 的 `GET /api/file/download?path=...`，把 `D:\workspace\codex-mobile\android\app\build\outputs\apk\debug\app-debug.apk` 编码成当前 bridge 下载 URL。App 内点击“下载安装”会下载 APK 到本机缓存，并通过 `FileProvider` 打开系统安装器；复制链接仅作为兜底动作。该能力不新建云服务，也不改变 bridge 协议；当前连接不可用时显示中文提示。

### 图片输入

1. Android 选图后立刻预上传到 bridge
2. bridge 将原图落到本地临时目录并返回 `stagedPath`
3. Android 发送消息时只提交文本和 `stagedPath`
4. bridge 将暂存图片解析为 app-server 可消费的本地图片输入

这样做的目的：

- 避免点击发送时再串行上传大图
- 避免 `JSON + Base64` 在代理链路中放大请求体
- 让会话发送阶段尽量保持轻量

## 运行模式

### 开发模式

- `bridge/npm run dev`
- 更适合本地调试 TypeScript 改动
- 不适合长期后台常驻

### 常驻模式

- 使用 `scripts/restart-bridge-background.ps1`
- 先构建 `dist/`，再后台运行 `node dist/index.js`
- 进程信息写入 `.tmp/bridge/bridge-process.json`
- 日志写入 `.logs/bridge/`

### 登录自启动

- 使用计划任务调用 `scripts/restart-bridge-background.ps1`
- 当前默认支持“当前用户登录后自启动”
- 可选扩展为 `SYSTEM` 的启动时任务

## 安全边界

### 鉴权

- `CODEX_MOBILE_AUTH_TOKEN`
- 开启后除 `/health` 外的所有 `/api/*` 都要求 Bearer token

### 目录白名单

- `CODEX_MOBILE_ALLOWED_CWDS`
- 限制 `POST /api/session` 的工作目录范围

### 图片文件边界

- `/api/image/file` 不是任意文件读取接口
- 只允许 bridge 明确认可的图片路径：
  - bridge 暂存附件目录
  - 白名单工作区内允许暴露的图片文件

## 当前已实现能力

- 会话列表
- 会话详情
- 会话配置修改
- 文本发送
- 图片预上传与图片输入
- 图片缩略图、大图预览和保存
- 历史 transcript 渲染
- 实时流状态同步
- 发送后前台服务后台监听当前会话并发送系统通知
- 结构化实时执行活动聚合
- 操作过程按连续片段合并显示，并挂在 Codex 回复布局内
- 审批请求展示与响应
- bridge 后台守护和登录自启动脚本

## 当前仍在推进的方向

- 进一步完善审批链路和更多 server request 映射
- 持续提高移动端状态识别与恢复能力
- 继续收紧公网暴露场景下的安全与运维文档

## 非目标

- 桌面 UI 远程控制
- 屏幕投射或远程桌面
- 多用户系统
- 把 Android 做成通用聊天平台
