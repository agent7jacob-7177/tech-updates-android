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
internal fun ListFooter(
    content: FeedContent.Stories,
    onLoadMore: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            content.isAppending -> {
                CircularProgressIndicator(Modifier.size(32.dp))
            }

            content.appendFailed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.feed_error_load_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onLoadMore) { Text(stringResource(R.string.feed_retry)) }
                }
            }

            content.canLoadMore -> {
                // Infinite scroll: reaching the footer loads the next page; the button is a fallback.
                LaunchedEffect(content.cards.size, content.hiddenCount) { onLoadMore() }
                OutlinedButton(onClick = onLoadMore) { Text(stringResource(R.string.feed_load_more)) }
            }
        }
    }
}

@Composable
internal fun OfflineBanner(since: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.feed_offline_banner, since), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ScrollableMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    // Scrollable so pull-to-refresh works on this state too.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MessageState(title, message, actionLabel = actionLabel, onAction = onAction)
    }
}

/** Port of JS `renderSkeletons()`. */
@Composable
internal fun SkeletonList() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val loading = stringResource(R.string.feed_loading_latest)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            Text(
                loading,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(Skeleton.COUNT) {
            OutlinedCard(Modifier.fillMaxWidth().alpha(alpha)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonLines.forEach { (fraction, height) -> SkeletonLine(fraction, height) }
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    fraction: Float,
    heightDp: Int,
) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(heightDp.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    )
}

private object Skeleton {
    const val COUNT = 6
}

/** Width fraction and height (dp) of each skeleton line (JS `renderSkeletons`). */
private val SkeletonLines = listOf(0.4f to 12, 0.95f to 16, 0.7f to 16, 0.55f to 12)
