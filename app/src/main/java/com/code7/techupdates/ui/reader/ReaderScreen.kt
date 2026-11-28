package com.code7.techupdates.ui.reader

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.code7.techupdates.R
import com.code7.techupdates.ui.common.MessageState
import com.code7.techupdates.ui.common.openInBrowser
import com.code7.techupdates.ui.common.rememberCustomTabsUriHandler
import com.code7.techupdates.ui.common.shareText
import com.code7.techupdates.ui.theme.Code7Theme
import kotlinx.coroutines.launch

@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    onOpenComments: (String) -> Unit,
    viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(state)
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = resources.getString(event.messageRes),
                        actionLabel = resources.getString(R.string.reader_undo),
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
            }
        }
    }
    val actions =
        remember(viewModel) {
            ReaderActions(
                onBack = onBack,
                onToggleLater = viewModel::toggleLater,
                onShare = {
                    val title =
                        current.story
                            ?.story
                            ?.title
                            .orEmpty()
                    context.shareText(
                        subject = title,
                        text = listOf(title, current.browserUrl).filter { it.isNotEmpty() }.joinToString("\n"),
                        chooserTitle = resources.getString(R.string.reader_share_chooser),
                    )
                },
                onOpenInBrowser = { context.openInBrowser(current.browserUrl) },
                onContinueOnSite = {
                    viewModel.onContinueOnSite()
                    context.openInBrowser(current.browserUrl)
                },
                onOpenComments = { onOpenComments(current.storyId) },
                onRetry = viewModel::retry,
                onReachedEnd = viewModel::onReachedEnd,
            )
        }
    ReaderScreen(state, actions, snackbarHostState)
}

private val ReaderEvent.messageRes: Int
    get() =
        when (this) {
            is ReaderEvent.LaterToggled -> if (saved) R.string.reader_saved_later else R.string.reader_removed_later
            is ReaderEvent.MarkedRead -> if (auto) R.string.reader_finished else R.string.reader_marked_read
        }

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    actions: ReaderActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollState = rememberScrollState()
    val progress by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            if (max in 1 until Int.MAX_VALUE) (scrollState.value.toFloat() / max).coerceIn(0f, 1f) else 0f
        }
    }
    var viewportBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var endTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val onReachedEnd by rememberUpdatedState(actions.onReachedEnd)
    LaunchedEffect(scrollState) {
        // Like the JS scroll listener: re-check on every scroll while the end block is on screen.
        snapshotFlow { scrollState.value to (endTop < viewportBottom) }
            .collect { (_, endVisible) -> if (endVisible) onReachedEnd() }
    }
    Scaffold(
        topBar = { ReaderTopBar(state, actions, progress = { progress }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        CompositionLocalProvider(LocalUriHandler provides rememberCustomTabsUriHandler()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .onGloballyPositioned { viewportBottom = it.boundsInRoot().bottom },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                ) {
                    when {
                        state.storyFailed -> {
                            StoryError(actions)
                        }

                        state.story == null -> {
                            ReaderHeadline(title = stringResource(R.string.reader_loading))
                            Spacer(Modifier.height(16.dp))
                            ArticleSkeleton()
                        }

                        else -> {
                            ReaderArticle(
                                story = state.story,
                                body = state.body,
                                nowEpochSec = state.nowEpochSec,
                                actions = actions,
                                modifier = Modifier.onGloballyPositioned { endTop = it.positionInRoot().y },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    actions: ReaderActions,
    progress: () -> Float,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = state.story?.domain.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.icon_back))
                }
            },
            actions = {
                if (state.story != null) {
                    IconToggleButton(checked = state.isSaved, onCheckedChange = { actions.onToggleLater() }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (state.isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                                ),
                            contentDescription =
                                stringResource(if (state.isSaved) R.string.reader_unsave else R.string.reader_save),
                            tint = with(MaterialTheme.colorScheme) { if (state.isSaved) primary else onSurfaceVariant },
                        )
                    }
                    IconButton(onClick = actions.onShare) {
                        Icon(painterResource(R.drawable.ic_share), stringResource(R.string.reader_share))
                    }
                }
                IconButton(onClick = actions.onOpenInBrowser) {
                    Icon(painterResource(R.drawable.ic_open_external), stringResource(R.string.reader_open_original))
                }
            },
        )
        val progressLabel = stringResource(R.string.reader_progress)
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(2.dp).semantics { contentDescription = progressLabel },
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

@Composable
private fun StoryError(actions: ReaderActions) {
    MessageState(
        title = stringResource(R.string.reader_story_error_title),
        message = stringResource(R.string.reader_story_error_subtitle),
        actionLabel = stringResource(R.string.reader_retry),
        onAction = actions.onRetry,
    )
    OutlinedButton(onClick = actions.onOpenInBrowser, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.reader_open_on_hn))
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReaderScreenPreview() {
    Code7Theme(dynamicColor = false) {
        ReaderScreen(ReaderPreviewData.article, ReaderActions())
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReaderFetchFailedPreview() {
    Code7Theme(dynamicColor = false) {
        ReaderScreen(ReaderPreviewData.failed, ReaderActions())
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReaderStoryErrorPreview() {
    Code7Theme(dynamicColor = false) {
        ReaderScreen(ReaderUiState(storyId = "1", storyFailed = true), ReaderActions())
    }
}
