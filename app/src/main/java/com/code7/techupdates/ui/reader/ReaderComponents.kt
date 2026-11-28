package com.code7.techupdates.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.timeAgo
import com.code7.techupdates.ui.feed.tagRes
import com.code7.techupdates.ui.theme.Code7Theme
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalImageWidth
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.ImageWidth

/**
 * Headline, byline, article text and the closing "Continue on …" block (JS `renderShell` + content).
 * [modifier] goes on that closing block, whose position drives the auto mark-as-read.
 */
@Composable
internal fun ReaderArticle(
    story: ReaderStory,
    body: ReaderBody,
    nowEpochSec: Long,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
) {
    MetaTop(story)
    Spacer(Modifier.height(8.dp))
    ReaderHeadline(story.story.title.ifEmpty { stringResource(R.string.reader_untitled) })
    Spacer(Modifier.height(12.dp))
    Byline(story.story, nowEpochSec, (body as? ReaderBody.Ready)?.readingMinutes)
    DiscussButton(story.story.numComments, actions.onOpenComments)
    Spacer(Modifier.height(8.dp))
    when (body) {
        ReaderBody.Loading -> ArticleSkeleton()
        is ReaderBody.Ready -> ArticleContent(body, actions)
    }
    Spacer(Modifier.height(24.dp))
    ContinueBlock(story, actions, modifier)
}

@Composable
internal fun ReaderHeadline(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MetaTop(story: ReaderStory) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            story.domain,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (story.topic != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    stringResource(story.topic.tagRes),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Byline(
    story: Story,
    nowEpochSec: Long,
    readingMinutes: Int?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BylineItem(
            R.drawable.ic_arrow_up,
            pluralStringResource(R.plurals.card_points, story.points, story.points),
            muted,
        )
        if (story.author.isNotEmpty()) BylineItem(null, stringResource(R.string.reader_by_author, story.author), muted)
        if (story.createdAtEpochSec > 0) {
            BylineItem(R.drawable.ic_clock, timeAgo(story.createdAtEpochSec, nowEpochSec), muted)
        }
        if (readingMinutes != null) {
            BylineItem(null, pluralStringResource(R.plurals.reader_reading_time, readingMinutes, readingMinutes), muted)
        }
    }
}

@Composable
private fun BylineItem(
    iconRes: Int?,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun DiscussButton(
    comments: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_chat), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(pluralStringResource(R.plurals.reader_discuss, comments, comments))
    }
}

@Composable
private fun ArticleContent(
    body: ReaderBody.Ready,
    actions: ReaderActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (body.storyHtml != null) StoryText(body.storyHtml)
        if (body.note != null) NoteCard(body.note, actions)
        if (body.markdown != null) {
            CompositionLocalProvider(LocalImageWidth provides ImageWidth.IMAGE_WIDTH) {
                Markdown(
                    content = body.markdown,
                    imageTransformer = Coil3ImageTransformerImpl,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A self post's own HN HTML as annotated text; links open via [androidx.compose.ui.platform.LocalUriHandler]. */
@Composable
private fun StoryText(html: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text =
        remember(html, linkColor) {
            AnnotatedString.fromHtml(
                html,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
        }
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun NoteCard(
    note: ReaderNote,
    actions: ReaderActions,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(note.messageRes), style = MaterialTheme.typography.bodyMedium)
            if (note != ReaderNote.SHORT_PREVIEW) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.onOpenInBrowser) { Text(stringResource(R.string.reader_open_in_browser)) }
                    if (note != ReaderNote.BLOCKED) {
                        OutlinedButton(onClick = actions.onRetry) { Text(stringResource(R.string.reader_retry)) }
                    }
                }
            }
        }
    }
}

private val ReaderNote.messageRes: Int
    get() =
        when (this) {
            ReaderNote.SHORT_PREVIEW -> R.string.reader_note_short
            ReaderNote.RATE_LIMITED -> R.string.reader_note_rate_limited
            ReaderNote.BLOCKED -> R.string.reader_note_blocked
            ReaderNote.FETCH_FAILED -> R.string.reader_note_failed
        }

/** JS `.continue`: where to go next. Its position drives the auto mark-as-read. */
@Composable
private fun ContinueBlock(
    story: ReaderStory,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(
            stringResource(
                if (story.originalUrl != null) R.string.reader_continue_note else R.string.reader_continue_note_hn,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (story.originalUrl != null) {
            Button(onClick = actions.onContinueOnSite, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reader_continue_on, story.domain))
            }
        }
        OutlinedButton(onClick = actions.onOpenComments, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.reader_discuss, story.story.numComments, story.story.numComments))
        }
    }
}

internal object ReaderPreviewData {
    private const val NOW = 1_795_822_000L

    private val story =
        Story(
            id = "1",
            title = "Anthropic releases a new Claude model with longer context",
            url = "https://www.anthropic.com/news/claude",
            author = "pg",
            points = 512,
            numComments = 230,
            createdAtEpochSec = NOW - 3_600,
            storyText = null,
        )

    val article =
        ReaderUiState(
            storyId = "1",
            story = ReaderStory(story),
            body =
                ReaderBody.Ready(
                    markdown =
                        "The new model handles **much longer** documents.\n\n" +
                            "## What changed\n\n- Longer context\n- Faster answers\n\n" +
                            "Read the [announcement](https://www.anthropic.com/news).",
                    readingMinutes = 4,
                ),
            isSaved = true,
            nowEpochSec = NOW,
        )

    val failed =
        ReaderUiState(
            storyId = "2",
            story =
                ReaderStory(
                    story.copy(
                        id = "2",
                        title = "Ask HN: How do you keep up with Rust compiler changes?",
                        url = "https://blog.rust-lang.org/",
                        storyText = "<p>I maintain a few crates &amp; keep missing <i>breaking</i> changes.</p>",
                    ),
                ),
            body =
                ReaderBody.Ready(
                    storyHtml = "<p>I maintain a few crates &amp; keep missing <i>breaking</i> changes.</p>",
                    note = ReaderNote.RATE_LIMITED,
                ),
            nowEpochSec = NOW,
        )
}
