# codex-mobile 项目文件整理需求巡检

- 时间：2026-05-25
- 目标：只读检查当前项目文件与目录现状，判断是否需要做一轮整理，不修改业务代码。
- 范围：项目根目录、`AGENTS.md`、`README.md`、`docs/api.md`、`.gitignore`、`.codex/skills/`、`mobile_uploads/`、`AI工作记录/records/`。

## 已检查事实

- 根目录当前可见顶层项包括：`.codex/`、`AI工作记录/`、`android/`、`bridge/`、`docs/`、`mobile_uploads/`、`scripts/`、`DESIGN.md` 等。
- `AGENTS.md` 的“当前代码面”和“当前目录结构”仍只描述 `bridge/`、`android/`、`docs/`、`scripts/`、`AI工作记录/`，没有反映 `.codex/`、`DESIGN.md`、`mobile_uploads/`。
- `README.md` 已将 `.codex/skills/` 作为项目目录之一，并把 `mobile_uploads/` 写成图片正式保存目录。
- `docs/api.md` 与 `bridge/src/app.ts` 也都把 `mobile_uploads/` 当成正式上传落盘路径的一部分。
- `.gitignore` 当前忽略了 `node_modules/`、`dist/`、`.gradle/`、`build/`、`.logs/`、`.tmp/`、`.tools/` 等，但没有忽略 `mobile_uploads/`。
- `mobile_uploads/` 当前有 20 个图片文件，总量约 11.89MB，其中至少 5 组文件存在 `xxx` / `xxx (2)` 重复副本。
- 根目录 `DESIGN.md` 与 `.codex/skills/codex-mobile-android-ui/references/DESIGN.md` 并存；两者长度接近，但不是同一文件哈希，说明存在双份维护风险。
- 巡检当时的 Git 工作区包含用户或历史遗留的未提交改动与未跟踪文件，包括 `README.md`、`docs/api.md`、部分工作记录、`DESIGN.md`、`mobile_uploads/`。

## 判断

- 需要整理，但重点不是重构主目录，而是收敛“半正式资产”的状态。
- `bridge/`、`android/`、`docs/`、`scripts/` 这四块主结构目前仍然清晰，不需要大搬家。
- 更需要整理的是三类内容：
  - 结构说明：`AGENTS.md` 与当前仓库实际顶层结构不同步。
  - 上传资产：`mobile_uploads/` 已被实现和文档当成正式目录使用，但既未纳入结构说明，也未建立忽略/清理策略。
  - 设计规范：`DESIGN.md` 在根目录与 skill 引用目录各保留一份，后续容易漂移。

## 建议顺序

1. 先决定 `mobile_uploads/` 的定位：
   - 如果它只是本地运行产物，应加入 `.gitignore`，并考虑迁到 `.tmp/` 或增加清理脚本。
   - 如果它是产品行为的一部分，应补进 `AGENTS.md` 与目录说明，并明确“只作本地资产、不提交”的规则。
2. 同步更新 `AGENTS.md`：
   - 补充 `.codex/skills/`。
   - 按实际定位补充 `DESIGN.md` 与 `mobile_uploads/`，或明确它们为何不进入协作目录结构。
3. 收敛 `DESIGN.md` 的单一事实来源：
   - 要么以根目录版本为主，skill 只引用；
   - 要么以 skill 内 `references/DESIGN.md` 为主，并说明根目录版本是否删除或自动同步。
4. 清一次 `mobile_uploads/` 重复图片，避免继续堆积。

## 巡检阶段未执行

- 未删除任何图片或记录。
- 未修改 `AGENTS.md`、`.gitignore`、`README.md`、`docs/api.md`。
- 未执行构建或测试，因为本次没有代码改动。

## 用户确认后的实际更新

- 时间：2026-05-25
- 用户明确要求：
  - `mobile_uploads/` 作为手机上传图片目录，默认忽略。
  - 其余整理按巡检建议执行。

### 实际改动

- 修改 `.gitignore`
  - 新增 `mobile_uploads/`，避免手机上传图片进入版本控制。
- 修改 `AGENTS.md`
  - 在“当前代码面”补充 `.codex/skills/`、`DESIGN.md`、`mobile_uploads/`。
  - 在“当前目录结构”补充 `.codex/`、`DESIGN.md`、`mobile_uploads/`。
  - 在“本地工具链”里明确 `mobile_uploads/` 属于本地资产，不提交。
- 修改 `README.md`
  - 在目录说明中补充 `DESIGN.md` 与 `mobile_uploads/`。
  - 在图片上传规则里明确 `mobile_uploads/` 默认忽略。
  - 在 UI 协作入口中加入 `DESIGN.md`。
- 收敛 `DESIGN.md`
  - 根目录 `DESIGN.md` 改为入口文件。
  - 单一正式设计规范来源收敛到 `.codex/skills/codex-mobile-android-ui/references/DESIGN.md`。

### 未执行

- 未删除 `mobile_uploads/` 里的现有图片；虽然目录中存在重复文件，但它属于用户运行资产，当前不自动清理。
- 未修改 `docs/api.md`；当前 API 文档对 `mobile_uploads/` 的说明已与实现一致。
- 未执行构建或测试；本次只涉及说明文档与忽略规则，没有代码逻辑变更。
