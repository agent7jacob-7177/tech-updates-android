package com.code7.techupdates.ui.feed

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.code7.techupdates.R
import kotlinx.coroutines.launch

/** Shows the feed's one-off events as snackbars. */
@Composable
internal fun FeedEventsEffect(
    viewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
) {
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
}
