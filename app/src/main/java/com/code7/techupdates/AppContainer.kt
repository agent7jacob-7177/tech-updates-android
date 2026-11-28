package com.code7.techupdates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.code7.techupdates.data.ArticleRepository
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.LibraryRepository
import com.code7.techupdates.data.RoomLearnerStore
import com.code7.techupdates.data.StoryRepository
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.local.SettingsStore
import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.data.remote.ReaderApi

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Manual DI: app-wide singletons, created lazily. The factory parameters are seams for UI tests. */
class AppContainer(
    context: Context,
    createHnApi: () -> HnApi = { HnApi() },
    createReaderApi: () -> ReaderApi = { ReaderApi() },
    createDatabase: (Context) -> AppDatabase = AppDatabase::create,
    createSettingsDataStore: (Context) -> DataStore<Preferences> = { it.settingsDataStore },
) {
    private val appContext = context.applicationContext

    val hnApi: HnApi by lazy(createHnApi)
    val readerApi: ReaderApi by lazy(createReaderApi)
    val articleRepository: ArticleRepository by lazy { ArticleRepository(readerApi) }
    val storyRepository: StoryRepository by lazy { StoryRepository(hnApi) }
    val database: AppDatabase by lazy { createDatabase(appContext) }
    val learnerStore: LearnerStore by lazy { RoomLearnerStore(database) }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(database, learnerStore) }
    val settings: SettingsStore by lazy { SettingsStore(createSettingsDataStore(appContext)) }
}
