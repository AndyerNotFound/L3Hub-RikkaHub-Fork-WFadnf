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
| 消息队列开关 | 设置 → 常规 → 消息队列 | `Settings.enableMessageQueue`（默认 true）。关闭后一条用户消息都不进队列：生成中或等工具审批时打断当前回复、立即发送（待审批的工具标成取消）；正在压缩上下文、或队列暂停还压着旧消息时拒绝并提示、输入框内容保留；语音模式同时改成一轮说完再开麦。判定收在 `service/MessageQueuePolicy.kt` |
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

## SSH 工作区修复（fork）

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

### SSH 工作区工具与内置工作区统一

SSH 工作区原本只有读工具（列表 / 读文件 / shell），写文件工具（`workspace_write_file` /
`workspace_edit_file` / `workspace_apply_patch`）和后台命令工具（`workspace_shell_output` /
`workspace_shell_kill`）只在本地 PRoot 工作区有。现在把 SSH 工作区的 `fileWriteTools` 与
`backgroundShell` 两个能力位翻成 true，两个工作区的 AI 工具集一致，app 侧 `WorkspaceTools`
按能力位自动注册，无需改动：

- 写文件直接走 SFTP（`writeText` / `writeAbsoluteText` 原本就有），覆盖写沿用现有实现
- 后台命令在 `SshWorkspaceBackend.startBackgroundCommand` 实现，状态文件放远程
  `/tmp/rikkahub/background/<taskId>.{log,pid,status}`（与本地 Rootfs 的目录约定对应）：
  - 启动：用探测到的登录 shell 起 `setsid`（无则 `nohup`）子 shell 脱离会话运行；命令包在
    `( eval ... )` 子 shell 里（命令里的 `exit` 只结束子 shell，包装进程不死，退出码照常落状态文件）；
    退出码先写临时状态文件再 rename，等待方读到的永远是完整内容；pid 由内层 shell 的 `$$` 写入
    （setsid fork 时外层 `$!` 不是组长）
  - `waitFor`：一条阻塞 exec 轮询状态文件，进程连续 3 秒失联且无状态文件时报 -1；exec 异常时
    退回 SFTP 读状态文件
  - `readOutput` / `outputSize`：SFTP 按 `offset` 区间读日志，`RESUME` 模式让 skip 生效，
    监控回调累计到长度后中断传输，缓冲超出部分按长度截断
  - `kill`：先按进程组 TERM、宽限 3 秒后 KILL（`kill -TERM -pid` 在 dash/bash/GNU 通用，
    不带 `--`）；组杀不可用时退回 `pkill -g`，再退回 `pkill -P` + 单进程
- 单测用嵌入式 Mina SSHD 覆盖退出码 / 日志区间读 / 工作目录 / kill（含孙进程）

## 子代理（Sub-Agent，fork 新增）

三处配合使用，形成一个最小的多代理协作：

1. **助手 → 基础**：打开「设为子代理」，这个助手才会出现在别的助手的派发列表里
2. **助手 → 本地工具**：打开「派发子代理」，主助手拿到 `dispatch_sub_agent`（`assistant` / `task` / 可选 `wait` / `timeout_seconds`）和 `await_sub_agent`（`conversation_id` / 可选 `timeout_seconds`）两个工具。派发能力只看这个开关（以及「当前会话自己是不是子代理会话」），「设为子代理」只表示「能被派发」，不再顺带给出派发工具
   - 开关下方的「允许派发的子代理」可以多选目标：勾选后工具目录与 `SubAgentRunner` 都只认这些助手，不勾选表示不限制（老配置行为不变）。子代理继续派发下一层时用它自己那份限制
3. **聊天页右上角**：任务树按钮，展开当前会话下所有子代理会话，以及每个会话里执行过的工具调用与输出

实现要点：

- 子代理跑在独立会话里，会话带 `parentConversationId`，挂在父会话下面，不占用主对话上下文
- 子代理最多三代（主 → 子 → 孙），超过后工具直接返回提示；不能把任务派发给自己
- 子代理会话就是普通会话：和别的会话一样出现在侧边栏与「历史会话」里，也能被搜索到；打开时侧边栏会跟着切到它自己的助手，`parentConversationId` 只用来画任务树
- 数据库升到 v33，新增 `parent_conversation_id` / `sub_agent_task` / `sub_agent_state` 三列，走 Room 自动迁移
- 子代理会话没有人在旁边点确认，工具调用一律按完全权限直接执行；`ask_user` 没有真人可答，直接返回提示让子代理自己决定
- `wait` 模式：`wait=true` 时父回合停在这次工具调用里，等子代理跑完第一轮，结果直接作为工具结果返回；默认等 600 秒，可传 `timeout_seconds`（夹在 30–1800 之间）。超时或父回合被掐断，这一轮照样落盘并按后台通知补投，不会丢
- 子代理没跑出结果（被停掉、生成出错、卡在工具审批、回复为空）时，`wait=true` 的调用会立刻拿到失败说明，不会干等到超时
- `background` 模式：派发后立即返回。子代理每一轮生成结束都把这一轮的结果投回父会话（自己派的孙代理回传结果后触发的新一轮同样回传），父会话正在生成时先缓冲，空闲后把多条结果合并成一条消息投递，不打断当前工作；回传正文带 `finished round N`，父会话据此区分同一个子代理的多条结果
- `await_sub_agent`：等已经派发出去的子会话，按顺序处理——会话被删就直说；还在跑就和 `wait=true` 一样等下一轮（队列里压着的旧轮次不会被当成新结果）；已完成且还有没投递的结果就直接返回那一份，并注明是等待开始前的旧结果；已完成且没有未投递结果就说明「没有可等的了」，不再等也不重复送；失败就立即返回失败说明。拿走的这一份当场取走（不再当通知投递）
- 父会话被停止或生成失败后队列是暂停状态：队列里没有别的消息时，新到的结果会自己恢复队列并开新一轮接着干；队列里还压着用户消息时才继续等，免得通知混进一堆不会执行的消息里
- `wait=true` / `await_sub_agent` 拿到的结果：这一轮的账是「+1 之后当场 -1」，所以不进队列、流式结尾不会再当通知送第二遍；父回合在这之后被打断（工具输出没落库）时，会把正文退回待回传，这一份照常补投
- 回传与否由**每个子代理一条账**决定，不再靠比对正文。账 =「还在排队的结果数 − 欠账数」：
  - 正常跑完一轮（自动跑的、用户手动拉起的都算）→ 排队多一段，+1
  - 这一轮正好被 `await_sub_agent` 当场取走 → +1 之后立刻 -1，不进队列
  - 异常中止（中断、被掐断、待审批、空回复）→ 记一笔欠账，-1，没有结果可回传
  - 回传被父会话收下 → 清掉这一段（-1），这批涉及的子会话欠账一起归零
  - 用户手动把停下来的任务重新拉起 → 不动账，只是按普通一轮正常结尾计

  只有「账 > 0」才回传：刚中止过一次的子代理，下一次正常结尾只排队、不打扰父会话，留到再下一次正常结尾一起送；连续两轮正常结尾而前一轮还没送出去时，两段合并成一条通知。老文件的待投递结果没有欠账这一列，按 0 处理
- `await_sub_agent` 与派发工具共用「派发子代理」开关：清空允许列表后，已经派发出去的子会话就 await 不到了，只能等通知
- 中间层子代理会话没有页面在打开时也会继续跑：它收到孙代理回传的结果就直接开下一轮，否则整条子代理链会断在中间这一层
- 回投的那条消息是系统通知，不是用户发言：为了各家 provider 都能带进请求，它存的角色是 USER，但带 `isSubAgentNotice` 标记，聊天页按「子代理任务完成 · 系统自动回传」的卡片渲染，导出 XML 记为 `system`，组装请求时才补一句「不是你输入的内容」

## 备份覆盖范围（fork 备注）

备份包结构：`settings.json` + 数据库快照 + `upload/`、`images/`、`skills/`、`fonts/`、`media_creation/`。

L3Hub 自己的设置都在 `Settings` 里，随 `settings.json` 整份序列化（`encodeDefaults = true`），包括：
消息队列开关、MAX 拦截、上下文预警与门限、主题/调色板、SSH 工作区配置、助手上的子代理开关与本地工具。

`app_language` 不在 `Settings` 里（`attachBaseContext` 要同步读，只能放 SharedPreferences），
所以备份时单独写进 `settings.json` 的 `appLanguageTag` 字段，恢复时在 `applyPendingRestore` 里回写 SharedPreferences。

`files/workspaces/` 不参与备份：工作区文件可能很大，且更换设备后 proot 路径会变，按需自行导出。

备份与覆盖式恢复只走真实文件：软链一律跳过，不下探也不打包。软链可能指向目录之外（恢复端也写不回原位置），
也可能成环；早期实现遇到前者直接抛错，整份导出作废，而失败提示还错用了「恢复失败」。现在这类条目跳过并记警告日志
（`BackupManager.backupFiles`、`PendingRestore.targetFileOrNull`），导出失败另有「导出失败」文案。

## 自动压缩（设置随助手，行为走上游）

助手 → 记忆 → 「自动压缩」：开关、触发比例（`Assistant.autoCompressRatio`，50%–95%，按 5% 取整）与预警线
（`Assistant.autoCompressThreshold`，WARN / MAX）。用户偏好是每个助手各自一套，所以设置不放在全局偏好里。
触发点取两者先到的那一个：`Model.autoCompressTriggerTokens()` = min(比例 × 上下文窗口, 选中预警线的门限)；
预警线沿用模型里手填或预设的 WARN/MAX，自动填充的值按窗口收窄。时机与压缩范围全部采用上游 2026-11-10 的两个提交
（`0cd90ef12` 按占比自动压缩、`bcc649abc` 一轮中途自动压缩），fork 不再自带预算与截断算法，也不再有「循环压缩」
与「压缩后保留条数」两个选项——上游核心在一轮中途本来就会再压，自动压缩也不留保留区。

| 项 | 说明 |
|---|---|
| 触发时机 | 回复结束后、派发收件箱之前检查一次；一轮中途每批工具执行完再检查一次，达到比例就压缩，之后的步骤写进检查点后面新的一条回复 |
| 判据 | 最近一次请求的 usage（`lastContextTokens()` / `contextTokens()`）达到触发点（比例 × 窗口与 WARN/MAX 门限的先到者）；检查点之前的 usage 不算数 |
| 任务槽 | 压缩与生成共用会话任务槽（`SessionActivity.Compressing`）：期间新消息照常排队、停止生成可取消、重新生成先取消它。不再有 `compressMutex` 与上下文指纹 |
| 压缩范围 | 上一个检查点之后到末尾；手动压缩可另填保留条数，自动压缩不留保留区，整段进摘要 |
| 摘要输入 | 上一个检查点加检查点之后的原文；检查点之前的原文不再重发 |
| 摘要 | 与手动压缩共用 `insertCompressionCheckpoint`，在原位置插入 `isContextCheckpoint` 摘要消息；请求按正常对话组装，前缀与上一轮一致 |
| 状态 | `SessionActivity.Compressing` 让最后一条消息不再显示生成中；前台通知与 Live Update 显示「正在压缩……」 |
| 上下文已满 | 到达 MAX 后确认发送会先弹窗；开了自动压缩则先放行，由压缩处理 |
| 限制 | provider 在流式输出中途才报超限时，这一轮直接失败：不再整轮重跑，也不再重装这一步 |

实现位置：

- `service/ChatService.kt`：`launchAutoCompressIfNeeded`（回复结束后）、`compressMidTurn`（一轮中途）、
  `launchCompressionJob` / `insertCompressionCheckpoint`（任务槽与摘要写入）
- `ai/agent/Agent.kt`、`AgentLoop.kt`：`AgentHooks.shouldPause` 与 `AgentFinishReason.Paused`
- `service/ConversationSession.kt`：`SessionActivity`、`canStartNextTurn`、`autoCompressedReplyId`
- `data/ai/ChatRequestFactory.kt`：`withContinuationHint`，检查点后面不是用户消息时补一句继续任务
- `data/model/AutoCompress.kt`：预警线换算（`autoCompressLimit`）、触发点合并（`autoCompressTriggerTokens`）
  与触发比例的可选范围；token 估算在 `TokenEstimate.kt`，只给用量看板与摘要请求的输出上限用

## 品牌与界面调整（fork）

- 应用图标：上游 mipmap 各密度 PNG（`ic_launcher` / `_background` / `_foreground`）整体反色，原版是深底白兔，现在是浅底黑兔；`ic_launcher_monochrome` 保持原样（主题图标靠 alpha 取形，反色会变透明）
- 关于页：标题改为 L3Hub，注明基于 RikkaHub；链接改为本 fork 仓库与 LICENSE，另保留「上游项目」指向 `rikkahub/rikkahub`
- 模型编辑：删掉「上下文长度」整段，上下文窗口只保留 WARN / MAX 两个预警门限
- 界面偏好：新增「上下文预警进度条」开关（`DisplaySetting.showContextMeter`），关闭后输入框上方的进度条整行隐藏
- 思考卡标题：折叠时用思考内容的第一句当标题（`DisplaySetting.showThinkingSummary`，默认开），没有正文或关掉开关就退回「思考了 N 秒」；截句是 `utils/MarkdownUtils.kt` 里的纯函数 `extractThinkingSummary()`，带单测
- 压缩检查点：分隔线默认折叠时露出两行摘要预览，展开可看完整摘要并编辑/删除
- 任务树：工具调用改为等宽字体代码块，输入按 JSON 格式化、shell 输出拆出 exit code 与 stdout，长内容折叠，可逐条展开

## 侧边栏改版（fork）

- 抽屉开关走 Material 的 `ModalNavigationDrawer` 与 `DrawerState`：边缘手势、拖拽仲裁、遮罩与收放曲线
  全部由 MD3 和当前 `motionScheme` 决定，点击外侧与返回键收起
- 抽屉内容不再订阅流式会话，只拿当前会话 id，生成中不会跟着每个 token 重组
- 抽屉底部改为 ChatGPT 风格：左侧大胶囊「新聊天」，右侧用户头像；头像点击进入设置，并带一段从抽屉飞向页面顶部的过渡动画
- 顶部用户信息（头像/昵称/问候）移除，昵称和头像改到「设置 → 偏好设置 → 通用 → 个人资料」；设置首页顶部改为紧凑标题栏 + 居中大头像，飞行动画正好落在这里
- 会话列表的日期分组可点击折叠/展开；折叠后只隐藏会话，不再吞掉下一个日期标题，也不留空白间隙
- 去掉「图像生成」入口；助手、收藏、统计三个入口改为「AI 翻译」下面的三行

## 后台运行与视图增强（fork）

- 通知设置新增「请求电池优化白名单」：已加入白名单时显示状态，未加入时点击拉起系统授权（没有弹窗的系统退回电池优化列表页）；对应 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限
- 消息视图双层折叠：最后一段正文之前的块整体折叠成「展开前面的 N 步」，展开后思考/工具仍按上游方式折叠；正文前只有一条工具循环链（合并成一个 ChainOfThought 块）时同样折叠，纯思考块仍交给上游折叠；生成中与含待审批工具时不折叠
- 子代理默认后台执行：`dispatch_sub_agent` 立即返回，子代理跑完后结果自动投递回父会话；父会话正在生成时先缓冲，空闲后合并成一条用户消息投递。确实要等结果时可以传 `wait=true`，在工具调用里阻塞等待，不必空转或轮询

## 流式生成不掉帧（fork）

流式预览与 RikkaHub 原版一致：每个 chunk 都落帧，正文随 token 平滑增长，气泡高度逐 token 变化。
曾试验过按正文长度自适应合并的预览节流（`AgentLoop.previewInterval`：16ms 起、每 400 字符加 1ms、
封顶 120ms），能压掉长回复生成期间的掉帧，但文本成批出现，观感退化为「卡顿 + 气泡硬过渡」，已回退。

逐 chunk 的 SSE 原始报文不再写 logd：每秒上百条，实测占掉 logcat 缓冲的三分之一。
五个 provider 的 `onEvent` 改成 `Log.isLoggable(TAG, Log.DEBUG)` 守卫，
排障时 `setprop log.tag.<TAG> DEBUG` 打开。

掉帧按重组范围处理——会话以 `State<Conversation>` 交给消息列表，聊天页只派生自己关心的
字段（有无消息、文件夹、清单、上下文占用等），token 变化只重组列表，输入框、顶栏、
背景和抽屉不跟着走；抽屉和顶栏只拿会话 id / 有没有消息，旧消息的分享回调不再捕获整份会话，
模型对象按节点缓存；输入框的玻璃底改取静息背景源，不再在每个 token 到达时重新模糊聊天列表。

生成中列表贴底跟随是程序滚动，不再给这段位移叠整屏滚动模糊（用户自己往回翻时照常保留）；
弹性伸缩只保留高度动画，不再按高度差挂 RenderEffect；头像的旋转形状只在生成中的那一个头像上跑，
其余头像不挂无限动画；思考预览改为直接贴底，避免每个 token 重启一次滚动动画。

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

## Coding Agent 第一轮：检索工具与只读命令免审批（fork 新增）

- 新增 `workspace_list` / `workspace_glob` / `workspace_grep`。三者都走工作区 backend 的 `listFiles` + `exportAbsoluteFile`，本地 Rootfs 与 SSH 工作区共用同一套实现；遍历会跳过 `.git`、`node_modules`、`build` 等目录，条目数、层数、单文件大小都有上限。
- `workspace_read_file` 增加 `offset` / `limit`（1-based 行号）与返回里的 `totalLines` / `truncated`，大文件可以分段读，不再整文件塞进上下文。
- `workspace_shell` 的只读命令（`ls`、`cat`、`grep`、`rg`、`find`、`git status/diff/log` 等）不再逐条询问。命令替换、重定向、子 shell、后台运行、`find -exec/-delete`、`git` 写操作等仍按原策略要求审批；判定逻辑在 `WorkspaceCommandPolicy`，纯函数，有单测。
- 系统提示里同步说明新工具与免审批范围。

## 玻璃底、选项卡透明度与多色背景（fork）

显示设置 → 「背景特效」选 BLUR / GLASS 后，设置页的开关行、填充色调按钮、选项卡（选项按钮）、
对话页顶栏的图标按钮都带一层毛玻璃底；同一页下面的「玻璃透明度」控制浓度（0%–100%，默认 35%）。

- 全局开关仍然是 `DisplaySetting.enableBlurEffect`：关掉时这些组件**一条 haze 都不挂**，
  也不改透明度，直接回到原来的实色 —— 低端机不该为一点美化付代价
- 没有 haze 源的层级（设置页的背景只有一层纯色）只画半透明底加一道描边：
  纯色背景糊一遍还是同一个颜色，省掉的是白花的 GPU 时间，视觉上没有差别
- 顶栏图标按钮原来读 `blurOpacity`（气泡/输入框用），现在读 `glassOpacity`
- 只给固定层级加效果，**不给列表每一项挂 `hazeSource`**：滚动时每项重糊一次会明显掉帧

改动文件：

| 文件 | 说明 |
|---|---|
| `ui/components/ui/GlassSurface.kt` | 新增：`Modifier.glassSurface`、`glassColor`、`glassSegmentedButtonColors`、`LocalGlassHazeState` |
| `data/datastore/Settings.kt` | `DisplaySetting.glassOpacity`（默认 0.35，跟着 `display_setting` 整份 JSON 走，旧数据缺字段按默认值） |
| `ui/components/ui/CardGroup.kt` | 开关行 / 按钮行 / 表单项的底色改由毛玻璃底铺 |
| `ui/components/ui/ConnectedChoiceButtons.kt` | 选项按钮组底色走玻璃透明度 |
| `ui/components/ui/shadow/ShadowButtons.kt` | 填充色调按钮（`FilledTonalButton`）底色走玻璃透明度 |
| `ui/components/ai/ChatInputHaze.kt` | 加 `alpha` 参数，让顶栏能单独指定浓度 |
| `ui/pages/chat/ChatPage.kt` | 顶栏导航钮 / 模型胶囊 / 动作胶囊改读 `glassOpacity` |
| 五个 `SingleChoiceSegmentedButtonRow` 页面 | 传入 `glassSegmentedButtonColors()` |
| `ui/pages/chat/MeshGradientBackground.kt` | 多色光斑 + 色相慢漂移 |

主色调用 `primary` 的填充按钮保持实色（半透明会掉文字对比度），只改色调按钮和表面类控件。

### 透明度补齐（2026-11）

第一版只把浓度接进了设置页与顶栏按钮：聊天区的气泡、输入框在玻璃档把 tint 写死 0.3，
拖动滑杆对它们完全无效；浮层、选项卡、Chip、开关也各自留在实色或固定透明度上。这一轮统一：

- `chatInputHaze` 的 `alpha` 改成可空：留空时按效果取浓度（模糊 → `blurOpacity`，玻璃 → `glassOpacity`）。
  玻璃档不再写死 0.3，气泡、思考链、输入框、空态卡片、顶栏按钮与胶囊走同一条链路
- 顶栏胶囊原来读 `blurOpacity`，改读 `glassOpacity`；气泡自己的着色层也不再固定 0.65
- 新增 `glassOverlayAlpha(floor)`：浮层与悬浮控件的着色仍由滑杆线性控制，只保留可读性下限
  - 详情面板、横屏右栏：底下没有正文，下限降到 0.5（原来 0.9/0.95，滑杆基本是死的）
  - 抽屉：下限 0.65（压在会话列表上）；两张 BottomSheet：下限 0.88（独立窗口拿不到 haze 源）
  - 消息跳转按钮、建议回复、下拉新建胶囊：下限 0.6–0.7
- 6 处 `SingleChoiceSegmentedButtonRow`、13 处 `FilterChip` / `AssistChip`、MCP 详情页的自定义开关、
  对话页清单卡片的开关补上玻璃配色
- 后台任务面板与会话导出改用带阴影的 `FilledTonalButton`：原来直接用 Material3 原版，没走玻璃底

### 统一特效路径（2026-11-12）

上一轮把浓度接进了各个组件，但每条链路仍各写一套：输入框与气泡在模糊档叠了两层色（haze style 里的
background 加 Surface 底色），设置页卡片只有 style 里那一层（画在模糊内容底下，源不透明时看不见），
顶栏按钮又固定读玻璃透明度。结果是滑杆看起来只对输入框有效，同样开到 45% 各组件浓淡也不一样。

这一轮把「组件特效」收敛成一个 `Modifier.effectSurface`（`ui/components/ui/GlassSurface.kt`）：

- `chatInputHaze` 与 `glassSurface` 都改为薄封装：输入框、气泡、思考链、顶栏胶囊与按钮、空态卡片、
  设置页开关行 / 表单项、抽屉、右栏、详情面板走同一个函数
- 模糊档：`HazeBlurStyle` 的背景色置空，着色统一叠在模糊之后（`hazeBlur(...).background(tint)`），
  每个组件只上一层色；玻璃档：着色交给 `GlassStyle.tint`，与输入框共用同一份光学参数
- 浓度只读当前档位的滑杆（`effectSurfaceAlpha`：模糊 → `blurOpacity`，玻璃 → `glassOpacity`），
  顶栏按钮不再固定读玻璃透明度；「玻璃透明度」滑杆也只在玻璃档显示
- 消息列表在静息时重新注册成 haze 源，输入框、顶栏按钮、抽屉与浮层能糊到滚动中的正文；
  生成中撤掉这个源，保留「流式不逐 token 重糊列表」的优化
- 抽屉、右栏、详情面板与两张 BottomSheet 的着色下限仍保留（压在未模糊的正文上），下限之内照样跟滑杆走

### 动效取用层（2026-11-21）

「动画速度」三档与「关闭动效」原先是两条轨：material3 自带组件认 `MaterialTheme.motionScheme`，
页面里手写的 `tween` / `AnimatedVisibility` / `animate*AsState` 各走各的，滑杆只对一部分生效。
这一轮把三条入口收进 `ui/theme/MotionSpecs.kt`，页面不再自己拼时长。

| 层 | 函数 | 用途 |
|---|---|---|
| 1. spec | `rememberSpatialSpec` / `rememberEffectsSpec` / `rememberFastSpatialSpec` / `rememberSizeSpec` / `rememberOffsetSpec` / `rememberFastOffsetSpec` / `rememberColorSpec` / `rememberSlideSpec` | 只描述快慢与曲线，档位从当前 scheme 读 |
| 2. 固定节奏 | `rememberFixedSpec(duration, easing)`、非组合版 `fixedMotionSpec(enabled, duration, easing)` | 手感定死、不跟三档走的动画（输入框展开、标题滚动、浮动球）保留原时长，只接通「关闭动效」 |
| 3. 状态动画 | `motionFloatAsState` / `motionFastFloatAsState` / `motionDpAsState` / `motionColorAsState`、`Modifier.motionContentSize()` | 替代裸 `animate*AsState` 与 `animateContentSize` 的默认 spring |
| 4. 组合过渡 | `rememberRevealEnter/Exit`（含横向）、`rememberFadeEnter/Exit`、`rememberContentTransition`；`MotionExpandCollapse` / `MotionSlideReveal` / `MotionScrollSwap` | 整块内容的进出场与带模糊的收放 |

无限循环动画（ASR 脉冲、翻译提示、shimmer、头像自转）改读 `motionAnimationsEnabled()`：
关闭动效时连 `rememberInfiniteTransition` 一起不建——只忽略它的值，它仍然每帧申请下一帧。

本轮迁移的调用点（36 个文件）：输入框与浮动条、语音按钮、推理标题、消息气泡与引用、代码块、
选择器头部与模式按钮、翻译卡、Tab 指示、设置页（关于 / 语音 / MCP / 搜索）、会话列表段落收放、
子代理窗口、工作区与媒体生成列表。页面里剩下的裸 `tween` 只在无限循环内部与 `MotionBlur` 自身的
attack 段，前者由 `motionAnimationsEnabled()` 兜住。

> 本轮改动未编译验证（Termux 侧禁止编译）：已做括号与 import 的静态检查，行为验证待下一次出包。

### 曲线与触感（ImageToolbox 移植 · 第一轮，2026-11-21）

从 `~/WorkSpace/ImageToolbox`（T8RIN，Apache-2.0）搬的是手感，不是导航引擎：
那个工程是 Decompose 的 `Children` + `StackNavigation`，本项目是 navigation3 的
`NavDisplay` + hero 容器变换，两套栈语义对不上（详见 `docs/port-imagetoolbox.md`）。
这一轮只动曲线、触感与按压轮廓，三处都不碰导航。

| 层 | 文件 | 说明 |
|---|---|---|
| 曲线 | `ui/theme/MotionEasing.kt`（新增） | 七条贝塞尔：`FancyTransitionEasing`(0.48,0.19,0.05,1.03) / `AlphaEasing` / `PointToPointEasing` / `FastInvokeEasing` / `OverslideEasing` / `RotationEasing` / `SoftEasing`。只管手感，快慢仍由 `MotionSpecs` 的档位给 |
| 页面滑入 | `ui/theme/MotionSpecs.kt` | `rememberSlideSpec` 的三档时长不变（300/180/420），曲线从 `FastOutSlowInEasing` 换成 `FancyTransitionEasing`：整页到位后再"顿"一下才停 |
| 触感 | `ui/haptics/EnhancedHaptics.kt`（新增）、`Theme.kt` | `HapticsStrength` 三档：`SYSTEM` 时**不接管** `LocalHapticFeedback`（等于功能关着，行为与改动前完全一致），`LIGHT` 发 `CONTEXT_CLICK`、`STRONG` 发 `LONG_PRESS`，两者都带 `FLAG_IGNORE_GLOBAL_SETTING`，绕过系统"触摸时震动"；读屏开着时直接不发 |
| 按压轮廓 | `ui/modifier/ShapeInteraction.kt`（新增）、`PressScale.kt` | `rememberShapeByInteraction(shape, pressedShape, interactionSource)` 在按下时逐角插值，`MorphShape` 只对 `Outline.Rounded` 之间做插值，其它形状整块切换；`Modifier.pressScale(restShape=, pressedShape=)` 把轮廓与缩放叠在一起（会顺带 `clip`） |
| 首个调用点 | `ui/pages/setting/components/PresetThemeButton.kt` | 色板缩略图按下时 16dp → 6dp，松手弹回 |

触感这一项新增了设置项：设置 → 偏好设置 → 通用 →「触感强度」，默认「跟随系统」，
老用户升级后手感不变。为了让「轻」和「重」发得出来，`EnhancedHapticFeedback` 只映射
`LongPress` / `TextHandleMove` 两类语义，其余落到 `VIRTUAL_KEY`：Compose 的
`HapticFeedbackType` 常量在各版本间增删过，逐个列举会在升级时编译失败。

> 本轮同样未编译验证（Termux 侧禁止编译）：括号与引用做了静态检查，行为待 CI 出包确认。

### 形状体系（ImageToolbox 移植 · 第二轮，2026-11-21）

截图里那组「形状类型 / 边角大小 / 边角动画节流」原样搬过来了。源工程用带 `strength` 的
sealed class，这里拆成 `CornerStyle` 枚举 + 独立的 `cornerScale`：能直接序列化进设置，
而且「形状」和「大小」本来就是两个设置项。

| 文件 | 说明 |
|---|---|
| `ui/shape/AppShape.kt`（新增） | `styledShape(style, 四角 CornerSize 或单个半径, scale)` 分发七种轮廓；`ScaledCornerSize` 按 `scale * style.scaleRatio` 缩放每个角，强度为 0 时退成直角 |
| `ui/shape/ContiniousRoundedRectangle.kt` | 连续圆角（Smooth），来自 `kyant0:capsule` 的 `Continuity` |
| `ui/shape/WavyShape.kt` / `ScoopShape.kt` / `NotchShape.kt` | 波浪边 / 凹角 / 缺口角，源工程本地 Path 实现，逐行搬，只改 package |
| `data/datastore/Settings.kt` | `cornerStyle`（七种，默认圆角）、`cornerScale`（0–2，默认 1）、`cornerAnimThrottle`（默认 128ms） |
| `ui/theme/Theme.kt` | `MaterialTheme.shapes` 五档逐角换成当前轮廓；`LocalCornerStyle` / `LocalCornerScale` / `LocalCornerThrottle` 下发；Miuix 卡片的 `miuixCornerRadius` 乘上边角大小 |
| `ui/theme/FullWidthComponents.kt` | `adaptiveShape` 返回 `Shape`（不再是 `RoundedCornerShape`）；新增 `adaptiveStyledShape(top, bottom)` 给卡片分组 |
| `ui/components/ui/CardGroup.kt` | 三处形状收口到 `adaptiveStyledShape`：`cardGroupItemShape()`、列表项、表单项 |
| `ui/hooks/HeroAnimation.kt` | 容器变换的起始 / 目标形状改走 `adaptiveShape`，换了切角时展开的第一帧才不会对不上条目 |
| `ui/modifier/ShapeInteraction.kt` | 按压轮廓按 `cornerAnimThrottle` 节流：先立刻生效，窗口内的连点只补最后一下（`Channel.CONFLATED`） |
| 设置页 | 偏好设置 → 通用 →「形状类型」七个 Chip、「边角大小」0–200%、「边角动画节流」0–500ms |

新增两个依赖，版本与源工程一致：`io.github.kyant0:capsule:2.1.3`（连续圆角）、
`com.stoyanvuchev:squircle-shape-android:5.4.0`（超椭圆），均在 Maven Central。

已知边界：

- **Miuix 皮肤只跟「边角大小」**：Miuix 的卡片只有 `cornerRadius` 一个参数，切角 / 波浪边这类轮廓它画不出来。切到 Miuix 时形状类型不生效，半径照样缩放。
- **全宽组件优先**：`fullWidthComponents` 打开时一律直角，形状类型被跳过（顺序与原来一致）。
- **波浪边 / 凹角 / 缺口角在极小的组件上**（Chip、开关）会看不清纹理，按半径比例自适应；小于 4dp 的角基本等同于直角。
- 容器变换期间形状每帧重建，波浪边这类要算 Path 的轮廓在转场动画里会多一次路径计算；转场只有几百毫秒，先按可用处理，真掉帧再给形状做实例缓存。

> 本轮未编译验证（Termux 侧禁止编译）：五个新文件与原文件逐行 diff 过（只差 package 与授权头），
> 引用与括号做过静态检查，行为待 CI 出包确认。

### 开关类型（ImageToolbox 移植 · 第三轮，2026-11-21）

「开关类型」这一档在源工程是八个 `data object`，各自一份自绘实现（`widget/switches/` 2512 行）。
这里逐字搬过来，只替换掉它的胶水层。

| 文件 | 说明 |
|---|---|
| `ui/components/ui/switch/{Compose,Pixel,Fluent,Cupertino,LiquidToggle,HyperOS,OneUI}Switch.kt` | 七份自绘实现，绘制逻辑与源文件逐行对应 |
| `ui/components/ui/switch/CompositeOverSafe.kt` | `Color.compositeOverSafe`，源工程 `core.resources.utils` 的等价物 |
| `ui/components/ui/switch/SwitchByType.kt` | 分发器（源 `EnhancedSwitch` 的 `when`），参数 `switchType / checked / onCheckedChange / colors / interactionSource / colorUnderSwitch` |
| `ui/components/ui/AppSwitch.kt` | 读 `LocalSwitchType`：默认档仍走原来的皮肤开关（原生 M3 / Miuix），其余七档走 `SwitchByType` |
| `data/datastore/Settings.kt` | `switchType`，八档，默认 `MATERIAL_YOU` |
| 设置页 | 偏好设置 → 通用 →「开关类型」八个 Chip |

替换掉的胶水层（每条都是「本项目没有这个东西」）：`LocalSettingsState.switchType` → 参数；
`rememberScrollAnchor` / `scrollAnchor` → 删除；`Modifier.container(...)`（它的阴影描边容器）→ 删除，
阴影仍由本项目的「按钮阴影」控制；`LocalMinimumInteractiveComponentSize.ProvidesValue` →
`CompositionLocalProvider`；`haptics.longPress()` → `performHapticFeedback(LongPress)`；
`ShapeDefaults.circle` → `CircleShape`；`AutoCircleShape()` → `CircleShape`；
`rememberRipple` → 新版 `ripple(bounded, radius)`；`core.ui.theme.blend` → 文件内
`ColorUtils.blendARGB` 副本（保持 sRGB 插值，不用 Oklab 的 `lerp`）。

两处**有意偏离**：

- `M3Switch`（MaterialYou 档）没搬。它的全部绘制本体是 MDC 的 `MaterialSwitch`，
  本仓库没有 `com.google.android.material`，也不为一个开关把 appcompat 拉进来；
  这一档本来就由 `AppSwitch` 直接走皮肤开关，`SwitchByType` 里保留成 Compose 款兜底。
- 液态玻璃档为它加了 `io.github.kyant0:backdrop:2.0.1`（与 capsule 同一个作者）。
  没有这个依赖，`blur` / `lens` / `highlight` / `InnerShadow` 全都不存在，
  用纯色填充替代就不是同一档东西了。

> 本轮未编译验证（Termux 侧禁止编译）：逐文件与原文件 diff 过，差异只有包名、授权头与上表那些替换；
> 违禁符号 grep 为空；括号静态检查通过。行为待 CI 出包确认。

### 滑块类型（ImageToolbox 移植 · 第四轮，2026-11-21）

四档滑块，其中三档依赖源工程自己的一套滑杆引擎（`custom_slider/`），所以是整包搬。

| 文件 | 说明 |
|---|---|
| `ui/components/ui/slider/CustomSlider*.kt`（5 个） | 滑杆引擎：`CustomSlider` / `CustomSliderState` / `CustomSliderColors` / `CustomSliderDefaults` / `CustomSliderRange` / `CustomSliderUtils`。区间滑杆（`CustomRangeSlider*` 约 900 行）没搬，本项目没有区间滑杆 |
| `ui/components/ui/slider/M3Slider.kt` / `M2Slider.kt` / `HyperOSSlider.kt` / `FancySlider.kt` | 四档外观；Fancy 的拇指星形（`MaterialStarShape`）连带路径数据一起搬进文件 |
| `ui/components/ui/slider/SliderByType.kt` | 分发器（源 `EnhancedSlider` 的 `when` 与配色选择），去掉它那份 `LocalSettingsState` 读取与逐步触觉反馈 |
| `ui/components/ui/AppSlider.kt` | 读 `LocalSliderType`：默认档仍是 M3 `Slider`，其余三档走 `SliderByType` |
| `data/datastore/Settings.kt` | `sliderType`，四档，默认 `MATERIAL_YOU` |
| 落点 | `NumberSlider` / `PercentNumberSlider`（设置里几乎所有滑杆都走这两个）、设置页里五处直接 `Slider` 的项 |
| 设置页 | 偏好设置 → 通用 →「滑块类型」四个 Chip |

去掉的胶水层：`Modifier.container(...)`（描边/圆角/阴影容器）与 `materialShadow` 删除，
只留它两侧的 padding；`drawSliderShadows` 这个设置项在本项目不存在。
`M3Slider` 里的 `rememberSliderState` 换成 `remember { SliderState(...) } + SideEffect`，
因为本仓库没有该函数的调用点、无法确认它在 1.5.0-rc01 的可见性（`ReasoningPicker.kt` 用的是构造函数）。

> 本轮未编译验证：逐文件 diff 过，差异只有包名、授权头、删除的容器/阴影与上表那些替换；
> 违禁符号 grep 为空；括号静态检查通过。风险最集中的两处是
> `Slider(state=, enabled=, colors=, …)` 这组参数与 `androidx.compose.ui.util` 的几个浮点打包函数。

### 页面转场三件套与形状动画（ImageToolbox 移植 · 第五轮，2026-11-21）

源工程的页面转场是 `fade + slide + scale` 三条同时跑（`Animations.kt` 的
`toolboxPredictiveBackAnimation` 兜底：fade 300 / slide 400 / scale 500，各一条曲线），
组件形状切换靠 `ShapeDefaults.AnimatedShape`（四角各一条 `Animatable`）。这两样都搬了。

| 位置 | 改了什么 |
|---|---|
| `ui/navigation/NavTransitions.kt` | 滑入页（会话、设置首页）的进场与返回从「只有位移」变成 `fadeIn(AlphaEasing) + slideInVertically(FancyTransitionEasing) + scaleIn(PointToPointEasing, 0.9 → 1)`，返回是同一组的三条逆动画；容器变换页不动——那边位移由 `sharedBounds` 演，叠加缩放会变成两套位移 |
| `ui/theme/MotionSpecs.kt` | `rememberSlideSpec` 增加 `easing` 参数（源工程三条动画各一条曲线），新增 `slideDurationMillis()`；三条动画共用同一档时长（SYSTEM 300 / FAST 180 / GENTLE 420），同时开始同时结束 |
| `ui/theme/MotionEasing.kt` | `AlphaEasing`、`PointToPointEasing` 就是这一轮接进去的 |
| `ui/shape/AnimatedShape.kt`（新增） | `AnimatedShape` / `rememberAnimatedShape` / `animateShape`：四角各一条 `Animatable`（弹簧阻尼 0.4 / `StiffnessLow`，与源工程一致），目标值走 `Channel.CONFLATED`，连续重组只取最后一个；半径按 px 存，`CornerSize` 是百分比也能插值；过冲由 `fastCoerceIn(0, halfHeight)` 兜住 |
| `ui/components/ui/CardGroup.kt` | 列表项与表单项的圆角动画从「上下两条 `animateDpAsState`」换成 `rememberAnimatedShape`：插入 / 删除条目、按下首尾项变圆、换轮廓类型时四个角各自收放 |

一处订正：P1 那次把 `FancyTransitionEasing` 换进了 `rememberSlideSpec`，但当时滑入页读的是
`rememberSpatialSpec`（MotionScheme 的空间弹簧），所以那条曲线实际只作用在容器变换的淡入窗口上。
这一轮把滑入页改成读 `rememberSlideSpec`，尾段过冲的手感才真正落到页面位移上。

边界：

- 预测性返回仍是「不跟手」：`predictivePop` 直接委托 `pop`，返回手势松手后才播整段三步动画。
  跟手预览要走 Decompose 那套 `predictiveBackAnimation`（换引擎之后才有）。
- 形状动画每帧按当前轮廓重建一次形状：波浪边这类要算 Path 的轮廓在动画期间会多一次路径计算。
- `AnimatedShape` 以 `(轮廓类型, 边角大小, 密度, 动画 spec)` 为 key：用户在中途换轮廓类型时直接以新形状重置，不播跨类型的插值（源工程同样如此）。

> 本轮未编译验证：括号与引用静态检查通过，行为待 CI 出包确认。
> 最可能出问题的是 `scaleIn/scaleOut` 与 `fadeIn/slideInVertically` 用 `+` 组合后的 `EnterTransition` 类型推断。

### 多色背景

`MeshGradientBackground` 从原来的蓝色系改成四个光斑（蓝 / 青 / 紫品红 / 橙琥珀），
色相另走一圈 31 秒的慢时钟来回摆动 ±10~18°，饱和度与明度写死，
所以换色不换亮度，深浅主题都不会忽明忽暗：

| 主题 | 底色 | 光斑明度 / 饱和度 | 光斑 alpha |
|---|---|---|---|
| 深色 | 靛紫 → 近黑 `#191428`→`#07060A` | 0.58–0.66 / 0.52–0.62 | 0.22–0.44 |
| 浅色 | 淡藕荷 → 纯白 `#EAE4F8`→`#FFFFFF` | 1.0 / 0.40–0.60 | 0.34–0.55 |

- 深色压明度加低 alpha，叠在近黑底上不会糊成一片；暖色光斑 alpha 只给 0.22，负责色彩参照不抢戏
- 浅色明度给满但饱和度封顶 0.6，是淡彩不是荧光
- 光斑渐隐从「透明黑」改成「同色 alpha = 0」，边缘不再发灰
- 跟帧时钟推进，不设帧率上限；切后台时停掉，色相漂移和位置漂移共用同一份相位状态，没有新增定时器

## 调色板（fork 新增）

设置 → 偏好设置 → 主题 → 「调色板」：内置预设 + 逐个角色自定义，浅色/深色各存一份，管住全应用的配色。

- 优先级：`dynamicColor`（跟随系统动态取色）> 调色板 > `themeId`（预设主题 / 自定义主题）。
  动态取色开着时调色板整块不生效，页面顶部同一处就能关掉它；默认值仍是上游的「开」，不动老用户
- 四十八个角色全部可改，按五组呈现：主色 / 辅色与第三色 / 表面与背景 / 错误与线条 / 高级。
  「高级」那组（fixed 系列、scrim、surfaceTint）在本项目里引用点都在 1 上下，默认折叠。
  每个字段都可空，**null 表示「没动过、跟着种子派生」**——旧版本存的九色配置照样能读，
  新加的三十九个角色在没被指定时也不会打断原有派生
- 生成方式：`paletteColorScheme()` 用四个彩色种子加中性种子（surface / surfaceVariant）构造 `DynamicScheme`，
  `toColorScheme()` 之后先把表面族交给 `withSurfaceFamily()` 重算，再按用户指定逐个 `copy` 回去
  （高级区直接调容器色时优先）；`onSurfaceVariant` 没被指定时由 `onSurface` 按音调关系派生一档，
  否则改「表面上的文字」只有标题会动
- 表面族由「表层色」接管：`withSurfaceFamily()` 取 surface 的 HCT 色相和彩度（彩度上限 12，
  保持 M3 中性色的观感），并在 scheme 自己算出的音调上**按表面色的深浅整体平移**——
  基准是 M3 的默认表面（浅色 tone 98、深色 tone 6），容器阶梯 94/96/98/100 相对它定。
  只换色相不平移的话，把表面色设成 `#000000`，容器仍是 tone 94 的近白（黑色彩度为 0，
  连色相都没有），而界面上绝大多数容器用的就是 `surfaceContainer*`，看上去就是「改了没反应」。
  `inverseSurface` / `inverseOnSurface`（Snackbar 的反色底）反向平移，避免黑底黑字。
  早先只覆盖 `surface` 一个字段，而应用里 118 处用的是 `surfaceContainer*`，
  它们由中性种子展开、音调钉死，所以浅色下改「表层色」几乎看不出变化。
  「背景色」从此只管自己（聊天页底、Markdown 底），不再偷偷带动整族容器色
- 取色器：`ColorPickerRow`（HSV/RGB 双模式 + 色卡 + 色号输入 + 可选 A 通道滑杆），
  已从 `SettingThemePage.kt` 提到 `ui/components/ui/ColorPickerRow.kt`，两处共用，没有引入新依赖。
  色号输入边输边生效，输入框不会中途被改写（之前每敲一个字符就被 `remember(argb)` 重置，
  粘贴 `#466E7A` 会被立刻改写成 `rgb(70 110 122)`）；失焦只负责把文本规范成标准写法。
  中途会闪一下中间态（`#466` → `#446666`），但保存时拿到的永远是当前输入——
  「保存」按钮不抢焦点，把提交压在失焦上会丢输入
- 正文跟随：`onSurfaceVariant`（全仓 216 处，正文几乎都用它）由 `onSurface` 按 M3 的音调关系
  错开一档推出来（浅色 +20、深色 −10）。不这么做的话，改「表面上的文字颜色」只在标题上看得见，
  正文一动不动，观感就是"没生效"
- 当前生效：调色板页放了一块读 `MaterialTheme.colorScheme` 的色带（背景色外圈、表面色卡片、
  表面变体色方块、两种文字色、主色圆点）。它不读编辑面板里的临时值，保存后不变就是真没进主题
- 色号：`#RRGGBB` / `#AARRGGBB` / `#RGB` 三种写法，解析和格式化在 `ui/theme/ColorHex.kt`，
  纯计算，JVM 单测直接跑。不带 alpha 按不透明处理
- 从色号生成：调色板页顶部填一个色号，`PaletteColors.fromSeed()` 当场展开成深浅两套色卡，
  可以只应用到其中一侧
- 导出 / 导入：`data/export/PaletteSerializer.kt`，外面套项目既有的 `ExportData`（version + type + data），
  导入时也吃裸的 `PaletteSetting` 对象，方便从剪贴板粘一段手写的
- 存盘：`Settings.palette`（`PaletteSetting`），DataStore key `palette`。三个字段都可空：
  `presetId == null` = 不启用，行为与改动前完全一致；`light` / `dark` 为 null 表示该侧用预设种子生成，
  非 null 表示该侧被逐个改过，所以可以只调其中一套

| 预设 | id | 主色种子 | 辅色 | 第三色 | 错误色 |
|---|---|---|---|---|---|
| 默认紫 | `default` | `#6750A4` | `#625B71` | `#7D5260` | `#B3261E` |
| 海洋蓝 | `ocean` | `#116682` | `#4D616C` | `#5D5B7D` | `#BA1A1A` |
| 森林绿 | `forest` | `#4C662B` | `#586249` | `#386663` | `#BA1A1A` |
| 落日橙 | `sunset` | `#8B5000` | `#755843` | `#8C4A60` | `#BA1A1A` |
| 玫瑰 | `rose` | `#8E4955` | `#76565A` | `#785831` | `#BA1A1A` |
| 单色灰 | `monochrome` | `#5C5C5C` | `#5C5C5C` | `#5C5C5C` | `#BA1A1A` |

单色灰单独给中性种子 `#5C5C5C`（其余预设的中性色跟随主色）；错误色保留红，
否则错误提示和普通正文分不开。

改动文件：

| 文件 | 说明 |
|---|---|
| `ui/theme/Palette.kt` | 新增：`PaletteColors` / `PaletteSetting` / `PaletteRole` / `paletteColorScheme()` |
| `ui/theme/PalettePresets.kt` | 新增：六套预设的种子色与展开逻辑 |
| `ui/theme/Theme.kt` | 动态取色之后、`themeId` 之前插入调色板配色 |
| `ui/components/ui/ColorPickerRow.kt` | 新增：从 `SettingThemePage` 抽出的取色行，含色卡与 A 通道 |
| `ui/theme/ColorHex.kt` | 新增：色号解析 / 格式化（纯函数） |
| `data/export/PaletteSerializer.kt` | 新增：调色板导出与导入 |
| `ui/pages/setting/SettingPalettePage.kt` | 新增：预设列表（色块 + 名称）、自定义编辑、恢复默认 |
| `ui/pages/setting/SettingPreferencesThemePage.kt` | 主题分组下加一行「调色板」 |
| `RouteActivity.kt` | `Screen.SettingPalette` 与对应的 `entry` |
| `data/datastore/Settings.kt`、`SettingsFields.kt`、`PreferencesStore.kt` | `palette` 字段、字段表、DataStore key |
| `SettingsFieldsTest` | 样本与 `STORED_KEYS` 补 `palette` |

> `Settings` 加了顶层字段，按本文约定必须同步 `SETTINGS_FIELDS` 和 `STORED_KEYS`，
> 否则 `every settings property has exactly one field` 与 `stored format stays compatible with existing data` 两个用例会挂。

## 全宽组件（fork 新增）

设置 → 偏好设置 → 通用 → 背景特效一栏里的「全宽组件」开关，就在「玻璃透明度」滑杆下面
（`DisplaySetting.fullWidthComponents`，默认关）。
关着的时候每个分支都退回改造前的取值，行为与改动前完全一致。

打开后：

- 气泡不再按内容收缩，左右边缘与消息列表等宽；消息列表的 16dp 左右留白归零
- `MaterialTheme.shapes` 换成 `SquareShapes`（五个档位都是 `RoundedCornerShape(0.dp)`），
  卡片、按钮、对话框、菜单一起变直角；气泡自己的 `uiCornerRadius`、接缝小圆角、输入框内外两层圆角
  都走 `adaptiveShape()`，一起归零
- 贴边的组件（输入框及其外层容器、顶栏两侧按钮组、搜索框）去掉左右留白；让位改用
  `WindowInsets.safeDrawing.only(Horizontal)`，所以刘海、横屏侧边系统栏照样让得开，
  `imePadding()` / `navigationBarsPadding()` 的顺序也没有变
- 设置页的页面留白同样归零（`Modifier.adaptiveHorizontalPadding`），`CardGroup` 分组卡片撑满屏宽

统一入口在 `ui/theme/FullWidthComponents.kt`：`LocalFullWidthComponents` 加
`adaptiveShape()` / `adaptiveCornerRadius()` / `adaptiveHorizontalPadding()` /
`adaptiveHorizontalPaddingValue()` / `fullWidthHorizontalInset()`。主题在 `RikkahubTheme` 里提供这个
CompositionLocal，页面只读它，不各自去判断 `Settings`。

| 文件 | 说明 |
|---|---|
| `ui/theme/FullWidthComponents.kt` | 新增：CompositionLocal、`SquareShapes` 与取值辅助 |
| `ui/theme/Theme.kt` | 提供 CompositionLocal；`shapes` 多一档 `SquareShapes` |
| `data/datastore/Settings.kt` | `DisplaySetting.fullWidthComponents`（默认 false） |
| `ui/pages/chat/ChatList.kt` | 消息列表左右留白归零（保留安全区），搜索框与建议行同步 |
| `ui/components/message/ChatMessage.kt` | 气泡撑满父宽、圆角归零、用户气泡左侧 24dp 归零 |
| `ui/components/ui/ChainOfThought.kt` | 思考/工具卡片撑满父宽、圆角归零 |
| `ui/components/ai/ChatInput.kt` | 输入框贴屏幕边、容器与 TextField 直角、发送/加号按钮直角 |
| `ui/pages/chat/ChatPage.kt` | 顶栏左右留白归零，顶栏胶囊与导航钮直角 |
| `ui/components/ui/CardGroup.kt` | 分组卡片圆角归零（`cardGroupItemShape` 随之变成 `@Composable`） |
| `ui/components/message/ConversationTodoCard.kt`、`ui/pages/chat/ChatDrawer.kt` | 任务卡与抽屉容器圆角归零 |
| 五个 `SettingPreferences*Page.kt`、`SettingProviderPage.kt` | 页面左右留白归零 |
| `res/values{,-zh}/strings.xml` | 开关的标题与说明 |

> 这个开关在 `DisplaySetting` 里，跟着 `display_setting` 整份 JSON 走，
> 所以**不用**改 `SETTINGS_FIELDS` / `PreferencesStore` 的 key —— 只有加在 `Settings` 顶层的字段才要。

默认关闭，打开后仍然保留的内边距（有意为之）：气泡内部文字 10dp、输入框内部的 `inputPadding`、
`CardGroup` 每项内部的 16dp、对话框按钮区。这些是内容离组件边缘的距离，不是组件边缘离屏幕的距离。

## 页面转场与容器变换（fork）

页面级转场统一由 `ui/navigation/NavTransitions.kt` 提供，中列与设置右列使用同一份规格：

- 页面按 `Screen.usesContainerTransform()` 分两类，各自的进出互为逆。被盖住的页面统一用
  `ExitTransition.None`，原地不动：
  - 容器变换页（走 `heroEntry` 的全部页面与 `Screen.MediaCreation`）：从卡片 / 选项卡展开成整页，
    返回收回那一块，进出都不位移，位移全交给页面自己的 sharedBounds；display 级只给一段与
    「动画速度」同档的淡入 / 淡出窗口，用来占住被盖住或离场那一页，供配对与收回播放。
  - 滑入页（会话、设置首页）：从下方滑入（`Screen.Chat.enterFromTop` 时改为从上方），
    返回**原路滑出**，即进场动画的逆。
- 普通返回与预测性返回共用同一个 `NavTransitionSpecs.pop`，按离场页（`initialState` 那一侧的 scene key）
  选上述两条中的一条。
- `usesContainerTransform()` 必须与 `RouteActivity.kt` 的入口清单一致：新增普通 `entry` 页面
  （不套 `heroEntry`）时要在那个 `when` 里列出来，否则它会被当成容器变换页、进场只剩一段淡入。
- `Screen.MediaCreation` 保留 entry 级 `None` 转场，容器变换只由页面内部的 sharedBounds 演出，避免双层位移。
- 路由身份在同包的 `Screen.kt`，入口清单是 `RouteActivity.kt` 顶层的 `appNavEntries()`；两个 `NavDisplay`
  共用 remembered entry provider，但各自持有 SaveableState / ViewModelStore / 输入守卫装饰器实例。

`heroEntry<T>` 负责可配对页面的容器变换：进场展开、返回收回，两个方向都配对共享元素，是同一段
容器变换的正反两面；`heroPageContainer` 因此在离场时也保留，不再按 `transition.targetState` 摘除。
容器变换页的 display 级进场与退场因此都不位移（只有淡入淡出窗口），不会与容器变换叠成两套位移。
两侧内容在变形 bounds 里的交叉淡化由 `heroContainer` 的 enter/exit 显式编排：展开时页面内容快淡入、
卡片内容快让位，返回时两侧都拖到末段（70%）才交叉——默认的全程交叉淡化会让两份内容同时半透明、
叠在原地不动的被盖页上成残影。时长跟页转场档位走，关闭动效时一起 snap。
页内元素（`heroAnimation`）仍只在进场配对，返回时通过 `LocalNavEntryPopped` 摘掉，
避免「整页收回条目 + 元素飞回」两种过渡同时出现。页面自身不要再包一层同 key 的
`HeroPage`；`Screen.Setting` 与 `Screen.MediaCreation` 的既有例外保持不变。

转场期间离场页仍会留在 `AnimatedContent` 里，导航层不干预它的指针输入，与上游一致。离场页继续吃点击是
navigation3 1.2 的既有行为，需要时由页面自行处理，不要另建一层全局指针守卫。

返回由 `ui/context/NavContext.kt` 直接出栈：与上游同口径，`popBackStack()` 只做「栈深大于 1 就弹一层」，
不做去重窗口、不排队等待转场落定、不区分预测性 seek。快速连按返回与转场中途返回都按栈的真实状态执行。

`ui/navigation/NavTransitions.kt` 里只剩一处装饰器：`NavEntryPoppedDecorator` 给「离场且已不在返回栈」的
entry 提供 `LocalNavEntryPopped`，供 `heroAnimation` 摘掉页内共享元素。它只读栈内容，不写导航状态。

动画曲线继续取 `ui/theme/MotionSpecs.kt` 与当前 MD3 motionScheme。不要为「统一」把页面弹簧改成另一套
 tween；关闭动效时仍由主题统一切到 snap。带模糊的滚动交换使用 `rememberMotionBlurSpec()`，跟随偏好设置里的
过渡时长。

`CardGroup` 的条件项必须始终注册，通过 `item(visible = ...)` / `formItem(visible = ...)` /
`switchItem(visible = ...)` 或 `optionGroup(visible = ...)` 收放。不得在外层写 `if`，否则条目在收集阶段
就被删掉，无法进入 `MotionExpandCollapse`，相邻项的圆角与高度也会落在两条时间线上。

`CardGroup` 的 `content` 参数不能标 `@Composable`：标了之后它成为独立重组作用域，外部状态变化只重建
items，下面的 Column 不一定跟着重组。调用点里的 `item(...)` / `formItem(...)` 本身不是 composable 调用，
composable 内容作为参数传入即可。

| 文件 | 职责 |
|---|---|
| `Screen.kt` | 路由身份与序列化声明 |
| `RouteActivity.kt` | 入口清单、三列装配 |
| `ui/navigation/NavTransitions.kt` | 两列共用的进场、返回、预测性返回规格与离场标记装饰器 |
| `ui/hooks/HeroAnimation.kt` | 进场共享元素与返回摘除 |
| `ui/context/NavContext.kt` | 栈操作与只读栈视图 |
| `ui/components/ui/CardGroup.kt` | 条件项注册、统一收放与原生/Miuix 条目布局 |

## 模型编辑页（fork 新增）

模型的新增/编辑原来是 `SettingProviderDetailPage` 里的一个 `ModalBottomSheet`，现在抽成独立页面
`Screen.ModelEditor(providerId, modelId)`（`modelId` 为 null 是新建），从模型卡片直接展开成整页：

- 卡片长按拖拽排序的 modifier 之外再挂 `heroSource(Screen.ModelEditor(...))`，点开时卡片自己展开成页
- 模型选择弹窗里长按模型也进这一页（原来带上 `focusModelId` 回到供应商详情再自动弹面板）
- 编辑对象是页面内的 `draft`，点右上角确认才写回；新建时 `modelId` 和 `displayName` 都非空才可确认，
  否则会写进一条点不动的空条目
- 走 `heroEntry` 注册，页面本身不再包 `HeroPage`

侧带的几处调整：

- 模型选择弹窗关掉了 `sheetGesturesEnabled`、去掉把手：列表本身要上下滑，下拉手势很容易误触关闭；
  关闭只留「点外部」和右上角 X 两种
- 用量弹窗的 sheet 只留 `Hidden` / `Expanded`，否则 `show()` 会先停在半屏
- 输入框附件面板里可收起的项（工作区、MCP、压缩）改用 `AnimatedVisibility`，收起的项不占位，
  后面的条目连同圆角一起挪过去

## 模型选择弹窗（fork 改版）

所有选模型的入口（聊天输入框、默认模型设置、助手、心跳、记忆整理、图像生成、翻译、连通性测试）共用
`ui/components/ai/ModelList.kt` 的 `ModelListSheet`，弹窗内容换成 `ModelPickerTwoPane` 的双列布局：

- 左栏 88dp：收藏入口 → 分组（各自可折叠，带 ▾）→ 组内供应商 Logo → 「未分组」段。
  长按分组名调整顺序，长按 Logo 进该供应商详情，底部齿轮直接进供应商设置页
- 左上角箭头把整条左栏收成 0 宽，右侧主视图拿满弹窗宽度；收起后同一个箭头移到标题左边
- 标题行右侧是关闭按钮，搜索框占满主视图一整行，带清空按钮。搜索同时匹配**供应商名**、
  模型的 `displayName` 和 `modelId`；命中供应商时连同该供应商的全部模型一起列出，结果卡片会带上供应商名
- 卡片：左侧图标方块，右侧模型名、`WARN x · MAX y`、标签行（供应商覆盖 / 类型 / 模态 / 能力）
- 屏幕宽度 < 360dp 时自动换成紧凑卡片：图标缩小，标签独占一行，名称不再被收藏按钮挤没

> 上游的单列实现（供应商 sticky header + 收藏拖动排序 + 供应商 Badge 横向滚动）在改成双列后
> 就没人调用了，这次随改版一起删掉，`ModelList.kt` 从 1016 行降到 639 行。

| 文件 | 说明 |
|---|---|
| `ui/components/ai/ModelPickerTwoPane.kt` | 重写：可收起的左栏、顶部搜索与关闭、双列布局、窄屏断点、分组排序对话框 |
| `ui/components/ai/ModelList.kt` | `ModelItem` 改成「图标方块 + 名称 / 水位 / 标签」，新增 `compact`、`subtitle` 参数与 `ModelProviderOverrideTag`；删除旧单列实现 |
| `res/values*/strings.xml` | 新增 `model_list_collapse_sidebar`、`model_list_expand_sidebar`、`model_list_no_match` |

## 内置 SSH 服务端（fork 新增）

设置 → Web 服务器 → 「在终端中使用」：打开后可以在系统终端里 `ssh -p 2222 l3hub@127.0.0.1` 直接操作 App。
和 Web 服务器是两个独立开关，但共用同一个前台服务 `WebServerService` 和同一条通知。

- **依赖**：`org.apache.sshd:sshd-core`（版本走 `gradle/libs.versions.toml` 的 `apache-sshd`，该版本此前已被 `:workspace` 的测试用到）。
  `sshd-common` 由它传递引入；SLF4J 输出走已有的 `slf4j-android`，不需要再加 `slf4j-nop`。
- **认证**：只开密码认证，密码就是 Web 服务器的访问密码（`webServerAccessPassword`），**不维护第二份令牌**；
  用户名随便填（示例里写 `l3hub`）。
- **绑定**：默认 `127.0.0.1`，打开「允许局域网访问」才绑 `0.0.0.0`。端口默认 `2222`，和 `8080` 分开。
- **主机密钥**：`filesDir/ssh/hostkey.ser`，第一次启动由 sshd 生成，之后一直复用（RSA，避免 Android 上没有 EdDSA）。
- **通道**：不申请 PTY，按行读命令；shell 请求（`ssh host`）进交互循环，exec 请求（`ssh host conversations`）跑一条就退出。
- **令牌为空时拒绝启动**，通知会被收掉，界面上显示错误。

命令（都直接调 `ChatService` / `ConversationRepository` / `SettingsStore`，不绕 HTTP）：

| 命令 | 作用 |
|---|---|
| `help` | 命令列表 |
| `conversations` | 当前助手的会话列表（id / 更新时间 / 标题） |
| `models` | 已启用提供商的对话模型，`*` 是当前模型 |
| `send <id> <text>` | 发消息，生成在后台继续，用 `tail` 跟 |
| `tail <id>` | 跟会话输出，生成时增量打印，Ctrl+C / 断开结束 |
| `whoami` | 当前账号与助手 |
| `exit` | 关闭会话 |

改动文件：

```
gradle/libs.versions.toml                       无需改动，apache-sshd 已有
app/build.gradle.kts                            implementation(libs.apache.sshd.core)
app/.../ssh/SshCommandParser.kt                 纯函数解析（命令 / 参数缺失 / 未知命令）
app/.../ssh/SshCommandSession.kt                命令实现
app/.../ssh/SshShellCommand.kt                  sshd 的 Command / ShellFactory 实现，按行读写
app/.../ssh/SshServerManager.kt                 Koin 单例：start / stop / isRunning / port / error
app/.../service/WebServerService.kt             两个服务共用前台服务与通知，新增 SSH 相关 action
app/.../di/AppModule.kt                         注册 SshServerManager
app/.../data/datastore/Settings*.kt             三个新字段（默认 false / 2222 / false）
app/.../ui/pages/setting/SettingWebPage.kt      「在终端中使用」卡片
app/.../RikkaHubApp.kt                          只恢复用户自己开过的服务，不会无条件启动
app/src/test/.../ssh/SshCommandParserTest.kt    解析器单测（不依赖 Android）
```

体积：预计 release APK **+1.5–2.5 MB**，debug **+3 MB 左右**。依据是 sshd-core 2.20 与
sshd-common 的 jar 合计约 2.7 MB，R8 会裁掉一部分用不到的算法/解析器但不会全裁；
下次出包时用 `assembleGithubRelease` 前后对比确认。

## 文件夹绑定工作区（fork 新增）

会话文件夹原来是助手内的分组。这一版让文件夹再绑一组工作区：落在这个文件夹里的会话都用这组工作区，
于是「按对话分组的文件夹」就等于「按工作区把会话分了组」。适合一个项目一堆聊天共用一个工作区的场景。

设计取舍是**运行时解析，不写快照**：

- 会话 `config` 里固定的工作区保持原样。读取时 `FolderRepository.workspaceBindingOf(conversation)`
  现取文件夹的绑定，盖在 `Settings.getAssistantOf(conversation)` 上（`Assistant.withWorkspaceBinding`）。
- 好处是换绑立即对全组生效，会话移出文件夹就自动回到自己的绑定；坏处是每次取助手多一次轻查询，
  而且聊天页那份助手带着「不是自己的」工作区——所以写回要还原（见下）。
- 子代理会话自己没有分组，解析时沿 `parentConversationId` 往上找父会话的文件夹（最多 3 跳），
  否则派出去的活会落在另一个工作区里。

生效点：

| 位置 | 作用 |
|---|---|
| `ChatRequestFactory.createContext` | 工具注册、`WorkspaceReminderTransformer`、每个工作区的 CWD 都吃这份 assistant |
| `ChatVM.assistant` | 聊天页的工作区入口、CWD 列表、补全提供者 |
| `ChatService.updateChatAssistant` | 写回会话/助手前把工作区字段还原成会话自己的，避免文件夹的绑定被固化成会话快照 |

界面：抽屉里文件夹芯片长按 → 「绑定工作区」，多选，芯片上多一个小盒子图标表示这组绑了工作区；
聊天页的工作区入口在文件夹接管时只显示「由文件夹「X」提供」，不提供改写（改了也会被文件夹盖掉）。

数据：`conversation_folder` 加两列 `workspace_id`（主工作区）/ `workspace_ids`（逗号分隔），
Room 版本 35 → 36，走 `AutoMigration(35, 36)`。工作区被删除时 `WorkspaceRepository.delete` 会顺手
调 `FolderRepository.detachWorkspace`，把文件夹上的引用摘掉。

Web API：`FolderDto` 多了 `workspaceId` / `workspaceIds`，新增 `POST /api/folders/{id}/workspaces`
（body `{"workspaceIds": [...], "workspaceId": "..."}`，留空即解绑）。web-ui 的文件夹菜单已做多选、
主工作区星标与解绑，见下文「WebUI 原生功能」。

改动文件：

```
app/.../data/model/Folder.kt                    文件夹模型加 workspaceId / workspaceIds
app/.../data/model/WorkspaceBinding.kt          新增：一组工作区 + Assistant.withWorkspaceBinding
app/.../data/db/entity/FolderEntity.kt          两列
app/.../data/db/dao/FolderDAO.kt                observeFolder / getAll / updateWorkspaces
app/.../data/db/dao/ConversationDAO.kt          getFolderRef（只取 folder_id + parent_conversation_id）
app/.../data/db/AppDatabase.kt                  version 36 + AutoMigration(35, 36)
app/.../data/repository/FolderRepository.kt     绑定读写、会话解析（含父链）、工作区删除后摘引用
app/.../data/repository/WorkspaceRepository.kt  删除工作区时摘掉文件夹引用
app/.../data/ai/ChatRequestFactory.kt           本轮 assistant 盖上文件夹的绑定
app/.../service/ChatService.kt                  写回前还原会话自己的绑定
app/.../ui/components/ai/WorkspaceSelectSheet.kt 入参从 assistant 改成 boundIds，可带一行说明
app/.../ui/components/ai/FilesPicker.kt         文件夹接管时工作区入口只读
app/.../ui/pages/chat/ChatDrawer.kt             「绑定工作区」菜单项、芯片标记、选择表
app/.../ui/pages/chat/ChatDrawerVM.kt           工作区列表、updateFolderWorkspaces
app/.../ui/pages/chat/ChatVM.kt                 助手流合并文件夹绑定
app/.../ui/pages/chat/ChatPage.kt               把文件夹名传给工作区入口
app/.../web/dto/WebDto.kt / routes/FolderRoutes.kt   DTO 字段与绑定接口
```

未验证：这次没有跑 Gradle（Termux 上不编译）。`app/schemas/.../36.json` 要等第一次构建由 Room 生成，
生成后需要一起提交，否则下一次加表/加列时自动迁移会缺上一版 schema。

## 引导提示词（fork 新增）

消息队列管的是「下一轮说什么」，引导管的是「这一轮跑到一半改方向」：生成过程中追加一句指令，
插进正在跑的工具间隙，模型下一步就读到它，不用掐断再重发。

实现上没有新增协议，也不动 agent 循环：引导被包成一条**合成的工具调用**（tool_use + tool_result）
写进当前助手消息，随消息一起落库、导出；组装请求时再展开成一条 user 消息，模型在历史里看不到这个工具。

```kotlin
UIMessagePart.Tool(
    toolCallId = "g_<16 位十六进制>",   // 不带连字符：OpenAI 的 tool_call_id 上限 40 字符，整套 uuid 会到 45
    toolName = "guidance",             // 不注册进工具表，模型调不到；声明了反而会诱导它去调用
    input = """{"step":2}""",          // 插在第几步，引导间隔按它算
    output = listOf(UIMessagePart.Text(引导正文)),
)
```

- 工具名 `guidance` / `todo_reminder` 都不在工具表里。各家 provider 转换时只按「已执行的 Tool part」成组序列化，
  不校验名字是否声明过，所以不需要为它们常驻一个工具。
- 合成调用只在落库和界面上存在。组装请求时 `expandInjectedTools` 把它拆成一条 **user 消息**（和通知的
  `expandNotices` 同一层，位置仍在工具结果之后）；模型在历史里看不到 `tool_use`，否则它会以为自己有这个工具，
  照着再调一次，拿回一个空结果。
- 「插入任务清单」按每一步的最新设置判断，不看这一轮开始时的快照：中途关掉，下一个工具间隙就停，
  请求尾部和历史里已经插过的提醒也一起不再发（`includeTodoReminders = false`），关了开关不会还留着旧清单。
- 每次插入都用新的 `toolCallId`：同一条助手消息里出现两个相同 id 的 tool_use，Anthropic 会整条拒掉。

### 什么时候插

注入点是 `ChatService.takeInjectionParts(current)`，挂在 `AgentHooks.pendingParts` 上
（钩子这一版多带了一个参数：本轮正在写的助手消息，第一步还没有内容时为 null）。顺序固定为
**通知 → 任务清单 → 引导**——模型最后读到的是用户新给的指令。

- 本轮第一步（`current == null`）只放通知，不插引导和清单：否则回答开头会凭空出现一条工具调用，
  而引导本意是「跑到一半」才出现。
- 末尾已经是合成调用时整批跳过：上下文超限后这一步会重装，`appendPendingParts` 会被再调一次，
  而中间没有跑过新的工具批次，这一批已经在消息里了。
- 清单只在**文本和上一次注入的不一样**时插；没变就交给请求尾部的 `TodoStateTransformer`（它每一步都会贴），
  `withTodoState` 看到末尾已有清单提醒时也不再另附一份——否则同一个请求里会出现两份不一样的清单。

### 单轮与多轮

| 模式 | 语义 | 来源 |
|---|---|---|
| 单轮 `SINGLE` | 插一次就从收件箱删掉 | 手输的一句提醒 |
| 多轮 `REPEAT` | 每一批工具之后都再插一次，直到取消 | 预设（长期约束） |

多轮会在一轮长对话里反复出现同一段话，这是「持续生效」的代价，卡片上点一下就能切成单轮、点叉取消。

待发出的引导放在会话收件箱（`ConversationInbox.guidances`）里，不落库：它和消息队列一样是运行时的控制项，
插进去之后就变成消息的一部分，随消息一起持久化。

### 界面

- 输入框左侧的闪电按钮：点开是选单，上半截是快捷消息（进输入框），下半截「引导消息」直接加进本轮；
  **长按**把输入框里的内容当引导发出去（输入框为空时长按退回成展开选单）。
  快捷消息为空时这个按钮也留着，否则长按这个手势就藏起来了。
- 输入框上方的引导卡片：一行一条，点一下切换单轮/多轮，右边叉取消；单轮的插入后自己消失。
- 扩展页的「快捷消息」拆成两个 Tab：`快捷消息` 与 `引导提示词`（预设存 `Settings.guideMessages`，
  和快捷消息分开存，因为前者插进正在跑的回合、后者是下一轮的用户消息）。

改动文件：

```
ai/.../agent/Agent.kt                            pendingParts 带上本轮消息
ai/.../agent/AgentLoop.kt                        把 current 传给钩子
app/.../data/model/Guidance.kt                   新增：Guidance / GuidanceMode
app/.../data/ai/GuidanceInjection.kt             新增：合成工具调用、注入内容识别、清单文本读取
app/.../service/ConversationInbox.kt             引导的加入/取用/切换模式/取消
app/.../service/ChatService.kt                   takeInjectionParts：通知 + 清单去重 + 引导
app/.../data/ai/ChatRequestFactory.kt            takePendingParts 传入本轮消息
app/.../data/ai/transformers/TodoStateTransformer.kt  末尾已有清单提醒时不再重贴
app/.../data/datastore/Settings*.kt              guide_messages 字段与序列化
app/.../ui/components/ai/GuidancePanel.kt        新增：生效中的引导卡片
app/.../ui/components/ai/ChatInput.kt            闪电按钮的引导区与长按、引导卡片插在队列上方
app/.../ui/components/message/ChatMessageTools.kt 合成调用画成提示卡片，不走通用工具渲染
app/.../ui/pages/chat/ChatPage.kt, ChatVM.kt     接线
app/.../ui/pages/extensions/QuickMessages*.kt    两个 Tab，预设增删改
```

单测：`GuidanceInjectionTest`（合成调用的形状、id 唯一与长度、清单文本）、`GuidanceInboxTest`（单轮/多轮的取用语义、
取消、顺序、空串）、`AgentLoopTest` 新增一条（钩子拿到的 current 取值）、`SettingsFieldsTest` 覆盖新键。

未验证：这次没有跑 Gradle（Termux 上不编译），第一个真机包要重点看两处——长按手势在有无预设时都能用，
以及多轮引导在一轮里插入多次后 provider 是否照常接受。

## WebUI 原生功能（2026-11-12）

参照 `~/WorkSpace/rikka-gateway` 的注入补丁，把值得留下的功能做进 web-ui，数据一律走后端 API，
不再由网关抓 DOM、估算 token 或篡改 SSE：

- **上下文用量条**：输入框上方按模型的 WARN / MAX 画两条进度条，数据取最近一次回复的 `lastUsage`
  （压缩检查点之前的 usage 不作数）；点开看输入 / 输出 / 缓存明细。沿用
  `DisplaySetting.contextMeterStyle`（BAR / RING / OFF）与 `contextMeterAbsolute`。
- **渠道额度**：`GET /api/providers/{id}/quota` 复用 app 的 `ProviderManager.getQuota`，
  模型选择器与顶栏显示余额或订阅用量。对应网关的 `/__gw/bal`。
- **引导提示词**：生成中插话走 `POST /api/conversations/{id}/guidance`（会话收件箱），
  支持单轮 / 多轮、预设、取消；对应网关对 SSE 流的「插话」篡改。
- **子代理任务**：`GET /api/conversations/{id}/subagents` 读 `getChildConversationSummaries`，
  面板按树列出直接子会话，点一行跳到子会话；回投消息按 `isSubAgentNotice` 画成系统卡片。
  对应网关的 `/__gw/mcp`。
- **文件夹绑定工作区**：文件夹菜单多选工作区、星标主工作区，走已有的
  `POST /api/folders/{id}/workspaces`；`GET /api/workspaces` 只回名称与类型。

网关的配色、MD3 换肤与模型菜单折叠属于纯前端皮肤，web-ui 已有自己的主题和模型选择器，不重复实现；
流式篡改（句末加喵）不移植。

CI 增加 `Web UI typecheck` job：web-ui 平时只在发版时构建，类型检查现在每次 push 都跑；
后端 DTO 与前端类型对不上会在 PR 阶段就红。

## 还要做的

1. **签名**：在 `local.properties`（已 gitignore）加四项，之后所有版本都用同一个 keystore，否则无法覆盖升级
   ```properties
   storeFile=/absolute/path/to/your.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
2. **Room schema 36.json**：本机不编译，`app/schemas/me.rerere.rikkahub.data.db.AppDatabase/36.json`
   还没生成。第一次 `assembleGithubDebug` 之后把它提交上来，下一次迁移要用
3. **图标与链接**：`app_name` 已改为 L3Hub，应用图标仍是上游素材；`ui/pages/setting/SettingAboutPage.kt` 里的 GitHub / Discord 链接也仍指向上游
4. **可选：深链和 action 改名**（两版共存时，外部应用触发这些会弹选择器）
   - `AndroidManifest.xml` 的 `rikkahub://` scheme → 你的 scheme，同步改 `res/xml/shortcuts.xml`
   - `AndroidManifest.xml` 与 `RouteActivity.kt` 里的 `me.rerere.rikkahub.action.*`
5. **构建**
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

## 发版：交给 GitHub Actions

本机不跑 Gradle（Termux 上编译会把机器拖死），出包和发版都放在 runner 上。
四条通道共用 `build-apk.yml`，按 tag 形态与事件分流：

| 通道 | tag | 变体 | 包名 | Release |
|---|---|---|---|---|
| 正式版 | `v3.2.31`、`v3.3.0.2` | `githubRelease` | `com.rikkahub.l3hub.wfadnf` | 正式 Release（非 prerelease，自动成为 latest） |
| Canary | `Canary-Build` | `githubRelease` + `-PappIdSuffix=.canary` | `com.rikkahub.l3hub.wfadnf.canary` | Pre-release |
| Nightly | `Nightly-Build` | `githubDebug` | `com.rikkahub.l3hub.wfadnf.debug` | Pre-release，每夜自动更新 |
| 旧预览 | `v3.2.31-preview.102` | `githubDebug` | `com.rikkahub.l3hub.wfadnf.debug` | Pre-release |

日常只需要前三条；带版本号的 `-canary.N` / `-preview.N` 形态保留作历史兼容，不再主动使用。
三个包名互相独立，正式版、Canary、Nightly 可以同装在一台手机上，数据互不影响。
Canary 与正式版用 Secrets 里的 release key，Nightly 与旧预览用仓库里的 `keystore/l3hub-debug.jks`。
桌面名分别显示 `L3Hub`、`L3Hub Canary`、`L3Hub Nightly`，由 `-PappLabel` 注入。

步骤：

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`（每次各 +1，例如 354 / `L.3.2.31.176`），
   连同改动一起提交
2. 推 tag：

   ```bash
   # 正式版
   git tag v3.2.31 && git push origin v3.2.31
   # Canary：把滚动 tag 挪到要发的提交
   git tag -f Canary-Build && git push -f origin Canary-Build
   ```

   Nightly 不需要手动推：`schedule` 每天 19:00 UTC（北京时间次日 03:00）自动构建，
   构建成功后把 `Nightly-Build` 强推到当夜提交。要立刻出一版，也可以手动执行上面
   `Canary-Build` 的写法，把 tag 换成 `Nightly-Build`。

   手动 `workflow_dispatch` 时如果填了 tag，checkout 用的就是这个 tag 的提交，不会拿分支
   HEAD 出的包挂到 tag 上；`gh release create` 带 `--verify-tag`，tag 不存在直接报错，
   不会现造一个。
3. `build-apk.yml` 自动跑：checkout（含子模块）→ 核对 tag 与版本号 → pnpm 装 web-ui →
   `tools/build-apk.sh`（按 `VARIANT` 选 `:app:assembleGithubRelease` / `:app:assembleGithubDebug`，
   跳过 lint vital）→ 重命名产物 → 建 Release

正式版 tag 允许两种形态：三段 `vX.Y.Z` 与四段 `vX.Y.Z.W`（同一条线上再补一版，前三段不动）。
版本号核对只对正式版严格：三段 tag 的 `versionName` 必须形如 `L.<tag 去掉 v>.NNN`，四段 tag 则直接用
`L.<tag 去掉 v>` 与 tag 对齐；`versionCode` 必须大于上一个正式版 tag 的值，否则覆盖安装时系统会当成没更新。测试通道都是独立包名，
versionCode 与正式版撞车不影响谁覆盖谁，只核对 `versionName`；固定 tag 的 Canary / Nightly
不带版本号，连前缀也不核对。tag 解析不到（没推上去、浅克隆没拿到）会直接红。

正式版出一个包，arm64-v8a，R8 已开：

- `L3Hub-<tag>-arm64-v8a.apk`：正式版安装包，含全部功能

各通道（正式版 / Canary / Nightly / 旧预览）都只产出一个 APK，不再区分含不含 LiteRT 的两个版本。
变体名仍是 `githubRelease` / `githubDebug`，工作流对每个通道只编一遍。

Release notes 由流程按「上一个 tag..本 tag」的提交自己生成——本仓库不走 PR，GitHub 自带的
`--generate-notes` 只会给一行 Full Changelog。正式版取区间时用 `git describe --tags
--exclude='*-*'`，测试通道 tag 不参与，否则两次正式版之间的改动会被预发布 tag 切在区间外、
整段消失；预发布反过来不排除，区间留在预发布 tag 之间，免得把已经随正式版发过的改动
再列一遍。`ci:` / `test:` / `build:` 开头、或作用域是这三个的提交一律不进 notes，
读的人只关心功能变化。

### Canary：固定 tag `Canary-Build`

不随版本号走的测试通道：固定 tag `Canary-Build` 始终指向最新一次 Canary 构建，Release 也
固定是同一个，重复推送只覆盖资产、刷新说明。它用 `githubRelease` 变体，并通过
`-PappIdSuffix=.canary` 把包名改成 `com.rikkahub.l3hub.wfadnf.canary`，与正式版是独立应用，
可同装，数据互不影响。

出包只需把 tag 挪到要发的提交：

```bash
git tag -f Canary-Build && git push -f origin Canary-Build
```

Canary 产物固定叫 `L3Hub-Canary-Build-arm64-v8a.apk`，
每次构建还会删掉 release 上这次没产出的旧资产，下载列表里不会留着上一版的包。
Release 说明的改动区间从上一版说明末尾 `<!-- canary:from=… head=… -->` 记下的那次构建算起，
两次滚动构建之间只列真正新增的提交；同一提交重跑时沿用那次算出的起点，说明不会退化成空。
升级前的老说明没有这行记录，才退回按 `Canary-Build` 之前最近的一个 tag 取区间。

### Nightly：固定 tag `Nightly-Build`

Nightly 用 `githubDebug` 变体，包名由 debug buildType 的 `applicationIdSuffix` 定为
`com.rikkahub.l3hub.wfadnf.debug`，签名用仓库里公开的调试证书。它与正式版是独立应用，
可以同装；也与本地日常构建的 debug 包同包名同签名，CI 出的夜包能直接覆盖安装。

- 触发：`schedule` 每天 19:00 UTC（北京时间次日 03:00）；也可以手动推 `Nightly-Build` tag
- 出包：资产固定叫 `L3Hub-Nightly-Build-arm64-v8a.apk`，
  每夜覆盖同名资产
- tag：构建成功后流程把远端 `Nightly-Build` 强推到当夜提交（`GITHUB_TOKEN` 推的 tag 不会
  再触发一轮 workflow），release 页面的源码快照与下一夜的改动区间都靠它
- 说明：改动区间从 `<!-- nightly:from=… head=… -->` 记下的上一夜构建算起；同 versionCode
  的构建可以直接互相覆盖安装
- 限制：GitHub 对 60 天没有 push 的仓库会停用 scheduled workflow；夜包本身不重置这个计时

### 正式版的签名

正式版密钥不在仓库里，走 repository secrets：

| secret | 内容 |
|---|---|
| `ANDROID_RELEASE_KEYSTORE` | `l3hub-release.jks` 的 base64（单行） |
| `ANDROID_RELEASE_STORE_PASSWORD` | store 口令 |
| `ANDROID_RELEASE_KEY_ALIAS` | `l3hub-release` |
| `ANDROID_RELEASE_KEY_PASSWORD` | key 口令（与 store 相同） |

流程把 keystore 还原到 `$RUNNER_TEMP`，再把 `storeFile` / `storePassword` / `keyAlias` / `keyPassword`
写进 `local.properties`——`app/build.gradle.kts` 的 release 签名只认这个文件。
证书 SHA-256 指纹是 `0E:D1:33:05:52:46:D7:23:18:88:E7:21:97:F2:9F:A6:16:A6:53:67:DE:87:50:42:10:8F:9D:41:7B:58:35:42`，
每次发版都会写进 Release notes，装之前可以核对。这个值也写死在 `build-apk.yml` 的 Verify APK 步骤里：
release 变体的签名证书对不上就红，免得 secret 被换成别的 key 之后一路绿灯发出去。换密钥要同步改这一行。

密钥丢了就再也发不出能覆盖安装的更新：本机备份在 `~/keystore-backup/`（jks + 口令），务必另外存一份。
Canary 走的是同一把 release key，包名不同，装上去是独立应用；Verify APK 仍核对指纹。
Nightly 与旧预览不走这套：它们用仓库里公开的 `keystore/l3hub-debug.jks`，泄露了也无所谓，
所以 `build-apk.yml` 的还原密钥那步对 debug 变体是直接跳过的（早先那个 `ANDROID_DEBUG_KEYSTORE`
secret 没人读，已经从流程里去掉，仓库设置里可以一并删掉）。

几点：

- `ci.yml` 只跑单测、只跟分支；tag 触发的构建由 `build-apk.yml` 负责，同一份代码不重复跑
- `:terminal` / `:workspace` 带 CMake，配置阶段需要 NDK 28.2.13676358（约 700MB）。两个 workflow 都缓存了这个目录；
  不缓存的话每次现下，下坏过一次（`Error reading Zip content from a SeekableByteChannel`），整轮直接红
- 同一个 tag 重跑不会失败：release 已存在时覆盖资产、刷新说明，prerelease 标记也跟着 tag 走
- 只想要个包不发版：`workflow_dispatch` 触发 `build-apk.yml`，tag 留空，产物在 run 的 artifact 里；
  也可以在 inputs 的 `variant` 里手动指定出哪个变体
- 正式版是 R8 全量混淆出来的，`proguard-rules.pro` 覆盖不到的反射装配（sshd、Koin、serialization 之类）
  只有装到真机才看得出来，发版前先自己装一遍跑核心功能

## 会话详情：工具与提示词（2026-11-12）

- 竖屏详情从顶栏下方展开改成浮层：挂在 `Scaffold` 内容层的 `Box` 里，不再占布局，
  展开和收起都不会把聊天区推上推下
- 浮层高度固定为可用屏幕高度的 75%（`DetailsPanelHeightFraction`），内容在面板内部滚动
- 新增三块内容，数据来自 `ChatRequestFactory.createPreview`：
  - 工具定义：`Tool.parameters()` 按 provider 相同的序列化方式转成 JSON，用 `JsonTree` 展开查看
  - 工具说明：`Tool.description`，以及追加到系统提示词的 `Tool.systemPrompt`
  - 当前提示词：按 `buildStep` 的同一套顺序拼系统提示词（用户提示词、计划模式、记忆、工具说明），
    再套一遍 `PromptInjectionTransformer`，显示 `BEFORE/AFTER_SYSTEM_PROMPT` 注入后的结果
- 原来的「提示注入」只显示数量，现在逐条列出模式注入与世界书条目的来源、位置和内容
- 工作区板块上移到会话之后，详情一打开就能看到绑定的工作区与文件夹接管情况
- 详情内的层次：区块标题加重、区块之间画分割线，工具与注入的折叠标题改用主色
- 快照只在详情可见时计算：竖屏展开时算一次，横屏要选中右栏的「详情」标签才加载；
  MCP 工具列表读内存里的设置，不会主动连服务器
- 新字符串补了 `values` 与 `values-zh`，其余语言回退英文

## 和上游同步

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git rebase upstream/master
```

改动尽量放在独立文件里；上面列的文件是冲突热点，能不加就别加。

## 数据中心（独立应用）

`com.l3hub.database.wfadnf`，在 `:datacenter` 模块里，与 L3Hub 一并构建。作用是列出本机各构建
（正式版 / Canary / Nightly / 改过包名的分支），经 ContentProvider 读它们的库结构与整库快照，
跨数据库版本时按目标结构换算后再投递。协议常量在 `:syncproto`，两边共用；provider 的可信调用方
名单加上了数据中心。设计与边界见 `docs/data-center.md`。

出包走单独的工作流，tag 形如 `DB-v1.0.0`，版本号在 `datacenter/build.gradle.kts`。

## 许可证

AGPL-3.0。分发 APK 或对外提供网络服务时，必须一并提供完整源码。

## 同步上游 3.1.2（2026-11-08）

并入了 `upstream/master` 的 11 个提交（3.1.2 与之前的 preview 累积改动），与本 fork 相关的部分如下。

### 消息概览（重做）

- 概览不再由顶栏按钮左右换页，改为**左滑进入**：`ChatListSwipe.kt` 用 `Animatable<Float>` 记进度，
  聊天页与概览页按同一进度整体平移；顶栏菜单仍可切换，返回键先退出概览
- 概览 UI 换成上游的 `ChatListPreview.kt`：按轮次分组、搜索命中片段高亮、当前阅读位置标记、
  附件/收藏/工具调用图标；聊天页自己的 `LazyListState` 不动，概览用独立的 `previewListState`
- fork 原来的内联 `ChatListPreview`（在 `ChatList.kt` 里）与它专用的 `extractMatchingSnippet` /
  `buildHighlightedText` 一并删除，改用上游文件里的实现
- `ChatSuggestionsRow` / `MessageJumper` 挪到 `ChatListOverlays.kt`（上游结构），fork 的
  “滚回最新消息用短动画”保留

### 顶栏对话菜单

- 原「消息概览」图标按钮改成三点菜单：**概览 / 导出 / 删除**；空会话时三项置灰
- 「导出」不再直接弹面板，而是进入消息多选（默认全选），确认后再选格式；
  多选状态 `ChatSelectionState` 提到 `ChatPage` 一层，消息长按「分享」与顶栏菜单共用同一份状态
- 删除会话在菜单里带确认框，确认后删除并回到新会话页

### 导出

- 采用上游重写的 `ChatExportSheet`：先选 Markdown / 图片 / XML，再选**保存**（系统另存为）
  或**分享**；文件先写临时目录，保存时等用户选完位置再拷贝
- fork 原有的 XML 导出保留为第三种格式，`buildXmlExport` 仍把子代理回投记成 `system`

### 其他并入项

- 世界书条目支持长按拖拽排序（`PromptPage.kt`，`ReorderableColumn`）
- 行内公式紧贴标点或中文时也能渲染（bettertext 0.1.5）
- 收藏模型去重：`SettingsNormalization` 里 `favoriteModels.distinct()`，收藏按钮按最新设置判断
- 删除对话时同步删除该会话的消息收藏（`FavoriteDAO.deleteNodeFavoritesOfConversation`）
- 历史页改为上游的多选删除：长按进入多选、底部操作栏、`BackHandler` 退出多选
- 历史图片压缩结果缓存（`ImageEncodeCache`）

### 未并入

- **SSH 跳板机**（`e011970c`）：上游把连接重构成 endpoint + `ProxyJump` 隧道，
  与 fork 的认证失败指纹、远程目录探测（`SshProbeResult.remoteDirIssue`）改动叠在同一段代码上。
  这次保持 fork 的 SSH 实现不动，相关文件整体回到 fork 版本，`SshJumpHostTest` 不引入。
  以后要跳板机时，需要把上述两处 fork 修复移植到新的 `connectSession` / `SshProbeResult` 上

## 横屏三栏（2026-11-09）

横屏时屏幕只有 400dp 出头的高度，聊天页原来那些 `ModalBottomSheet`（附件面板、用量、任务树、模型列表、后台任务）
一开就把聊天区压在下面。现在横屏改成三列：左列是原来的会话抽屉，中间是聊天区，右列是标签页式的看板，
整块 UI 缩到 75%。竖屏照旧走原来的弹层。

### 判定与缩放

- 判定只看窗口：宽 > 高，且宽 ≥ 600dp → `threePaneLayout`（`ChatPage.kt` 里用 `currentWindowDpSize()` 算）
- 整块 UI 用 `LocalDensity` 缩到 0.75（`LANDSCAPE_UI_SCALE`）：字号、行高、控件一起缩；
  insets 按密度换算，物理高度不变
- 左列宽度 300dp（`LANDSCAPE_SIDEBAR_WIDTH`），默认展开，顶栏菜单按钮做宽度动画收合
- 中列宽度固定：`宽 / 0.75 × 0.4`，夹在 360–640dp；收起左栏时中列整体左移、宽度不变，
  腾出来的空间全部给右列（右列是 `weight(1f)`，跟着动画一路变宽）
- 中列宽度用可空的 `Dp?` 表达：横屏传具体值，竖屏传 `null`（中列吃满整宽）。
  竖屏曾经传 `0.dp`，配上定宽后整页塌成一条零宽的盒子

### 右列：浏览器标签页

- `ChatSidePanel.kt` 的 `ChatSidePane` 枚举是能开的看板：会话详情、任务树、本轮改动、用量、附件与工具、
  模型、后台任务；看板自己不再画标题行，标题归标签条
- `ChatSidePanelColumn` 画玻璃底板和 `SecondaryScrollableTabRow` 标签条，末尾的 `+` 打开「新建标签页」菜单
  （已经开着的打勾）；标签上的 × 关闭，关完最后一个退化成「新标签页」列表
- 和上一版的浮窗不同：不是模态的，不盖聊天区，点聊天区也不会收起来，没有返回键接管
- 顶栏在横屏只留菜单（收合左栏）、新建会话、会话菜单三项；模型胶囊、详情、任务树都收进右列标签页

### 本轮改动

`EditedFilesList`（消息下面那一行）在横屏只对最后一条消息隐藏，开关由 `ChatPageContent` 通过
`LocalTurnChangesInSidePanel` 提供。右列的「本轮改动」标签页复用同一个 composable（`TurnChangesPane`），
一轮里第一次改文件时自动把标签开出来，但不抢当前看板；同一轮手动关掉以后不会再弹回来。
历史轮次的改动仍然挂在各自的回复下面。

### 复用的内容

| 内容 | 抽出 | 竖屏外壳 |
|---|---|---|
| 附件 / 工作区 / 搜索 / 扩展 | `ChatFilesPickerContent` | `ChatFilesPickerSheet` |
| 子代理任务树 | `SubAgentTaskTree` | `SubAgentTaskSheet` |
| 后台命令 | `BackgroundTasksContent` | `BackgroundTasksSheet` |
| 模型列表 | `ModelListContent`（新增 `modifier`） | `ModelListSheet` |
| 会话详情 | `ConversationDetailsPanel`（新增 `embedded`、`showHeader`） | 顶栏下方的浮层 |
| 本轮改动 | `EditedFilesList` / `TurnChangesPane` | 消息下方那一行 |

### 没动的地方

- 确认类对话框（压缩后发送、删除会话）和终端输出 / 终端模型选择仍是对话框，它们本来就小，不吃高度
- 后台任务弹层从 `ChatInputContent` 提到了 `ChatPageContent` 一层，横屏才放得进右列
- 新增字符串 `chat_page_side_panel_model`、`chat_page_side_panel_new_tab`、`chat_page_side_panel_empty`、
  `chat_page_side_panel_close_tab`、`chat_page_turn_changes_empty`，补了 `values`、`values-zh`、`values-zh-rTW`，
  其余语言走英文回退

## 横屏两栏：设置与模型编辑（2026-11-09）

横屏宽屏（宽 > 高且 ≥ 700dp）时，设置相关页面改成左栏 + 右栏：
左栏是常驻的一级选项（`SettingRail.kt`），右栏是当前页面。模型编辑页单独走三栏。

### 设置分区栏

- 只在设置相关路由上出现：`Screen.isSettingBoard()`（`Setting*` 与 `ModelEditor`，
  后者除外——它自己就要三栏的宽度，见下）
- `SettingSection` 枚举列出栏里的一级项，顺序跟设置首页的卡片一致：偏好设置 / 助手 / 扩展功能 /
  默认模型 / 提供商 / 搜索 / 语音 / 媒体 / MCP / Web 服务器 / 数据备份 / 聊天存储 / 关于 / 请求日志。
  详情页按 `Screen.settingSection()` 高亮所属一级项，助手的一串子页（基本、提示词、记忆、请求参数、
  心跳、MCP、本地工具、扩展注入）都归在「助手」下
- 栏挂在 `RouteActivity` 的 `NavDisplay` 外层：`Row { SettingRail(...); NavDisplay(weight(1f)) }`。
  `NavDisplay` 一直待在同一个槽位，进出设置不会重建；点栏里的项时 `popUpTo(Screen.Setting)` +
  `launchSingleTop`，换分区不会把上一分区的详情页留在栈里
- 设置首页在右栏照常显示（头像、提醒、四组卡片），相当于桌面端的「主页」；
  助手 / 扩展功能 / 数据备份 / 请求日志这些独立管理页也收进栏里，横屏时不用退回首页再点一层
- 项数变多以后行高与分组间距相应收紧（图标 17dp、行内上下各 7dp），超出栏高时在栏内滚动

### 模型编辑三栏

`ModelSettingsForm` 按窗口分成两套：

| 窗口 | 布局 |
|---|---|
| 窄屏 / 竖屏 | 原来的 `PagerTabs` + `HorizontalPager` |
| 横屏 ≥ 700dp | 最左是标签列（基础设置 / 高级设置 / 内置工具），中列固定显示模型 ID、名称、类型，右列是当前标签的其余选项 |

中列常驻：切标签只换右列，改完 ID 再去看高级设置不用滚回去找字段。

### 复用的内容

- `ModelIdentityFields`（ID / 名称 / 类型）、`ModelBasicOptions`（模态 / 能力 / 上下文门限）、
  `ModelAdvancedOptions`（覆盖配置 / 请求头 / 请求体）从原表单里拆出来，两套布局共用
- 新增字符串 `setting_page_more`（更多），只补了 `values`、`values-zh`、`values-zh-rTW`

## 横屏的刘海避让与状态栏（2026-11-09）

- 横屏隐藏状态栏（`RouteActivity` 的 `HideStatusBarInLandscape`）：顶栏直接贴到屏幕顶边，
  竖向空间全给内容；回到竖屏再 `show(statusBars())`。判据用窗口尺寸，`onResume` 再补一次，
  转屏发生在后台时回来也能对上
- 挖孔的避让收到 `RouteActivity` 的 `Row` 一层：横屏时整块内容让开 `WindowInsets.displayCutout`
  的左右内侧。insets 被消费以后，页面里的顶栏不会再各自让一遍——之前聊天页中列的顶栏还会按刘海
  再让一次，左栏展开时「打开侧边栏」按钮就离左栏多出一段空当；设置页则完全没有避让
- 设置栏、模型编辑三栏、聊天三栏都在这一层之内，横屏下对刘海的处理因此一致

## 横屏三栏：文件看板、可拖列宽与设置三列（2026-11-09）

### 右侧栏的工作区文件看板

- `ChatSidePane` 新增 `Workspace`：只列会话绑定工作区的 FILES 区，目录逐级进入，
  文本文件在栏内只读预览（等宽字体、可选中），不跳页面，横屏聊天区不会被顶掉
- 会话没绑定工作区时给一句提示；绑了多个时标题行可以切换；右上角刷新重新读当前目录

### 三列的拖动手柄

- `ColumnResizeHandle`（`ui/components/ui`）：10dp 抓取区 + 中间一条短竖线，
  左栏与中列、中列与右栏之间各一个
- 宽度存 `rememberSaveable`：左栏 200–440dp，中列 320dp 起、上限按窗口减去左栏与右栏最小宽算；
  拖动时左栏的宽度动画切到 `snap()`，手柄不落后于手指
- 中列没拖过时仍按窗口的 0.4 倍给默认值，拖过以后按拖出来的宽度

### 设置页三列

- 左栏（`SettingRail`）选中进中列；页面里的下钻页（提供商详情、搜索服务详情、助手详情等）
  挂到右列，中列停在分区页，像 iPad 设置
- 两个 `NavDisplay` 共用同一条返回栈和同一份 `entryProvider`：中列显示栈里最后一个分区页
  及之前的条目，右列显示它之后的条目。返回键先退右列的详情，再退中列
- 中列 / 右列的比例可拖（0.28–0.66），右列进出只做淡入淡出
- 中列不再被右列压窄：中列宽度固定为「右列最窄时」的宽度（内容宽度的 0.66），
  右列比这个更宽时从右边盖住中列，列表与表单不跟着重排；右列收起时中列回到整宽

### 工作区终端看板

- `ChatSidePane` 新增 `Terminal`：复用 `WorkspaceTerminalSessionManager`，和整页的终端页
  共享同一批会话与标签，切过去还能接上
- 顶部是工作区下拉、终端页签下拉、新建与关闭；面板窄，不搬整页那条 160dp 宽的标签条
- 终端固定深色：嵌套一层 `RikkahubTheme(colorMode = DARK, applyUiScale = false)`。
  `RikkahubTheme` 新增 `applyUiScale` 开关，嵌套主题不再把界面缩放乘第二遍
- 键盘弹出时看板用 `imePadding()` 往上收，终端底部那一行不会被盖住

### 横屏的工具调用详情

- 横屏时点开工具调用不再弹 `ModalBottomSheet`，改成页面内从右侧滑入的看板：
  `ChatPage` 提供 `ToolDetailHostState`，`ChatMessageTools` 只把请求塞进去
- 走的是普通 Compose 覆盖，不另开窗口，所以不会把已经隐藏的状态栏带回来；
  竖屏与没有宿主时仍用原来的 BottomSheet
- 面板里预览铺满高度（`LocalToolPreviewFullHeight`），BottomSheet 里仍留 20% 给手势区

### 工具调用详情的富文本

- 详情里的文本按内容选择展示：JSON 格式化后高亮，Markdown 渲染成富文本，其它代码按文件扩展名高亮，
  纯文本按正文排版并可长按选择（其中的 URL 可点）
- Markdown 块右上角可在渲染与源码之间切换；记忆、技能文件开头的 YAML frontmatter 只在源码里显示；
  超过 60K 字符的 Markdown 默认只显示源码，避免面板一次排版几万行
- `workspace_read_file` / `workspace_write_file` 按扩展名推断语言，`.md` 直接渲染；记忆库的读取、写入、
  追加与片段替换分别显示排版后的正文和 diff
- 未单独定制渲染器的工具（如 `use_skill`、MCP 文本输出）也会自动识别 Markdown，不再一律当作 JSON 源码

### 清单与引导收进右栏

- `ChatSidePane` 新增 `Status`（清单与引导）：和输入区上方那张卡片是同一个
  `ConversationTodoCard`，在右栏里默认展开；清单、引导、插入提醒的开关都在
- 横屏时 `LocalConversationStatusInSidePanel` 为真，输入区不再重复渲染这张卡片；
  竖屏仍留在输入框上方
- 清单或引导出现时自动把标签页开出来，但不抢当前看板；手动关掉后同一份内容不会再弹回来

### 外观

- 左栏不再用圆角：横屏是常驻一列，竖屏滑出来也贴边，`drawerShape` 固定 `RectangleShape`
- 横屏不再盖背景图上的全屏渐变遮罩（`AssistantBackground(scrim = !threePane)`），
  竖屏保持原样；输入区上方那条底部渐变也一起去掉（`ChatInput(showScrim = false)`）

## 界面缩放、右列动画与扩展页归属（2026-11-09）

- 显示设置新增「界面缩放」：50%–200%，每档 5%。值存 `DisplaySetting.uiScale`，
  由 `RikkahubTheme` 用缩放后的 `LocalDensity` 提供给整棵树，dp 与 sp 一起变；
  1.0 时不额外干预。窗口 dp 尺寸跟着一起变，所以阈值类布局会按缩放后的宽度重新判定
- 设置三列的右列改成宽度动画进出：`animateFloatAsState` 控制右列权重，关闭时这段宽度
  平滑还给中列；拖动分隔条时动画切 `snap()`，手柄不落后于手指。收起过程中右列栈已经空了，
  用最后一次的栈把动画演完再移除
- 扩展功能下面的管理页（快速消息 / 提示词 / 技能 / 技能详情 / 工作区 / 工作区详情、终端、
  文件编辑、SSH 编辑）与捐赠、调试页都归到对应分区：在设置里打开时不再丢掉左栏、不再退回单列
- 左栏的显示条件加了一条「返回栈里得有分区页」：从聊天抽屉直接打开的扩展页仍是整页单列，
  不会凭空挂一条没有选中的左栏

## 显示模式（2026-11-09）

设置 → 界面偏好设置 顶部新增「显示模式」，点开四选一：

| 选项 | 落到窗口上的值 | 说明 |
|---|---|---|
| 竖屏 | `SCREEN_ORIENTATION_PORTRAIT` | 固定竖屏 |
| 横屏 | `SCREEN_ORIENTATION_LANDSCAPE` | 固定横屏，聊天页走上面那套三栏 |
| 自动 | `SCREEN_ORIENTATION_SENSOR` | 跟重力感应，系统方向锁拦不住，不转倒竖屏 |
| 跟随系统 | `SCREEN_ORIENTATION_UNSPECIFIED` | 什么都不锁，系统自动旋转开关说了算（也是默认值） |

- 存 `DisplaySetting.displayMode`（`Settings.kt`），默认 `SYSTEM`；老数据缺这个键时按默认值解出来，
  备份导入导出跟着 `display_setting` 那一块走，不用额外迁移
- 落在 `RouteActivity`：`LaunchedEffect(displayMode) { requestedOrientation = ... }`，改完即时生效；
  映射单独放在 `ui/activity/DisplayModeOrientation.kt`
- 平台限制：targetSdk 36+ 起，最小宽度 ≥ 600dp 的屏幕会忽略固定竖屏 / 横屏（Android 16 的适配规则），
  手机（sw < 600dp）不受影响。`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` 只恢复 resizability、
  不恢复方向锁，API 37 起连这个属性也没有了，所以没有加。自动与跟随系统用的两个值不在忽略名单里，
  任何屏幕都生效
- 只作用于 `RouteActivity`（主界面）。锁屏页、安全模式页仍跟随系统，它们是过场页面

## 余额精度与预警（2026-11-13）

- 余额从固定 2 位小数改为最多 12 位：`QuotaQueryRunner` 里的数字统一过 `formatBalanceAmount`，
  去掉末尾的 0、至少保留 2 位，`unlimited` 这类非数字文本原样显示；DeepSeek 的 `balance_infos`
  也走同一套格式化。多 key 求和（`sumBalanceValues`）保留各自的小数位
- 提供商设置 → 获取账户余额的展开区新增「余额预警值」，只在开关打开时出现。接受 `10`、
  `CNY 10`、`$10`、`10 USD`、`¥10` 等写法；带单位时只和同币种的余额比较，
  不带单位时比较余额里的第一笔（多币种以第一笔为主币种）；余额小于或等于预警值时，余额文字与图标转为 error 色
- 聊天页环形上下文用量条：余额和左侧用量各占一半宽度，余额过长时在自己的格子里省略，不再把
  WARN / MAX 数值挤成多行；余额在设置页最多显示两行

改动文件：

```text
ai/.../provider/BalanceThreshold.kt                     新增：金额解析、预警阈值与比较
ai/.../provider/QuotaQueryRunner.kt                     12 位小数格式化
ai/.../provider/ProviderQuota.kt                        QuotaQuery.balanceWarning
app/.../ui/components/ai/ProviderQuotaText.kt           预警标红、省略与右对齐
app/.../ui/components/ai/ContextUsageMeter.kt           余额与用量各占一半宽度
app/.../ui/pages/setting/components/QuotaQueryOption.kt 预警值输入框
app/.../ui/pages/setting/SettingProviderDetailPage.kt   余额最多两行
app/src/main/res/values*/strings.xml                    新文案
```

单测：`BalanceThresholdTest`（阈值格式、币种匹配、多币种取第一笔、无法解析的余额）、
`QuotaQueryRunnerTest`（12 位精度与至少 2 位）。本次没有跑 Gradle，以 CI 单测与真机包为准。

## DeepSeek 新服务接入（2026-11-13）

DeepSeek 于 2026-11-13 开放应用能力 API。L3Hub 侧的落点：

- 搜索 / 抓取：`search` 模块的 `DeepSeekSearchService`，在设置 → 搜索服务里选择 DeepSeek 并填入 API Key，
  `search_web` / `scrape_web` 工具分别走 `POST /v1/tools/search` 与 `POST /v1/tools/fetch`。
- OCR：按「模型」接入。DeepSeek 官方提供商的可用模型列表会多一条 `deepseek-ocr`（类型 OCR），
  在设置 → 模型 → OCR 辅助模型（或单个模型自己的 OCR 辅助模型）里选择它即可。请求走
  `POST /v1/tools/ocr`（multipart，`purpose=text_recognition`），逐页文本拼成一条 assistant 消息；
  识别结果里的 `<|ref|>`、`<|box|>` 等版面标记和坐标在进入对话上下文前会去掉。
- 语音识别：设置 → 语音 → 添加 ASR 提供商 → DeepSeek。接口是 `POST /v1/tools/asr`，
  录音按段包成 16 位 PCM WAV 上传（单段最多 120 秒、8 MiB），返回文本按段拼接；中文段之间不加空格。
- 语音合成：设置 → 语音 → 添加 TTS 提供商 → DeepSeek。接口是 `POST /v1/tools/tts`，
  音色 `stella` / `tide`，输出 `wav` 或 24 kHz PCM；文本沿用现有 TTS 分片管线（每片 150 字）。

改动文件：

```text
ai/.../provider/Model.kt                        ModelType.OCR
ai/.../provider/providers/openai/OpenAIProvider.kt  OCR 模型路由、DeepSeek 模型列表补 deepseek-ocr
ai/.../provider/providers/openai/DeepSeekOcrAPI.kt  新增：/v1/tools/ocr multipart 客户端与版面标记清理
ai/.../registry/ModelRegistry.kt                deepseek-ocr 的图片输入能力
app/.../data/ai/transformers/OcrSupport.kt      OCR 目标接受 ModelType.OCR
app/.../ui/components/ai/ModelList.kt           模型选择器支持多类型（聊天 + OCR）
app/.../ui/pages/setting/SettingProviderDetailPage.kt  OCR 类型选项、OCR 辅助模型选择
speech/.../tts/provider/providers/DeepSeekTTSProvider.kt  新增
speech/.../asr/providers/DeepSeekASRController.kt         新增
speech/.../tts/provider/TTSProviderSetting.kt    DeepSeek TTS 设置
speech/.../asr/ASRProviderSetting.kt             DeepSeek ASR 设置
app/.../ui/pages/setting/components/{TTS,ASR}ProviderConfigure.kt  配置项
app/src/main/res/values*/strings.xml             新文案
```

单测：`DeepSeekOcrTextTest`（版面标记清理）、`ModelRegistryTest.testDeepSeekOcr`、
`OcrSupportTest`（OCR 类型模型参与识别）、`TTSProviderSettingDeepSeekTest`、
`ASRProviderSettingDeepSeekTest`、`DeepSeekToolsUrlTest`。本次不跑本机 Gradle，以 CI 单测为准。

## 动图裁剪、视频头像与视频识别（2026-11-13）

### 动图裁剪保留动画

- `ImageCropDialog` 确认时不再只给一张 `Bitmap`，而是 `CropResult`：第一帧图、归一化裁剪区域
  （`NormalizedCrop`）和源文件是否为动图。`ImageCropImage` 用 `ImageDecoder.ImageInfo.isAnimated`
  判断动图；API 28 以下按 GIF / WebP 文件头兜底
- 头像选到动图时，`useCropLauncher` 把源文件复制成临时文件，配合裁剪区域交给 `UIAvatar`，
  存成 `Avatar.Image(url, crop)`；静态图仍是原来的「裁好写 JPEG」路径
- 新增 `CroppedImage`：用 Coil 的 `AsyncImagePainter` 按 `ImageCrop` 旋转、缩放、平移后绘制，
  GIF / WebP / 动画 HEIF 的动画不会被压掉；头像按实际 dp 取解码尺寸，背景最多 2048
- 背景同样支持动图裁剪：`Assistant.backgroundCrop`、`BackgroundPicker` 与 `Background` 共用同一套
  渲染，背景按「覆盖」方式居中裁剪区域；静态背景仍直接存裁好的 JPEG
- 聊天选图时如果源文件是动图，跳过裁剪直接发原图，避免 GIF 被裁成一张静态图

### 视频头像与背景

- `Avatar.Video` 与 `Assistant.backgroundIsVideo`：视频直接作为头像或聊天背景，静音循环播放；
  AVC / HEVC / AV1 / VPX 等编码能否播放取决于设备的 MediaCodec，容器由 ExoPlayer 负责
- `VideoSurface` 用 `TextureView` + `ExoPlayer`，按 `RESIZE_MODE_ZOOM` 的算法修正比例；
  头像位置小于 72dp（聊天列表、消息气泡）只显示 `MediaMetadataRetriever` 抓的一帧封面，
  避免一个列表挂多个解码器；大图头像和背景才真正播放
- 背景视频的不透明度直接设在 View 上，模糊用 `RenderEffect`（Android 12+），低版本忽略模糊
- 头像与背景的清理逻辑按 URL 比较，换裁剪区域不会误删同一个文件；复制、删除、备份路径都覆盖
  `Avatar.Video`

### 聊天视频与识别报错

- 「+」菜单的视频入口对所有模型开放，不再只对 Google 提供商显示
- `UIMessagePart.Video` 的 base64 编码改为 `encodeMedia`：按文件头识别 MP4 / MOV / 3GP / WebM /
  Matroska / AVI / FLV / WMV，Google provider 不再一律标成 `video/mp4`；内联视频上限 20 MB
- 主模型不是 Google 时，`VideoRecognitionTransformer` 找一个支持视频的 OCR 辅助模型
  （候选顺序同 OCR：模型指定 → 全局 OCR → 快速模型，且提供商必须是 Google）把视频读成文字，
  用 `<video_file_ocr>` 替换请求里的视频，并把结果固化到视频的 metadata
- 识别失败或没有可用模型时：错误原因写进请求让主模型转告用户，同时通过
  `TransformerContext.onInputError` 直接弹错误卡片，视频消息下面也会挂一张「视频识别失败」卡片；
  失败结果会固化但下一轮会重试，用户补好设置后不用重发视频
- 输入阶段新增的错误回调只用于聊天轮次，压缩摘要、标题生成这类只读请求保持静默

改动文件：

```text
ai/.../util/FileEncoder.kt                             视频 MIME 识别与 encodeMedia
ai/.../provider/providers/google/GoogleProvider.kt      视频使用真实 MIME
ai/.../provider/providers/google/InteractionsAPI.kt     同上
ai/.../ui/MessageMetadata.kt                            VideoRecognitionMetadata
app/.../data/model/Avatar.kt                            Avatar.Video 与裁剪字段
app/.../data/model/ImageCrop.kt                         新增：归一化裁剪区域
app/.../data/ai/transformers/OcrSupport.kt              视频识别目标、替换与固化
app/.../data/ai/transformers/VideoRecognitionTransformer.kt  新增：视频识别转换器
app/.../ui/components/ui/CroppedImage.kt                动图裁剪渲染
app/.../ui/components/ui/VideoMedia.kt                  视频播放与封面
app/.../ui/components/ui/UIAvatar.kt                    视频头像、动图裁剪保存
app/.../ui/components/ai/CropLauncher.kt                动图裁剪回调
app/.../ui/components/message/ChatMessage.kt            视频识别结果与失败卡片
app/.../ui/pages/chat/Background.kt                     视频背景、动图背景
app/.../ui/pages/assistant/detail/BackgroundPicker.kt   视频/动图背景选择
ui/.../crop/ImageCropDialog.kt, ImageCropState.kt, ImageCropImage.kt  裁剪结果与动图判断
```

验证：CI 单测与编译结果为准；GIF / WebP / 视频播放与视频识别需要真机逐项确认。

## 提供商请求调度（高级设置新增）

提供给商的请求约束保存在 `ProviderSetting.guard`（`ProviderGuard`）里，随提供商配置一起序列化、备份和分享。
界面在「提供商 → 高级设置」，校验发生在 `UsageRecorder` 包住每一次模型请求的位置，聊天、标题、翻译、
记忆整理、OCR 等所有请求共用同一套计数。

| 设置 | 行为 |
|---|---|
| RPM 限制 | 最近 60 秒的请求数达到上限后直接拦截，提示还要等多久 |
| TPM 限制 | 最近 60 秒的 token 用量加上本次请求的输入估算超过上限后拦截；请求开始时按估算占位，结束后换成真实用量 |
| 自定义时间段限额 | 可添加多条，每条独立选择时长（分钟/小时/天/周/月）、计量（请求次数/Token/费用）和上限；窗口算法三选一：「滚动」（从当前时刻往前推，用量逐条释放）、「固定」（从起算日期时刻对齐，每满一个周期整段归零）、「首条请求」（设定后第一条请求起算，归零后由下一条请求重新起算） |
| 自动锁定 | 可添加多条计划；「仅一次」在指定日期时刻触发，「每天」或「每周指定一个或多个星期几」从开始日期起重复，可设截止日期；支持跨午夜，锁定期间所有请求直接拦截 |
| 余额刷新间隔 | 聊天页余额的自动刷新频率，0 表示关闭；关闭后只在打开页面时查一次 |
| 覆写请求命令 | 一条 cURL 模板，替换 OpenAI / Claude / Google 的聊天请求；支持 `{{apiKey}}`、`{{baseUrl}}`、`{{model}}`、`{{stream}}`、`{{body}}`、`{{messages}}`、`{{tools}}`；响应仍按原提供商协议解析 |

- 三种算法统一走 `ProviderQuotaWindow.periodAt(usage, now)`，返回当前周期 [起点, 下一次重置)。固定窗口的边界
  由起算日期时刻加整数倍周期取当前段，起算日期留空时按本地 0 点 / 周一 / 每月 1 日的固定网格对齐，网格原点取
  1970-01-01 而不是「今天」，避免「每 2 天」这类周期随日期漂移；按月时按日历月份算（月末日期挤压，1 月 31 日的
  下一个月是 2 月 28 日）。
- 首条请求窗口把用量按周期切段：设定时刻（`effectiveAt`，新建时写入、编辑保留）之后的第一条请求开段，段尾归零；
  归零后如果一直没有请求，就没有进行中的周期（用量 0，不拦），下一条请求再开段。因此这类窗口需要从设定时刻起
  回读 `usage_record`，回溯超过 366 天时按截断后的第一条流水起算。
- 旧备份里没有 `mode` / `anchorDate` / `anchorTime` / `effectiveAt` 字段，反序列化后按滚动窗口处理，行为与升级前一致；
  设置页新建的限额默认是首条请求窗口。
- 历史用量从 `usage_record` 读回（进程启动前的部分），进程内的请求记在内存里；长窗口在重启后仍然有效。
- `ProviderGuard` 是具体提供商数据类的构造参数，`copy(apiKey = ...)`、`copyProvider(...)` 都不会丢。
- 覆写命令的解析在 `ai/.../RequestOverride.kt`，纯 JVM 单测见 `CurlCommandTest`；锁定窗口的计算见 `ProviderGuardTest`。
- 覆写只对聊天请求生效，OCR、生图和余额查询仍走原来的请求构建。

### 顶栏限额折叠条（2026-11-14）

「偏好设置 → 界面 → 限额显示」打开后，聊天页顶栏下方出现一条折叠条：

- 收起时一行摘要：提供商名 + 最近一分钟的 RPM / TPM 用量、各时间窗用量，以及能查到的余额或订阅用量。
- 点按整条展开看板，再次点按收起；展开与收起走 `expandVertically` / `shrinkVertically`，箭头同步旋转，
  动画速度跟随「动画速度」设置。
- 看板内容取自两处：`ProviderGate.status()`（高级设置里的 RPM / TPM / 自定义时间段限额，每 5 秒刷新）
  与 `getQuota()`（账号用量或自定义额度查询，按「余额刷新间隔」查询）。限流与额度窗口逐条带
  `LinearWavyProgressIndicator`，满格转 error 色；余额与定时锁定单独占一行。
- 没配限流、也没有额度查询的渠道显示一条空状态，不伪造数字。
- 看板最高 280dp，内容超出时在看板内部滚动，避免横屏把聊天区挤没。
- 开关存在 `DisplaySetting.showQuotaBar`，默认关闭；额度查询仍受提供商自己的「余额刷新间隔」约束。

涉及文件：`ui/components/ai/ChatQuotaStrip.kt`（新增）、`ui/pages/chat/ChatPage.kt`（挂到顶栏下方）、
`ui/pages/setting/SettingPreferencesUIPage.kt`（开关）、`data/datastore/Settings.kt`（字段）。

## 模型计费（2026-11-14 补充）

- 每次请求的费用在 `ProviderGate` 里按「请求开始那一刻的价目表」结算，写进 `usage_record` 和消息统计。
- 对话循环里改价目表（含变价规则）后，下一步请求就按新价目算：`ChatRequestFactory` 在每一步请求前按最新设置
  重新取一次价目（`Model.repriced`），只换价格表，模型身份仍用轮次快照，避免一轮里换模型或改系统提示词。
- 已经在途的请求按开始时的价目结算，不会因为中途改价重算；压缩、标题、翻译这些即时取设置的后台请求本来就按最新价目。

## 本轮改动：可折叠的路径树（2026-11-15）

消息下方的文件变更从一排文件名胶囊改成一张可折叠的卡片：默认收起，标题行是「文件变更 · N 个文件」，
点开按目录分级列出路径。

- 路径按目录建树，只有一层子目录、自己又没有文件的层级向上合并：`/a/b/c/d.kt` 只占 `/a/b/c` 和文件两行
- 目录行可以逐级折叠；文件行点开仍是原来的导出 / 分享面板
- 长路径先换行，超过三行才截断，路径用等宽字体
- 整块和每一级目录的收放都走 `MotionExpandCollapse`，与消息里其他折叠块共用动态模糊与时长设置
- 横屏右栏的「本轮改动」标签页复用同一个 composable，打开时默认展开

## 本轮改动：正文气泡的弹性伸缩（2026-11-15）

生成中的正文气泡不再一格一格地硬跳高度：新的文字进来时按空间曲线长高，内容变少时收回，
伸缩过程整只气泡叠一层动态模糊。文字本身仍走原来的飘入过渡（`streamingTextMotion`）。

- 只改报告给父布局的高度：内容仍按自然高度排版并贴顶放置，气泡的形状、底色与圆角按当前报告高度画，
  超出的部分由形状裁掉，文字不会在伸缩过程中重新换行
- 模糊按「离目标还差多少」给：差得越多越糊，追上目标就回到清晰；单行换行只差一个行高，糊得很轻，
  新起一段这类大跨度才推得明显，连续输出时不会一直糊着
- 关掉动态模糊时只伸缩不糊；关掉动效时空间曲线是 snap，直接落位
- 没开助手气泡时同样作用在正文块上，两种模式的伸缩手感一致
- 涉及文件：`ui/modifier/MotionBlur.kt`（`motionStretchHeight`）、
  `ui/components/message/ChatMessage.kt`（助手气泡与无气泡分支）

## 本轮改动：悬浮栏并入顶栏胶囊（2026-11-15）

输入框下方的悬浮栏不再是一块单独的圆角面板：容器换成顶栏两颗胶囊用的同一份实现（`ChatCapsule`），
按钮按胶囊的切面平均分段。

- 容器：`surfaceContainerLow` 底色、50% 圆角、按钮投影，模糊与描边走和顶栏同一条路径（只在模糊档描 1dp 细边）
- 分段：每个槽位占胶囊的一段，两头的段跟着外圈收圆、中间的段是直角；按下反馈照段形裁切，
  不再给每个按钮单独画一颗圆，全宽模式下那颗横跨整段的椭圆底色也一并去掉
- 段与段之间是一条细线，高度按胶囊高度的 40% 随悬浮栏大小缩放，比例和顶栏一致
- 紧凑模式不再留 4dp 内边距和 2dp 间隙，图标左右的可点区域就是整段，段与段之间没有死区
- 涉及文件：`ui/components/ai/ChatCapsule.kt`（顶栏与悬浮栏共用，取代原 `ChatTopBarCapsule.kt`）、
  `ui/components/ai/ChatFloatingBar.kt`、`ui/pages/chat/ChatPage.kt`（顶栏改用同一个容器）

## 本轮改动：底部抽屉补上统一退场，动画档位交给主题（2026-11-16）

底部抽屉（`ModalBottomSheet`）原先只有进场是自带的：material3 在组合时自己播 `show()`，
退场却只能由调用方先 `hide()` 再关状态。全仓 52 处调用里有 42 处是「点按钮就把布尔翻回 false」，
窗口随组合销毁，收起一帧消失——观感就是「打开有动画、关闭硬切」。

- 新增 `ui/components/SheetMotion.kt`：`AnimatedModalBottomSheet(visible, onDismissRequest)` 把窗口存活
  和可见性拆开，`visible` 变 false 时先 `hide()`、播完再拆窗口。52 处调用点统一换成它，不再自己
  `rememberBottomSheetState` / `hide()`，关窗怎么写（点按钮、选中一项、点外部）都走同一条退场
- 内容里的按钮要「播完再执行」（跳页面、弹确认框）时走 `rememberSheetDismiss()`，与对话框的
  `LocalDialogDismiss` 是同一个约定：没有 sheet 包着时退化为直接执行
- 退场期间要保住最后一份内容：`selectedLog`、`editingProvider` 这类状态在关闭瞬间已经是 null，
  统一用 `shownXxx` 记住最后一份再传进 sheet。`SettingProviderDetailPage` 的
  `remember(editingProvider) { editingProvider!! }` 就是会被 null 直接打崩的那一类
- `ui/theme/MotionSpecs.kt` 新增 `motionSchemeOf(animationSpeed)`：FAST / GENTLE 两档各给一套
  `MotionScheme` 交给主题。material3 自己的动效（抽屉收放、菜单、遮罩淡入、拖拽吸附）只读这一个口子，
  原先「动画速度」只作用于手写的位移，弹窗还按默认时长慢慢走；`rememberSpatialSpec()` /
  `rememberEffectsSpec()` / `rememberFastSpatialSpec()` 现在直接读当前 scheme，参数不再各写一份
- 横屏工具详情侧滑（`ToolDetailSidePanel`）的 `tween(160 / 220 / 180)` 换成主题档位，进出场同参
- 涉及文件：`ui/components/SheetMotion.kt`（新增）、`ui/theme/MotionSpecs.kt`、`ui/theme/Theme.kt`、
  52 处 `ModalBottomSheet` 调用点、`ui/pages/chat/ToolDetailSidePanel.kt`

## 本轮改动：设置里的未知类型不再把应用判死刑（2026-11-17）

装完包打不开、闪一下退回桌面、没有任何提示——根因是设置里存了一条当前代码不认识的提供商。

- 现象：`PROVIDERS` 里有一条 `{"type":"deepseek-account"}`（由 `feat/deepseek-account` 分支写入），
  master 上没有这个子类，`kotlinx.serialization` 解码整个数组时抛 `JsonDecodingException`；
  `SettingsStore` 捕获后调用 `Runtime.getRuntime().halt(1)`，静默杀进程，不弹窗、不写崩溃标记，
  于是「换回任何 master 包都起不来」，只能清数据。
- `SettingsFields` 新增 `decodeListLenient`：多态列表逐条解码，认不出的子类型跳过并记日志，
  其余提供商 / 助手 / 模型原样保留。提供商、媒体生成、TTS、ASR 四个列表都改走它。
- `SettingsStore` 读盘失败不再 `halt`：退回默认设置继续启动，`persisted` 同步设成这份默认值，
  之后用户改设置只写改过的字段，不会拿默认值盖掉磁盘上的原数据；换成认识这份数据的包以后原样回来。
- 单测：`SettingsLenientDecodeTest` 覆盖「未知类型被跳过」与「已知提供商在旁边仍然读得出来」。
- 排查手段（已随定位完成删除）：`StartupTrace` 把启动各阶段逐行写进 `Download/l3hub-startup-trace.txt`，
  `StartupWatchdog` 盯首帧之前的主线程。这类「起不来又拿不到 logcat」的问题，
  靠它一次拿到 `settings-load-failed` 的完整栈。埋点本身要防住两件事：
  单测里 `android.util.Log` / `android.os.SystemClock` 都是空实现会抛，埋点不能因此把业务带崩。

## 同步上游 3.3.0（2026-11-17）

上游 18 个提交，`64452581d..bfac2440c`，合并进 `sync/upstream-20261117`，39 处冲突。

上游这一轮带来的功能：

- 助手回复按段落拆成多个气泡（`MarkdownParagraphs.kt`、`DisplaySetting.splitAssistantBubbleByParagraph`）
- 本地工作区把设备共享存储的目录挂进 Rootfs（`WorkspaceStorageMount`、`WorkspaceStorageMountCard`）
- 从 DeepSeek 导出的 ZIP 导入每个会话的当前分支（`DeepSeekImporter`）
- DeepSeek 语音合成与识别
- Gemini 单独下发的 `thoughtSignature` part、Vertex 服务账号走 Interactions API
- 安全模式支持清除超大的设置数据
- Phone Use 后台生成期间由悬浮窗保持亮屏
- 后台任务详情长命令可滚动、测试连接弹窗点模型搜索框不再自动关闭
- `message_node` 复合索引、请求体日志只在调试构建打印

取舍与实现差异：

- **DeepSeek 语音**：Fork 早前已自行实现，保留 Fork 版本（`baseUrl` 带不带 `/v1` 都能拼出 tool 端点、中文段拼接不加空格、TTS 可选 `wav` / `pcm` 与 Markdown 过滤），只把上游两处更强的实现搬过来：`stop()` 先等录音协程退出再取缓冲区（否则最后一块 PCM 被截掉）、TTS 的流加 `flowOn(Dispatchers.IO)`。`pcm16ToWav` 改用上游抽出的 `speech/.../Wav.kt`，删掉 Fork 那份私有拷贝，单测直接覆盖共用实现。
- **数据库**：上游把 `message_node` 索引换成 `(conversation_id, node_index)`（32→33）、`workspaces` 加 `storage_mounts`（33→34）。Fork 的版本号已经走到 37，33–35 的 schema 与上游同名但内容不同，不能照搬，改为 `Migration_37_38` 一次性补齐这两项，版本升到 38；上游的 `Migration_32_33` 与其 androidTest 未采用，`AutoMigration(32, 33)` 保留，Fork 的 32→37 老链路继续可用。
- **自动压缩**：上游在偏好设置里重新加了全局开关与触发比例（默认开启），Fork 仍按助手配置（见上一节），所以 `Settings` 里不加这三个字段，`SettingPreferencesUIPage` 也不加对应条目。`DefaultProvidersTest` 上游以「默认提供商全部开启后它一直失败」删掉，Fork 侧那版断言本来就是 `enabled = true`，保留。
- **测试连接弹窗**：采用上游的「只有 dialog、开关由调用方持有」结构（入口按钮在键盘弹出时会移出 composition，dialog 挂在它下面会被一起销毁），外层仍用 Fork 的 `AnimatedAlertDialog` / `AnimatedModalBottomSheet`，报错抽屉保留「用最后一份报错把退场演完」。
- **段落拆泡**：保留 Fork 气泡的 `LocalUiSlots` 配色、`hazeState`、`stretchHeight`；拆段后只有首段沿用 `joinedToPrevious`、末段沿用 `joinedToNext`，中间各段自成一只气泡。
- **DeepSeek 导入**：与 Fork 的「恢复方式 / 恢复内容」选项并存——本地备份仍先弹 `RestoreOptionsDialog` 再选文件，DeepSeek 导入成功后弹自己的结果框，且不触发重启提示。
- **DeepSeek 语音配置项**：`ASRProviderConfigure` / `TTSProviderConfigure` 两边都加了 `deepSeekASRItems` / `deepSeekTTSItems`，合并后同一文件里出现两份同名函数。保留 Fork 的本地化版本（走 `stringResource`、TTS 多出格式与 Markdown 过滤两项），删掉上游那份，顺带给输入框补 `singleLine`。
- **文案**：8 种语言的 `strings.xml` 先取 Fork 版本，再把上游新增的 31 个键补进去。在用的 25 个已按各语言补齐翻译；`deep_thinking_seconds`、`chat_page_folder_add`、`setting_display_page_auto_compress_*` 这 6 个当前没有引用，留英文原文。
- **版本号**：本次只合代码，`versionCode` / `versionName`（352 / `L.3.2.30.174`）未动，出包时再统一决定。
- **验证**：本机不编译，行为验证走 CI 单测（push 即触发）；真机观感未实测。
