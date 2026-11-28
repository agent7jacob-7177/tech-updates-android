package com.code7.techupdates.ui.foryou

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.code7.techupdates.R
import com.code7.techupdates.recommender.Ranking
import com.code7.techupdates.ui.theme.Code7Theme

/** Port of JS `renderInsights(ready)`. */
@Composable
fun InsightsPanel(
    insights: Insights,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (insights.isReady) LearnedInterests(insights, onReset) else ColdStart(insights)
        }
    }
}

@Composable
private fun ColdStart(insights: Insights) {
    PanelTitle(stringResource(R.string.for_you_teach_title))
    PanelNote(stringResource(R.string.for_you_progress, insights.progress, Ranking.MIN_SIGNALS))
    LinearProgressIndicator(
        progress = { insights.progress.toFloat() / Ranking.MIN_SIGNALS },
        modifier = Modifier.fillMaxWidth(),
    )
    PanelNote(stringResource(R.string.for_you_teach_summary))
}

@Composable
private fun LearnedInterests(
    insights: Insights,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PanelTitle(stringResource(R.string.for_you_interests_title), Modifier.weight(1f))
        TextButton(onClick = onReset) { Text(stringResource(R.string.for_you_reset)) }
    }
    PanelNote(pluralStringResource(R.plurals.for_you_learned_from, insights.positives, insights.positives))
    if (insights.interests.isEmpty()) {
        PanelNote(stringResource(R.string.for_you_still_learning))
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            insights.interests.forEach { InterestChip(it) }
        }
    }
}

@Composable
private fun PanelTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PanelNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InterestChip(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** JS `confirm("Forget what the model has learned? …")`. */
@Composable
internal fun ResetLearningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.for_you_reset_confirm)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.for_you_reset)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.for_you_cancel)) } },
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsightsPanelPreview() {
    Code7Theme(dynamicColor = false) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightsPanel(Insights(isReady = false, positives = 1, interests = emptyList()), onReset = {})
                InsightsPanel(
                    Insights(isReady = true, positives = 12, interests = listOf("AI", "openai.com", "Space")),
                    onReset = {},
                )
            }
        }
    }
}
