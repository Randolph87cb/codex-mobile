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

## 针对 `codex-pet-suite` 线程的进一步确认

- 继续排查后，已定位到 `cwd = D:\workspace\codex-pet-suite` 的唯一相关 thread：
  - `threadId = 019e441b-fe4f-7020-bafc-e36735387389`
  - `source = vscode`
- 这条 thread 最后一轮修复“紫底/串帧”的 turn 上下文不是 `never + danger-full-access`，而是：
  - `approval_policy = "on-request"`
  - `sandbox_policy.type = "workspace-write"`
  - `network_access = false`
- 这说明该轮任务本身就运行在一个需要审批、且不是全权限的宿主会话里，不是我们当前 Android bridge 的托管策略。

## 审批项的精确触发点

- 在该 turn 的收尾阶段，模型先执行：
  - `git add ...`
- 随后失败，原始输出为：
  - `fatal: Unable to create 'D:/workspace/codex-pet-suite/.git/index.lock': Permission denied`
- 之后该线程明确进入“需要提权”的分支，并发起了一个带审批参数的命令调用：
  - `git commit -m "修复桌宠透明紫边与相邻动作串帧"`
  - 调用参数里包含：
    - `sandbox_permissions = "require_escalated"`
    - `justification = "Do you want to allow creating the git commit ..."`
    - `prefix_rule = ["git", "commit"]`
- Android 里看到的“本会话都批准（item/commandExecution/request...）”对应的就是这类提权命令审批，而不是普通 shell 在 `never + danger-full-access` 下仍被卡审批。

## 最终判断

- `codex-pet-suite` 这条会话里的审批，根因不是 `app-server` 做不到“完全权限 + 不审批”。
- 根因是：
  - 这条历史 thread 当时运行在另一个宿主会话里，turn context 明确是 `on-request + workspace-write`
  - 提交 Git 时又触发了 `.git/index.lock` 写入权限限制
  - 因而模型改走了 `require_escalated` 的审批流程
- 所以，手机端看到的审批项是这条历史 turn 的真实执行记录，不是当前 bridge 托管模式失效。

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

## 后续实现

- 用户确认后，bridge 已补“历史 thread 首次接管即按托管策略提权”的能力。
- 代码改动：
  - `bridge/src/app-server-runner.ts`
    - 对已 attach 的历史 session，在 `attachSession()` 时先把本地策略提升到：
      - `approvalMode = auto`
      - `sandboxMode = danger-full-access`
    - 对陌生历史 thread 的首次 `thread/resume`，显式携带：
      - `approvalPolicy = never`
      - `sandbox = danger-full-access`
    - 如果上游不接受这些 override，再回退到原来的兼容 resume 路径，避免 attach 失败
  - `bridge/tests/app-server-runner.test.ts`
    - 新增/更新测试，覆盖：
      - 已 attach 历史 session 被再次接管时，会先按托管策略提权
      - 陌生历史 thread 首次 attach 时，会带 `never + danger-full-access` 去 `thread/resume`

## 实现后的边界

- 这个能力只影响：
  - bridge 接管历史 thread 之后的后续 `resume` / `turn`
- 不影响：
  - 旧 turn 已经写进 rollout 的审批记录
  - Windows 自身管理员权限 / UAC / 受保护文件访问限制

## 验证补充

- `cd bridge`
- `npm run check`
  - 通过
- `npm test`
  - 通过
