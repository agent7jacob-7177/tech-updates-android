package com.code7.techupdates.ui.foryou

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.code7.techupdates.R
import com.code7.techupdates.ui.common.MessageState
import com.code7.techupdates.ui.feed.FeedActions
import com.code7.techupdates.ui.feed.FeedViewModel
import com.code7.techupdates.ui.feed.SkeletonList
import com.code7.techupdates.ui.feed.StoryCard
import kotlinx.coroutines.delay
import java.util.Date

/** The "For you" tab: insights panel, status line and ranked picks. */
@Composable
fun ForYouList(
    state: ForYouUiState,
    actions: ForYouActions,
    cardActions: FeedActions,
) {
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    if (confirmingReset) {
        ResetLearningDialog(
            onConfirm = {
                confirmingReset = false
                actions.onReset()
            },
            onDismiss = { confirmingReset = false },
        )
    }
    val content = state.content
    if (content == ForYouContent.Loading) {
        SkeletonList(stringResource(R.string.for_you_loading))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.insights?.let { insights ->
            item(key = "insights") { InsightsPanel(insights, onReset = { confirmingReset = true }) }
        }
        when (content) {
            is ForYouContent.Picks -> {
                item(key = "status") { StatusLine(content, state.insights?.isReady == true) }
                if (content.cards.isEmpty()) item(key = "empty") { EmptyPicks(allRead = content.hiddenCount > 0) }
                items(content.cards, key = { it.story.id }) { card ->
                    LaunchedEffect(card.story.id) {
                        delay(FeedViewModel.IMPRESSION_DELAY_MS)
                        cardActions.onCardShown(card.story)
                    }
                    StoryCard(
                        card = card,
                        featured = false,
                        nowEpochSec = content.updatedAtEpochSec,
                        onOpen = { cardActions.onOpen(card.story) },
                        onOpenComments = { cardActions.onOpenComments(card.story) },
                        onToggleLater = { cardActions.onToggleLater(card.story) },
                    )
                }
            }

            else -> {
                item(key = "error") {
                    MessageState(
                        title = stringResource(R.string.feed_error_title),
                        message = stringResource(R.string.feed_error_subtitle),
                        actionLabel = stringResource(R.string.feed_retry),
                        onAction = actions.onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    picks: ForYouContent.Picks,
    ready: Boolean,
) {
    val context = LocalContext.current
    val text =
        if (ready) {
            val time = DateFormat.getTimeFormat(context).format(Date(picks.updatedAtEpochSec * Clock.MILLIS_PER_SECOND))
            pluralStringResource(R.plurals.for_you_status_ranked, picks.cards.size, picks.cards.size, time)
        } else {
            stringResource(R.string.for_you_status_cold)
        }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyPicks(allRead: Boolean) {
    MessageState(
        title = stringResource(if (allRead) R.string.feed_all_read_title else R.string.feed_empty_title),
        message = stringResource(if (allRead) R.string.feed_all_read_subtitle else R.string.feed_empty_subtitle),
    )
}

private object Clock {
    const val MILLIS_PER_SECOND = 1000L
}
