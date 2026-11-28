package com.code7.techupdates.ui.comments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.code7.techupdates.domain.RichStyle
import com.code7.techupdates.domain.RichText

/**
 * [RichText] as an [AnnotatedString]. Links are [LinkAnnotation.Url]s, opened by the
 * `LocalUriHandler` (Custom Tabs on the comments screen).
 */
fun RichText.toAnnotatedString(
    linkColor: Color,
    codeBackground: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        spans.forEach { span ->
            when (span.style) {
                RichStyle.ITALIC -> {
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                }

                RichStyle.BOLD -> {
                    addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), span.start, span.end)
                }

                RichStyle.CODE -> {
                    addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground),
                        span.start,
                        span.end,
                    )
                }

                RichStyle.LINK -> {
                    span.url?.let { addLink(LinkAnnotation.Url(it, linkStyles), span.start, span.end) }
                }
            }
        }
    }
