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
- 在发送前预上传图片，再按 bridge 暂存路径提交输入

### Windows Bridge

- 负责启动并维护 `codex.exe app-server`
- 提供移动端稳定接口，而不是把原始 app-server 协议暴露出去
- 管理会话、配置、审批、历史线程详情和实时事件
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
- 操作过程合并显示
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
