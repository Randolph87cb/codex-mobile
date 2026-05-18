# 架构说明

## 目标

为 `Windows host + Android client` 提供一个比通用 ChatGPT mobile 更轻、更快的本地 Codex 控制链路。

## 总体结构

```text
Android App
    |
    | HTTPS / WebSocket
    v
Windows Bridge
    |
    | stdio
    v
codex app-server
    |
    +--> file system / git / shell / workspace
```

## 分层职责

### Android App

- 负责连接配置、会话展示、流式输出渲染和用户输入；
- 只消费 bridge 提供的稳定协议，不感知本地进程细节。

### Windows Bridge

- 拉起并维护本地 `codex app-server`；
- 做鉴权、目录白名单、审批、会话管理和事件转换；
- 将底层复杂事件整理为移动端更容易消费的统一消息流。
- 当前已确认 `stdio` 传输使用 JSON Lines，一行一个 JSON-RPC 消息。

### codex app-server

- 提供原生会话与工具调用能力；
- 不直接暴露给外网或手机端。

## MVP 范围

- 新建会话
- 查看会话状态
- 向会话发送输入
- 中断当前运行
- 查看流式事件
- 审批事件占位

## 当前实现状态

- `mock runner`：稳定，可用于 Android 联调 UI。
- `app-server runner`：已打通 `initialize -> thread/start -> turn/start -> turn/interrupt` 最短链路。
- 真实审批工作流尚未接入；收到 server request 时当前会回传“不支持”错误，并向 bridge 事件流发出 `tool.request` 占位事件。

## 非目标

- 不做桌面 UI 控制
- 不做屏幕流式投射
- 不做多用户系统
- 不在第一版做图片、语音和附件
