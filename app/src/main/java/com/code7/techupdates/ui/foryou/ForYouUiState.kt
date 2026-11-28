package com.code7.techupdates.ui.foryou

import com.code7.techupdates.recommender.Ranking
import com.code7.techupdates.ui.feed.StoryCardUi

/** The panel above the picks (JS `renderInsights`), as of the last load. */
data class Insights(
    /** Enough stories engaged for the model to rank the feed. */
    val isReady: Boolean,
    /** Distinct stories opened, saved or read. */
    val positives: Int,
    /** Learned interest labels; empty during cold start or while still learning. */
    val interests: List<String>,
) {
    /** Progress toward [Ranking.MIN_SIGNALS], capped at it. */
    val progress: Int get() = minOf(positives, Ranking.MIN_SIGNALS)
}

sealed interface ForYouContent {
    data object Loading : ForYouContent

    /** Every source failed or returned nothing. */
    data object Error : ForYouContent

    data class Picks(
        val cards: List<StoryCardUi>,
        /** Picks hidden because they were read since the list was built. */
        val hiddenCount: Int,
        /** Reference time for `timeAgo` labels, and when the list was built. */
        val updatedAtEpochSec: Long,
    ) : ForYouContent
}

data class ForYouUiState(
    val content: ForYouContent = ForYouContent.Loading,
    /** Null until the first load has read the learner. */
    val insights: Insights? = null,
    /** Pull-to-refresh in progress (content stays visible). */
    val isRefreshing: Boolean = false,
)

/** The search text and topic chip that narrow the picks. */
data class ForYouFilter(
    val query: String = "",
    val topicId: String = "",
)
