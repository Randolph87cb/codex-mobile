# codex-mobile 中文文件下载修复

- 日期：2026-06-05
- 来源：AI 对话摘要
- 类型：缺陷修复
- 相关目录：
  - `bridge/`
  - `android/`
  - `AI工作记录/`
- 标签：
  - `download`
  - `bridge`
  - `android`
  - `http-header`
  - `utf8`

## 本次目标

- 排查手机端下载图片时报 `HTTP 500` 的具体原因。
- 在不改 Android 主流程的前提下修复 bridge 对中文文件名下载的兼容问题。
- 补上能覆盖中文文件名场景的测试。

## 问题定位

- Android 下载入口走的是 `android/app/src/main/java/com/openai/codexmobile/ui/screen/TranscriptFileSupport.kt` 中的 `/api/file/download?path=...`。
- 实际报错发生在 bridge 返回下载响应时。
- 日志 `.logs/bridge/bridge-stdout.log` 显示：
  - 请求路径为 `/api/file/download?path=D:\workspace\...番茄鸡蛋米线-手绘注解样张.png`
  - bridge 抛出 `ERR_INVALID_CHAR`
  - 具体错误为 `Invalid character in header content ["content-disposition"]`
- 根因是 bridge 在 `Content-Disposition` 的 `filename="..."` 段里直接放入中文文件名，Node `setHeader()` 不接受该写法。

## 本次改动

- 修改 `bridge/src/attachment-service.ts`
  - `filename=` 改为纯 ASCII fallback 文件名
  - 保留原扩展名
  - `filename*=` 继续使用 UTF-8 编码后的原始文件名
- 修改 `bridge/tests/app.test.ts`
  - 新增中文文件名下载测试
  - 断言返回 `200`
  - 断言 `content-disposition` 同时包含 ASCII fallback 和 UTF-8 `filename*=`

## 验证结果

- `cd bridge && npm run check`
  - 结果：通过
- `cd bridge && npm test`
  - 结果：通过
  - 汇总：`5` 个测试文件，`75` 个测试全部通过

## 当前结论

- 这次故障不是手机下载权限问题，也不是 bridge 路径白名单问题。
- 问题集中在 bridge 下载响应头生成。
- 修复方案采用标准兼容写法：
  - `filename=` 只放 ASCII fallback
  - `filename*=` 放 UTF-8 原始中文文件名
