package am.noz.caelum.ui.composer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import am.noz.caelum.core.alttext.AltTextException
import am.noz.caelum.core.alttext.DescriptionSupportStatus
import am.noz.caelum.core.emoji.EmojiSearchEngine
import am.noz.caelum.core.emoji.EmojiSearchResult
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.model.TimeFormatter
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.DraftEntity
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

private val MENTION_TOKEN_REGEX = Regex("(^|\\s)@([a-zA-Z0-9._-]{1,64})$")
private const val MAX_POST_IMAGES = 4
private val postingLanguages = listOf("en", "es", "fr", "de", "ja", "pt", "ko", "it", "nl", "pl", "ru", "zh")

private data class EditorPost(
    val id: String = UUID.randomUUID().toString(),
    val field: TextFieldValue = TextFieldValue(),
    val attachments: List<ComposerAttachment> = emptyList(),
)

private data class CameraCapture(val postId: String, val file: File, val uri: Uri)

/** The selected draft, or null when the prompt will close the composer. */
private data class PendingDraftAction(val nextDraft: DraftEntity? = null)

private fun editorField(text: String) = TextFieldValue(text, TextRange(text.length))

@Composable
fun ComposerSheet(
    initialText: String = "",
    initialSharedUris: List<Uri> = emptyList(),
    replyTo: PostView? = null,
    quotePost: PostView? = null,
    onDismiss: () -> Unit,
    onPostPublished: (JsonArray) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val i18n = LocalI18n.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val emojiEngine by container.emojiEngine.collectAsState()
    val scope = rememberAccountCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val draftDao = container.database.draftDao()
    val drafts = remember(context, draftDao) { ComposerDrafts(context, draftDao) }

    val posts = remember { mutableStateListOf(EditorPost(field = editorField(initialText))) }
    var activeId by remember { mutableStateOf(posts.first().id) }
    var languageOverride by remember { mutableStateOf<String?>(null) }
    val defaultLanguage = settings.postLanguage.ifBlank { "en" }
    val language = languageOverride ?: defaultLanguage
    var threadgateMode by remember { mutableStateOf("anyone") }
    var allowQuotes by remember { mutableStateOf(true) }
    var replySubject by remember { mutableStateOf(replyTo) }
    var quoteSubject by remember { mutableStateOf(quotePost) }
    var selectedDraft by remember { mutableStateOf<DraftEntity?>(null) }
    var savedSnapshot by remember { mutableStateOf<LocalDraftPayload?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(initialSharedUris.isNotEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingDraftAction?>(null) }
    val busy = isPublishing || isSaving || isImporting

    var emojiResults by remember { mutableStateOf<List<EmojiSearchResult>>(emptyList()) }
    var mentionResults by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }

    var altTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var galleryTarget by remember { mutableStateOf<String?>(null) }
    var cameraPermissionTarget by remember { mutableStateOf<String?>(null) }
    var cameraCapture by remember { mutableStateOf<CameraCapture?>(null) }
    var gifTarget by remember { mutableStateOf<String?>(null) }
    var showDrafts by remember { mutableStateOf(false) }
    var showThreadgate by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    val cameraFiles = remember { mutableSetOf<File>() }
    DisposableEffect(Unit) { onDispose { cameraFiles.forEach { it.delete() } } }

    val activePost = posts.firstOrNull { it.id == activeId } ?: posts.first()
    val activeField = activePost.field
    val snapshot = LocalDraftPayload(
        posts = posts.map { it.field.text },
        language = language,
        threadgateMode = threadgateMode,
        mediaByPost = posts.map { it.attachments },
        replyTo = replySubject,
        quotePost = quoteSubject,
        allowQuotes = allowQuotes,
    )
    val baseline = savedSnapshot ?: LocalDraftPayload(
        posts = listOf(""),
        language = defaultLanguage,
        threadgateMode = "anyone",
        mediaByPost = listOf(emptyList()),
        replyTo = replyTo,
        quotePost = quotePost,
    )
    val dirty = snapshot != baseline
    val hasPost = snapshot.posts.any { it.isNotBlank() } || snapshot.mediaByPost.any { it.isNotEmpty() }
    val needsAlt = settings.requireAltTextEnabled && snapshot.mediaByPost.flatten().any { it.altText.isBlank() }
    val lengthAllowed = snapshot.posts.all { RichTextEngine.graphemeLength(it) <= 300 }
    val allowsMoreImages = activePost.attachments.all { it is ComposerAttachment.Image } &&
        activePost.attachments.size < MAX_POST_IMAGES

    fun updatePost(id: String, update: (EditorPost) -> EditorPost) {
        val index = posts.indexOfFirst { it.id == id }
        if (index >= 0) posts[index] = update(posts[index])
    }

    fun loadDraft(entity: DraftEntity) {
        val payload = entity.localPayload() ?: return
        val text = payload.posts.ifEmpty { listOf("") }
        val media = text.indices.map { payload.mediaByPost.getOrNull(it).orEmpty() }
        posts.clear()
        text.forEachIndexed { index, value ->
            posts.add(EditorPost(field = editorField(value), attachments = media[index]))
        }
        activeId = posts.first().id
        languageOverride = payload.language
        threadgateMode = payload.threadgateMode
        allowQuotes = payload.allowQuotes
        replySubject = payload.replyTo
        quoteSubject = payload.quotePost
        savedSnapshot = payload.copy(posts = text, mediaByPost = media, mediaFolder = null)
        selectedDraft = entity
        errorMessage = null
    }

    fun finishDraftAction(action: PendingDraftAction) {
        pendingAction = null
        val next = action.nextDraft
        if (next == null) onDismiss() else loadDraft(next)
    }

    fun requestDraftAction(action: PendingDraftAction) {
        if (busy) return
        if (dirty) {
            keyboard?.hide()
            pendingAction = action
        } else {
            finishDraftAction(action)
        }
    }

    fun saveDraft(action: PendingDraftAction) {
        val did = session.currentDid ?: return
        if (busy) return
        isSaving = true
        scope.launch {
            try {
                drafts.save(did, selectedDraft, snapshot)
                finishDraftAction(action)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                errorMessage = i18n.translate("Failed to save draft")
                pendingAction = null
            } finally {
                isSaving = false
            }
        }
    }

    suspend fun importMedia(target: String, uris: List<Uri>) {
        isImporting = true
        try {
            val existing = posts.firstOrNull { it.id == target }?.attachments ?: return
            val types = withContext(Dispatchers.IO) {
                uris.map { context.contentResolver.getType(it).orEmpty() }
            }
            val imports = if (types.any { it.startsWith("video/") }) {
                require(existing.isEmpty() && uris.size == 1) {
                    i18n.translate("Selecting multiple media types is not supported.")
                }
                listOf(readComposerVideo(context, uris.single(), i18n))
            } else {
                uris.take(MAX_POST_IMAGES - existing.size).map { ComposerAttachment.Image(it.toString()) }
            }
            updatePost(target) { it.copy(attachments = it.attachments + imports) }
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message ?: i18n.translate("Could not open the selected media.")
        } finally {
            isImporting = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_POST_IMAGES)) { uris ->
        val target = galleryTarget
        galleryTarget = null
        if (target != null && uris.isNotEmpty()) scope.launch { importMedia(target, uris) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val capture = cameraCapture
        cameraCapture = null
        if (capture != null) {
            if (captured && capture.file.length() > 0) {
                updatePost(capture.postId) { post ->
                    post.copy(attachments = post.attachments + ComposerAttachment.Image(capture.uri.toString()))
                }
                errorMessage = null
            } else {
                capture.file.delete()
                cameraFiles.remove(capture.file)
            }
        }
    }

    fun launchCamera(postId: String) {
        var file: File? = null
        try {
            val output = File.createTempFile("composer_camera_", ".jpg", context.cacheDir)
            file = output
            cameraFiles.add(output)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
            cameraCapture = CameraCapture(postId, output, uri)
            camera.launch(uri)
        } catch (_: Exception) {
            file?.let {
                it.delete()
                cameraFiles.remove(it)
            }
            cameraCapture = null
            errorMessage = i18n.translate("Could not open the camera.")
        }
    }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = cameraPermissionTarget
        cameraPermissionTarget = null
        if (target != null) {
            if (granted) launchCamera(target)
            else errorMessage = i18n.translate("Camera access needed")
        }
    }

    LaunchedEffect(Unit) {
        if (initialSharedUris.isNotEmpty()) importMedia(posts.first().id, initialSharedUris)
    }

    LaunchedEffect(activePost.id, activeField.text, activeField.selection, emojiEngine) {
        val text = activeField.text
        val start = activeField.selection.start
        val end = activeField.selection.end
        val token = EmojiSearchEngine.getEmojiToken(text, start, end)
        if (emojiEngine != null && token != null && token.query.isNotEmpty()) {
            emojiResults = emojiEngine?.search(token.query, limit = 8, context = token.context).orEmpty()
            mentionResults = emptyList()
            return@LaunchedEffect
        }
        emojiResults = emptyList()
        mentionResults = emptyList()
        if (start == end && start in 0..text.length) {
            val match = MENTION_TOKEN_REGEX.find(text.substring(0, start)) ?: return@LaunchedEffect
            delay(180)
            mentionResults = try {
                container.api.searchActorsTypeahead(match.groupValues[2], limit = 6)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun publish() {
        val did = session.currentDid ?: return
        val pdsOrigin = container.sessionManager.currentAuth()?.takeIf { it.did == did }?.pdsUrl ?: return
        isPublishing = true
        errorMessage = null
        keyboard?.hide()
        scope.launch {
            try {
                val parent = snapshot.replyTo?.let { StrongRef(it.uri, it.cid) }
                val root = (snapshot.replyTo?.record?.get("reply") as? JsonObject)?.get("root")
                    ?.let { AtProtoJson.decodeFromJsonElement(StrongRef.serializer(), it) }
                val postRecords = mutableListOf<JsonObject>()
                for ((index, input) in snapshot.posts.withIndex()) {
                    val raw = RichTextEngine.cleanNewlines(input).trim()
                    val media = snapshot.mediaByPost[index]
                    val quote = snapshot.quotePost?.takeIf { postRecords.isEmpty() }
                    if (raw.isEmpty() && media.isEmpty() && quote == null) continue

                    val facets = RichTextEngine.detectFacets(raw, container.tlds) { handle ->
                        container.identityResolver.resolveHandle(handle, pdsOrigin)
                    }
                    val (shortText, shortFacets) = RichTextEngine.shortenLinks(raw, facets)
                    val mediaEmbed = container.createMediaEmbed(media, did, pdsOrigin)
                    val quoteEmbed = quote?.let {
                        buildJsonObject {
                            put("\$type", "app.bsky.embed.record")
                            put("record", AtProtoJson.encodeToJsonElement(StrongRef(it.uri, it.cid)))
                        }
                    }
                    val embed = if (mediaEmbed != null && quoteEmbed != null) {
                        buildJsonObject {
                            put("\$type", "app.bsky.embed.recordWithMedia")
                            put("media", mediaEmbed)
                            put("record", quoteEmbed)
                        }
                    } else mediaEmbed ?: quoteEmbed

                    postRecords += buildJsonObject {
                        put("text", shortText)
                        if (shortFacets.isNotEmpty()) {
                            put("facets", AtProtoJson.encodeToJsonElement(ListSerializer(RichTextFacet.serializer()), shortFacets))
                        }
                        embed?.let { put("embed", it) }
                        put("langs", buildJsonArray { add(JsonPrimitive(snapshot.language)) })
                    }
                }
                if (postRecords.isEmpty()) return@launch
                val writes = composerPostWrites(
                    did, postRecords, parent, root, snapshot.threadgateMode, snapshot.allowQuotes,
                )
                container.api.applyWrites(did, writes)
                selectedDraft?.let { runCatching { drafts.delete(it) } }
                onPostPublished(writes)
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
                    ?: i18n.translate("Post failed to upload. Please check your Internet connection and try again.")
            } finally {
                isPublishing = false
            }
        }
    }

    Dialog(
        onDismissRequest = { requestDraftAction(PendingDraftAction()) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            containerColor = palette.contrast0.toComposeColor(),
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().background(palette.contrast0.toComposeColor())
                        .statusBarsPadding().padding(top = 8.dp, start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { requestDraftAction(PendingDraftAction()) }, enabled = !busy) {
                        Text(tr("Cancel"), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.weight(1f))
                    if (replySubject == null) {
                        IconButton(onClick = {
                            keyboard?.hide()
                            showDrafts = true
                        }, enabled = !busy) {
                            Icon(Icons.Filled.Drafts, tr("Drafts"), tint = palette.primary500.toComposeColor())
                        }
                    }
                    val publishLabel = when {
                        replySubject != null && posts.size > 1 -> tr("Publish replies")
                        replySubject != null -> tr("Publish reply")
                        posts.size > 1 -> tr("Publish posts")
                        else -> tr("Publish post")
                    }
                    Button(
                        enabled = !busy && (hasPost || quoteSubject != null) && !needsAlt && lengthAllowed,
                        onClick = ::publish,
                        modifier = Modifier.semantics { contentDescription = publishLabel },
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = palette.primary500.toComposeColor().copy(alpha = 0.45f),
                            disabledContentColor = palette.contrast1000.toComposeColor().copy(alpha = 0.75f),
                        ),
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                tr(if (replySubject == null) "Post" else "Reply"),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth().background(palette.contrast0.toComposeColor())
                        .navigationBarsPadding().imePadding(),
                ) {
                    if (!busy) {
                        ComposerSuggestions(
                            emojis = emojiResults,
                            mentions = mentionResults,
                            onEmoji = { item ->
                                val token = EmojiSearchEngine.getEmojiToken(
                                    activeField.text, activeField.selection.start, activeField.selection.end,
                                )
                                if (token != null) {
                                    val change = EmojiSearchEngine.replaceEmojiToken(activeField.text, token, item.native)
                                    updatePost(activeId) {
                                        it.copy(field = TextFieldValue(change.text, TextRange(change.selectionStart, change.selectionEnd)))
                                    }
                                    emojiResults = emptyList()
                                }
                            },
                            onMention = { actor ->
                                val cursor = activeField.selection.start
                                val match = MENTION_TOKEN_REGEX.find(activeField.text.substring(0, cursor))
                                if (match != null) {
                                    val start = match.range.first + match.groupValues[1].length
                                    val mention = "@${actor.handle} "
                                    val updated = activeField.text.replaceRange(start, cursor, mention)
                                    updatePost(activeId) {
                                        it.copy(field = TextFieldValue(updated, TextRange(start + mention.length)))
                                    }
                                    mentionResults = emptyList()
                                }
                            },
                        )
                    }
                    if (replySubject == null) {
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                showThreadgate = true
                            },
                            enabled = !busy,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                tr(
                                    if (threadgateMode == "anyone" && allowQuotes) "Anyone can interact"
                                    else "Post interaction settings",
                                ),
                            )
                        }
                    }
                    HorizontalDivider(color = palette.contrast100.toComposeColor())
                    ComposerToolbar(
                        language = language,
                        count = RichTextEngine.graphemeLength(activeField.text),
                        enabled = !busy,
                        canPickMedia = allowsMoreImages,
                        canPickGif = activePost.attachments.isEmpty(),
                        canAddPost = (activeField.text.isNotBlank() || activePost.attachments.isNotEmpty()) &&
                            posts.none { it.field.text.isBlank() && it.attachments.isEmpty() },
                        onGallery = {
                            val filter = if (activePost.attachments.isEmpty()) ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            else ActivityResultContracts.PickVisualMedia.ImageOnly
                            try {
                                galleryTarget = activeId
                                gallery.launch(PickVisualMediaRequest(filter))
                            } catch (_: ActivityNotFoundException) {
                                galleryTarget = null
                                errorMessage = i18n.translate("Could not open the selected media.")
                            }
                        },
                        onCamera = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                launchCamera(activeId)
                            } else {
                                cameraPermissionTarget = activeId
                                requestCamera.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onGif = {
                            keyboard?.hide()
                            gifTarget = activeId
                        },
                        onLanguage = {
                            keyboard?.hide()
                            showLanguages = true
                        },
                        onAddPost = {
                            val post = EditorPost()
                            posts.add(post)
                            activeId = post.id
                        },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = palette.negative500.toComposeColor(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                if (needsAlt) {
                    Text(
                        tr("Alt text is required before posting."),
                        color = palette.negative500.toComposeColor(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                replySubject?.let { ComposerSubjectPreview(it, isReply = true) }
                posts.forEachIndexed { index, post ->
                    key(post.id) {
                        ComposerPostEditor(
                            avatar = session.currentAccount?.avatar,
                            field = post.field,
                            attachments = post.attachments,
                            enabled = !busy,
                            reply = replySubject != null,
                            quote = quoteSubject.takeIf { index == 0 },
                            onActivate = { activeId = post.id },
                            onValueChange = { value -> updatePost(post.id) { it.copy(field = value) } },
                            onRemovePost = if (posts.size > 1) {
                                {
                                    posts.removeAt(index)
                                    if (activeId == post.id) activeId = posts[index.coerceAtMost(posts.lastIndex)].id
                                }
                            } else null,
                            onRemoveAttachment = { attachment ->
                                updatePost(post.id) { it.copy(attachments = it.attachments.filterIndexed { i, _ -> i != attachment }) }
                            },
                            onEditAlt = { attachment ->
                                keyboard?.hide()
                                altTarget = post.id to attachment
                            },
                        )
                    }
                }
            }
        }
    }

    altTarget?.let { (postId, index) ->
        val attachment = posts.firstOrNull { it.id == postId }?.attachments?.getOrNull(index)
        if (attachment != null) {
            val saveAlt: (String) -> Unit = { alt ->
                val updated = when (attachment) {
                    is ComposerAttachment.Image -> attachment.copy(altText = alt)
                    is ComposerAttachment.Video -> attachment.copy(altText = alt)
                    is ComposerAttachment.Gif -> attachment.copy(altText = alt)
                }
                updatePost(postId) { it.copy(attachments = it.attachments.toMutableList().apply { this[index] = updated }) }
                altTarget = null
            }
            if (attachment is ComposerAttachment.Image) {
                AltTextEditorDialog(attachment, onSave = saveAlt, onDismiss = { altTarget = null })
            } else {
                MediaAltTextDialog(attachment, onSave = saveAlt, onDismiss = { altTarget = null })
            }
        }
    }

    gifTarget?.let { target ->
        ComposerGifPicker(
            onSelect = { gif ->
                updatePost(target) { it.copy(attachments = listOf(gif)) }
                gifTarget = null
            },
            onDismiss = { gifTarget = null },
        )
    }

    if (showDrafts) {
        session.currentDid?.let { did ->
            SavedDraftsDialog(
                accountDid = did,
                canSave = dirty || (quoteSubject != null && selectedDraft == null),
                onSave = {
                    showDrafts = false
                    saveDraft(PendingDraftAction())
                },
                onSelect = {
                    showDrafts = false
                    if (it.id != selectedDraft?.id) requestDraftAction(PendingDraftAction(it))
                },
                onDelete = { draft ->
                    scope.launch {
                        try {
                            drafts.delete(draft)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            errorMessage = i18n.translate("Failed to delete draft")
                        }
                    }
                },
                onDismiss = { showDrafts = false },
                currentDraftId = selectedDraft?.id,
            )
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!isSaving) pendingAction = null },
            title = { Text(tr(if (selectedDraft == null) "Save draft?" else "Save changes?")) },
            text = {
                Text(
                    tr(
                        if (selectedDraft == null) "Would you like to save this as a draft to edit later?"
                        else "You have unsaved changes to this draft, would you like to save them?",
                    )
                )
            },
            confirmButton = {
                TextButton(enabled = !isSaving, onClick = { saveDraft(action) }) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(tr(if (selectedDraft == null) "Save draft" else "Save changes"))
                }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !isSaving, onClick = { pendingAction = null }) {
                        Text(tr("Keep editing"))
                    }
                    TextButton(
                        enabled = !isSaving,
                        onClick = { finishDraftAction(action) },
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.negative500.toComposeColor()),
                    ) {
                        Text(tr("Discard"))
                    }
                }
            },
        )
    }

    if (showThreadgate) {
        val modes = listOf(
            "anyone" to "Anyone can reply",
            "following" to "People you follow",
            "followers" to "Your followers",
            "mentioned" to "People you mention",
            "nobody" to "Nobody",
        )
        AlertDialog(
            onDismissRequest = { showThreadgate = false },
            title = { Text(tr("Post interaction settings")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column(Modifier.selectableGroup()) {
                        modes.forEach { (mode, label) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                    selected = threadgateMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { threadgateMode = mode },
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = threadgateMode == mode, onClick = null)
                                Text(tr(label))
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = allowQuotes,
                            role = Role.Checkbox,
                            onValueChange = { allowQuotes = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = allowQuotes, onCheckedChange = null)
                        Text(tr("Allow quote posts"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadgate = false }) { Text(tr("Done")) }
            },
        )
    }

    if (showLanguages) {
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            title = { Text(tr("Select post language")) },
            text = {
                Column(Modifier.height(300.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    postingLanguages.forEach { code ->
                        val select = {
                            languageOverride = code
                            showLanguages = false
                        }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = language == code,
                                role = Role.RadioButton,
                                onClick = select,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = language == code, onClick = null)
                            Text(Locale.forLanguageTag(code).getDisplayLanguage(i18n.locale))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguages = false }) { Text(tr("Close")) }
            },
        )
    }
}
@Composable
private fun AltTextEditorDialog(
    attachment: ComposerAttachment.Image,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var alt by remember { mutableStateOf(attachment.altText) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var activeReqId by remember { mutableStateOf<String?>(null) }
    var confirmDownload by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add alt text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)),
                )
                OutlinedTextField(
                    value = alt,
                    onValueChange = { if (it.length <= 2000) alt = it },
                    label = { Text("Descriptive alt text (${alt.length}/2000)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = busyLabel == null,
                        onClick = {
                            val reqId = UUID.randomUUID().toString()
                            activeReqId = reqId
                            busyLabel = "Extracting text..."
                            statusMsg = null
                            scope.launch {
                                runCatching {
                                    container.altTextEngine.extractText(reqId, Uri.parse(attachment.uri))
                                }.onSuccess { text ->
                                    if (text.isBlank()) statusMsg = "No text found in image."
                                    else alt = if (alt.isBlank()) text else "$alt\n\n$text".take(2000)
                                }.onFailure { ex ->
                                    statusMsg = (ex as? AltTextException)?.code ?: ex.message
                                }
                                busyLabel = null
                                activeReqId = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.TextSnippet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Extract text")
                    }
                    OutlinedButton(
                        enabled = busyLabel == null,
                        onClick = {
                            scope.launch {
                                val st = container.altTextEngine.getDescriptionStatus()
                                if (st == DescriptionSupportStatus.DOWNLOADABLE) {
                                    confirmDownload = true
                                } else if (st == DescriptionSupportStatus.UNAVAILABLE) {
                                    statusMsg = "On-device GenAI image description is unavailable on this device."
                                } else {
                                    val reqId = UUID.randomUUID().toString()
                                    activeReqId = reqId
                                    busyLabel = "Describing image..."
                                    statusMsg = null
                                    runCatching {
                                        container.altTextEngine.suggestDescription(reqId, Uri.parse(attachment.uri), allowDownload = false)
                                    }.onSuccess { desc ->
                                        alt = if (alt.isBlank()) desc else "$desc\n\n$alt".take(2000)
                                    }.onFailure { ex ->
                                        statusMsg = (ex as? AltTextException)?.code ?: ex.message
                                    }
                                    busyLabel = null
                                    activeReqId = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Describe")
                    }
                }
                if (busyLabel != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(busyLabel.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { activeReqId?.let { container.altTextEngine.cancel(it) } }) { Text("Cancel") }
                    }
                }
                statusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(alt.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Download description model?") },
            text = { Text("Gemini Nano on-device image description requires downloading model assets via Google AICore.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDownload = false
                    val reqId = UUID.randomUUID().toString()
                    activeReqId = reqId
                    busyLabel = "Downloading & describing..."
                    scope.launch {
                        runCatching {
                            container.altTextEngine.suggestDescription(reqId, Uri.parse(attachment.uri), allowDownload = true) { prog ->
                                busyLabel = "${prog.phase}..."
                            }
                        }.onSuccess { desc ->
                            alt = desc.take(2000)
                        }.onFailure { ex ->
                            statusMsg = (ex as? AltTextException)?.code ?: ex.message
                        }
                        busyLabel = null
                        activeReqId = null
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownload = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Caelum 10-minute post editor modal (`src/components/dialogs/EditPostDialog.tsx`).
 */
@Composable
fun EditPostDialog(
    post: PostView,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberAccountCoroutineScope()

    val createdAt = (post.record["createdAt"] as? JsonPrimitive)?.content ?: post.indexedAt
    val origText = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
    var text by remember(post.uri) { mutableStateOf(origText) }
    var remaining by remember(post.uri) { mutableStateOf(TimeFormatter.formatEditCountdown(createdAt)) }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(post.uri) {
        while (true) {
            remaining = TimeFormatter.formatEditCountdown(createdAt)
            delay(1000L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit post")
                Text(
                    text = remaining ?: "Expired",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (remaining == null) palette.negative500.toComposeColor() else palette.primary500.toComposeColor(),
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Note: editing recreates the record under the same rkey, preserving your original createdAt timestamp and replies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${RichTextEngine.graphemeLength(text)}/300",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.contrast600.toComposeColor(),
                )
                err?.let { Text(it, color = palette.negative500.toComposeColor(), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && remaining != null && RichTextEngine.graphemeLength(text) in 1..300,
                onClick = {
                    scope.launch {
                        saving = true
                        container.postController.editPostWithinWindow(post, text)
                            .onSuccess {
                                onSaved()
                                onDismiss()
                            }
                            .onFailure { err = it.message }
                        saving = false
                    }
                },
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp))
                else Text("Save edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
