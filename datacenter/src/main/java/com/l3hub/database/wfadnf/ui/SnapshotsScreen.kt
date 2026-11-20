package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.SnapshotKind
import com.l3hub.database.wfadnf.core.SnapshotRecord
import com.l3hub.database.wfadnf.core.SyncEngine
import com.l3hub.database.wfadnf.core.formatTime

@Composable
fun SnapshotsScreen(state: HubUiState, controller: HubController) {
    val groups = state.builds.filter { it.snapshots.isNotEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "每个包各留一份仓库：快照记录当时的库版本与行数，回滚就是把它重新投递回原包。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (groups.isEmpty()) {
            item { EmptyHint("还没有快照。到「构建」页拉一份。") }
        }
        groups.forEach { build ->
            item(key = "header-${build.packageName}") { GroupHeader(build) }
            items(build.snapshots, key = { "${build.packageName}/${it.id}" }) { snapshot ->
                SnapshotCard(
                    build = build,
                    snapshot = snapshot,
                    busy = state.busyLabel != null,
                    onRollback = { controller.rollback(build.packageName, snapshot.id) },
                    onDelete = { controller.deleteSnapshot(build.packageName, snapshot.id) },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GroupHeader(build: BuildUi) {
    Column {
        Text(build.label, style = MaterialTheme.typography.titleSmall)
        Text(
            "${build.packageName} · ${build.snapshots.size} 份快照",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SnapshotCard(
    build: BuildUi,
    snapshot: SnapshotRecord,
    busy: Boolean,
    onRollback: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    snapshotKindLabel(snapshot.kind),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    snapshot.createdAt.formatTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "库 v${snapshot.schemaVersion} · 会话 ${snapshot.conversations} · " +
                    "消息 ${snapshot.messages} · ${SyncEngine.formatBytes(snapshot.databaseBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (snapshot.filesIncluded) {
                Text(
                    "含附件镜像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRollback, enabled = build.supported && !busy) {
                    Text("回滚到此")
                }
                TextButton(onClick = onDelete, enabled = !busy) { Text("删除") }
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
