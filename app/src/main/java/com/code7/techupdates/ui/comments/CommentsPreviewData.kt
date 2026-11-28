package com.code7.techupdates.ui.comments

import com.code7.techupdates.domain.Comment
import com.code7.techupdates.domain.CommentThread
import com.code7.techupdates.domain.Story

/** Sample data for previews. */
internal object CommentsPreviewData {
    const val NOW = 1_795_822_000L

    val story =
        Story(
            id = "1",
            title = "WebRTC leaks are back, now via browser extensions",
            url = "https://www.example.com/webrtc",
            author = "scanr",
            points = 412,
            numComments = 5,
            createdAtEpochSec = NOW - 7_200,
            storyText = null,
        )

    private val comments =
        listOf(
            Comment(
                id = 1,
                author = "barbaudue710",
                text = "WebRTC is <i>wild</i>. See <a href=\"https://example.com/rfc\">the RFC</a>.",
                createdAtEpochSec = NOW - 5_400,
                children =
                    listOf(
                        Comment(
                            id = 2,
                            author = null,
                            text = null,
                            createdAtEpochSec = 0,
                            children =
                                listOf(
                                    Comment(
                                        3,
                                        "mlhpdx",
                                        "Or DNS lookups.",
                                        NOW - 900,
                                        children =
                                            listOf(
                                                Comment(
                                                    4,
                                                    "pg",
                                                    "Exactly.<p>Use <code>dig</code>.",
                                                    NOW - 60,
                                                    emptyList(),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ),
            Comment(5, "javcasas", "Time to disable it by default.", NOW - 3_600, emptyList()),
        )

    val thread = CommentThread.of(comments)

    val rows = thread.visible(setOf(3L))

    val ready =
        CommentsUiState(
            storyId = story.id,
            story = story,
            content = CommentsContent.Ready(rows, setOf(3L), thread.total),
            nowEpochSec = NOW,
        )
}
