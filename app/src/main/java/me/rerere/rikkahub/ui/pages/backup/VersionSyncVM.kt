package me.rerere.rikkahub.ui.pages.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.sync.RestoreOptions
import me.rerere.rikkahub.data.sync.RestoreStageResult
import me.rerere.rikkahub.data.sync.bridge.VersionSyncClient

/** 版本同步页：对端概况的探测结果，以及两个方向的搬运入口。 */
class VersionSyncVM(
    private val client: VersionSyncClient,
) : ViewModel() {
    private val _peers = MutableStateFlow<List<VersionSyncClient.Peer>>(emptyList())
    val peers = _peers.asStateFlow()

    private val _detecting = MutableStateFlow(false)
    val detecting = _detecting.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_detecting.value) return
        viewModelScope.launch {
            _detecting.value = true
            _peers.value = runCatching { client.installedPeers() }.getOrDefault(emptyList())
            _detecting.value = false
        }
    }

    suspend fun pull(
        peer: VersionSyncClient.Peer,
        options: RestoreOptions,
        onProgress: (VersionSyncClient.Progress) -> Unit,
    ): Result<RestoreStageResult> = runCatching { client.pull(peer, options, onProgress) }

    suspend fun push(
        peer: VersionSyncClient.Peer,
        options: RestoreOptions,
        onProgress: (VersionSyncClient.Progress) -> Unit,
    ): Result<RestoreStageResult> = runCatching { client.push(peer, options, onProgress) }
}
