package com.code7.techupdates.ui.feed

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.timeAgo
import com.code7.techupdates.ui.common.MessageState
import com.code7.techupdates.ui.theme.Code7Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FeedRoute(
    onOpenStory: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FeedEvent.LaterToggled -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message =
                                    resources.getString(
                                        if (event.saved) R.string.feed_saved_later else R.string.feed_removed_later,
                                    ),
                                actionLabel = resources.getString(R.string.feed_undo),
                                duration = SnackbarDuration.Short,
                            )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoToggleLater(event)
                    }
                }
            }
        }
    }
    val actions =
        remember(viewModel) {
            FeedActions(
                onSelectTab = viewModel::selectTab,
                onSelectTopic = viewModel::selectTopic,
                onSearchTextChange = viewModel::onSearchTextChange,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onCardShown = viewModel::onCardShown,
                onOpen = { story ->
                    viewModel.onOpen(story)
                    onOpenStory(story.id)
                },
                onOpenComments = { story ->
                    viewModel.onOpenComments(story)
                    onOpenComments(story.id)
                },
                onToggleLater = viewModel::toggleLater,
            )
        }
    FeedScreen(state, actions, snackbarHostState, onOpenSettings)
}

@Composable
fun FeedScreen(
    state: FeedUiState,
    actions: FeedActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = { FeedTopBar(state.searchText, actions.onSearchTextChange, onOpenSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                FeedTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { actions.onSelectTab(tab) },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            if (state.tab != FeedTab.FOR_YOU) TopicChips(state.topicId, actions.onSelectTopic)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when (val content = state.content) {
                    FeedContent.Loading -> {
                        SkeletonList()
                    }

                    FeedContent.Error -> {
                        ScrollableMessage(
                            title = stringResource(R.string.feed_error_title),
                            message = stringResource(R.string.feed_error_subtitle),
                            actionLabel = stringResource(R.string.feed_retry),
                            onAction = actions.onRetry,
                        )
                    }

                    FeedContent.ComingSoon -> {
                        ScrollableMessage(
                            title = stringResource(R.string.feed_for_you_title),
                            message = stringResource(R.string.feed_for_you_subtitle),
                        )
                    }

                    is FeedContent.Stories -> {
                        StoryList(content, actions)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTopBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(searchText.isNotEmpty()) }
    val close = {
        searching = false
        onSearchTextChange("")
    }
    BackHandler(enabled = searching, onBack = close)
    TopAppBar(
        title = {
            if (searching) {
                SearchField(searchText, onSearchTextChange)
            } else {
                Text(stringResource(R.string.app_name))
            }
        },
        navigationIcon = {
            if (searching) {
                IconButton(onClick = close) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.feed_search_close))
                }
            }
        },
        actions = {
            when {
                !searching -> {
                    IconButton(onClick = { searching = true }) {
                        Icon(painterResource(R.drawable.ic_search), stringResource(R.string.feed_search_open))
                    }
                    OverflowMenu(onOpenSettings)
                }

                searchText.isNotEmpty() -> {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.feed_search_clear))
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.feed_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Composable
private fun TopicChips(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Topic.entries, key = { it.name }) { topic ->
            FilterChip(
                selected = topic.id == selectedId,
                onClick = { onSelect(topic.id) },
                label = { Text(stringResource(topic.labelRes)) },
            )
        }
    }
}

@Composable
private fun StoryList(
    content: FeedContent.Stories,
    actions: FeedActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content.offlineSinceEpochSec?.let { since ->
            item(key = "offline") { OfflineBanner(timeAgo(since, content.nowEpochSec)) }
        }
        if (content.isEmpty) {
            item(key = "empty") {
                val allRead = content.hiddenCount > 0
                MessageState(
                    title = stringResource(if (allRead) R.string.feed_all_read_title else R.string.feed_empty_title),
                    message =
                        stringResource(if (allRead) R.string.feed_all_read_subtitle else R.string.feed_empty_subtitle),
                )
            }
        }
        itemsIndexed(content.cards, key = { _, card -> card.story.id }) { index, card ->
            LaunchedEffect(card.story.id) {
                delay(FeedViewModel.IMPRESSION_DELAY_MS)
                actions.onCardShown(card.story)
            }
            StoryCard(
                card = card,
                featured = content.showFeatured && index == 0,
                nowEpochSec = content.nowEpochSec,
                onOpen = { actions.onOpen(card.story) },
                onOpenComments = { actions.onOpenComments(card.story) },
                onToggleLater = { actions.onToggleLater(card.story) },
            )
        }
        item(key = "footer") { ListFooter(content, actions.onLoadMore) }
    }
}

private fun previewStories(offline: Boolean) =
    FeedContent.Stories(
        cards = PreviewData.cards,
        hiddenCount = 0,
        showFeatured = true,
        canLoadMore = false,
        isAppending = false,
        appendFailed = false,
        offlineSinceEpochSec = if (offline) PreviewData.NOW - 1_800 else null,
        nowEpochSec = PreviewData.NOW,
    )

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenPreview() {
    Code7Theme(dynamicColor = false) {
        FeedScreen(FeedUiState(topicId = "AI", content = previewStories(offline = true)), FeedActions())
    }
}

@Preview(name = "Loading light", showBackground = true)
@Preview(name = "Loading dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenLoadingPreview() {
    Code7Theme(dynamicColor = false) { FeedScreen(FeedUiState(content = FeedContent.Loading), FeedActions()) }
}

@Preview(name = "Error light", showBackground = true)
@Preview(name = "Error dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedScreenErrorPreview() {
    Code7Theme(dynamicColor = false) { FeedScreen(FeedUiState(content = FeedContent.Error), FeedActions()) }
}
