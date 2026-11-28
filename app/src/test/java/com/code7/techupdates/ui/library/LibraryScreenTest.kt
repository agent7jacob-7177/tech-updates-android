package com.code7.techupdates.ui.library

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.ui.feed.FakeFeedSource
import com.code7.techupdates.ui.feed.FakeLearner
import com.code7.techupdates.ui.feed.story
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.awaitCondition
import com.code7.techupdates.uitest.awaitText
import com.code7.techupdates.uitest.performCustomAction
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
class LibraryScreenTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val library = FakeLibrarySource()

    private fun launch() {
        val viewModel = LibraryViewModel(library, FakeFeedSource(), FakeLearner(), nowEpochSec = { 5_000L })
        compose.setContent {
            Code7Theme(dynamicColor = false) {
                LibraryRoute(onOpenStory = {}, onOpenComments = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun seed() {
        library.save(story("1", "Saved one"), story("2", "Saved two"))
        library.addRead(story("3", "Finished one"), story("4", "Finished two"), story("5", "Finished three"))
    }

    private fun tab(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    private fun card(title: String) =
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
                hasAnyDescendant(hasText(title)),
        )

    private fun laterIds() = library.later.value.map { it.story.id }

    @Test
    fun tabs_showCountsAndTheirOwnStories() {
        seed()
        launch()
        tab(R.string.library_tab_later).assertIsSelected().assert(hasText("2"))
        tab(R.string.library_tab_read).assert(hasText("3"))
        compose.onNodeWithText("Saved one").assertIsDisplayed()
        compose.onNodeWithText("Finished one").assertDoesNotExist()

        tab(R.string.library_tab_read).performClick()
        compose.waitForIdle()
        tab(R.string.library_tab_read).assertIsSelected()
        compose.onNodeWithText("Finished one").assertIsDisplayed()
        compose.onNodeWithText("Saved one").assertDoesNotExist()
    }

    @Test
    fun swipeLeft_removes_andUndoRestores() {
        seed()
        launch()
        compose.onNodeWithText("Saved one").performTouchInput { swipeLeft() }
        compose.awaitText(string(R.string.library_removed_from_later))
        assertEquals(listOf("2"), laterIds())

        compose.onNodeWithText(string(R.string.library_undo)).performClick()
        compose.awaitText("Saved one")
        assertEquals(setOf("1", "2"), laterIds().toSet())
    }

    @Test
    fun removeAccessibilityAction_inReadTab_showsUndo() {
        seed()
        launch()
        tab(R.string.library_tab_read).performClick()
        compose.waitForIdle()
        compose.performCustomAction(card("Finished two"), string(R.string.library_remove))
        compose.awaitText(string(R.string.library_removed_from_read))
        compose.onNodeWithText("Finished two").assertDoesNotExist()

        compose.onNodeWithText(string(R.string.library_undo)).performClick()
        compose.awaitText("Finished two")
        assertEquals(3, library.read.value.size)
    }

    @Test
    fun markReadAccessibilityAction_movesToRead_andUndo() {
        seed()
        launch()
        compose.performCustomAction(card("Saved two"), string(R.string.library_mark_read))
        compose.awaitText(string(R.string.library_marked_as_read))
        tab(R.string.library_tab_read).assert(hasText("4"))

        compose.onNodeWithText(string(R.string.library_undo)).performClick()
        compose.awaitCondition { library.read.value.size == 3 }
        compose.awaitText("Saved two")
    }

    @Test
    fun emptyTabs_showTheirOwnMessages() {
        launch()
        compose.onNodeWithText(string(R.string.library_later_empty_title)).assertIsDisplayed()
        tab(R.string.library_tab_read).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.library_read_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.library_clear_all)).assertDoesNotExist()
    }

    @Test
    fun clearAll_confirmsThenEmptiesHistory() {
        seed()
        launch()
        tab(R.string.library_tab_read).performClick()
        compose.onNodeWithText(string(R.string.library_clear_all)).performClick()
        compose.onNodeWithText(string(R.string.library_clear_title)).assertIsDisplayed()
        compose
            .onNode(hasText(string(R.string.library_clear_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()
        compose.awaitText(string(R.string.library_history_cleared))
        compose.onNodeWithText(string(R.string.library_read_empty_title)).assertIsDisplayed()
        assertEquals(0, library.read.value.size)
    }
}
