package com.code7.techupdates.uitest

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.domain.ThemeMode
import com.code7.techupdates.ui.comments.CommentsActions
import com.code7.techupdates.ui.comments.CommentsPreviewData
import com.code7.techupdates.ui.comments.CommentsScreen
import com.code7.techupdates.ui.feed.FeedActions
import com.code7.techupdates.ui.feed.FeedScreen
import com.code7.techupdates.ui.library.LibraryActions
import com.code7.techupdates.ui.library.LibraryScreen
import com.code7.techupdates.ui.library.LibraryUiState
import com.code7.techupdates.ui.reader.ReaderActions
import com.code7.techupdates.ui.reader.ReaderPreviewData
import com.code7.techupdates.ui.reader.ReaderScreen
import com.code7.techupdates.ui.settings.SettingsActions
import com.code7.techupdates.ui.settings.SettingsScreen
import com.code7.techupdates.ui.settings.SettingsUiState
import com.code7.techupdates.ui.theme.Code7Theme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phone-sized screenshots of the main screens in light and dark. `testDebugUnitTest` compares them
 * with the goldens in `src/test/screenshots`; `recordRoborazziDebug` rewrites the goldens.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun capture(
        name: String,
        theme: ThemeMode,
        readyText: String? = null,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            // Fixed palette: dynamic colour would follow the wallpaper.
            Code7Theme(darkTheme = theme == ThemeMode.DARK, dynamicColor = false, content = content)
        }
        if (readyText != null) compose.awaitText(readyText, substring = true)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/${name}_${theme.id}.png",
            roborazziOptions = OPTIONS,
        )
    }

    private fun feed(theme: ThemeMode) = capture("feed_list", theme) { FeedScreen(UiFixtures.feed, FeedActions()) }

    private fun forYou(theme: ThemeMode) =
        capture("for_you_insights", theme) {
            FeedScreen(UiFixtures.forYouTab, FeedActions(), forYouState = UiFixtures.forYouRanked)
        }

    private fun libraryEmpty(theme: ThemeMode) =
        capture("library_empty", theme) { LibraryScreen(LibraryUiState(isLoading = false), LibraryActions()) }

    private fun reader(theme: ThemeMode) =
        // The Markdown is parsed off the main thread: wait for its last paragraph.
        capture("reader_article", theme, readyText = "announcement") {
            ReaderScreen(ReaderPreviewData.article, ReaderActions())
        }

    private fun comments(theme: ThemeMode) =
        capture("comments", theme) { CommentsScreen(CommentsPreviewData.ready, CommentsActions()) }

    private fun settings(theme: ThemeMode) =
        capture("settings", theme) {
            SettingsScreen(
                SettingsUiState(isLoading = false, themeMode = theme, versionLabel = "v0.1.0 (1)"),
                SettingsActions(),
            )
        }

    @Test fun feedList_light() = feed(ThemeMode.LIGHT)

    @Test fun feedList_dark() = feed(ThemeMode.DARK)

    @Test fun forYouInsights_light() = forYou(ThemeMode.LIGHT)

    @Test fun forYouInsights_dark() = forYou(ThemeMode.DARK)

    @Test fun libraryEmpty_light() = libraryEmpty(ThemeMode.LIGHT)

    @Test fun libraryEmpty_dark() = libraryEmpty(ThemeMode.DARK)

    @Test fun readerArticle_light() = reader(ThemeMode.LIGHT)

    @Test fun readerArticle_dark() = reader(ThemeMode.DARK)

    @Test fun comments_light() = comments(ThemeMode.LIGHT)

    @Test fun comments_dark() = comments(ThemeMode.DARK)

    @Test fun settings_light() = settings(ThemeMode.LIGHT)

    @Test fun settings_dark() = settings(ThemeMode.DARK)

    private companion object {
        /** Up to 0.001% of pixels (about a dozen) may differ, to absorb anti-aliasing differences between CPUs. */
        val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.00001F))
    }
}
