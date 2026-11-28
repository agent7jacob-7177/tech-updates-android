package com.code7.techupdates.domain

import java.net.URI

/** The web app's reader links: `https://agent7jacob-7177.github.io/code7/#story/<id>`. */
object DeepLink {
    const val HOST = "agent7jacob-7177.github.io"
    private val PATHS = setOf("/code7", "/code7/", "/code7/index.html")

    /** JS `route()`: `location.hash.match(/^#story\/(\d+)$/)`. */
    private val STORY_FRAGMENT = Regex("""^story/(\d+)$""")

    /**
     * The story id of a web reader link, or null if [uri] is not one (other site pages, other
     * fragments, malformed input). Android intent filters cannot match on the fragment, so this is
     * where `#story/<id>` is told apart from plain links to the web app.
     */
    fun storyId(uri: String?): String? {
        val parsed =
            uri
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { URI(it) }.getOrNull() }
                ?.takeIf {
                    it.scheme.equals("https", ignoreCase = true) &&
                        it.host.equals(HOST, ignoreCase = true) &&
                        it.rawPath in PATHS
                }
        return parsed?.rawFragment?.let { STORY_FRAGMENT.matchEntire(it)?.groupValues?.get(1) }
    }

    /** The web reader link for [storyId] (inverse of [storyId]). */
    fun storyUrl(storyId: String): String = "https://$HOST/code7/#story/$storyId"
}
