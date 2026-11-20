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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.SyncEngine

@Composable
fun BuildsScreen(state: HubUiState, controller: HubController) {
    var includeFiles by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(state) }
        item {
            OptionsCard(includeFiles = includeFiles, onIncludeFiles = { includeFiles = it })
        }
        if (state.loading) {
            item { Text("正在扫描本机…", style = MaterialTheme.typography.bodyMedium) }
        }
        items(state.builds, key = { it.packageName }) { build ->
            BuildCard(
                build = build,
                busy = state.busyLabel != null,
                includeFiles = includeFiles,
                onPull = { controller.pull(build.packageName, includeFiles) },
                onClearMirror = { controller.clearMirror(build.packageName) },
            )
        }
        if (!state.loading && state.builds.isEmpty()) {
            item { EmptyHint("没有找到 L3Hub。装一个再回来。") }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SummaryCard(state: HubUiState) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("本机的 L3Hub 构建", style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.builds.count { it.installed }} 个已安装 · 仓库占用 ${SyncEngine.formatBytes(state.storageBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionsCard(includeFiles: Boolean, onIncludeFiles: (Boolean) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("拉取时同时镜像附件", style = MaterialTheme.typography.titleSmall)
                Text(
                    "附件按清单与大小差异增量拉取；关掉只存数据库。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = includeFiles, onCheckedChange = onIncludeFiles)
        }
    }
}

@Composable
private fun BuildCard(
    build: BuildUi,
    busy: Boolean,
    includeFiles: Boolean,
    onPull: () -> Unit,
    onClearMirror: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        build.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        build.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusBadge(build)
            }
            Text(
                "版本 ${build.versionName.ifBlank { "—" }}（${build.versionCode}）· 库 v${build.schemaVersion}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (build.counts.isNotEmpty()) {
                Text(
                    "会话 ${build.counts["ConversationEntity"] ?: 0} · " +
                        "消息 ${build.counts["message_node"] ?: 0} · " +
                        "工作区 ${build.counts["workspaces"] ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "数据库 ${SyncEngine.formatBytes(build.databaseBytes)} · 快照 ${build.snapshots.size} 份 · " +
                    "附件 ${build.mirrorFiles} 个（${SyncEngine.formatBytes(build.mirrorBytes)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            build.reason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPull, enabled = build.supported && !busy) {
                    Text(if (includeFiles) "拉取快照与附件" else "拉取快照")
                }
                if (build.mirrorFiles > 0) {
                    TextButton(onClick = onClearMirror, enabled = !busy) { Text("清空附件镜像") }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(build: BuildUi) {
    val text: String
    val color: androidx.compose.ui.graphics.Color
    when {
        !build.installed -> {
            text = "未安装"
            color = MaterialTheme.colorScheme.surfaceVariant
        }
        !build.supported -> {
            text = "不可用"
            color = MaterialTheme.colorScheme.errorContainer
        }
        build.pendingRestore -> {
            text = "待重启"
            color = MaterialTheme.colorScheme.tertiaryContainer
        }
        else -> {
            text = "可同步"
            color = MaterialTheme.colorScheme.secondaryContainer
        }
    }
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
