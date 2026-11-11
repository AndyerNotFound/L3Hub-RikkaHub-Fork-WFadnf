package me.rerere.rikkahub.web.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.web.dto.WorkspaceDto

fun Route.workspaceRoutes(workspaceRepository: WorkspaceRepository) {
    // GET /api/workspaces - names only, for folder binding pickers
    get("/workspaces") {
        val workspaces = workspaceRepository.listFlow().first()
        call.respond(
            workspaces.map { workspace ->
                WorkspaceDto(
                    id = workspace.id,
                    name = workspace.name,
                    type = workspace.type,
                )
            }
        )
    }
}
