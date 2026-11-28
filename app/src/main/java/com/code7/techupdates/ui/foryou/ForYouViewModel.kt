package com.code7.techupdates.ui.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.CandidateSource
import com.code7.techupdates.data.LaterToggle
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.toItem
import com.code7.techupdates.domain.SearchRequest
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.forYouRequests
import com.code7.techupdates.recommender.Ranked
import com.code7.techupdates.recommender.Ranking
import com.code7.techupdates.ui.feed.toCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "For you" tab (port of the web app's `loadForYou()` / `renderInsights()`). As on the web, the
 * list is ranked when it is loaded and does not reorder while being read; the model is meanwhile
 * kept up to date in the background so that the next load is quick. [computeDispatcher] runs
 * training and ranking.
 */
class ForYouViewModel(
    private val source: CandidateSource,
    private val library: LaterToggle,
    private val learner: LearnerStore,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val models: ModelCache = ModelCache(computeDispatcher),
) : ViewModel() {
    private data class State(
        val content: Content = Content.Loading,
        val insights: Insights? = null,
        val refreshing: Boolean = false,
    )

    private sealed interface Content {
        data object Loading : Content

        data object Error : Content

        data class Loaded(
            val picks: List<Ranked<Story>>,
            val at: Long,
        ) : Content
    }

    private val state = MutableStateFlow(State())
    private var filter: ForYouFilter? = null
    private var stale = true
    private var loadJob: Job? = null

    val uiState: StateFlow<ForYouUiState> =
        combine(state, library.laterIds(), library.readIds()) { s, later, read -> s.toUi(later, read) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ForYouUiState())

    init {
        viewModelScope.launch { keepModelTrained() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun keepModelTrained() {
        combine(learner.events(), learner.impressions(), ::LearnerData)
            .debounce(RETRAIN_DEBOUNCE_MS)
            .collectLatest { models.modelFor(it) }
    }

    /** The tab is on screen with [filter]: loads unless the current list is still wanted. */
    fun show(filter: ForYouFilter) {
        if (!stale && filter == this.filter) return
        this.filter = filter
        load(keepContent = false)
    }

    /** The user switched to the tab: the next [show] ranks afresh (the web app reloads on tab clicks). */
    fun onTabEntered() {
        stale = true
    }

    /** Pull-to-refresh. */
    fun refresh() = load(keepContent = state.value.content is Content.Loaded)

    fun retry() = load(keepContent = false)

    /** Forgets everything learned (the Read later and Read lists are kept) and starts over. */
    fun reset() {
        viewModelScope.launch {
            learner.reset()
            load(keepContent = false)
        }
    }

    private fun load(keepContent: Boolean) {
        val wanted = filter ?: return
        stale = false
        loadJob?.cancel()
        state.update {
            if (keepContent) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = Content.Loading, refreshing = false)
            }
        }
        loadJob = viewModelScope.launch { state.value = buildFeed(wanted) }
    }

    private suspend fun buildFeed(filter: ForYouFilter): State {
        val before = learner.snapshot()
        val interests =
            if (before.positives >= Ranking.MIN_SIGNALS) {
                models.modelFor(before).interests(QUERY_INTERESTS)
            } else {
                emptyList()
            }
        val pool = fetchPool(forYouRequests(interests, nowEpochSec()))
        // Read again: signals may have arrived while fetching.
        val data = learner.snapshot()
        val ready = data.positives >= Ranking.MIN_SIGNALS
        val model = if (ready) models.modelFor(data) else null
        val insights =
            Insights(
                isReady = ready,
                positives = data.positives,
                interests = model?.let { withContext(computeDispatcher) { it.interests() } }.orEmpty(),
            )
        if (pool.isEmpty()) return State(Content.Error, insights)
        val excluded = library.readIds().first() + library.laterIds().first()
        val picks =
            withContext(computeDispatcher) {
                val candidates = pool.filter { it.id !in excluded && it.matches(filter) }
                Ranking.rank(candidates, model, Story::toItem, Story::points)
            }
        return State(Content.Loaded(picks, nowEpochSec()), insights)
    }

    /** Fetches every source in parallel; failed ones are skipped (JS `Promise.allSettled`). */
    private suspend fun fetchPool(requests: List<SearchRequest>): List<Story> =
        coroutineScope {
            requests
                .map { async { source.candidates(it).getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
                .distinctBy(Story::id)
        }

    private fun State.toUi(
        later: Set<String>,
        read: Set<String>,
    ): ForYouUiState {
        val shown =
            when (content) {
                Content.Loading -> {
                    ForYouContent.Loading
                }

                Content.Error -> {
                    ForYouContent.Error
                }

                is Content.Loaded -> {
                    val visible = content.picks.filter { it.value.id !in read }
                    ForYouContent.Picks(
                        cards = visible.map { it.value.toCard(it.value.id in later).copy(why = it.why) },
                        hiddenCount = content.picks.size - visible.size,
                        updatedAtEpochSec = content.at,
                    )
                }
            }
        return ForYouUiState(shown, insights, refreshing)
    }

    companion object {
        const val RETRAIN_DEBOUNCE_MS = 500L

        /** Learned interests that get their own candidate search. */
        const val QUERY_INTERESTS = 4
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MILLIS_PER_SECOND = 1000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    ForYouViewModel(
                        source = container.storyRepository,
                        library = container.libraryRepository,
                        learner = container.learnerStore,
                    )
                }
            }
    }
}

private fun Story.matches(filter: ForYouFilter): Boolean {
    val topic = Topic.byId(filter.topicId)?.match
    return (filter.query.isEmpty() || title.lowercase().contains(filter.query.lowercase())) &&
        (topic == null || topic.containsMatchIn(title))
}

private suspend fun LearnerStore.snapshot() = LearnerData(events().first(), impressions().first())
