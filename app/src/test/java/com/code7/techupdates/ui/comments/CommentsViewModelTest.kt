package com.code7.techupdates.ui.comments

import app.cash.turbine.test
import com.code7.techupdates.data.Discussion
import com.code7.techupdates.domain.Comment
import com.code7.techupdates.domain.Story
import com.code7.techupdates.ui.feed.MainDispatcherRule
import com.code7.techupdates.ui.feed.story
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CommentsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val source = FakeDiscussions()

    private fun c(
        id: Long,
        text: String? = "c$id",
        vararg kids: Comment,
    ) = Comment(id, "u$id", text, 500, kids.toList())

    // 1 -> 2 -> 3, and 4
    private val comments = listOf(c(1, "one", c(2, "two", c(3))), c(4))
    private val fresh = story("1", title = "Fresh title", points = 99)

    private fun viewModel() = CommentsViewModel("1", source, nowMillis = { 2_000_000L })

    private val CommentsUiState.ready get() = content as CommentsContent.Ready

    private fun CommentsContent.ids() = (this as CommentsContent.Ready).rows.map { it.id }

    @Test
    fun loading_showsCachedStoryHeader() =
        runTest {
            source.cached["1"] = story("1")
            source.gate = CompletableDeferred()
            val vm = viewModel()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(CommentsContent.Loading, state.content)
                assertEquals("Story 1", state.story?.title)
                assertEquals("example.com", state.domain)
                assertEquals("https://news.ycombinator.com/item?id=1", state.hnUrl)
                assertEquals(2_000L, state.nowEpochSec)
                source.gate?.complete(Unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun loaded_showsThreadWithDeeperRepliesCollapsed() =
        runTest {
            source.result = Result.success(Discussion(fresh, comments))
            val vm = viewModel()
            val state = vm.uiState.value
            assertEquals("Fresh title", state.story?.title)
            assertEquals(4, state.ready.total)
            assertEquals(setOf(2L), state.ready.collapsed)
            assertEquals(listOf(1L, 2L, 4L), state.content.ids())
            assertEquals(1, source.calls)
        }

    @Test
    fun noStoryInCache_headerFromThreadResponse() =
        runTest {
            source.gate = CompletableDeferred()
            source.result = Result.success(Discussion(fresh, comments))
            val vm = viewModel()
            vm.uiState.test {
                assertNull(awaitItem().story)
                source.gate?.complete(Unit)
                assertEquals("Fresh title", awaitItem().story?.title)
            }
        }

    @Test
    fun noComments_isEmpty() =
        runTest {
            source.result = Result.success(Discussion(fresh, listOf(c(1, null))))
            assertEquals(CommentsContent.Empty, viewModel().uiState.value.content)
        }

    @Test
    fun failure_isError_thenRetrySucceeds() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                assertEquals(CommentsContent.Error, awaitItem().content)
                source.result = Result.success(Discussion(fresh, comments))
                vm.retry()
                assertEquals(CommentsContent.Loading, awaitItem().content)
                assertEquals(listOf(1L, 2L, 4L), awaitItem().content.ids())
            }
            assertEquals(2, source.calls)
        }

    @Test
    fun retry_whileLoading_isIgnored() =
        runTest {
            source.gate = CompletableDeferred()
            val vm = viewModel()
            vm.retry()
            assertEquals(1, source.calls)
            source.gate?.complete(Unit)
        }

    @Test
    fun toggle_expandsAndCollapsesSubtrees() =
        runTest {
            source.result = Result.success(Discussion(fresh, comments))
            val vm = viewModel()
            vm.uiState.test {
                assertEquals(listOf(1L, 2L, 4L), awaitItem().content.ids())
                vm.toggle(2) // expand
                awaitItem().let {
                    assertEquals(listOf(1L, 2L, 3L, 4L), it.content.ids())
                    assertEquals(emptySet<Long>(), it.ready.collapsed)
                }
                vm.toggle(1) // collapse the whole top-level thread
                awaitItem().let {
                    assertEquals(listOf(1L, 4L), it.content.ids())
                    assertEquals(setOf(1L), it.ready.collapsed)
                }
                vm.toggle(1)
                assertEquals(listOf(1L, 2L, 3L, 4L), awaitItem().content.ids())
            }
        }

    @Test
    fun toggle_withoutReplies_orBeforeLoaded_isIgnored() =
        runTest {
            val vm = viewModel() // Error state
            vm.toggle(1)
            assertEquals(CommentsContent.Error, vm.uiState.value.content)

            source.result = Result.success(Discussion(fresh, comments))
            vm.retry()
            val before = vm.uiState.value
            vm.toggle(4) // leaf
            vm.toggle(99) // unknown
            assertEquals(before, vm.uiState.value)
        }
}
