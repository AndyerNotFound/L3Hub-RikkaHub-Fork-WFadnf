package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.ItemContainer
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.style.IconButton

/**
 * S3 连接信息的表单项，备份和媒体上传的配置共用。
 *
 * 普通 Composable：调用方在外面套 `RoundedColumn { s3ConnectionItems(...) }`，
 * 每一行自带 Salt 的内距。
 *
 * @param modifier 作用在整列上，分组的外距由调用方的 RoundedColumn 提供
 */
@Composable
fun s3ConnectionItems(
    config: S3Config,
    onUpdate: (S3Config) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        S3TextFieldRow(headline = stringResource(R.string.backup_page_s3_endpoint)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.endpoint,
                onValueChange = { onUpdate(config.copy(endpoint = it.trim())) },
                placeholder = { Text("https://s3.amazonaws.com") },
                singleLine = true
            )
        }
        S3TextFieldRow(headline = stringResource(R.string.backup_page_s3_access_key_id)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.accessKeyId,
                onValueChange = { onUpdate(config.copy(accessKeyId = it.trim())) },
                singleLine = true
            )
        }
        S3TextFieldRow(headline = stringResource(R.string.backup_page_s3_secret_access_key)) {
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.secretAccessKey,
                onValueChange = { onUpdate(config.copy(secretAccessKey = it.trim())) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) {
                        HugeIcons.ViewOff
                    } else {
                        HugeIcons.View
                    }
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                singleLine = true
            )
        }
        S3TextFieldRow(headline = stringResource(R.string.backup_page_s3_bucket)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.bucket,
                onValueChange = { onUpdate(config.copy(bucket = it.trim())) },
                placeholder = { Text("my-bucket") },
                singleLine = true
            )
        }
        SaltSwitchRow(
            checked = config.pathStyle,
            onCheckedChange = { onUpdate(config.copy(pathStyle = it)) },
            headline = stringResource(R.string.backup_page_s3_path_style),
            supporting = stringResource(R.string.backup_page_s3_path_style_desc),
        )
        S3TextFieldRow(headline = stringResource(R.string.backup_page_s3_region)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = config.region,
                onValueChange = { onUpdate(config.copy(region = it.trim())) },
                placeholder = { Text("auto") },
                singleLine = true
            )
        }
    }
}

/** 标题行 + 输入框，标题样式跟 Salt 的设置项一致 */
@Composable
private fun S3TextFieldRow(
    headline: String,
    content: @Composable () -> Unit,
) {
    ItemContainer {
        Column {
            Text(text = headline)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
