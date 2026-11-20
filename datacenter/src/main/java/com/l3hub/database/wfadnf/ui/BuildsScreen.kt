package com.l3hub.database.wfadnf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.l3hub.database.wfadnf.core.BuildUi
import com.l3hub.database.wfadnf.core.ExportRequest
import com.l3hub.database.wfadnf.core.HubController
import com.l3hub.database.wfadnf.core.HubUiState
import com.l3hub.database.wfadnf.core.SyncEngine
import com.l3hub.database.wfadnf.ui.components.HubActionIcon
import com.l3hub.database.wfadnf.ui.components.HubCardGroup
import com.l3hub.database.wfadnf.ui.components.HubCardIcon
import com.l3hub.database.wfadnf.ui.components.HubNote
import com.l3hub.database.wfadnf.ui.components.HubStatusPill
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.SmartPhone01

@Composable
fun BuildsScreen(
    state: HubUiState,
    controller: HubController,
    onExport: (ExportRequest) -> Unit,
    onOpenDatabase: (DbSelection) -> Unit,
) {
    var includeFiles by rememberSaveable { mutableStateOf(false) }
    val busy = state.busyLabel != null
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HubCardGroup(title = "本机") {
                formItem {
                    Text(
                        "${state.builds.count { it.installed }} 个已安装 · 仓库占用 " +
                            SyncEngine.formatBytes(state.storageBytes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HubNote(
                        if (state.scanning) {
                            "正在扫描本机安装的 L3Hub 构建…"
                        } else {
                            "扫描会找正式版、Canary、Nightly，以及沿用同一套同步协议、改过包名的分支。"
                        },
                    )
                }
            }
        }
        item {
            HubCardGroup(title = "拉取选项") {
                switchItem(
                    checked = includeFiles,
                    onCheckedChange = { includeFiles = it },
                    headlineContent = { Text("同时镜像附件") },
                    supportingContent = {
                        Text("按清单与大小差异只取变化的部分；关掉只存数据库。")
                    },
                    leadingContent = { HubCardIcon(HugeIcons.Download04) },
                )
            }
        }
        items(state.builds, key = { it.packageName }) { build ->
            BuildGroup(
                build = build,
                busy = busy,
                includeFiles = includeFiles,
                onPull = { controller.pull(build.packageName, includeFiles) },
                onExport = onExport,
                onOpenDatabase = onOpenDatabase,
                onClearMirror = { controller.clearMirror(build.packageName) },
            )
        }
        if (!state.loading && state.builds.isEmpty()) {
            item { HubNote("本机没有找到 L3Hub。装一个再回来，或点右上角重新扫描。") }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun BuildGroup(
    build: BuildUi,
    busy: Boolean,
    includeFiles: Boolean,
    onPull: () -> Unit,
    onExport: (ExportRequest) -> Unit,
    onOpenDatabase: (DbSelection) -> Unit,
    onClearMirror: () -> Unit,
) {
    val status = statusOf(build)
    val enabled = build.supported && !busy
    val latestSnapshot = build.snapshots.maxByOrNull { it.createdAt }
    HubCardGroup(title = build.label) {
        item(
            headlineContent = {
                Text(
                    "${build.versionName.ifBlank { "—" }}（${build.versionCode}）",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            supportingContent = { Text(build.packageName) },
            leadingContent = { HubCardIcon(HugeIcons.SmartPhone01) },
            trailingContent = { HubStatusPill(status.text, status.container, status.content) },
        )
        formItem {
            Text(
                "库 v${build.schemaVersion} · 数据库 ${SyncEngine.formatBytes(build.databaseBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (build.counts.isNotEmpty()) {
                Text(
                    "会话 ${build.counts["ConversationEntity"] ?: 0} · " +
                        "消息 ${build.counts["message_node"] ?: 0} · " +
                        "工作区 ${build.counts["workspaces"] ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HubNote(
                "快照 ${build.snapshots.size} 份 · 附件 ${build.mirrorFiles} 个" +
                    "（${SyncEngine.formatBytes(build.mirrorBytes)}）",
            )
            build.reason?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item(
            onClick = if (enabled) onPull else null,
            headlineContent = { Text(if (includeFiles) "拉取快照与附件" else "拉取快照") },
            supportingContent = { Text("把整份数据库落到数据中心的仓库，作为回滚点与同步源。") },
            leadingContent = { HubActionIcon(HugeIcons.Download04) },
        )
        item(
            onClick = if (build.installed) {
                { onExport(ExportRequest(build.packageName, null, build.label)) }
            } else {
                null
            },
            headlineContent = { Text("导出数据库") },
            supportingContent = { Text("整库导出为 .db；运行中的库会先拉一份快照再交给所选位置。") },
            leadingContent = { HubActionIcon(HugeIcons.FileExport) },
        )
        item(
            onClick = if (build.schema != null || latestSnapshot != null) {
                { onOpenDatabase(DbSelection(build.packageName, latestSnapshot?.id)) }
            } else {
                null
            },
            headlineContent = { Text("查看数据库") },
            supportingContent = {
                Text(
                    if (latestSnapshot != null) {
                        "表结构、会话与附件；默认打开最近一份快照。"
                    } else {
                        "表结构与行数；拉一份快照后才能看会话与附件。"
                    }
                )
            },
            leadingContent = { HubCardIcon(HugeIcons.File02) },
        )
        item(
            onClick = if (busy || build.mirrorFiles == 0) null else onClearMirror,
            visible = build.mirrorFiles > 0,
            headlineContent = { Text("清空附件镜像") },
            supportingContent = {
                Text("${SyncEngine.formatBytes(build.mirrorBytes)}，删掉后下次拉取会重新下载。")
            },
            leadingContent = { HubCardIcon(HugeIcons.Delete01) },
        )
    }
}

private data class StatusStyle(val text: String, val container: Color, val content: Color)

@Composable
private fun statusOf(build: BuildUi): StatusStyle {
    val scheme = MaterialTheme.colorScheme
    return when {
        !build.installed -> StatusStyle("未安装", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        !build.supported -> StatusStyle("不可用", scheme.errorContainer, scheme.onErrorContainer)
        build.pendingRestore -> StatusStyle("待重启", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> StatusStyle("可同步", scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}
