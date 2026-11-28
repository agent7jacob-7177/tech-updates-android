package com.code7.techupdates.ui.feed

import com.code7.techupdates.domain.FeedType
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic

/** Feed screen tabs; [id] is the web app's tab id persisted in settings. [type] is null for "For you". */
enum class FeedTab(
    val id: String,
    val type: FeedType?,
) {
    TOP("top", FeedType.TOP),
    LATEST("new", FeedType.LATEST),
    WEEK("week", FeedType.WEEK),
    FOR_YOU("foryou", null),
    ;

    companion object {
        /** Unknown ids (including the library tabs, which live on their own screen) fall back to [TOP]. */
        fun fromId(id: String?): FeedTab = entries.firstOrNull { it.id == id } ?: TOP
    }
}

/** One story card, with everything the card shows precomputed (JS `card(h)`). */
data class StoryCardUi(
    val story: Story,
    /** `domainOf(url) || "news.ycombinator.com"`. */
    val domain: String,
    /** First topic whose matcher matches the title (JS `topicOf`), shown as a tag; null when none. */
    val topic: Topic?,
    /** Plain-text excerpt of Ask HN / Show HN text, "" when none. */
    val excerpt: String,
    val isSaved: Boolean,
) {
    val faviconUrl: String get() = "https://icons.duckduckgo.com/ip3/$domain.ico"

    /** JS: `points >= 300 ? "hot" : ""`. */
    val isHot: Boolean get() = story.points >= HOT_POINTS

    companion object {
        const val HOT_POINTS = 300
        const val FALLBACK_DOMAIN = "news.ycombinator.com"
    }
}

sealed interface FeedContent {
    /** First load of a feed: skeleton cards. */
    data object Loading : FeedContent

    /** Nothing could be loaded (no cache either): error with Retry. */
    data object Error : FeedContent

    /** "For you" placeholder until the recommender lands (#15). */
    data object ComingSoon : FeedContent

    data class Stories(
        /** Visible cards; already-read stories are hidden (JS `render`). */
        val cards: List<StoryCardUi>,
        /** How many loaded stories were hidden because they were already read. */
        val hiddenCount: Int,
        /** Whether the first card is shown "featured" (bigger title). */
        val showFeatured: Boolean,
        val canLoadMore: Boolean,
        val isAppending: Boolean,
        val appendFailed: Boolean,
        /** When non-null the network failed and these are saved stories fetched at this time. */
        val offlineSinceEpochSec: Long?,
        /** Reference time for `timeAgo` labels. */
        val nowEpochSec: Long,
    ) : FeedContent {
        val isEmpty: Boolean get() = cards.isEmpty()
    }
}

data class FeedUiState(
    val tab: FeedTab = FeedTab.TOP,
    val topicId: String = "",
    /** Raw search field text (the applied query is debounced and trimmed). */
    val searchText: String = "",
    val content: FeedContent = FeedContent.Loading,
    /** Pull-to-refresh in progress (content stays visible). */
    val isRefreshing: Boolean = false,
)

/** One-off events for the UI. */
sealed interface FeedEvent {
    /** A bookmark was toggled; show a snackbar with Undo. */
    data class LaterToggled(
        val story: Story,
        val saved: Boolean,
    ) : FeedEvent
}
