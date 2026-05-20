# codex-mobile bridge 连接超时排查与修复

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`bridge/`、`scripts/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`
- 标签：`Codex`、`bridge`、`连接超时`、`app-server`、`日志`

## 任务输入摘要

- 最终结果：排查 Android 更新后无法连接 bridge 的问题，修复 bridge，并补足下次定位所需日志。
- 现有素材：Android 连接超时日志、bridge 后台启动脚本、项目内 `.logs/bridge/` 日志。
- 明确约束：先定位原因，再修 bridge；补日志；最后重启 bridge。
- 完成标准：明确主故障原因；bridge 修复后健康检查恢复；补桥接层日志与测试；重启后本机与公网健康检查通过。
- 产出后动作：更新记录、检查 git 状态、提交并推送。

## 排查结论

- Android 侧报的是 `https://codex.randolph87.top/health` 读取超时，但主故障不在 Android。
- 直接从当前机器请求同一公网健康检查也会超时，说明问题已经上升到服务端链路。
- 继续检查 `.logs/bridge/bridge-stderr.log` 后确认：后台 bridge 进程已经崩溃。
- 崩溃根因位于 `bridge/src/app-server-client.ts`：
  - `AppServerClient.handleLine()` 默认把 app-server `stdout` 的每一行都当作 JSON-RPC，直接 `JSON.parse(line)`。
  - 实际收到了一行非 JSON 的中文 Windows 文本：
    - `成功: 已终止 PID 1996 (父进程 PID 23768 子进程)的进程。`
  - 这行文本触发 `JSON.parse` 异常，导致整个 bridge 进程退出。
- 结论：这是 `bridge` 对上游 `app-server` / 子进程输出过于脆弱的协议容错问题，而不是 Android 回归。

## 关键修改

- `bridge/src/app-server-client.ts`
  - 为 `LineTransport` 增加 `stderr` 行监听
  - `ChildProcessLineTransport` 开始采集 app-server `stderr`
  - `AppServerClient` 新增 logger 注入
  - 对 app-server `stdout` 做防御性处理：
    - 空行跳过
    - 明显非 JSON 行记录 warning 后忽略
    - 看起来像 JSON 但解析失败的行记录 warning 后忽略
  - app-server `stderr` 行统一写入 bridge warning 日志
- `bridge/tests/app-server-client.test.ts`
  - 补“非 JSON stdout 行不应打死 client，后续 JSON-RPC 仍能继续处理”的测试
  - 补“app-server stderr 行会进入日志”的测试

## 验证结果

- 已执行 `cd bridge; npm run check`
  - 结果：通过
- 已执行 `cd bridge; npm test`
  - 结果：通过，`47` 个测试通过
- 已执行 `powershell -ExecutionPolicy Bypass -File .\scripts\restart-bridge-background.ps1 -HostAddress 0.0.0.0 -Port 8787 -Runner app-server`
  - 结果：后台 bridge 已重启成功
- 已执行 `Invoke-RestMethod http://127.0.0.1:8787/health`
  - 结果：返回 `ok=true`
- 已执行 `Invoke-RestMethod https://codex.randolph87.top/health`
  - 结果：返回 `ok=true`

## 结果

- bridge 不再因为单行非 JSON `stdout` 输出直接崩溃。
- app-server 的 `stderr` 和协议脏行现在会进入 bridge 日志，后续再出现类似问题时可以直接从日志继续追来源。
- 公网域名和本机健康检查均已恢复。

## 风险与后续

- 当前修复是桥接层“抗噪”和“保留证据”，不是从上游彻底消除那条中文输出。
- 下次如果再次出现 warning，应继续追是哪一个上游命令把 Windows 文本写进了 JSON-RPC `stdout` 通道。
- 如果后续发现这类污染频繁出现，建议继续细分：
  - `stdout` 非 JSON 行计数
  - 连续异常阈值告警
  - 必要时把上游原始通道单独落盘
