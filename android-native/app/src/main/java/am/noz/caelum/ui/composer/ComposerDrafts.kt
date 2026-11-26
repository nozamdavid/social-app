package am.noz.caelum.ui.composer

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.storage.DraftDao
import am.noz.caelum.core.storage.DraftEntity
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal sealed interface ComposerAttachment {
    val altText: String

    @Serializable
    @SerialName("image")
    data class Image(val uri: String, override val altText: String = "") : ComposerAttachment

    @Serializable
    @SerialName("video")
    data class Video(
        val uri: String,
        val width: Int,
        val height: Int,
        override val altText: String = "",
        val mimeType: String = "video/mp4",
    ) : ComposerAttachment

    @Serializable
    @SerialName("gif")
    data class Gif(
        val uri: String,
        val previewUri: String,
        val title: String,
        override val altText: String = "",
        val previewThumbnailUri: String? = null,
    ) : ComposerAttachment
}

@Serializable
internal data class LocalDraftPayload(
    val posts: List<String>,
    val language: String,
    val threadgateMode: String,
    val mediaByPost: List<List<ComposerAttachment>> = emptyList(),
    val replyTo: PostView? = null,
    val quotePost: PostView? = null,
    val allowQuotes: Boolean = true,
    val mediaFolder: String? = null,
)

internal class ComposerDrafts(
    private val context: Context,
    private val dao: DraftDao,
) {
    private val mediaRoot = File(context.filesDir, "composer-drafts")

    suspend fun save(accountDid: String, previous: DraftEntity?, payload: LocalDraftPayload): DraftEntity {
        require(previous == null || previous.accountDid == accountDid)
        val folder = UUID.randomUUID().toString()
        val mediaDir = File(mediaRoot, folder)
        val stored = try {
            withContext(Dispatchers.IO) {
                payload.copy(
                    mediaByPost = payload.mediaByPost.map { attachments ->
                        attachments.map { attachment ->
                            when (attachment) {
                                is ComposerAttachment.Image -> attachment.copy(
                                    uri = copyMedia(attachment.uri, mediaDir, "image"),
                                )
                                is ComposerAttachment.Video -> attachment.copy(
                                    uri = copyMedia(
                                        attachment.uri,
                                        mediaDir,
                                        MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mimeType) ?: "mp4",
                                    ),
                                )
                                is ComposerAttachment.Gif -> attachment
                            }
                        }
                    },
                    mediaFolder = folder.takeIf { mediaDir.exists() },
                )
            }
        } catch (error: Exception) {
            withContext(Dispatchers.IO + NonCancellable) { mediaDir.deleteRecursively() }
            throw error
        }
        val now = System.currentTimeMillis()
        val draft = DraftEntity(
            id = previous?.id ?: UUID.randomUUID().toString(),
            accountDid = accountDid,
            payloadJson = AtProtoJson.encodeToString(stored),
            createdAtMs = previous?.createdAtMs ?: now,
            updatedAtMs = now,
        )
        // Commit the new paths before removing the old files, even if the composer closes in flight.
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                dao.upsert(draft)
            } catch (error: Exception) {
                mediaDir.deleteRecursively()
                throw error
            }
            previous?.let(::removeFiles)
        }
        return draft
    }

    suspend fun delete(draft: DraftEntity) = withContext(Dispatchers.IO + NonCancellable) {
        dao.deleteById(draft.id)
        removeFiles(draft)
    }

    private fun copyMedia(uri: String, mediaDir: File, suffix: String): String {
        mediaDir.mkdirs()
        val destination = File.createTempFile("attachment_", ".$suffix", mediaDir)
        val source = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("Could not open an attachment")
        source.use { input -> destination.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(destination).toString()
    }

    private fun removeFiles(draft: DraftEntity) {
        val folder = draft.localPayload()?.mediaFolder ?: return
        if (runCatching { UUID.fromString(folder).toString() }.getOrNull() == folder) {
            File(mediaRoot, folder).deleteRecursively()
        }
    }
}

internal fun DraftEntity.localPayload(): LocalDraftPayload? =
    runCatching { AtProtoJson.decodeFromString<LocalDraftPayload>(payloadJson) }.getOrNull()
