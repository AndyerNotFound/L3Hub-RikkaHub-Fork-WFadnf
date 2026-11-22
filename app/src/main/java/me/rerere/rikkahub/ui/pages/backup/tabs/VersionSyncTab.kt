package me.rerere.rikkahub.ui.pages.backup.tabs

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moriafly.salt.ui.ItemContainer
import com.moriafly.salt.ui.SaltTheme
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.FileExport
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.RestoreItem
import me.rerere.rikkahub.data.sync.bridge.VersionSyncClient
import me.rerere.rikkahub.ui.components.ui.SaltGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.backup.VersionSyncVM
import me.rerere.rikkahub.ui.pages.backup.components.BackupActionIcon
import me.rerere.rikkahub.ui.pages.backup.components.BackupActionRow
import me.rerere.rikkahub.ui.pages.backup.components.RestoreOptionsDialog
import me.rerere.rikkahub.ui.pages.backup.components.restoreSkippedMessage
import me.rerere.ui.toast.ToastType
import org.koin.androidx.compose.koinViewModel

private enum class SyncDirection { PULL, PUSH }

private data class Working(
    val direction: SyncDirection,
    val progress: VersionSyncClient.Progress?,
)

/** 版本同步默认不同步设置：设置里带着 API Key、模型选择与界面偏好，跨版本覆盖容易把界面改乱 */
private val DEFAULT_SYNC_ITEMS = setOf(RestoreItem.CHAT_RECORDS, RestoreItem.FILES)

@Composable
fun VersionSyncTab(
    onShowRestartDialog: () -> Unit,
    vm: VersionSyncVM = koinViewModel(),
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val detecting by vm.detecting.collectAsStateWithLifecycle()
    var working by remember { mutableStateOf<Working?>(null) }
    var pendingDirection by remember { mutableStateOf<SyncDirection?>(null) }

    // 正式版与预览版只会装着一个对端；两个都没装或都装不上时这里是 null
    val peer = peers.firstOrNull()
    val busy = working != null
    val ready = peer != null && peer.supported && !peer.pendingRestore && !busy
    val onRefresh: () -> Unit = { vm.refresh() }
    val onPull: () -> Unit = { pendingDirection = SyncDirection.PULL }
    val onPush: () -> Unit = { pendingDirection = SyncDirection.PUSH }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 横向内距交给 Salt 的 RoundedColumn（自带 16dp），这里不再补
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SaltGroup(
            title = stringResource(R.string.backup_page_version_sync_peer_title),
        ) {
            BackupActionRow(
                icon = HugeIcons.SmartPhone01,
                loading = detecting,
                headline = if (peer == null) {
                    stringResource(R.string.backup_page_version_sync_absent)
                } else if (peer.isPreview) {
                    stringResource(R.string.backup_page_version_sync_preview)
                } else {
                    stringResource(R.string.backup_page_version_sync_release)
                },
                supporting = when {
                    peer == null -> stringResource(R.string.backup_page_version_sync_absent_desc)
                    !peer.supported -> stringResource(R.string.backup_page_version_sync_unsupported)
                    peer.pendingRestore -> stringResource(R.string.backup_page_version_sync_peer_pending)
                    else -> stringResource(
                        R.string.backup_page_version_sync_peer_detail,
                        peer.versionName,
                        peer.schemaVersion,
                    )
                },
                onClick = if (detecting) null else onRefresh,
                // 探测中时右侧换成进度圈
                trailing = { BackupActionIcon(HugeIcons.Refresh, loading = detecting) },
            )
        }

        SaltGroup(
            title = stringResource(R.string.backup_page_version_sync),
        ) {
            BackupActionRow(
                icon = HugeIcons.FileImport,
                loading = working?.direction == SyncDirection.PULL,
                headline = stringResource(R.string.backup_page_version_sync_pull),
                supporting = stringResource(R.string.backup_page_version_sync_pull_desc),
                onClick = if (ready) onPull else null,
            )
            BackupActionRow(
                icon = HugeIcons.FileExport,
                loading = working?.direction == SyncDirection.PUSH,
                headline = stringResource(R.string.backup_page_version_sync_push),
                supporting = stringResource(R.string.backup_page_version_sync_push_desc),
                onClick = if (ready) onPush else null,
            )
            // 同步进行中时把进度条接在下面
            val state = working
            if (state != null) {
                ItemContainer {
                    Column {
                        val progress = state.progress
                        Text(
                            text = syncPhaseText(context, state.direction, progress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val fraction = progress?.let(::progressFraction)
                        if (fraction == null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            ItemContainer {
                Text(
                    text = stringResource(R.string.backup_page_version_sync_merge_note),
                    color = SaltTheme.colors.subText,
                    style = SaltTheme.textStyles.sub,
                )
            }
        }
    }

    pendingDirection?.let { direction ->
        RestoreOptionsDialog(
            onDismissRequest = { pendingDirection = null },
            defaultItems = DEFAULT_SYNC_ITEMS,
            onConfirm = { options ->
                pendingDirection = null
                val target = peer ?: return@RestoreOptionsDialog
                scope.launch {
                    working = Working(direction, VersionSyncClient.Progress.Packing)
                    val result = if (direction == SyncDirection.PULL) {
                        vm.pull(target, options) { working = Working(direction, it) }
                    } else {
                        vm.push(target, options) { working = Working(direction, it) }
                    }
                    working = null
                    result.onSuccess { staged ->
                        restoreSkippedMessage(context, staged)?.let { message ->
                            toaster.show(message, type = ToastType.Warning)
                        }
                        if (direction == SyncDirection.PULL) {
                            toaster.show(
                                context.getString(R.string.backup_page_version_sync_pulled),
                                type = ToastType.Success,
                            )
                            onShowRestartDialog()
                        } else {
                            toaster.show(
                                context.getString(R.string.backup_page_version_sync_pushed),
                                type = ToastType.Success,
                            )
                            vm.refresh()
                        }
                    }.onFailure { error ->
                        toaster.show(
                            context.getString(
                                R.string.backup_page_version_sync_failed,
                                syncFailureText(context, error),
                            ),
                            type = ToastType.Error,
                        )
                        vm.refresh()
                    }
                }
            },
        )
    }
}

private fun progressFraction(progress: VersionSyncClient.Progress): Float? = when (progress) {
    is VersionSyncClient.Progress.Copying -> when {
        progress.total <= 0L -> null
        else -> (progress.copied.toFloat() / progress.total).coerceIn(0f, 1f)
    }

    else -> null
}

private fun syncPhaseText(
    context: Context,
    direction: SyncDirection,
    progress: VersionSyncClient.Progress?,
): String = when (progress) {
    is VersionSyncClient.Progress.Copying -> context.getString(
        R.string.backup_page_version_sync_copying,
        Formatter.formatShortFileSize(context, progress.copied),
        Formatter.formatShortFileSize(context, progress.total),
    )

    is VersionSyncClient.Progress.Importing -> context.getString(
        if (direction == SyncDirection.PULL) {
            R.string.backup_page_version_sync_importing
        } else {
            R.string.backup_page_version_sync_handing_over
        }
    )

    else -> context.getString(R.string.backup_page_version_sync_packing)
}

private fun syncFailureText(context: Context, error: Throwable): String = when (error) {
    is VersionSyncClient.Failure.Unsupported ->
        context.getString(R.string.backup_page_version_sync_unsupported)

    is VersionSyncClient.Failure.PeerBusy ->
        context.getString(R.string.backup_page_version_sync_peer_pending)

    is VersionSyncClient.Failure.LocalBusy ->
        context.getString(R.string.backup_page_version_sync_local_pending)

    is VersionSyncClient.Failure.NewerSchema -> context.getString(
        R.string.backup_page_version_sync_schema_newer,
        error.peerVersion,
        error.localVersion,
    )

    is VersionSyncClient.Failure.Remote -> error.detail

    else -> error.message ?: error.javaClass.simpleName
}
