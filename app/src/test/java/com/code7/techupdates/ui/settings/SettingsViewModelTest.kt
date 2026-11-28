package com.code7.techupdates.ui.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.ui.feed.FakeLearner
import com.code7.techupdates.ui.feed.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class CountingLearner(
    delegate: LearnerStore = FakeLearner(),
) : LearnerStore by delegate {
    var resets = 0

    override suspend fun reset() {
        resets++
    }
}

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val appearance = FakeAppearance()
    private val learner = CountingLearner()

    private fun viewModel(dynamicSupported: Boolean = true) =
        SettingsViewModel(appearance, learner, dynamicSupported, versionLabel = "v1.2.3 (4)")

    private suspend fun ReceiveTurbine<SettingsUiState>.awaitUntil(
        predicate: (SettingsUiState) -> Boolean,
    ): SettingsUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun initial_isLoadingThenShowsStoredSettings() =
        runTest {
            appearance.themeMode.value = ThemeMode.DARK
            appearance.dynamicColor.value = false
            val vm = viewModel()
            assertTrue(vm.uiState.value.isLoading)
            assertEquals("v1.2.3 (4)", vm.uiState.value.versionLabel)
            vm.uiState.test {
                val state = awaitUntil { !it.isLoading }
                assertEquals(ThemeMode.DARK, state.themeMode)
                assertFalse(state.dynamicColor)
                assertTrue(state.dynamicColorSupported)
                assertFalse(state.confirmingReset)
                assertEquals("v1.2.3 (4)", state.versionLabel)
            }
        }

    @Test
    fun selectTheme_persistsAndUpdatesState() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { !it.isLoading }
                vm.selectTheme(ThemeMode.LIGHT)
                assertEquals(ThemeMode.LIGHT, awaitUntil { it.themeMode == ThemeMode.LIGHT }.themeMode)
                assertEquals(ThemeMode.LIGHT, appearance.themeMode.value)
                vm.selectTheme(ThemeMode.SYSTEM)
                awaitUntil { it.themeMode == ThemeMode.SYSTEM }
            }
        }

    @Test
    fun setDynamicColor_persistsAndUpdatesState() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { !it.isLoading && it.dynamicColor }
                vm.setDynamicColor(false)
                awaitUntil { !it.dynamicColor }
                assertFalse(appearance.dynamicColor.value)
            }
        }

    @Test
    fun setDynamicColor_ignoredWhenUnsupported() =
        runTest {
            val vm = viewModel(dynamicSupported = false)
            vm.uiState.test {
                val state = awaitUntil { !it.isLoading }
                assertFalse(state.dynamicColorSupported)
                vm.setDynamicColor(false)
                expectNoEvents()
                assertTrue(appearance.dynamicColor.value)
            }
        }

    @Test
    fun requestReset_thenDismiss_doesNotReset() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { !it.isLoading }
                vm.requestReset()
                assertTrue(awaitItem().confirmingReset)
                vm.dismissReset()
                assertFalse(awaitItem().confirmingReset)
                assertEquals(0, learner.resets)
            }
        }

    @Test
    fun confirmReset_resetsLearnerAndSendsEvent() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                awaitUntil { !it.isLoading }
                vm.uiEvents.test {
                    vm.requestReset()
                    vm.confirmReset()
                    assertEquals(SettingsEvent.LearningReset, awaitItem())
                }
                assertFalse(awaitUntil { !it.confirmingReset }.confirmingReset)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, learner.resets)
        }
}
