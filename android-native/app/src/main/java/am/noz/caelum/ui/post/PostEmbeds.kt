package am.noz.caelum.ui.post

import am.noz.caelum.core.i18n.LocalI18n
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.Label
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.moderation.ModerationCause
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.moderation.ModerationUI
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

data class EmbedImageItem(
    val thumb: String,
    val fullsize: String,
    val alt: String = "",
    val aspectRatio: Float? = null,
)

@Composable
fun PostEmbeds(
    embed: JsonObject?,
    mediaModeration: ModerationUI,
    blurReason: String?,
    largeAltBadge: Boolean,
    disableAutoplay: Boolean,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (playlistUrl: String, thumbUrl: String?, alt: String?) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    moderationOpts: ModerationOpts = ModerationOpts(),
    expanded: Boolean = false,
    quoteDepth: Int = 0,
) {
    if (embed == null) return
    val type = (embed["\$type"] as? JsonPrimitive)?.content.orEmpty()

    @Composable
    fun Quote(record: JsonObject?) {
        if (record != null && quoteDepth < 2) {
            QuotedRecordCard(
                record = record,
                onNavigate = onNavigate,
                moderationOpts = moderationOpts,
                largeAltBadge = largeAltBadge,
                disableAutoplay = disableAutoplay,
                expanded = expanded,
                quoteDepth = quoteDepth,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
                onOpenExternalLink = onOpenExternalLink,
            )
        }
    }

    key(embed) {
        when (type) {
            "app.bsky.embed.images#view",
            "app.bsky.embed.gallery#view",
            -> {
                val images = remember(embed) { parseImages((embed["images"] ?: embed["items"]) as? JsonArray) }
                MediaShieldWrapper(moderation = mediaModeration, reason = blurReason) {
                    PostImagesEmbed(
                        images = images,
                        largeAltBadge = largeAltBadge,
                        expanded = expanded,
                        onImageClick = { index -> onOpenLightbox(images, index) },
                    )
                }
            }
            "app.bsky.embed.video#view" -> {
                MediaShieldWrapper(moderation = mediaModeration, reason = blurReason) {
                    PostVideoEmbed(
                        videoObj = embed,
                        largeAltBadge = largeAltBadge,
                        disableAutoplay = disableAutoplay,
                        onFullscreen = { url, thumb, alt -> onOpenVideoLightbox(url, thumb, alt) },
                    )
                }
            }
            "app.bsky.embed.external#view" -> {
                val ext = (embed["external"] as? JsonObject) ?: return
                MediaShieldWrapper(moderation = mediaModeration, reason = blurReason) {
                    PostExternalEmbed(
                        external = ext,
                        disableAutoplay = disableAutoplay,
                        largeAltBadge = largeAltBadge,
                        onOpenExternalLink = onOpenExternalLink,
                    )
                }
            }
            "app.bsky.embed.record#view" -> {
                Quote(embed["record"] as? JsonObject)
            }
            "app.bsky.embed.recordWithMedia#view" -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PostEmbeds(
                        embed = embed["media"] as? JsonObject,
                        mediaModeration = mediaModeration,
                        blurReason = blurReason,
                        largeAltBadge = largeAltBadge,
                        disableAutoplay = disableAutoplay,
                        onOpenLightbox = onOpenLightbox,
                        onOpenVideoLightbox = onOpenVideoLightbox,
                        onNavigate = onNavigate,
                        onOpenExternalLink = onOpenExternalLink,
                        moderationOpts = moderationOpts,
                        expanded = expanded,
                        quoteDepth = quoteDepth,
                    )
                    Quote((embed["record"] as? JsonObject)?.get("record") as? JsonObject)
                }
            }
        }
    }
}

@Composable
private fun MediaShieldWrapper(
    moderation: ModerationUI,
    reason: String?,
    placeholderHeight: Dp = 140.dp,
    content: @Composable () -> Unit,
) {
    val blurMedia = moderation.blur
    val canReveal = !moderation.noOverride
    var revealed by remember(reason, blurMedia, canReveal) { mutableStateOf(false) }
    val palette = LocalAlfPalette.current
    if (!blurMedia || (canReveal && revealed)) {
        Box {
            content()
            if (blurMedia) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { revealed = false }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(tr("Hide"), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = placeholderHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.contrast50.toComposeColor())
                .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = palette.contrast600.toComposeColor())
                Text(
                    text = reason ?: tr("Hidden by your moderation settings."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast700.toComposeColor(),
                )
                if (canReveal) {
                    Button(
                        onClick = { revealed = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.contrast100.toComposeColor(),
                            contentColor = palette.contrast900.toComposeColor(),
                        ),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Show"))
                    }
                }
            }
        }
    }
}

@Composable
private fun PostImagesEmbed(
    images: List<EmbedImageItem>,
    largeAltBadge: Boolean,
    expanded: Boolean,
    onImageClick: (Int) -> Unit,
) {
    if (images.isEmpty()) return
    val palette = LocalAlfPalette.current
    var altDialogText by remember { mutableStateOf<String?>(null) }

    @Composable
    fun ImageTile(
        item: EmbedImageItem,
        index: Int,
        modifier: Modifier,
        contentScale: ContentScale = ContentScale.Crop,
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(palette.contrast50.toComposeColor())
                .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { onImageClick(index) },
        ) {
            AsyncImage(
                model = item.thumb,
                contentDescription = item.alt.ifBlank { tr("View photo") },
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.alt.isNotBlank()) {
                val showAltText = tr("Show alt text")
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(role = Role.Button) { altDialogText = item.alt }
                        .semantics { contentDescription = showAltText }
                        .padding(
                            horizontal = if (largeAltBadge) 8.dp else 5.dp,
                            vertical = if (largeAltBadge) 4.dp else 2.dp,
                        ),
                ) {
                    Text(
                        text = tr("ALT"),
                        color = Color.White,
                        style = if (largeAltBadge) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    when (images.size) {
        1 -> {
            val item = images[0]
            val ratio = (item.aspectRatio ?: 1f).coerceAtLeast(if (expanded) 0.25f else 0.5f)
            // Feed portraits fit inside a 9:16 box. The opened post uses the full column.
            val widthFraction = if (expanded) 1f else (ratio * 16f / 9f).coerceAtMost(1f)
            ImageTile(
                item = item,
                index = 0,
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .aspectRatio(ratio),
                contentScale = if (item.aspectRatio == null) ContentScale.Fit else ContentScale.Crop,
            )
        }
        2 -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ImageTile(images[0], 0, Modifier.weight(1f).aspectRatio(1f))
                ImageTile(images[1], 1, Modifier.weight(1f).aspectRatio(1f))
            }
        }
        3 -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ImageTile(images[0], 0, Modifier.weight(1f).aspectRatio(1f))
                Column(modifier = Modifier.weight(1f).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImageTile(images[1], 1, Modifier.weight(1f).fillMaxWidth())
                    ImageTile(images[2], 2, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        4 -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImageTile(images[0], 0, Modifier.weight(1f).aspectRatio(1.5f))
                    ImageTile(images[1], 1, Modifier.weight(1f).aspectRatio(1.5f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImageTile(images[2], 2, Modifier.weight(1f).aspectRatio(1.5f))
                    ImageTile(images[3], 3, Modifier.weight(1f).aspectRatio(1.5f))
                }
            }
        }
        else -> {
            // Caelum/Bluesky 5-10 multi-image horizontal strip
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(images) { index, item ->
                    ImageTile(
                        item = item,
                        index = index,
                        modifier = Modifier
                            .width(200.dp)
                            .height(220.dp),
                    )
                }
            }
        }
    }

    if (altDialogText != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { altDialogText = null },
            title = { Text(tr("Alt text")) },
            text = { Text(altDialogText.orEmpty()) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { altDialogText = null }) {
                    Text(tr("Close"))
                }
            },
        )
    }
}

@Composable
private fun PostVideoEmbed(
    videoObj: JsonObject,
    largeAltBadge: Boolean,
    disableAutoplay: Boolean,
    onFullscreen: (playlistUrl: String, thumbUrl: String?, alt: String?) -> Unit,
) {
    val playlist = (videoObj["playlist"] as? JsonPrimitive)?.content ?: return
    val thumbnail = (videoObj["thumbnail"] as? JsonPrimitive)?.content
    val alt = (videoObj["alt"] as? JsonPrimitive)?.content
    val ratioObj = videoObj["aspectRatio"] as? JsonObject
    val w = ratioObj?.get("width")?.jsonPrimitive?.intOrNull ?: 16
    val h = ratioObj?.get("height")?.jsonPrimitive?.intOrNull ?: 9
    val aspect = (w.toFloat() / h.coerceAtLeast(1)).coerceIn(0.6f, 2.2f)

    val container = LocalAppContainer.current
    val pool = container.videoPlayerPool
    val activeUri by pool.activeUri.collectAsState()
    val muted by pool.muted.collectAsState()
    val isActive = activeUri == playlist

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable {
                if (!isActive) {
                    pool.acquirePlayer(playlist, loop = true, autoPlay = true)
                } else {
                    onFullscreen(playlist, thumbnail, alt)
                }
            },
    ) {
        if (isActive) {
            val player = remember(playlist) {
                pool.acquirePlayer(playlist, loop = true, autoPlay = !disableAutoplay)
            }
            DisposableEffect(playlist) {
                onDispose {
                    pool.releasePlayerIfUrl(playlist)
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        this.player = player
                    }
                },
                update = { pv -> pv.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = thumbnail,
                contentDescription = alt?.takeIf { it.isNotBlank() },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = tr("Play video"), tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }

        // Mute & fullscreen controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = {
                    if (!isActive) {
                        pool.acquirePlayer(playlist, loop = true, autoPlay = true)
                    }
                    pool.toggleMuted()
                },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
            ) {
                Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) tr("Unmute video") else tr("Mute"),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = { onFullscreen(playlist, thumbnail, alt) },
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = tr("Enter fullscreen"), tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        if (!alt.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = if (largeAltBadge) 8.dp else 5.dp, vertical = 2.dp),
            ) {
                Text(tr("ALT"), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PostExternalEmbed(
    external: JsonObject,
    disableAutoplay: Boolean,
    largeAltBadge: Boolean,
    onOpenExternalLink: (String) -> Unit,
) {
    val uri = (external["uri"] as? JsonPrimitive)?.content ?: return
    val title = (external["title"] as? JsonPrimitive)?.content.orEmpty()
    val desc = (external["description"] as? JsonPrimitive)?.content.orEmpty()
    val thumb = (external["thumb"] as? JsonPrimitive)?.content
    val gif = remember(uri) { parsePostGif(uri) }
    if (gif != null) {
        PostGifEmbed(
            gif = gif,
            title = title,
            description = desc,
            thumbnail = thumb,
            disableAutoplay = disableAutoplay,
            largeAltBadge = largeAltBadge,
        )
        return
    }

    val palette = LocalAlfPalette.current
    val domain = remember(uri) { runCatching { URI(uri).host?.removePrefix("www.") }.getOrNull().orEmpty() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.contrast25.toComposeColor())
            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
            .clickable { onOpenExternalLink(uri) },
    ) {
        if (!thumb.isNullOrBlank()) {
            AsyncImage(
                model = thumb,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )
        }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(13.dp), tint = palette.contrast500.toComposeColor())
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.contrast600.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast700.toComposeColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class PostGif(val source: String, val aspectRatio: Float)

internal fun parsePostGif(uri: String): PostGif? {
    val original = uri.toHttpUrlOrNull() ?: return null
    if (original.username.isNotEmpty() || original.password.isNotEmpty()) return null
    val builder = HttpUrl.Builder().scheme("https")
    when (original.host) {
        "media.tenor.com" -> {
            val (id, filename) = original.encodedPathSegments.takeIf { it.size == 2 } ?: return null
            if ("AAAAC" !in id || !filename.endsWith(".gif", ignoreCase = true)) return null
            builder.host("t.gifs.bsky.app").addEncodedPathSegment(id.replace("AAAAC", "AAAAM"))
                .addEncodedPathSegment(filename)
        }
        "static.klipy.com" -> {
            if (!original.encodedPath.startsWith("/ii/") || !original.encodedPath.endsWith(".gif", ignoreCase = true)) {
                return null
            }
            builder.host("k.gifs.bsky.app").encodedPath(original.encodedPath).encodedQuery(original.encodedQuery)
            for (param in listOf("hh", "ww", "mp4", "webm")) builder.removeAllQueryParameters(param)
        }
        else -> return null
    }
    val width = original.queryParameter("ww")?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
    val height = original.queryParameter("hh")?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f } ?: return null
    val aspectRatio = (width / height).takeIf { it.isFinite() && it > 0f } ?: 1f
    return PostGif(builder.build().toString(), aspectRatio.coerceAtLeast(0.5f))
}

@Composable
private fun PostGifEmbed(
    gif: PostGif,
    title: String,
    description: String,
    thumbnail: String?,
    disableAutoplay: Boolean,
    largeAltBadge: Boolean,
) {
    val context = LocalContext.current
    val imageLoader = LocalAppContainer.current.imageLoader
    val palette = LocalAlfPalette.current
    var playing by remember(gif, disableAutoplay) { mutableStateOf(!disableAutoplay) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var drawable by remember(gif) { mutableStateOf<Drawable?>(null) }
    var failed by remember(gif) { mutableStateOf(false) }
    var retry by remember(gif) { mutableStateOf(0) }
    var showAlt by remember(gif) { mutableStateOf(false) }
    val alt = description.removePrefix("Alt: ").removePrefix("ALT: ").ifBlank { title }.ifBlank { tr("Animated GIF") }
    val actionLabel = when {
        failed -> tr("Try again")
        playing -> tr("Pause GIF")
        else -> tr("Play GIF")
    }

    LaunchedEffect(gif, frameSize, retry) {
        if (frameSize.width <= 0 || frameSize.height <= 0) return@LaunchedEffect
        failed = false
        val request = ImageRequest.Builder(context)
            .data(gif.source)
            .size(frameSize.width, frameSize.height)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = imageLoader.execute(request) as? SuccessResult
        drawable = result?.image?.asDrawable(context.resources)
        failed = drawable == null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth((gif.aspectRatio * 16f / 9f).coerceAtMost(1f))
            .aspectRatio(gif.aspectRatio)
            .onSizeChanged { frameSize = it }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable(onClickLabel = actionLabel, role = Role.Button) {
                if (failed) retry++ else playing = !playing
            }
            .semantics { contentDescription = alt },
    ) {
        val frame = drawable
        if (frame != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                },
                onReset = null,
                onRelease = { view -> (view.drawable as? Animatable)?.stop() },
                update = { view ->
                    if (view.drawable !== frame) {
                        (view.drawable as? Animatable)?.stop()
                        view.setImageDrawable(frame)
                    }
                    (frame as? Animatable)?.let { if (playing) it.start() else it.stop() }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!playing || failed) {
            Box(
                Modifier.align(Alignment.Center).size(52.dp).background(Color.Black.copy(alpha = 0.68f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        } else if (frame == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White)
        }
        val badgeBackground = palette.contrast25.toComposeColor().copy(alpha = 0.8f)
        val badgeModifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeBackground)
        val badgePadding = Modifier.padding(horizontal = if (largeAltBadge) 6.dp else 4.dp, vertical = 3.dp)
        val badgeStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(tr("GIF"), badgeModifier.then(badgePadding), color = palette.contrast1000.toComposeColor(), style = badgeStyle)
            if (description.startsWith("Alt: ") && description.length > 5) {
                val showLabel = tr("Show alt text")
                Text(
                    tr("ALT"),
                    badgeModifier
                        .clickable(onClickLabel = showLabel, role = Role.Button) { showAlt = true }
                        .then(badgePadding),
                    color = palette.contrast1000.toComposeColor(),
                    style = badgeStyle,
                )
            }
        }
    }
    if (showAlt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAlt = false },
            title = { Text(tr("Alt text")) },
            text = { Text(alt) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showAlt = false }) { Text(tr("Close")) }
            },
        )
    }
}

@Composable
fun QuotedRecordCard(
    record: JsonObject,
    onNavigate: (AppRoute) -> Unit,
    moderationOpts: ModerationOpts = ModerationOpts(),
    largeAltBadge: Boolean = false,
    disableAutoplay: Boolean = true,
    expanded: Boolean = false,
    quoteDepth: Int = 0,
    onOpenLightbox: ((List<EmbedImageItem>, Int) -> Unit)? = null,
    onOpenVideoLightbox: ((String, String?, String?) -> Unit)? = null,
    onOpenExternalLink: ((String) -> Unit)? = null,
) {
    val palette = LocalAlfPalette.current
    val type = (record["\$type"] as? JsonPrimitive)?.content.orEmpty()

    when (type) {
        "app.bsky.embed.record#viewRecord" -> {
            val quote = remember(record) { record.asQuotedPost() } ?: return
            val moderation = remember(quote, moderationOpts) {
                ModerationEngine.moderatePost(quote, moderationOpts)
            }
            val listUi = remember(moderation) { moderation.ui(ModerationContext.contentList) }
            val viewUi = remember(moderation) { moderation.ui(ModerationContext.contentView) }
            val mediaUi = remember(moderation) { moderation.ui(ModerationContext.contentMedia) }
            val avatarUi = remember(moderation) { moderation.ui(ModerationContext.avatar) }
            val nameUi = remember(moderation) { moderation.ui(ModerationContext.displayName) }
            val text = (quote.record["text"] as? JsonPrimitive)?.content.orEmpty()
            val author = quote.author
            val openQuote = {
                onNavigate(AppRoute.PostThread(author.handle.ifBlank { author.did }, quote.uri.substringAfterLast('/')))
            }

            MediaShieldWrapper(
                moderation = listUi,
                reason = quoteWarning(listUi.blurs.firstOrNull()),
                placeholderHeight = 116.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = openQuote)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CaelumAvatar(
                            url = author.avatar.takeUnless { avatarUi.blur },
                            contentDescription = null,
                            size = 16.dp,
                            shapeType = if (author.associated?.labeler == true) AvatarShape.RoundedSquare else AvatarShape.Circle,
                        )
                        DisplayNameRow(
                            profile = if (nameUi.blur) author.copy(displayName = null) else author,
                            timestamp = LocalI18n.current.formatRelativeShort(quote.indexedAt),
                            showHandle = !nameUi.blur && !author.displayName.isNullOrBlank(),
                            badgeSize = 12.dp,
                        )
                    }
                    quoteWarning(viewUi.alerts.firstOrNull() ?: viewUi.informs.firstOrNull())?.let { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = palette.contrast700.toComposeColor())
                    }
                    if (text.isNotBlank()) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast950.toComposeColor(),
                            maxLines = 20,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PostEmbeds(
                        embed = quote.embed,
                        mediaModeration = mediaUi,
                        blurReason = quoteWarning(mediaUi.blurs.firstOrNull()),
                        largeAltBadge = largeAltBadge,
                        disableAutoplay = disableAutoplay,
                        onOpenLightbox = onOpenLightbox ?: { _, _ -> openQuote() },
                        onOpenVideoLightbox = onOpenVideoLightbox ?: { _, _, _ -> openQuote() },
                        onNavigate = onNavigate,
                        onOpenExternalLink = onOpenExternalLink ?: { openQuote() },
                        moderationOpts = moderationOpts,
                        expanded = expanded,
                        quoteDepth = quoteDepth + 1,
                    )
                }
            }
        }
        "app.bsky.embed.record#viewBlocked",
        "app.bsky.embed.record#viewNotFound",
        "app.bsky.embed.record#viewDetached",
        -> {
            val msg = when (type) {
                "app.bsky.embed.record#viewBlocked" -> tr("Post blocked")
                "app.bsky.embed.record#viewDetached" -> tr("Removed by author")
                else -> tr("Post has been deleted")
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.contrast25.toComposeColor())
                    .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
            }
        }
        "app.bsky.feed.defs#generatorView" -> {
            val uri = (record["uri"] as? JsonPrimitive)?.content.orEmpty()
            val name = (record["displayName"] as? JsonPrimitive)?.content ?: tr("Feed")
            val avatar = (record["avatar"] as? JsonPrimitive)?.content
            val creatorHandle = (record["creator"] as? JsonObject)?.get("handle")?.jsonPrimitive?.content.orEmpty()
            val repoDid = uri.removePrefix("at://").substringBefore('/')
            val rkey = uri.substringAfterLast('/')
            CardStubRow(
                avatarUrl = avatar,
                title = name,
                subtitle = tr("Feed by {handle}", mapOf("handle" to "@$creatorHandle")),
                onClick = { onNavigate(AppRoute.CustomFeed(repoDid, rkey)) },
            )
        }
        "app.bsky.graph.defs#listView" -> {
            val uri = (record["uri"] as? JsonPrimitive)?.content.orEmpty()
            val name = (record["name"] as? JsonPrimitive)?.content ?: tr("List")
            val avatar = (record["avatar"] as? JsonPrimitive)?.content
            val repoDid = uri.removePrefix("at://").substringBefore('/')
            val rkey = uri.substringAfterLast('/')
            CardStubRow(
                avatarUrl = avatar,
                title = name,
                subtitle = tr("List"),
                onClick = { onNavigate(AppRoute.ProfileList(repoDid, rkey)) },
            )
        }
        "app.bsky.graph.defs#starterPackViewBasic" -> {
            val uri = (record["uri"] as? JsonPrimitive)?.content.orEmpty()
            val packName = ((record["record"] as? JsonObject)?.get("name") as? JsonPrimitive)?.content ?: tr("Starter Pack")
            val creatorHandle = (record["creator"] as? JsonObject)?.get("handle")?.jsonPrimitive?.content.orEmpty()
            val repoDid = uri.removePrefix("at://").substringBefore('/')
            val rkey = uri.substringAfterLast('/')
            CardStubRow(
                avatarUrl = null,
                title = packName,
                subtitle = tr("Starter pack by {handle}", mapOf("handle" to "@$creatorHandle")),
                onClick = { onNavigate(AppRoute.StarterPack(repoDid, rkey)) },
            )
        }
    }
}

internal fun JsonObject.asQuotedPost(): PostView? {
    val author = (get("author") as? JsonObject)?.let {
        runCatching { AtProtoJson.decodeFromJsonElement<ProfileViewBasic>(it) }.getOrNull()
    } ?: return null
    val uri = (get("uri") as? JsonPrimitive)?.content ?: return null
    val value = get("value") as? JsonObject ?: return null
    return PostView(
        uri = uri,
        cid = (get("cid") as? JsonPrimitive)?.content.orEmpty(),
        author = author,
        record = value,
        embed = (get("embeds") as? JsonArray)?.firstOrNull() as? JsonObject,
        indexedAt = (get("indexedAt") as? JsonPrimitive)?.content.orEmpty(),
        labels = (get("labels") as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<Label>(it) }.getOrNull()
        }.orEmpty(),
    )
}

@Composable
private fun quoteWarning(cause: ModerationCause?): String? = when (cause) {
    is ModerationCause.Blocking -> tr("User Blocked")
    is ModerationCause.BlockedBy -> tr("User Blocking You")
    is ModerationCause.BlockOther -> tr("Post blocked")
    is ModerationCause.Muted -> tr("Account Muted")
    is ModerationCause.MuteWord -> tr("Post Hidden by Muted Word")
    is ModerationCause.Hidden -> tr("Post Hidden by You")
    is ModerationCause.LabelCause -> cause.labelDef.title
    null -> null
}

@Composable
private fun CardStubRow(
    avatarUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.contrast25.toComposeColor())
            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CaelumAvatar(url = avatarUrl, contentDescription = title, size = 40.dp, shapeType = AvatarShape.RoundedSquare)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun parseImages(arr: JsonArray?): List<EmbedImageItem> {
    if (arr == null) return emptyList()
    return arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val thumb = ((obj["thumb"] ?: obj["thumbnail"]) as? JsonPrimitive)?.content ?: return@mapNotNull null
        val full = (obj["fullsize"] as? JsonPrimitive)?.content ?: thumb
        val alt = (obj["alt"] as? JsonPrimitive)?.content.orEmpty()
        val ratioObj = obj["aspectRatio"] as? JsonObject
        val w = (ratioObj?.get("width") as? JsonPrimitive)?.intOrNull
        val h = (ratioObj?.get("height") as? JsonPrimitive)?.intOrNull
        EmbedImageItem(
            thumb = thumb,
            fullsize = full,
            alt = alt,
            aspectRatio = if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h else null,
        )
    }
}
