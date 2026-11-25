package me.rerere.rikkahub.ui.components.message

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Play
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.ui.components.ui.VideoPlayerDialog
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.modifier.shimmer
import me.rerere.ui.toast.ToastType
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Composable
internal fun ChatMessageVideoCard(
    url: String,
    modifier: Modifier = Modifier,
) {
    val playLabel = stringResource(R.string.chat_media_video_play)
    val local = remember(url) { url.startsWith('/') || url.startsWith("file:", ignoreCase = true) }
    ChatVideoAttachment(url) { startPlayback, thumbnail, loading ->
        val labeledModifier = modifier.semantics {
            onClick(label = playLabel) { startPlayback(); true }
        }
        if (!local) {
            ChatMessageMediaTile(icon = HugeIcons.Video01, onClick = startPlayback, modifier = labeledModifier)
            return@ChatVideoAttachment
        }
        Surface(
            modifier = labeledModifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = startPlayback,
        ) {
            Box(
                modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer).shimmer(loading))
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = .45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HugeIcons.Play, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** 用户附件和助手视频共用取帧与播放逻辑，附件自己的点击区域和长按手势由调用方管理。 */
@Composable
internal fun ChatVideoAttachment(
    url: String,
    content: @Composable (play: () -> Unit, thumbnail: File?, loading: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val uri = remember(url) { url.takeUnless { it.isBlank() }?.toUri()?.normalizeScheme() }
    key(context, url) {
        val scope = rememberCoroutineScope()
        var playing by remember { mutableStateOf<Uri?>(null) }
        var preparing by remember { mutableStateOf(false) }
        val preview by produceState(VideoPreview(loading = true)) {
            val thumbnail = withContext(Dispatchers.IO) {
                uri?.let { attachmentFile(context, it) }?.let { loadVideoThumbnailFile(context, it) }
            }
            value = VideoPreview(loading = false, thumbnail = thumbnail)
        }
        val play: () -> Unit = {
            if (!preparing && playing == null) {
                preparing = true
                scope.launch {
                    try {
                        val local = withContext(Dispatchers.IO) { uri?.let { resolvePlayback(context, it) } }
                        if (local != null) {
                            playing = local
                        } else if (uri == null || !uri.isRemoteVideo() || !launchVideoView(context, uri)) {
                            toaster.show(context.getString(R.string.video_player_error), type = ToastType.Error)
                        }
                    } finally {
                        preparing = false
                    }
                }
            }
        }
        content(play, preview.thumbnail, preview.loading)
        playing?.let { VideoPlayerDialog(mediaUri = it, onDismissRequest = { playing = null }) }
    }
}

private data class VideoPreview(val loading: Boolean, val thumbnail: File? = null)

/** Only a direct, readable video attachment can be sampled automatically. */
private fun attachmentFile(context: Context, uri: Uri): File? = runCatching {
    if (uri.scheme != null && uri.scheme != ContentResolver.SCHEME_FILE) return@runCatching null
    if (!uri.authority.isNullOrEmpty() || uri.query != null || uri.fragment != null) return@runCatching null
    val path = uri.path?.takeIf { it.startsWith('/') } ?: return@runCatching null
    val upload = context.filesDir.canonicalFile.resolve(FileFolders.UPLOAD)
    if (upload.canonicalFile != upload) return@runCatching null
    val file = File(path).canonicalFile
    file.takeIf { it.parentFile == upload && it.isFile && it.canRead() && it.length() > 0 }
}.getOrNull()

private fun resolvePlayback(context: Context, uri: Uri): Uri? = when (uri.scheme) {
    ContentResolver.SCHEME_FILE, null -> attachmentFile(context, uri)?.let(Uri::fromFile)
    ContentResolver.SCHEME_CONTENT -> uri.takeIf {
        !it.authority.isNullOrBlank() && it.authority != "${context.packageName}.fileprovider" &&
            runCatching {
                context.checkUriPermission(
                    it, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION,
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
    }
    else -> null
}

private fun Uri.isRemoteVideo(): Boolean =
    (scheme == "http" || scheme == "https") && !host.isNullOrBlank()

/** The app hands remote URLs to a video handler only in response to a click. */
private fun launchVideoView(context: Context, uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
    return runCatching { context.startActivity(Intent.createChooser(intent, null)) }.isSuccess
}

/**
 * 抽一帧写到本地缓存。API 26 没有缩放抽帧接口，保留视频图标。
 */
private fun loadVideoThumbnailFile(context: Context, file: File): File? = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@runCatching null
    val cacheKey = "${file.absolutePath}|${file.lastModified()}|${file.length()}"
    writeThumbnailFile(context, file, cacheKey)
}.getOrNull()

private fun writeThumbnailFile(context: Context, video: File, cacheKey: String): File? =
    synchronized(VideoThumbnailLock) {
        val directory = File(context.cacheDir, ThumbnailCacheDir).apply { mkdirs() }
        val target = File(directory, "${sha256(cacheKey)}.jpg")
        if (target.isFile && target.length() > 0L) {
            return@synchronized target
        }
        // 先写临时文件再改名：抽帧失败时不会留下半张图被当成有效封面
        val temp = File(directory, "${target.name}.tmp")
        temp.delete()
        val extracted = runCatching { extractFirstFrame(video, temp) }.getOrDefault(false)
        if (!extracted || !temp.isFile || temp.length() == 0L) {
            temp.delete()
            pruneThumbnailCache(directory, keep = null)
            return@synchronized null
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            return@synchronized null
        }
        pruneThumbnailCache(directory, keep = target)
        target
    }

private fun extractFirstFrame(video: File, target: File): Boolean {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(video.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: 480
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()?.coerceAtLeast(1) ?: 270
        val scale = (ThumbnailMaxEdge / maxOf(width, height)).coerceAtMost(1f)
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false
        val frame = retriever.getScaledFrameAtTime(
            0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, scaledWidth, scaledHeight,
        ) ?: return false
        return try {
            target.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, ThumbnailQuality, it) }
        } finally {
            frame.recycle()
        }
    } catch (_: RuntimeException) {
        // 解不了的容器：当作没有封面，卡片留在图标态。不写日志——URL 可能是签名地址
        return false
    } catch (_: IOException) {
        // 写缓存失败（空间不足等）：同样当作没有封面
        return false
    } finally {
        runCatching { retriever.release() }
    }
}

/** 封面缓存超过上限时按最旧优先删，别让它无限长。 */
private fun pruneThumbnailCache(directory: File, keep: File?) {
    val files = directory.listFiles().orEmpty().filter { it.isFile }
    var total = files.sumOf { it.length() }
    if (total <= ThumbnailCacheBytes) return
    files.sortedBy { it.lastModified() }.forEach { file ->
        if (total <= ThumbnailCacheBytes || file == keep) return@forEach
        val size = file.length()
        if (file.delete()) total -= size
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

private const val ThumbnailCacheDir = "video-thumbnails"
private const val ThumbnailMaxEdge = 480f
private const val ThumbnailQuality = 80
private const val ThumbnailCacheBytes = 32L * 1024 * 1024

private object VideoThumbnailLock
