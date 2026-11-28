package com.code7.techupdates.ui.foryou

import com.code7.techupdates.ui.feed.FeedActions

/** User actions specific to "For you"; card actions are the feed's [FeedActions]. */
data class ForYouActions(
    val onShow: (ForYouFilter) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onReset: () -> Unit = {},
)
