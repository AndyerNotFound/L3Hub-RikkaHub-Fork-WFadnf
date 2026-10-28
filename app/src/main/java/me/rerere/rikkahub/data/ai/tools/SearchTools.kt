package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.search.SearchResult
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.time.LocalDate
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun createSearchTools(settings: Settings): Set<Tool> {
    val selectedOptions = settings.searchServices.getOrElse(
        index = settings.searchServiceSelected,
        defaultValue = { SearchServiceOptions.DEFAULT },
    )
    // 勾选了多个引擎时全部查询；一个都没勾时沿用当前默认引擎
    val activeOptions = settings.searchServices
        .filter { it.id.toString() in settings.enabledSearchServiceIds }
        .ifEmpty { listOf(selectedOptions) }

    return buildSet {
        add(
            Tool(
                name = "search_web",
                description = """
                    Search the web for up-to-date or specific information.
                    Use this when the user asks for the latest news, current facts, or needs verification.
                    Do not treat result order as proof of freshness. Prefer primary sources and inspect
                    each result's title, URL, publication date, and content before making a current claim.
                    Use the optional publication-date and domain filters only when they match the question.
                    If a date or primary source is missing, or sources conflict, run another focused search
                    or use scrape_web to verify the most relevant source before answering.
                    Generate focused keywords and run multiple searches if needed.
                    Today is ${LocalDate.now().toLocalString(true)}.

                    Response format:
                    - retrievedAt is the local retrieval time, never a publication date
                    - items[].id (short id), index, title, url, publishedDate (if supplied), highlights (if supplied), text
                    - images[]: image urls related to the query (may be empty)

                    Citations:
                    - After using results, add `[citation,domain](id)` after the sentence.
                    - Multiple citations are allowed.
                    - If no results are cited, omit citations.

                    Images:
                    - When images help the user understand the answer, embed relevant ones using Markdown: `![](url)`.
                    - Embed 2 to 4 images, and only use urls from `images[]` (never fabricate or alter urls).
                    - Usually place the images at the very beginning of your reply; skip them entirely if none are relevant.

                    Example:
                    The capital of France is Paris. [citation,example.com](abc123)
                    The population is about 2.1 million. [citation,example.com](abc123) [citation,example2.com](def456)
                    """.trimIndent(),
                parameters = {
                    val options = activeOptions.first()
                    SearchService.getService(options).parameters(options)
                },
                execute = {
                    var firstError: Throwable? = null
                    val successes = activeOptions.mapNotNull { options ->
                        runCatching {
                            SearchService.getService(options).search(
                                params = it.jsonObject,
                                commonOptions = settings.searchCommonOptions,
                                serviceOptions = options,
                            ).getOrThrow()
                        }.onFailure { error -> if (firstError == null) firstError = error }
                            .getOrNull()
                    }
                    if (successes.isEmpty()) {
                        throw firstError ?: IllegalStateException("No search service available")
                    }
                    val result = mergeSearchResults(successes)
                        .copy(retrievedAt = Clock.System.now().toString())
                    val results =
                        JsonInstantPretty.encodeToJsonElement(result).jsonObject.let { json ->
                            val map = json.toMutableMap()
                            map["items"] =
                                JsonArray(map["items"]!!.jsonArray.mapIndexed { index, item ->
                                    JsonObject(item.jsonObject.toMutableMap().apply {
                                        put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                                        put("index", JsonPrimitive(index + 1))
                                    })
                                })
                            JsonObject(map)
                        }
                    listOf(UIMessagePart.Text(results.toString()))
                }
            )
        )

        val scrapeOptions = activeOptions.firstOrNull { options ->
            SearchService.getService(options).scrapingParameters(options) != null
        }
        if (scrapeOptions != null) {
            val options = scrapeOptions
            val service = SearchService.getService(options)
            add(
                Tool(
                    name = "scrape_web",
                    description = """
                        Scrape a URL for detailed page content.
                        Use this when the user requests content from a specific page, when search snippets are insufficient,
                        or when a current claim needs verification against a specific source.
                        Avoid using it for common questions unless the user asks.
                        """.trimIndent(),
                    parameters = {
                        service.scrapingParameters(options)
                    },
                    execute = {
                        val result = service.scrape(
                            params = it.jsonObject,
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = options,
                        ).getOrThrow().copy(retrievedAt = Clock.System.now().toString())
                        val payload = JsonInstantPretty.encodeToJsonElement(result).jsonObject
                        listOf(UIMessagePart.Text(payload.toString()))
                    }
                ))
        }
    }
}

private fun mergeSearchResults(results: List<SearchResult>): SearchResult {
    val items = results.flatMap { it.items }.distinctBy { it.url }
    val images = results.flatMap { it.images }.distinct()
    val answer = results.mapNotNull { it.answer?.takeIf(String::isNotBlank) }
        .joinToString("\n\n")
        .ifBlank { null }
    return SearchResult(answer = answer, items = items, images = images)
}
