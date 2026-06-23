# codex-mobile 与 Paseo 功能对比

- 日期：2026-06-09
- 来源：AI 对话摘要
- 类型：调研对比
- 相关目录：
  - `README.md`
  - `docs/`
  - `AI工作记录/`
- 标签：
  - `paseo`
  - `feature-compare`
  - `android`
  - `bridge`
  - `codex`

## 本次目标

- 对比当前 `codex-mobile` 与 `getpaseo/paseo` 的公开功能面。
- 判断 `Paseo` 是否已经覆盖我们当前主链路能力。
- 梳理 `Paseo` 相比我们的额外能力，以及我们当前更特化的能力。

## 对比口径

- 本仓库能力面主要依据：
  - `README.md`
  - `docs/api.md`
  - `docs/architecture.md`
- `Paseo` 能力面主要依据 2026-06-09 可见的：
  - GitHub `README.md`
  - GitHub `CHANGELOG.md`
  - 本地临时克隆 `.tmp/paseo-upstream/`

## 当前结论

- 如果只比较“手机远程控制本机 Codex”这条主链路，`Paseo` 已覆盖我们的大部分基础能力，并且在产品面更完整。
- 如果比较我们当前仓库里已经做出的全部细节，`Paseo` 不是逐项一一对应：
  - 我们有一些明显偏 `Codex app-server + Android bridge` 的特化能力；
  - `Paseo` 则明显更强在多端、多 provider、worktree / Git / GitHub 工作流和多 agent 编排。

## Paseo 已覆盖或大概率覆盖的能力

- 手机端连接本机 daemon / host
- 会话或 workspace 列表、详情、流式输出
- 中断、继续发后续消息、归档
- 权限提示与审批流
- 图片附件、Markdown / 代码块展示、代码复制
- 远程连接与跨设备使用

## Paseo 明显比我们多的能力

- 多 provider 统一抽象：
  - `Claude Code / Codex / Copilot / OpenCode / Pi`
- 多端产品面：
  - `iOS / Android / desktop / web / CLI`
- 语音与本地听写：
  - voice mode
  - multilingual local dictation
- worktree 工作流：
  - 新建 worktree
  - 选择 worktree 创建位置
  - 新鲜 worktree 运行完成后自动归档
- Git / GitHub 深度集成：
  - PR sidebar pane
  - 把 GitHub issue / PR 附到 agent prompt context
  - 更完整的 push / pull / merge / shipped 分支工作流
- 远程接入能力更完整：
  - QR 配对
  - remote daemon
  - relay / public service proxy
- 多 agent 编排：
  - `/paseo-handoff`
  - `/paseo-loop`
  - `/paseo-advisor`
  - `/paseo-committee`
- 桌面工作区能力：
  - 多窗口
  - command center
  - workspace services
  - 文件预览 / 终端 / 面板化工作区

## 我们当前更特化或公开信息里未见 Paseo 对等强调的能力

- `Codex app-server` 定制 bridge：
  - 不是通用 agent 抽象，而是围绕 `Codex` 协议稳定化
- 线程级 goal 能力直接暴露到手机端：
  - 创建 / 更新 / 暂停 / 恢复 / 清除
- 账号 quota 快照：
  - `GET /api/account/quota`
  - Android 顶栏 `5 小时 / 1 周` 指示器
- 手机上传媒体自动正式保存到会话 `cwd/mobile_uploads/`
- host 侧安全边界更显式：
  - Bearer token
  - `cwd` 白名单
  - 本地图片读取边界
- bridge 平滑重启窗口 + Android 自动重连 / 补快照
- 视频附件第一阶段支持：
  - 预上传
  - 会话内文件链接展示与下载

## 后续判断建议

- 如果目标是“尽快拥有一个完整成熟的多端 agent 平台”，`Paseo` 当前产品完成度明显更高。
- 如果目标是“围绕 Codex app-server 做一条短链路、强可控、便于后续加审批和安全边界的 Android 远控方案”，当前仓库仍然有清晰差异化。
- 如果后面要继续和 `Paseo` 对标，优先级更适合放在：
  - worktree / Git / GitHub 工作流
  - 多 agent 协作
  - 更完整的远程接入与配对
  - 更丰富的多端表面
