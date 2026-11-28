package com.code7.techupdates.ui.foryou

import com.code7.techupdates.data.CandidateSource
import com.code7.techupdates.data.Impression
import com.code7.techupdates.data.LearnerEvent
import com.code7.techupdates.data.LearnerStore
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.domain.SearchRequest
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Answers every search with [stories], or fails when [failing]. */
class FakeCandidates(
    var stories: List<Story> = emptyList(),
) : CandidateSource {
    val requests = mutableListOf<SearchRequest>()
    var failing: (SearchRequest) -> Boolean = { false }

    override suspend fun candidates(request: SearchRequest): Result<List<Story>> {
        requests += request
        return if (failing(request)) Result.failure(IllegalStateException("offline")) else Result.success(stories)
    }
}

class FakeLearnerData : LearnerStore {
    val eventList = MutableStateFlow<List<LearnerEvent>>(emptyList())
    val impressionList = MutableStateFlow<List<Impression>>(emptyList())
    var resets = 0

    override fun events() = eventList

    override fun impressions() = impressionList

    override suspend fun record(
        kind: SignalKind,
        story: Story,
    ) {
        eventList.update { it + LearnerEvent(kind, story, it.size.toLong()) }
    }

    override suspend fun forget(
        kind: SignalKind,
        storyId: String,
    ) {
        eventList.update { events -> events.filterNot { it.kind == kind && it.story.id == storyId } }
    }

    override suspend fun recordImpression(story: Story): Boolean {
        impressionList.update { it + Impression(story, 1, it.size.toLong()) }
        return true
    }

    override suspend fun reset() {
        resets++
        eventList.value = emptyList()
        impressionList.value = emptyList()
    }
}
