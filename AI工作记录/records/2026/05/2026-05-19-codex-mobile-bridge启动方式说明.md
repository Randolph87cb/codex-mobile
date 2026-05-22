# codex-mobile-bridge启动方式说明

- 日期：2026-05-19
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：
- 相关 skill：
- 标签：

## 本次目标
- 说明如何启动 bridge 服务。

## 结论
- 本地开发默认在 `bridge/` 目录执行 `npm install` 后运行 `npm run dev`。
- 如果要让 bridge 以真实 `app-server` runner 启动，先设置 `$env:CODEX_MOBILE_RUNNER = ''app-server''`，再执行 `npm run dev`。
- 如果要让 Android 真机从局域网访问，优先运行 `scripts/start-bridge-lan.ps1`，该脚本会监听 `0.0.0.0:8787`。

## 参考位置
- `README.md`
- `bridge/package.json`
- `scripts/start-bridge-lan.ps1`

