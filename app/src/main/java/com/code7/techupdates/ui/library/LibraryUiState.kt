package com.code7.techupdates.ui.library

import com.code7.techupdates.data.ReadReceipt
import com.code7.techupdates.data.ReadStory
import com.code7.techupdates.data.SavedStory
import com.code7.techupdates.domain.Story
import com.code7.techupdates.ui.feed.StoryCardUi

/** The library's two lists (JS feeds `later` / `read`). */
enum class LibraryTab { LATER, READ }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.LATER,
    /** Tab badge counts (JS `syncCounts`). */
    val laterCount: Int = 0,
    val readCount: Int = 0,
    /** True until both lists have loaded once. */
    val isLoading: Boolean = true,
    /** Cards of the selected tab, newest first. */
    val cards: List<StoryCardUi> = emptyList(),
    /** "Clear all" confirmation dialog is showing. */
    val confirmingClear: Boolean = false,
    /** Reference time for `timeAgo` labels. */
    val nowEpochSec: Long = 0,
) {
    val canClear: Boolean get() = tab == LibraryTab.READ && readCount > 0
}

/** One-off events: each shows a snackbar whose Undo calls [LibraryViewModel.undo]. */
sealed interface LibraryEvent {
    data class LaterRemoved(
        val entry: SavedStory,
    ) : LibraryEvent

    data class MarkedRead(
        val receipt: ReadReceipt,
    ) : LibraryEvent

    data class ReadRemoved(
        val entry: ReadStory,
    ) : LibraryEvent

    data class HistoryCleared(
        val entries: List<ReadStory>,
    ) : LibraryEvent

    /** Bookmark tapped on a card in the Read tab. */
    data class LaterToggled(
        val story: Story,
        val saved: Boolean,
    ) : LibraryEvent
}
