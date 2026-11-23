package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AlertCircle
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.FingerPrint
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Key01
import me.rerere.hugeicons.stroke.Tick02
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AppIconBadge
import me.rerere.rikkahub.ui.components.ui.AppIconRow
import me.rerere.rikkahub.ui.components.ui.AppSettingsCard
import me.rerere.rikkahub.ui.components.ui.AppSettingsGroup
import me.rerere.rikkahub.ui.components.ui.AppSettingsRow
import me.rerere.rikkahub.ui.components.ui.glassSegmentedButtonColors
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.style.IconButton
import me.rerere.rikkahub.ui.style.OutlinedButton
import me.rerere.rikkahub.ui.style.TextButton
import me.rerere.rikkahub.ui.style.TopAppBar
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.ui.components.AnimatedAlertDialog
import me.rerere.workspace.ssh.SshAuthType
import me.rerere.workspace.ssh.SshRemoteDirIssue
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_PRIVATE_KEY_BYTES = 64 * 1024

@Composable
fun WorkspaceSshEditorPage(id: String?) {
    val navController = LocalNavController.current
    val vm: WorkspaceSshEditorVM = koinViewModel(key = "ssh-editor-$id", parameters = { parametersOf(id) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 测试连接和保存针对开始时的表单快照, 期间锁定表单
    val editable = !state.busy

    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    val privateKeyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_PRIVATE_KEY_BYTES) }
        }.getOrNull()
        if (text != null) vm.update { it.copy(privateKey = text.trim()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) stringResource(R.string.workspace_ssh_editor_title_new) else stringResource(R.string.workspace_ssh_editor_title_edit)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = vm::save, enabled = !state.busy) {
                        Icon(HugeIcons.Tick02, contentDescription = stringResource(R.string.workspace_ssh_editor_save))
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            // 横向内距交给 AppSettingsCard（自带 12dp 外距），这里不再补
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_basic)) {
                    FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_name)) {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.name,
                            onValueChange = { value -> vm.update { it.copy(name = value) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.workspace_ssh_editor_name_placeholder)) },
                            singleLine = true,
                        )
                    }
                }
            }

            item {
                AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_connection)) {
                    FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_host)) {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.host,
                            onValueChange = { value -> vm.update { it.copy(host = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.workspace_ssh_editor_host_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    }
                    FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_port)) {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.port,
                            onValueChange = { value ->
                                vm.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_username)) {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.username,
                            onValueChange = { value -> vm.update { it.copy(username = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                }
            }

            item {
                AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_auth)) {
                    if (state.savedCredentialsLost) {
                        AppIconRow(
                            icon = HugeIcons.AlertCircle,
                            headline = stringResource(R.string.workspace_ssh_editor_credentials_lost),
                            supporting = stringResource(R.string.workspace_ssh_editor_credentials_lost_desc),
                        )
                    }
                    FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_auth_method)) {
                        val options = listOf(
                            SshAuthType.PASSWORD to stringResource(R.string.workspace_ssh_editor_password),
                            SshAuthType.PRIVATE_KEY to stringResource(R.string.workspace_ssh_editor_private_key),
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            options.forEachIndexed { index, (type, label) ->
                                SegmentedButton(
                                    colors = glassSegmentedButtonColors(),
                                    enabled = editable,
                                    selected = state.authType == type,
                                    onClick = { vm.update { it.copy(authType = type) } },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                    when (state.authType) {
                        SshAuthType.PASSWORD -> FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_password)) {
                            SecretTextField(
                                enabled = editable,
                                value = state.password,
                                onValueChange = { value -> vm.update { it.copy(password = value) } },
                                placeholder = if (state.hasSavedPassword) stringResource(R.string.workspace_ssh_editor_saved_keep) else null,
                            )
                        }

                        SshAuthType.PRIVATE_KEY -> {
                            FormFieldRow(
                                headline = stringResource(R.string.workspace_ssh_editor_private_key),
                                supporting = stringResource(R.string.workspace_ssh_editor_private_key_desc),
                            ) {
                                OutlinedTextField(
                                    enabled = editable,
                                    value = state.privateKey,
                                    onValueChange = { value -> vm.update { it.copy(privateKey = value) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            if (state.hasSavedPrivateKey) {
                                                stringResource(R.string.workspace_ssh_editor_saved_keep)
                                            } else {
                                                "-----BEGIN OPENSSH PRIVATE KEY-----"
                                            }
                                        )
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    minLines = 3,
                                    maxLines = 8,
                                )
                                OutlinedButton(
                                    enabled = editable,
                                    onClick = { privateKeyPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        HugeIcons.FileImport,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.workspace_ssh_editor_import_key))
                                }
                            }
                            FormFieldRow(headline = stringResource(R.string.workspace_ssh_editor_passphrase)) {
                                SecretTextField(
                                    enabled = editable,
                                    value = state.passphrase,
                                    onValueChange = { value -> vm.update { it.copy(passphrase = value) } },
                                    placeholder = stringResource(R.string.workspace_ssh_editor_passphrase_placeholder),
                                )
                            }
                        }
                    }
                }
            }

            item {
                AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_working_dir)) {
                    val homeDir = state.probeResult?.homeDir?.takeIf { it != state.remoteDir }
                    FormFieldRow(
                        headline = stringResource(R.string.workspace_ssh_editor_remote_dir),
                        supporting = stringResource(R.string.workspace_ssh_editor_remote_dir_desc),
                        trailing = if (homeDir != null) {
                            {
                                TextButton(onClick = { vm.update { it.copy(remoteDir = homeDir) } }) {
                                    Text(stringResource(R.string.workspace_ssh_editor_use_home))
                                }
                            }
                        } else {
                            null
                        },
                    ) {
                        OutlinedTextField(
                            enabled = editable,
                            value = state.remoteDir,
                            onValueChange = { value -> vm.update { it.copy(remoteDir = value.trim()) } },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("/home/user/project") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                    }
                }
            }

            item {
                AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_security)) {
                    // 指纹用等宽字体多行显示，AppIconRow 只吃纯文本，这一行自己拼
                    AppSettingsRow {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconBadge(HugeIcons.FingerPrint)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.workspace_ssh_editor_host_key))
                                Spacer(Modifier.height(2.dp))
                                val hostKey = state.hostKey
                                if (hostKey == null) {
                                    Text(
                                        text = stringResource(R.string.workspace_ssh_editor_host_key_unconfirmed),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                } else {
                                    Text(
                                        text = "${hostKey.type}\n${hostKey.fingerprint}",
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ProbeSection(
                    state = state,
                    onTest = vm::testConnection,
                )
            }
        }
    }

    state.pendingHostKey?.let { pending ->
        HostKeyDialog(
            pending = pending,
            address = "${pending.draft.host}:${pending.draft.port}",
            onTrust = vm::trustHostKey,
            onDismiss = vm::dismissHostKey,
        )
    }

    state.error?.let { error ->
        val message = when (error) {
            is SshEditorError.Resource -> stringResource(error.id)
            is SshEditorError.Message -> error.text
            is SshEditorError.MessageArgs -> stringResource(error.id, error.arg)
        }
        AnimatedAlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(
                    if (state.canSaveAnyway) {
                        stringResource(R.string.workspace_ssh_editor_connect_failed)
                    } else {
                        stringResource(R.string.workspace_ssh_editor_error)
                    }
                ) },
            text = {
                Text(
                    if (state.canSaveAnyway) stringResource(R.string.workspace_ssh_editor_save_anyway_message, message) else message
                )
            },
            confirmButton = {
                if (state.canSaveAnyway) {
                    TextButton(onClick = vm::saveAnyway) { Text(stringResource(R.string.workspace_ssh_editor_save_anyway)) }
                } else {
                    TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.confirm)) }
                }
            },
            dismissButton = if (state.canSaveAnyway) {
                { TextButton(onClick = vm::dismissError) { Text(stringResource(R.string.cancel)) } }
            } else {
                null
            },
        )
    }
}

// 表单项：标题行（可带说明与右侧控件）+ 内容区，内距由 AppSettingsRow 给
@Composable
private fun FormFieldRow(
    headline: String,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppSettingsRow {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = headline)
                    if (supporting != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = supporting,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    enabled: Boolean,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) HugeIcons.ViewOff else HugeIcons.View, contentDescription = null)
            }
        },
    )
}

@Composable
private fun ProbeSection(
    state: WorkspaceSshEditorState,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = onTest,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                // LazyColumn 的横向内距交给了 Salt 的分组，这个按钮自己补回来
                .padding(horizontal = 16.dp),
        ) {
            if (state.testing) {
                LoadingIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
            } else {
                Icon(HugeIcons.Connect, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                if (state.testing) {
                    stringResource(R.string.workspace_ssh_editor_connecting)
                } else {
                    stringResource(R.string.workspace_ssh_editor_test)
                }
            )
        }
        state.probeResult?.let { result ->
            AppSettingsGroup(title = stringResource(R.string.workspace_ssh_editor_connected)) {
                AppIconRow(
                    icon = HugeIcons.CloudServer,
                    headline = stringResource(R.string.workspace_ssh_editor_shell),
                    supporting = result.shell,
                )
                val remoteDirText = when {
                    state.remoteDir.isBlank() || state.remoteDir == result.homeDir ->
                        stringResource(R.string.workspace_ssh_editor_home_dir, result.homeDir)

                    result.remoteDirExists -> state.remoteDir
                    else -> when (result.remoteDirIssue) {
                        SshRemoteDirIssue.PERMISSION_DENIED -> stringResource(
                            R.string.workspace_ssh_editor_dir_denied,
                            state.remoteDir,
                        )

                        SshRemoteDirIssue.NOT_A_DIRECTORY -> stringResource(
                            R.string.workspace_ssh_editor_dir_not_directory,
                            state.remoteDir,
                        )

                        else -> stringResource(R.string.workspace_ssh_editor_dir_missing, state.remoteDir)
                    }
                }
                AppIconRow(
                    icon = HugeIcons.Folder01,
                    headline = stringResource(R.string.workspace_ssh_editor_working_dir),
                    supporting = remoteDirText,
                )
            }
        }
    }
}

@Composable
private fun HostKeyDialog(
    pending: PendingHostKey,
    address: String,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    val changed = pending.expected != null
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(HugeIcons.Key01, contentDescription = null) },
        title = { Text(
                if (changed) {
                    stringResource(R.string.workspace_ssh_editor_host_key_changed)
                } else {
                    stringResource(R.string.workspace_ssh_editor_host_key_confirm)
                }
            ) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (changed) {
                        stringResource(R.string.workspace_ssh_editor_host_key_changed_message, address)
                    } else {
                        val keyFile = pending.actual.type.removePrefix("ssh-").substringBefore('-')
                        stringResource(
                            R.string.workspace_ssh_editor_host_key_first_message,
                            address,
                            "ssh-keygen -lf /etc/ssh/ssh_host_${keyFile}_key.pub",
                        )
                    }
                )
                Text(
                    text = "${pending.actual.type}\n${pending.actual.fingerprint}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        },
        confirmButton = {
            if (changed) {
                Button(
                    onClick = onTrust,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.workspace_ssh_editor_trust_new_key)) }
            } else {
                TextButton(onClick = onTrust) { Text(stringResource(R.string.workspace_ssh_editor_trust_continue)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// 私钥文件很小, 超过上限的多半是选错了文件, 不整个读进内存
private fun InputStream.readLimited(limit: Int): String? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
