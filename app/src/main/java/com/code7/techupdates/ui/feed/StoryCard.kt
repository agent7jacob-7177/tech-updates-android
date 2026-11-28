package com.code7.techupdates.ui.feed

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.code7.techupdates.R
import com.code7.techupdates.domain.Story
import com.code7.techupdates.domain.Topic
import com.code7.techupdates.domain.timeAgo
import com.code7.techupdates.ui.theme.Code7Theme
import com.code7.techupdates.ui.theme.hot

/** Port of JS `card(h, featured)`. */
@Composable
fun StoryCard(
    card: StoryCardUi,
    featured: Boolean,
    nowEpochSec: Long,
    onOpen: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = "${card.story.title} · ${card.domain} · ${card.story.points} points · ${card.story.numComments} comments"
    OutlinedCard(
        onClick = onOpen,
        modifier =
            modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = desc
            },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            CardHeader(card, onToggleLater)
            Column(Modifier.padding(end = 12.dp)) {
                Text(
                    text = card.story.title,
                    style = with(MaterialTheme.typography) { if (featured) headlineSmall else titleMedium },
                    fontWeight = FontWeight.SemiBold,
                )
                if (card.why.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    WhyLine(card.why)
                }
                if (card.excerpt.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = card.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            CardMeta(card.story, card.isHot, nowEpochSec, onOpenComments)
        }
    }
}

/** JS `.why`: "Because you read about X and Y". */
@Composable
private fun WhyLine(why: List<String>) {
    val reasons = why.reduce { joined, next -> stringResource(R.string.card_why_join, joined, next) }
    val color = MaterialTheme.colorScheme.primary
    Row {
        Icon(
            painterResource(R.drawable.ic_spark),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp).size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.card_why, reasons),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun CardHeader(
    card: StoryCardUi,
    onToggleLater: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = card.faviconUrl,
            contentDescription = null,
            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = card.domain,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (card.topic != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = stringResource(card.topic.tagRes),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconToggleButton(checked = card.isSaved, onCheckedChange = { onToggleLater() }) {
            Icon(
                painter = painterResource(if (card.isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
                contentDescription =
                    stringResource(if (card.isSaved) R.string.card_remove_later else R.string.card_read_later),
                tint = with(MaterialTheme.colorScheme) { if (card.isSaved) primary else onSurfaceVariant },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CardMeta(
    story: Story,
    isHot: Boolean,
    nowEpochSec: Long,
    onOpenComments: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val pointsLabel = pluralStringResource(R.plurals.card_points, story.points, story.points)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetaItem(
            iconRes = R.drawable.ic_arrow_up,
            text = story.points.toString(),
            color = if (isHot) MaterialTheme.colorScheme.hot else muted,
            bold = isHot,
            modifier = Modifier.semantics { contentDescription = pointsLabel },
        )
        MetaItem(R.drawable.ic_clock, timeAgo(story.createdAtEpochSec, nowEpochSec), muted)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenComments) {
            MetaItem(
                iconRes = R.drawable.ic_chat,
                text = pluralStringResource(R.plurals.card_comments, story.numComments, story.numComments),
                color = muted,
            )
        }
    }
}

@Composable
private fun MetaItem(
    iconRes: Int,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (bold) FontWeight.SemiBold else null,
        )
    }
}

internal object PreviewData {
    const val NOW = 1_795_822_000L

    val cards =
        listOf(
            StoryCardUi(
                story =
                    Story(
                        "1",
                        "Anthropic releases a new Claude model with longer context",
                        "https://www.anthropic.com/news",
                        "pg",
                        512,
                        230,
                        NOW - 3_600,
                        null,
                    ),
                domain = "anthropic.com",
                topic = Topic.AI,
                excerpt = "",
                isSaved = true,
            ),
            StoryCardUi(
                story =
                    Story(
                        "2",
                        "Ask HN: How do you keep up with Rust compiler changes?",
                        null,
                        "dang",
                        87,
                        1,
                        NOW - 86_400,
                        "<p>I maintain a few crates &amp; keep missing breaking changes.</p>",
                    ),
                domain = StoryCardUi.FALLBACK_DOMAIN,
                topic = Topic.PROGRAMMING,
                excerpt = "I maintain a few crates & keep missing breaking changes.",
                isSaved = false,
            ),
        )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StoryCardPreview() {
    Code7Theme(dynamicColor = false) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StoryCard(PreviewData.cards[0], featured = true, PreviewData.NOW, {}, {}, {})
                StoryCard(PreviewData.cards[1], featured = false, PreviewData.NOW, {}, {}, {})
            }
        }
    }
}
