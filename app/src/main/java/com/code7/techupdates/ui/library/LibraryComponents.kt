package com.code7.techupdates.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.code7.techupdates.R
import com.code7.techupdates.ui.feed.StoryCard
import com.code7.techupdates.ui.feed.StoryCardUi
import com.code7.techupdates.ui.theme.Code7Theme

/** Swipe left to remove; in "Read later", swipe right to mark as read. */
@Composable
internal fun SwipeableCard(
    card: StoryCardUi,
    nowEpochSec: Long,
    canMarkRead: Boolean,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
) {
    val swipeState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        enableDismissFromStartToEnd = canMarkRead,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.onMarkRead(card.story)
                SwipeToDismissBoxValue.EndToStart -> actions.onRemove(card.story)
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = { SwipeBackground(swipeState.dismissDirection) },
    ) {
        StoryCard(
            card = card,
            featured = false,
            nowEpochSec = nowEpochSec,
            onOpen = { actions.onOpen(card.story) },
            onOpenComments = { actions.onOpenComments(card.story) },
            onToggleLater = { actions.onToggleLater(card.story) },
        )
    }
}

@Composable
internal fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val colors = MaterialTheme.colorScheme
    val style =
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> {
                SwipeStyle(
                    colors.primaryContainer,
                    colors.onPrimaryContainer,
                    R.drawable.ic_check,
                    R.string.library_mark_read,
                )
            }

            SwipeToDismissBoxValue.EndToStart -> {
                SwipeStyle(
                    colors.errorContainer,
                    colors.onErrorContainer,
                    R.drawable.ic_delete,
                    R.string.library_remove,
                )
            }

            SwipeToDismissBoxValue.Settled -> {
                return
            }
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(style.container)
                .padding(horizontal = 24.dp),
        contentAlignment =
            if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(painterResource(style.icon), contentDescription = stringResource(style.label), tint = style.content)
    }
}

private data class SwipeStyle(
    val container: Color,
    val content: Color,
    val icon: Int,
    val label: Int,
)

@Composable
internal fun ClearHistoryDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_clear_title)) },
        text = { Text(pluralStringResource(R.plurals.library_clear_message, count, count)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_clear_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Preview(name = "Swipe background light", showBackground = true)
@Preview(name = "Swipe background dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SwipeBackgroundPreview() {
    Code7Theme(dynamicColor = false) {
        Surface {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(72.dp)) { SwipeBackground(SwipeToDismissBoxValue.StartToEnd) }
                Box(Modifier.fillMaxWidth().height(72.dp)) { SwipeBackground(SwipeToDismissBoxValue.EndToStart) }
            }
        }
    }
}
