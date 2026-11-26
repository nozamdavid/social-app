package am.noz.caelum.ui.social

import am.noz.caelum.core.i18n.LocalI18n
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ContentLabelPref
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.session.SessionManager
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationHubScreen(
    route: AppRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    when (route) {
        AppRoute.Moderation -> ModerationOverviewScreen(onBack = onBack, onNavigate = onNavigate)
        AppRoute.ModerationMutedAccounts -> MutedAccountsScreen(onBack = onBack, onNavigate = onNavigate)
        AppRoute.ModerationBlockedAccounts -> BlockedAccountsScreen(onBack = onBack, onNavigate = onNavigate)
        AppRoute.ModerationModlists -> ModlistsScreen(onBack = onBack, onNavigate = onNavigate)
        AppRoute.ModerationInteractionSettings -> InteractionSettingsScreen(onBack = onBack)
        AppRoute.ModerationVerificationSettings -> VerificationSettingsScreen(onBack = onBack)
        AppRoute.ModerationInbox -> ModerationInboxScreen(onBack = onBack, onNavigate = onNavigate)
        AppRoute.ModerationInboxSettings -> ModerationInboxSettingsScreen(onBack = onBack)
        is AppRoute.ModerationInboxReportDetails -> ModerationInboxReportDetailsScreen(
            moderationService = route.moderationService,
            reportId = route.id,
            onBack = onBack,
        )
        is AppRoute.ModerationInboxNoticeDetails -> ModerationInboxNoticeDetailsScreen(
            subjectJson = route.subjectJson,
            onBack = onBack,
        )
        else -> ModerationOverviewScreen(onBack = onBack, onNavigate = onNavigate)
    }
}

private data class ContentFilterCategory(
    val id: String,
    val title: String,
    val description: String,
    val adultOnly: Boolean,
    val defaultVis: String,
)

private val CONTENT_FILTER_CATEGORIES = listOf(
    ContentFilterCategory("porn", "Adult content (NSFW)", "Explicit adult imagery and pornography", adultOnly = true, defaultVis = "hide"),
    ContentFilterCategory("sexual", "Suggestive", "Sexually suggestive or provocative content", adultOnly = true, defaultVis = "warn"),
    ContentFilterCategory("nudity", "Nudity", "Non-sexual artistic or educational nudity", adultOnly = false, defaultVis = "ignore"),
    ContentFilterCategory("graphic-media", "Graphic media / Gore", "Violence, blood, or disturbing imagery", adultOnly = true, defaultVis = "warn"),
)

private enum class MutedWordDuration(val label: String, val days: Long?) {
    Forever("Forever", null),
    OneDay("24h", 1),
    SevenDays("7d", 7),
    ThirtyDays("30d", 30),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModerationOverviewScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberAccountCoroutineScope()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value
        ?: ModerationUserSettings()

    var newWord by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf(MutedWordDuration.Forever) }
    var tagsOnly by remember { mutableStateOf(false) }
    var excludeFollowing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var expandedLabelerDid by remember { mutableStateOf<String?>(null) }
    var showAddLabelerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.currentDid) {
        val did = session.currentDid ?: return@LaunchedEffect
        runCatching { container.sessionManager.syncPreferencesFromServer(did) }
    }

    suspend fun pushPreferencesTransform(
        localTransform: (ModerationUserSettings) -> ModerationUserSettings,
        serverTransform: (MutableList<JsonObject>) -> Unit,
    ) {
        val did = session.currentDid ?: return
        isSyncing = true
        container.preferences.updateModerationSettings(did, localTransform)
        runCatching {
            val raw = container.api.getActorPreferences()
                .mapNotNull { it as? JsonObject }
                .toMutableList()
            serverTransform(raw)
            container.api.putActorPreferences(JsonArray(raw))
            container.sessionManager.syncPreferencesFromServer(did)
        }.onFailure { ex ->
            Toast.makeText(context, ex.message ?: "Failed to sync moderation preferences", Toast.LENGTH_SHORT).show()
        }
        isSyncing = false
    }

    fun addMutedWord() {
        val trimmed = newWord.trim()
        if (trimmed.isEmpty()) return
        val expiresAt = selectedDuration.days?.let { days ->
            Instant.now().plus(days, ChronoUnit.DAYS).toString()
        }
        val entry = MutedWord(
            id = UUID.randomUUID().toString(),
            value = trimmed,
            targets = if (tagsOnly) listOf("tag") else listOf("content", "tag"),
            actorTarget = if (excludeFollowing) "exclude-following" else "all",
            expiresAt = expiresAt,
        )
        newWord = ""
        scope.launch {
            pushPreferencesTransform(
                localTransform = { it.copy(mutedWords = it.mutedWords + entry) },
                serverTransform = { prefs ->
                    val existing = prefs.firstOrNull {
                        (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref"
                    }
                    val existingItems = (existing?.get("items") as? JsonArray)?.toMutableList() ?: mutableListOf()
                    existingItems.add(AtProtoJson.encodeToJsonElement(MutedWord.serializer(), entry))
                    prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref" }
                    prefs.add(
                        buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.actor.defs#mutedWordsPref"))
                            put("items", JsonArray(existingItems))
                        }
                    )
                },
            )
        }
    }

    fun removeMutedWord(word: MutedWord) {
        scope.launch {
            pushPreferencesTransform(
                localTransform = { st ->
                    st.copy(
                        mutedWords = st.mutedWords.filterNot {
                            if (word.id != null && it.id != null) it.id == word.id else it.value == word.value
                        }
                    )
                },
                serverTransform = { prefs ->
                    val existing = prefs.firstOrNull {
                        (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref"
                    }
                    val existingItems = (existing?.get("items") as? JsonArray)?.filterNot { el ->
                        val decoded = runCatching { AtProtoJson.decodeFromJsonElement(MutedWord.serializer(), el) }.getOrNull()
                        decoded != null && if (word.id != null && decoded.id != null) decoded.id == word.id else decoded.value == word.value
                    }.orEmpty()
                    prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref" }
                    prefs.add(
                        buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.actor.defs#mutedWordsPref"))
                            put("items", JsonArray(existingItems))
                        }
                    )
                },
            )
        }
    }

    fun setAdultContent(enabled: Boolean) {
        scope.launch {
            pushPreferencesTransform(
                localTransform = { it.copy(adultContentEnabled = enabled) },
                serverTransform = { prefs ->
                    prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#adultContentPref" }
                    prefs.add(
                        buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.actor.defs#adultContentPref"))
                            put("enabled", JsonPrimitive(enabled))
                        }
                    )
                },
            )
        }
    }

    fun setContentLabelVisibility(label: String, visibility: String, labelerDid: String? = null) {
        scope.launch {
            pushPreferencesTransform(
                localTransform = { st ->
                    if (labelerDid == null) {
                        val updatedMap = st.contentLabels.toMutableMap()
                        updatedMap[label] = visibility
                        if (label == "graphic-media") updatedMap["gore"] = visibility
                        st.copy(contentLabels = updatedMap)
                    } else {
                        val filtered = st.labelerContentLabels.filterNot {
                            it.labelerDid == labelerDid && it.label == label
                        }
                        st.copy(
                            labelerContentLabels = filtered + ContentLabelPref(
                                labelerDid = labelerDid,
                                label = label,
                                visibility = visibility,
                            )
                        )
                    }
                },
                serverTransform = { prefs ->
                    val targets = if (labelerDid == null && label == "graphic-media") listOf("graphic-media", "gore") else listOf(label)
                    prefs.removeAll { obj ->
                        if ((obj["\$type"] as? JsonPrimitive)?.content != "app.bsky.actor.defs#contentLabelPref") return@removeAll false
                        val objLabel = (obj["label"] as? JsonPrimitive)?.content
                        val objLabeler = (obj["labelerDid"] as? JsonPrimitive)?.content
                        objLabel in targets && objLabeler == labelerDid
                    }
                    for (t in targets) {
                        prefs.add(
                            buildJsonObject {
                                put("\$type", JsonPrimitive("app.bsky.actor.defs#contentLabelPref"))
                                if (labelerDid != null) put("labelerDid", JsonPrimitive(labelerDid))
                                put("label", JsonPrimitive(t))
                                put("visibility", JsonPrimitive(visibility))
                            }
                        )
                    }
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Moderation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp),
        ) {
            SectionHeader("Moderation tools")
            ModNavRow(
                icon = Icons.Filled.ChatBubbleOutline,
                title = "Interaction settings",
                subtitle = "Reply and quote post defaults",
                onClick = { onNavigate(AppRoute.ModerationInteractionSettings) },
            )
            ModNavRow(
                icon = Icons.Filled.FormatListBulleted,
                title = "Moderation lists",
                subtitle = "Mute or block curated lists",
                onClick = { onNavigate(AppRoute.ModerationModlists) },
            )
            ModNavRow(
                icon = Icons.Filled.VolumeOff,
                title = "Muted accounts",
                onClick = { onNavigate(AppRoute.ModerationMutedAccounts) },
            )
            ModNavRow(
                icon = Icons.Filled.Block,
                title = "Blocked accounts",
                onClick = { onNavigate(AppRoute.ModerationBlockedAccounts) },
            )
            ModNavRow(
                icon = Icons.Filled.Verified,
                title = "Verification settings",
                onClick = { onNavigate(AppRoute.ModerationVerificationSettings) },
            )
            ModNavRow(
                icon = Icons.Filled.Inbox,
                title = "Moderation inbox",
                subtitle = "Notices and submitted reports",
                onClick = { onNavigate(AppRoute.ModerationInbox) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.contrast100.toComposeColor())

            SectionHeader("Muted words & tags")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        placeholder = { Text("Enter a word or #tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { addMutedWord() },
                        enabled = newWord.isNotBlank() && !isSyncing,
                    ) {
                        Text("Add")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MutedWordDuration.entries.forEach { dur ->
                        FilterChip(
                            selected = selectedDuration == dur,
                            onClick = { selectedDuration = dur },
                            label = { Text(dur.label) },
                        )
                    }
                    FilterChip(
                        selected = tagsOnly,
                        onClick = { tagsOnly = !tagsOnly },
                        label = { Text(if (tagsOnly) "Tags only" else "Text & tags") },
                    )
                    FilterChip(
                        selected = excludeFollowing,
                        onClick = { excludeFollowing = !excludeFollowing },
                        label = { Text("Exclude following") },
                    )
                }

                if (modSettings.mutedWords.isEmpty()) {
                    Text(
                        text = "No muted words yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        modSettings.mutedWords.forEach { word ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.contrast25.toComposeColor())
                                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = word.value,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.contrast1000.toComposeColor(),
                                    )
                                    val scopeLabel = if (word.targets == listOf("tag")) "Tags only" else "Text & tags"
                                    val actorLabel = if (word.actorTarget == "exclude-following") " · Excl. following" else ""
                                    val expLabel = word.expiresAt?.let { " · " + LocalI18n.current.translate("Expires {0}", mapOf("0" to LocalI18n.current.formatDate(it))) } ?: " · Forever"
                                    Text(
                                        text = "$scopeLabel$actorLabel$expLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.contrast600.toComposeColor(),
                                    )
                                }
                                IconButton(onClick = { removeMutedWord(word) }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove muted word",
                                        tint = palette.negative500.toComposeColor(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.contrast100.toComposeColor())

            SectionHeader("Content filters")
            ListItem(
                headlineContent = { Text("Enable adult content") },
                supportingContent = { Text("Allow viewing adult-labeled media and posts") },
                trailingContent = {
                    Switch(
                        checked = modSettings.adultContentEnabled,
                        onCheckedChange = { setAdultContent(it) },
                    )
                },
            )

            CONTENT_FILTER_CATEGORIES.forEach { cat ->
                val lockedByAdult = cat.adultOnly && !modSettings.adultContentEnabled
                val effectiveVis = if (lockedByAdult) "hide" else (modSettings.contentLabels[cat.id] ?: cat.defaultVis)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(cat.title, style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor())
                    Text(cat.description, style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
                    VisibilitySegmentedSelector(
                        selected = effectiveVis,
                        enabled = !lockedByAdult && !isSyncing,
                        onSelect = { vis -> setContentLabelVisibility(cat.id, vis) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.contrast100.toComposeColor())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Subscribed labelers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.contrast1000.toComposeColor(),
                )
                TextButton(onClick = { showAddLabelerDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add labeler")
                }
            }

            session.subscribedLabelers.forEach { labeler ->
                val isExpanded = expandedLabelerDid == labeler.creator.did
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.contrast25.toComposeColor())
                        .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedLabelerDid = if (isExpanded) null else labeler.creator.did
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CaelumAvatar(
                            url = labeler.creator.avatar,
                            contentDescription = labeler.creator.displayName ?: labeler.creator.handle,
                            size = 40.dp,
                            shapeType = AvatarShape.RoundedSquare,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = labeler.creator.displayName?.takeIf { it.isNotBlank() } ?: "@${labeler.creator.handle}",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.contrast1000.toComposeColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "@${labeler.creator.handle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                                maxLines = 1,
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                        )
                    }

                    if (isExpanded) {
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!labeler.creator.description.isNullOrBlank()) {
                                Text(
                                    text = labeler.creator.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast700.toComposeColor(),
                                )
                            }
                            if (labeler.policies.labelValueDefinitions.isEmpty()) {
                                Text(
                                    text = "No custom label definitions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            } else {
                                labeler.policies.labelValueDefinitions.forEach { def ->
                                    val locale = def.locales.firstOrNull { it.lang.startsWith("en") } ?: def.locales.firstOrNull()
                                    val currentVis = modSettings.labelerContentLabels
                                        .firstOrNull { it.labelerDid == labeler.creator.did && it.label == def.identifier }
                                        ?.visibility
                                        ?: def.defaultSetting
                                        ?: "warn"
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = locale?.name ?: def.identifier,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = palette.contrast1000.toComposeColor(),
                                        )
                                        if (!locale?.description.isNullOrBlank()) {
                                            Text(
                                                text = locale?.description.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.contrast600.toComposeColor(),
                                            )
                                        }
                                        VisibilitySegmentedSelector(
                                            selected = currentVis,
                                            enabled = !isSyncing,
                                            onSelect = { vis ->
                                                setContentLabelVisibility(def.identifier, vis, labeler.creator.did)
                                            },
                                        )
                                    }
                                }
                            }
                            if (labeler.creator.did != SessionManager.BLUESKY_LABELER_DID) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            pushPreferencesTransform(
                                                localTransform = { st ->
                                                    st.copy(labelers = st.labelers.filterNot { it.did == labeler.creator.did })
                                                },
                                                serverTransform = { prefs ->
                                                    val existing = prefs.firstOrNull {
                                                        (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#labelersPref"
                                                    }
                                                    val filtered = (existing?.get("labelers") as? JsonArray)?.filterNot {
                                                        (it as? JsonObject)?.get("did")?.jsonPrimitive?.contentOrNull == labeler.creator.did
                                                    }.orEmpty()
                                                    prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#labelersPref" }
                                                    prefs.add(
                                                        buildJsonObject {
                                                            put("\$type", JsonPrimitive("app.bsky.actor.defs#labelersPref"))
                                                            put("labelers", JsonArray(filtered))
                                                        }
                                                    )
                                                },
                                            )
                                        }
                                    },
                                ) {
                                    Text("Unsubscribe labeler", color = palette.negative500.toComposeColor())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddLabelerDialog) {
        var handleOrDid by remember { mutableStateOf("") }
        var addError by remember { mutableStateOf<String?>(null) }
        var isAdding by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isAdding) showAddLabelerDialog = false },
            title = { Text("Subscribe to labeler") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = handleOrDid,
                        onValueChange = {
                            handleOrDid = it
                            addError = null
                        },
                        label = { Text("Labeler handle or DID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    addError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.negative500.toComposeColor())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = handleOrDid.isNotBlank() && !isAdding,
                    onClick = {
                        scope.launch {
                            isAdding = true
                            addError = null
                            runCatching {
                                val raw = handleOrDid.trim().removePrefix("@")
                                val did = if (raw.startsWith("did:")) raw else {
                                    container.identityResolver.resolveHandle(raw)
                                        ?: error("Could not resolve @$raw")
                                }
                                val services = container.api.getLabelerServices(listOf(did))
                                if (services.isEmpty()) error("Account is not a labeler service")
                                pushPreferencesTransform(
                                    localTransform = { st ->
                                        val existing = st.labelers.map { it.did }.toSet()
                                        val next = if (did in existing) st.labelers else st.labelers + LabelerPrefItem(did)
                                        st.copy(labelers = next)
                                    },
                                    serverTransform = { prefs ->
                                        val existing = prefs.firstOrNull {
                                            (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#labelersPref"
                                        }
                                        val items = ((existing?.get("labelers") as? JsonArray)?.toMutableList() ?: mutableListOf()).apply {
                                            if (none { (it as? JsonObject)?.get("did")?.jsonPrimitive?.contentOrNull == did }) {
                                                add(buildJsonObject { put("did", JsonPrimitive(did)) })
                                            }
                                        }
                                        prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#labelersPref" }
                                        prefs.add(
                                            buildJsonObject {
                                                put("\$type", JsonPrimitive("app.bsky.actor.defs#labelersPref"))
                                                put("labelers", JsonArray(items))
                                            }
                                        )
                                    },
                                )
                            }.onSuccess {
                                isAdding = false
                                showAddLabelerDialog = false
                            }.onFailure { ex ->
                                isAdding = false
                                addError = ex.message ?: "Failed to add labeler"
                            }
                        }
                    },
                ) {
                    Text("Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLabelerDialog = false }, enabled = !isAdding) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun VisibilitySegmentedSelector(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val options = listOf(
        "hide" to "Hide",
        "warn" to "Warn",
        "ignore" to "Show",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { idx, (wire, label) ->
            SegmentedButton(
                selected = selected == wire,
                onClick = { if (enabled) onSelect(wire) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalAlfPalette.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = palette.contrast1000.toComposeColor(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ModNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = palette.contrast700.toComposeColor())
        },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MutedAccountsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<ProfileView>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadMutes(refresh: Boolean = false) {
        isLoading = !refresh && accounts.isEmpty()
        isRefreshing = refresh
        runCatching {
            val res = container.api.getMutes()
            val list = res["mutes"]?.jsonArray?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(it) }.getOrNull()
            }.orEmpty()
            val nextCursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull
            list to nextCursor
        }.onSuccess { (list, next) ->
            accounts = list
            cursor = next
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(Unit) { loadMutes() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Muted accounts", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadMutes(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                accounts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("You haven’t muted any accounts.", color = palette.contrast600.toComposeColor())
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = accounts, key = { it.did }) { profile ->
                        val basic = ProfileViewBasic(
                            did = profile.did,
                            handle = profile.handle,
                            displayName = profile.displayName,
                            pronouns = profile.pronouns,
                            avatar = profile.avatar,
                            verification = profile.verification,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(AppRoute.Profile(profile.handle.ifBlank { profile.did })) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CaelumAvatar(url = profile.avatar, contentDescription = profile.displayName ?: profile.handle, size = 42.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                DisplayNameRow(profile = basic, showHandle = true)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching { container.api.unmuteActor(profile.did) }
                                            .onSuccess { accounts = accounts.filterNot { it.did == profile.did } }
                                    }
                                },
                            ) {
                                Text("Unmute")
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                    }
                    if (!cursor.isNullOrBlank()) {
                        item(key = "load_more_mutes") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { container.api.getMutes(cursor) }.onSuccess { res ->
                                                val more = res["mutes"]?.jsonArray?.mapNotNull {
                                                    runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(it) }.getOrNull()
                                                }.orEmpty()
                                                accounts = (accounts + more).distinctBy { it.did }
                                                cursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull
                                            }
                                        }
                                    },
                                ) {
                                    Text("Load more")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedAccountsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var accounts by remember { mutableStateOf<List<ProfileView>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadBlocks(refresh: Boolean = false) {
        isLoading = !refresh && accounts.isEmpty()
        isRefreshing = refresh
        runCatching {
            val res = container.api.getBlocks()
            val list = res["blocks"]?.jsonArray?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(it) }.getOrNull()
            }.orEmpty()
            val nextCursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull
            list to nextCursor
        }.onSuccess { (list, next) ->
            accounts = list
            cursor = next
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(Unit) { loadBlocks() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Blocked accounts", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadBlocks(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                accounts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("You haven’t blocked any accounts.", color = palette.contrast600.toComposeColor())
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = accounts, key = { it.did }) { profile ->
                        val basic = ProfileViewBasic(
                            did = profile.did,
                            handle = profile.handle,
                            displayName = profile.displayName,
                            pronouns = profile.pronouns,
                            avatar = profile.avatar,
                            verification = profile.verification,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(AppRoute.Profile(profile.handle.ifBlank { profile.did })) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CaelumAvatar(url = profile.avatar, contentDescription = profile.displayName ?: profile.handle, size = 42.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                DisplayNameRow(profile = basic, showHandle = true)
                            }
                            OutlinedButton(
                                onClick = {
                                    val myDid = session.currentDid ?: return@OutlinedButton
                                    val blockingUri = profile.viewer?.blocking ?: return@OutlinedButton
                                    val rkey = blockingUri.substringAfterLast('/')
                                    scope.launch {
                                        runCatching {
                                            container.api.deleteRecord(myDid, "app.bsky.graph.block", rkey)
                                        }.onSuccess {
                                            accounts = accounts.filterNot { it.did == profile.did }
                                        }
                                    }
                                },
                            ) {
                                Text("Unblock")
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                    }
                    if (!cursor.isNullOrBlank()) {
                        item(key = "load_more_blocks") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { container.api.getBlocks(cursor) }.onSuccess { res ->
                                                val more = res["blocks"]?.jsonArray?.mapNotNull {
                                                    runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(it) }.getOrNull()
                                                }.orEmpty()
                                                accounts = (accounts + more).distinctBy { it.did }
                                                cursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull
                                            }
                                        }
                                    },
                                ) {
                                    Text("Load more")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModlistsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var lists by remember { mutableStateOf<List<ListView>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadModlists(refresh: Boolean = false) {
        val myDid = session.currentDid ?: return
        isLoading = !refresh && lists.isEmpty()
        isRefreshing = refresh
        runCatching {
            val mutedRes = runCatching { container.xrpcClient.getJson("app.bsky.graph.getListMutes").jsonObject }.getOrNull()
            val blockedRes = runCatching { container.xrpcClient.getJson("app.bsky.graph.getListBlocks").jsonObject }.getOrNull()
            val ownLists = runCatching { container.api.getLists(myDid) }.getOrDefault(emptyList())
                .filter { it.purpose == "app.bsky.graph.defs#modlist" }

            val muted = mutedRes?.get("lists")?.jsonArray?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<ListView>(it) }.getOrNull()
            }.orEmpty()
            val blocked = blockedRes?.get("lists")?.jsonArray?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<ListView>(it) }.getOrNull()
            }.orEmpty()

            (muted + blocked + ownLists).distinctBy { it.uri }
        }.onSuccess {
            lists = it
        }
        isLoading = false
        isRefreshing = false
    }

    LaunchedEffect(session.currentDid) { loadModlists() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Moderation lists", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadModlists(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                lists.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No moderation lists subscribed or created.", color = palette.contrast600.toComposeColor())
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = lists, key = { it.uri }) { list ->
                        val repoDid = list.uri.removePrefix("at://").substringBefore('/')
                        val rkey = list.uri.substringAfterLast('/')
                        val isMuted = list.viewer?.muted == true
                        val isBlocked = !list.viewer?.blocked.isNullOrBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(AppRoute.ProfileList(repoDid, rkey)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CaelumAvatar(
                                url = list.avatar,
                                contentDescription = list.name,
                                size = 44.dp,
                                shapeType = AvatarShape.RoundedSquare,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(list.name, style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor())
                                Text(
                                    text = "by @${list.creator.handle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                                val status = listOfNotNull(
                                    if (isMuted) "Muted" else null,
                                    if (isBlocked) "Blocked" else null,
                                ).joinToString(" · ")
                                if (status.isNotBlank()) {
                                    Text(status, style = MaterialTheme.typography.labelSmall, color = palette.primary500.toComposeColor())
                                }
                            }
                            if (isMuted) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching {
                                                container.xrpcClient.postJson(
                                                    "app.bsky.graph.unmuteActorList",
                                                    buildJsonObject { put("list", JsonPrimitive(list.uri)) },
                                                )
                                                loadModlists(refresh = true)
                                            }
                                        }
                                    },
                                ) {
                                    Text("Unmute")
                                }
                            } else if (isBlocked) {
                                OutlinedButton(
                                    onClick = {
                                        val myDid = session.currentDid ?: return@OutlinedButton
                                        val blockUri = list.viewer?.blocked ?: return@OutlinedButton
                                        scope.launch {
                                            runCatching {
                                                container.api.deleteRecord(myDid, "app.bsky.graph.listblock", blockUri.substringAfterLast('/'))
                                                loadModlists(refresh = true)
                                            }
                                        }
                                    },
                                ) {
                                    Text("Unblock")
                                }
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberAccountCoroutineScope()

    var replyGate by remember { mutableStateOf("all") } // all | nobody | custom
    var allowMentioned by remember { mutableStateOf(false) }
    var allowFollowers by remember { mutableStateOf(false) }
    var allowFollowing by remember { mutableStateOf(false) }
    var allowQuotePosts by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching {
            val prefs = container.api.getActorPreferences()
            val interaction = prefs.firstOrNull {
                (it as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull == "app.bsky.actor.defs#postInteractionSettingsPref"
            } as? JsonObject
            if (interaction != null) {
                val rules = interaction["threadgateAllowRules"] as? JsonArray
                if (rules == null) {
                    replyGate = "all"
                } else if (rules.isEmpty()) {
                    replyGate = "nobody"
                } else {
                    replyGate = "custom"
                    val types = rules.mapNotNull { (it as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull }.toSet()
                    allowMentioned = "app.bsky.feed.threadgate#mentionRule" in types
                    allowFollowers = "app.bsky.feed.threadgate#followerRule" in types
                    allowFollowing = "app.bsky.feed.threadgate#followingRule" in types
                }
                val postgateRules = interaction["postgateEmbeddingRules"] as? JsonArray
                allowQuotePosts = postgateRules?.none {
                    (it as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull == "app.bsky.feed.postgate#disableRule"
                } ?: true
            }
        }
        isLoading = false
    }

    fun saveSettings() {
        scope.launch {
            isSaving = true
            runCatching {
                val raw = container.api.getActorPreferences()
                    .mapNotNull { it as? JsonObject }
                    .filterNot {
                        (it["\$type"] as? JsonPrimitive)?.contentOrNull == "app.bsky.actor.defs#postInteractionSettingsPref"
                    }
                    .toMutableList()

                val newPref = buildJsonObject {
                    put("\$type", JsonPrimitive("app.bsky.actor.defs#postInteractionSettingsPref"))
                    when (replyGate) {
                        "nobody" -> put("threadgateAllowRules", JsonArray(emptyList()))
                        "custom" -> put(
                            "threadgateAllowRules",
                            buildJsonArray {
                                if (allowMentioned) add(buildJsonObject { put("\$type", JsonPrimitive("app.bsky.feed.threadgate#mentionRule")) })
                                if (allowFollowers) add(buildJsonObject { put("\$type", JsonPrimitive("app.bsky.feed.threadgate#followerRule")) })
                                if (allowFollowing) add(buildJsonObject { put("\$type", JsonPrimitive("app.bsky.feed.threadgate#followingRule")) })
                            }
                        )
                    }
                    if (!allowQuotePosts) {
                        put(
                            "postgateEmbeddingRules",
                            buildJsonArray {
                                add(buildJsonObject { put("\$type", JsonPrimitive("app.bsky.feed.postgate#disableRule")) })
                            }
                        )
                    } else {
                        put("postgateEmbeddingRules", JsonArray(emptyList()))
                    }
                }
                raw.add(newPref)
                container.api.putActorPreferences(JsonArray(raw))
                Toast.makeText(context, "Interaction settings saved", Toast.LENGTH_SHORT).show()
            }.onFailure { ex ->
                Toast.makeText(context, ex.message ?: "Failed to save", Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Interaction settings", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Default reply settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                Column {
                    listOf(
                        "all" to "Everyone can reply",
                        "nobody" to "Nobody can reply",
                        "custom" to "Specific groups",
                    ).forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { replyGate = key }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = replyGate == key, onClick = { replyGate = key })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (replyGate == "custom") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.contrast25.toComposeColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allowMentioned = !allowMentioned }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = allowMentioned, onCheckedChange = { allowMentioned = it })
                            Text("Mentioned users")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allowFollowing = !allowFollowing }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = allowFollowing, onCheckedChange = { allowFollowing = it })
                            Text("Users you follow")
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allowFollowers = !allowFollowers }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = allowFollowers, onCheckedChange = { allowFollowers = it })
                            Text("Your followers")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                ListItem(
                    headlineContent = { Text("Allow quote posts") },
                    supportingContent = { Text("Allow others to quote your new posts by default") },
                    trailingContent = {
                        Switch(checked = allowQuotePosts, onCheckedChange = { allowQuotePosts = it })
                    },
                )
            }

            Button(
                onClick = { saveSettings() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSaving) "Saving…" else "Save defaults")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerificationSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberAccountCoroutineScope()

    var hideBadges by remember(session.currentDid) { mutableStateOf(false) }
    var isVerified by remember(session.currentDid) { mutableStateOf(false) }
    var isTrustedVerifier by remember(session.currentDid) { mutableStateOf(false) }
    var isLoading by remember(session.currentDid) { mutableStateOf(true) }
    var isSaving by remember(session.currentDid) { mutableStateOf(false) }

    LaunchedEffect(session.currentDid) {
        val myDid = session.currentDid ?: return@LaunchedEffect
        isLoading = true
        runCatching {
            hideBadges = container.preferences.getModerationSettings(myDid).hideVerificationBadges
            val prefs = container.api.getActorPreferences()
            val verPref = prefs.firstOrNull {
                (it as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull == "app.bsky.actor.defs#verificationPrefs"
            } as? JsonObject
            hideBadges = (verPref?.get("hideBadges") as? JsonPrimitive)?.booleanOrNull ?: false
            container.preferences.updateModerationSettings(myDid) {
                it.copy(hideVerificationBadges = hideBadges)
            }
            val profile = container.api.getProfile(myDid)
            isVerified = profile.verification?.verifiedStatus == "valid"
            isTrustedVerifier = profile.verification?.trustedVerifierStatus == "valid"
        }
        isLoading = false
    }

    fun toggleHideBadges(hide: Boolean) {
        val did = session.currentDid ?: return
        val previous = hideBadges
        hideBadges = hide
        isSaving = true
        scope.launch {
            runCatching {
                val raw = container.api.getActorPreferences()
                    .mapNotNull { it as? JsonObject }
                    .filterNot {
                        (it["\$type"] as? JsonPrimitive)?.contentOrNull == "app.bsky.actor.defs#verificationPrefs"
                    }
                    .toMutableList()
                raw.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#verificationPrefs"))
                        put("hideBadges", JsonPrimitive(hide))
                    }
                )
                container.api.putActorPreferences(JsonArray(raw))
                container.preferences.updateModerationSettings(did) {
                    it.copy(hideVerificationBadges = hide)
                }
            }.onFailure { ex ->
                hideBadges = previous
                Toast.makeText(context, ex.message ?: "Failed to update verification settings", Toast.LENGTH_SHORT).show()
            }
            isSaving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Verification", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Account verification status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            isTrustedVerifier -> "Trusted Verifier"
                            isVerified -> "Verified Account"
                            else -> "Standard Account"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.primary500.toComposeColor(),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.contrast25.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
            ) {
                ListItem(
                    headlineContent = { Text("Hide verification badges") },
                    supportingContent = { Text("Hide blue verification checkmarks across the app") },
                    trailingContent = {
                        Switch(checked = hideBadges, onCheckedChange = { toggleHideBadges(it) }, enabled = !isSaving)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationInboxScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()

    var accountStatus by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(session.currentDid) {
        isLoading = true
        runCatching {
            val sess = container.api.getSession()
            accountStatus = (sess["status"] as? JsonPrimitive)?.contentOrNull
            val svc = session.subscribedLabelers.firstOrNull()?.creator?.did ?: SessionManager.BLUESKY_LABELER_DID
            val res = runCatching {
                container.xrpcClient.getJson(
                    nsid = "tools.ozone.moderation.queryEvents",
                    params = listOf("limit" to "25"),
                    proxyServiceDid = "$svc#atproto_labeler",
                ).jsonObject
            }.getOrNull()
            events = res?.get("events")?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Moderation inbox", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { onNavigate(AppRoute.ModerationInboxSettings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Moderation inbox settings")
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            events.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = palette.contrast400.toComposeColor(), modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (accountStatus.isNullOrBlank()) "No active moderation notices" else "Account status: $accountStatus",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Moderation events and appeal updates for your account appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(events) { ev ->
                    val id = (ev["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    val createdAt = (ev["createdAt"] as? JsonPrimitive)?.contentOrNull
                    val svc = session.subscribedLabelers.firstOrNull()?.creator?.did ?: SessionManager.BLUESKY_LABELER_DID
                    ListItem(
                        headlineContent = { Text("Report #$id") },
                        supportingContent = { Text(LocalI18n.current.formatRelativeShort(createdAt)) },
                        modifier = Modifier.clickable {
                            onNavigate(AppRoute.ModerationInboxReportDetails(svc, id))
                        },
                    )
                    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationInboxSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Inbox settings", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Active moderation services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            session.subscribedLabelers.forEach { labeler ->
                ListItem(
                    leadingContent = {
                        CaelumAvatar(
                            url = labeler.creator.avatar,
                            contentDescription = labeler.creator.displayName ?: labeler.creator.handle,
                            size = 38.dp,
                            shapeType = AvatarShape.RoundedSquare,
                        )
                    },
                    headlineContent = { Text(labeler.creator.displayName ?: "@${labeler.creator.handle}") },
                    supportingContent = { Text(labeler.creator.did) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationInboxReportDetailsScreen(
    moderationService: String,
    reportId: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    var eventJson by remember { mutableStateOf<JsonObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(moderationService, reportId) {
        isLoading = true
        eventJson = runCatching {
            container.xrpcClient.getJson(
                nsid = "tools.ozone.moderation.getEvent",
                params = listOf("id" to reportId),
                proxyServiceDid = "$moderationService#atproto_labeler",
            ).jsonObject
        }.getOrNull()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Report #$reportId", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Service: $moderationService", style = MaterialTheme.typography.bodyMedium)
                Text("Report ID: $reportId", style = MaterialTheme.typography.bodyMedium)
                eventJson?.let {
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = palette.contrast700.toComposeColor())
                } ?: Text(
                    "Details are not available for this report.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast600.toComposeColor(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModerationInboxNoticeDetailsScreen(
    subjectJson: String,
    onBack: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val parsed = remember(subjectJson) {
        runCatching { AtProtoJson.parseToJsonElement(subjectJson).jsonObject }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Moderation notice", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (parsed != null) {
                parsed.entries.forEach { (k, v) ->
                    Text(
                        text = "$k: ${v.toString().trim('\"')}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast900.toComposeColor(),
                    )
                }
            } else {
                Text(
                    text = subjectJson.ifBlank { "No notice details available." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast700.toComposeColor(),
                )
            }
        }
    }
}

private data class ReportReasonOption(
    val reasonType: String,
    val title: String,
    val description: String,
)

private val REPORT_REASON_OPTIONS = listOf(
    ReportReasonOption("com.atproto.moderation.defs#reasonSpam", "Spam", "Excessive mentions, replies, or promotional spam"),
    ReportReasonOption("com.atproto.moderation.defs#reasonViolation", "Rule Violation", "Hate speech, threats, illegal content, or self-harm"),
    ReportReasonOption("com.atproto.moderation.defs#reasonMisleading", "Misleading", "Impersonation, scams, or false information"),
    ReportReasonOption("com.atproto.moderation.defs#reasonSexual", "Sexual Content", "Unwanted or unlabeled adult/sexual content"),
    ReportReasonOption("com.atproto.moderation.defs#reasonRude", "Harassment / Rude", "Targeted harassment, abuse, or trolling"),
    ReportReasonOption("com.atproto.moderation.defs#reasonOther", "Other", "An issue not covered by the categories above"),
)

@Composable
fun ReportPostOrActorDialog(
    post: PostView?,
    actorDid: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedReason by remember { mutableStateOf(REPORT_REASON_OPTIONS.first().reasonType) }
    var details by remember { mutableStateOf("") }
    var selectedLabelerDid by remember {
        mutableStateOf(session.subscribedLabelers.firstOrNull()?.creator?.did ?: SessionManager.BLUESKY_LABELER_DID)
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val subject: JsonObject? = remember(post, actorDid) {
        when {
            post != null -> buildJsonObject {
                put("\$type", JsonPrimitive("com.atproto.repo.strongRef"))
                put("uri", JsonPrimitive(post.uri))
                put("cid", JsonPrimitive(post.cid))
            }
            !actorDid.isNullOrBlank() -> buildJsonObject {
                put("\$type", JsonPrimitive("com.atproto.admin.defs#repoRef"))
                put("did", JsonPrimitive(actorDid))
            }
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(if (post != null) "Report post" else "Report account")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                REPORT_REASON_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) { selectedReason = option.reasonType }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = selectedReason == option.reasonType,
                            onClick = { if (!isSubmitting) selectedReason = option.reasonType },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall)
                            Text(option.description, style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
                        }
                    }
                }

                if (session.subscribedLabelers.size > 1) {
                    Text(
                        text = "Send report to",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.contrast700.toComposeColor(),
                    )
                    session.subscribedLabelers.forEach { labeler ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSubmitting) { selectedLabelerDid = labeler.creator.did }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedLabelerDid == labeler.creator.did,
                                onClick = { if (!isSubmitting) selectedLabelerDid = labeler.creator.did },
                            )
                            Text(
                                text = labeler.creator.displayName ?: "@${labeler.creator.handle}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Additional details (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                errorMsg?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.negative500.toComposeColor(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting && subject != null,
                onClick = {
                    val subj = subject ?: return@TextButton
                    scope.launch {
                        isSubmitting = true
                        errorMsg = null
                        runCatching {
                            container.api.createModerationReport(
                                reasonType = selectedReason,
                                subject = subj,
                                reason = details.trim().ifBlank { null },
                                labelerDid = selectedLabelerDid,
                            )
                        }.onSuccess {
                            isSubmitting = false
                            Toast.makeText(context, "Report submitted. Thank you.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }.onFailure { ex ->
                            isSubmitting = false
                            errorMsg = ex.message ?: "Failed to submit report"
                        }
                    }
                },
            ) {
                Text(if (isSubmitting) "Submitting…" else "Submit report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}
