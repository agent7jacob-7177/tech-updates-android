package com.code7.techupdates.ui.reader

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.data.remote.ReaderException
import com.code7.techupdates.data.remote.ReaderFailure
import com.code7.techupdates.ui.feed.FakeLearner
import com.code7.techupdates.ui.feed.story
import com.code7.techupdates.ui.library.FakeLibrarySource
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.awaitCondition
import com.code7.techupdates.uitest.awaitDescription
import com.code7.techupdates.uitest.awaitText
import com.code7.techupdates.uitest.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScreenTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val lookup = FakeStoryLookup()
    private val library = FakeLibrarySource()
    private val articles = FakeArticles()
    private val article = "The quick brown fox explains the new compiler. ".repeat(30).trim()

    private fun launch() {
        val viewModel = ReaderViewModel("1", lookup, library, articles, FakeLearner(), nowMillis = { 5_000_000L })
        compose.setContent {
            Code7Theme(dynamicColor = false) {
                ReaderRoute(onBack = {}, onOpenComments = {}, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    private fun cacheStory() = lookup.remember(story("1", "A new compiler"))

    private fun openedUrl(): String? {
        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        return intent?.dataString
    }

    @Test
    fun article_rendersHeadlineAndText() {
        cacheStory()
        articles.result = Result.success("## What changed\n\n$article")
        launch()
        compose.onNodeWithText("A new compiler").assertIsDisplayed()
        compose.awaitText("What changed")
        compose.awaitText("The quick brown fox", substring = true)
        compose.onNodeWithText(string(R.string.reader_open_in_browser)).assertDoesNotExist()
    }

    @Test
    fun articleFailure_offersOpenInBrowser() {
        cacheStory()
        articles.result = Result.failure(ReaderException(ReaderFailure.RATE_LIMITED, status = 429))
        launch()
        compose.awaitText(string(R.string.reader_note_rate_limited))
        compose.onNodeWithText(string(R.string.reader_open_in_browser)).performScrollTo().performClick()
        assertEquals("https://www.example.com/1", openedUrl())
    }

    @Test
    fun storyFailure_offersRetryAndHackerNews() {
        launch()
        compose.awaitText(string(R.string.reader_story_error_title))
        compose.onNodeWithText(string(R.string.reader_open_on_hn)).performClick()
        assertEquals(hnLink("1"), openedUrl())

        lookup.fetchResult = Result.success(story("1", "Recovered story"))
        compose.onNodeWithText(string(R.string.reader_retry)).performClick()
        compose.awaitText("Recovered story")
    }

    @Test
    fun saveToggle_savesAndRemoves() {
        cacheStory()
        articles.result = Result.success(article)
        launch()
        compose.onNodeWithContentDescription(string(R.string.reader_save)).performClick()
        compose.awaitDescription(string(R.string.reader_unsave))
        compose.awaitText(string(R.string.reader_saved_later))
        assertEquals(listOf("1"), library.later.value.map { it.story.id })

        compose.onNodeWithContentDescription(string(R.string.reader_unsave)).performClick()
        compose.awaitDescription(string(R.string.reader_save))
        compose.awaitCondition { library.later.value.isEmpty() }
    }
}
