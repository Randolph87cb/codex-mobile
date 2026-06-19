# Bridge 后台脚本验收

本文档记录 `scripts/restart-bridge-background.ps1` 与 `scripts/stop-bridge-background.ps1` 的脚本级验收入口。该验收不依赖 Pester，不启动真实 bridge，不杀真实进程；测试会复制脚本到临时仓库目录，并在子 PowerShell 进程中用函数桩替代进程、健康检查和 `taskkill.exe`。

## 运行方式

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\tests\bridge-background.acceptance.ps1
```

如果本机安装了 PowerShell 7，脚本会优先用 `pwsh` 执行子用例；否则回退到 `powershell.exe`。

## 覆盖点

- restart lock 竞争：`.tmp\bridge\restart-bridge.lock` 被其他进程以 `FileShare.None` 持有时，`restart-bridge-background.ps1` 必须 `exit 0`，写控制日志，并且不能调用 stop、不能调用 drain 或 health HTTP 请求、不能 `Start-Process` 启动新 bridge。
- 重复后台启动：已有 state 文件时，`restart-bridge-background.ps1` 会先走停止逻辑，但不能再请求 `/internal/lifecycle/drain`；控制日志需要记录 drain 被跳过或没有触发。
- `Stop-Process` 失败：`stop-bridge-background.ps1` 必须尝试 `taskkill.exe /PID <pid> /T /F` fallback。
- fallback 成功：确认 state 文件被删除，控制日志包含 `Stop-Process failed`、`attempting taskkill fallback`、`bridge stopped and state file removed` 等关键事件。
- fallback 失败：脚本必须非零退出，保留 state 文件，并在控制日志中记录失败及保留 state 的原因。

## 未覆盖风险

- 验收使用命令桩，不覆盖真实 Windows 权限边界下 `Stop-Process` 与 `taskkill.exe` 的系统行为。
- 验收不启动真实 `node dist/index.js`，只验证脚本编排、退出码、state 文件处理和控制日志。
- 重复启动用例使用临时 stop stub 让 `restart` 继续执行到启动阶段，因此不覆盖 `restart` 到真实 `stop` 脚本的跨进程环境差异；真实 `stop` 的 fallback 行为由另外两个 stop 用例覆盖。
- restart lock 用例在父测试进程持有锁文件，子进程运行 restart 脚本；它覆盖同机文件锁竞争，不覆盖 Windows 登录启动项重复触发的来源归因。
