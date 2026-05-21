# codex-mobile app-server 权限审批排查

- 日期：2026-05-21
- 来源：Codex
- 类型：记录
- 相关目录：`bridge/`、`android/`、`docs/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`bridge`、`app-server`、`approval`、`sandbox`

## 任务输入摘要

- 最终结果：确认当前 `app-server` 是否支持“完全权限 + 不审批”，并查明为什么手机端仍看到审批相关痕迹。
- 现有素材：Android 截图、bridge/app-server 本地代码、上游文档、当前机器上的 Codex 配置。
- 明确约束：本轮先做只读排查，不先改 bridge 或 Android 逻辑。
- 完成标准：确认官方能力边界；确认本机实际生效配置；确认当前 turn 是否仍会触发审批。
- 产出后动作：记录结论，必要时再决定是否补诊断日志或修 UI 表达。

## 核心排查

- 官方文档与本地协议生成物都表明：
  - `approvalPolicy = "never"` 合法
  - `sandbox = "danger-full-access"` 合法
  - `configRequirements/read` 可返回 `allowedApprovalPolicies` 与 `allowedSandboxModes`
- 本机 `C:\Users\Administrator\.codex\config.toml` 中：
  - 未显式设置 `approval_policy`
  - 未显式设置 `sandbox_mode`
  - 仅看到 `[windows].sandbox = "elevated"`
- 直接通过 `AppServerClient` 向本机 `codex app-server` 发起只读诊断请求，结果为：
  - `configRequirements/read` 返回 `requirements = null`
  - 说明当前没有 `requirements.toml` / MDM 之类的要求把审批模式或沙箱模式锁死
  - `config/read` 返回 `approval_policy = null`、`sandbox_mode = null`
  - 说明没有额外的全局默认值覆盖
- 再直接调用 `thread/start`，显式传入：
  - `approvalPolicy = "never"`
  - `sandbox = "danger-full-access"`
  - 返回结果中明确确认：
    - `approvalPolicy = "never"`
    - `sandbox.type = "dangerFullAccess"`
    - `permissionProfile.type = "disabled"`
- 最后执行最小真实回归：
  - 新建临时 thread
  - 发起一个要求模型使用 shell 输出当前目录的 turn
  - 全程监听 `server request`
  - 结果：
    - shell 实际执行成功
    - turn 正常完成
    - `approvalRequests = []`

## 结论

- 当前这台机器上的 `app-server` 已经可以做到：
  - 不审批：`approvalPolicy = never`
  - 全权限模式：`danger-full-access`
- 因此，手机端截图里出现的“本会话都批准（item/commandExecution/request...）”不是因为现在这组配置无效。
- 更大概率是以下两类情况之一：
  - 这是旧会话历史里残留的审批过程项，当前详情页只是把历史 transcript 展示出来
  - 真正碰到的是 Windows 系统权限问题，例如 UAC / 管理员令牌 / 高完整性进程访问限制，而不是 Codex 的审批机制

## 补充判断

- 当前 Android 端会强制把会话视图修正为托管模式 `auto + danger-full-access`。
- bridge 端也会把 `auto` 映射到 `approvalPolicy = never`。
- 但已发生过的审批历史项不会因为后来切换到自动模式而从 transcript 里消失，因此旧会话中看到“已批准本会话”是可能的。

## 后续建议

- 如果要进一步确认是否是“旧历史项误导”，最直接的方法是：
  - 新建一个全新会话
  - 发一条明确要求执行 shell 的消息
  - 看是否仍然出现新的 `requestApproval`
- 如果新会话里仍出现新的审批项，就需要继续补 bridge 诊断日志，记录每次 `thread/start` / `turn/start` 实际发出的 `approvalPolicy` 与 `sandboxPolicy`。
