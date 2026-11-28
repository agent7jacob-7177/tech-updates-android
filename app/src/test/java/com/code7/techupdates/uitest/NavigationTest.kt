package com.code7.techupdates.uitest

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code7.techupdates.AppContainer
import com.code7.techupdates.MainActivity
import com.code7.techupdates.R
import com.code7.techupdates.TechUpdatesApp
import com.code7.techupdates.data.local.AppDatabase
import com.code7.techupdates.data.remote.Fixtures
import com.code7.techupdates.data.remote.Fixtures.json
import com.code7.techupdates.data.remote.HnApi
import com.code7.techupdates.data.remote.ReaderApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/** The real [MainActivity] and navigation graph, with canned HTTP, an in-memory database and settings. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TechUpdatesApp>()
    private val itemRequests = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var database: AppDatabase
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun installFakes() {
        // Loading takes real time here (Ktor, Room), with endlessly animating skeletons on screen: Compose
        // would never be idle on an auto-advancing clock, so the await helpers move it instead.
        compose.mainClock.autoAdvance = false
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val hn =
            MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.contains("/items/") -> {
                        itemRequests += path.substringAfterLast('/')
                        respond(DEEP_LINKED_ITEM, HttpStatusCode.OK, JSON_HEADERS)
                    }

                    else -> {
                        json("search_front_page.json")
                    }
                }
            }
        val article = requireNotNull(javaClass.classLoader?.getResource(ARTICLE)).readText()
        val reader = MockEngine { respond(article) }
        app.container =
            AppContainer(
                context = app,
                createHnApi = { HnApi(hn) },
                createReaderApi = { ReaderApi(reader) },
                createDatabase = { database },
                createSettingsDataStore = { InMemoryPreferences() },
            )
    }

    @After
    fun close() {
        scenario?.close()
        database.close()
    }

    private fun launch(intent: Intent = Intent(app, MainActivity::class.java)) {
        scenario = ActivityScenario.launch(intent)
    }

    private fun navItem(label: Int) = compose.onNode(hasText(string(label)) and hasClickAction())

    /** A web reader link, checked against the manifest's intent filter before targeting the activity. */
    private fun webLink(context: Context = app): Intent {
        val view =
            Intent(Intent.ACTION_VIEW, DEEP_LINK.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(view, 0)
        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        return view.setClass(context, MainActivity::class.java)
    }

    private fun assertReaderShows(title: String) {
        compose.awaitText(title)
        compose.onNodeWithContentDescription(string(R.string.reader_open_original)).assertIsDisplayed()
        // The reader is full screen: no bottom bar.
        // Waits out the screen transition, during which the previous screen is still there.
        compose.awaitNoText(string(R.string.nav_library))
    }

    @Test
    fun bottomBar_switchesBetweenNewsAndLibrary() {
        launch()
        compose.awaitText(FIRST_STORY)
        navItem(R.string.nav_news).assertIsSelected()

        navItem(R.string.nav_library).performClick()
        compose.awaitText(string(R.string.library_later_empty_title))
        navItem(R.string.nav_library).assertIsSelected()
        compose.awaitNoText(FIRST_STORY)

        navItem(R.string.nav_news).performClick()
        compose.awaitText(FIRST_STORY)
        navItem(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun cardTap_opensReader_andBackReturnsToNews() {
        launch()
        compose.awaitText(FIRST_STORY)
        compose.onNodeWithText(FIRST_STORY).performClick()
        assertReaderShows(FIRST_STORY)
        compose.awaitText("Researchers found", substring = true)

        compose.onNodeWithContentDescription(string(R.string.icon_back)).performClick()
        compose.awaitText(FIRST_STORY)
        compose.awaitNode(hasText(string(R.string.nav_library)))
        navItem(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun deepLink_opensReader_andBackGoesToNews() {
        launch(webLink())
        assertReaderShows(LINKED_STORY)
        assertTrue("123" in itemRequests)

        compose.onNodeWithContentDescription(string(R.string.icon_back)).performClick()
        compose.awaitText(FIRST_STORY)
        navItem(R.string.nav_news).assertIsSelected()
    }

    @Test
    fun deepLink_whileRunning_opensReader() {
        launch()
        compose.awaitText(FIRST_STORY)
        val link = webLink()
        // The system calls onNewIntent for a singleTop activity that is already on top.
        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, link) }
        assertReaderShows(LINKED_STORY)
        compose.onNode(hasContentDescription(string(R.string.icon_back))).assertIsDisplayed()
    }

    /** In-memory settings: the app's DataStore is a process-wide singleton that would leak between tests. */
    private class InMemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    private companion object {
        const val DEEP_LINK = "https://agent7jacob-7177.github.io/code7/#story/123"
        const val FIRST_STORY = "AI made self-hosting fun again"
        const val LINKED_STORY = "Opened from a web link"
        const val ARTICLE = "reader/article.txt"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val DEEP_LINKED_ITEM =
            """{"id":123,"type":"story","author":"pg","title":"$LINKED_STORY",""" +
                """"url":"https://example.com/linked","points":5,"created_at_i":1795729665,"children":[]}"""
    }
}
