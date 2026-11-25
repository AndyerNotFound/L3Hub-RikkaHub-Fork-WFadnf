package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.ui.components.AnimatedAlertDialog

@Composable
internal fun CreateChatFolderDialog(onDismissRequest: () -> Unit, onCreate: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.chat_page_create_folder)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.chat_page_folder_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = folderName.isNotBlank(), onClick = {
                onCreate(folderName)
                onDismissRequest()
            }) { Text(stringResource(R.string.chat_page_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.chat_page_cancel)) }
        },
    )
}
