package am.noz.caelum.ui.composer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import am.noz.caelum.core.i18n.I18nCatalog
import am.noz.caelum.core.media.ImageCompressor
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BlobRef
import am.noz.caelum.di.AppContainer
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

private const val MAX_VIDEO_BYTES = 300_000_000L
private const val MAX_VIDEO_DURATION_MS = 600_000L
private const val MAX_GIF_THUMBNAIL_BYTES = 1_000_000L
private const val VIDEO_FORMAT_ERROR = "The selected video uses an unsupported format."
private const val VIDEO_DURATION_ERROR = "The selected video is too long."
private const val VIDEO_PROCESS_ERROR = "The selected video could not be processed."
private const val VIDEO_SIZE_ERROR = "The selected video is larger than {VIDEO_MAX_SIZE_MB} MB. Please try again with a smaller file."
private val VIDEO_SIZE_ARGS = mapOf("VIDEO_MAX_SIZE_MB" to MAX_VIDEO_BYTES / 1_000_000)
private const val GIF_SERVICE_ORIGIN = "https://gifs.bsky.app"
private const val GIF_SERVICE_CLIENT_KEY = "bluesky-android"
private val gifProviderProxies = mapOf(
    "static.klipy.com" to "k.gifs.bsky.app",
    "media.tenor.com" to "t.gifs.bsky.app",
)
private val gifSlugPattern = Regex("[A-Za-z0-9_-]+")

internal suspend fun readComposerVideo(
    context: Context,
    uri: Uri,
    i18n: I18nCatalog,
): ComposerAttachment.Video = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val formatMessage = i18n.translate(VIDEO_FORMAT_ERROR)
    resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        require(descriptor.length != 0L) { formatMessage }
        require(descriptor.length <= MAX_VIDEO_BYTES) { i18n.translate(VIDEO_SIZE_ERROR, VIDEO_SIZE_ARGS) }
    }
    val metadata = MediaMetadataRetriever()
    try {
        try {
            metadata.setDataSource(context, uri)
        } catch (error: RuntimeException) {
            throw IOException(formatMessage, error)
        }
        val extension = uri.lastPathSegment?.substringAfterLast('.')?.lowercase(Locale.ROOT)
        val mimeType = sequenceOf(
            resolver.getType(uri)?.lowercase(Locale.ROOT),
            composerVideoExtensions.entries.firstOrNull { it.value == extension }?.key,
            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
        ).firstOrNull { it in composerVideoExtensions.keys } ?: throw IOException(formatMessage)

        val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        require(duration != null && duration > 0) { formatMessage }
        require(duration <= MAX_VIDEO_DURATION_MS) { i18n.translate(VIDEO_DURATION_ERROR) }
        val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        require(width > 0 && height > 0) { formatMessage }
        val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        val rotated = rotation == 90 || rotation == 270
        ComposerAttachment.Video(
            uri = uri.toString(),
            width = if (rotated) height else width,
            height = if (rotated) width else height,
            mimeType = mimeType,
        )
    } finally {
        metadata.release()
    }
}

internal suspend fun AppContainer.createMediaEmbed(
    attachments: List<ComposerAttachment>,
    did: String,
    pdsOrigin: String,
): JsonObject? {
    return when (val first = attachments.firstOrNull()) {
        null -> null
        is ComposerAttachment.Image -> {
            val selectedImages = attachments.filterIsInstance<ComposerAttachment.Image>()
            check(selectedImages.size == attachments.size && selectedImages.size <= 4)
            val images = selectedImages.map { image ->
                val compressed = ImageCompressor.compressUri(context, Uri.parse(image.uri))
                val blob = try {
                    api.uploadBlob(withContext(Dispatchers.IO) { compressed.file.readBytes() }, compressed.mimeType)
                } finally {
                    compressed.file.delete()
                }
                buildJsonObject {
                    put("image", AtProtoJson.encodeToJsonElement(blob))
                    put("alt", image.altText)
                    put("aspectRatio", aspectRatio(compressed.width, compressed.height))
                }
            }
            buildJsonObject {
                put("\$type", "app.bsky.embed.images")
                put("images", buildJsonArray { images.forEach { add(it) } })
            }
        }
        is ComposerAttachment.Video -> {
            check(attachments.size == 1)
            val blob = uploadComposerVideo(first, did, pdsOrigin)
            buildJsonObject {
                put("\$type", "app.bsky.embed.video")
                put("video", AtProtoJson.encodeToJsonElement(blob))
                first.altText.takeIf { it.isNotBlank() }?.let { put("alt", it) }
                put("aspectRatio", aspectRatio(first.width, first.height))
                put("presentation", "default")
            }
        }
        is ComposerAttachment.Gif -> {
            check(attachments.size == 1)
            val thumb = first.previewThumbnailUri?.let { uploadGifThumbnail(it) }
            composerGifEmbed(first, thumb)
        }
    }
}

private fun aspectRatio(width: Int, height: Int) = buildJsonObject {
    put("width", width)
    put("height", height)
}

private suspend fun AppContainer.uploadComposerVideo(
    video: ComposerAttachment.Video,
    did: String,
    pdsOrigin: String,
): BlobRef {
    val i18n = i18nCatalog.value
    val failedMessage = i18n.translate(VIDEO_PROCESS_ERROR)
    val pds = pdsOrigin.toHttpUrlOrNull() ?: throw IOException(failedMessage)
    val extension = composerVideoExtensions[video.mimeType] ?: throw IOException(i18n.translate(VIDEO_FORMAT_ERROR))
    val file = File(context.cacheDir, "composer_${UUID.randomUUID()}.$extension")
    try {
        withContext(Dispatchers.IO) {
            val source = context.contentResolver.openInputStream(Uri.parse(video.uri)) ?: throw IOException(failedMessage)
            source.use { input ->
                file.outputStream().use { output ->
                    val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(bytes)
                        if (count == -1) break
                        total += count
                        require(total <= MAX_VIDEO_BYTES) { i18n.translate(VIDEO_SIZE_ERROR, VIDEO_SIZE_ARGS) }
                        output.write(bytes, 0, count)
                    }
                    require(total > 0) { failedMessage }
                }
            }
        }
        return ComposerVideoUpload(
            client = okHttpClient,
            serviceAuth = api::getServiceAuth,
            failedMessage = failedMessage,
            limitMessage = i18n.translate("You have temporarily reached the limit for video uploads. Please try again later."),
            timeoutMessage = i18n.translate("The video took too long to process. Please try again."),
        ).upload(file, video.mimeType, pds, did)
    } finally {
        file.delete()
    }
}

internal data class ComposerGifPage(
    val results: List<ComposerAttachment.Gif>,
    val next: String?,
)

@Serializable
private data class GifSearchResponse(
    val results: List<GifResult> = emptyList(),
    val next: String? = null,
)

@Serializable
private data class GifResult(
    val title: String = "",
    @SerialName("content_description") val description: String = "",
    @SerialName("media_formats") val formats: Map<String, GifFormat> = emptyMap(),
)

@Serializable
private data class GifFormat(val url: String = "", val dims: List<Int> = emptyList())

internal suspend fun AppContainer.findComposerGifs(query: String, cursor: String?): ComposerGifPage {
    val url = composerGifSearchUrl(query.trim(), Locale.getDefault().country, cursor)
    val request = Request.Builder().url(url).header("Accept", "application/json").build()
    return okHttpClient.readComposerResponse(request) { response ->
        if (!response.isSuccessful) throw IOException("GIF service returned HTTP ${response.code}")
        decodeComposerGifPage(response.body?.string().orEmpty())
    }
}

internal fun composerGifSearchUrl(query: String, country: String, cursor: String?): HttpUrl {
    val path = if (query.isBlank()) "featured" else "search"
    return GIF_SERVICE_ORIGIN.toHttpUrl().newBuilder()
        .addPathSegments("klipy/v2/$path")
        .addQueryParameter("limit", "30")
        .addQueryParameter("contentfilter", "low")
        .addQueryParameter("client_key", GIF_SERVICE_CLIENT_KEY)
        .apply {
            if (country.isNotBlank()) addQueryParameter("locale", country.lowercase(Locale.ROOT))
            if (query.isNotBlank()) addQueryParameter("q", query)
            cursor?.let { addQueryParameter("pos", it) }
        }
        .build()
}

internal fun decodeComposerGifPage(body: String): ComposerGifPage {
    val page = AtProtoJson.decodeFromString<GifSearchResponse>(body)
    return ComposerGifPage(
        results = page.results.mapNotNull(::composerGifAttachment).distinctBy { it.uri },
        next = page.next?.takeIf { it.isNotBlank() },
    )
}

private fun composerGifAttachment(result: GifResult): ComposerAttachment.Gif? {
    val gif = result.formats["gif"] ?: return null
    val gifUrl = gif.url.providerGifUrl() ?: return null
    val (width, height) = gif.dims.takeIf { it.size == 2 && it.all { dimension -> dimension > 0 } } ?: return null
    val embedUri = gifUrl.newBuilder()
        .setQueryParameter("hh", height.toString())
        .setQueryParameter("ww", width.toString())
        .apply {
            if (gifUrl.host == "static.klipy.com") {
                for (format in listOf("mp4", "webm")) {
                    val media = result.formats[format]?.url?.providerGifUrl() ?: continue
                    if (media.host != gifUrl.host || media.pathSegments.dropLast(1) != gifUrl.pathSegments.dropLast(1)) continue
                    val slug = media.pathSegments.last().substringBeforeLast('.', "")
                    if (gifSlugPattern.matches(slug)) setQueryParameter(format, slug)
                }
            }
        }
        .build()

    val previewUrl = result.formats["tinygif"]?.url?.providerGifUrl() ?: gifUrl
    val thumbUrl = result.formats["preview"]?.url?.providerGifUrl()
    return ComposerAttachment.Gif(
        uri = embedUri.toString(),
        previewUri = previewUrl.proxyGifUrl(),
        title = result.description.ifBlank { result.title },
        previewThumbnailUri = thumbUrl?.proxyGifUrl(),
    )
}

private fun String.providerGifUrl(): HttpUrl? = toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() && it.host in gifProviderProxies
}

private fun HttpUrl.proxyGifUrl() = newBuilder().host(gifProviderProxies.getValue(host)).build().toString()

internal fun composerGifEmbed(gif: ComposerAttachment.Gif, thumb: BlobRef? = null) = buildJsonObject {
    val alt = gif.altText.trim()
    put("\$type", "app.bsky.embed.external")
    put("external", buildJsonObject {
        put("uri", gif.uri)
        put("title", gif.title)
        put("description", if (alt.isEmpty()) "ALT: ${gif.title}" else "Alt: $alt")
        thumb?.let { put("thumb", AtProtoJson.encodeToJsonElement(it)) }
    })
}

private suspend fun AppContainer.uploadGifThumbnail(uri: String): BlobRef? {
    val url = uri.toHttpUrlOrNull()?.takeIf {
        it.isHttps && it.port == 443 && it.host in gifProviderProxies.values
    } ?: return null
    val request = Request.Builder().url(url).build()
    val thumbnail = try {
        okHttpClient.readComposerResponse(request) { response ->
            if (!response.isSuccessful) return@readComposerResponse null
            val body = response.body ?: return@readComposerResponse null
            val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }
            if (mime == null || mime !in setOf("image/jpeg", "image/png", "image/webp")) return@readComposerResponse null
            if (body.contentLength() > MAX_GIF_THUMBNAIL_BYTES) return@readComposerResponse null
            val source = body.source()
            if (source.request(MAX_GIF_THUMBNAIL_BYTES + 1)) return@readComposerResponse null
            source.readByteArray().takeIf { it.isNotEmpty() }?.let { it to mime }
        }
    } catch (_: IOException) {
        null
    } ?: return null
    return api.uploadBlob(thumbnail.first, thumbnail.second)
}
