package me.rerere.rikkahub.ui.pages.assistant.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.ImageCrop
import me.rerere.rikkahub.ui.components.ai.useCropLauncher
import me.rerere.rikkahub.ui.components.ui.CroppedImage
import me.rerere.rikkahub.ui.components.ui.VideoThumbnail
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.ui.crop.NormalizedCrop
import org.koin.compose.koinInject

@Composable
fun BackgroundPicker(
    modifier: Modifier = Modifier,
    background: String?,
    backgroundCrop: ImageCrop? = null,
    backgroundIsVideo: Boolean = false,
    backgroundOpacity: Float = 1.0f,
    onUpdate: (String?, ImageCrop?, Boolean) -> Unit,
) {
    val filesManager: FilesManager = koinInject()
    var showPickOption by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    fun saveImage(uri: Uri, crop: ImageCrop?) {
        filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()?.let { localUri ->
            onUpdate(localUri.toString(), crop, false)
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            saveImage(croppedUri, null)
        },
        onCroppedAnimated = { sourceUri, crop ->
            // 动图背景保留源文件，裁剪区域随助手一起保存，聊天页按区域裁切后仍然会动
            saveImage(sourceUri, crop.toImageCrop())
        },
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(launchImageCrop)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { source ->
            filesManager.createChatFilesByContents(listOf(source)).firstOrNull()?.let { localUri ->
                onUpdate(localUri.toString(), null, true)
            }
        }
    }

    val previewOpacity = backgroundOpacity.coerceIn(0f, 1f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (background != null) {
            val previewModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .alpha(previewOpacity)
            when {
                backgroundIsVideo -> VideoThumbnail(
                    model = background,
                    modifier = previewModifier,
                )

                backgroundCrop != null -> CroppedImage(
                    model = background,
                    crop = backgroundCrop,
                    modifier = previewModifier,
                    cover = true,
                    requestSize = 1280,
                )

                else -> AsyncImage(
                    model = background,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .alpha(previewOpacity)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = {
                    showPickOption = true
                },
            ) {
                Icon(
                    imageVector = HugeIcons.Image02,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = if (background != null) {
                        stringResource(R.string.assistant_page_change_background)
                    } else {
                        stringResource(R.string.assistant_page_select_background)
                    }
                )
            }
            if (background != null) {
                TextButton(
                    onClick = {
                        onUpdate(null, null, false)
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_remove))
                }
            }
        }
    }

    if (showPickOption) {
        AnimatedAlertDialog(
            onDismissRequest = {
                showPickOption = false
            },
            title = {
                Text(stringResource(R.string.assistant_page_select_background))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showPickOption = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.assistant_page_select_from_gallery))
                    }
                    Button(
                        onClick = {
                            showPickOption = false
                            videoPickerLauncher.launch("video/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.assistant_page_select_video))
                    }
                    Button(
                        onClick = {
                            showPickOption = false
                            urlInput = ""
                            showUrlInput = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.assistant_page_enter_image_url))
                    }
                    if (background != null) {
                        Button(
                            onClick = {
                                showPickOption = false
                                onUpdate(null, null, false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.assistant_page_remove_background))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPickOption = false
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    if (showUrlInput) {
        AnimatedAlertDialog(
            onDismissRequest = {
                showUrlInput = false
            },
            title = {
                Text(stringResource(R.string.assistant_page_enter_image_url))
            },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(R.string.assistant_page_image_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/image.jpg") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            onUpdate(urlInput.trim(), null, false)
                            showUrlInput = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUrlInput = false
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }
}

private fun NormalizedCrop.toImageCrop(): ImageCrop = ImageCrop(
    left = left,
    top = top,
    width = width,
    height = height,
    rotation = rotation,
)
