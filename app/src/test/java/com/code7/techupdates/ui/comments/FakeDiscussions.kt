package com.code7.techupdates.ui.comments

import com.code7.techupdates.data.Discussion
import com.code7.techupdates.data.DiscussionSource
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

class FakeDiscussions : DiscussionSource {
    val cached = mutableMapOf<String, Story>()
    var result: Result<Discussion> = Result.failure(IOException("offline"))
    var calls = 0

    /** When set, the next call suspends until completed. */
    var gate: CompletableDeferred<Unit>? = null

    override fun story(id: String): Story? = cached[id]

    override suspend fun discussion(storyId: String): Result<Discussion> {
        calls++
        gate?.let {
            it.await()
            gate = null
        }
        return result
    }
}
