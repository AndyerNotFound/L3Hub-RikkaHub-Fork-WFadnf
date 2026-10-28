# Fork Notes

本仓库是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的二次改造版，对外名称 **L3Hub**，目标是**与原版共存**（同一台设备上两个应用并排安装）。

## 共存原理

Android 用 `applicationId` 识别应用，不是 Kotlin 包名：

| | 值 | 说明 |
|---|---|---|
| `namespace` | `me.rerere.rikkahub` | 保留，547 个文件的 import 不用动 |
| `applicationId` | `com.rikkahub.l3hub.wfadnf` | 与上游不同，系统视为独立应用 |

- 不同 applicationId + 自己的签名 → 可以共存
- 同一 applicationId + 不同签名 → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
- 改 applicationId 后数据目录独立，读不到原版数据（要迁移走内置备份的导出/导入）

## 本仓库相对上游的改动

1. `app/build.gradle.kts`
   - `applicationId` → `com.rikkahub.l3hub.wfadnf`
   - 移除 Firebase：`google.services` / `firebase.crashlytics` 两个插件 + bom/analytics/crashlytics 三个依赖
   - 新增 `buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")`
2. `build.gradle.kts`：移除两个 Firebase 插件声明
3. `di/AppModule.kt`：移除 `Firebase.crashlytics` / `Firebase.analytics` 两个单例
4. `ui/pages/chat/ChatVM.kt`：移除 `FirebaseAnalytics` 构造参数和 5 处埋点；更新检查受 `UPDATE_CHECK_ENABLED` 控制
5. `ui/pages/chat/ChatDrawer.kt`、`ui/pages/setting/SettingPreferencesNotificationPage.kt`：更新卡片入口受同一开关控制
6. `di/ViewModelModule.kt`：移除 `ChatVM` 的 `analytics = get()` 注入
7. `androidTest/.../ExampleInstrumentedTest.kt`：断言改为 `BuildConfig.APPLICATION_ID`
8. 新增上下文预警进度条（见下节）

### 为什么摘 Firebase

- 原仓库的 `google-services.json` 是 CI 从 secret 注入的，本地没有 → `com.google.gms.google-services` 插件会直接让构建失败
- 就算补上，analytics / crashlytics 也会把数据发到原作者的项目
- 需要自己的埋点或崩溃收集时：注册自己的 Firebase 项目、放回 `app/google-services.json`、恢复上面删掉的 4 处即可

## 上下文预警进度条（fork 新增）

输入框上方两条进度条，分别以模型上的两个门限为分母，共用同一个当前占用量：

| 条 | 含义 | 配置 |
|---|---|---|
| `WARN` | 性能最佳点，超过后回复质量开始下滑 | 模型编辑页 → WARN 阈值 |
| `MAX` | 性能骤降点，到达后禁止继续发送消息 | 模型编辑页 → MAX 阈值 |

- 当前占用取最近一次回复的真实用量（`prompt + completion`，即 `Conversation.lastContextTokens()`），新会话或刚压缩完显示 0
- 点按进度条在百分比和绝对值（`12.3K / 200K`）之间切换，选择存在 `DisplaySetting.contextMeterAbsolute`
- 占用 ≥ 80% 转提醒色，MAX 条满格转 error 色并显示提示
- 两个门限都留空时整块不显示
- 拦截点：聊天页发送（toast）、`ChatService.sendMessage`、语音入队、队列派发（暂停队列）

改动文件（`Model` 多了两个可空字段，旧设置 JSON 缺字段时按 null 处理，向后兼容）：

```
ai/.../provider/Model.kt                        warnContextTokens / maxContextTokens
app/.../data/model/ContextWindow.kt             纯逻辑：比例、是否到达上限
app/.../ui/components/ai/ContextUsageMeter.kt   两条进度条的 UI
app/.../ui/components/ai/ChatInput.kt           插到输入框上方
app/.../ui/pages/chat/ChatPage.kt               传入占用、点击切换、发送拦截
app/.../ui/pages/setting/SettingProviderDetailPage.kt  模型表单里的 WARN/MAX 输入
app/.../service/ChatService.kt                  发送/语音/队列的硬拦截
app/.../data/datastore/Settings.kt              contextMeterAbsolute
```

## 恢复备份时的附件路径改写（fork 必需）

原版把附件存成绝对路径：

```
file:///data/user/0/me.rerere.rikkahub/files/upload/<uuid>.png
```

这条路径同时出现在 `settings.json`（用户头像、助手头像、聊天背景、字体）和数据库的
`conversation.nodes` / `message_node.messages` JSON 里。fork 换了 applicationId，
新应用读不到原应用的私有目录，导入备份后就会出现「聊天记录和配置都在，但图片/文件全是空白」。

`data/sync/BackupPaths.kt` 在 `stageRestore()` 里做前缀改写：

- `settings.json`：整段文本正则替换
- 数据库：遍历所有非 FTS 文本列，取出现过的前缀后用 `REPLACE()` 批量改写
- 同时兼容 `file://` URI 与裸路径（字体）、`/data/user/N` 与 `/data/data`

改写后路径指向当前应用的 `filesDir`，而文件在同一个函数里已经解包到了相同的相对位置，所以能正常显示。
同一包名恢复（前缀相同）时不做任何修改，可重复执行。

> 已经导入过坏数据的，重新导入一次备份即可修复。

另外把 AI 生图的输出目录 `files/images` 加进了备份范围（上游只备份 upload/skills/fonts/media_creation），
否则生成的图片在换包名恢复后同样找不回来。旧备份里没有这个目录，恢复时跳过，不影响。

## 自建功能二：语言 / 调色板 / 上下文开关 / md 导入 / 消息队列

| 功能 | 入口 | 实现 |
|---|---|---|
| 应用内语言 | 设置 → 常规 → 语言 | `utils/AppLanguage.kt`，选择存 SharedPreferences（attachBaseContext 要同步读），Application 和 RouteActivity 各包一层 configuration，选完 `recreate()` |
| RGB/HSV 调色板 | 设置 → 主题 → 自定义颜色 | `SettingThemePage.ColorPickerRow` 从 HSL 改成 HSV/RGB 双模式，文本输入支持 `hsv()`/`rgb()`/`#RRGGBB` |
| MAX 拦截开关 | 模型编辑 → 上下文预警 | `Model.enforceMaxContext`（默认 true）；关闭后到 MAX 只弹提示，`ChatService` 不再硬拦 |
| Markdown 导入 | 备份 → 从 Markdown 导入 | `data/sync/importer/MarkdownChatImporter.kt`，支持 `**User**:` / `User:` / `## User` 三种标记，代码块内不切分 |
| 消息队列开关 | 设置 → 常规 → 消息队列 | `Settings.enableMessageQueue`（默认 true）；关闭后生成中发送 = 打断当前回复并立即发送 |
| 强制插话 | 生成中输入框右侧按钮 | 仅队列开启时出现；`MessageQueue.enqueueFirst` + cancel 当前 job，生成结束立刻派发队首 |

语言支持 en / zh-CN / zh-TW / ja / ko / ru / de / ar，对应 `res/values-*`。

注意：`Settings` 新增字段必须同时加进 `SettingsFields.SETTINGS_FIELDS` 和 `PreferencesStore` 的 key，
否则 `SettingsFieldsTest` 会失败，数据也不会落盘。

## 上下文门限预设

`data/model/ContextThresholdPresets.kt`：按模型名给 WARN/MAX 预填一份，命中不了的用 131072 / 196608。

| 型号 | WARN | MAX |
|---|---|---|
| Kimi K3 | 262144 | 327680 |
| DeepSeek V4.1 | 172032 | 221184 |
| DeepSeek V4 | 196608 | 262144 |
| DeepSeek V3* | 131072 | 131072 |
| GLM 5.* | 147456 | 221184 |
| Qwen 3.* | 196608 | 262144 |
| GPT 5.* | 196608 | 262144 |
| Claude 4.* | 147456 | 221184 |
| Claude 5.* | 221184 | 327680 |
| Step(fun) 3.* | 147456 | 221184 |
| Step(fun) 5* | 196608 | 327680 |
| Nemotron | 131072 | 196608 |
| Muse | 147456 | 221184 |
| GPT OSS | 65536 | 131072 |
| 其他 | 131072 | 196608 |

- 在 `Settings.normalized()` 里执行：`contextThresholdsAuto` 为 true **且**两个门限都为空时才填
- 用户在模型编辑页手改任一值 → `contextThresholdsAuto = false`，之后不再覆盖；页面上会出现「使用预设」按钮可以恢复
- 主版本号从模型名里取（`claude-3-5-sonnet` 算 3 代、`qwen2.5-72b` 算 2 代），避免把旧型号误判成新代

## SSH 工作区的两个修复（fork）

### 远程目录不可访问不再让"测试连接"整体失败

`SshConnection.probe` 以前直接 `stat` 远程目录，目录没权限时 SFTP 抛错，整个测试连接失败，
界面上只剩一句 `SSH_FX_PERMISSION_DENIED: Permission denied`，看不出连接其实已经通了。
现在目录探测失败只作为结果返回（`SshProbeResult.remoteDirIssue`：不存在 / 不是目录 / 权限不足 / 其他），
连接照样成功，编辑页会说明原因，并在测试成功后给一个"主目录"按钮，一键填回远程用户的 home。

典型场景：连 Termux 时把 App 自己的私有目录（`/data/user/0/...`）填进了远程目录，Termux 的 sshd
以另一个 uid 运行，访问不到；换成 `/data/data/com.termux/files/home` 即可。

### 恢复备份后 SSH 凭据解不开

凭据用 Android Keystore 里不可导出的 AES 密钥加密，换设备、重装或换包名（原版 → fork）恢复备份后
密文一定解不开。以前界面仍显示"已保存，留空保持不变"，一测试连接就报
`Saved SSH credentials cannot be decrypted...`。

现在：

- `BackupManager` 导入时调用 `SshCredentialSanitizer.dropUnreadable()`，把解不开的字段清掉
- 打开编辑页时 `WorkspaceRepository.usableSavedSecrets()` 再校验一次并回写，顺手修好已经导入过的旧数据
- 清掉后界面如实显示"未填写"，并提示"已保存的凭据不可用，请重新填写"
- 保存时不再沿用解不开的密文；没有可用凭据时直接提示，不再去连服务器

## 子代理（Sub-Agent，fork 新增）

三处配合使用，形成一个最小的多代理协作：

1. **助手 → 基础**：打开「设为子代理」，这个助手才会出现在别的助手的派发列表里
2. **助手 → 本地工具**：打开「派发子代理」，主助手拿到 `dispatch_sub_agent` 工具，参数为 `assistant`（子代理名称）和 `task`（自包含的任务描述）
3. **聊天页右上角**：任务树按钮，展开当前会话下所有子代理会话，以及每个会话里执行过的工具调用与输出

实现要点：

- 子代理跑在独立会话里，会话带 `parentConversationId`，挂在父会话下面，不占用主对话上下文
- 子代理最多嵌套 4 层，超过后工具直接返回提示；不能把任务派发给自己
- 子代理会话不出现在会话列表与搜索里（`parent_conversation_id = ''` 过滤），只能从任务树进入
- 数据库升到 v33，新增 `parent_conversation_id` / `sub_agent_task` / `sub_agent_state` 三列，走 Room 自动迁移
- 预览版限制：子代理是同步执行的，父回合会等它跑完；子代理如果调用需要用户审批的工具（`ask_user`、需要审批的 MCP 工具），当前会直接判定失败并在任务树里标红

## 性能：只出的那个包按 release 方式编译

`app/build.gradle.kts` 里把 debug 变体改成 `isDebuggable = false`，包名（`com.rikkahub.l3hub.wfadnf.debug`）
和 debug 签名都不变，所以还是覆盖安装、数据不丢。非调试包在安装时会被 ART 做 AOT 编译，也不加载调试钩子，
启动和滚动明显快于调试构建，体积也从 57 MB 降到 42 MB。

`optimization { enable = true }`（R8）默认注释掉了：这台机器上 R8 单步就要 8 分钟，改一行代码再出包太慢。
需要最终版时再打开它，或者交给 CI 跑（`tools/build-apk.sh` 只出包，不跑 lint vital）。

另外两处小优化：

- `SshWorkspaceBackend.listFiles` 不再对目标目录多 stat 一次；含软链接的目录（如 `/usr/bin`）
  改成一条 `exec` 一次性问出哪些子项是目录，服务器不允许 exec 时退回逐个 stat
- `Conversation.lastContextTokens()` 流式输出每个 token 都会调用，改成不再复制整个消息列表

## 还要做的

1. **签名**：在 `local.properties`（已 gitignore）加四项，之后所有版本都用同一个 keystore，否则无法覆盖升级
   ```properties
   storeFile=/absolute/path/to/your.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
2. **图标与链接**：`app_name` 已改为 L3Hub，应用图标仍是上游素材；`ui/pages/setting/SettingAboutPage.kt` 里的 GitHub / Discord 链接也仍指向上游
3. **可选：深链和 action 改名**（两版共存时，外部应用触发这些会弹选择器）
   - `AndroidManifest.xml` 的 `rikkahub://` scheme → 你的 scheme，同步改 `res/xml/shortcuts.xml`
   - `AndroidManifest.xml` 与 `RouteActivity.kt` 里的 `me.rerere.rikkahub.action.*`
4. **构建**
   ```bash
   ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleGithubDebug
   ```

## 验证记录

> 下列数据产生于 applicationId 仍为 `com.rikkahub.fork` 时；改名为 `com.rikkahub.l3hub.wfadnf` 后尚未重新出包验证。

```
./gradlew :app:assembleGithubDebug -x :web:buildWebUi   # BUILD SUCCESSFUL
```

产物与体积（同一套代码）：

| 变体 | 体积 |
|---|---|
| `assembleGithubDebug` universal（arm64 + x86_64，未跑 R8） | 55 MB |
| `assembleGithubRelease` arm64-v8a（R8） | 13.9 MB |
| `assembleGithubRelease` x86_64（R8） | 14.0 MB |
| `assembleGithubRelease` universal（R8） | 16.6 MB |

debug 里 dex 有 38 个、release 压缩后只剩 2 个，差的就是 R8。上面 release 的体积不含 web-ui 静态资源（构建时用了 `-x :web:buildWebUi`）。

- `aapt2 dump badging` → `package: name='com.rikkahub.fork'`（debug 为 `com.rikkahub.fork.debug`）
- `apksigner verify` → V2 签名有效
- 合并后 authorities：`com.rikkahub.fork.debug.fileprovider` / `.documents` / `.androidx-startup`，与原版不冲突
- APK 内无 Firebase 类

> `-x :web:buildWebUi` 只是跳过 React 前端的 pnpm 构建；正式出包要去掉这个参数，否则 APK 里没有 web UI 静态资源。

## 和上游同步

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git rebase upstream/master
```

改动尽量放在独立文件里；上面列的文件是冲突热点，能不加就别加。

## 许可证

AGPL-3.0。分发 APK 或对外提供网络服务时，必须一并提供完整源码。
