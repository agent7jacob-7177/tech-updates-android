package com.code7.techupdates.uitest

import com.code7.techupdates.ui.feed.FeedContent
import com.code7.techupdates.ui.feed.FeedTab
import com.code7.techupdates.ui.feed.FeedUiState
import com.code7.techupdates.ui.feed.PreviewData
import com.code7.techupdates.ui.foryou.ForYouContent
import com.code7.techupdates.ui.foryou.ForYouUiState
import com.code7.techupdates.ui.foryou.Insights

/** Prebuilt UI states with a fixed clock ([PreviewData.NOW]), shared by UI and screenshot tests. */
object UiFixtures {
    val feed =
        FeedUiState(
            content =
                FeedContent.Stories(
                    cards = PreviewData.cards,
                    hiddenCount = 0,
                    showFeatured = true,
                    canLoadMore = false,
                    isAppending = false,
                    appendFailed = false,
                    offlineSinceEpochSec = null,
                    nowEpochSec = PreviewData.NOW,
                ),
        )

    val forYouTab = FeedUiState(tab = FeedTab.FOR_YOU, content = FeedContent.ForYou)

    private val picks =
        ForYouContent.Picks(
            cards =
                listOf(
                    PreviewData.cards[0].copy(isSaved = false, why = listOf("AI", "anthropic.com")),
                    PreviewData.cards[1].copy(why = listOf("rust")),
                ),
            hiddenCount = 0,
            updatedAtEpochSec = PreviewData.NOW,
        )

    val forYouRanked =
        ForYouUiState(
            content = picks,
            insights = Insights(isReady = true, positives = 12, interests = listOf("AI", "openai.com", "rust")),
        )

    val forYouCold =
        ForYouUiState(
            content = picks.copy(cards = PreviewData.cards),
            insights = Insights(isReady = false, positives = 2, interests = emptyList()),
        )
}
