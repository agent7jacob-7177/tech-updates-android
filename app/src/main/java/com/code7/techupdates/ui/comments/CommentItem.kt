package com.code7.techupdates.ui.comments

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.code7.techupdates.R
import com.code7.techupdates.domain.ThreadNode
import com.code7.techupdates.domain.parseHnHtml
import com.code7.techupdates.domain.timeAgo
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.ui.theme.commentRail

private object Thread {
    /** Deeper replies keep this indentation so text stays readable on phones. */
    const val MAX_VISUAL_DEPTH = 6
}

private val BasePadding = 16.dp
private val Indent = 12.dp
private val RailWidth = 2.dp

/**
 * One comment row (port of JS `commentNode`): author · time, the comment text, and "N replies" when
 * its replies are collapsed. Nesting is shown with one coloured rail per level, capped at
 * [Thread.MAX_VISUAL_DEPTH]. Tapping a comment with replies collapses / expands them.
 */
@Composable
fun CommentItem(
    node: ThreadNode,
    collapsed: Boolean,
    nowEpochSec: Long,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = minOf(node.depth, Thread.MAX_VISUAL_DEPTH)
    val colors = MaterialTheme.colorScheme
    val toggleLabel = stringResource(if (collapsed) R.string.comments_expand else R.string.comments_collapse)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (node.hasReplies) {
                        Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button) { onToggle(node.id) }
                    } else {
                        Modifier
                    },
                ).drawBehind {
                    for (l in 1..level) {
                        val x = (BasePadding + Indent * (l - 1) + RailWidth).toPx()
                        drawLine(colors.commentRail(l), Offset(x, 0f), Offset(x, size.height), RailWidth.toPx())
                    }
                }.padding(start = BasePadding + Indent * level + railGap(level), end = BasePadding)
                .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CommentByline(node, nowEpochSec)
        CommentBody(node.html)
        if (collapsed && node.hasReplies) {
            Text(
                text = pluralStringResource(R.plurals.comments_replies, node.replyCount, node.replyCount),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun railGap(level: Int): Dp = if (level > 0) 4.dp else 0.dp

@Composable
private fun CommentByline(
    node: ThreadNode,
    nowEpochSec: Long,
) {
    val author = node.author ?: stringResource(R.string.comments_deleted)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val byline =
        remember(author, node.createdAtEpochSec, nowEpochSec, onSurface) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = onSurface, fontWeight = FontWeight.SemiBold)) { append(author) }
                if (node.createdAtEpochSec > 0) append(" · " + timeAgo(node.createdAtEpochSec, nowEpochSec))
            }
        }
    Text(byline, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CommentBody(html: String?) {
    if (html == null) {
        Text(
            stringResource(R.string.comments_deleted),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val text =
        remember(html, linkColor, codeBackground) { parseHnHtml(html).toAnnotatedString(linkColor, codeBackground) }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CommentItemPreview() {
    Code7Theme(dynamicColor = false) {
        Surface {
            Column {
                CommentsPreviewData.rows.forEach { node ->
                    CommentItem(node, collapsed = node.id == 3L, nowEpochSec = CommentsPreviewData.NOW, onToggle = {})
                }
            }
        }
    }
}
