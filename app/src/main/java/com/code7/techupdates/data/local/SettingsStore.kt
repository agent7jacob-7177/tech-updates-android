package com.code7.techupdates.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.domain.Topic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persisted feed/topic selection (a seam for fakes in tests). */
interface FeedSettings {
    val selectedFeed: Flow<String>
    val selectedTopic: Flow<String>

    suspend fun setSelectedFeed(feed: String)

    suspend fun setSelectedTopic(topicId: String)
}

/** Appearance options from the Settings screen (a seam for fakes in tests). */
interface AppearanceSettings {
    val themeMode: Flow<ThemeMode>

    /** Material You colours from the wallpaper (Android 12+). */
    val dynamicColor: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)
}

/**
 * UI state restored across launches (JS `tu.feed`; the topic is persisted too so the app reopens
 * where it was left). Feed ids are the web app's tab ids.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) : FeedSettings,
    AppearanceSettings {
    /** Selected tab id; unknown or missing values fall back to [DEFAULT_FEED] (as JS does). */
    override val selectedFeed: Flow<String> =
        dataStore.data
            .map { prefs -> prefs[KEY_FEED]?.takeIf { it in FEEDS } ?: DEFAULT_FEED }
            .distinctUntilChanged()

    /** Selected [Topic.id]; unknown values fall back to "" (All). */
    override val selectedTopic: Flow<String> =
        dataStore.data
            .map { prefs -> prefs[KEY_TOPIC]?.takeIf { Topic.byId(it) != null } ?: "" }
            .distinctUntilChanged()

    override suspend fun setSelectedFeed(feed: String) {
        require(feed in FEEDS) { "Unknown feed: $feed" }
        dataStore.edit { it[KEY_FEED] = feed }
    }

    override suspend fun setSelectedTopic(topicId: String) {
        require(Topic.byId(topicId) != null) { "Unknown topic: $topicId" }
        dataStore.edit { it[KEY_TOPIC] = topicId }
    }

    /** Unknown or missing values fall back to [ThemeMode.SYSTEM]. */
    override val themeMode: Flow<ThemeMode> =
        dataStore.data
            .map { prefs -> ThemeMode.fromId(prefs[KEY_THEME]) }
            .distinctUntilChanged()

    override val dynamicColor: Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[KEY_DYNAMIC_COLOR] ?: DEFAULT_DYNAMIC_COLOR }
            .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.id }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    companion object {
        const val DEFAULT_FEED = "top"
        const val DEFAULT_DYNAMIC_COLOR = true

        /** JS tab ids: live feeds, "For you", and the two library lists. */
        val FEEDS = listOf("top", "new", "week", "foryou", "later", "read")

        private val KEY_FEED = stringPreferencesKey("feed")
        private val KEY_TOPIC = stringPreferencesKey("topic")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
