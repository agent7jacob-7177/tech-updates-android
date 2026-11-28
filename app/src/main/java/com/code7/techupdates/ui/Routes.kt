package com.code7.techupdates.ui

/** Navigation routes. */
object Routes {
    const val NEWS = "news"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ARG_ID = "id"
    const val STORY = "story/{$ARG_ID}"
    const val COMMENTS = "comments/{$ARG_ID}"

    fun story(id: String) = "story/$id"

    fun comments(id: String) = "comments/$id"
}
