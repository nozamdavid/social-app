package am.noz.caelum.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor

/**
 * Renders ATProto rich-text using UTF-8 facet segments and Compose `LinkAnnotation`.
 * Works with TalkBack accessibility actions for every mention, link, and hashtag.
 */
@Composable
fun RichTextView(
    text: String,
    facets: List<RichTextFacet>?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = LocalAlfPalette.current.primary500.toComposeColor(),
    linkDecoration: TextDecoration = TextDecoration.None,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onMentionClick: (didOrHandle: String) -> Unit = {},
    onLinkClick: (displayText: String, uri: String) -> Unit = { _, _ -> },
    onTagClick: (tag: String) -> Unit = {},
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = linkDecoration),
        pressedStyle = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium,
        ),
    )

    val segments = remember(text, facets) { RichTextEngine.segments(text, facets) }
    val annotated = buildAnnotatedString {
        for ((idx, segment) in segments.withIndex()) {
            val mention = segment.mention
            val link = segment.link
            val tag = segment.tag
            when {
                mention != null -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "mention_${idx}_${mention.did}",
                            styles = linkStyles,
                            linkInteractionListener = { onMentionClick(mention.did) },
                        ),
                    ) {
                        append(segment.text)
                    }
                }
                link != null -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "link_${idx}_${link.uri}",
                            styles = linkStyles,
                            linkInteractionListener = { onLinkClick(segment.text, link.uri) },
                        ),
                    ) {
                        append(segment.text)
                    }
                }
                tag != null -> {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "tag_${idx}_${tag.tag}",
                            styles = linkStyles,
                            linkInteractionListener = { onTagClick(tag.tag) },
                        ),
                    ) {
                        append(segment.text)
                    }
                }
                else -> append(segment.text)
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout,
    )
}
