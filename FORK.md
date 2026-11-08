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

## 自动压缩（fork 新增）

助手 → 记忆 → 「自动压缩」：

| 项 | 说明 |
|---|---|
| 触发阈值 | `WARN` 或 `MAX`，取模型上配置的两个上下文门限；两个都没配时按窗口兜底（WARN 75%、MAX 90%）；自动填充的门限（`contextThresholdsAuto`）按窗口收窄，比例和预设一致（WARN 75%、MAX 90%）——超过真实窗口的门限等于没有门限，两档都夹到 90% 又会把 WARN 和 MAX 夹成同一个数，预警那一档就没了；手填的值不动。以前最后一步退回的是 `Model.contextLength` 原值，而它的默认值是 1M，真实窗口 128k / 200k 又没被识别出来的模型会拿到一个永远到不了的门限，只能等 provider 报超限 |
| 压缩后保留 | 只按消息条数保留，切点一定落在两条消息之间。配置的条数比现有消息还多时按一半压；一轮对话只剩「提问 + 回复」两条时退到只保回复、把提问压进摘要，否则超限的请求原样发出去只会被拒。旧版本按 token 数保留的配置退回默认 8 条：那套算法要在消息内部找 part 边界，切出来的历史是残缺的一轮 |
| 触发时机 | 写入用户消息前后各判断一次；开了「循环压缩」时，模型循环里每次请求之前再判断一次——在 `prepareStep` 里压完，这一步和之后每一步都按最新会话重新组装历史（`AgentLoop` 手里的 history 是启动时的快照），既不发已经注定超限的请求，也不把整轮重发一遍。判据以最近一次请求的真实 usage 打底，只补上它之后新增的内容（新消息，以及上一次请求之后才执行完的工具输出——工具输出是原地写回已有 part 的，usage 里看不到）：usage 已经包含系统提示词、工具定义和它之前的全部历史，而估算只是补 usage 看不到的那部分。两边各算整段再取大（旧做法）会把整段上下文放大约一倍，中文会话在门限六成时就反复触发压缩。检查点之前的 usage 不算数，那份占用算的是压缩前的上下文。循环里的判据用这一步真正要发的消息，不是会话快照：`MessageUpdated` 要经过 `flowOn` 的缓冲才写回会话，`prepareStep` 跑在产出侧，会话里往往还没有刚执行完的工具输出。provider 仍然报超限时由 `AgentHooks.onContextOverflow` 压缩后重装这一步（最多两次），不再把整轮重跑一遍 |
| 压缩目标 | 当前占用的 50%（`AUTO_COMPRESS_TARGET_RATIO`）与门限的 50% 取小，再扣掉摘要和 systemPrompt 的固定开销。保留区超出预算时从旧的一端整条丢，压完不会立刻又贴着门限 |
| 同一份上下文只压一次 | 节点数、末尾节点、末尾消息的 token 估算都没变就直接跳过：循环压缩后回复还在续写，`usage` 还是压缩前那次请求的，拿它判断会立刻再压一遍 |
| 上下文已满 | 到达 MAX 后确认发送会先弹窗，确认后压缩再发送 |
| 效果 | 与手动压缩完全一致：调用同一个压缩请求，在原位置插入 `isContextCheckpoint` 摘要消息 |
| 原文 | 压缩只改变发给模型的上下文，会话里存的消息一条都不删。旧数据里切在消息中间的检查点仍按 `contextStartMessageId` / `contextStartPartIndex` 裁剪请求 |
| 界面 | 不弹浮窗，复用生成中的加载球与 `processingStatus`，显示「正在压缩……」 |
| 已知限制 | 整轮工具调用和输出都堆在同一条 assistant 消息里，按节点压缩只能切它之前的历史。压到只剩「提问 + 回复」后，这一轮再涨也没有可切的节点，只能由 provider 的硬上限兜底。另外，流式已经吐出内容之后 provider 才报超限时，重装这一步是重新生成：请求里仍带着那半截回复，而最终存下来的回复从空消息重新写，已经显示过的开头会被替换掉 |

实现位置：

- `data/model/AutoCompress.kt`：阈值枚举、`resolveContextUsage`（门限判据）、`autoCompressBudget` / `resolveAutoCompressTrim`（压缩目标与截断点）
- `ai/ui/Message.kt` 的 `limitContext`：真正的请求裁剪点，跳过检查点之前的消息、按条数滞回截断、再按检查点里的 part 切分点丢掉已进摘要的前缀
- `Assistant` 新增 `autoCompress`、`autoCompressThreshold`、`autoCompressTarget`、`autoCompressTargetValue`
- `AgentHooks.onContextOverflow`：`AgentLoop` 在一步请求超限时回调，压缩后按新历史重装这一步；`ChatService` 用它接上 `retryWithCompression`，外层只剩「循环没机会处理」时的整轮兜底
- `ChatService.autoCompressIfNeeded`：在 `sendQueuedMessage` 的 job 里、写入用户消息之前执行；循环压缩在 `ChatRequestFactory` 的 `beforeStep` 钩子里、每次请求之前执行；`compressConversation` 与它们共用 `compressSession`
- 开启自动压缩后，MAX 拦截（`sendMessage` / `dispatchNextQueuedMessage`）先放行，由这一轮生成前的压缩处理；压缩失败只记日志，不阻塞回复

## 品牌与界面调整（fork）

- 应用图标：上游 mipmap 各密度 PNG（`ic_launcher` / `_background` / `_foreground`）整体反色，原版是深底白兔，现在是浅底黑兔；`ic_launcher_monochrome` 保持原样（主题图标靠 alpha 取形，反色会变透明）
- 关于页：标题改为 L3Hub，注明基于 RikkaHub；链接改为本 fork 仓库与 LICENSE，另保留「上游项目」指向 `rikkahub/rikkahub`
- 模型编辑：删掉「上下文长度」整段，上下文窗口只保留 WARN / MAX 两个预警门限
- 界面偏好：新增「上下文预警进度条」开关（`DisplaySetting.showContextMeter`），关闭后输入框上方的进度条整行隐藏
- 思考卡标题：折叠时用思考内容的第一句当标题（`DisplaySetting.showThinkingSummary`，默认开），没有正文或关掉开关就退回「思考了 N 秒」；截句是 `utils/MarkdownUtils.kt` 里的纯函数 `extractThinkingSummary()`，带单测
- 压缩检查点：分隔线默认折叠时露出两行摘要预览，展开可看完整摘要并编辑/删除
- 任务树：工具调用改为等宽字体代码块，输入按 JSON 格式化、shell 输出拆出 exit code 与 stdout，长内容折叠，可逐条展开

## 侧边栏改版（fork）

- 抽屉底部改为 ChatGPT 风格：左侧大胶囊「新聊天」，右侧用户头像；头像点击进入设置，并带一段从抽屉飞向页面顶部的过渡动画
- 顶部用户信息（头像/昵称/问候）移除，昵称和头像改到「设置 → 偏好设置 → 通用 → 个人资料」；设置首页顶部改为紧凑标题栏 + 居中大头像，飞行动画正好落在这里
- 会话列表的日期分组可点击折叠/展开；折叠后只隐藏会话，不再吞掉下一个日期标题，也不留空白间隙
- 去掉「图像生成」入口；助手、收藏、统计三个入口改为「AI 翻译」下面的三行

## 后台运行与视图增强（fork）

- 通知设置新增「请求电池优化白名单」：已加入白名单时显示状态，未加入时点击拉起系统授权（没有弹窗的系统退回电池优化列表页）；对应 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限
- 消息视图双层折叠：最后一段正文之前的块整体折叠成「展开前面的 N 步」，展开后思考/工具仍按上游方式折叠；正文前只有一条工具循环链（合并成一个 ChainOfThought 块）时同样折叠，纯思考块仍交给上游折叠；生成中与含待审批工具时不折叠
- 子代理默认后台执行：`dispatch_sub_agent` 立即返回，子代理跑完后结果自动投递回父会话；父会话正在生成时先缓冲，空闲后合并成一条用户消息投递。确实要等结果时可以传 `wait=true`，在工具调用里阻塞等待，不必空转或轮询

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
- 仍是 20fps 节流；`animate = false`（生成中、滚动中）和切后台时不空转；
  色相漂移和位置漂移共用同一份相位状态，没有新增定时器

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

## 页面容器变换（fork 新增）

页面切换不再整页横移，改成「容器变换」：点的那一行（设置里的分组卡片、助手卡片、列表项）自己展开成整页，
返回时收回那一行里。位移交给 `SharedTransitionLayout` 里的 hero 容器演，`NavDisplay` 的
`transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` 只留淡入淡出，两层动画不再打架。

- 目标页：`RouteActivity.kt` 里绝大多数 `entry<T>` 换成 `heroEntry<T>`，把页面包进
  `HeroPage(key.heroKey())`；页面到位后圆角从 20dp 收到直角，退场时再长回去。
  页面**自己不要再包一层 `HeroPage`**——`heroEntry` 已经包了，叠两层同一个 key 的共享容器会打架
- 没有源条目的页面（抽屉、聊天流里的入口、模型编辑页的「新建」）由 `Screen.usesHeroTransition()`
  判定为 false，转场改成从下方滑入 / 向下滑出，比纯淡出更有方向感；新增 `heroSource` 入口时
  要在这个 `when` 里补一条，漏了会和容器变换叠成两层位移
- 源条目：`Modifier.heroSource(screen, shape)`，圆角默认取 `HeroSourceCorner`（20dp）；
  全宽组件模式下两边一起归零（都走 `adaptiveCornerRadius`），否则展开的第一帧会对不上
- 两边的 key 都由 `Screen.heroKey()` 算：`Screen` 是 sealed 接口，新增条目时 `when` 少一条编译不过，
  正好挡住「两个页面共用一个 key、容器变换串台」
- `Screen.Setting` 没套容器变换：切配色时会 `popUpTo` 原地重进，新旧两页会同时在组合里
- 助手列表头像原来的 `heroAnimation` 去掉了，整卡已经是容器变换，两层叠加互相打架
- `Screen.MediaCreation` 复用 `mediaCreationHeroKey()`，和会话列表那侧同一个 key

转场期间离场的那一页还会吃点击：navigation3 1.2.0 的 `NavDisplay` 在 exit 动画结束前把离场的 entry
留在 `AnimatedContent` 里继续组合，却不拦它的指针输入。pop 时它 zIndex 更高（淡出早完了、位移还没收尾），
看起来是空的，点下去算点它。`ui/hooks/NavInputGuard.kt` 的 `rememberNavInputGuardDecorator()` 给
「目标状态不是 Visible」的 entry 盖一层 `Box`，在 `PointerEventPass.Initial` 把指针事件消费掉；
正在进场的那一页不拦，它自己的位移是 `graphicsLayer` 做的，命中区域跟着视觉位置走。
代价是纯 fade 的退场（`usesHeroTransition()` 为 false 的那几个页面）在 pop 时整屏有约一个动画时长的空窗。

`CardGroup` 跟着加了两样：

- `item(heroScreen = ...)`：这一行点击后展开成对应页面，形状由条目自己算（中间项是 4dp 不是 20dp）
- `item(visible = ...)` / `formItem(visible = ...)` / `switchItem(visible = ...)`：条件项不再写成
  `if (cond) { switchItem(...) }`，而是挂在条目上，收起/展开走 `expandVertically` + `shrinkVertically`
  （弹簧过冲后回弹），后面的条目连同圆角一起挪过去；关掉动效时用 `snap` 直接到位

| 文件 | 说明 |
|---|---|
| `ui/hooks/HeroAnimation.kt` | 新增 `HeroPage`、`heroSource`、`Screen.heroKey()`、`HeroSourceCorner` |
| `RouteActivity.kt` | `heroEntry` 扩展；`transitionSpec` 只留淡入淡出 |
| `ui/components/ui/CardGroup.kt` | `heroScreen` / `visible` 参数、展开收起动画 |

`CardGroup` 的 `content` 参数**不能**标 `@Composable`：标了之后它变成一个独立的重组作用域，
外部状态（比如某个开关的 `remember` 值）变了只会把 `items` 重新填一遍，下面读 `items` 的
`Column` 不会重组，表现是「点了开关没反应，动一下别的才刷新」。调用点里的 `item(...)` /
`formItem(...)` 都不是 composable 调用，把 composable 块作为参数传进去，去掉标注照样编译。
| 各 `*Page.kt` | 入口条目补 `heroScreen`、列表项补 `heroSource`；原来的条件项改用 `visible` |

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
（body `{"workspaceIds": [...], "workspaceId": "..."}`，留空即解绑）。web-ui 前端还没做对应控件。

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
写进当前助手消息，和真实工具结果一样进请求、进落库、进导出。

```kotlin
UIMessagePart.Tool(
    toolCallId = "g_<16 位十六进制>",   // 不带连字符：OpenAI 的 tool_call_id 上限 40 字符，整套 uuid 会到 45
    toolName = "guidance",             // 不注册进工具表，模型调不到；声明了反而会诱导它去调用
    input = "{}",
    output = listOf(UIMessagePart.Text(引导正文)),
)
```

- 工具名 `guidance` / `todo_reminder` 都不在工具表里。各家 provider 转换时只按「已执行的 Tool part」成组序列化，
  不校验名字是否声明过，所以不需要为它们常驻一个工具。
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
两条线共用 `build-apk.yml`，按 tag 形态分流：

| tag | 变体 | 包名 | Release |
|---|---|---|---|
| `v3.1.1` | `githubRelease` | `com.rikkahub.l3hub.wfadnf` | 正式版（非 prerelease，自动成为 latest） |
| `v3.1.1-preview.102` | `githubDebug` | `com.rikkahub.l3hub.wfadnf.debug` | Pre-release |

两个包是两个独立应用，可以同装在一台手机上，数据互不影响（预览版桌面名是 L3Hub Preview）。

步骤：

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`（每次各 +1，例如 283 / `L.3.1.4.105`），连同改动一起提交
2. 打 tag 推上去：`git tag v3.1.1 && git push origin v3.1.1`（预览版就是 `v3.1.1-preview.102`）。
   手动触发时如果填了 tag，checkout 用的就是这个 tag 的提交，不会拿分支 HEAD 出的包挂到 tag 上；
   `gh release create` 带 `--verify-tag`，tag 不存在直接报错，不会现造一个
   tag 和版本号由 `Check version against tag` 这一步互相对账：`versionName` 必须以
   `L.<tag 去掉 v 与 -preview.NN>.` 开头，`versionCode` 必须大于上一个正式版 tag 的值。
   忘了改版本号、或者 tag 打错一位，都在出包前就红，不会发出去一个 versionCode 没涨、
   装上等于没更新的包。tag 本身解析不到（没推上去、浅克隆没拿到）也直接红；
   只有三种早期历史会跳过递增校验：根本没有上一个正式版 tag、上一个 tag 上还没有
   `app/build.gradle.kts`、上一个 tag 里读不到 `versionCode`（跳过时会在 Actions 里
   留一条 warning）。正式版的 tag 必须写成 `vX.Y.Z` 三段，少打一段直接红
3. `build-apk.yml` 自动跑：checkout（含子模块）→ 核对 tag 与版本号 → pnpm 装 web-ui → `tools/build-apk.sh`
   （按 `VARIANT` 选 `:app:assembleGithubRelease` / `:app:assembleGithubDebug`，跳过 lint vital）→ 重命名产物 → 建 Release

产物是 `L3Hub-<tag>-arm64-v8a.apk` 加同名 `.sha256`，arm64-v8a，R8 已开。
Release notes 由流程按「上一个正式版 tag..本 tag」的提交自己生成——本仓库不走 PR，
GitHub 自带的 `--generate-notes` 只会给一行 Full Changelog。正式版取区间时用
`git describe --tags --exclude='*-*'`，预览版 tag（`v3.1.1-preview.102`）
不参与，否则两次正式版之间的改动会被预览版切在区间外、整段消失；预览版反过来不排除，
区间留在预览 tag 之间，免得把已经随正式版发过的改动再列一遍。
`ci:` / `test:` / `build:` 开头（含 `ci(xxx):` 这种带域的写法）的提交一律不进 notes，
读的人只关心功能变化。

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
正式版签名证书对不上就红，免得 secret 被换成别的 key 之后一路绿灯发出去。换密钥要同步改这一行。

密钥丢了就再也发不出能覆盖安装的更新：本机备份在 `~/keystore-backup/`（jks + 口令），务必另外存一份。
预览版不走这套：它用仓库里公开的 `keystore/l3hub-debug.jks`，泄露了也无所谓，
所以 `build-apk.yml` 的还原密钥那步对预览版是直接跳过的（早先那个 `ANDROID_DEBUG_KEYSTORE`
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

## 和上游同步

```bash
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git rebase upstream/master
```

改动尽量放在独立文件里；上面列的文件是冲突热点，能不加就别加。

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
| 会话详情 | `ConversationDetailsPanel`（新增 `embedded`、`showHeader`） | 顶栏下方展开 |
| 本轮改动 | `EditedFilesList` / `TurnChangesPane` | 消息下方那一行 |

### 没动的地方

- 确认类对话框（压缩后发送、删除会话）和终端输出 / 终端模型选择仍是对话框，它们本来就小，不吃高度
- 后台任务弹层从 `ChatInputContent` 提到了 `ChatPageContent` 一层，横屏才放得进右列
- 终端风格横屏同样走三栏，终端顶栏只留 MENU / NEW / HELP
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

### 工作区终端看板

- `ChatSidePane` 新增 `Terminal`：复用 `WorkspaceTerminalSessionManager`，和整页的终端页
  共享同一批会话与标签，切过去还能接上
- 顶部是工作区下拉、终端页签下拉、新建与关闭；面板窄，不搬整页那条 160dp 宽的标签条
- 终端固定深色：嵌套一层 `RikkahubTheme(colorMode = DARK, applyUiScale = false)`。
  `RikkahubTheme` 新增 `applyUiScale` 开关，嵌套主题不再把界面缩放乘第二遍
- 键盘弹出时看板用 `imePadding()` 往上收，终端底部那一行不会被盖住

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
