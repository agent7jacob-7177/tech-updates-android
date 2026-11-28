package com.code7.techupdates.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.FeedSource
import com.code7.techupdates.data.LaterToggle
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.Page
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.data.local.FeedSettings
import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.domainOf
import com.code7.techupdates.domain.stripHtml
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Feed screen (port of the web app's `load()` / `render()` for the live feeds). [nowEpochSec] is
 * the clock used for `timeAgo` labels.
 */
@Suppress("TooManyFunctions") // one entry point per user action
class FeedViewModel(
    private val stories: FeedSource,
    private val library: LaterToggle,
    private val learner: LearnerStore,
    private val settings: FeedSettings,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) : ViewModel() {
    private enum class Status { LOADING, LOADED, ERROR, COMING_SOON }

    private data class State(
        val restored: Boolean = false,
        val tab: FeedTab = FeedTab.TOP,
        val topicId: String = "",
        val searchText: String = "",
        /** Applied (debounced, trimmed) search query. */
        val query: String = "",
        val status: Status = Status.LOADING,
        val stories: List<Story> = emptyList(),
        val page: Int = 0,
        val nbPages: Int = 1,
        val offlineSince: Long? = null,
        val refreshing: Boolean = false,
        val appending: Boolean = false,
        val appendFailed: Boolean = false,
        val now: Long = 0,
    ) {
        fun query(page: Int) = tab.type?.let { FeedQuery(it, topicId, query, page) }
    }

    private val state = MutableStateFlow(State())
    private val searchInput = MutableStateFlow("")
    private val laterIds: StateFlow<Set<String>> =
        library.laterIds().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val events = Channel<FeedEvent>(Channel.BUFFERED)
    private var loadJob: Job? = null

    val uiEvents: Flow<FeedEvent> = events.receiveAsFlow()

    val uiState: StateFlow<FeedUiState> =
        combine(state, laterIds, library.readIds()) { s, later, read -> s.toUi(later, read) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState())

    init {
        viewModelScope.launch {
            val tab = FeedTab.fromId(settings.selectedFeed.first())
            val topicId = settings.selectedTopic.first()
            state.update { it.copy(restored = true, tab = tab, topicId = topicId) }
            load(forceRefresh = false, keepContent = false)
        }
        viewModelScope.launch { observeSearch() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeSearch() {
        searchInput
            .debounce(SEARCH_DEBOUNCE_MS)
            .map(String::trim)
            .distinctUntilChanged()
            .collect { q ->
                if (q == state.value.query) return@collect
                state.update { it.copy(query = q) }
                if (state.value.restored) load(forceRefresh = false, keepContent = false)
            }
    }

    fun selectTab(tab: FeedTab) {
        if (tab == state.value.tab) return
        state.update { it.copy(tab = tab) }
        viewModelScope.launch { settings.setSelectedFeed(tab.id) }
        load(forceRefresh = false, keepContent = false)
    }

    fun selectTopic(topicId: String) {
        if (topicId == state.value.topicId || Topic.byId(topicId) == null) return
        state.update { it.copy(topicId = topicId) }
        viewModelScope.launch { settings.setSelectedTopic(topicId) }
        load(forceRefresh = false, keepContent = false)
    }

    fun onSearchTextChange(text: String) {
        state.update { it.copy(searchText = text) }
        searchInput.value = text
    }

    /** Pull-to-refresh: bypasses the cache, keeps current cards visible meanwhile. */
    fun refresh() = load(forceRefresh = true, keepContent = state.value.status == Status.LOADED)

    /** Retry from the error state. */
    fun retry() = load(forceRefresh = true, keepContent = false)

    /** Next page (infinite scroll / "Load more"), also retries a failed append. */
    fun loadMore() {
        val s = state.value
        val query = s.query(s.page + 1)
        val busy = s.appending || s.refreshing || loadJob?.isActive == true
        val canLoad = s.status == Status.LOADED && s.page + 1 < s.nbPages
        if (query == null || busy || !canLoad) return
        state.update { it.copy(appending = true, appendFailed = false) }
        loadJob =
            viewModelScope.launch {
                stories.feed(query).fold(
                    onSuccess = { page ->
                        state.update {
                            it.copy(
                                stories = (it.stories + page.stories).distinctBy(Story::id),
                                page = page.page,
                                nbPages = page.nbPages,
                                appending = false,
                            )
                        }
                    },
                    onFailure = { state.update { it.copy(appending = false, appendFailed = true) } },
                )
            }
    }

    /** A card stayed on screen for [IMPRESSION_DELAY_MS]: a mild "not interested" signal (JS `io`). */
    fun onCardShown(story: Story) {
        if (story.id in laterIds.value) return
        viewModelScope.launch { learner.recordImpression(story) }
    }

    /** A card was tapped: keep it for the reader and record an OPEN signal. */
    fun onOpen(story: Story) {
        stories.remember(story)
        viewModelScope.launch { learner.record(SignalKind.OPEN, story) }
    }

    /** Remembers [story] before opening its comments. */
    fun onOpenComments(story: Story) = stories.remember(story)

    fun toggleLater(story: Story) {
        viewModelScope.launch {
            val saved = library.toggleLater(story)
            events.send(FeedEvent.LaterToggled(story, saved))
        }
    }

    fun undoToggleLater(event: FeedEvent.LaterToggled) {
        viewModelScope.launch { library.toggleLater(event.story, !event.saved) }
    }

    private fun load(
        forceRefresh: Boolean,
        keepContent: Boolean,
    ) {
        loadJob?.cancel()
        val query = state.value.query(0)
        if (query == null) {
            state.update { it.copy(status = Status.COMING_SOON, refreshing = false, appending = false) }
            return
        }
        state.update {
            if (keepContent) {
                it.copy(refreshing = true, appending = false, appendFailed = false)
            } else {
                it.copy(status = Status.LOADING, refreshing = false, appending = false, appendFailed = false)
            }
        }
        loadJob =
            viewModelScope.launch {
                stories.feed(query, forceRefresh).fold(
                    onSuccess = { page -> state.update { it.withPage(page) } },
                    onFailure = { state.update { it.copy(status = Status.ERROR, refreshing = false) } },
                )
            }
    }

    private fun State.withPage(page: Page) =
        copy(
            status = Status.LOADED,
            stories = page.stories,
            page = page.page,
            nbPages = page.nbPages,
            offlineSince = page.fetchedAtEpochSec.takeIf { page.offline },
            refreshing = false,
            now = nowEpochSec(),
        )

    private fun State.toUi(
        later: Set<String>,
        read: Set<String>,
    ): FeedUiState {
        val content =
            when (status) {
                Status.LOADING -> {
                    FeedContent.Loading
                }

                Status.ERROR -> {
                    FeedContent.Error
                }

                Status.COMING_SOON -> {
                    FeedContent.ComingSoon
                }

                Status.LOADED -> {
                    val visible = stories.filter { it.id !in read }
                    FeedContent.Stories(
                        cards = visible.map { it.toCard(it.id in later) },
                        hiddenCount = stories.size - visible.size,
                        showFeatured = query.isEmpty(),
                        canLoadMore = page + 1 < nbPages,
                        isAppending = appending,
                        appendFailed = appendFailed,
                        offlineSinceEpochSec = offlineSince,
                        nowEpochSec = now,
                    )
                }
            }
        return FeedUiState(tab, topicId, searchText, content, refreshing)
    }

    private fun Story.toCard(saved: Boolean) =
        StoryCardUi(
            story = this,
            domain = domainOf(url).ifEmpty { StoryCardUi.FALLBACK_DOMAIN },
            topic = Topic.forTitle(title),
            excerpt = stripHtml(storyText),
            isSaved = saved,
        )

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val IMPRESSION_DELAY_MS = 1_200L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MILLIS_PER_SECOND = 1000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    FeedViewModel(
                        stories = container.storyRepository,
                        library = container.libraryRepository,
                        learner = container.learnerStore,
                        settings = container.settings,
                    )
                }
            }
    }
}
