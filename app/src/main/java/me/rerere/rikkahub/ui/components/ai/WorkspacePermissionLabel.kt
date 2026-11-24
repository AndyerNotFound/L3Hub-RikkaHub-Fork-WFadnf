package me.rerere.rikkahub.ui.components.ai

import androidx.annotation.StringRes
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ExecutionPermission

@StringRes
internal fun ExecutionPermission.workspacePermissionLabelRes(): Int = when (this) {
    ExecutionPermission.READ_ONLY -> R.string.assistant_page_execution_permission_read_only
    ExecutionPermission.APPROVAL -> R.string.assistant_page_execution_permission_approval
    ExecutionPermission.FULL -> R.string.assistant_page_execution_permission_full
}
