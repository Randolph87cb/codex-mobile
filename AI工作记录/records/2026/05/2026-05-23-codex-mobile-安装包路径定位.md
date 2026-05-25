# codex-mobile 安装包路径定位

- 日期：2026-05-23
- 来源：AI 对话摘要
- 类型：记录
- 相关目录：`android/`
- 相关 skill：`record-and-reflect-review`
- 标签：`android`、`apk`、`build-output`、`path`

## 本次目标

- 目标：按用户要求，仅提供当前项目内安装包路径的 Markdown 结果。
- 操作：只读扫描仓库内现有 `*.apk` / `*.aab` 文件，确认 Android 构建产物是否存在。
- 结果：定位到应用安装包 `android/app/build/outputs/apk/debug/app-debug.apk`；另有 `androidTest` 测试安装包与 Android SDK 模拟器 overlay APK，不作为主安装包返回。
- 文件修改：无代码修改。
- 验证：通过文件系统扫描确认路径存在。
