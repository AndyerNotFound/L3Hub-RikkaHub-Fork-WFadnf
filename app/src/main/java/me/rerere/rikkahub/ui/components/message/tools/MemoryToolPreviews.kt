package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.MemoryToolNames
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ui.richtext.DiffView

/**
 * 记忆库工具的详情
 *
 * 记忆文件本身就是 Markdown, 读出来直接排版; 写入与追加展示将要落盘的正文;
 * 片段替换用 diff 展示改了哪一段. 参数不完整或输出不是预期结构时退回默认详情.
 */
@Composable
internal fun MemoryToolPreview(context: ToolUIContext) {
    when (context.tool.toolName) {
        MemoryToolNames.READ -> MemoryReadPreview(context)
        MemoryToolNames.WRITE, MemoryToolNames.APPEND -> MemoryWritePreview(context)
        MemoryToolNames.STR_REPLACE -> MemoryReplacePreview(context)
        else -> DefaultToolPreview(context = context)
    }
}

@Composable
private fun MemoryReadPreview(context: ToolUIContext) {
    val files = remember(context.content) {
        (context.content?.jsonObjectOrNull?.get("files") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()
    }
    val notFound = remember(context.content) {
        (context.content?.jsonObjectOrNull?.get("not_found") as? JsonArray)
            ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
            .orEmpty()
    }
    if (files.isEmpty() && notFound.isEmpty()) {
        DefaultToolPreview(context = context)
        return
    }

    Column(
        modifier = Modifier
            .toolPreviewSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_message_tool_read_memory),
            style = MaterialTheme.typography.titleMedium,
        )
        files.forEach { file ->
            val path = file.getStringContent("path") ?: return@forEach
            val content = file.getStringContent("content").orEmpty()
            MemoryFilePreview(
                path = path,
                content = content,
                version = file.getStringContent("version"),
            )
        }
        notFound.forEach { path ->
            Text(
                text = stringResource(R.string.tool_ui_memory_not_found, path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun MemoryWritePreview(context: ToolUIContext) {
    val path = context.arguments.getStringContent("path")
    val content = context.arguments.getStringContent("content")
    if (path == null || content == null || content.isBlank()) {
        DefaultToolPreview(context = context)
        return
    }
    Column(
        modifier = Modifier
            .toolPreviewSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        MemoryFilePreview(path = path, content = content)
    }
}

@Composable
private fun MemoryReplacePreview(context: ToolUIContext) {
    val path = context.arguments.getStringContent("path")
    val oldText = context.arguments.getStringContent("old_str")
    val newText = context.arguments.getStringContent("new_str")
    if (path == null || oldText == null || newText == null) {
        DefaultToolPreview(context = context)
        return
    }
    val diff = remember(path, oldText, newText) { generateUnifiedDiff(oldText, newText, path) }
    if (diff == null) {
        DefaultToolPreview(context = context)
        return
    }

    Column(
        modifier = Modifier
            .toolPreviewSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_message_tool_edit_memory),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = path,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        DiffView(
            diff = diff,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 一个记忆文件: 标题取 frontmatter, 路径与版本在上方, 正文渲染成 Markdown */
@Composable
private fun MemoryFilePreview(path: String, content: String, version: String? = null) {
    val title = remember(path, content) {
        if (content.isBlank()) path.substringAfterLast('/')
        else MemoryFile(path = path, content = content, updatedAt = 0L).title
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (version != null) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        Text(
            text = path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (content.isNotBlank()) {
            ToolRichTextForPath(
                path = path,
                text = content,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
