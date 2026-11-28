package com.code7.techupdates.ui.comments

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.code7.techupdates.R
import com.code7.techupdates.data.Discussion
import com.code7.techupdates.domain.Comment
import com.code7.techupdates.ui.feed.story
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.uitest.DeterministicUiRule
import com.code7.techupdates.uitest.UiTestDevice
import com.code7.techupdates.uitest.plural
import com.code7.techupdates.uitest.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [UiTestDevice.SDK], qualifiers = UiTestDevice.PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommentsScreenTest {
    @get:Rule(order = 0)
    val deterministic = DeterministicUiRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val source = FakeDiscussions()

    private fun comment(
        id: Long,
        text: String?,
        vararg replies: Comment,
    ) = Comment(id, if (text == null) null else "user$id", text, 500, replies.toList())

    // first -> reply -> nested; a deleted leaf (dropped); a deleted comment kept for its reply.
    private val thread =
        listOf(
            comment(1, "first comment", comment(2, "a reply", comment(3, "nested reply"))),
            comment(4, null),
            comment(5, null, comment(6, "orphaned reply")),
        )

    private fun succeed(comments: List<Comment>) {
        source.result = Result.success(Discussion(story("1", "Discussed story"), comments))
    }

    private fun launch() {
        val viewModel = CommentsViewModel("1", source, nowMillis = { 2_000_000L })
        compose.setContent {
            Code7Theme(dynamicColor = false) { CommentsRoute(onBack = {}, viewModel = viewModel) }
        }
        compose.waitForIdle()
    }

    @Test
    fun nestedComments_render_andDeepRepliesExpand() {
        succeed(thread)
        launch()
        compose.onNodeWithText("Discussed story").assertIsDisplayed()
        compose.onNodeWithText("first comment").assertIsDisplayed()
        compose.onNodeWithText("a reply").assertIsDisplayed()
        // Replies below the first level start collapsed, as on the web.
        compose.onNodeWithText("nested reply").assertDoesNotExist()

        compose.onNodeWithText("a reply").performClick()
        compose.onNodeWithText("nested reply").assertIsDisplayed()
    }

    @Test
    fun tappingComment_collapsesSubtree_withReplyCount() {
        succeed(thread)
        launch()
        compose.onNodeWithText(plural(R.plurals.comments_replies, 2)).assertDoesNotExist()

        compose.onNodeWithText("first comment").performClick()
        compose.onNodeWithText("a reply").assertDoesNotExist()
        compose.onNodeWithText(plural(R.plurals.comments_replies, 2)).assertIsDisplayed()
        compose.onNodeWithText("orphaned reply").assertIsDisplayed()

        compose.onNodeWithText("first comment").performClick()
        compose.onNodeWithText("a reply").assertIsDisplayed()
    }

    @Test
    fun deletedComments_areSkippedUnlessTheyHaveReplies() {
        succeed(thread)
        launch()
        compose.onNodeWithText(plural(R.plurals.comments_summary, 4)).assertIsDisplayed()
        // Only comment 5, which has a reply, is shown as a "[deleted]" placeholder; leaf 4 is dropped.
        compose
            .onAllNodes(hasText(string(R.string.comments_deleted), substring = true))
            .assertCountEquals(1)
        compose.onNodeWithText("orphaned reply").assertIsDisplayed()
    }

    @Test
    fun noComments_showsEmptyText() {
        succeed(listOf(comment(4, null)))
        launch()
        compose.onNodeWithText(string(R.string.comments_empty)).assertIsDisplayed()
    }

    @Test
    fun error_retryLoadsThread() {
        source.result = Result.failure(IOException("offline"))
        launch()
        compose.onNodeWithText(string(R.string.comments_error_title)).assertIsDisplayed()

        succeed(thread)
        compose.onNodeWithText(string(R.string.comments_retry)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("first comment").assertIsDisplayed()
    }
}
