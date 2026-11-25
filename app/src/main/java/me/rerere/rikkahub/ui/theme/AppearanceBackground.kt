package me.rerere.rikkahub.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.FileObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.AdvancedAppearanceSetting
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.ui.components.ui.resolvedLiveAppearanceRadius
import java.io.File
import kotlin.math.max

internal const val MAX_APPEARANCE_IMAGE_BYTES = 16 * 1024 * 1024
internal const val MAX_APPEARANCE_IMAGE_DIMENSION = 16_384

internal data class AppearanceImage(
    val file: File,
    val modifiedAt: Long,
    val byteCount: Long,
    val fileKey: Long,
    val colors: AppearanceImageColors,
)

internal class AppearanceImageState {
    var image by mutableStateOf<AppearanceImage?>(null)
    var resolved by mutableStateOf(false)
}

internal val LocalAppearanceImage = compositionLocalOf<AppearanceImageState?> { null }

// Store a relative path so restoring the upload directory on another device keeps the image usable.
// Never pass a preference/backup-supplied URL directly to an image loader.
private fun appearanceUploadDirectory(context: Context): File? {
    val directory = context.filesDir.canonicalFile.resolve(FileFolders.UPLOAD)
    return directory.takeIf { it.canonicalFile == it && it.isDirectory }
}

private fun loadAppearanceImage(context: Context, relativePath: String): AppearanceImage? = runCatching {
    val name = relativePath.removePrefix("${FileFolders.UPLOAD}/")
    if (name.isBlank() || name == relativePath || '/' in name || '\\' in name) return@runCatching null
    val upload = appearanceUploadDirectory(context) ?: return@runCatching null
    val file = upload.resolve(name).canonicalFile
    if (file.parentFile != upload || file.name != name || !file.isFile) return@runCatching null
    val size = file.length()
    if (size !in 1L..MAX_APPEARANCE_IMAGE_BYTES.toLong()) return@runCatching null
    val colors = sampleAppearanceImage { options -> BitmapFactory.decodeFile(file.path, options) }
        ?: return@runCatching null
    AppearanceImage(file, file.lastModified(), size, appearanceFileKey(file) ?: 0L, colors)
}.getOrNull()

// Device + inode of a path. It changes when the path is replaced, which is what a restored upload directory
// does, so it both detects a swapped directory and keeps a stale bitmap out of Coil's cache when the new file
// happens to have the same size and timestamp as the old one.
private fun appearanceFileKey(file: File): Long? = runCatching {
    val stat = android.system.Os.stat(file.path)
    stat.st_dev xor stat.st_ino
}.getOrNull()

// Watch one configured attachment for all panes so a storage cleanup also drops its tint and Haze source.
// The upload directory can be missing at first launch or swapped wholesale (storage restore): a FileObserver
// bound to a removed/replaced directory inode never fires again, so the parent directory re-arms it.
@Suppress("DEPRECATION") // FileObserver(String, Int) also supports Android 8 and 9.
private fun appearanceImageChanges(context: Context, path: String) = callbackFlow<Unit> {
    val name = path.substringAfterLast('/')
    // CLOSE_WRITE, not CREATE: a file copied back into the directory is only sampled once it is complete.
    val fileEvents = FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or FileObserver.DELETE or
        FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    val directoryEvents = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
        FileObserver.MOVED_TO or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    val selfEvents = FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
    // FileObserver callbacks run on their own thread, so arming/tearing down is serialized through this lock,
    // and re-arm requests are replayed on the producer coroutine instead of inside onEvent.
    val lock = Any()
    val rearm = Channel<Unit>(Channel.CONFLATED)
    var uploadObserver: FileObserver? = null
    var released = false

    /** (Re)arms the upload-directory observer and samples once, whether or not the directory exists. */
    fun watchUploadDirectory() {
        var sample = false
        synchronized(lock) {
            if (!released) {
                uploadObserver?.let { observer -> runCatching { observer.stopWatching() } }
                uploadObserver = null
                val directory = runCatching { appearanceUploadDirectory(context) }.getOrNull()
                if (directory != null) {
                    val observer = object : FileObserver(directory.path, fileEvents) {
                        override fun onEvent(event: Int, path: String?) {
                            val watchedInodeGone = (event and selfEvents) != 0
                            // The watched inode is gone: the directory was removed, moved away or replaced, and
                            // re-arming also samples, so a single path covers both the fallback and the recovery.
                            if (watchedInodeGone) rearm.trySend(Unit)
                            else if (path == null || path == name) trySend(Unit)
                        }
                    }
                    if (runCatching { observer.startWatching() }.isSuccess) uploadObserver = observer
                }
                sample = true
            }
        }
        // Sample right after arming, and also when the directory is absent or could not be watched: a file
        // written between the previous sample and this startWatching emits no event, and an absent directory
        // must resolve to the fallback instead of staying pending.
        if (sample) trySend(Unit)
    }

    // One observer on filesDir only follows the upload entry itself; unrelated files stay unwatched.
    // Re-arming samples too, so a single signal covers both the fallback and the recovery.
    val parentObserver = runCatching { context.filesDir.canonicalFile }.getOrNull()?.let { directory ->
        runCatching {
            object : FileObserver(directory.path, directoryEvents) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null && path != FileFolders.UPLOAD) return
                    rearm.trySend(Unit)
                }
            }.also { it.startWatching() }
        }.getOrNull()
    }

    // The first arm also samples, so an absent upload directory resolves to the fallback instead of staying pending.
    watchUploadDirectory()
    // Drain any changes queued during the initial arm before waiting for more filesystem events.
    val rearmer = launch { for (ignored in rearm) watchUploadDirectory() }
    awaitClose {
        val pending = synchronized(lock) {
            released = true
            uploadObserver.also { uploadObserver = null }
        }
        rearmer.cancel()
        rearm.close()
        parentObserver?.let { observer -> runCatching { observer.stopWatching() } }
        pending?.let { observer -> runCatching { observer.stopWatching() } }
    }
}.conflate().map { loadAppearanceImage(context, path) }.flowOn(Dispatchers.IO)

@Composable
internal fun rememberAppearanceImageState(path: String?): AppearanceImageState? {
    if (path.isNullOrBlank()) return null
    val context = LocalContext.current.applicationContext
    val state = remember(context, path) { AppearanceImageState() }
    LaunchedEffect(state) {
        appearanceImageChanges(context, path).collect {
            state.image = it
            state.resolved = true
        }
    }
    return state
}

@Composable
internal fun AppearanceBackdrop(
    image: AppearanceImage,
    appearance: AdvancedAppearanceSetting,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = LocalAppearanceImage.current
    val request = remember(context, image) {
        // Identity includes the filesystem file key: a restored upload directory can hand back the same path,
        // size and timestamp for different bytes, and the memory cache is keyed on this string.
        val identity = "appearance:${image.file.path}:${image.modifiedAt}:${image.byteCount}:${image.fileKey}"
        ImageRequest.Builder(context).data(image.file)
            .memoryCacheKey(identity)
            .build()
    }
    val radius = resolvedLiveAppearanceRadius(appearance.globalBackgroundBlurRadius, Build.VERSION.SDK_INT, true).dp
    Box(modifier = modifier.clipToBounds()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = {
                if (state != null && state.image === image) state.image = null
            },
            modifier = Modifier.fillMaxSize()
                .alpha(appearance.globalBackgroundOpacity)
                .then(if (radius > 0.dp) Modifier.blur(radius, edgeTreatment = BlurredEdgeTreatment.Rectangle) else Modifier),
        )
    }
}

/** Local backdrop for a navigation pane or an independent full-screen image preview. */
@Composable
internal fun AppearancePreviewBackdrop(
    image: AppearanceImage,
    appearance: AdvancedAppearanceSetting,
    modifier: Modifier = Modifier,
) {
    val scrim = appearanceBackdropScrim(image.colors.average, appearance.globalBackgroundOpacity, minimum = .28f)
    Box(modifier.background(LocalUiSlots.current.pageBackground.copy(alpha = 1f))) {
        AppearanceBackdrop(image, appearance, Modifier.fillMaxSize())
        Box(Modifier.matchParentSize().background(scrim))
    }
}

internal data class AppearanceImageColors(val accent: Int?, val average: Int?)

internal fun BitmapFactory.Options.isAppearanceImage(): Boolean =
    outMimeType?.startsWith("image/") == true &&
        outWidth in 1..MAX_APPEARANCE_IMAGE_DIMENSION &&
        outHeight in 1..MAX_APPEARANCE_IMAGE_DIMENSION

// Decode a small sample once per selected file, off the main thread. Alpha is not a color vote.
internal fun sampleAppearanceImageBytes(bytes: ByteArray): AppearanceImageColors? = sampleAppearanceImage { options ->
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private inline fun sampleAppearanceImage(
    decode: (BitmapFactory.Options) -> android.graphics.Bitmap?,
): AppearanceImageColors? = runCatching {
    val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(info)
    if (!info.isAppearanceImage()) return@runCatching null
    val sample = Integer.highestOneBit(max(info.outWidth, info.outHeight) / 96).coerceAtLeast(1)
    val bitmap = decode(BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }) ?: return@runCatching null
    try {
        var total = 0
        var red = 0
        var green = 0
        var blue = 0
        val votes = IntArray(12)
        val hueSamples = Array(12) { IntArray(3) }
        val hsv = FloatArray(3)
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            total++
            red += r
            green += g
            blue += b
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] < .18f || hsv[2] < .18f) continue
            val bin = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            votes[bin]++
            hueSamples[bin][0] += r
            hueSamples[bin][1] += g
            hueSamples[bin][2] += b
        }
        if (total == 0) return@runCatching AppearanceImageColors(accent = null, average = null)
        val best = votes.indices.maxBy { votes[it] }
        val count = votes[best]
        val accent = if (count > 0) android.graphics.Color.rgb(
            hueSamples[best][0] / count, hueSamples[best][1] / count, hueSamples[best][2] / count,
        ) else null
        AppearanceImageColors(accent, android.graphics.Color.rgb(red / total, green / total, blue / total))
    } finally {
        bitmap.recycle()
    }
}.getOrNull()
