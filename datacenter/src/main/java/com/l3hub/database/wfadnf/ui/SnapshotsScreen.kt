package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.ExportRequest
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.SnapshotKind
import com.l3hub.database.wfadnf.core.SnapshotRecord
import com.l3hub.database.wfadnf.core.SyncEngine
import com.l3hub.database.wfadnf.core.formatTime
import com.l3hub.database.wfadnf.ui.components.HubCardGroup
import com.l3hub.database.wfadnf.ui.components.HubCardIcon
import com.l3hub.database.wfadnf.ui.components.HubNote
import com.l3hub.database.wfadnf.ui.components.HubStatusPill
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.Refresh01

private data class SnapshotTarget(val build: BuildUi, val snapshot: SnapshotRecord)

@Composable
fun SnapshotsScreen(
    state: HubUiState,
    controller: HubController,
    onExport: (ExportRequest) -> Unit,
    onOpenDatabase: (DbSelection) -> Unit,
) {
    var sheet by remember { mutableStateOf<SnapshotTarget?>(null) }
    val groups = state.builds.filter { it.snapshots.isNotEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HubNote(
                "每个包各留一份仓库：快照记下当时的库版本与行数，可以导出、回滚，也可以单独打开看内容。" +
                    "每包只留最近 3 份，最近的「同步前备份」始终保留。",
            )
        }
        if (!state.loading && groups.isEmpty()) {
            item { HubNote("还没有快照。到「构建」页拉一份。") }
        }
        groups.forEach { build ->
            item(key = "group-${build.packageName}") {
                HubCardGroup(title = build.label) {
                    build.snapshots.forEach { snapshot ->
                        item(
                            onClick = { sheet = SnapshotTarget(build, snapshot) },
                            headlineContent = {
                                Text(
                                    "${snapshotKindLabel(snapshot.kind)} · ${snapshot.createdAt.formatTime()}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(
                                    "库 v${snapshot.schemaVersion} · 会话 ${snapshot.conversations} · " +
                                        "消息 ${snapshot.messages} · ${SyncEngine.formatBytes(snapshot.databaseBytes)}",
                                )
                            },
                            leadingContent = { HubCardIcon(HugeIcons.Clock01) },
                            trailingContent = {
                                if (snapshot.filesIncluded) HubStatusPill("含附件") else null
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    sheet?.let { target ->
        SnapshotSheet(
            target = target,
            busy = state.busyLabel != null,
            onDismiss = { sheet = null },
            onExport = {
                sheet = null
                onExport(ExportRequest(target.build.packageName, target.snapshot.id, target.build.label))
            },
            onOpenDatabase = {
                sheet = null
                onOpenDatabase(DbSelection(target.build.packageName, target.snapshot.id))
            },
            onRollback = {
                sheet = null
                controller.rollback(target.build.packageName, target.snapshot.id)
            },
            onDelete = {
                sheet = null
                controller.deleteSnapshot(target.build.packageName, target.snapshot.id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotSheet(
    target: SnapshotTarget,
    busy: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onOpenDatabase: () -> Unit,
    onRollback: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${snapshotKindLabel(target.snapshot.kind)}快照 · ${target.snapshot.createdAt.formatTime()}",
                style = MaterialTheme.typography.titleMedium,
            )
            HubNote(
                "${target.build.label} · 库 v${target.snapshot.schemaVersion} · " +
                    "${SyncEngine.formatBytes(target.snapshot.databaseBytes)}" +
                    if (target.snapshot.filesIncluded) " · 含附件镜像" else "",
            )
            HubCardGroup {
                item(
                    onClick = onOpenDatabase,
                    headlineContent = { Text("查看数据库") },
                    supportingContent = { Text("表结构、会话、消息与附件清单。") },
                    leadingContent = { HubCardIcon(HugeIcons.File02) },
                )
                item(
                    onClick = onExport,
                    headlineContent = { Text("导出数据库") },
                    supportingContent = { Text("把这份快照的 .db 复制到所选位置。") },
                    leadingContent = { HubCardIcon(HugeIcons.FileExport) },
                )
                item(
                    onClick = if (target.build.supported && !busy) onRollback else null,
                    headlineContent = { Text("回滚到此") },
                    supportingContent = { Text("整库投递回原包，重启该应用后生效。") },
                    leadingContent = { HubCardIcon(HugeIcons.Refresh01) },
                )
                item(
                    onClick = if (busy) null else onDelete,
                    headlineContent = { Text("删除快照") },
                    supportingContent = { Text("只删数据中心里的这份副本，不影响原应用。") },
                    leadingContent = { HubCardIcon(HugeIcons.Delete01) },
                )
            }
        }
    }
}

private fun snapshotKindLabel(kind: SnapshotKind): String = when (kind) {
    SnapshotKind.AUTO -> "自动"
    SnapshotKind.MANUAL -> "手动"
    SnapshotKind.PRE_SYNC -> "同步前"
    SnapshotKind.IMPORTED -> "导入"
}
