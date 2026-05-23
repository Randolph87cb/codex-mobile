# codex-mobile Figma UI 设计流程可行性判断

- 日期：2026-05-23
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：`android/`、`AI工作记录/`
- 相关 skill：`record-and-reflect-review`
- 标签：`figma`、`ui`、`workflow`、`design`

## 任务输入摘要

- 最终结果：判断并验证 Figma 能力，随后转为本地 Compose 所见即所得设计，并落地一轮真实 UI 改动。
- 现有素材：用户提供的会话详情 / 输入区参考图，当前项目 Android Compose UI、既有 showcase 活动与截图迭代方式。
- 明确约束：Figma MCP 额度紧张，不适合作为高频试错主战场；用户更关心设计本身，而不是必须使用 Figma。
- 完成标准：确认 Figma 可用边界，切换到本地 Compose 方案，并完成一轮可编译、可测试的 UI 细化。
- 产出后动作：继续以本地 Compose showcase 作为主设计面板，必要时再低频同步回 Figma。

## 背景

用户进一步要求直接安装 Figma 插件，并按提供的参考图尝试做一版 UI 设计，以图片形式展示结果。

## 关键过程

- 读取项目与全局记录规则，确认本线程需要保留项目内摘要记录。
- 检查当前会话可用能力，确认默认工具列表中没有已启用的 Figma 工具。
- 检查可安装插件候选，确认存在 `figma@openai-curated` 插件可供接入。
- 在用户明确要求后发起 `figma` 插件安装请求；当前返回状态为待用户确认安装。
- 在插件尚未可调用的前提下，改为先基于参考图直接产出一版高保真 UI 设计图，保证本轮先有可视化结果。
- 用户确认插件已安装后，继续检查本机 Figma 插件缓存并读取 `figma-use` / `figma-create-new-file` 技能文档。
- 调用 `whoami` 确认当前只有一个 Figma plan：`曹彬's team`，`seatType=view`。
- 成功创建设计文件：`https://www.figma.com/design/g4LSzfTWKGzhBEkcsvQUIJ`。
- 继续检查库与组件时，Starter 计划在 MCP 调用阶段触发 rate limit，组件搜索后续调用开始报限流错误。
- 因 Figma MCP 限额阻塞，切换为“保留已创建 Figma 文件 + 本轮继续输出图片化高保真设计预览”的降级路径。
- 用户后续升级账号后再次检查，`whoami` 返回当前 tier 已变为 `student`，但 `seatType` 仍显示为 `view`。
- 再次执行 `get_libraries` 与 `search_design_system` 均成功，说明此前的 Starter 限流阻塞已解除。
- 使用 `use_figma` 在测试文件内成功创建 `MCP Capability Check` 页面及测试 Frame/文本，确认当前会话已具备 Figma 写入能力。
- 在 `codex-mobile-会话详情-ui-探索-2026-05-23` 文件中新增页面 `会话详情 输入区 v1`。
- 完成首版高保真设计板 `Design Board`，包含标题区、手机壳、会话流、附件卡和底部输入区。
- 成功导出该画板截图，确认当前 Figma 文件内节点已可正常渲染与查看。
- 用户要求继续细化后，再次调用 `use_figma` 读取结构时命中 Education plan 的 MCP tool call limit，当前无法继续实时写回 Figma。
- 为避免中断设计推进，改为先生成一张 `v2` 视觉细化预览图，明确顶部栏、消息气泡、附件卡和底部输入区的下一轮收口方向。
- 进一步复盘本线程 Figma MCP 使用情况：按线程内已发出的 Figma MCP 请求统计，约有 `16` 次调用尝试，其中约 `12` 次成功、`4` 次为超时或限流失败；实际账号剩余额度无法从 MCP 直接读取。
- 当前调用主要消耗在账号/库/组件检索与权限验证，而不是真正的画面搭建本身；后续应减少 `whoami`、`get_libraries`、`search_design_system` 和结构探测类调用。
- 用户明确确认“目标是设计本身，而不是必须使用 Figma”，因此切换为本地 Compose 所见即所得方案。
- 读取 `codex-mobile-android-ui` 项目 skill，并检查 `SessionDetailScreen.kt`、`SessionDetailShowcaseActivity.kt`、`Theme.kt` 与相关 test tag。
- 直接修改真实 `SessionDetailScreen`：统一面板圆角/描边、压紧状态条和目标卡、重构附件托盘比例、放大缩略图窗口、重做底部输入区按钮与输入框比例，让 showcase 更接近设计面板而不是仅仅功能预览。
- 首次 Android 构建暴露 `BorderStroke` 导包与 `defaultMinSize` 依赖遗漏，修复后重新验证通过。
- 用户继续要求“再收一轮”后，补做详情页第二轮微调：提升共享顶栏与 showcase 顶栏的可读性，修正状态条指标过小问题，并继续提高 chip、placeholder 和分隔线的真机可读性。
- 第二轮改动覆盖 `SessionDetailScreen.kt`、`CodexMobileApp.kt`、`SessionDetailShowcaseActivity.kt`，确保模拟器预览与真实页面顶栏节奏一致。
- 用户随后进一步指出状态条下方的状态值文案冗余，因此继续做第三轮小修：移除 `进行中 / 已连接 / 无排队 / 正常` 这一排可见文本，仅保留图标与标签。

## 结果

- 已确认：从流程角度，Figma 适合承担高保真 UI 方案、布局对齐、视觉迭代和设计评审。
- 已确认：`figma` 插件现已可用，并已成功创建一个新的设计文件。
- Figma 设计文件：`https://www.figma.com/design/g4LSzfTWKGzhBEkcsvQUIJ`
- 当前状态更新：账号已切到 `student`，读库、搜组件、`use_figma` 写节点均已验证通过。
- 当前风险：`whoami` 仍回报 `seatType=view`，说明 seat 展示与实际可写能力可能存在 drafts 特例或权限信息滞后；但从实际 MCP 行为看，当前会话已可继续推进 Figma 设计工作流。
- 当前已实际开始设计：首版 `会话详情 / 输入区` 设计稿已落到 Figma 文件中，可继续迭代细节并扩展到其它页面。
- 当前新增阻塞：Education 计划下的 MCP 调用额度仍然较紧，连续操作后会再次触发限流，因此本轮后续细化先保留为图片化预览，待额度恢复后再同步进 Figma。
- 当前结论：后续若继续用 Figma 作为主战场，需要采用“本地高频试错、Figma 低频落板”的省额度工作流。
- 当前结论更新：本地 Compose showcase 更适合当前项目的所见即所得设计流程，Figma 仅保留为低频归档与结构化交付工具。
- 本次已修改 Android UI 代码，并完成调试构建与单元测试验证。
- 实际修改文件：
- `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- `android/app/src/main/java/com/openai/codexmobile/ui/CodexMobileApp.kt`
- `android/app/src/debug/java/com/openai/codexmobile/SessionDetailShowcaseActivity.kt`
- 第三轮仅继续修改 `android/app/src/main/java/com/openai/codexmobile/ui/screen/SessionDetailScreen.kt`
- 验证结果：
- `powershell -ExecutionPolicy Bypass -File .\scripts\build-android-debug.ps1`：通过
- `cd android; .\gradlew.bat testDebugUnitTest`：通过

## 可复用经验

- 在讨论设计协作工具时，需要区分三层：
  - 设计方法是否适合；
  - 当前会话是否已具备可操作工具；
  - 项目内是否已经建立从设计稿到代码的稳定映射规则。

## Skill 观察

- 是否出现新 skill 候选：否
- 是否应该优化已有 skill：暂否
- 触发条件或典型用户说法：`能不能直接用 Figma 设计 UI`、`想要所见即所得地改界面`

## 后续事项

- [ ] 继续在 `g4LSzfTWKGzhBEkcsvQUIJ` 中搭建可编辑稿并整理组件层级。
- [ ] 根据用户反馈继续细化 `会话详情 / 输入区` 的视觉细节与组件复用。
- [ ] Figma MCP 额度恢复后，把 `v2` 细化方向同步回 `会话详情 输入区 v1` 页面。
- [ ] 后续 Figma 改动尽量合并为少量大步骤，避免继续消耗在探测与小步截图上。
- [ ] 如需继续设计迭代，优先继续改 `SessionDetailShowcaseActivity` 驱动下的本地 Compose 预览。
- [ ] 保存并整理本轮 UI 设计图的设计要点，作为后续代码实现依据。
