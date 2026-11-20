package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.DbAttachment
import com.l3hub.database.wfadnf.core.DbConversation
import com.l3hub.database.wfadnf.core.DbInspector
import com.l3hub.database.wfadnf.core.DbSummary
import com.l3hub.database.wfadnf.core.DbTableInfo
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.MirrorFolder
import com.l3hub.database.wfadnf.core.SyncEngine
import com.l3hub.database.wfadnf.core.formatTime
import com.l3hub.database.wfadnf.ui.components.HubCardGroup
import com.l3hub.database.wfadnf.ui.components.HubCardIcon
import com.l3hub.database.wfadnf.ui.components.HubNote
import com.l3hub.database.wfadnf.ui.components.HubStatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Tools

private const val DB_LIST_LIMIT = 300

/**
 * 「数据库」页：先选数据源（运行中的库或某一份快照），再看结构、会话、附件。
 *
 * 运行中的库只经 `schema` 拿到表与计数（没有内容）；要看会话与消息，得先拉一份快照——
 * 快照是脱离应用的一整份库，页面上所有读取都在副本上完成。
 */
@Composable
fun DatabaseScreen(
    state: HubUiState,
    controller: HubController,
    selection: DbSelection?,
    onSelect: (DbSelection) -> Unit,
    onExport: (DbExportRequest) -> Unit,
) {
    val context = LocalContext.current
    val build = state.builds.firstOrNull { it.packageName == selection?.packageName }
    val snapshot = build?.snapshots?.firstOrNull { it.id == selection?.snapshotId }
    val snapshotFile = remember(selection) {
        val sel = selection ?: return@remember null
        sel.snapshotId?.let { id -> controller.snapshotDatabase(sel.packageName, id) }
    }
    var summary by remember { mutableStateOf<DbSummary?>(null) }
    var conversations by remember { mutableStateOf<List<DbConversation>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<DbAttachment>>(emptyList()) }
    var mirror by remember { mutableStateOf<List<MirrorFolder>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<DbSheet?>(null) }

    LaunchedEffect(selection, build?.schemaVersion) {
        val sel = selection
        summary = null
        conversations = emptyList()
        attachments = emptyList()
        failure = null
        if (sel == null) {
            mirror = emptyList()
            return@LaunchedEffect
        }
        loading = true
        val file = sel.snapshotId?.let { id -> controller.snapshotDatabase(sel.packageName, id) }
        withContext(Dispatchers.IO) {
            mirror = runCatching { controller.mirrorFolders(sel.packageName) }.getOrDefault(emptyList())
            if (file == null || !file.isFile) return@withContext
            runCatching { DbInspector.summary(context, file) }
                .onSuccess { summary = it }
                .onFailure { failure = it.message ?: it.javaClass.simpleName }
            runCatching { DbInspector.conversations(context, file) }.onSuccess { conversations = it }
            runCatching { DbInspector.attachments(context, file) }.onSuccess { attachments = it }
        }
        loading = false
    }

    val liveSchema = if (selection != null && snapshot == null) build?.schema else null
    val tables: List<DbTableInfo> = summary?.tables ?: liveSchema?.objects.orEmpty()
        .filter { it.type == "table" }
        .map { objectInfo ->
            DbTableInfo(
                name = objectInfo.name,
                rows = liveSchema?.counts?.get(objectInfo.name) ?: 0L,
                columns = emptyList(),
                sql = objectInfo.sql,
            )
        }
    val others = summary?.others ?: liveSchema?.objects.orEmpty().filter { it.type != "table" }
    val schemaVersion = summary?.schemaVersion ?: liveSchema?.schemaVersion ?: 0
    val pageSize = summary?.pageSize ?: liveSchema?.pageSize ?: 0
    val databaseBytes = summary?.bytes ?: liveSchema?.databaseBytes ?: 0L
    val identityHash = summary?.identityHash ?: liveSchema?.identityHash
    val liveCounts = liveSchema?.counts.orEmpty()
    val conversationCount = summary?.tables?.firstOrNull { it.name == "ConversationEntity" }?.rows
        ?: liveCounts["ConversationEntity"] ?: 0L
    val messageCount = summary?.tables?.firstOrNull { it.name == "message_node" }?.rows
        ?: liveCounts["message_node"] ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HubCardGroup(title = "数据源") {
                item(
                    onClick = { showSources = true },
                    headlineContent = {
                        Text(
                            when {
                                selection == null -> "选择数据源"
                                snapshot != null ->
                                    "${build?.label ?: selection.packageName} · " +
                                        "快照 ${snapshot.createdAt.formatTime()}"

                                else -> "${build?.label ?: selection.packageName} · 运行中的库"
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            when {
                                selection == null -> "运行中的库只看结构；快照能看会话、消息与附件。"
                                snapshot != null ->
                                    "库 v${snapshot.schemaVersion} · 会话 ${snapshot.conversations} · " +
                                        "消息 ${snapshot.messages}"

                                else -> "对端报上来的 sqlite_master 与行数，内容需要拉一份快照。"
                            },
                        )
                    },
                    leadingContent = { HubCardIcon(HugeIcons.SmartPhone01) },
                    trailingContent = { HubCardIcon(HugeIcons.ArrowRight01) },
                )
                item(
                    onClick = selection?.let { current ->
                        {
                            onExport(
                                DbExportRequest(
                                    packageName = current.packageName,
                                    snapshotId = current.snapshotId,
                                    label = build?.label ?: current.packageName,
                                )
                            )
                        }
                    },
                    headlineContent = { Text("导出数据库") },
                    supportingContent = { Text("整库导出为 .db，交给所选位置。") },
                    leadingContent = { HubCardIcon(HugeIcons.FileExport) },
                )
            }
        }

        if (selection == null) {
            item { HubNote("上面选一个数据源。构建页的「查看数据库」也会跳到这里。") }
        }

        if (selection != null) {
            item {
                HubCardGroup(title = "概况") {
                    formItem {
                        StatRow("库版本", if (schemaVersion > 0) "v$schemaVersion" else "—")
                        StatRow("页大小", if (pageSize > 0) "$pageSize B" else "—")
                        StatRow("体积", SyncEngine.formatBytes(databaseBytes))
                        StatRow("表", "${tables.size} 张")
                        StatRow("会话", conversationCount.toString())
                        StatRow("消息节点", messageCount.toString())
                        StatRow("附件", "${attachments.size} 条登记 · ${mirror.sumOf { it.files }} 个镜像文件")
                        identityHash?.let { hash -> StatRow("identity_hash", hash, monospace = true) }
                        if (loading) {
                            HubNote("正在读取数据库…")
                        }
                        failure?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                HubCardGroup(title = "表结构") {
                    formItem(visible = tables.isEmpty()) { HubNote("没有读到表。") }
                    tables.forEach { table ->
                        item(
                            onClick = { sheet = DbSheet.Table(table, snapshotFile) },
                            headlineContent = { Text(table.name) },
                            supportingContent = {
                                Text(
                                    "${table.rows} 行" +
                                        if (table.columns.isNotEmpty()) " · ${table.columns.size} 列" else "",
                                )
                            },
                            leadingContent = { HubCardIcon(HugeIcons.File02) },
                            trailingContent = { HubCardIcon(HugeIcons.ArrowRight01) },
                        )
                    }
                    item(
                        onClick = if (others.isEmpty()) null else {
                            { sheet = DbSheet.Objects(others) }
                        },
                        headlineContent = { Text("索引、触发器与视图") },
                        supportingContent = {
                            Text(
                                "索引 ${others.count { it.type == "index" }} · " +
                                    "触发器 ${others.count { it.type == "trigger" }} · " +
                                    "视图 ${others.count { it.type == "view" }}",
                            )
                        },
                        leadingContent = { HubCardIcon(HugeIcons.Tools) },
                    )
                }
            }

            item {
                HubCardGroup(title = "会话") {
                    formItem(visible = conversations.isEmpty()) {
                        HubNote(
                            if (snapshot == null) {
                                "运行中的库只给结构。拉一份快照后这里会列出会话标题、消息数与正文。"
                            } else {
                                "这份快照里没有会话。"
                            },
                        )
                    }
                    conversations.forEach { conversation ->
                        item(
                            onClick = { sheet = DbSheet.Conversation(conversation, snapshotFile) },
                            headlineContent = {
                                Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    "${conversation.updatedAt.formatTime()} · " +
                                        "${conversation.nodes} 个消息节点" +
                                        if (conversation.pinned) " · 置顶" else "",
                                )
                            },
                            leadingContent = { HubCardIcon(HugeIcons.BubbleChat) },
                        )
                    }
                    formItem(visible = conversations.size >= DB_LIST_LIMIT) {
                        HubNote("只列最近 $DB_LIST_LIMIT 个会话，其余仍在库里。")
                    }
                }
            }

            item {
                HubCardGroup(title = "附件") {
                    formItem(visible = attachments.isEmpty() && mirror.isEmpty()) {
                        HubNote("这份快照没有登记附件，镜像里也没有文件。")
                    }
                    attachments.take(DB_LIST_LIMIT).forEach { attachment ->
                        item(
                            headlineContent = {
                                Text(
                                    attachment.name.ifBlank { attachment.path },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${attachment.path} · ${SyncEngine.formatBytes(attachment.bytes)}")
                            },
                            leadingContent = { HubCardIcon(HugeIcons.Image02) },
                        )
                    }
                    formItem(visible = attachments.size >= DB_LIST_LIMIT) {
                        HubNote("只列最近 $DB_LIST_LIMIT 条附件登记。")
                    }
                    mirror.forEach { folder ->
                        item(
                            onClick = { sheet = DbSheet.Mirror(folder) },
                            headlineContent = { Text("镜像 · ${folder.name}") },
                            supportingContent = {
                                Text("${folder.files} 个文件 · ${SyncEngine.formatBytes(folder.bytes)}")
                            },
                            leadingContent = { HubCardIcon(HugeIcons.Folder01) },
                            trailingContent = { HubCardIcon(HugeIcons.ArrowRight01) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showSources) {
        SourceSheet(
            builds = state.builds,
            selection = selection,
            onDismiss = { showSources = false },
            onSelect = { picked ->
                showSources = false
                onSelect(picked)
            },
        )
    }

    sheet?.let { current ->
        when (current) {
            is DbSheet.Table -> TableSheet(current, onDismiss = { sheet = null })
            is DbSheet.Conversation -> ConversationSheet(current, onDismiss = { sheet = null })
            is DbSheet.Objects -> ObjectsSheet(current.objects, onDismiss = { sheet = null })
            is DbSheet.Mirror -> MirrorSheet(
                folder = current.folder,
                files = { folder -> controller.mirrorFiles(selection?.packageName.orEmpty(), folder) },
                onDismiss = { sheet = null },
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(
    builds: List<BuildUi>,
    selection: DbSelection?,
    onDismiss: () -> Unit,
    onSelect: (DbSelection) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("数据源", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                builds.forEach { build ->
                    item(key = "src-${build.packageName}") {
                        HubCardGroup(title = build.label) {
                            item(
                                onClick = if (build.schema == null) {
                                    null
                                } else {
                                    { onSelect(DbSelection(build.packageName)) }
                                },
                                headlineContent = { Text("运行中的库") },
                                supportingContent = {
                                    Text(
                                        if (build.schema == null) {
                                            "这个构建不支持结构导出，先拉快照。"
                                        } else {
                                            "库 v${build.schemaVersion} · " +
                                                "会话 ${build.schema?.counts?.get("ConversationEntity") ?: 0}"
                                        },
                                    )
                                },
                                leadingContent = { HubCardIcon(HugeIcons.SmartPhone01) },
                                trailingContent = {
                                    if (selection?.packageName == build.packageName &&
                                        selection.snapshotId == null
                                    ) {
                                        HubStatusPill("查看中")
                                    } else {
                                        null
                                    }
                                },
                            )
                            build.snapshots.forEach { snapshot ->
                                item(
                                    onClick = { onSelect(DbSelection(build.packageName, snapshot.id)) },
                                    headlineContent = { Text("快照 · ${snapshot.createdAt.formatTime()}") },
                                    supportingContent = {
                                        Text(
                                            "库 v${snapshot.schemaVersion} · 会话 ${snapshot.conversations} · " +
                                                SyncEngine.formatBytes(snapshot.databaseBytes),
                                        )
                                    },
                                    leadingContent = { HubCardIcon(HugeIcons.Clock01) },
                                    trailingContent = {
                                        if (selection?.packageName == build.packageName &&
                                            selection.snapshotId == snapshot.id
                                        ) {
                                            HubStatusPill("查看中")
                                        } else {
                                            null
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
