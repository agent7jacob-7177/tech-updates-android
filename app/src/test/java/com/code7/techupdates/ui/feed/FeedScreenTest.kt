package com.code7.techupdates.ui.feed

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.domain.FeedType
import com.code7.techupdates.ui.foryou.FakeCandidates
import com.code7.techupdates.ui.foryou.FakeLearnerData
import com.code7.techupdates.ui.foryou.ForYouViewModel
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.awaitCondition
import com.code7.techupdates.uitest.awaitText
import com.code7.techupdates.uitest.string
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedScreenTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val source = FakeFeedSource()
    private val library = FakeLibrary()
    private val settings = FakeSettings()
    private val opened = mutableListOf<String>()

    private fun launch() {
        val feed = FeedViewModel(source, library, FakeLearner(), settings, nowEpochSec = { NOW })
        val forYou =
            ForYouViewModel(
                source = FakeCandidates(listOf(story("9", "A pick for you"))),
                library = library,
                learner = FakeLearnerData(),
                nowEpochSec = { NOW },
                computeDispatcher = Dispatchers.Main,
            )
        compose.setContent {
            Code7Theme(dynamicColor = false) {
                FeedRoute(
                    onOpenStory = { opened += it },
                    onOpenComments = {},
                    viewModel = feed,
                    forYouViewModel = forYou,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tab(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    @Test
    fun list_rendersTitles_andCardTapOpensStory() {
        launch()
        compose.onNodeWithText("Story 1").assertIsDisplayed()
        compose.onNodeWithText("Story 2").assertIsDisplayed().performClick()
        assertEquals(listOf("2"), opened)
    }

    @Test
    fun tabs_switchFeeds() {
        launch()
        tab(R.string.feed_tab_top).assertIsSelected()

        tab(R.string.feed_tab_latest).performClick()
        compose.waitForIdle()
        tab(R.string.feed_tab_latest).assertIsSelected()
        assertEquals(FeedType.LATEST, source.lastQuery.feed)

        tab(R.string.feed_tab_this_week).performClick()
        compose.waitForIdle()
        assertEquals(FeedType.WEEK, source.lastQuery.feed)
        assertEquals("week", settings.selectedFeed.value)

        tab(R.string.feed_tab_for_you).performClick()
        compose.awaitText("A pick for you")
        tab(R.string.feed_tab_for_you).assertIsSelected()
        assertEquals("foryou", settings.selectedFeed.value)
    }

    @Test
    fun topicChip_selectsTopicAndReloads() {
        launch()
        tab(R.string.topic_all).assertIsSelected()
        tab(R.string.topic_security).performScrollTo().performClick()
        compose.waitForIdle()
        tab(R.string.topic_security).assertIsSelected()
        assertEquals("Security", source.lastQuery.topicId)
        assertEquals("Security", settings.selectedTopic.value)
    }

    @Test
    fun bookmark_toggles_andUndoReverts() {
        launch()
        compose.onAllNodesWithContentDescription(string(R.string.card_read_later)).onFirst().performClick()
        compose.awaitText(string(R.string.feed_saved_later))
        compose.onNodeWithContentDescription(string(R.string.card_remove_later)).assertIsDisplayed()
        assertEquals(setOf("1"), library.later.value)

        compose.onNodeWithText(string(R.string.feed_undo)).performClick()
        compose.awaitCondition { library.later.value.isEmpty() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(string(R.string.card_remove_later)).assertDoesNotExist()
    }

    @Test
    fun error_retryReloads() {
        source.responses[0] = Result.failure(IOException("offline"))
        launch()
        compose.onNodeWithText(string(R.string.feed_error_title)).assertIsDisplayed()

        source.responses[0] = Result.success(page("7"))
        compose.onNodeWithText(string(R.string.feed_retry)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Story 7").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.feed_error_title)).assertDoesNotExist()
        assertEquals(true, source.requests.last().second)
    }

    @Test
    fun emptyFeed_showsEmptyState() {
        source.responses[0] = Result.success(page())
        launch()
        compose.onNodeWithText(string(R.string.feed_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.feed_empty_subtitle)).assertIsDisplayed()
    }

    @Test
    fun offlinePage_showsBannerAboveSavedStories() {
        // The saved page was fetched at 500s, two hours before NOW.
        source.responses[0] = Result.success(page("1", offline = true))
        launch()
        compose.onNodeWithText(string(R.string.feed_offline_banner, "2h ago")).assertIsDisplayed()
        compose.onNodeWithText("Story 1").assertIsDisplayed()
    }

    private companion object {
        const val NOW = 7_700L
    }
}
