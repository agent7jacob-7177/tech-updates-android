package com.code7.techupdates.ui.reader

import com.code7.techupdates.data.ArticleSource
import com.code7.techupdates.data.StoryLookup
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

class FakeStoryLookup : StoryLookup {
    val cached = mutableMapOf<String, Story>()
    var fetchResult: Result<Story> = Result.failure(IOException("offline"))
    var fetches = 0

    override fun story(id: String): Story? = cached[id]

    override fun remember(story: Story) {
        cached[story.id] = story
    }

    override suspend fun fetchStory(id: String): Result<Story> {
        fetches++
        return fetchResult
    }
}

class FakeArticles : ArticleSource {
    val requests = mutableListOf<String>()
    var result: Result<String> = Result.success("")

    /** When set, the next call suspends until completed. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun article(url: String): Result<String> {
        requests += url
        gate?.let {
            it.await()
            gate = null
        }
        return result
    }
}
