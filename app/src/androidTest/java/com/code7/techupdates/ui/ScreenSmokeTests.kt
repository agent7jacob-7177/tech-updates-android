package com.code7.techupdates.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.MainActivity
import com.code7.techupdates.R
import com.code7.techupdates.recommender.Ranking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One smoke test per screen: the real [MainActivity] and navigation graph on top of [FakeAppRule]. */
@RunWith(AndroidJUnit4::class)
class ScreenSmokeTests {
    @get:Rule(order = 0)
    val fakeApp = FakeAppRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_showsNewsFeed() {
        compose.enableAccessibilityChecks()
        await(hasText(FakeAppRule.FIRST_STORY))
        await(hasText(FakeAppRule.SECOND_STORY))
        clickable(R.string.nav_news).assertIsSelected()
    }

    @Test
    @Offline
    fun launch_offline_showsErrorState() {
        compose.enableAccessibilityChecks()
        await(hasText(string(R.string.feed_error_title)))
        clickable(R.string.feed_retry).assertIsDisplayed()
        clickable(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun openFirstStory_showsReader_andBackReturnsToNews() {
        compose.enableAccessibilityChecks()
        await(hasText(FakeAppRule.FIRST_STORY))
        compose.onNode(hasText(FakeAppRule.FIRST_STORY)).performClick()

        await(hasText(FakeAppRule.ARTICLE_TEXT, substring = true))
        await(hasContentDescription(string(R.string.reader_open_original)))
        // The reader is full screen; this also waits for the feed to leave after the transition.
        awaitGone(hasText(string(R.string.nav_library)))

        compose.onNode(hasContentDescription(string(R.string.icon_back))).performClick()
        awaitGone(hasContentDescription(string(R.string.reader_open_original)))
        await(hasText(string(R.string.nav_library)))
        clickable(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun bottomBar_opensLibrary_andReturnsToNews() {
        compose.enableAccessibilityChecks()
        await(hasText(FakeAppRule.FIRST_STORY))
        clickable(R.string.nav_library).performClick()
        await(hasText(string(R.string.library_later_empty_title)))
        awaitGone(hasText(FakeAppRule.FIRST_STORY))
        clickable(R.string.nav_library).assertIsSelected()

        clickable(R.string.nav_news).performClick()
        await(hasText(FakeAppRule.FIRST_STORY))
        clickable(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun forYouTab_showsColdStartPanel() {
        compose.enableAccessibilityChecks()
        await(hasText(FakeAppRule.FIRST_STORY))
        clickable(R.string.feed_tab_for_you).performClick()

        await(hasText(string(R.string.for_you_teach_title)))
        await(hasText(string(R.string.for_you_progress, 0, Ranking.MIN_SIGNALS)))
        clickable(R.string.feed_tab_for_you).assertIsSelected()
    }

    @Test
    fun overflowMenu_opensSettings() {
        compose.enableAccessibilityChecks()
        await(hasContentDescription(string(R.string.feed_more_options)))
        compose.onNode(hasContentDescription(string(R.string.feed_more_options))).performClick()
        await(hasText(string(R.string.settings_open)))
        clickable(R.string.settings_open).performClick()

        await(hasText(string(R.string.settings_section_appearance)))
        compose.onNode(hasText(string(R.string.settings_section_appearance))).assertIsDisplayed()
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = compose.activity.getString(id, *args)

    private fun clickable(
        @StringRes label: Int,
    ) = compose.onNode(hasText(string(label)) and hasClickAction())

    /** Waits for work outside Compose's clock (Ktor, Room, Markdown parsing) to show a matching node. */
    private fun await(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun awaitGone(matcher: SemanticsMatcher) =
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }

    private companion object {
        /** Generous: software-rendered CI emulators can stall for seconds. */
        const val TIMEOUT_MILLIS = 30_000L
    }
}
