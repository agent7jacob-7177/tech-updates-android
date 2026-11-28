package com.code7.techupdates.ui.library

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.ui.common.MessageState
import com.code7.techupdates.ui.feed.PreviewData
import com.code7.techupdates.ui.feed.StoryCard
import com.code7.techupdates.ui.feed.StoryCardUi
import com.code7.techupdates.ui.theme.Code7Theme
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    onOpenStory: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = resources.getString(event.messageRes),
                        actionLabel = resources.getString(R.string.library_undo),
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
            }
        }
    }
    val actions =
        remember(viewModel) {
            LibraryActions(
                onSelectTab = viewModel::selectTab,
                onOpen = { story ->
                    viewModel.onOpen(story)
                    onOpenStory(story.id)
                },
                onOpenComments = { story ->
                    viewModel.onOpenComments(story)
                    onOpenComments(story.id)
                },
                onToggleLater = viewModel::toggleLater,
                onMarkRead = viewModel::markRead,
                onRemove = viewModel::remove,
                onRequestClear = viewModel::requestClearRead,
                onConfirmClear = viewModel::confirmClearRead,
                onDismissClear = viewModel::dismissClearRead,
            )
        }
    LibraryScreen(state, actions, snackbarHostState)
}

private val LibraryEvent.messageRes: Int
    get() =
        when (this) {
            is LibraryEvent.LaterRemoved -> {
                R.string.library_removed_from_later
            }

            is LibraryEvent.MarkedRead -> {
                R.string.library_marked_as_read
            }

            is LibraryEvent.ReadRemoved -> {
                R.string.library_removed_from_read
            }

            is LibraryEvent.HistoryCleared -> {
                R.string.library_history_cleared
            }

            is LibraryEvent.LaterToggled -> {
                if (saved) R.string.library_saved_later else R.string.library_removed_from_later
            }
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    if (state.canClear) {
                        TextButton(
                            onClick = actions.onRequestClear,
                        ) { Text(stringResource(R.string.library_clear_all)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LibraryTabs(state, actions.onSelectTab)
            when {
                state.isLoading -> Unit
                state.cards.isEmpty() -> EmptyLibrary(state.tab)
                else -> LibraryList(state, actions)
            }
        }
    }
    if (state.confirmingClear) {
        ClearHistoryDialog(state.readCount, actions.onConfirmClear, actions.onDismissClear)
    }
}

@Composable
private fun LibraryTabs(
    state: LibraryUiState,
    onSelect: (LibraryTab) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
        LibraryTab.entries.forEach { tab ->
            val count = if (tab == LibraryTab.LATER) state.laterCount else state.readCount
            Tab(
                selected = tab == state.tab,
                onClick = { onSelect(tab) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(tab.labelRes))
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) { Text(count.toString()) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(tab: LibraryTab) {
    val later = tab == LibraryTab.LATER
    MessageState(
        title = stringResource(if (later) R.string.library_later_empty_title else R.string.library_read_empty_title),
        message =
            stringResource(if (later) R.string.library_later_empty_subtitle else R.string.library_read_empty_subtitle),
    )
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    actions: LibraryActions,
) {
    val markReadLabel = stringResource(R.string.library_mark_read)
    val removeLabel = stringResource(R.string.library_remove)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.cards, key = { "${state.tab}-${it.story.id}" }) { card ->
            val canMarkRead = state.tab == LibraryTab.LATER
            val a11yActions =
                buildList {
                    if (canMarkRead) {
                        add(
                            CustomAccessibilityAction(markReadLabel) {
                                actions.onMarkRead(card.story)
                                true
                            },
                        )
                    }
                    add(
                        CustomAccessibilityAction(removeLabel) {
                            actions.onRemove(card.story)
                            true
                        },
                    )
                }
            SwipeableCard(
                card = card,
                nowEpochSec = state.nowEpochSec,
                canMarkRead = canMarkRead,
                actions = actions,
                modifier = Modifier.animateItem().semantics { customActions = a11yActions },
            )
        }
    }
}

private val LibraryTab.labelRes: Int
    get() =
        when (this) {
            LibraryTab.LATER -> R.string.library_tab_later
            LibraryTab.READ -> R.string.library_tab_read
        }

private fun previewState(
    tab: LibraryTab = LibraryTab.LATER,
    empty: Boolean = false,
    confirmingClear: Boolean = false,
) = LibraryUiState(
    tab = tab,
    laterCount = if (tab == LibraryTab.LATER && empty) 0 else 2,
    readCount = 14,
    isLoading = false,
    cards = if (empty) emptyList() else PreviewData.cards,
    confirmingClear = confirmingClear,
    nowEpochSec = PreviewData.NOW,
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenPreview() {
    Code7Theme(dynamicColor = false) { LibraryScreen(previewState(), LibraryActions()) }
}

@Preview(name = "Empty light", showBackground = true)
@Preview(name = "Empty dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenEmptyPreview() {
    Code7Theme(dynamicColor = false) { LibraryScreen(previewState(empty = true), LibraryActions()) }
}

@Preview(name = "Clear dialog light", showBackground = true)
@Preview(name = "Clear dialog dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenClearPreview() {
    Code7Theme(dynamicColor = false) {
        LibraryScreen(previewState(tab = LibraryTab.READ, confirmingClear = true), LibraryActions())
    }
}
