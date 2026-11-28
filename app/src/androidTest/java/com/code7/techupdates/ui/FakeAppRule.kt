package com.code7.techupdates.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.code7.techupdates.AppContainer
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.data.remote.ReaderApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

/** Runs the test with a news service that cannot be reached. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Offline

/**
 * Replaces the app's [AppContainer] with canned HTTP, an in-memory database and in-memory settings,
 * so the tests never touch the network. Must run before the rule that launches the activity.
 */
class FakeAppRule : TestWatcher() {
    private lateinit var database: AppDatabase

    override fun starting(description: Description) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TechUpdatesApp
        val offline = description.getAnnotation(Offline::class.java) != null
        val hn =
            MockEngine { request ->
                when {
                    offline -> throw IOException("offline")
                    request.url.encodedPath.contains("search") -> respond(SEARCH_RESULTS, headers = JSON_HEADERS)
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        val reader = MockEngine { respond(ARTICLE) }
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        app.container =
            AppContainer(
                context = app,
                createHnApi = { HnApi(hn) },
                createReaderApi = { ReaderApi(reader) },
                createDatabase = { database },
                createSettingsDataStore = { InMemoryPreferences() },
            )
    }

    override fun finished(description: Description) {
        database.close()
    }

    /** The app's DataStore is a process-wide singleton that would leak between tests. */
    private class InMemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    companion object {
        const val FIRST_STORY = "A first story for the smoke tests"
        const val SECOND_STORY = "A second story for the smoke tests"
        const val ARTICLE_TEXT = "This is the canned article body."

        private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        private val ARTICLE = "Markdown Content:\n" + "$ARTICLE_TEXT\n\n".repeat(20)
        private val SEARCH_RESULTS =
            """
            {"page":0,"nbPages":1,"hits":[
              {"objectID":"1","title":"$FIRST_STORY","url":"https://example.com/first",
               "author":"pg","points":250,"num_comments":40,"created_at_i":1795729665},
              {"objectID":"2","title":"$SECOND_STORY","url":"https://example.com/second",
               "author":"dang","points":120,"num_comments":12,"created_at_i":1795726065}
            ]}
            """.trimIndent()
    }
}
