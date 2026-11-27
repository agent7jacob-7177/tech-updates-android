package com.code7.techupdates.domain

/** A Hacker News story as returned by an Algolia search hit. */
data class Story(
    val id: String,
    val title: String,
    val url: String?,
    val author: String,
    val points: Int,
    val numComments: Int,
    val createdAtEpochSec: Long,
    val storyText: String?,
)

/** A node of a Hacker News comment tree (`/items/{id}`). [text] is HTML; null for deleted comments. */
data class Comment(
    val id: Long,
    val author: String?,
    val text: String?,
    val createdAtEpochSec: Long,
    val children: List<Comment>,
)
