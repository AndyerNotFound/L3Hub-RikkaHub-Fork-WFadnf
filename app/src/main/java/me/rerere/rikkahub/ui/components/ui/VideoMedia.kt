package me.rerere.rikkahub.ui.components.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.android.Logging
import kotlin.math.max

private const val TAG = "VideoMedia"

private val thumbnailCache = object : LruCache<String, Bitmap>(12) {}

/**
 * 视频头像 / 背景：静音循环播放。画面按 ZOOM 方式铺满整块，圆角由外层裁剪。
 *
 * @param maxVideoSize 解码上限（长边），头像用小值省电；背景可以放宽
 * @param alpha 整体不透明度，直接设到 View 上，Compose 的 alpha 不一定管得到 TextureView
 * @param blurRadiusPx 模糊半径，Android 12 以下忽略
 * @param onError 解码或容器不支持时回调一次，调用方可以退回封面图
 */
@Composable
fun VideoSurface(
    model: String,
    modifier: Modifier = Modifier,
    maxVideoSize: Int = 512,
    alpha: Float = 1f,
    blurRadiusPx: Float = 0f,
    onError: ((PlaybackException) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uri = remember(model) { Uri.parse(model) }
    var failed by remember(uri) { mutableStateOf(false) }
    var videoSize by remember(uri) { mutableStateOf<VideoSize?>(null) }
    var textureView by remember(uri) { mutableStateOf<TextureView?>(null) }
    var viewport by remember(uri) { mutableStateOf(IntSize.Zero) }

    val player = remember(uri, maxVideoSize) {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(maxVideoSize, maxVideoSize)
                    .build()
            )
        }
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onPlayerError(error: PlaybackException) {
                Logging.log(TAG, "playback failed for $model: ${error.errorCodeName} ${error.message}")
                failed = true
                onError?.invoke(error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(textureView, videoSize, viewport, alpha, blurRadiusPx) {
        val view = textureView ?: return@LaunchedEffect
        view.alpha = alpha
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                if (blurRadiusPx > 0f) {
                    RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
        val size = videoSize ?: return@LaunchedEffect
        if (size.width <= 0 || size.height <= 0 || viewport.width <= 0 || viewport.height <= 0) {
            return@LaunchedEffect
        }
        // TextureView 默认把画面拉伸到整块，这里按原始比例反向修正成 ZOOM
        val scale = max(
            viewport.width.toFloat() / size.width,
            viewport.height.toFloat() / size.height,
        )
        val scaleX = size.width * scale / viewport.width
        val scaleY = size.height * scale / viewport.height
        view.setTransform(
            Matrix().apply {
                setScale(scaleX, scaleY, viewport.width / 2f, viewport.height / 2f)
            }
        )
    }

    if (failed) {
        VideoThumbnail(model = model, modifier = modifier)
        return
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                player.setVideoTextureView(this)
                textureView = this
            }
        },
        modifier = modifier.onSizeChanged { viewport = it },
    )
}

/** 视频封面：用一个静音解码器抓一帧，给太小的头像位置和不支持播放的容器兜底。 */
@Composable
fun VideoThumbnail(
    model: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val frame by produceState<ImageBitmap?>(initialValue = null, model) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadVideoThumbnail(context, model)?.asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = frame
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

private fun loadVideoThumbnail(context: android.content.Context, model: String): Bitmap? {
    thumbnailCache.get(model)?.let { return it }
    val uri = runCatching { Uri.parse(model) }.getOrNull() ?: return null
    if (uri.scheme != "file" && uri.scheme != "content") return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                512,
                512,
            )
        } else {
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } ?: retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        frame?.also { thumbnailCache.put(model, it) }
    } catch (e: Exception) {
        Logging.log(TAG, "thumbnail failed for $model: ${e.message}")
        null
    } finally {
        runCatching { retriever.release() }
    }
}
