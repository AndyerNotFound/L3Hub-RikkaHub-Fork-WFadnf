# 数据中心（com.l3hub.database.wfadnf）

独立应用，用来在本机各 L3Hub 构建之间搬运数据：正式版、Canary、Nightly，以及任何沿用同一套
同步协议、只是改了包名的分支。它不依赖备份文件，全部经 ContentProvider 走 IPC。

## 为什么单独做一个应用

L3Hub 自带的版本同步要求两边数据库版本一致：增量库没有完整表结构，交给 Room 迁移会失败；
整份归档虽然能迁移，但只有「旧的往新的」一条路，返不回来。数据库版本一旦分叉，两版之间就只剩
导备份、卸载、重装、导入这一条路。

数据中心把「结构」与「数据」分开处理：结构由每个构建自己报（运行中的 `sqlite_master`），
数据由数据中心按目标结构重建。于是跨版本、跨分支都能投递，且各包的库各自留档。

## 协议

协议常量在 `:syncproto`（`com.l3hub.syncproto.L3HubSyncProtocol`），应用与数据中心共用一份，
避免两边各写一套键名慢慢漂移。

在原有方法（`ping` / `export` / `job` / `import` / `manifest` / `exportDelta` / `importDelta` /
`cleanup`）之外新增一个：

| 方法 | 参数 | 返回 |
| --- | --- | --- |
| `schema` | 无 | `KEY_SCHEMA`：`PeerSchema` JSON |

`PeerSchema` 含库版本、`room_master_table.identity_hash`、页大小、体积、几张主表的行数，
以及 `sqlite_master` 里全部表 / 索引 / 触发器 / 视图的建表语句。取的是运行中的库，不是编译期导出的
schema 文件——fork 的 migrations 各自演进，导出文件未必和线上一致。

`PROTOCOL_VERSION` 保持 2：新方法只增不改，老构建收到 `schema` 会抛「未知方法」，数据中心据此
把该构建标成「只能拉、不能跨版本投递」。

## 调用方信任

各构建的 provider 由 UID 反查包名，允许的调用方是三个构建加数据中心
（`SyncBridge.TRUSTED_CALLERS`）。签名密钥不同，signature 级权限会把要同步的一方挡在外面，
所以只能按包名判断；包名全设备唯一，伪造只能靠改包名，那会撞掉安装。

尚未更新的 L3Hub 构建会抛 `SecurityException`，数据中心在列表里显示「需要更新到包含数据中心的版本」。

## 仓库布局

```
files/hub/
  index.json                                  元数据（各包、各快照、镜像统计）
  packages/<包名>/snapshots/<id>/rikka_hub.db 快照
  packages/<包名>/snapshots/<id>/schema.json  快照的结构（拉取时对方报的那份）
  packages/<包名>/mirror/<相对路径>            附件镜像
```

每包只留最近 8 份快照，最新的「同步前备份」始终保留。

## 拉取

1. `ping` 确认版本、库版本、是否有待安装数据；
2. `schema` 取结构与指纹；
3. `export`（只带数据库）→ 解出 `rikka_hub.db` → 读行数、体积、`user_version` → 落成快照；
4. 需要附件时 `manifest` 拿清单，按「本地镜像缺失或大小不同」分批 `exportDelta`（只带附件），
   单批最多 400 个文件。

重复拉取只传变化的附件；数据库每次都是整份，因为它必须包含全部表。

## 投递

结构判定不只看版本号，还看 Room 的 `identity_hash`：同一个版本号在 fork 里可能被改出不同结构。

- 结构一致：直接搬运快照里的库。
- 结构不同：以目标的建表语句重建一个空库，逐表按列名取交集抄数据。目标多出来的列用它自己的
  `DEFAULT`；没有默认值且非空时跳过整张表并在结果里报告；源里多出来的列丢弃并记录。
  消息索引 `message_fts` 连同 rowid 一起抄，`message_fts_rowid` 按抄完的索引重建，避免搜索指错会话。
  写回目标的 `identity_hash` 与 `user_version`，Room 打开时不会判定结构不符。

两种方式：

- **合并**：先把目标现状拉一份作回滚点，再把源合并进这份副本——删除墓碑先落地，对端较新的会话
  整条替换，本地缺的补进来，分组与收藏补缺，工作区与记忆按 `updated_at` 取新，用量流水只补
  本地新会话名下的。合并规则与 L3Hub 的增量合并一致。
- **替换**：目标整库换成这份快照。附件一并投递时以镜像为准，目标里多出来的附件也会清掉。

投递包沿用备份格式（数据库条目 `rikka_hub.db`，附件按相对路径平铺），目标侧走 `PendingRestore`
/ `PendingDelta`，在下次启动、Room 打开之前安装。合并模式先补附件、后投数据库，两个待安装目录
互不覆盖；启动顺序是先整份恢复、再增量。

## 出包

```
./gradlew :datacenter:assembleRelease
```

CI：`.github/workflows/build-datacenter.yml`，tag 形如 `DB-v1.0.0`，产物 `L3Hub-DataCenter-DB-v1.0.0.apk`
（不分 ABI，内含 arm64-v8a 与 x86_64 的 `libsimple.so`），签名与 L3Hub 正式版同一把 release key。

版本号只改 `datacenter/build.gradle.kts` 的 `versionCode` / `versionName`，CI 会核对 tag 与
`versionName` 一致。

## 已知边界

- 需要目标构建先更新到含数据中心授权的版本，否则只能看到「不可用」。
- 附件镜像按「路径 + 字节数」判断是否需要重传；内容变了但大小恰好相同的文件会漏一次。
- 跨结构换算只做列级映射：字段含义变化、JSON 内部结构变化不在自动换算范围内，需在目标端手动处理。
