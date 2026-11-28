package com.code7.techupdates.ui.feed

import com.code7.techupdates.data.FeedSource
import com.code7.techupdates.data.Impression
import com.code7.techupdates.data.LaterToggle
import com.code7.techupdates.data.LearnerEvent
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.Page
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.data.local.FeedSettings
import com.code7.techupdates.domain.FeedQuery
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun story(
    id: String,
    title: String = "Story $id",
    points: Int = 10,
    url: String? = "https://www.example.com/$id",
    storyText: String? = null,
) = Story(id, title, url, "pg", points, 3, 1_000L, storyText)

fun page(
    vararg ids: String,
    page: Int = 0,
    nbPages: Int = 1,
    offline: Boolean = false,
) = Page(ids.map { story(it) }, page, nbPages, fetchedAtEpochSec = 500L, fromCache = offline, offline = offline)

class FakeFeedSource : FeedSource {
    val requests = mutableListOf<Pair<FeedQuery, Boolean>>()
    val remembered = mutableMapOf<String, Story>()

    /** Responses by page number; a pending [gate] suspends the next call until completed. */
    var responses: MutableMap<Int, Result<Page>> = mutableMapOf(0 to Result.success(page("1", "2")))
    var gate: CompletableDeferred<Unit>? = null

    val lastQuery: FeedQuery get() = requests.last().first

    override suspend fun feed(
        query: FeedQuery,
        forceRefresh: Boolean,
    ): Result<Page> {
        requests += query to forceRefresh
        gate?.let {
            it.await()
            gate = null
        }
        return responses[query.page] ?: Result.failure(IllegalStateException("no page ${query.page}"))
    }

    override fun remember(story: Story) {
        remembered[story.id] = story
    }

    override fun story(id: String): Story? = remembered[id]
}

class FakeLibrary : LaterToggle {
    val later = MutableStateFlow<Set<String>>(emptySet())
    val read = MutableStateFlow<Set<String>>(emptySet())

    override fun laterIds(): Flow<Set<String>> = later

    override fun readIds(): Flow<Set<String>> = read

    override suspend fun toggleLater(
        story: Story,
        on: Boolean?,
    ): Boolean {
        val target = on ?: (story.id !in later.value)
        later.update { if (target) it + story.id else it - story.id }
        return target
    }
}

class FakeLearner : LearnerStore {
    val recorded = mutableListOf<Pair<SignalKind, String>>()
    val impressionIds = mutableListOf<String>()

    override fun events(): Flow<List<LearnerEvent>> = emptyFlow()

    override fun impressions(): Flow<List<Impression>> = emptyFlow()

    override suspend fun record(
        kind: SignalKind,
        story: Story,
    ) {
        recorded += kind to story.id
    }

    override suspend fun forget(
        kind: SignalKind,
        storyId: String,
    ) {
        recorded.remove(kind to storyId)
    }

    override suspend fun recordImpression(story: Story): Boolean {
        impressionIds += story.id
        return true
    }

    override suspend fun reset() = Unit
}

class FakeSettings(
    feed: String = "top",
    topic: String = "",
) : FeedSettings {
    override val selectedFeed = MutableStateFlow(feed)
    override val selectedTopic = MutableStateFlow(topic)

    override suspend fun setSelectedFeed(feed: String) {
        selectedFeed.value = feed
    }

    override suspend fun setSelectedTopic(topicId: String) {
        selectedTopic.value = topicId
    }
}
