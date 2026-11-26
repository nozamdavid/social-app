package am.noz.caelum.ui.post

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.ui.PlayerView
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.ui.components.LocalAppContainer
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.abs
import kotlin.math.sign

sealed interface LightboxMedia {
    data class Images(val items: List<EmbedImageItem>, val initialIndex: Int) : LightboxMedia
    data class Video(val playlistUrl: String, val thumbUrl: String?, val alt: String?) : LightboxMedia
}

@Composable
fun LightboxOverlay(
    media: LightboxMedia,
    onDismiss: () -> Unit,
) {
    if (media is LightboxMedia.Images && media.items.isEmpty()) return
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var backdropOpacity by remember(media) { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropOpacity }
                    .background(Color.Black),
            )
            when (media) {
                is LightboxMedia.Images -> {
                    val pagerState = rememberPagerState(initialPage = media.initialIndex.coerceIn(media.items.indices)) {
                        media.items.size
                    }
                    var zoomedPage by remember { mutableStateOf<Int?>(null) }
                    var isSharing by remember { mutableStateOf(false) }
                    val savedMessage = tr("Image saved")
                    val saveError = tr("Failed to save image")
                    val shareError = tr("Could not share – please try again")
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = zoomedPage != pagerState.currentPage,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        LightboxImage(
                            item = media.items[page],
                            onZoomChange = { zoomed ->
                                if (zoomed) zoomedPage = page else if (zoomedPage == page) zoomedPage = null
                            },
                            onBackdropChange = { opacity -> backdropOpacity = opacity },
                            onDismiss = onDismiss,
                        )
                    }

                    val currentItem = media.items.getOrNull(pagerState.currentPage)
                    // Top action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(12.dp)
                            .graphicsLayer { alpha = backdropOpacity },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = tr("Close image"), tint = Color.White)
                        }
                        Row {
                            if (currentItem != null) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val ok = saveImageToGallery(context, container.okHttpClient, currentItem.fullsize)
                                        Toast.makeText(
                                            context,
                                            if (ok) savedMessage else saveError,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }) {
                                    Icon(Icons.Filled.Download, contentDescription = tr("Save image"), tint = Color.White)
                                }
                                IconButton(
                                    enabled = !isSharing,
                                    onClick = {
                                        isSharing = true
                                        scope.launch {
                                            try {
                                                val shared = shareImage(context, container.okHttpClient, currentItem.fullsize)
                                                if (!shared) Toast.makeText(context, shareError, Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isSharing = false
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = tr("Share image"), tint = Color.White)
                                }
                            }
                        }
                    }

                    if (currentItem != null && currentItem.alt.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .graphicsLayer { alpha = backdropOpacity }
                                .background(Color.Black.copy(alpha = 0.72f))
                                .navigationBarsPadding()
                                .padding(14.dp)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(currentItem.alt, color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is LightboxMedia.Video -> {
                    val pool = container.videoPlayerPool
                    val player = remember(media.playlistUrl) {
                        pool.acquirePlayer(media.playlistUrl, loop = true, autoPlay = true)
                    }
                    DisposableEffect(media.playlistUrl) {
                        onDispose { pool.pauseActive() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Close"), tint = Color.White)
                    }
                }
            }
        }
    }
}

private enum class ImageGesture { Pending, Transform, Dismiss }

@Composable
private fun LightboxImage(
    item: EmbedImageItem,
    onZoomChange: (Boolean) -> Unit,
    onBackdropChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val notifyZoom by rememberUpdatedState(onZoomChange)
    val updateBackdrop by rememberUpdatedState(onBackdropChange)
    val dismiss by rememberUpdatedState(onDismiss)
    var scale by remember(item) { mutableFloatStateOf(1f) }
    var offset by remember(item) { mutableStateOf(Offset.Zero) }
    var dismissY by remember(item) { mutableFloatStateOf(0f) }
    var settleJob by remember(item) { mutableStateOf<Job?>(null) }
    var isClosing by remember(item) { mutableStateOf(false) }

    DisposableEffect(item) {
        onDispose {
            if (scale > 1f) notifyZoom(false)
            if (dismissY != 0f) updateBackdrop(1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item) {
                detectTapGestures(onDoubleTap = {
                    if (!isClosing) {
                        scale = if (scale > 1f) 1f else 2.2f
                        offset = Offset.Zero
                        notifyZoom(scale > 1f)
                    }
                })
            }
            .pointerInput(item) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isClosing) return@awaitEachGesture
                    settleJob?.cancel()
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    val fadeDistance = (size.height / 2f).coerceAtLeast(1f)
                    fun updateDismiss(y: Float) {
                        dismissY = y
                        updateBackdrop(1f - (abs(y) / fadeDistance).coerceIn(0f, 1f))
                    }
                    var totalPan = Offset.Zero
                    var mode = if (scale > 1f) ImageGesture.Transform else ImageGesture.Pending
                    var released = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed && it.position != it.previousPosition }) break
                        event.changes.firstOrNull { it.id == down.id }?.let {
                            velocity.addPosition(it.uptimeMillis, it.position)
                        }
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) {
                            released = true
                            break
                        }

                        val pan = event.calculatePan()
                        if (pressed > 1 && mode != ImageGesture.Transform) {
                            updateDismiss(0f)
                            mode = ImageGesture.Transform
                        }
                        if (mode == ImageGesture.Pending) {
                            totalPan += pan
                            if (totalPan.getDistance() < viewConfiguration.touchSlop) continue
                            // Leave horizontal gestures to the image pager.
                            if (abs(totalPan.x) >= abs(totalPan.y)) break
                            mode = ImageGesture.Dismiss
                        }
                        when (mode) {
                            ImageGesture.Transform -> {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                                notifyZoom(scale > 1f)
                            }
                            ImageGesture.Dismiss -> updateDismiss(dismissY + pan.y)
                            ImageGesture.Pending -> Unit
                        }
                        event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                    }
                    if (dismissY != 0f) {
                        val releaseSpeed = velocity.calculateVelocity().y
                        val fling = abs(dismissY) > 24.dp.toPx() && releaseSpeed * sign(dismissY) > 500.dp.toPx()
                        isClosing = released && (abs(dismissY) > 100.dp.toPx() || fling)
                        val target = if (isClosing) size.height * sign(dismissY) else 0f
                        settleJob = scope.launch {
                            animate(dismissY, target, animationSpec = tween(170)) { value, _ -> updateDismiss(value) }
                            if (isClosing) dismiss()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.fullsize,
            contentDescription = item.alt.ifBlank { tr("Image") },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y + dismissY
                },
        )
    }
}

private suspend fun shareImage(context: Context, client: OkHttpClient, url: String): Boolean {
    val attachment = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "lightbox-share").apply { mkdirs() }
            val staleBefore = System.currentTimeMillis() - 48L * 60 * 60 * 1000
            directory.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach { it.delete() }
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Image download failed" }
                val body = checkNotNull(response.body)
                val type = body.contentType()?.takeIf { it.type == "image" }?.let { "image/${it.subtype}" }
                val extension = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "img"
                val file = File.createTempFile("caelum-", ".$extension", directory)
                try {
                    file.outputStream().use { body.byteStream().copyTo(it) }
                    check(file.length() > 0) { "Empty image" }
                    val mimeType = type ?: BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                        BitmapFactory.decodeFile(file.path, this)
                    }.outMimeType
                    checkNotNull(mimeType) { "Unknown image type" }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to mimeType
                } catch (failure: Exception) {
                    file.delete()
                    throw failure
                }
            }
        }.getOrNull()
    } ?: return false
    val (uri, mimeType) = attachment
    return runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

private suspend fun saveImageToGallery(
    context: android.content.Context,
    okHttpClient: okhttp3.OkHttpClient,
    url: String,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val (bytes, headerMime) = okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("Empty body")
            val mime = body.contentType()?.takeIf { it.type == "image" }?.let { "image/${it.subtype}" }
            body.bytes() to mime
        }
        check(bytes.isNotEmpty()) { "Empty image" }
        val mimeType = headerMime ?: BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        }.outMimeType ?: "image/jpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        val filename = "caelum_${System.currentTimeMillis()}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Caelum")
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: error("OutputStream null")
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
        true
    }.getOrDefault(false)
}
