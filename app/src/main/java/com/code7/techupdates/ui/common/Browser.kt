package com.code7.techupdates.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import com.code7.techupdates.domain.safeUrl

/** Opens [url] in a Custom Tab, falling back to any app that can view it. Non-http(s) URLs are ignored. */
fun Context.openInBrowser(url: String) {
    val uri = safeUrl(url)?.toUri() ?: return
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No browser at all: nothing sensible left to do.
        }
    }
}

/** Opens [text] (and [subject]) in the system share sheet titled [chooserTitle]. */
fun Context.shareText(
    subject: String,
    text: String,
    chooserTitle: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
    try {
        startActivity(Intent.createChooser(send, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        // No share targets.
    }
}

/** A [UriHandler] that opens links (Markdown / HTML text) in Custom Tabs. */
@Composable
fun rememberCustomTabsUriHandler(): UriHandler {
    val context = LocalContext.current
    return remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) = context.openInBrowser(uri)
        }
    }
}
