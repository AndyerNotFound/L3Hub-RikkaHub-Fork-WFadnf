package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant

@Serializable
enum class WorkspaceToolMode {
    DISABLED,
    APPROVAL,
    AUTO;

    fun next(): WorkspaceToolMode = when (this) {
        DISABLED -> APPROVAL
        APPROVAL -> AUTO
        AUTO -> DISABLED
    }
}

internal val WorkspaceToolDefaults = mapOf(
    "workspace_read_file" to WorkspaceToolMode.AUTO,
    "workspace_list" to WorkspaceToolMode.AUTO,
    "workspace_glob" to WorkspaceToolMode.AUTO,
    "workspace_grep" to WorkspaceToolMode.AUTO,
    "workspace_repo_map" to WorkspaceToolMode.AUTO,
    "workspace_write_file" to WorkspaceToolMode.AUTO,
    "workspace_edit_file" to WorkspaceToolMode.AUTO,
    "workspace_apply_patch" to WorkspaceToolMode.AUTO,
    "workspace_shell" to WorkspaceToolMode.APPROVAL,
    "workspace_git" to WorkspaceToolMode.APPROVAL,
    "workspace_test" to WorkspaceToolMode.APPROVAL,
)

internal fun Map<String, WorkspaceToolMode>.toolMode(name: String): WorkspaceToolMode =
    this[name] ?: WorkspaceToolDefaults[name] ?: WorkspaceToolMode.DISABLED

internal fun decodeWorkspaceToolModes(raw: String): Map<String, WorkspaceToolMode> {
    val fields = runCatching { JsonInstant.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: return WorkspaceToolDefaults.mapValues { WorkspaceToolMode.DISABLED }
    return fields.mapValues { (_, value) ->
        when (value) {
            JsonPrimitive(true) -> WorkspaceToolMode.APPROVAL
            JsonPrimitive(false) -> WorkspaceToolMode.AUTO
            else -> WorkspaceToolMode.entries.firstOrNull { JsonPrimitive(it.name) == value }
                // An unrecognized value must not re-enable a tool that was disabled.
                ?: WorkspaceToolMode.DISABLED
        }
    }
}
