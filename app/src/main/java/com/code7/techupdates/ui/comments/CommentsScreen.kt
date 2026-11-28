package com.code7.techupdates.ui.comments

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.timeAgo
import com.code7.techupdates.ui.common.MessageState
import com.code7.techupdates.ui.common.openInBrowser
import com.code7.techupdates.ui.common.rememberCustomTabsUriHandler
import com.code7.techupdates.ui.theme.Code7Theme

@Composable
fun CommentsRoute(
    onBack: () -> Unit,
    viewModel: CommentsViewModel = viewModel(factory = CommentsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current by rememberUpdatedState(state)
    val actions =
        remember(viewModel) {
            CommentsActions(
                onBack = onBack,
                onOpenOnHn = { context.openInBrowser(current.hnUrl) },
                onRetry = viewModel::retry,
                onToggle = viewModel::toggle,
            )
        }
    CommentsScreen(state, actions)
}

/** Story header plus the flattened thread in one lazy list, so big threads stay smooth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    state: CommentsUiState,
    actions: CommentsActions,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.comments_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.icon_back))
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenOnHn) {
                        Icon(painterResource(R.drawable.ic_open_external), stringResource(R.string.comments_open_on_hn))
                    }
                },
            )
        },
    ) { padding ->
        CompositionLocalProvider(LocalUriHandler provides rememberCustomTabsUriHandler()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding =
                    PaddingValues(
                        bottom =
                            16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
            ) {
                item(
                    key = "header",
                    contentType = "header",
                ) { StoryHeader(state.story, state.domain, state.nowEpochSec) }
                threadItems(state, actions)
            }
        }
    }
}

private fun LazyListScope.threadItems(
    state: CommentsUiState,
    actions: CommentsActions,
) {
    when (val content = state.content) {
        CommentsContent.Loading -> {
            item(key = "loading") { LoadingComments() }
        }

        CommentsContent.Empty -> {
            item(key = "empty") { StatusText(stringResource(R.string.comments_empty)) }
        }

        CommentsContent.Error -> {
            item(key = "error") { CommentsError(actions) }
        }

        is CommentsContent.Ready -> {
            item(key = "summary") {
                StatusText(pluralStringResource(R.plurals.comments_summary, content.total, content.total))
            }
            itemsIndexed(content.rows, key = {
                _,
                node,
                ->
                node.id
            }, contentType = { _, _ -> "comment" }) { index, node ->
                if (node.depth == 0 && index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                CommentItem(
                    node = node,
                    collapsed = node.id in content.collapsed,
                    nowEpochSec = state.nowEpochSec,
                    onToggle = actions.onToggle,
                )
            }
        }
    }
}

@Composable
private fun StoryHeader(
    story: Story?,
    domain: String,
    nowEpochSec: Long,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (domain.isNotEmpty()) {
            Text(domain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text =
                when {
                    story == null -> stringResource(R.string.reader_loading)
                    story.title.isEmpty() -> stringResource(R.string.reader_untitled)
                    else -> story.title
                },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (story != null) {
            val meta =
                listOfNotNull(
                    pluralStringResource(R.plurals.card_points, story.points, story.points),
                    story.author.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.reader_by_author, it) },
                    story.createdAtEpochSec.takeIf { it > 0 }?.let { timeAgo(it, nowEpochSec) },
                ).joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun LoadingComments() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.comments_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CommentsError(actions: CommentsActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        MessageState(
            title = stringResource(R.string.comments_error_title),
            message = stringResource(R.string.comments_error_subtitle),
            actionLabel = stringResource(R.string.comments_retry),
            onAction = actions.onRetry,
        )
        OutlinedButton(onClick = actions.onOpenOnHn) { Text(stringResource(R.string.comments_open_on_hn)) }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentsScreenPreview() {
    Code7Theme(dynamicColor = false) {
        CommentsScreen(CommentsPreviewData.ready, CommentsActions())
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentsLoadingPreview() {
    Code7Theme(dynamicColor = false) {
        CommentsScreen(CommentsUiState("1", story = CommentsPreviewData.story), CommentsActions())
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentsErrorPreview() {
    Code7Theme(dynamicColor = false) {
        CommentsScreen(CommentsUiState("1", content = CommentsContent.Error), CommentsActions())
    }
}
