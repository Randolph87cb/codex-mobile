# bridge restarting 状态排查

- 时间：2026-06-19 21:17 +08:00
- 目标：排查手机端发送消息时持续显示 bridge restarting 的原因。
- 只读检查：
  - `.logs/bridge/bridge-stdout.log` 与 `bridge-stderr.log` 均为 0 字节，没有可用运行日志。
  - `8787` 端口由 PID 2400 的 node 进程监听，启动时间为 2026-06-19 09:46:14，未观察到发送消息导致 bridge 进程反复重启。
  - `/health` 返回 `lifecycle.phase = "restarting"`、`draining = true`，`drainStartedAt = 2026-06-19T01:46:42.187Z`。
  - `/api/sessions` 可读，说明读接口仍可用；mutating 请求会因 draining 状态被拦截。
- 初步结论：
  - bridge 不是每次发送都真实重启，而是早上一次 drain 后停留在 `restarting/draining` 状态。
  - 源码中 `beginDrain()` 只有进入 draining 的路径，没有恢复 running 的路径；设计上依赖 `restart-bridge-background.ps1` 调用 `stop-bridge-background.ps1` 杀掉旧进程并启动新进程。
  - 当前旧进程未被杀掉，导致后续手机端每次发送都收到 `bridge-restarting`。
- 进一步排查：
  - 未发现匹配 `CodexMobileBridge`、`restart-bridge`、`codex-mobile` 的 Windows 计划任务。
  - 未发现匹配 bridge/codex-mobile 的 Windows 服务。
  - Registry Run/RunOnce 中未发现 bridge 启动项。
  - 用户 Startup 文件夹存在并启用 `CodexMobileBridge.cmd`，内容为调用 `scripts/restart-bridge-background.ps1 -HostAddress 0.0.0.0 -Port 8787 -Runner app-server`。
  - 全局搜索只明确找到一个 `CodexMobileBridge.cmd`，未确认存在多个同名启动脚本。
  - PowerShell Operational 日志显示 2026-06-19 09:46:02 启动过一次 `restart-bridge-background.ps1`，随后 09:46:33 与 09:46:41 又出现新的 PowerShell 控制台启动。
  - 2026-06-19 09:46:44 日志显示 `stop-bridge-background.ps1` 执行 `Stop-Process` 时无法停止 `node (2400)`，错误为“拒绝访问”。
  - `tasklist` 显示 PID 2400 属于当前用户 `DESKTOP-5G7VMQQ\hetao`，不是 SYSTEM 服务进程。
- 深挖结论：
  - 目前证据不支持“注册了多个 bridge 服务”或“多个 bridge 同时监听端口”；`8787` 只有 PID 2400 一个监听进程。
  - 更可能的链路是：Startup 文件夹启动项先启动 bridge，随后同一个启动脚本又被重复触发；第二次触发时调用 `/internal/lifecycle/drain` 让 PID 2400 进入 draining/restarting，但 stop 阶段因拒绝访问失败，导致旧进程永久卡在 restarting。
  - 仍未能仅凭现有日志区分第二次触发来自 Windows 登录阶段重复执行 Startup 项，还是用户/其他工具手动执行了相同脚本；脚本自身缺少启动锁和详细日志，导致来源不可完全还原。
- 未执行：
  - 未重启 bridge。
  - 未修改业务代码。

## 2026-06-19 后台脚本测试与验收补充

- 目标：为 bridge 后台启动/停止脚本修复补脚本级验收，不改 `scripts/restart-bridge-background.ps1` 与 `scripts/stop-bridge-background.ps1`，不提交 Git。
- 写入：
  - 新增 `scripts/tests/bridge-background.acceptance.ps1`，采用无 Pester 依赖的 PowerShell 子进程验收；复制脚本到临时仓库目录，并用函数桩替代进程、健康检查和 `taskkill.exe`。
  - 新增 `docs/bridge-background-script-acceptance.md`，记录运行方式、覆盖点和未覆盖风险。
- 覆盖：
  - restart lock 被另一个进程以 `FileShare.None` 持有时，restart 脚本应 `exit 0`，写控制日志，且不能调用 stop、drain 或 `Start-Process`。
  - 重复后台启动不能请求 `/internal/lifecycle/drain`，并要求控制日志记录 drain 被跳过或未触发。
  - `Stop-Process` 失败后必须尝试 `taskkill.exe` fallback。
  - fallback 成功时删除 state 文件并记录关键控制日志。
  - fallback 失败时非零退出、保留 state 文件并记录失败原因。
- 验证：已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\tests\bridge-background.acceptance.ps1`，新增 lock 竞争用例后共 4 个脚本级验收用例通过。

## 2026-06-19 拉取 main 并重新注册 bridge 服务

- 目标：拉取远端 `main` 最新内容，并重新注册本机 bridge 后台服务。
- 执行：
  - `git pull origin main` 快进到 `3253b7a`。
  - 当前 `CodexMobileBridge` 计划任务原配置为登录触发，监听 `0.0.0.0:8787`，runner 为 `app-server`。
  - 先执行 `scripts/uninstall-bridge-autostart.ps1 -TaskName CodexMobileBridge` 移除旧计划任务。
  - 再执行 `scripts/install-bridge-autostart.ps1 -TaskName CodexMobileBridge -HostAddress 0.0.0.0 -Port 8787 -Runner app-server` 重新注册计划任务。
  - 执行 `Start-ScheduledTask -TaskName CodexMobileBridge` 启动任务。
- 验证：
  - `http://127.0.0.1:8787/health` 返回 `ok = true`，`runnerMode = app-server`，`lifecycle.phase = running`。
  - 计划任务 `CodexMobileBridge` 存在，状态为 `Ready`，动作指向 `scripts/restart-bridge-background.ps1`。
