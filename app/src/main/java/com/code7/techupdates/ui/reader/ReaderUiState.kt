package com.code7.techupdates.ui.reader

import com.code7.techupdates.data.ReadReceipt
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.domainOf
import com.code7.techupdates.domain.safeUrl
import com.code7.techupdates.ui.feed.StoryCardUi

/** A friendly note shown in the reader instead of (or above) the article text. */
enum class ReaderNote {
    /** Under 80 words extracted: paywall, JS-only page or mostly media. */
    SHORT_PREVIEW,

    /** The reader service is throttling (HTTP 429). */
    RATE_LIMITED,

    /** The site refuses reader copies (HTTP 451). */
    BLOCKED,

    /** Any other failure: 5xx, timeout, offline, empty answer. */
    FETCH_FAILED,
}

/** The article area below the byline (JS `#r-content`). */
sealed interface ReaderBody {
    /** Skeleton lines while the article text is fetched. */
    data object Loading : ReaderBody

    /**
     * [storyHtml] is a self post's own text (HN HTML), [markdown] the tidied article text; either may
     * be null. [note] explains a short or missing article; [readingMinutes] is null for short texts.
     */
    data class Ready(
        val storyHtml: String? = null,
        val markdown: String? = null,
        val note: ReaderNote? = null,
        val readingMinutes: Int? = null,
    ) : ReaderBody
}

/** Header data for a resolved story (JS `renderShell`). */
data class ReaderStory(
    val story: Story,
) {
    val domain: String = domainOf(story.url).ifEmpty { StoryCardUi.FALLBACK_DOMAIN }
    val topic: Topic? = Topic.forTitle(story.title)

    /** The article URL if it is http(s), else null (self posts). */
    val originalUrl: String? = safeUrl(story.url)
    val hnUrl: String = hnLink(story.id)

    /** Where "Open original" / "Open in browser" go: the article, or the HN thread for self posts. */
    val browserUrl: String get() = originalUrl ?: hnUrl
}

fun hnLink(id: String) = "https://news.ycombinator.com/item?id=" + java.net.URLEncoder.encode(id, "UTF-8")

data class ReaderUiState(
    val storyId: String,
    /** Null while the story is resolved by id, or if that failed ([storyFailed]). */
    val story: ReaderStory? = null,
    val storyFailed: Boolean = false,
    val body: ReaderBody = ReaderBody.Loading,
    val isSaved: Boolean = false,
    val isRead: Boolean = false,
    val nowEpochSec: Long = 0,
) {
    val isLoadingStory: Boolean get() = story == null && !storyFailed

    /** "Open in browser" target, available in every state so the reader is never a dead end. */
    val browserUrl: String get() = story?.browserUrl ?: hnLink(storyId)
}

/** One-off reader events, shown as snackbars. */
sealed interface ReaderEvent {
    data class LaterToggled(
        val saved: Boolean,
    ) : ReaderEvent

    /** [auto] = finished by scrolling to the end ("Finished. Moved to Read"). */
    data class MarkedRead(
        val receipt: ReadReceipt,
        val auto: Boolean,
    ) : ReaderEvent
}
