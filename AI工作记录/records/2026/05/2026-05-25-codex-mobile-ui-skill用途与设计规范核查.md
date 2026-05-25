# codex-mobile UI skill 用途与设计规范核查

- 时间：2026-05-25
- 目标：确认项目内 UI skill 当前用途、实际内容，以及 AI Studio 风格重构后是否存在 `design.md` 形式的 UI 设计规范。
- 范围：只读检查 skill 文件、引用文档与项目内相关工作记录，不修改业务代码。

## 已检查文件

- `.codex/skills/codex-mobile-android-ui/SKILL.md`
- `.codex/skills/codex-mobile-android-ui/references/ui-rules.md`
- `.codex/skills/codex-mobile-android-ui/agents/openai.yaml`
- `AI工作记录/records/2026/05/2026-05-20-codex-mobile-安卓端UI重构参考与视觉方向.md`
- `AI工作记录/records/2026/05/2026-05-24-codex-mobile-android-ui页面地图梳理.md`

## 关键结论

- 当前项目内的 UI skill 名称为 `codex-mobile-android-ui`，定位是指导本仓库 Android Compose UI 的重设计与抛光。
- skill 重点约束的是：
  - 保持 Android 端为轻客户端，不把产品做成通用聊天应用；
  - 优先处理连接、会话列表、会话详情、设置四个页面；
  - 优先做主题、间距、卡片层级、图标化次级操作等轻量 UI 重构；
  - 保留中文文案、性能、`TestTags`、bridge 驱动的数据流和现有验证流程。
- 当前 skill 目录内没有 `design.md`。
- 当前与 UI 设计规范最接近的文件是 `.codex/skills/codex-mobile-android-ui/references/ui-rules.md`，它提供的是轻量规则，不是完整设计系统文档。
- 项目工作记录表明，AI Studio 版本主要被当作视觉结构参考与详情页样式参考使用，没有同步落成单独的 `design.md` 规范文件。

## 补充说明

- `.codex/skills/codex-mobile-android-ui/agents/openai.yaml` 只定义了该 skill 的展示名、简述和默认提示词。
- 项目根目录与 `.codex/` 目录下未发现名为 `design.md` 的文件。
