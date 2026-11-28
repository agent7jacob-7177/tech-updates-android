package com.code7.techupdates.data.remote

import com.code7.techupdates.domain.SearchRequest
import com.code7.techupdates.domain.Story
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** One page of search results. */
data class SearchResult(
    val stories: List<Story>,
    val page: Int,
    val nbPages: Int,
)

/** Client for the public Hacker News Algolia API. */
class HnApi(
    engine: HttpClientEngine = OkHttp.create(),
    private val baseUrl: String = BASE_URL,
) {
    private val client =
        HttpClient(engine) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }
            install(ContentNegotiation) { json(JsonConfig) }
        }

    /** `GET search` / `search_by_date` with [request]'s params; titleless hits are dropped. */
    suspend fun search(request: SearchRequest): SearchResult {
        val dto: SearchResponseDto =
            client
                .get(baseUrl + request.endpoint) {
                    request.params.forEach { (k, v) -> parameter(k, v) }
                }.body()
        return SearchResult(
            stories = dto.hits.mapNotNull { it.toStory() },
            page = dto.page,
            nbPages = dto.nbPages,
        )
    }

    /** `GET items/{id}`: the item with its full comment tree. */
    suspend fun item(id: String): ItemDto = client.get(baseUrl + "items/" + id.encodePathSegment()).body()

    fun close() = client.close()

    companion object {
        const val BASE_URL = "https://hn.algolia.com/api/v1/"
        const val TIMEOUT_MILLIS = 20_000L
        val JsonConfig = Json { ignoreUnknownKeys = true }

        private fun String.encodePathSegment(): String =
            java.net.URLEncoder
                .encode(this, "UTF-8")
                .replace("+", "%20")
    }
}
