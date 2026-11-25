package me.rerere.rikkahub.ui.components.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.OutlinedButton
import me.rerere.ui.components.AnimatedDialog
import java.io.File

/**
 * 全屏循环播放一个本地视频文件。设备解不了这个视频时给出提示，并可以交给其他应用打开。
 */
@Composable
fun VideoPlayerDialog(
    file: File,
    onDismissRequest: () -> Unit,
) {
    VideoPlayerDialogContent(
        mediaUri = Uri.fromFile(file),
        fallbackFile = file,
        onDismissRequest = onDismissRequest,
    )
}

/**
 * 同一个播放器，直接播一条 [mediaUri]（content:// 用）。
 *
 * 不把内容复制成缓存文件：播放器自己按 URI 读，省掉一份可能很大的临时副本。
 * content:// 能播的前提是当前确实握着这条 URI 的读权限，调用方负责确认。
 */
@Composable
internal fun VideoPlayerDialog(
    mediaUri: Uri,
    onDismissRequest: () -> Unit,
) {
    VideoPlayerDialogContent(
        mediaUri = mediaUri,
        fallbackFile = null,
        onDismissRequest = onDismissRequest,
    )
}

@OptIn(UnstableApi::class)
private class VideoDialogPlayback {
    var player by mutableStateOf<ExoPlayer?>(null)
    var errorCode by mutableStateOf<String?>(null)
    var view: PlayerView? = null
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerDialogContent(
    mediaUri: Uri,
    fallbackFile: File?,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val playback = remember(context, mediaUri, lifecycle) { VideoDialogPlayback() }
    DisposableEffect(playback) {
        var player: ExoPlayer? = null
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                playback.errorCode = playbackError.errorCodeName
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player?.pause()
        }
        val dataSource: DataSource.Factory? = when (mediaUri.scheme) {
            ContentResolver.SCHEME_FILE -> FileDataSource.Factory()
            ContentResolver.SCHEME_CONTENT -> DataSource.Factory { ContentDataSource(context) }
            else -> null
        }
        if (dataSource == null) {
            playback.errorCode = ""
        } else {
            try {
                // Attachments are single local streams, never playlists inferred from the URI suffix.
                val source = ProgressiveMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(mediaUri))
                val instance = ExoPlayer.Builder(context, MediaSource.Factory.UNSUPPORTED).build()
                player = instance
                instance.addListener(listener)
                instance.setMediaSource(source)
                instance.repeatMode = Player.REPEAT_MODE_ONE
                instance.playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                instance.prepare()
                playback.player = instance
                lifecycle.addObserver(observer)
            } catch (_: RuntimeException) {
                playback.errorCode = ""
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            playback.view?.player = null
            playback.view = null
            player?.removeListener(listener)
            player?.release()
        }
    }

    AnimatedDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding(),
        ) {
            val error = playback.errorCode
            if (error == null) {
                playback.player?.let { player ->
                    key(playback) {
                        AndroidView(
                            factory = { PlayerView(it).apply { setEnableComposeSurfaceSyncWorkaround(true) } },
                            modifier = Modifier.fillMaxSize(),
                            update = { view -> playback.view = view; view.player = player },
                            onRelease = { view ->
                                view.player = null
                                if (playback.view === view) playback.view = null
                            },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.video_player_error).let {
                            if (error.isBlank()) it else "$it\n$error"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = {
                            shareableUri(context, mediaUri, fallbackFile)?.let { shareUri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(shareUri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.video_player_open_with), color = Color.White)
                    }
                }
            }
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(HugeIcons.Cancel01, stringResource(R.string.cancel), tint = Color.White)
            }
        }
    }
}

/**
 * 交给外部应用时用的 URI。
 *
 * 文件走 FileProvider（file:// 直接发出去在 7.0+ 会被拦）；content:// 原样传，权限靠
 * FLAG_GRANT_READ_URI_PERMISSION 转授；其余 scheme 不发。
 */
private fun shareableUri(context: Context, mediaUri: Uri, fallbackFile: File?): Uri? =
    when (mediaUri.scheme) {
        ContentResolver.SCHEME_FILE -> {
            val file = fallbackFile ?: mediaUri.path?.let { File(it) }
            file?.let {
                runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
                }.getOrNull()
            }
        }

        ContentResolver.SCHEME_CONTENT -> mediaUri
        else -> null
    }
