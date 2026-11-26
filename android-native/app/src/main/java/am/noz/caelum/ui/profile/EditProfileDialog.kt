package am.noz.caelum.ui.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.media.ImageCompressor
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BlobRef
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant

private const val MAX_DISPLAY_NAME_GRAPHEMES = 64
private const val MAX_DESCRIPTION_GRAPHEMES = 256
private const val MAX_PRONOUNS_GRAPHEMES = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    profile: ProfileViewDetailed,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val i18n = LocalI18n.current
    val scope = rememberAccountCoroutineScope()

    var displayName by remember(profile.did) { mutableStateOf(profile.displayName.orEmpty()) }
    var description by remember(profile.did) { mutableStateOf(profile.description.orEmpty()) }
    var pronouns by remember(profile.did) { mutableStateOf(profile.pronouns.orEmpty()) }
    var website by remember(profile.did) { mutableStateOf(profile.website.orEmpty()) }

    var pendingAvatarUri by remember(profile.did) { mutableStateOf<Uri?>(null) }
    var removeAvatar by remember(profile.did) { mutableStateOf(false) }
    var pendingBannerUri by remember(profile.did) { mutableStateOf<Uri?>(null) }
    var removeBanner by remember(profile.did) { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingAvatarUri = uri
            removeAvatar = false
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingBannerUri = uri
            removeBanner = false
        }
    }

    val isDirty = displayName != profile.displayName.orEmpty() ||
        description != profile.description.orEmpty() ||
        pronouns != profile.pronouns.orEmpty() ||
        website != profile.website.orEmpty() ||
        pendingAvatarUri != null ||
        removeAvatar ||
        pendingBannerUri != null ||
        removeBanner

    val displayNameLen = RichTextEngine.graphemeLength(displayName)
    val descriptionLen = RichTextEngine.graphemeLength(description)
    val pronounsLen = RichTextEngine.graphemeLength(pronouns)
    val hasValidationError = displayNameLen > MAX_DISPLAY_NAME_GRAPHEMES ||
        descriptionLen > MAX_DESCRIPTION_GRAPHEMES ||
        pronounsLen > MAX_PRONOUNS_GRAPHEMES

    fun handleRequestClose() {
        if (isSaving) return
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    fun saveProfile() {
        if (isSaving || hasValidationError) return
        isSaving = true
        errorMsg = null
        scope.launch {
            runCatching {
                val repoDid = profile.did
                val existingRecord: JsonObject = try {
                    container.xrpcClient.getJson(
                        nsid = "com.atproto.repo.getRecord",
                        params = listOf(
                            "repo" to repoDid,
                            "collection" to "app.bsky.actor.profile",
                            "rkey" to "self",
                        ),
                    ).jsonObject["value"]?.jsonObject ?: JsonObject(emptyMap())
                } catch (ex: XrpcException) {
                    val isNotFound = ex.statusCode == 404 ||
                        ex.error == "RecordNotFound" ||
                        (ex.error == "InvalidRequest" && ex.message.contains("Could not locate record", ignoreCase = true))
                    if (isNotFound) JsonObject(emptyMap()) else throw ex
                }

                suspend fun compressAndUpload(uri: Uri): BlobRef {
                    val (bytes, mimeType) = withContext(Dispatchers.IO) {
                        val compressed = ImageCompressor.compressUri(
                            context = context,
                            uri = uri,
                            maxDimension = ImageCompressor.THUMB_MAX_DIMENSION,
                            maxBytes = ImageCompressor.THUMB_MAX_BYTES,
                        )
                        try {
                            compressed.file.readBytes() to compressed.mimeType
                        } finally {
                            compressed.file.delete()
                        }
                    }
                    return container.api.uploadBlob(bytes, mimeType)
                }

                val uploadedAvatarBlob = pendingAvatarUri?.let { compressAndUpload(it) }
                val uploadedBannerBlob = pendingBannerUri?.let { compressAndUpload(it) }

                val trimmedName = displayName.trimEnd()
                val trimmedDesc = description.trimEnd()
                val trimmedPronouns = pronouns.trim()
                val normalizedWebsite = website.trim().let { raw ->
                    when {
                        raw.isEmpty() -> ""
                        raw.startsWith("http://", ignoreCase = true) ||
                            raw.startsWith("https://", ignoreCase = true) -> raw
                        else -> "https://$raw"
                    }
                }

                val managedKeys = setOf(
                    "\$type",
                    "displayName",
                    "description",
                    "pronouns",
                    "website",
                    "avatar",
                    "banner",
                )

                val updatedRecord = buildJsonObject {
                    for ((k, v) in existingRecord) {
                        if (k !in managedKeys) {
                            put(k, v)
                        }
                    }
                    put("\$type", JsonPrimitive("app.bsky.actor.profile"))
                    if (trimmedName.isNotEmpty()) {
                        put("displayName", JsonPrimitive(trimmedName))
                    }
                    if (trimmedDesc.isNotEmpty()) {
                        put("description", JsonPrimitive(trimmedDesc))
                    }
                    if (trimmedPronouns.isNotEmpty()) {
                        put("pronouns", JsonPrimitive(trimmedPronouns))
                    }
                    if (normalizedWebsite.isNotEmpty()) {
                        put("website", JsonPrimitive(normalizedWebsite))
                    }

                    when {
                        uploadedAvatarBlob != null -> {
                            put("avatar", AtProtoJson.encodeToJsonElement(BlobRef.serializer(), uploadedAvatarBlob))
                        }
                        !removeAvatar && existingRecord["avatar"] != null -> {
                            put("avatar", existingRecord["avatar"]!!)
                        }
                    }

                    when {
                        uploadedBannerBlob != null -> {
                            put("banner", AtProtoJson.encodeToJsonElement(BlobRef.serializer(), uploadedBannerBlob))
                        }
                        !removeBanner && existingRecord["banner"] != null -> {
                            put("banner", existingRecord["banner"]!!)
                        }
                    }

                    if (existingRecord["pinnedPost"] == null && profile.pinnedPost != null) {
                        put("pinnedPost", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), profile.pinnedPost))
                    }
                    if (existingRecord["createdAt"] == null) {
                        put("createdAt", JsonPrimitive(Instant.now().toString()))
                    }
                }

                container.api.putRecord(
                    repo = repoDid,
                    collection = "app.bsky.actor.profile",
                    rkey = "self",
                    record = updatedRecord,
                )
            }.onSuccess {
                isSaving = false
                Toast.makeText(context, i18n.translate("Profile updated"), Toast.LENGTH_SHORT).show()
                onSaved()
            }.onFailure { ex ->
                if (ex is CancellationException) throw ex
                isSaving = false
                errorMsg = ex.message ?: i18n.translate("Failed to update profile")
            }
        }
    }

    Dialog(
        onDismissRequest = ::handleRequestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        TextButton(onClick = ::handleRequestClose, enabled = !isSaving) {
                            Text(tr("Cancel"))
                        }
                    },
                    title = {
                        Text(
                            text = tr("Edit profile"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    actions = {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(
                                onClick = ::saveProfile,
                                enabled = isDirty && !hasValidationError,
                            ) {
                                Text(
                                    text = tr("Save"),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    },
                )
            },
            containerColor = palette.contrast0.toComposeColor(),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Banner + overlapping avatar picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    val bannerModel: Any? = when {
                        removeBanner -> null
                        pendingBannerUri != null -> pendingBannerUri
                        else -> profile.banner
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(palette.primary500.toComposeColor())
                            .clickable(enabled = !isSaving) {
                                bannerPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                    ) {
                        if (bannerModel != null) {
                            AsyncImage(
                                model = bannerModel,
                                contentDescription = tr("Banner image"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                        )
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = tr("Change banner"),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (bannerModel != null) {
                                IconButton(
                                    onClick = {
                                        pendingBannerUri = null
                                        removeBanner = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = tr("Remove banner"),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    val avatarModel: Any? = when {
                        removeAvatar -> null
                        pendingAvatarUri != null -> pendingAvatarUri
                        else -> profile.avatar
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .offset(y = 98.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(palette.contrast0.toComposeColor())
                                .border(3.dp, palette.contrast0.toComposeColor(), CircleShape)
                                .clickable(enabled = !isSaving) {
                                    avatarPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = tr("Profile avatar"),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                            } else {
                                CaelumAvatar(
                                    url = null,
                                    contentDescription = tr("Default avatar"),
                                    size = 80.dp,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = tr("Change avatar"),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        if (avatarModel != null) {
                            IconButton(
                                onClick = {
                                    pendingAvatarUri = null
                                    removeAvatar = true
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.7f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = tr("Remove avatar"),
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(54.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (errorMsg != null) {
                        Text(
                            text = errorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.negative500.toComposeColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.negative50.toComposeColor())
                                .padding(10.dp),
                        )
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(tr("Display Name")) },
                        placeholder = { Text("e.g. Alice") },
                        singleLine = true,
                        isError = displayNameLen > MAX_DISPLAY_NAME_GRAPHEMES,
                        supportingText = {
                            if (displayNameLen > MAX_DISPLAY_NAME_GRAPHEMES) {
                                Text("$displayNameLen / $MAX_DISPLAY_NAME_GRAPHEMES")
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(tr("Description")) },
                        placeholder = { Text(tr("Tell us a bit about yourself")) },
                        minLines = 3,
                        maxLines = 6,
                        isError = descriptionLen > MAX_DESCRIPTION_GRAPHEMES,
                        supportingText = {
                            Text("$descriptionLen / $MAX_DESCRIPTION_GRAPHEMES")
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = pronouns,
                        onValueChange = { pronouns = it },
                        label = { Text(tr("Pronouns")) },
                        placeholder = { Text("e.g. she/her, they/them") },
                        singleLine = true,
                        isError = pronounsLen > MAX_PRONOUNS_GRAPHEMES,
                        supportingText = {
                            if (pronounsLen > MAX_PRONOUNS_GRAPHEMES) {
                                Text("$pronounsLen / $MAX_PRONOUNS_GRAPHEMES")
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text(tr("Website")) },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(tr("Discard changes?")) },
            text = { Text(tr("Are you sure you want to discard your changes?")) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onDismiss()
                }) {
                    Text(tr("Discard"), color = palette.negative500.toComposeColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(tr("Keep editing"))
                }
            },
        )
    }
}
