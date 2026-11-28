package com.code7.techupdates.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.code7.techupdates.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.newStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("settings.preferences_pb") }

    @Test
    fun defaults() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = SettingsStore(newStore())
            assertEquals("top", settings.selectedFeed.first())
            assertEquals("", settings.selectedTopic.first())
            assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
            assertTrue(settings.dynamicColor.first())
        }

    @Test
    fun persistsAppearance() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = SettingsStore(newStore())
            settings.setThemeMode(ThemeMode.DARK)
            settings.setDynamicColor(false)
            assertEquals(ThemeMode.DARK, settings.themeMode.first())
            assertFalse(settings.dynamicColor.first())
        }

    @Test
    fun unknownTheme_fallsBackToSystem() =
        runTest(UnconfinedTestDispatcher()) {
            val ds = newStore()
            ds.edit { it[stringPreferencesKey("theme")] = "sepia" }
            assertEquals(ThemeMode.SYSTEM, SettingsStore(ds).themeMode.first())
        }

    @Test
    fun persistsSelection() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = SettingsStore(newStore())
            settings.setSelectedFeed("foryou")
            settings.setSelectedTopic("AI")
            assertEquals("foryou", settings.selectedFeed.first())
            assertEquals("AI", settings.selectedTopic.first())
        }

    @Test
    fun unknownStoredValues_fallBack() =
        runTest(UnconfinedTestDispatcher()) {
            val ds = newStore()
            ds.edit {
                it[stringPreferencesKey("feed")] = "bogus"
                it[stringPreferencesKey("topic")] = "nope"
            }
            val settings = SettingsStore(ds)
            assertEquals("top", settings.selectedFeed.first())
            assertEquals("", settings.selectedTopic.first())
        }

    @Test(expected = IllegalArgumentException::class)
    fun setSelectedFeed_rejectsUnknown() =
        runTest(UnconfinedTestDispatcher()) {
            SettingsStore(newStore()).setSelectedFeed("bogus")
        }
}
