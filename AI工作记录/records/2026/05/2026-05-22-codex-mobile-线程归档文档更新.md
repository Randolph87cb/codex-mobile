# 2026-05-22 线程归档文档更新

## 目标

- 按协作开发用途更新线程归档相关说明文档。
- 范围限定为：
  - `README.md`
  - `docs/`

## 本次修改

- 更新 `README.md`
  - 在“当前状态”补充 Android 会话列表已支持“当前 / 已归档”切换及单条归档 / 恢复归档
  - 在“设计原则”补充优先归档、不暴露物理删除
  - 在“协作开发入口”增加 `docs/thread-archive-collaboration.md`
- 更新 `docs/api.md`
  - 补充 `GET /api/sessions?archived=true|false`
  - 补充 `POST /api/session/:id/archive`
  - 补充 `POST /api/session/:id/unarchive`
  - 会话摘要响应示例补充 `archived` 字段
  - Android 当前依赖列表补充归档相关接口
- 新增 `docs/thread-archive-collaboration.md`
  - 说明归档与 `codex app-server` 的语义一致性
  - 说明 bridge 列表合并逻辑和 Android 当前交互
  - 说明一期范围边界与协作注意事项
- 更新 `docs/android-ui-collaboration.md`
  - 在 UI 基线中补充会话列表已支持当前/已归档切换与归档交互
  - 在重点文件和交付同步项里补充线程归档文档入口

## 验证

- 本次仅更新文档，未修改 bridge 或 Android 源码逻辑。
- 未运行构建或测试。

## 备注

- 保留工作区内原有未跟踪记录 `AI工作记录/records/2026/05/2026-05-19-codex-mobile-bridge启动方式说明.md`，未触碰。
