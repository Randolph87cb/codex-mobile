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

## DESIGN.md 对比补充

- 时间：2026-05-25
- 新增检查文件：
  - `DESIGN.md`
- 结论：
  - `DESIGN.md` 与现有 UI skill 总体方向一致，没有明显硬冲突。
  - `DESIGN.md` 偏设计系统与视觉规范，提供颜色、字体、圆角、间距、组件语言和品牌语气。
  - UI skill 偏执行约束与仓库落地流程，提供页面优先级、保留中文文案、保护 `TestTags`、避免协议层改动、验证命令等。
  - 两者存在少量重复，但不是互斥关系；更合理的方式是让 skill 引用 `DESIGN.md`，而不是直接删除 skill。
- 风险判断：
  - 如果只保留 `DESIGN.md`，后续 AI/代理在本仓库做 UI 修改时，会缺少项目约束、文件入口、测试要求和“哪些动作必须保留文本按钮”等安全边界。
  - 如果只保留现有 skill，不吸收 `DESIGN.md`，则视觉 token、排版和组件语言会继续分散在轻量规则里，不够系统化。
- 建议：
  - 保留 `codex-mobile-android-ui` skill，但把视觉规范主来源切到根目录 `DESIGN.md`。
  - 将 `references/ui-rules.md` 收缩为“仓库特有补充规则”，避免和 `DESIGN.md` 重复维护。

## skill 合并落地

- 时间：2026-05-25
- 目标：按用户要求把视觉规范放入 skill 目录中作为参考，并删除 skill 内与设计规范重复的视觉描述。

## 实际改动

- 新增：
  - `.codex/skills/codex-mobile-android-ui/references/DESIGN.md`
- 修改：
  - `.codex/skills/codex-mobile-android-ui/SKILL.md`
  - `.codex/skills/codex-mobile-android-ui/references/ui-rules.md`
- 具体处理：
  - 将根目录 `DESIGN.md` 复制到 skill 的 `references/` 目录，作为 skill 内可直接引用的视觉规范文件。
  - 在 `SKILL.md` 中明确 `references/DESIGN.md` 是颜色、字体、间距、圆角、层级和组件样式的主来源。
  - 精简 `SKILL.md` 中重复的视觉方向描述，仅保留仓库约束、页面优先级、验证要求和执行边界。
  - 将 `ui-rules.md` 收缩为仓库特有补充规则，只保留页面顺序、图标替换边界、Compose 安全约束和页面职责提示。
  - 根目录 `DESIGN.md` 未删除，避免影响用户当前手工维护或后续比对；当前存在一份副本，需要后续自行决定是否只保留 skill 内版本。

## 验证

- 已执行：
  - `python "C:\Users\Administrator\.codex\skills\.system\skill-creator\scripts\quick_validate.py" ".\.codex\skills\codex-mobile-android-ui"`
- 结果：
  - `Skill is valid!`
