package com.code7.techupdates.ui.library

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.code7.techupdates.data.ReadHow
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.ui.feed.FakeFeedSource
import com.code7.techupdates.ui.feed.FakeLearner
import com.code7.techupdates.ui.feed.MainDispatcherRule
import com.code7.techupdates.ui.feed.story
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val library = FakeLibrarySource()
    private val source = FakeFeedSource()
    private val learner = FakeLearner()

    private fun viewModel() = LibraryViewModel(library, source, learner, nowEpochSec = { 3_000L })

    private suspend fun ReceiveTurbine<LibraryUiState>.awaitUntil(
        predicate: (LibraryUiState) -> Boolean,
    ): LibraryUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private val LibraryUiState.ids get() = cards.map { it.story.id }

    /** Collects state in the background so [LibraryViewModel.uiState] stays hot during the test. */
    private fun LibraryViewModel.test(block: suspend (LibraryViewModel, ReceiveTurbine<LibraryEvent>) -> Unit) =
        runTest {
            val vm = this@test
            uiState.test {
                uiEvents.test { block(vm, this) }
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- loading, tabs, counts

    @Test
    fun initial_isLoadingThenShowsReadLaterNewestFirstWithCounts() =
        runTest {
            library.save(story("1"), story("2"))
            library.addRead(story("3"))
            val vm = viewModel()
            assertTrue(vm.uiState.value.isLoading)
            vm.uiState.test {
                val state = awaitUntil { !it.isLoading }
                assertEquals(LibraryTab.LATER, state.tab)
                assertEquals(listOf("2", "1"), state.ids)
                assertTrue(state.cards.all { it.isSaved })
                assertEquals(2, state.laterCount)
                assertEquals(1, state.readCount)
                assertEquals(3_000L, state.nowEpochSec)
                assertFalse(state.canClear)
            }
        }

    @Test
    fun emptyLibrary_showsEmptyListsForBothTabs() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                val later = awaitUntil { !it.isLoading }
                assertTrue(later.cards.isEmpty())
                vm.selectTab(LibraryTab.READ)
                val read = awaitUntil { it.tab == LibraryTab.READ }
                assertTrue(read.cards.isEmpty())
                assertFalse(read.canClear)
            }
        }

    @Test
    fun selectRead_showsHistory_bookmarkReflectsReadLater() =
        runTest {
            library.addRead(story("1"), story("2"))
            library.save(story("1"))
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { !it.isLoading }
                vm.selectTab(LibraryTab.READ)
                val state = awaitUntil { it.tab == LibraryTab.READ }
                assertEquals(listOf("2", "1"), state.ids)
                assertEquals(listOf(false, true), state.cards.map { it.isSaved })
                assertTrue(state.canClear)
            }
        }

    @Test
    fun counts_followLibraryChanges() =
        runTest {
            viewModel().uiState.test {
                awaitUntil { !it.isLoading }
                library.save(story("1"))
                assertEquals(1, awaitUntil { it.laterCount == 1 }.cards.size)
                library.addRead(story("2"))
                assertEquals(1, awaitUntil { it.readCount == 1 }.laterCount)
            }
        }

    // ---- Read later: remove / mark read

    @Test
    fun removeLater_forgetsSaveAndSendsEvent_undoRestores() =
        viewModel().test { vm, events ->
            library.save(story("1"), story("2"))
            library.signals += setOf(SignalKind.SAVE to "1", SignalKind.SAVE to "2")
            vm.removeLater(story("1"))
            val event = events.awaitItem() as LibraryEvent.LaterRemoved
            assertEquals("1", event.entry.story.id)
            assertEquals(listOf("2"), vm.uiState.value.ids)
            assertEquals(setOf(SignalKind.SAVE to "2"), library.signals)

            vm.undo(event)
            assertEquals(listOf("2", "1"), vm.uiState.value.ids)
            assertEquals(setOf(SignalKind.SAVE to "1", SignalKind.SAVE to "2"), library.signals)
        }

    @Test
    fun removeLater_missingStory_sendsNoEvent() =
        viewModel().test { vm, events ->
            vm.removeLater(story("9"))
            events.expectNoEvents()
        }

    @Test
    fun removeAndBookmark_inReadLater_removeFromReadLater() =
        viewModel().test { vm, events ->
            library.save(story("1"), story("2"))
            vm.remove(story("1"))
            assertTrue(events.awaitItem() is LibraryEvent.LaterRemoved)
            vm.toggleLater(story("2"))
            assertTrue(events.awaitItem() is LibraryEvent.LaterRemoved)
            assertEquals(0, vm.uiState.value.laterCount)
        }

    @Test
    fun markRead_movesToRead_undoMovesBack() =
        viewModel().test { vm, events ->
            library.save(story("1"))
            vm.markRead(story("1"))
            val event = events.awaitItem() as LibraryEvent.MarkedRead
            assertTrue(event.receipt.wasLater)
            with(vm.uiState.value) {
                assertEquals(0, laterCount)
                assertEquals(1, readCount)
            }
            assertEquals(
                ReadHow.MANUAL,
                library.read.value
                    .single()
                    .how,
            )
            assertEquals(setOf(SignalKind.READ to "1"), library.signals)

            vm.undo(event)
            with(vm.uiState.value) {
                assertEquals(listOf("1"), ids)
                assertEquals(0, readCount)
            }
            assertTrue(library.signals.isEmpty())
        }

    @Test
    fun markRead_alreadyRead_sendsNoEvent() =
        viewModel().test { vm, events ->
            library.addRead(story("1"))
            vm.markRead(story("1"))
            events.expectNoEvents()
        }

    // ---- Read: remove / bookmark

    @Test
    fun removeInRead_forgetsReadSignal_undoRestoresIt() =
        viewModel().test { vm, events ->
            library.addRead(story("1"), story("2"))
            library.signals += setOf(SignalKind.READ to "1", SignalKind.READ to "2")
            vm.selectTab(LibraryTab.READ)
            vm.remove(story("1"))
            val event = events.awaitItem() as LibraryEvent.ReadRemoved
            assertEquals(listOf("2"), vm.uiState.value.ids)
            assertEquals(setOf(SignalKind.READ to "2"), library.signals)

            vm.undo(event)
            assertEquals(listOf("2", "1"), vm.uiState.value.ids)
            assertEquals(setOf(SignalKind.READ to "1", SignalKind.READ to "2"), library.signals)
        }

    @Test
    fun bookmarkInRead_togglesReadLater_undoReverts() =
        viewModel().test { vm, events ->
            library.addRead(story("1"))
            vm.selectTab(LibraryTab.READ)
            vm.toggleLater(story("1"))
            val event = events.awaitItem() as LibraryEvent.LaterToggled
            assertTrue(event.saved)
            assertTrue(
                vm.uiState.value.cards
                    .single()
                    .isSaved,
            )
            assertEquals(1, vm.uiState.value.laterCount)

            vm.undo(event)
            assertFalse(
                vm.uiState.value.cards
                    .single()
                    .isSaved,
            )
            assertEquals(0, vm.uiState.value.laterCount)
        }

    // ---- Clear all

    @Test
    fun requestClear_showsDialog_dismissHidesIt() =
        viewModel().test { vm, _ ->
            library.addRead(story("1"))
            vm.selectTab(LibraryTab.READ)
            vm.requestClearRead()
            assertTrue(vm.uiState.value.confirmingClear)
            vm.dismissClearRead()
            assertFalse(vm.uiState.value.confirmingClear)
            assertEquals(1, vm.uiState.value.readCount)
        }

    @Test
    fun requestClear_emptyHistory_noDialog() =
        viewModel().test { vm, _ ->
            vm.selectTab(LibraryTab.READ)
            vm.requestClearRead()
            assertFalse(vm.uiState.value.confirmingClear)
        }

    @Test
    fun selectTab_closesDialog() =
        viewModel().test { vm, _ ->
            library.addRead(story("1"))
            vm.selectTab(LibraryTab.READ)
            vm.requestClearRead()
            vm.selectTab(LibraryTab.LATER)
            vm.selectTab(LibraryTab.READ)
            assertFalse(vm.uiState.value.confirmingClear)
        }

    @Test
    fun confirmClear_emptiesHistoryKeepsSignals_undoRestores() =
        viewModel().test { vm, events ->
            library.addRead(story("1"), story("2"))
            library.signals += setOf(SignalKind.READ to "1", SignalKind.READ to "2")
            vm.selectTab(LibraryTab.READ)
            vm.requestClearRead()
            vm.confirmClearRead()
            val event = events.awaitItem() as LibraryEvent.HistoryCleared
            assertEquals(2, event.entries.size)
            with(vm.uiState.value) {
                assertFalse(confirmingClear)
                assertEquals(0, readCount)
                assertTrue(cards.isEmpty())
            }
            assertEquals(2, library.signals.size)

            vm.undo(event)
            assertEquals(listOf("2", "1"), vm.uiState.value.ids)
        }

    @Test
    fun confirmClear_nothingToClear_sendsNoEvent() =
        viewModel().test { vm, events ->
            vm.confirmClearRead()
            events.expectNoEvents()
        }

    // ---- opening

    @Test
    fun onOpen_remembersStoryAndRecordsOpen() =
        viewModel().test { vm, _ ->
            vm.onOpen(story("1"))
            vm.onOpenComments(story("2"))
            assertEquals(setOf("1", "2"), source.remembered.keys)
            assertEquals(listOf(SignalKind.OPEN to "1"), learner.recorded)
        }
}
