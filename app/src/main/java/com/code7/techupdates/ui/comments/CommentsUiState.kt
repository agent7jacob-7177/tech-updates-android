package com.code7.techupdates.ui.comments

import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.ThreadNode
import com.code7.techupdates.domain.domainOf
import com.code7.techupdates.ui.reader.hnLink

/** The thread area below the story header (JS `#r-comments`). */
sealed interface CommentsContent {
    data object Loading : CommentsContent

    /** The thread couldn't be loaded (JS "Couldn't load comments. Open them on Hacker News"). */
    data object Error : CommentsContent

    /** The story has no comments with text (JS "No comments yet."). */
    data object Empty : CommentsContent

    /**
     * [rows] are the visible comments in display order; [collapsed] the nodes whose replies are
     * hidden; [total] the number of comments in the thread.
     */
    data class Ready(
        val rows: List<ThreadNode>,
        val collapsed: Set<Long>,
        val total: Int,
    ) : CommentsContent
}

data class CommentsUiState(
    val storyId: String,
    /** Null until the story is known (from the feed cache or the thread response). */
    val story: Story? = null,
    val content: CommentsContent = CommentsContent.Loading,
    val nowEpochSec: Long = 0,
) {
    val domain: String get() = domainOf(story?.url)

    /** "Open on HN": the thread on news.ycombinator.com, available in every state. */
    val hnUrl: String get() = hnLink(storyId)
}
