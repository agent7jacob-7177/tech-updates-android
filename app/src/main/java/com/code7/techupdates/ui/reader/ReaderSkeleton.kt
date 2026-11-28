package com.code7.techupdates.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.code7.techupdates.ui.theme.Code7Theme

private val SkeletonWidths = listOf(0.96f, 1f, 0.88f, 1f, 0.72f, 0f, 1f, 0.94f, 1f, 0.6f)

/** JS `.skel-lines` while the article loads. */
@Composable
internal fun ArticleSkeleton() {
    val transition = rememberInfiniteTransition(label = "readerSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "readerSkeletonAlpha",
    )
    Column(Modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonWidths.forEach { w ->
            if (w == 0f) {
                Spacer(Modifier.height(8.dp))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth(w)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ArticleSkeletonPreview() {
    Code7Theme(dynamicColor = false) {
        Surface { Column(Modifier.padding(16.dp)) { ArticleSkeleton() } }
    }
}
