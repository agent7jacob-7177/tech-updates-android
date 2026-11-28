package com.code7.techupdates.ui.foryou

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.recommender.Ranking
import com.code7.techupdates.ui.feed.FeedActions
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiFixtures
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.plural
import com.code7.techupdates.uitest.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForYouListTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var resets = 0

    private fun show(state: ForYouUiState) =
        compose.setContent {
            Code7Theme(dynamicColor = false) {
                ForYouList(state, ForYouActions(onReset = { resets++ }), FeedActions())
            }
        }

    private fun dialogButton(label: Int) = compose.onNode(hasText(string(label)) and hasAnyAncestor(isDialog()))

    @Test
    fun coldStart_showsProgressMeter() {
        show(UiFixtures.forYouCold)
        compose.onNodeWithText(string(R.string.for_you_teach_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.for_you_progress, 2, Ranking.MIN_SIGNALS)).assertIsDisplayed()
        compose
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(2f / Ranking.MIN_SIGNALS, 0f..1f)))
            .assertIsDisplayed()
        compose.onNodeWithText(string(R.string.for_you_status_cold)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.for_you_reset)).assertDoesNotExist()
    }

    @Test
    fun ranked_showsInterestsAndWhyLine() {
        show(UiFixtures.forYouRanked)
        compose.onNodeWithText(string(R.string.for_you_interests_title)).assertIsDisplayed()
        compose.onNodeWithText(plural(R.plurals.for_you_learned_from, 12)).assertIsDisplayed()
        compose.onNodeWithText("openai.com").assertIsDisplayed()
        val reasons = string(R.string.card_why_join, "AI", "anthropic.com")
        compose.onNodeWithText(string(R.string.card_why, reasons)).assertIsDisplayed()
    }

    @Test
    fun reset_asksForConfirmation_andCancelKeepsLearning() {
        show(UiFixtures.forYouRanked)
        compose.onNodeWithText(string(R.string.for_you_reset)).performClick()
        compose.onNodeWithText(string(R.string.for_you_reset_confirm)).assertIsDisplayed()

        dialogButton(R.string.for_you_cancel).performClick()
        compose.onNodeWithText(string(R.string.for_you_reset_confirm)).assertDoesNotExist()
        assertEquals(0, resets)
    }

    @Test
    fun reset_confirmed_resets() {
        show(UiFixtures.forYouRanked)
        compose.onNodeWithText(string(R.string.for_you_reset)).performClick()
        dialogButton(R.string.for_you_reset).performClick()
        compose.onNodeWithText(string(R.string.for_you_reset_confirm)).assertDoesNotExist()
        assertEquals(1, resets)
    }
}
