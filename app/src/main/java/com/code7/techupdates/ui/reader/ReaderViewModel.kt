package com.code7.techupdates.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.ArticleSource
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.ReadHow
import com.code7.techupdates.data.ReaderLibrary
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.data.StoryLookup
import com.code7.techupdates.data.remote.ReaderException
import com.code7.techupdates.data.remote.ReaderFailure
import com.code7.techupdates.domain.Reading
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.markdownPlainText
import com.code7.techupdates.domain.resolveMarkdownUrls
import com.code7.techupdates.domain.stripHtml
import com.code7.techupdates.domain.tidyMarkdown
import com.code7.techupdates.ui.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reader for one story (port of JS `showReader`, `getArticle`, `tidyArticle`, `updateReadingTime`
 * and the reader's `markRead` triggers). The story comes from the feed cache, the library, or the
 * network (deep links); article text comes from the Jina reader, self posts show their own text.
 * [nowMillis] is the clock for the mark-as-read delay and `timeAgo`.
 */
@Suppress("TooManyFunctions") // one entry point per user action
class ReaderViewModel(
    private val storyId: String,
    private val stories: StoryLookup,
    private val library: ReaderLibrary,
    private val articles: ArticleSource,
    private val learner: LearnerStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val content = MutableStateFlow(ReaderUiState(storyId, nowEpochSec = nowEpochSec()))
    private val events = Channel<ReaderEvent>(Channel.BUFFERED)
    private var openedAt = 0L
    private var contentWords = 0
    private var loadJob: Job? = null
    private var markingRead = false

    val uiEvents: Flow<ReaderEvent> = events.receiveAsFlow()

    val uiState: StateFlow<ReaderUiState> =
        combine(content, library.isLater(storyId), library.isRead(storyId)) { state, saved, read ->
            state.copy(isSaved = saved, isRead = read)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), content.value)

    init {
        load()
    }

    /** "Try again" after the story or its article failed to load. */
    fun retry() {
        if (loadJob?.isActive == true) return
        val story = content.value.story
        if (story == null) load() else loadJob = viewModelScope.launch { loadBody(story) }
    }

    /**
     * The end of the article (the "Continue on …" block) is on screen. Marks the story read once
     * the reader has been open for [Reading.markReadDelayMillis] (JS scroll handler).
     */
    fun onReachedEnd() {
        val state = uiState.value
        if (state.story == null || state.body !is ReaderBody.Ready || state.isRead) return
        if (nowMillis() - openedAt <= Reading.markReadDelayMillis(contentWords)) return
        markRead(ReadHow.AUTO)
    }

    /** "Continue on <site>" was tapped: counts as read (JS `markRead(current, "continue")`). */
    fun onContinueOnSite() {
        if (!uiState.value.isRead) markRead(ReadHow.CONTINUE)
    }

    fun toggleLater() {
        val story = content.value.story?.story ?: return
        viewModelScope.launch { events.send(ReaderEvent.LaterToggled(library.toggleLater(story))) }
    }

    fun undo(event: ReaderEvent) {
        viewModelScope.launch {
            when (event) {
                is ReaderEvent.MarkedRead -> {
                    library.undoMarkRead(event.receipt)
                }

                is ReaderEvent.LaterToggled -> {
                    content.value.story
                        ?.story
                        ?.let { library.toggleLater(it, !event.saved) }
                }
            }
        }
    }

    private fun markRead(how: ReadHow) {
        val story = content.value.story?.story ?: return
        if (markingRead) return
        markingRead = true
        viewModelScope.launch {
            try {
                library
                    .markRead(
                        story,
                        how,
                    )?.let { events.send(ReaderEvent.MarkedRead(it, auto = how == ReadHow.AUTO)) }
            } finally {
                markingRead = false
            }
        }
    }

    private fun load() {
        content.update { it.copy(storyFailed = false) }
        loadJob =
            viewModelScope.launch {
                val story =
                    stories.story(storyId)
                        ?: library.savedStory(storyId)
                        ?: stories.fetchStory(storyId).getOrNull()
                if (story == null) {
                    content.update { it.copy(storyFailed = true) }
                    return@launch
                }
                stories.remember(story)
                val reader = ReaderStory(story)
                openedAt = nowMillis()
                content.update { it.copy(story = reader, storyFailed = false, nowEpochSec = nowEpochSec()) }
                learner.record(SignalKind.OPEN, story)
                loadBody(reader)
            }
    }

    private suspend fun loadBody(reader: ReaderStory) {
        val story = reader.story
        val textWords = Reading.wordCount(stripHtml(story.storyText))
        val url = reader.originalUrl
        if (url == null) {
            showBody(ReaderBody.Ready(storyHtml = story.storyText), textWords)
            return
        }
        content.update { it.copy(body = ReaderBody.Loading) }
        articles.article(url).fold(
            onSuccess = { md -> showArticle(story, url, md, textWords) },
            onFailure = { e -> showBody(ReaderBody.Ready(story.storyText, note = noteFor(e)), textWords) },
        )
    }

    private fun showArticle(
        story: Story,
        url: String,
        md: String,
        textWords: Int,
    ) {
        val tidy = tidyMarkdown(resolveMarkdownUrls(md, url), story.title)
        val words = Reading.wordCount(markdownPlainText(tidy))
        val note = if (words < Reading.SHORT_PREVIEW_WORDS) ReaderNote.SHORT_PREVIEW else null
        showBody(ReaderBody.Ready(story.storyText, tidy.ifEmpty { null }, note), textWords + words)
    }

    private fun showBody(
        body: ReaderBody.Ready,
        words: Int,
    ) {
        contentWords = words
        content.update { it.copy(body = body.copy(readingMinutes = Reading.readingMinutes(words))) }
    }

    private fun noteFor(e: Throwable): ReaderNote =
        when ((e as? ReaderException)?.failure) {
            ReaderFailure.RATE_LIMITED -> ReaderNote.RATE_LIMITED
            ReaderFailure.BLOCKED -> ReaderNote.BLOCKED
            else -> ReaderNote.FETCH_FAILED
        }

    private fun nowEpochSec() = nowMillis() / MILLIS_PER_SECOND

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MILLIS_PER_SECOND = 1000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    val handle: SavedStateHandle = createSavedStateHandle()
                    ReaderViewModel(
                        storyId = checkNotNull(handle.get<String>(Routes.ARG_ID)) { "story id missing" },
                        stories = container.storyRepository,
                        library = container.libraryRepository,
                        articles = container.articleRepository,
                        learner = container.learnerStore,
                    )
                }
            }
    }
}
