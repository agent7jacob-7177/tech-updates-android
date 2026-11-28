package com.code7.techupdates.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.code7.techupdates.R
import com.code7.techupdates.ui.theme.Code7Theme

/** Temporary screen for destinations whose real UI lands in a later issue. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.icon_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        MessageState(
            title = stringResource(R.string.coming_soon),
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaceholderScreenPreview() {
    Code7Theme(dynamicColor = false) {
        PlaceholderScreen(title = "Library", message = "Your saved stories will live here.", onBack = {})
    }
}
