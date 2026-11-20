package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.l3hub.database.wfadnf.core.SnapshotRecord
import com.l3hub.database.wfadnf.core.SyncMode
import com.l3hub.database.wfadnf.core.formatTime

private data class SourceOption(val build: BuildUi, val snapshot: SnapshotRecord) {
    val key: String get() = "${build.packageName}/${snapshot.id}"
    val label: String get() = "${build.label} · ${snapshot.createdAt.formatTime()} · v${snapshot.schemaVersion}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(state: HubUiState, controller: HubController) {
    val sources = state.builds.flatMap { build -> build.snapshots.map { SourceOption(build, it) } }
    val targets = state.builds.filter { it.installed && it.supported }
    var sourceKey by rememberSaveable { mutableStateOf("") }
    var targetPackage by rememberSaveable { mutableStateOf("") }
    var modeName by rememberSaveable { mutableStateOf(SyncMode.MERGE.name) }
    var includeFiles by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(sources.size, targets.size) {
        if (sources.none { it.key == sourceKey }) sourceKey = sources.firstOrNull()?.key.orEmpty()
        if (targets.none { it.packageName == targetPackage }) targetPackage = targets.firstOrNull()?.packageName.orEmpty()
    }

    val source = sources.firstOrNull { it.key == sourceKey }
    val target = targets.firstOrNull { it.packageName == targetPackage }
    val mode = SyncMode.entries.firstOrNull { it.name == modeName } ?: SyncMode.MERGE
    val structural = source != null && target != null && source.snapshot.schemaVersion != target.schemaVersion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("把一份快照投递到另一个构建", style = MaterialTheme.typography.titleMedium)
        Text(
            "同结构直接搬运；结构不同先按目标的结构重建，再按合并或替换落到目标。" +
                "投递后需要重启目标应用才会安装。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PickerField(
            label = "源快照",
            value = source?.label ?: "没有可用快照",
            options = sources.map { it.key to it.label },
            onSelect = { sourceKey = it },
        )
        PickerField(
            label = "目标构建",
            value = target?.let { "${it.label} · v${it.schemaVersion}" } ?: "没有可投递的构建",
            options = targets.map { it.packageName to "${it.label} · v${it.schemaVersion}" },
            onSelect = { targetPackage = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == SyncMode.MERGE,
                onClick = { modeName = SyncMode.MERGE.name },
                label = { Text("合并") },
            )
            FilterChip(
                selected = mode == SyncMode.OVERWRITE,
                onClick = { modeName = SyncMode.OVERWRITE.name },
                label = { Text("替换") },
            )
        }
        Text(
            when (mode) {
                SyncMode.MERGE -> "合并：先拉一份目标现状作回滚点，对端较新的会话整条替换，本地缺的补进来，删除墓碑照常生效。"
                SyncMode.OVERWRITE -> "替换：目标整库换成这份快照；附件一并投递时，目标里多出来的附件也会清掉。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("同时同步附件", style = MaterialTheme.typography.titleSmall)
                Text(
                    "附件从数据中心的镜像里补；合并模式只补缺，替换模式以镜像为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = includeFiles, onCheckedChange = { includeFiles = it })
        }

        if (source != null && target != null) {
            PreviewCard(source, target, structural)
        }

        Button(
            onClick = {
                val current = source ?: return@Button
                controller.push(
                    sourcePackage = current.build.packageName,
                    snapshotId = current.snapshot.id,
                    targetPackage = targetPackage,
                    mode = mode,
                    includeFiles = includeFiles,
                )
            },
            enabled = source != null && target != null && state.busyLabel == null,
        ) {
            Text(if (state.busyLabel == null) "开始同步" else state.busyLabel.orEmpty())
        }

        if (state.log.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("过程", style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    state.log.takeLast(10).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PreviewCard(source: SourceOption, target: BuildUi, structural: Boolean) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("将要投递", style = MaterialTheme.typography.titleSmall)
            Text(
                "会话 ${source.snapshot.conversations} · 消息 ${source.snapshot.messages} · " +
                    "库 ${source.snapshot.schemaVersion}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "目标现有会话 ${target.counts["ConversationEntity"] ?: 0} · " +
                    "消息 ${target.counts["message_node"] ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (structural) {
                    "结构不同：v${source.snapshot.schemaVersion} → v${target.schemaVersion}，会先换算再投递。"
                } else {
                    "两边结构一致，直接搬运。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (structural) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = options.isNotEmpty(),
        ) {
            Text(
                "$label：$value",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}
