package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.android.Logging
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.ui.crop.ImageCropDialog
import me.rerere.ui.crop.NormalizedCrop
import me.rerere.ui.toast.ToastType
import java.io.File

/**
 * 返回一个打开裁剪对话框的函数。裁好的图片先写成临时的 JPEG 交给 [onCroppedImageReady]，回调结束后删除；
 * 看不了动图（或者动图但没传 [onCroppedAnimated]）时就是这条路径。
 *
 * 源文件是动图且传了 [onCroppedAnimated] 时，不压图：把原文件复制成临时文件连同裁剪区域交给回调，
 * 回调结束后删除临时文件。调用方负责把源文件存到自己那边并在渲染时应用裁剪区域。
 *
 * 不管裁剪完成还是取消，最后都会调用 [onCleanup]。
 *
 * @param aspectRatio 固定的宽高比（宽 / 高），为空时可以自由裁剪
 */
@Composable
internal fun useCropLauncher(
    onCroppedImageReady: (Uri) -> Unit,
    onCroppedAnimated: (suspend (Uri, NormalizedCrop) -> Unit)? = null,
    onCleanup: (() -> Unit)? = null,
    aspectRatio: Float? = null,
): (Uri) -> Unit {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    // 写文件期间对话框还开着，防止连点确认存出两张
    var saving by remember { mutableStateOf(false) }

    fun finish() {
        source = null
        onCleanup?.invoke()
    }

    source?.let { sourceUri ->
        ImageCropDialog(
            image = sourceUri,
            aspectRatio = aspectRatio,
            onDismiss = ::finish,
            onConfirm = { result ->
                if (saving) return@ImageCropDialog
                saving = true
                scope.launch {
                    runCatching {
                        if (result.animated && onCroppedAnimated != null) {
                            // 复制完再删临时文件：回调里可以安心切到 IO 线程搬运大动图
                            val sourceFile = withContext(Dispatchers.IO) {
                                copyCropSource(context, sourceUri)
                            }
                            try {
                                onCroppedAnimated(sourceFile.toUri(), result.crop)
                            } finally {
                                sourceFile.delete()
                            }
                        } else {
                            val outputFile = File(
                                context.appTempFolder,
                                "crop_output_${System.currentTimeMillis()}.jpg",
                            )
                            try {
                                withContext(Dispatchers.IO) {
                                    outputFile.outputStream().use { output ->
                                        result.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                                    }
                                }
                                onCroppedImageReady(outputFile.toUri())
                            } finally {
                                outputFile.delete()
                            }
                        }
                    }.onFailure { error ->
                        Logging.log(
                            "CropLauncher",
                            "crop failed: ${error.message} | ${error.stackTraceToString()}"
                        )
                        toaster.show(
                            "Failed to crop image: ${error.message ?: "unknown error"}",
                            type = ToastType.Error
                        )
                    }
                    saving = false
                    finish()
                }
            },
        )
    }

    return { source = it }
}

// 临时的动图源文件：保留扩展名，后面按文件名猜 mime 时才不会把它当成未知格式
private fun copyCropSource(context: Context, uri: Uri): File {
    val mime = context.contentResolver.getType(uri)
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: uri.lastPathSegment?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
        ?: "img"
    val file = File(context.appTempFolder, "crop_source_${System.currentTimeMillis()}.$extension")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: error("Failed to open input stream for $uri")
    return file
}
