package com.code7.techupdates.ui.feed

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.FeedType
import com.code7.techupdates.domain.Topic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val source = FakeFeedSource()
    private val library = FakeLibrary()
    private val learner = FakeLearner()

    private fun viewModel(settings: FakeSettings = FakeSettings()) =
        FeedViewModel(source, library, learner, settings, nowEpochSec = { 2_000L })

    private suspend fun ReceiveTurbine<FeedUiState>.awaitUntil(predicate: (FeedUiState) -> Boolean): FeedUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private suspend fun ReceiveTurbine<FeedUiState>.awaitStories(): FeedContent.Stories =
        awaitUntil { it.content is FeedContent.Stories }.content as FeedContent.Stories

    private val FeedUiState.stories get() = content as FeedContent.Stories
    private val FeedContent.Stories.ids get() = cards.map { it.story.id }

    // ---- initial load / restore

    @Test
    fun initialLoad_restoresSelection_showsSkeleton_thenStories() =
        runTest {
            source.gate = CompletableDeferred()
            val settings = FakeSettings(feed = "week", topic = "AI")
            val vm = viewModel(settings)
            vm.uiState.test {
                val loading = awaitUntil { it.tab == FeedTab.WEEK }
                assertEquals(FeedContent.Loading, loading.content)
                assertEquals("AI", loading.topicId)

                source.gate!!.complete(Unit)
                val stories = awaitStories()
                assertEquals(listOf("1", "2"), stories.ids)
                assertTrue(stories.showFeatured)
                assertFalse(stories.canLoadMore)
                assertNull(stories.offlineSinceEpochSec)
                assertEquals(2_000L, stories.nowEpochSec)
            }
            assertEquals(listOf(FeedQuery(FeedType.WEEK, "AI") to false), source.requests)
        }

    @Test
    fun initialLoad_libraryOrUnknownFeed_fallsBackToTop() =
        runTest {
            val vm = viewModel(FakeSettings(feed = "later"))
            vm.uiState.test { assertEquals(FeedTab.TOP, awaitUntil { it.content is FeedContent.Stories }.tab) }
            assertEquals(FeedType.TOP, source.lastQuery.feed)
        }

    @Test
    fun initialLoad_restoredForYou_showsForYouContentWithoutRequest() =
        runTest {
            val vm = viewModel(FakeSettings(feed = "foryou"))
            vm.uiState.test {
                val state = awaitUntil { it.content == FeedContent.ForYou }
                assertEquals(FeedTab.FOR_YOU, state.tab)
            }
            assertTrue(source.requests.isEmpty())
        }

    @Test
    fun offlinePage_showsOfflineBanner() =
        runTest {
            source.responses[0] = Result.success(page("1", offline = true))
            val vm = viewModel()
            vm.uiState.test { assertEquals(500L, awaitStories().offlineSinceEpochSec) }
        }

    // ---- error / retry

    @Test
    fun failure_showsError_thenRetry_forcesRefresh_viaLoading() =
        runTest {
            source.responses[0] = Result.failure(RuntimeException("boom"))
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { it.content == FeedContent.Error }

                source.responses[0] = Result.success(page("9"))
                source.gate = CompletableDeferred()
                vm.retry()
                awaitUntil { it.content == FeedContent.Loading }
                source.gate!!.complete(Unit)
                assertEquals(listOf("9"), awaitStories().ids)
            }
            assertTrue(source.requests.last().second)
        }

    // ---- pull to refresh

    @Test
    fun refresh_keepsContentVisible_forcesRefresh_thenReplaces() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitStories()
                source.responses[0] = Result.success(page("3"))
                source.gate = CompletableDeferred()
                vm.refresh()
                val refreshing = awaitUntil { it.isRefreshing }
                assertEquals(listOf("1", "2"), refreshing.stories.ids)

                source.gate!!.complete(Unit)
                val done = awaitUntil { !it.isRefreshing }
                assertEquals(listOf("3"), done.stories.ids)
            }
            assertEquals(FeedQuery(FeedType.TOP) to true, source.requests.last())
        }

    @Test
    fun refresh_failure_showsError() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitStories()
                source.responses[0] = Result.failure(RuntimeException("offline, no cache"))
                vm.refresh()
                val state = awaitUntil { it.content == FeedContent.Error }
                assertFalse(state.isRefreshing)
            }
        }

    // ---- paging

    @Test
    fun loadMore_appendsNextPage_dedupesAndTracksPages() =
        runTest {
            source.responses[0] = Result.success(page("1", "2", nbPages = 3))
            source.responses[1] = Result.success(page("2", "3", page = 1, nbPages = 3))
            val vm = viewModel()
            vm.uiState.test {
                assertTrue(awaitStories().canLoadMore)
                source.gate = CompletableDeferred()
                vm.loadMore()
                assertTrue(awaitUntil { (it.content as? FeedContent.Stories)?.isAppending == true }.stories.canLoadMore)

                source.gate!!.complete(Unit)
                val appended = awaitUntil { !it.stories.isAppending }.stories
                assertEquals(listOf("1", "2", "3"), appended.ids)
                assertTrue(appended.canLoadMore)
            }
            assertEquals(FeedQuery(FeedType.TOP, page = 1) to false, source.requests.last())
        }

    @Test
    fun loadMore_onLastPage_orWhileLoading_doesNothing() =
        runTest {
            val vm = viewModel()
            vm.uiState.test { assertFalse(awaitStories().canLoadMore) }
            vm.loadMore()
            assertEquals(1, source.requests.size)

            source.gate = CompletableDeferred()
            vm.retry()
            vm.loadMore()
            assertEquals(2, source.requests.size)
        }

    @Test
    fun loadMore_failure_keepsCards_flagsError_andRetrySucceeds() =
        runTest {
            source.responses[0] = Result.success(page("1", nbPages = 2))
            val vm = viewModel()
            vm.uiState.test {
                awaitStories()
                vm.loadMore()
                val failed = awaitUntil { it.stories.appendFailed }.stories
                assertEquals(listOf("1"), failed.ids)
                assertFalse(failed.isAppending)
                assertTrue(failed.canLoadMore)

                source.responses[1] = Result.success(page("2", page = 1, nbPages = 2))
                vm.loadMore()
                val ok = awaitUntil { it.stories.ids.size == 2 }.stories
                assertFalse(ok.appendFailed)
                assertFalse(ok.canLoadMore)
            }
        }

    // ---- tabs / topics / search

    @Test
    fun selectTab_persists_andLoadsThatFeedFromCache() =
        runTest {
            val settings = FakeSettings()
            val vm = viewModel(settings)
            vm.selectTab(FeedTab.LATEST)
            vm.uiState.test { assertEquals(FeedTab.LATEST, awaitUntil { it.content is FeedContent.Stories }.tab) }
            assertEquals("new", settings.selectedFeed.value)
            assertEquals(FeedQuery(FeedType.LATEST) to false, source.requests.last())

            vm.selectTab(FeedTab.LATEST)
            assertEquals(2, source.requests.size)
        }

    @Test
    fun selectTab_forYou_showsForYouContent_withoutRequest() =
        runTest {
            val settings = FakeSettings()
            val vm = viewModel(settings)
            vm.selectTab(FeedTab.FOR_YOU)
            vm.uiState.test { awaitUntil { it.content == FeedContent.ForYou } }
            assertEquals("foryou", settings.selectedFeed.value)
            assertEquals(1, source.requests.size)
        }

    @Test
    fun selectTopic_persists_andReloads_ignoringSameOrUnknown() =
        runTest {
            val settings = FakeSettings()
            val vm = viewModel(settings)
            vm.selectTopic(Topic.SECURITY.id)
            vm.uiState.test { assertEquals("Security", awaitUntil { it.content is FeedContent.Stories }.topicId) }
            assertEquals("Security", settings.selectedTopic.value)
            assertEquals(FeedQuery(FeedType.TOP, topicId = "Security"), source.lastQuery)

            vm.selectTopic("Security")
            vm.selectTopic("Nope")
            assertEquals(2, source.requests.size)
        }

    @Test
    fun search_isDebounced400ms_trimmed_andDisablesFeatured() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                assertTrue(awaitStories().showFeatured)
                vm.onSearchTextChange("ru")
                advanceTimeBy(200)
                vm.onSearchTextChange("  rust ")
                assertEquals("  rust ", awaitUntil { it.searchText == "  rust " }.searchText)
                advanceTimeBy(399)
                runCurrent()
                assertEquals(1, source.requests.size)

                advanceTimeBy(2)
                runCurrent()
                assertEquals(FeedQuery(FeedType.TOP, query = "rust"), source.lastQuery)
                assertFalse(
                    awaitUntil { (it.content as? FeedContent.Stories)?.showFeatured == false }.stories.showFeatured,
                )
            }
            assertEquals(2, source.requests.size)
        }

    @Test
    fun search_sameQueryAfterTrim_doesNotReload() =
        runTest {
            val vm = viewModel()
            vm.onSearchTextChange("   ")
            advanceTimeBy(500)
            runCurrent()
            assertEquals(1, source.requests.size)
        }

    // ---- library state on cards

    @Test
    fun readStories_areHidden_andCounted() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitStories()
                library.read.value = setOf("1")
                val partly = awaitUntil { it.stories.hiddenCount == 1 }.stories
                assertEquals(listOf("2"), partly.ids)

                library.read.value = setOf("1", "2")
                val all = awaitUntil { it.stories.hiddenCount == 2 }.stories
                assertTrue(all.isEmpty)
            }
        }

    @Test
    fun cards_reflectSavedState_andPrecomputedFields() =
        runTest {
            source.responses[0] =
                Result.success(
                    page("1").copy(
                        stories =
                            listOf(
                                story("1", title = "OpenAI ships a thing", points = 300),
                                story("2", url = null, storyText = "<p>Hi &amp; bye</p>", points = 299),
                            ),
                    ),
                )
            library.later.value = setOf("2")
            val vm = viewModel()
            vm.uiState.test {
                val (first, second) = awaitStories().cards
                assertEquals("example.com", first.domain)
                assertEquals("https://icons.duckduckgo.com/ip3/example.com.ico", first.faviconUrl)
                assertEquals(Topic.AI, first.topic)
                assertTrue(first.isHot)
                assertFalse(first.isSaved)
                assertEquals("", first.excerpt)

                assertEquals(StoryCardUi.FALLBACK_DOMAIN, second.domain)
                assertNull(second.topic)
                assertFalse(second.isHot)
                assertTrue(second.isSaved)
                assertEquals("Hi & bye", second.excerpt)
            }
        }

    // ---- bookmark / signals

    @Test
    fun toggleLater_emitsSnackbarEvent_andUndoReverts() =
        runTest {
            val vm = viewModel()
            val s = story("1")
            vm.uiEvents.test {
                vm.toggleLater(s)
                assertEquals(FeedEvent.LaterToggled(s, saved = true), awaitItem())
                assertEquals(setOf("1"), library.later.value)

                vm.undoToggleLater(FeedEvent.LaterToggled(s, saved = true))
                assertEquals(emptySet<String>(), library.later.value)

                library.later.value = setOf("1")
                vm.toggleLater(s)
                assertEquals(FeedEvent.LaterToggled(s, saved = false), awaitItem())
            }
            vm.uiState.test { assertFalse(awaitStories().cards.first().isSaved) }
        }

    @Test
    fun onCardShown_recordsImpression_unlessSaved() =
        runTest {
            library.later.value = setOf("2")
            val vm = viewModel()
            vm.onCardShown(story("1"))
            vm.onCardShown(story("2"))
            assertEquals(listOf("1"), learner.impressionIds)
        }

    @Test
    fun onOpen_recordsOpenSignal_andRemembersStory() =
        runTest {
            val vm = viewModel()
            vm.onOpen(story("7"))
            assertEquals(listOf(SignalKind.OPEN to "7"), learner.recorded)
            assertEquals(story("7"), source.story("7"))

            vm.onOpenComments(story("8"))
            assertEquals(story("8"), source.story("8"))
            assertEquals(1, learner.recorded.size)
        }

    @Test
    fun feedTab_fromId_mapsWebIds() {
        assertEquals(FeedTab.LATEST, FeedTab.fromId("new"))
        assertEquals(FeedTab.FOR_YOU, FeedTab.fromId("foryou"))
        assertEquals(FeedTab.TOP, FeedTab.fromId("read"))
        assertEquals(FeedTab.TOP, FeedTab.fromId(null))
    }
}
