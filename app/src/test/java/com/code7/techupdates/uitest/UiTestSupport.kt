package com.code7.techupdates.uitest

import android.content.Context
import android.provider.Settings
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.graphics.Color as ComposeColor

/** The Robolectric SDK and the phone-sized, en-US screen every UI test renders on. */
object UiTestDevice {
    const val SDK = 35
    const val PHONE = "en-rUS-w393dp-h851dp-normal-long-notround-any-xhdpi-keyshidden-nonav"
}

private object Await {
    const val TIMEOUT_MILLIS = 10_000L
    const val FRAME_STEP_MILLIS = 100L
}

private object Favicon {
    const val SIZE_PX = 32
}

/**
 * Makes rendering independent of the machine: en-US, UTC, no system animations, and a fake Coil
 * engine that draws every image (favicons, article images) as a grey square without the network.
 */
class DeterministicUiRule : ExternalResource() {
    private lateinit var locale: Locale
    private lateinit var timeZone: TimeZone

    @OptIn(DelicateCoilApi::class)
    override fun before() {
        locale = Locale.getDefault()
        timeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine =
            FakeImageLoaderEngine
                .Builder()
                .default(ColorImage(ComposeColor.Gray.toArgb(), width = Favicon.SIZE_PX, height = Favicon.SIZE_PX))
                .build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
    }

    @OptIn(DelicateCoilApi::class)
    override fun after() {
        SingletonImageLoader.reset()
        Locale.setDefault(locale)
        TimeZone.setDefault(timeZone)
    }
}

fun string(
    @StringRes id: Int,
    vararg args: Any,
): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

fun plural(
    @PluralsRes id: Int,
    count: Int,
): String = ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(id, count, count)

/**
 * Waits for work outside Compose's clock (Markdown parsing, Room, Ktor) to show a matching node.
 * Also works with `mainClock.autoAdvance = false`, by moving the clock itself.
 */
fun ComposeTestRule.awaitNode(matcher: SemanticsMatcher) =
    awaitCondition { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.awaitText(
    text: String,
    substring: Boolean = false,
) = awaitNode(hasText(text, substring = substring))

fun ComposeTestRule.awaitDescription(description: String) = awaitNode(hasContentDescription(description))

/** Waits until no node matches, e.g. for a screen transition to remove the previous screen. */
fun ComposeTestRule.awaitNoText(text: String) =
    awaitCondition { onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }

fun ComposeTestRule.awaitCondition(condition: () -> Boolean) =
    waitUntil(Await.TIMEOUT_MILLIS) {
        if (!mainClock.autoAdvance) mainClock.advanceTimeBy(Await.FRAME_STEP_MILLIS)
        condition()
    }

/** Runs the node's custom accessibility action called [label] (what TalkBack offers instead of a swipe). */
fun ComposeTestRule.performCustomAction(
    node: SemanticsNodeInteraction,
    label: String,
) {
    val action = node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
    runOnUiThread { assertTrue(action.action()) }
    waitForIdle()
}
