# 会话详情页 UI 说明

本文档只聚焦 Android 会话详情页当前的 UI 结构、截图验证方式，以及 showcase 截图与真实线程页之间的边界。

## 当前已落地的 UI 调整

- 顶部状态条已经压缩成紧凑四列指标。
- 目标卡默认态优先展示 `目标 + 状态 + 一行正文`，展开后再看完整信息。
- 待发送图片区使用固定尺寸预览窗，不再按原图比例把托盘整体拉高。
- 点击待发送图片缩略图后，仍然查看原图。
- `你 / Codex` 的真实聊天正文宽度已经单独放宽，避免手机端只剩过窄阅读列。

## 截图验证入口

当前会话详情页的视觉回归主要依赖两组验证：

1. `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailScreenshotTest.kt`
   作用：
   - 产出 showcase 截图
   - 稳定比较层级、间距、卡片宽度、待发送图片区样式

2. `android/app/src/androidTest/java/com/openai/codexmobile/SessionDetailReplayTest.kt`
   作用：
   - 回放真实详情页链路
   - 确认 UI 改动没有把真实页面打坏

当前参考图产物位于：

- `.tmp/ui-screenshots/session-detail-showcase-full-v*.png`
- `.tmp/ui-screenshots/session-detail-pending-tray-v*.png`

## 重要边界

showcase 截图不是“真机真实工作线程截图”。

原因很直接：

- showcase 吃的是 `SessionDetailScreenshotTest.kt` 里手工构造的标题、目标、短消息样本和固定附件集。
- 真实手机线程页直接吃 bridge 返回的真实 `transcript`、执行过程、目标正文和待发送附件。

这意味着：

- showcase 越来越像参考稿，只能证明“样板页结构更像”；
- 它不能自动证明“你手机上正在工作的那条真实线程也会一样像”。

## 后续如果目标是“真机真实线程也像参考图”

不要只抠 showcase 截图，还要优先检查这些真实页面策略：

- 消息正文宽度是否足够
- 长回复是否需要默认折叠
- 系统 / 执行过程卡是否需要默认摘要化
- 目标卡默认态是否还需要继续压缩
- 用户那条线程里是否真的存在待发送图片区，而不是只有历史消息

## 最近一次和真实可读性直接相关的调整

当前真实消息宽度参数已经放宽为：

- 用户消息约 `74%`
- Codex 消息约 `80%`

同时把消息气泡左右额外偏移从 `18dp` 收到 `10dp`，避免在真机上出现“正文只占大约三成屏宽”的阅读问题。
