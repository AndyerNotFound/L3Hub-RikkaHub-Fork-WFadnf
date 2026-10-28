package me.rerere.rikkahub.ui.pages.stats

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIME_PNG = "image/png"
private const val MIME_PDF = "application/pdf"

suspend fun exportStatsImage(context: Context, bitmap: Bitmap): String {
    val name = "L3Hub-stats-${timestamp()}.png"
    return saveExport(
        context = context,
        displayName = name,
        mime = MIME_PNG,
        relativeDir = "${Environment.DIRECTORY_PICTURES}/L3Hub",
        legacyDir = Environment.DIRECTORY_PICTURES,
    ) { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
}

suspend fun exportStatsPdf(context: Context, bitmap: Bitmap): String {
    val name = "L3Hub-stats-${timestamp()}.pdf"
    return saveExport(
        context = context,
        displayName = name,
        mime = MIME_PDF,
        relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/L3Hub",
        legacyDir = Environment.DIRECTORY_DOWNLOADS,
    ) { output ->
        writePdf(bitmap, output)
    }
}

private fun writePdf(bitmap: Bitmap, output: OutputStream) {
    val pageWidth = 1240
    val pageHeight = 1754
    val scale = pageWidth.toFloat() / bitmap.width
    val scaledHeight = (bitmap.height * scale).roundToInt()
    val pageCount = ceil(scaledHeight.toFloat() / pageHeight).toInt().coerceAtLeast(1)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    PdfDocument().use { document ->
        for (index in 0 until pageCount) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            )
            page.canvas.drawColor(Color.WHITE)

            val top = (index * pageHeight / scale).toInt().coerceAtLeast(0)
            val bottom = min(bitmap.height, ((index + 1) * pageHeight / scale).roundToInt())
            if (bottom > top) {
                val src = Rect(0, top, bitmap.width, bottom)
                val dstHeight = ((bottom - top) * scale).roundToInt().coerceAtMost(pageHeight)
                val dst = Rect(0, 0, pageWidth, dstHeight)
                page.canvas.drawBitmap(bitmap, src, dst, paint)
            }
            document.finishPage(page)
        }
        document.writeTo(output)
    }
}

private fun saveExport(
    context: Context,
    displayName: String,
    mime: String,
    relativeDir: String,
    legacyDir: String,
    write: (OutputStream) -> Unit,
): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collection = if (mime == MIME_PDF) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("无法创建导出文件")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: error("无法写入导出文件")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return "$relativeDir/$displayName"
    }

    val dir = context.getExternalFilesDir(legacyDir)?.resolve("L3Hub")
        ?: context.filesDir.resolve("L3Hub")
    dir.mkdirs()
    val file = File(dir, displayName)
    file.outputStream().use(write)
    return file.absolutePath
}

private fun timestamp(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
