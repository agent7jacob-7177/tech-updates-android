package com.code7.techupdates.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.ui.foryou.FakeLearnerData
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.awaitText
import com.code7.techupdates.uitest.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val appearance = FakeAppearance()
    private val learner = FakeLearnerData()

    private fun viewModel() =
        SettingsViewModel(appearance, learner, dynamicColorSupported = true, versionLabel = "v1.2.3 (4)")

    private fun launch() {
        val viewModel = viewModel()
        compose.setContent {
            Code7Theme(dynamicColor = false) { SettingsRoute(onBack = {}, viewModel = viewModel) }
        }
        compose.waitForIdle()
    }

    private fun dialogButton(label: Int) = compose.onNode(hasText(string(label)) and hasAnyAncestor(isDialog()))

    @Test
    fun themeSelection_persistsInStore() {
        launch()
        compose.onNodeWithText(string(R.string.settings_theme_system)).assertIsSelected()
        compose.onNodeWithText(string(R.string.settings_theme_dark)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(string(R.string.settings_theme_dark)).assertIsSelected()
        assertEquals(ThemeMode.DARK, appearance.themeMode.value)
    }

    @Test
    fun storedTheme_isSelectedOnOpen() {
        appearance.themeMode.value = ThemeMode.LIGHT
        launch()
        compose.onNodeWithText(string(R.string.settings_theme_light)).assertIsSelected()
        compose.onNodeWithText("v1.2.3 (4)").assertExists()
    }

    @Test
    fun resetLearning_cancelKeepsModel() {
        launch()
        compose.onNodeWithText(string(R.string.settings_reset_learning)).performClick()
        compose.onNodeWithText(string(R.string.settings_reset_message)).assertIsDisplayed()
        dialogButton(R.string.settings_cancel).performClick()
        compose.onNodeWithText(string(R.string.settings_reset_message)).assertDoesNotExist()
        assertEquals(0, learner.resets)
    }

    @Test
    fun resetLearning_confirmResetsAndShowsSnackbar() {
        launch()
        compose.onNodeWithText(string(R.string.settings_reset_learning)).performClick()
        dialogButton(R.string.settings_reset_confirm).performClick()
        compose.awaitText(string(R.string.settings_reset_done))
        assertEquals(1, learner.resets)
    }
}
