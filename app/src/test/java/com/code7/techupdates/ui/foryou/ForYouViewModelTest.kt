package com.code7.techupdates.ui.foryou

import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.ui.feed.FakeLibrary
import com.code7.techupdates.ui.feed.MainDispatcherRule
import com.code7.techupdates.ui.feed.story
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForYouViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val rust = story("rust", "Rust compiler internals", points = 20, url = "https://rust-lang.org/a")
    private val popular = story("popular", "Quarterly earnings call", points = 400, url = "https://news.test/b")
    private val middling = story("middling", "Gardening in autumn", points = 90, url = "https://garden.test/c")
    private val source = FakeCandidates(listOf(rust, popular, middling))
    private val library = FakeLibrary()
    private val learner = FakeLearnerData()
    private var trainings = 0

    private fun TestScope.viewModel(): ForYouViewModel {
        val cache =
            ModelCache(main.dispatcher) {
                trainings++
                trainModel(it)
            }
        val vm = ForYouViewModel(source, library, learner, { NOW }, main.dispatcher, cache)
        vm.uiState.launchIn(backgroundScope)
        return vm
    }

    private val ForYouViewModel.picks get() = uiState.value.content as ForYouContent.Picks
    private val ForYouViewModel.ids get() = picks.cards.map { it.story.id }
    private val ForYouViewModel.insights get() = uiState.value.insights!!

    private suspend fun likesRust(count: Int = 3) {
        (1..count).forEach {
            learner.record(SignalKind.READ, story("r$it", "Rust compiler notes $it", url = "https://rust-lang.org/$it"))
        }
    }

    @Test
    fun coldStart_ranksByPopularity_withoutReasonsOrInterestSearches() =
        runTest {
            likesRust(count = 2)
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()

            assertEquals(listOf("popular", "middling", "rust"), vm.ids)
            assertTrue(vm.picks.cards.all { it.why.isEmpty() })
            assertEquals(Insights(isReady = false, positives = 2, interests = emptyList()), vm.insights)
            assertEquals(2, vm.insights.progress)
            assertEquals(3, source.requests.size)
        }

    @Test
    fun ready_ranksByAffinity_explainsPicks_andSearchesInterests() =
        runTest {
            likesRust()
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()

            assertEquals(listOf("rust", "popular", "middling"), vm.ids)
            assertTrue(
                vm.picks.cards
                    .first()
                    .why
                    .isNotEmpty(),
            )
            assertTrue(vm.insights.isReady)
            assertEquals(3, vm.insights.positives)
            assertTrue(vm.insights.interests.isNotEmpty())
            val queries = source.requests.mapNotNull { it.params["query"] }
            assertEquals(vm.insights.interests.take(ForYouViewModel.QUERY_INTERESTS), queries)
            assertEquals(NOW, vm.picks.updatedAtEpochSec)
        }

    @Test
    fun pool_excludesReadAndSaved_andAppliesSearchAndTopic() =
        runTest {
            library.read.value = setOf("popular")
            library.later.value = setOf("middling")
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()
            assertEquals(listOf("rust"), vm.ids)
            assertEquals(0, vm.picks.hiddenCount)

            library.read.value = emptySet()
            library.later.value = emptySet()
            vm.show(ForYouFilter(query = "EARNINGS"))
            runCurrent()
            assertEquals(listOf("popular"), vm.ids)

            vm.show(ForYouFilter(topicId = "Programming"))
            runCurrent()
            assertEquals(listOf("rust"), vm.ids)
        }

    @Test
    fun savingKeepsTheCard_readingHidesIt() =
        runTest {
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()

            library.later.value = setOf("rust")
            runCurrent()
            assertTrue(
                vm.picks.cards
                    .single { it.story.id == "rust" }
                    .isSaved,
            )

            library.read.value = setOf("rust")
            runCurrent()
            assertEquals(listOf("popular", "middling"), vm.ids)
            assertEquals(1, vm.picks.hiddenCount)
        }

    @Test
    fun listStaysPut_untilTheTabIsEnteredAgainOrRefreshed() =
        runTest {
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()
            likesRust()
            advanceTimeBy(ForYouViewModel.RETRAIN_DEBOUNCE_MS + 1)
            vm.show(ForYouFilter())
            runCurrent()
            assertEquals(listOf("popular", "middling", "rust"), vm.ids)
            assertEquals(3, source.requests.size)

            vm.onTabEntered()
            vm.show(ForYouFilter())
            runCurrent()
            assertEquals("rust", vm.ids.first())

            val requests = source.requests.size
            vm.refresh()
            runCurrent()
            assertTrue(source.requests.size > requests)
        }

    @Test
    fun model_retrainsOnceAfterABurstOfChanges_andIsReused() =
        runTest {
            val vm = viewModel()
            advanceTimeBy(ForYouViewModel.RETRAIN_DEBOUNCE_MS + 1)
            assertEquals(1, trainings)

            likesRust()
            advanceTimeBy(ForYouViewModel.RETRAIN_DEBOUNCE_MS - 1)
            assertEquals(1, trainings)
            advanceTimeBy(2)
            assertEquals(2, trainings)

            vm.show(ForYouFilter())
            runCurrent()
            assertEquals(2, trainings)
        }

    @Test
    fun reset_forgetsLearning_andReturnsToColdStart() =
        runTest {
            likesRust()
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()

            vm.reset()
            runCurrent()

            assertEquals(1, learner.resets)
            assertEquals(Insights(isReady = false, positives = 0, interests = emptyList()), vm.insights)
            assertEquals(listOf("popular", "middling", "rust"), vm.ids)
        }

    @Test
    fun partialFailureKeepsOtherSources_totalFailureShowsError_thenRetryRecovers() =
        runTest {
            source.failing = { it.endpoint == "search_by_date" }
            val vm = viewModel()
            vm.show(ForYouFilter())
            runCurrent()
            assertEquals(3, vm.ids.size)

            source.failing = { true }
            vm.retry()
            runCurrent()
            assertEquals(ForYouContent.Error, vm.uiState.value.content)
            assertEquals(0, vm.insights.positives)

            source.failing = { false }
            vm.retry()
            runCurrent()
            assertEquals(3, vm.ids.size)
        }

    private companion object {
        const val NOW = 2_000_000L
    }
}
