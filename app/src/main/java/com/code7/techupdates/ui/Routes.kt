package com.code7.techupdates.ui

/** Navigation routes. */
object Routes {
    const val NEWS = "news"
    const val LIBRARY = "library"
    const val ARG_ID = "id"
    const val STORY = "story/{$ARG_ID}"
    const val COMMENTS = "comments/{$ARG_ID}"

    /** The web app's reader URL (`location.hash = "story/<id>"`). */
    const val STORY_DEEP_LINK = "https://agent7jacob-7177.github.io/code7/#story/{$ARG_ID}"

    fun story(id: String) = "story/$id"

    fun comments(id: String) = "comments/$id"
}
