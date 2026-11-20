package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.SnapshotRecord
import com.l3hub.database.wfadnf.core.SyncEngine
import com.l3hub.database.wfadnf.core.SyncMode
import com.l3hub.database.wfadnf.core.formatTime
import com.l3hub.database.wfadnf.ui.components.HubActionIcon
import com.l3hub.database.wfadnf.ui.components.HubCardGroup
import com.l3hub.database.wfadnf.ui.components.HubCardIcon
import com.l3hub.database.wfadnf.ui.components.HubNote
import com.l3hub.database.wfadnf.ui.components.HubStatusPill
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock01
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.SmartPhone01

private data class SyncSource(val build: BuildUi, val snapshot: SnapshotRecord) {
    val key: String get() = "${build.packageName}/${snapshot.id}"
}

@Composable
fun SyncScreen(state: HubUiState, controller: HubController) {
    val sources = state.builds.flatMap { build -> build.snapshots.map { SyncSource(build, it) } }
        .sortedByDescending { it.snapshot.createdAt }
    val targets = state.builds.filter { it.installed }
    var sourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var targetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var modeName by rememberSaveable { mutableStateOf(SyncMode.MERGE.name) }
    var includeFiles by rememberSaveable { mutableStateOf(true) }
    val busy = state.busyLabel != null

    val source = sources.firstOrNull { it.key == sourceKey } ?: sources.firstOrNull()
    val target = targets.firstOrNull { it.packageName == targetPackage }
        ?: targets.firstOrNull { it.supported && it.packageName != source?.build?.packageName }
        ?: targets.firstOrNull { it.supported }
    val mode = runCatching { SyncMode.valueOf(modeName) }.getOrDefault(SyncMode.MERGE)
    val chosenSource = source
    val chosenTarget = target
    val hint = if (chosenSource == null) {
        "先选一份源快照。"
    } else if (chosenTarget == null) {
        "先选目标构建。"
    } else if (!chosenTarget.supported) {
        "目标不支持结构导出，无法接收这份数据。"
    } else if (chosenTarget.pendingRestore) {
        "目标有一份数据等着安装，先打开它完成安装。"
    } else {
        "把 ${chosenSource.snapshot.createdAt.formatTime()} 的快照投递给 ${chosenTarget.label}。"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HubNote(
                "同步在两边的副本上做：先把源快照换算成目标库的结构，再按合并或替换落到目标。" +
                    "目标会在下次启动、数据库打开之前安装，完成后需要重启那个应用。",
            )
        }
        item {
            HubCardGroup(title = "源快照") {
                formItem(visible = sources.isEmpty()) { HubNote("还没有快照。到「构建」页拉一份。") }
                sources.forEach { entry ->
                    item(
                        onClick = { sourceKey = entry.key },
                        headlineContent = {
                            Text(
                                "${entry.build.label} · ${entry.snapshot.createdAt.formatTime()}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                "库 v${entry.snapshot.schemaVersion} · 会话 ${entry.snapshot.conversations} · " +
                                    "${SyncEngine.formatBytes(entry.snapshot.databaseBytes)}",
                            )
                        },
                        leadingContent = { HubCardIcon(HugeIcons.Clock01) },
                        trailingContent = {
                            if (source?.key == entry.key) HubStatusPill("已选") else null
                        },
                    )
                }
            }
        }
        item {
            HubCardGroup(title = "目标构建") {
                formItem(visible = targets.isEmpty()) { HubNote("本机没有可接收的构建。") }
                targets.forEach { build ->
                    item(
                        onClick = if (build.supported) {
                            { targetPackage = build.packageName }
                        } else {
                            null
                        },
                        headlineContent = { Text(build.label) },
                        supportingContent = {
                            Text(
                                build.packageName + " · 库 v" + build.schemaVersion +
                                    if (build.pendingRestore) " · 有一份数据等着安装" else "",
                            )
                        },
                        leadingContent = { HubCardIcon(HugeIcons.SmartPhone01) },
                        trailingContent = {
                            when {
                                target?.packageName == build.packageName -> HubStatusPill("已选")
                                !build.supported -> HubStatusPill("不可用")
                                build.pendingRestore -> HubStatusPill("待重启")
                                else -> null
                            }
                        },
                    )
                }
            }
        }
        item {
            HubCardGroup(title = "方式") {
                item(
                    onClick = { modeName = SyncMode.MERGE.name },
                    headlineContent = { Text("合并") },
                    supportingContent = { Text("保留目标现有数据，按主键补缺、对端较新的整条替换。") },
                    leadingContent = { HubCardIcon(HugeIcons.File02) },
                    trailingContent = {
                        if (mode == SyncMode.MERGE) HubStatusPill("已选") else null
                    },
                )
                item(
                    onClick = { modeName = SyncMode.OVERWRITE.name },
                    headlineContent = { Text("替换") },
                    supportingContent = { Text("目标整库换成这份快照；附件以镜像为准。") },
                    leadingContent = { HubCardIcon(HugeIcons.Refresh01) },
                    trailingContent = {
                        if (mode == SyncMode.OVERWRITE) HubStatusPill("已选") else null
                    },
                )
                switchItem(
                    checked = includeFiles,
                    onCheckedChange = { includeFiles = it },
                    headlineContent = { Text("一并投递附件") },
                    supportingContent = { Text("只补目标缺的文件；替换模式以镜像为准。") },
                    leadingContent = { HubCardIcon(HugeIcons.File02) },
                )
            }
        }
        item {
            HubCardGroup(title = "执行") {
                item(
                    onClick = chosenSource?.let { src ->
                        chosenTarget?.takeIf { it.supported && !it.pendingRestore && !busy }?.let { tgt ->
                            {
                                controller.push(
                                    sourcePackage = src.build.packageName,
                                    snapshotId = src.snapshot.id,
                                    targetPackage = tgt.packageName,
                                    mode = mode,
                                    includeFiles = includeFiles,
                                )
                            }
                        }
                    },
                    headlineContent = { Text("开始同步") },
                    supportingContent = { Text(hint) },
                    leadingContent = { HubActionIcon(HugeIcons.Refresh01, loading = busy) },
                )
                formItem(visible = state.log.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.log.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
