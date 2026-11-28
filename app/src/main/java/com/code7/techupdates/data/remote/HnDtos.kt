package com.code7.techupdates.data.remote

import com.code7.techupdates.domain.Comment
import com.code7.techupdates.domain.Story
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val hits: List<HitDto> = emptyList(),
    val page: Int = 0,
    val nbPages: Int = 1,
)

@Serializable
data class HitDto(
    val objectID: String,
    val title: String? = null,
    val url: String? = null,
    val author: String? = null,
    val points: Int? = null,
    @SerialName("num_comments") val numComments: Int? = null,
    @SerialName("created_at_i") val createdAtI: Long? = null,
    @SerialName("story_text") val storyText: String? = null,
)

@Serializable
data class ItemDto(
    val id: Long,
    val type: String? = null,
    val author: String? = null,
    val title: String? = null,
    val url: String? = null,
    val text: String? = null,
    val points: Int? = null,
    @SerialName("created_at_i") val createdAtI: Long = 0,
    val children: List<ItemDto> = emptyList(),
)

/** Maps a hit to a [Story]; null for hits without a title (the web app drops those). */
fun HitDto.toStory(): Story? {
    val t = title?.takeIf { it.isNotEmpty() } ?: return null
    return Story(
        id = objectID,
        title = t,
        url = url?.takeIf { it.isNotEmpty() },
        author = author.orEmpty(),
        points = points ?: 0,
        numComments = numComments ?: 0,
        createdAtEpochSec = createdAtI ?: 0,
        storyText = storyText?.takeIf { it.isNotEmpty() },
    )
}

fun ItemDto.toComment(): Comment =
    Comment(
        id = id,
        author = author,
        text = text,
        createdAtEpochSec = createdAtI,
        children = children.map { it.toComment() },
    )
