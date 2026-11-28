package com.code7.techupdates.ui.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.DiscussionSource
import com.code7.techupdates.domain.CommentThread
import com.code7.techupdates.ui.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The HN discussion of one story (port of JS `renderComments` / `commentNode`). The header shows
 * the story from the feed cache right away; the thread (and fresh story data) comes from
 * `items/{id}`. Deeper replies start collapsed like the web app; [toggle] collapses or expands a
 * subtree.
 */
class CommentsViewModel(
    private val storyId: String,
    private val source: DiscussionSource,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val state =
        MutableStateFlow(CommentsUiState(storyId, story = source.story(storyId), nowEpochSec = nowEpochSec()))
    private var thread = CommentThread.EMPTY
    private var loadJob: Job? = null

    val uiState: StateFlow<CommentsUiState> = state.asStateFlow()

    init {
        load()
    }

    /** "Try again" after the thread failed to load. */
    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    /** Collapses or expands the replies of comment [id]; ignored for comments without replies. */
    fun toggle(id: Long) {
        state.update { current ->
            val ready = current.content as? CommentsContent.Ready ?: return@update current
            if (id !in thread.collapsibleIds) return@update current
            val collapsed = if (id in ready.collapsed) ready.collapsed - id else ready.collapsed + id
            current.copy(content = ready.copy(rows = thread.visible(collapsed), collapsed = collapsed))
        }
    }

    private fun load() {
        state.update { it.copy(content = CommentsContent.Loading) }
        loadJob =
            viewModelScope.launch {
                source.discussion(storyId).fold(
                    onSuccess = { discussion ->
                        thread = CommentThread.of(discussion.comments)
                        val content =
                            if (thread.isEmpty) {
                                CommentsContent.Empty
                            } else {
                                val collapsed = thread.defaultCollapsed()
                                CommentsContent.Ready(thread.visible(collapsed), collapsed, thread.total)
                            }
                        state.update {
                            it.copy(story = discussion.story, content = content, nowEpochSec = nowEpochSec())
                        }
                    },
                    onFailure = { state.update { it.copy(content = CommentsContent.Error) } },
                )
            }
    }

    private fun nowEpochSec() = nowMillis() / MILLIS_PER_SECOND

    companion object {
        private const val MILLIS_PER_SECOND = 1000L

        val Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as TechUpdatesApp).container
                    val handle: SavedStateHandle = createSavedStateHandle()
                    CommentsViewModel(
                        storyId = checkNotNull(handle.get<String>(Routes.ARG_ID)) { "story id missing" },
                        source = container.storyRepository,
                    )
                }
            }
    }
}
