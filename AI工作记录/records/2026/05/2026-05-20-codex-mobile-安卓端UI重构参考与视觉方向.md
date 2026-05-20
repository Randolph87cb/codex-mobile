# codex-mobile 安卓端 UI 重构参考与视觉方向

- 日期：2026-05-20
- 来源：Codex
- 类型：记录
- 相关目录：`android/`
- 相关 skill：`record-and-reflect-review`、`delegation-orchestrator`、`imagegen`
- 标签：`Android`、`UI`、`Compose`、`视觉重构`、`图标化`

## 任务输入摘要

- 最终结果：给出安卓端 UI 重构方向，要求更好看、不明显影响性能，并评估哪些按钮适合从文字改为图标；最好提供图片参考。
- 现有素材：当前 Compose 页面代码、主题配置、项目协作规则、项目内历史记录。
- 明确约束：当前先做方案与参考，不直接开始业务代码改造；Android 端保持轻客户端定位，优先保障连接、会话列表、会话详情、发消息、看回复这几条主链路。
- 完成标准：给出适配当前项目的信息架构与视觉建议，指出可图标化的控件，并提供可讨论的参考图方向。
- 产出后动作：如用户确认某一视觉方向，再进入具体 UI 重构实施，补测试并执行 Android 验证脚本。

## 当前现状

- 已检查当前 Android 主要页面集中在：
  - `ui/screen/ConnectionScreen.kt`
  - `ui/screen/SessionListScreen.kt`
  - `ui/screen/SessionDetailScreen.kt`
  - `ui/screen/SettingsScreen.kt`
- 已检查主题层：
  - `ui/theme/Theme.kt`
  - `ui/theme/Color.kt`
  - `ui/theme/Type.kt`
- 当前界面以 Material 3 默认组件为主，结构清楚，但视觉层次、排版张力、控件密度和图标语义还比较保守。
- 当前主题颜色已做基础定制，但字体仍是默认 `Typography()`，品牌感和界面识别度不足。

## 当前判断

- 这次重构优先做“视觉层 + 控件表达 + 信息密度”优化，不优先改动导航和数据流。
- 性能约束下，应优先使用：
  - 现有 Compose Material3 组件
  - 少量稳定图标
  - 轻量背景和卡片层次
  - 避免大面积模糊、复杂阴影、持续动画和高成本自定义绘制
- 适合优先从文字改图标的区域包括：
  - 顶栏设置入口
  - 会话详情刷新
  - 图片附件入口
  - 展开/收起状态切换
  - 复制类辅助操作
- 不适合直接只保留图标的区域包括：
  - “连接桥接服务”
  - “发送”
  - 审批通过 / 拒绝
  - 具有高风险或低频且容易误触的操作

## 建议方向

- 视觉基调建议采用“浅底、低饱和、强层级、弱装饰”的工作台风格，而不是高饱和聊天社交风格。
- 连接页建议强调：
  - 当前连接状态
  - 最近连接
  - 单一主操作按钮
- 会话列表建议强调：
  - 目录分组标题层级
  - 会话状态、更新时间和工作目录的视觉分离
  - 新建草稿入口固定为高显著主操作
- 会话详情建议强调：
  - 顶部状态收纳成更紧凑的信息条
  - 消息区卡片弱化，减少大块边框感
  - 输入区改成“附件 + 输入框 + 发送”的稳定底部工具栏

## 参考来源

- Android Developers `Material components in Compose`
  - 参考点：按钮、图标按钮、卡片、脚手架等组件分工
  - 链接：`https://developer.android.com/develop/ui/compose/components`
- Android Developers `App bars`
  - 参考点：顶栏类型、标题与动作区的组织方式
  - 链接：`https://developer.android.com/develop/ui/compose/components/app-bars`
- Android Developers `Icon buttons`
  - 参考点：图标按钮适合承载低负担、单击可理解的次级动作
  - 链接：`https://developer.android.com/develop/ui/compose/components/icon-button`
- Behance `Mio – Minimal Messaging App UI`
  - 参考点：轻量聊天页的留白、层次和低压视觉
  - 链接：`https://www.behance.net/gallery/223587657/Mio-Minimal-Messaging-App-UI-%28Light-Dark%29`
- Behance `AI Chatbot Mobile App Design | UI/UX Case Study`
  - 参考点：AI 助手场景下的输入区、消息区和工具区布局
  - 链接：`https://www.behance.net/gallery/220143229/AI-Chatbot-Mobile-App-Design-UIUX-Case-Study`

## 补充调研：Codex 可用 UI Skill

- 已按“是否能在 Codex 用、是否偏 UI、是否更贴近移动端/工程落地”做筛选。
- 当前较值得关注的候选：
  - `ceorkm/mobile-app-ui-design`
    - 特点：偏移动端设计流程，强调 8pt 栅格、拇指热区、颜色比例和行业设计惯例。
    - 现状：GitHub 公开仓库，约 25 stars，描述主要面向 Claude Code，但本质仍是 `SKILL.md` 结构，可迁移到 Codex 使用。
    - 适合：先出移动端视觉规范、页面框架和改版原则。
  - `howells/arc` 的 `$design`
    - 特点：明确声明可直接在 Codex 使用，包含 UI/UX 设计、线框、复用模式和 critique 工作流。
    - 现状：仓库活跃度较高，约 494 commits，偏完整工作流而不是单一“美化 UI”规则。
    - 适合：从“需求 -> 设计 -> 实施”一体化推进。
  - `hursh-shah/codex-design-skill`
    - 特点：明确写的是 Codex UI 设计 skill，强调先定视觉方向，再落代码。
    - 现状：体量小，约 2 stars，更像轻量实验型 skill。
    - 适合：作为灵感和 prompt 结构参考，不建议单独作为主依赖。
  - `vadimcomanescu/codex-skills` 里的 `frontend-design`
    - 特点：是一个 Codex 技能目录，`frontend-design` 属于 curated 稳定项，另有 `ui-design-system`、`accessibility-auditor` 等设计相关技能。
    - 现状：更偏 Web/Next.js，不是移动端专用。
    - 适合：抽取它的设计约束和审美规则，少量借鉴，不适合作为 Android 直接模板。
  - `addyosmani/agent-skills` 的 `frontend-ui-engineering`
    - 特点：目标明确，要求 UI 看起来像顶级产品工程师做的，而不是 AI 生成物。
    - 现状：偏工程实现规范，不是移动端视觉专用。
    - 适合：给真正开始写界面代码时补“实现质量约束”。

## 当前结论

- 如果目标是“给 Codex 一个更会做移动端 UI 的工作流”，目前最值得参考的是：
  - 移动端设计思路：`ceorkm/mobile-app-ui-design`
  - Codex 直接可用工作流：`howells/arc` 的 `$design`
  - 代码落地质量约束：`addyosmani/agent-skills` 的 `frontend-ui-engineering`
- 如果你后面要我真的给这个仓库装一个项目级 UI skill，我建议不是直接照搬某个现成 skill，而是：
  - 以 `ceorkm/mobile-app-ui-design` 的移动端规则为骨架
  - 混合 `frontend-ui-engineering` 的实现约束
  - 再按你这个项目“轻客户端、低认知负担、中文文案、Compose + Material3”的特点定制一个项目 skill

## 后续建议

- 基于当前产品定位，优先讨论三块：
  - 连接页：做成单卡片主入口，突出连接状态与最近使用连接
  - 会话列表：强化目录分组层次、时间信息和草稿创建入口
  - 会话详情：把顶部状态条做得更轻，把输入区做成更像“工作台”
- 如果用户确认方向，可继续输出：
  - 低保守版：仅换主题、间距、按钮和图标
  - 中等重构版：重做列表卡片、状态条、输入区
  - 强风格版：连同品牌色、字体、页面节奏一起重塑
