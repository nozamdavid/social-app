package am.noz.caelum.ui.lists

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.time.Instant

private data class ExistingListItemRef(
    val uri: String,
    val subjectDid: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StarterPackWizardScreen(
    editRkey: String? = null,
    onBack: () -> Unit,
    onCompleted: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isEditMode = !editRkey.isNullOrBlank()
    var currentStep by remember { mutableIntStateOf(0) } // 0: Name/Desc, 1: Members, 2: Feeds

    var isInitializing by remember(editRkey) { mutableStateOf(isEditMode) }
    var existingListUri by remember(editRkey) { mutableStateOf<String?>(null) }
    var existingCreatedAt by remember(editRkey) { mutableStateOf<String?>(null) }
    var existingListItems by remember(editRkey) { mutableStateOf<List<ExistingListItemRef>>(emptyList()) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var selectedFeeds by remember { mutableStateOf<List<GeneratorView>>(emptyList()) }

    // Step 2: Member search
    var memberQuery by remember { mutableStateOf("") }
    var memberSearchResults by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var isSearchingMembers by remember { mutableStateOf(false) }

    // Step 3: Feed search / popular
    var feedQuery by remember { mutableStateOf("") }
    var popularFeeds by remember { mutableStateOf<List<GeneratorView>>(emptyList()) }
    var isSearchingFeeds by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }

    // Prefill when editing an existing starter pack
    LaunchedEffect(editRkey, session.currentDid) {
        val rkey = editRkey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val did = session.currentDid ?: return@LaunchedEffect
        isInitializing = true
        runCatching {
            val spUri = "at://$did/app.bsky.graph.starterpack/$rkey"
            val spJson = container.api.getStarterPack(spUri)
            val sp = (spJson["starterPack"] as? JsonObject) ?: spJson
            val rec = sp["record"] as? JsonObject
            name = (rec?.get("name") as? JsonPrimitive)?.content.orEmpty()
            description = (rec?.get("description") as? JsonPrimitive)?.content.orEmpty()
            existingCreatedAt = (rec?.get("createdAt") as? JsonPrimitive)?.content

            val listUri = (sp["list"] as? JsonObject)?.get("uri")?.let { (it as? JsonPrimitive)?.content }
                ?: (rec?.get("list") as? JsonPrimitive)?.content
            existingListUri = listUri

            val feeds = (sp["feeds"] as? JsonArray)?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(it) }.getOrNull()
            }.orEmpty()
            selectedFeeds = feeds.take(3)

            if (!listUri.isNullOrBlank()) {
                val loadedRefs = mutableListOf<ExistingListItemRef>()
                val loadedProfiles = mutableListOf<ProfileViewBasic>()
                var cursor: String? = null
                var pages = 0
                do {
                    val listJson = container.api.getList(listUri, cursor = cursor)
                    val items = listJson["items"]?.jsonArray.orEmpty()
                    for (el in items) {
                        val obj = el as? JsonObject ?: continue
                        val itemUri = (obj["uri"] as? JsonPrimitive)?.content ?: continue
                        val subj = obj["subject"] ?: continue
                        val p = runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subj) }.getOrNull() ?: continue
                        loadedRefs.add(ExistingListItemRef(uri = itemUri, subjectDid = p.did))
                        if (p.did != did) {
                            loadedProfiles.add(
                                ProfileViewBasic(
                                    did = p.did,
                                    handle = p.handle,
                                    displayName = p.displayName,
                                    pronouns = p.pronouns,
                                    avatar = p.avatar,
                                    associated = p.associated,
                                    viewer = p.viewer,
                                    labels = p.labels,
                                    verification = p.verification,
                                    status = p.status,
                                ),
                            )
                        }
                    }
                    cursor = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    pages++
                } while (!cursor.isNullOrBlank() && items.isNotEmpty() && pages < 5)

                existingListItems = loadedRefs
                selectedMembers = loadedProfiles.distinctBy { it.did }
            }
        }.onFailure { ex ->
            snackbarHostState.showSnackbar(ex.message ?: "Failed to load starter pack for editing")
        }
        isInitializing = false
    }

    // Member search / default suggestions
    LaunchedEffect(memberQuery, currentStep) {
        if (currentStep != 1) return@LaunchedEffect
        val q = memberQuery.trim()
        if (q.isNotEmpty()) {
            delay(250L)
            isSearchingMembers = true
            memberSearchResults = runCatching {
                container.api.searchActorsTypeahead(q, limit = 15)
            }.getOrDefault(emptyList())
            isSearchingMembers = false
        } else {
            isSearchingMembers = true
            memberSearchResults = runCatching {
                container.api.getSuggestions(limit = 20).actors.map { p ->
                    ProfileViewBasic(
                        did = p.did,
                        handle = p.handle,
                        displayName = p.displayName,
                        pronouns = p.pronouns,
                        avatar = p.avatar,
                        associated = p.associated,
                        viewer = p.viewer,
                        labels = p.labels,
                        verification = p.verification,
                        status = p.status,
                    )
                }
            }.getOrDefault(emptyList())
            isSearchingMembers = false
        }
    }

    // Feed search / popular feeds
    LaunchedEffect(feedQuery, currentStep) {
        if (currentStep != 2) return@LaunchedEffect
        val q = feedQuery.trim()
        if (q.isNotEmpty()) delay(250L)
        isSearchingFeeds = true
        popularFeeds = runCatching {
            val fetched = container.api.getPopularFeedGenerators(query = q.ifBlank { null })
            if (q.isBlank()) {
                (session.feedGenerators.values.toList() + fetched).distinctBy { it.uri }
            } else {
                fetched
            }
        }.getOrDefault(emptyList())
        isSearchingFeeds = false
    }

    fun submitStarterPack() {
        val did = session.currentDid ?: return
        if (isSubmitting) return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val cleanDesc = description.trim()

        isSubmitting = true
        scope.launch {
            runCatching {
                val nowIso = Instant.now().toString()
                val feedRefs = buildJsonArray {
                    for (f in selectedFeeds.take(3)) {
                        add(buildJsonObject { put("uri", JsonPrimitive(f.uri)) })
                    }
                }

                if (isEditMode && editRkey != null) {
                    val listUri = existingListUri ?: error("Starter pack list URI is missing")

                    // Delete removed list items
                    val selectedDids = selectedMembers.map { it.did }.toSet()
                    val removed = existingListItems.filter { it.subjectDid != did && it.subjectDid !in selectedDids }
                    for (chunk in removed.chunked(50)) {
                        val writes = buildJsonArray {
                            for (item in chunk) {
                                add(
                                    buildJsonObject {
                                        put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#delete"))
                                        put("collection", JsonPrimitive("app.bsky.graph.listitem"))
                                        put("rkey", JsonPrimitive(item.uri.substringAfterLast('/')))
                                    },
                                )
                            }
                        }
                        container.xrpcClient.postJson(
                            "com.atproto.repo.applyWrites",
                            buildJsonObject {
                                put("repo", JsonPrimitive(did))
                                put("writes", writes)
                            },
                        )
                    }

                    // Create newly added list items
                    val existingDids = existingListItems.map { it.subjectDid }.toSet()
                    val added = selectedMembers.filter { it.did !in existingDids }
                    for (chunk in added.chunked(50)) {
                        val writes = buildJsonArray {
                            for (p in chunk) {
                                add(
                                    buildJsonObject {
                                        put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#create"))
                                        put("collection", JsonPrimitive("app.bsky.graph.listitem"))
                                        put(
                                            "value",
                                            buildJsonObject {
                                                put("\$type", JsonPrimitive("app.bsky.graph.listitem"))
                                                put("subject", JsonPrimitive(p.did))
                                                put("list", JsonPrimitive(listUri))
                                                put("createdAt", JsonPrimitive(nowIso))
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        container.xrpcClient.postJson(
                            "com.atproto.repo.applyWrites",
                            buildJsonObject {
                                put("repo", JsonPrimitive(did))
                                put("writes", writes)
                            },
                        )
                    }

                    val spRecord = buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.graph.starterpack"))
                        put("name", JsonPrimitive(cleanName))
                        if (cleanDesc.isNotEmpty()) put("description", JsonPrimitive(cleanDesc))
                        put("list", JsonPrimitive(listUri))
                        if (selectedFeeds.isNotEmpty()) put("feeds", feedRefs)
                        put("createdAt", JsonPrimitive(existingCreatedAt ?: nowIso))
                        put("updatedAt", JsonPrimitive(nowIso))
                    }
                    container.api.putRecord(did, "app.bsky.graph.starterpack", editRkey, spRecord)
                    val handleOrDid = session.currentAccount?.handle?.ifBlank { did } ?: did
                    AppRoute.StarterPack(handleOrDid, editRkey)
                } else {
                    // Step 1: Create referencelist
                    val listRecord = buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.graph.list"))
                        put("purpose", JsonPrimitive("app.bsky.graph.defs#referencelist"))
                        put("name", JsonPrimitive(cleanName))
                        if (cleanDesc.isNotEmpty()) put("description", JsonPrimitive(cleanDesc))
                        put("createdAt", JsonPrimitive(nowIso))
                    }
                    val listRes = container.api.createRecord(did, "app.bsky.graph.list", listRecord)

                    // Step 2: Create listitems in chunks of 50
                    val membersToWrite = selectedMembers.distinctBy { it.did }
                    for (chunk in membersToWrite.chunked(50)) {
                        val writes = buildJsonArray {
                            for (p in chunk) {
                                add(
                                    buildJsonObject {
                                        put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#create"))
                                        put("collection", JsonPrimitive("app.bsky.graph.listitem"))
                                        put(
                                            "value",
                                            buildJsonObject {
                                                put("\$type", JsonPrimitive("app.bsky.graph.listitem"))
                                                put("subject", JsonPrimitive(p.did))
                                                put("list", JsonPrimitive(listRes.uri))
                                                put("createdAt", JsonPrimitive(nowIso))
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        container.xrpcClient.postJson(
                            "com.atproto.repo.applyWrites",
                            buildJsonObject {
                                put("repo", JsonPrimitive(did))
                                put("writes", writes)
                            },
                        )
                    }

                    // Step 3: Create starterpack record
                    val spRecord = buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.graph.starterpack"))
                        put("name", JsonPrimitive(cleanName))
                        if (cleanDesc.isNotEmpty()) put("description", JsonPrimitive(cleanDesc))
                        put("list", JsonPrimitive(listRes.uri))
                        if (selectedFeeds.isNotEmpty()) put("feeds", feedRefs)
                        put("createdAt", JsonPrimitive(nowIso))
                    }
                    val spRes = container.api.createRecord(did, "app.bsky.graph.starterpack", spRecord)
                    val rkey = spRes.uri.substringAfterLast('/')
                    val handleOrDid = session.currentAccount?.handle?.ifBlank { did } ?: did
                    AppRoute.StarterPack(handleOrDid, rkey)
                }
            }.onSuccess { targetRoute ->
                onCompleted(targetRoute)
            }.onFailure { ex ->
                snackbarHostState.showSnackbar(ex.message ?: "Failed to save starter pack")
            }
            isSubmitting = false
        }
    }

    val stepTitle = when (currentStep) {
        0 -> if (isEditMode) "Edit Starter Pack" else "Create Starter Pack"
        1 -> "Choose People (${selectedMembers.size})"
        else -> "Choose Feeds (${selectedFeeds.size}/3)"
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentStep > 0) currentStep-- else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text(stepTitle, fontWeight = FontWeight.SemiBold) },
                )
                LinearProgressIndicator(
                    progress = { (currentStep + 1) / 3f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            enabled = !isSubmitting,
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    when (currentStep) {
                        0 -> {
                            Button(
                                onClick = { currentStep = 1 },
                                enabled = name.trim().isNotEmpty() && !isInitializing,
                            ) {
                                Text("Next: People")
                            }
                        }
                        1 -> {
                            Button(
                                onClick = { currentStep = 2 },
                                enabled = selectedMembers.isNotEmpty(),
                            ) {
                                Text("Next: Feeds")
                            }
                        }
                        else -> {
                            Button(
                                onClick = ::submitStarterPack,
                                enabled = !isSubmitting && name.trim().isNotEmpty(),
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Saving…")
                                } else {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isEditMode) "Save changes" else "Create pack")
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isInitializing) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (currentStep) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Give your starter pack a clear name and optional description to help people discover a community.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast700.toComposeColor(),
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 50) name = it },
                            label = { Text("Starter pack name") },
                            supportingText = { Text("${name.length}/50") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { if (it.length <= 300) description = it },
                            label = { Text("Description (optional)") },
                            supportingText = { Text("${description.length}/300") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = memberQuery,
                                onValueChange = { memberQuery = it },
                                placeholder = { Text("Search people to include…") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (selectedMembers.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    selectedMembers.forEach { member ->
                                        InputChip(
                                            selected = true,
                                            onClick = {
                                                selectedMembers = selectedMembers.filterNot { it.did == member.did }
                                            },
                                            label = {
                                                Text(
                                                    text = member.displayName?.takeIf { it.isNotBlank() } ?: "@${member.handle}",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)

                        if (isSearchingMembers && memberSearchResults.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(items = memberSearchResults, key = { it.did }) { profile ->
                                    val isSelected = selectedMembers.any { it.did == profile.did }
                                    val canAddMore = selectedMembers.size < 150
                                    ActorListRow(
                                        profile = profile,
                                        onClick = {
                                            selectedMembers = if (isSelected) {
                                                selectedMembers.filterNot { it.did == profile.did }
                                            } else if (canAddMore) {
                                                selectedMembers + profile
                                            } else {
                                                selectedMembers
                                            }
                                        },
                                        trailingContent = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedMembers = if (!checked) {
                                                        selectedMembers.filterNot { it.did == profile.did }
                                                    } else if (canAddMore && !isSelected) {
                                                        selectedMembers + profile
                                                    } else {
                                                        selectedMembers
                                                    }
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Optionally include up to 3 custom feeds (${selectedFeeds.size}/3 selected).",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                            )
                            OutlinedTextField(
                                value = feedQuery,
                                onValueChange = { feedQuery = it },
                                placeholder = { Text("Search feeds…") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)

                        if (isSearchingFeeds && popularFeeds.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(items = popularFeeds, key = { it.uri }) { gen ->
                                    val isSelected = selectedFeeds.any { it.uri == gen.uri }
                                    val canSelect = isSelected || selectedFeeds.size < 3
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = canSelect) {
                                                selectedFeeds = if (isSelected) {
                                                    selectedFeeds.filterNot { it.uri == gen.uri }
                                                } else if (selectedFeeds.size < 3) {
                                                    selectedFeeds + gen
                                                } else {
                                                    selectedFeeds
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CaelumAvatar(
                                            url = gen.avatar,
                                            contentDescription = gen.displayName,
                                            size = 40.dp,
                                            shapeType = AvatarShape.RoundedSquare,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = gen.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = palette.contrast1000.toComposeColor(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "by @${gen.creator.handle}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = palette.contrast600.toComposeColor(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Checkbox(
                                            checked = isSelected,
                                            enabled = canSelect,
                                            onCheckedChange = { checked ->
                                                selectedFeeds = if (!checked) {
                                                    selectedFeeds.filterNot { it.uri == gen.uri }
                                                } else if (selectedFeeds.size < 3 && !isSelected) {
                                                    selectedFeeds + gen
                                                } else {
                                                    selectedFeeds
                                                }
                                            },
                                        )
                                    }
                                    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
