---
name: publish-release
description: Use this skill when users request to publish an release update.
---

# Publish Release

本仓库是 L3Hub fork。发布构建与 GitHub Release 由 `.github/workflows/build-apk.yml` 负责；不要使用上游的 `release.yml`、原版包名或 Play 发布流程。
推送正式版 tag 会直接触发构建并发布 Release，因此推送前必须先向用户确认。Termux 不做本地构建。

## 更新日志

- 查看从上一次release tag到当前commit的修改
- 总结更新内容并生成更新日志, BUG修复和UI调整尽可能合并，确保总更新日志条目数量不超过 10条
- 日志中避免出现技术名词
- 每条只写一句话，说清核心功能即可；不要附带同一功能的次要行为、交互细节或提示类改动（如「多张图片左右滑动切换」「为空时显示提示并可跳转设置」），这些直接省略
- 功能条目中文不超过 20 个字，英文同样简短；合并的 BUG 修复/UI 调整条目可以例外，但只列问题本身

使用以下格式:

```markdown
更新内容:

- xxx
- xxx

Updates:

- xxx
- xxx
```

(双语更新日志)

更新日志生成后先交给用户确认。正式版若存在 `docs/releases/<tag>.md`，CI 会优先采用该文件；否则由 `build-apk.yml` 按提交记录生成 Release notes。

## 流程

1. **检查版本**：读取 `app/build.gradle.kts` 的 `versionName` 和 `versionCode`，再对照 `FORK.md` 的正式版 tag 规则及远端已有 tags。正式版 `versionCode` 必须高于上一个正式版；tag、版本名或代码不一致时先与用户确认，不要自行改版本号。
2. **检查发布目标**：确认当前仓库是 L3Hub fork、目标分支和提交已推送到 `origin`，工作区没有未包含的改动。不要把命令指向 `upstream`。
3. **生成更新日志**，和用户探讨确认。
4. **发布前确认**：说明将要推送的 tag 与提交。用户确认后，按 `FORK.md` 的发版说明创建并推送正式版 tag；tag 推送会直接构建 APK 并发布 GitHub Release。
   ```bash
   git tag <tag>
   git push origin <tag>
   ```
5. **等待 CI**：从当前 fork 仓库查询 `.github/workflows/build-apk.yml` 对应的运行，等待完成并核对 Release 资产。不要使用 `-R rikkahub/rikkahub`。
   - 失败时查看失败日志并汇报；确认修复后重跑该 workflow，不要擅自移动或删除正式版 tag。
   - tag push 会直接发布，后续步骤不再创建草稿或执行第二次发布。
6. **仅需 artifact 时**：使用 `build-apk.yml` 的 `workflow_dispatch`，tag 留空；这只构建并上传 artifact，不发布 Release。
