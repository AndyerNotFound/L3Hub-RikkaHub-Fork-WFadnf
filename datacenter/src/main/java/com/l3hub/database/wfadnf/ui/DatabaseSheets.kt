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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.l3hub.database.wfadnf.core.DbColumnInfo
import com.l3hub.database.wfadnf.core.DbConversation
import com.l3hub.database.wfadnf.core.DbInspector
import com.l3hub.database.wfadnf.core.DbMessage
import com.l3hub.database.wfadnf.core.DbObjectInfo
import com.l3hub.database.wfadnf.core.DbSummary
import com.l3hub.database.wfadnf.core.DbTableInfo
import com.l3hub.database.wfadnf.core.DbTablePage
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
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.SmartPhone01
import java.io.File

internal sealed interface DbSheet {
    data class Table(val table: DbTableInfo, val file: File?) : DbSheet
    data class Conversation(val conversation: DbConversation, val file: File?) : DbSheet
    data class Objects(val objects: List<DbObjectInfo>) : DbSheet
    data class Mirror(val folder: MirrorFolder) : DbSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableSheet(sheet: DbSheet.Table, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = sheet.file
    var page by remember(sheet.table.name, file) { mutableStateOf<DbTablePage?>(null) }
    var failure by remember(sheet.table.name, file) { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet.table.name, file) {
        if (file == null || !file.isFile) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { DbInspector.tablePage(context, file, sheet.table.name) }
                .onSuccess { page = it }
                .onFailure { failure = it.message ?: it.javaClass.simpleName }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(sheet.table.name, style = MaterialTheme.typography.titleMedium)
            HubNote("${sheet.table.rows} 行 · ${sheet.table.columns.size} 列")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    HubCardGroup(title = "列") {
                        formItem(visible = sheet.table.columns.isEmpty()) {
                            HubNote("运行中的库只报建表语句，列信息要从快照里读。")
                        }
                        sheet.table.columns.forEach { column ->
                            item(
                                headlineContent = { Text(column.name) },
                                supportingContent = { Text(column.label()) },
                            )
                        }
                    }
                }
                item {
                    HubCardGroup(title = "建表语句") {
                        formItem {
                            SelectionContainer {
                                Text(
                                    sheet.table.sql.ifBlank { "（没有记录建表语句）" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                        }
                    }
                }
                item { Text("前 40 行", style = MaterialTheme.typography.titleSmall) }
                item {
                    when {
                        failure != null -> HubNote("读取失败：$failure")
                        file == null -> HubNote("这份结构来自运行中的库，没有可以读取的副本。")
                        page == null -> HubNote("正在读取…")
                        page?.rows.isNullOrEmpty() -> HubNote("这张表是空的。")
                        else -> {
                            val rows = page ?: return@item
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                rows.rows.forEachIndexed { index, row ->
                                    RowValues(index = index, columns = rows.columns, values = row)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowValues(index: Int, columns: List<String>, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("第 ${index + 1} 行", style = MaterialTheme.typography.labelMedium)
        columns.forEachIndexed { columnIndex, column ->
            val value = values.getOrNull(columnIndex).orEmpty()
            if (value.isNotBlank() && value != "—") {
                Text(
                    "$column = $value",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationSheet(sheet: DbSheet.Conversation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = sheet.file
    var messages by remember(sheet.conversation.id, file) { mutableStateOf<List<DbMessage>?>(null) }
    var failure by remember(sheet.conversation.id, file) { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet.conversation.id, file) {
        if (file == null || !file.isFile) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { DbInspector.messages(context, file, sheet.conversation.id) }
                .onSuccess { messages = it }
                .onFailure { failure = it.message ?: it.javaClass.simpleName }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                sheet.conversation.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HubNote(
                "更新于 ${sheet.conversation.updatedAt.formatTime()} · " +
                    "${sheet.conversation.nodes} 个消息节点 · ${sheet.conversation.id}",
            )
            when {
                failure != null -> HubNote("读取失败：$failure")
                file == null -> HubNote("运行中的库没有消息正文，拉一份快照再看。")
                messages == null -> HubNote("正在读取消息…")
                messages?.isEmpty() == true -> HubNote("这个会话没有消息。")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(messages.orEmpty()) { message ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HubStatusPill(message.role.uppercase())
                            if (message.text.isNotBlank()) {
                                Text(
                                    message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            message.attachments.forEach { attachment ->
                                Text(
                                    "附件 $attachment",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObjectsSheet(objects: List<DbObjectInfo>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("索引、触发器与视图", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(objects) { objectInfo ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${objectInfo.type} · ${objectInfo.name}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        SelectionContainer {
                            Text(
                                objectInfo.sql,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MirrorSheet(
    folder: MirrorFolder,
    files: (String) -> List<Pair<String, Long>>,
    onDismiss: () -> Unit,
) {
    var entries by remember(folder.name) { mutableStateOf<List<Pair<String, Long>>?>(null) }
    LaunchedEffect(folder.name) {
        withContext(Dispatchers.IO) { entries = runCatching { files(folder.name) }.getOrDefault(emptyList()) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("镜像 · ${folder.name}", style = MaterialTheme.typography.titleMedium)
            HubNote("${folder.files} 个文件 · ${SyncEngine.formatBytes(folder.bytes)}")
            when {
                entries == null -> HubNote("正在读取…")
                entries?.isEmpty() == true -> HubNote("这个目录是空的。")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entries.orEmpty()) { (path, bytes) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                path,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                SyncEngine.formatBytes(bytes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun DbColumnInfo.label(): String = buildString {
    append(type.ifBlank { "ANY" })
    if (primaryKey) append(" · 主键")
    if (notNull) append(" · 非空")
}
