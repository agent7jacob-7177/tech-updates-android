package com.code7.techupdates.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

object Fixtures {
    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("hn/$name")) { "missing fixture $name" }.readText()

    fun MockRequestHandleScope.json(
        name: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(read(name), status, headersOf(HttpHeaders.ContentType, "application/json"))

    /** Serves fixtures by path: search → front page, search_by_date → latest, items/{id} → item tree. */
    fun engine(requests: MutableList<HttpRequestData> = mutableListOf()) =
        MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            when {
                path.endsWith("/search_by_date") -> json("search_by_date.json")
                path.endsWith("/search") -> json("search_front_page.json")
                path.contains("/items/") -> json("item_story.json")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
}
