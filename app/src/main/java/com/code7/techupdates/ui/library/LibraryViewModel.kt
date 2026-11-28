package com.code7.techupdates.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.FeedSource
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.LibrarySource
import com.code7.techupdates.data.ReadHow
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.domain.Story
import com.code7.techupdates.ui.feed.toCard
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
 * Library screen: "Read later" and "Read" tabs (port of the web app's `loadLibrary`, `toggleLater`,
 * `markRead`, `syncCounts` and the "Clear history" action). Every change can be undone from a snackbar.
 * [stories] keeps opened stories in memory for the reader; [nowEpochSec] is the `timeAgo` clock.
 */
@Suppress("TooManyFunctions") // one entry point per user action
class LibraryViewModel(
    private val library: LibrarySource,
    private val stories: FeedSource,
    private val learner: LearnerStore,
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) : ViewModel() {
    private data class Selection(
        val tab: LibraryTab = LibraryTab.LATER,
        val confirmingClear: Boolean = false,
    )

    private val selection = MutableStateFlow(Selection())
    private val events = Channel<LibraryEvent>(Channel.BUFFERED)

    val uiEvents: Flow<LibraryEvent> = events.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> =
        combine(selection, library.later(), library.read()) { sel, later, read ->
            val laterIds = later.mapTo(HashSet()) { it.story.id }
            val list = if (sel.tab == LibraryTab.LATER) later.map { it.story } else read.map { it.story }
            LibraryUiState(
                tab = sel.tab,
                laterCount = later.size,
                readCount = read.size,
                isLoading = false,
                cards = list.map { it.toCard(saved = it.id in laterIds) },
                confirmingClear = sel.confirmingClear && read.isNotEmpty(),
                nowEpochSec = nowEpochSec(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun selectTab(tab: LibraryTab) = selection.update { it.copy(tab = tab, confirmingClear = false) }

    /** Swipe away / un-bookmark in "Read later": leaves the list and forgets the `save` signal. */
    fun removeLater(story: Story) = launchAndSend { library.removeLater(story.id)?.let(LibraryEvent::LaterRemoved) }

    /** "Mark as read" in "Read later": moves the story to "Read" (JS `markRead(h, "manual")`). */
    fun markRead(story: Story) =
        launchAndSend { library.markRead(story, ReadHow.MANUAL)?.let(LibraryEvent::MarkedRead) }

    /** Swipe away in "Read": leaves the history and forgets the `read` signal. */
    fun removeRead(story: Story) = launchAndSend { library.removeRead(story.id)?.let(LibraryEvent::ReadRemoved) }

    /** Swipe away (or the "Remove" accessibility action) in the selected tab. */
    fun remove(story: Story) = if (selection.value.tab == LibraryTab.LATER) removeLater(story) else removeRead(story)

    /** Bookmark tap. In "Read later" that removes the story (undoable, original position kept). */
    fun toggleLater(story: Story) {
        if (selection.value.tab == LibraryTab.LATER) {
            removeLater(story)
        } else {
            launchAndSend { LibraryEvent.LaterToggled(story, library.toggleLater(story)) }
        }
    }

    /** "Clear all" in "Read": asks for confirmation first. */
    fun requestClearRead() = selection.update { it.copy(confirmingClear = true) }

    fun dismissClearRead() = selection.update { it.copy(confirmingClear = false) }

    fun confirmClearRead() {
        selection.update { it.copy(confirmingClear = false) }
        launchAndSend { library.clearRead().takeIf { it.isNotEmpty() }?.let(LibraryEvent::HistoryCleared) }
    }

    fun undo(event: LibraryEvent) {
        viewModelScope.launch {
            when (event) {
                is LibraryEvent.LaterRemoved -> library.undoRemoveLater(event.entry)
                is LibraryEvent.MarkedRead -> library.undoMarkRead(event.receipt)
                is LibraryEvent.ReadRemoved -> library.undoRemoveRead(event.entry)
                is LibraryEvent.HistoryCleared -> library.restoreRead(event.entries)
                is LibraryEvent.LaterToggled -> library.toggleLater(event.story, !event.saved)
            }
        }
    }

    /** A card was tapped: keep it for the reader and record an OPEN signal (as the feed does). */
    fun onOpen(story: Story) {
        stories.remember(story)
        viewModelScope.launch { learner.record(SignalKind.OPEN, story) }
    }

    fun onOpenComments(story: Story) = stories.remember(story)

    private fun launchAndSend(block: suspend () -> LibraryEvent?) {
        viewModelScope.launch { block()?.let { events.send(it) } }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MILLIS_PER_SECOND = 1000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    LibraryViewModel(
                        library = container.libraryRepository,
                        stories = container.storyRepository,
                        learner = container.learnerStore,
                    )
                }
            }
    }
}
