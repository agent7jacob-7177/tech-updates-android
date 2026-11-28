package com.code7.techupdates

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.code7.techupdates.data.LibraryRepository
import com.code7.techupdates.data.RoomLearnerStore
import com.code7.techupdates.data.StoryRepository
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.local.SettingsStore
import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.recommender.LearnerStore

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI: app-wide singletons, created lazily. */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val hnApi: HnApi by lazy { HnApi() }
    val storyRepository: StoryRepository by lazy { StoryRepository(hnApi) }
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val learnerStore: LearnerStore by lazy { RoomLearnerStore(database) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database, learnerStore) }
    val settings: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }
}
