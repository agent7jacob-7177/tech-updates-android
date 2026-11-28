package com.code7.techupdates.ui.library

import com.code7.techupdates.data.LibrarySource
import com.code7.techupdates.data.ReadHow
import com.code7.techupdates.data.ReadReceipt
import com.code7.techupdates.data.ReadStory
import com.code7.techupdates.data.ReaderLibrary
import com.code7.techupdates.data.SavedStory
import com.code7.techupdates.data.SignalKind
import com.code7.techupdates.domain.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [LibrarySource] mirroring [com.code7.techupdates.data.LibraryRepository]'s rules. */
class FakeLibrarySource :
    LibrarySource,
    ReaderLibrary {
    val later = MutableStateFlow<List<SavedStory>>(emptyList())
    val read = MutableStateFlow<List<ReadStory>>(emptyList())

    /** Learner signals as the real repository would leave them. */
    val signals = mutableSetOf<Pair<SignalKind, String>>()
    private var clock = 100L

    fun save(vararg stories: Story) = stories.forEach { later.update { l -> listOf(SavedStory(it, clock++)) + l } }

    fun addRead(vararg stories: Story) =
        stories.forEach { read.update { l -> listOf(ReadStory(it, clock++, ReadHow.MANUAL)) + l } }

    /** How each story was marked read, in call order. */
    val readHows = mutableListOf<Pair<String, ReadHow>>()

    override fun later(): Flow<List<SavedStory>> = later

    override fun isLater(storyId: String): Flow<Boolean> = later.map { l -> l.any { it.story.id == storyId } }

    override fun isRead(storyId: String): Flow<Boolean> = read.map { l -> l.any { it.story.id == storyId } }

    override suspend fun savedStory(storyId: String): Story? =
        (later.value.map { it.story } + read.value.map { it.story }).firstOrNull { it.id == storyId }

    override fun read(): Flow<List<ReadStory>> = read

    override fun laterIds(): Flow<Set<String>> = later.map { l -> l.mapTo(HashSet()) { it.story.id } }

    override fun readIds(): Flow<Set<String>> = read.map { l -> l.mapTo(HashSet()) { it.story.id } }

    override suspend fun toggleLater(
        story: Story,
        on: Boolean?,
    ): Boolean {
        val target = on ?: later.value.none { it.story.id == story.id }
        later.update { l -> l.filterNot { it.story.id == story.id } }
        if (target) {
            save(story)
            signals += SignalKind.SAVE to story.id
        } else {
            signals -= SignalKind.SAVE to story.id
        }
        return target
    }

    override suspend fun removeLater(storyId: String): SavedStory? {
        val entry = later.value.firstOrNull { it.story.id == storyId } ?: return null
        later.update { it - entry }
        signals -= SignalKind.SAVE to storyId
        return entry
    }

    override suspend fun undoRemoveLater(entry: SavedStory) {
        later.update { (it + entry).sortedByDescending(SavedStory::savedAt) }
        signals += SignalKind.SAVE to entry.story.id
    }

    override suspend fun markRead(
        story: Story,
        how: ReadHow,
    ): ReadReceipt? {
        if (read.value.any { it.story.id == story.id }) return null
        readHows += story.id to how
        val wasLater = later.value.any { it.story.id == story.id }
        later.update { l -> l.filterNot { it.story.id == story.id } }
        read.update { listOf(ReadStory(story, clock++, how)) + it }
        signals += SignalKind.READ to story.id
        return ReadReceipt(story, wasLater)
    }

    override suspend fun undoMarkRead(receipt: ReadReceipt) {
        read.update { l -> l.filterNot { it.story.id == receipt.story.id } }
        if (receipt.wasLater) save(receipt.story)
        signals -= SignalKind.READ to receipt.story.id
    }

    override suspend fun removeRead(storyId: String): ReadStory? {
        val entry = read.value.firstOrNull { it.story.id == storyId } ?: return null
        read.update { it - entry }
        signals -= SignalKind.READ to storyId
        return entry
    }

    override suspend fun undoRemoveRead(entry: ReadStory) {
        restoreRead(listOf(entry))
        signals += SignalKind.READ to entry.story.id
    }

    override suspend fun clearRead(): List<ReadStory> = read.value.also { read.value = emptyList() }

    override suspend fun restoreRead(entries: List<ReadStory>) =
        read.update { (it + entries).sortedByDescending(ReadStory::readAt) }
}
