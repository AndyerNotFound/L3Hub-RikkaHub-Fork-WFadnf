package me.rerere.rikkahub.web.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderQuota
import me.rerere.ai.provider.supportsQuota
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.web.dto.ProviderQuotaDto
import me.rerere.rikkahub.web.dto.ProviderQuotaWindowDto

fun Route.providerRoutes(
    settingsStore: SettingsStore,
    providerManager: ProviderManager,
) {
    route("/providers") {
        // GET /api/providers/{id}/quota - balance or subscription usage of one provider
        get("/{id}/quota") {
            val uuid = call.parameters["id"].toUuid("provider id")
            val settings = settingsStore.awaitLoaded()
            val provider = settings.providers.firstOrNull { it.id == uuid }
                ?: throw NotFoundException("Provider not found")

            if (!provider.supportsQuota) {
                call.respond(ProviderQuotaDto(type = "none"))
                return@get
            }

            val quota = providerManager.getProviderByType(provider).getQuota(provider)
            if (quota == null) {
                call.respond(ProviderQuotaDto(type = "none"))
                return@get
            }

            val dto = when (quota) {
                is ProviderQuota.Balance -> ProviderQuotaDto(
                    type = "balance",
                    value = quota.value,
                )

                is ProviderQuota.UsageLimits -> ProviderQuotaDto(
                    type = "usage",
                    limitReached = quota.limitReached,
                    windows = quota.windows.map { window ->
                        ProviderQuotaWindowDto(
                            usedPercent = window.usedPercent,
                            windowMinutes = window.windowMinutes,
                            resetsAt = window.resetsAt?.toString(),
                        )
                    },
                )
            }
            call.respond(dto)
        }
    }
}
