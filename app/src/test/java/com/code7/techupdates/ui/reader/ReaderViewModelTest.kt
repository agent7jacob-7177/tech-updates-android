package com.code7.techupdates.ui.reader

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.code7.techupdates.data.ReadHow
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.data.remote.ReaderException
import com.code7.techupdates.data.remote.ReaderFailure
import com.code7.techupdates.ui.feed.FakeLearner
import com.code7.techupdates.ui.feed.MainDispatcherRule
import com.code7.techupdates.ui.feed.story
import com.code7.techupdates.ui.library.FakeLibrarySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val stories = FakeStoryLookup()
    private val library = FakeLibrarySource()
    private val articles = FakeArticles()
    private val learner = FakeLearner()
    private var now = 1_000_000L

    private val longArticle = "# Story 1\n\n" + List(300) { "word$it" }.joinToString(" ") + "\n\nSee [more](/more)."

    private fun viewModel(id: String = "1") =
        ReaderViewModel(id, stories, library, articles, learner, nowMillis = { now })

    private suspend fun ReceiveTurbine<ReaderUiState>.awaitUntil(predicate: (ReaderUiState) -> Boolean): ReaderUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private val ReaderUiState.ready get() = body as? ReaderBody.Ready

    /** Keeps uiState hot and exposes the events turbine. */
    private fun ReaderViewModel.test(
        block: suspend ReceiveTurbine<ReaderUiState>.(ReceiveTurbine<ReaderEvent>) -> Unit,
    ) = runTest {
        uiState.test {
            val states = this
            uiEvents.test { states.block(this) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- resolving the story

    @Test
    fun cachedStory_showsArticleWithReadingTime() =
        runTest {
            stories.cached["1"] = story("1")
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitUntil { it.ready != null }
                assertEquals("1", state.story?.story?.id)
                assertEquals("example.com", state.story?.domain)
                assertEquals("https://www.example.com/1", state.browserUrl)
                val body = state.ready!!
                // duplicate headline dropped, relative link resolved against the article URL
                assertFalse(body.markdown!!.startsWith("#"))
                assertTrue(body.markdown.contains("[more](https://www.example.com/more)"))
                assertNull(body.note)
                assertEquals(1, body.readingMinutes) // 302 words / 230 = 1.3
                assertEquals(1_000L, state.nowEpochSec)
            }
            assertEquals(listOf("https://www.example.com/1"), articles.requests)
            assertEquals(listOf(SignalKind.OPEN to "1"), learner.recorded)
            assertEquals(0, stories.fetches)
        }

    @Test
    fun whileArticleLoads_bodyIsLoading() =
        runTest {
            stories.cached["1"] = story("1")
            articles.gate = CompletableDeferred()
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.uiState.test {
                val loading = awaitUntil { it.story != null }
                assertEquals(ReaderBody.Loading, loading.body)
                assertFalse(loading.isLoadingStory)
                articles.gate!!.complete(Unit)
                awaitUntil { it.ready != null }
            }
        }

    @Test
    fun storyFromLibrary_whenNotInFeedCache() =
        runTest {
            library.save(story("1", title = "Saved one"))
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitUntil { it.ready != null }
                assertEquals("Saved one", state.story?.story?.title)
                assertTrue(state.isSaved)
            }
            assertEquals(0, stories.fetches)
            assertEquals("Saved one", stories.cached["1"]?.title)
        }

    @Test
    fun deepLink_fetchesStoryById() =
        runTest {
            stories.fetchResult = Result.success(story("42"))
            articles.result = Result.success(longArticle)
            val vm = viewModel("42")
            assertTrue(vm.uiState.value.isLoadingStory)
            vm.uiState.test {
                val state = awaitUntil { it.ready != null }
                assertEquals("42", state.story?.story?.id)
            }
            assertEquals(1, stories.fetches)
            assertTrue("42" in stories.cached)
        }

    @Test
    fun storyLoadFails_thenRetrySucceeds() =
        runTest {
            val vm = viewModel("42")
            vm.uiState.test {
                val failed = awaitUntil { it.storyFailed }
                assertNull(failed.story)
                assertFalse(failed.isLoadingStory)
                assertEquals("https://news.ycombinator.com/item?id=42", failed.browserUrl)
                stories.fetchResult = Result.success(story("42"))
                articles.result = Result.success(longArticle)
                vm.retry()
                val state = awaitUntil { it.ready != null }
                assertFalse(state.storyFailed)
            }
            assertTrue(learner.recorded == listOf(SignalKind.OPEN to "42"))
        }

    // ---- self posts

    @Test
    fun askHn_withoutUrl_showsStoryTextWithoutFetching() =
        runTest {
            val text = "<p>" + List(100) { "w$it" }.joinToString(" ") + "</p>"
            stories.cached["1"] = story("1", url = null, storyText = text)
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitUntil { it.ready != null }
                assertEquals(ReaderBody.Ready(storyHtml = text, readingMinutes = 1), state.body)
                assertEquals("news.ycombinator.com", state.story?.domain)
                assertEquals("https://news.ycombinator.com/item?id=1", state.browserUrl)
            }
            assertTrue(articles.requests.isEmpty())
        }

    @Test
    fun shortSelfPost_hasNoReadingTime() =
        runTest {
            stories.cached["1"] = story("1", url = null, storyText = "<p>Short question?</p>")
            val vm = viewModel()
            vm.uiState.test { assertNull(awaitUntil { it.ready != null }.ready!!.readingMinutes) }
        }

    @Test
    fun selfPostWithUrl_showsTextAndArticle() =
        runTest {
            stories.cached["1"] = story("1", storyText = "<p>Look at this</p>")
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.uiState.test {
                val body = awaitUntil { it.ready != null }.ready!!
                assertEquals("<p>Look at this</p>", body.storyHtml)
                assertTrue(body.markdown != null)
            }
        }

    // ---- notes and fallbacks

    @Test
    fun shortArticle_addsShortPreviewNote() =
        runTest {
            stories.cached["1"] = story("1")
            articles.result = Result.success("Subscribe to keep reading.")
            val vm = viewModel()
            vm.uiState.test {
                val body = awaitUntil { it.ready != null }.ready!!
                assertEquals(ReaderNote.SHORT_PREVIEW, body.note)
                assertEquals("Subscribe to keep reading.", body.markdown)
                assertNull(body.readingMinutes)
            }
        }

    private fun assertNoteFor(
        error: Throwable,
        expected: ReaderNote,
    ) = runTest {
        stories.cached["1"] = story("1")
        articles.result = Result.failure(error)
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitUntil { it.ready != null }
            assertEquals(ReaderBody.Ready(note = expected), state.body)
            assertEquals("https://www.example.com/1", state.browserUrl)
        }
    }

    @Test
    fun rateLimited_showsRateLimitNote() =
        assertNoteFor(ReaderException(ReaderFailure.RATE_LIMITED, 429), ReaderNote.RATE_LIMITED)

    @Test
    fun blocked_showsBlockedNote() = assertNoteFor(ReaderException(ReaderFailure.BLOCKED, 451), ReaderNote.BLOCKED)

    @Test
    fun serverError_showsFailedNote() =
        assertNoteFor(ReaderException(ReaderFailure.SERVER, 503), ReaderNote.FETCH_FAILED)

    @Test
    fun timeout_showsFailedNote() = assertNoteFor(ReaderException(ReaderFailure.TIMEOUT), ReaderNote.FETCH_FAILED)

    @Test
    fun unknownError_showsFailedNote() = assertNoteFor(IllegalStateException("boom"), ReaderNote.FETCH_FAILED)

    @Test
    fun retryAfterArticleFailure_fetchesAgain() =
        runTest {
            stories.cached["1"] = story("1")
            articles.result = Result.failure(ReaderException(ReaderFailure.TIMEOUT))
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { it.ready?.note == ReaderNote.FETCH_FAILED }
                articles.result = Result.success(longArticle)
                vm.retry()
                val body = awaitUntil { it.ready?.markdown != null }.ready!!
                assertNull(body.note)
            }
            assertEquals(2, articles.requests.size)
            assertEquals(listOf(SignalKind.OPEN to "1"), learner.recorded)
        }

    // ---- mark as read

    @Test
    fun reachingEndTooSoon_doesNotMarkRead() =
        run {
            stories.cached["1"] = story("1")
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.ready != null }
                now += 6_000L // 302 words -> needs > 18.12 s
                vm.onReachedEnd()
                events.expectNoEvents()
                assertTrue(library.read.value.isEmpty())
            }
        }

    @Test
    fun reachingEndAfterDelay_marksReadAutomatically() =
        run {
            stories.cached["1"] = story("1")
            library.save(story("1"))
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.ready != null }
                now += 18_121L
                vm.onReachedEnd()
                val event = events.awaitItem() as ReaderEvent.MarkedRead
                assertTrue(event.auto)
                assertTrue(event.receipt.wasLater)
                val state = awaitUntil { it.isRead }
                assertFalse(state.isSaved)
                assertEquals(listOf("1" to ReadHow.AUTO), library.readHows)
                // further scrolling doesn't mark again
                vm.onReachedEnd()
                events.expectNoEvents()
            }
        }

    @Test
    fun reachingEnd_whileArticleLoading_doesNothing() =
        run {
            stories.cached["1"] = story("1")
            articles.gate = CompletableDeferred()
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.story != null }
                now += 60_000L
                vm.onReachedEnd()
                events.expectNoEvents()
                articles.gate!!.complete(Unit)
            }
            assertTrue(library.readHows.isEmpty())
        }

    @Test
    fun alreadyRead_reachingEndDoesNothing() =
        run {
            stories.cached["1"] = story("1")
            library.addRead(story("1"))
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.ready != null && it.isRead }
                now += 60_000L
                vm.onReachedEnd()
                events.expectNoEvents()
            }
        }

    @Test
    fun continueOnSite_marksReadImmediately_andUndoRestores() =
        run {
            stories.cached["1"] = story("1")
            library.save(story("1"))
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.ready != null && it.isSaved }
                vm.onContinueOnSite()
                val event = events.awaitItem() as ReaderEvent.MarkedRead
                assertFalse(event.auto)
                awaitUntil { it.isRead && !it.isSaved }
                vm.undo(event)
                awaitUntil { !it.isRead && it.isSaved }
            }
            assertEquals(listOf("1" to ReadHow.CONTINUE), library.readHows)
            assertFalse(SignalKind.READ to "1" in library.signals)
        }

    // ---- read later

    @Test
    fun toggleLater_savesAndUndoUnsaves() =
        run {
            stories.cached["1"] = story("1")
            articles.result = Result.success(longArticle)
            val vm = viewModel()
            vm.test { events ->
                awaitUntil { it.story != null && !it.isSaved }
                vm.toggleLater()
                val event = events.awaitItem()
                assertEquals(ReaderEvent.LaterToggled(saved = true), event)
                awaitUntil { it.isSaved }
                vm.undo(event)
                awaitUntil { !it.isSaved }
            }
        }

    @Test
    fun toggleLater_beforeStoryLoaded_doesNothing() =
        run {
            val vm = viewModel("42")
            vm.test { events ->
                awaitUntil { it.storyFailed }
                vm.toggleLater()
                events.expectNoEvents()
            }
            assertTrue(library.later.value.isEmpty())
        }
}
