package am.noz.caelum.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ChatNotificationPref
import am.noz.caelum.core.model.NotificationPrefEntry
import am.noz.caelum.core.model.NotificationPreferencesResponse
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.core.network.PushGatewayManager
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private data class NotifCategorySpec(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val supportsIncludeFilter: Boolean,
    val isChat: Boolean = false,
)

private val NOTIFICATION_CATEGORIES = listOf(
    NotifCategorySpec("like", "Likes", Icons.Filled.Favorite, supportsIncludeFilter = true),
    NotifCategorySpec("follow", "New followers", Icons.Filled.PersonAdd, supportsIncludeFilter = true),
    NotifCategorySpec("reply", "Replies", Icons.Filled.Reply, supportsIncludeFilter = true),
    NotifCategorySpec("mention", "Mentions", Icons.Filled.AlternateEmail, supportsIncludeFilter = true),
    NotifCategorySpec("quote", "Quotes", Icons.Filled.FormatQuote, supportsIncludeFilter = true),
    NotifCategorySpec("repost", "Reposts", Icons.Filled.Repeat, supportsIncludeFilter = true),
    NotifCategorySpec("likeViaRepost", "Likes of your reposts", Icons.Filled.Favorite, supportsIncludeFilter = true),
    NotifCategorySpec("repostViaRepost", "Reposts of your reposts", Icons.Filled.Repeat, supportsIncludeFilter = true),
    NotifCategorySpec("chat", "Direct messages", Icons.Filled.ChatBubbleOutline, supportsIncludeFilter = true, isChat = true),
    NotifCategorySpec("subscribedPost", "Activity from subscribed accounts", Icons.Filled.NotificationsActive, supportsIncludeFilter = false),
    NotifCategorySpec("starterpackJoined", "Starter pack joins", Icons.Filled.DynamicFeed, supportsIncludeFilter = false),
    NotifCategorySpec("verified", "Verification updates", Icons.Filled.Verified, supportsIncludeFilter = false),
    NotifCategorySpec("unverified", "Unverification updates", Icons.Filled.Notifications, supportsIncludeFilter = false),
)

private fun entryForKey(prefs: NotificationPreferencesResponse, key: String): NotificationPrefEntry = when (key) {
    "like" -> prefs.like
    "follow" -> prefs.follow
    "reply" -> prefs.reply
    "mention" -> prefs.mention
    "quote" -> prefs.quote
    "repost" -> prefs.repost
    "likeViaRepost" -> prefs.likeViaRepost
    "repostViaRepost" -> prefs.repostViaRepost
    "starterpackJoined" -> prefs.starterpackJoined
    "subscribedPost" -> prefs.subscribedPost
    "verified" -> prefs.verified
    "unverified" -> prefs.unverified
    "chat" -> NotificationPrefEntry(
        include = prefs.chat?.include ?: "all",
        list = true,
        push = prefs.chat?.push ?: true,
    )
    else -> NotificationPrefEntry()
}

private fun withUpdatedEntry(
    prefs: NotificationPreferencesResponse,
    key: String,
    entry: NotificationPrefEntry,
): NotificationPreferencesResponse = when (key) {
    "like" -> prefs.copy(like = entry)
    "follow" -> prefs.copy(follow = entry)
    "reply" -> prefs.copy(reply = entry)
    "mention" -> prefs.copy(mention = entry)
    "quote" -> prefs.copy(quote = entry)
    "repost" -> prefs.copy(repost = entry)
    "likeViaRepost" -> prefs.copy(likeViaRepost = entry)
    "repostViaRepost" -> prefs.copy(repostViaRepost = entry)
    "starterpackJoined" -> prefs.copy(starterpackJoined = entry)
    "subscribedPost" -> prefs.copy(subscribedPost = entry)
    "verified" -> prefs.copy(verified = entry)
    "unverified" -> prefs.copy(unverified = entry)
    "chat" -> prefs.copy(
        chat = ChatNotificationPref(
            include = entry.include ?: "all",
            push = entry.push,
        )
    )
    else -> prefs
}

private fun describeNotificationSummary(spec: NotifCategorySpec, entry: NotificationPrefEntry): String {
    if (!entry.push && (spec.isChat || !entry.list)) return "None"
    val filterLabel = when {
        spec.isChat -> if (entry.include == "accepted") "Accepted chats" else "All"
        !spec.supportsIncludeFilter -> null
        entry.include == "follows" -> "Follows"
        else -> "All"
    }
    val channels = buildList {
        if (entry.push) add("Push")
        if (!spec.isChat && entry.list) add("In-app")
    }.joinToString(", ").ifEmpty { "None" }
    return if (filterLabel != null) "$filterLabel · $channels" else channels
}

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    var prefs by remember { mutableStateOf<NotificationPreferencesResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var editingSpec by remember { mutableStateOf<NotifCategorySpec?>(null) }

    fun checkOsPushGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    var osPushGranted by remember(context) { mutableStateOf(checkOsPushGranted()) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                osPushGranted = checkOsPushGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(sessionState.currentDid) {
        loading = true
        errorMsg = null
        runCatching { container.api.getNotificationPreferences() }
            .onSuccess { loaded -> prefs = loaded }
            .onFailure { err -> errorMsg = err.message ?: "Failed to load notification preferences" }
        loading = false
    }

    suspend fun saveUpdatedPrefs(key: String, nextEntry: NotificationPrefEntry) {
        val current = prefs ?: return
        val did = sessionState.currentDid ?: return
        val updated = withUpdatedEntry(current, key, nextEntry)
        val body = if (key == "chat") {
            buildJsonObject {
                put(
                    "chat",
                    buildJsonObject {
                        put("include", JsonPrimitive(nextEntry.include ?: "all"))
                        put("push", JsonPrimitive(nextEntry.push))
                    },
                )
            }
        } else {
            PushGatewayManager.buildGatewayPreferenceBody(updated)
        }
        container.api.putNotificationPreferencesV2(body = body)
        prefs = updated
        container.preferences.setDisabledPushReasons(did, PushGatewayManager.getDisabledPushReasons(updated))
        container.pushGatewayManager.syncPreferences(did, updated, force = true)
    }

    SettingsScaffold(
        title = "Notifications",
        onBack = onBack,
        testTag = "notification_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (!osPushGranted) {
                item {
                    SettingsInfoCard(
                        title = "Push notifications disabled in Android",
                        body = "Grant notification permission in Android system settings to receive push alerts.",
                        icon = Icons.Filled.NotificationsOff,
                        isError = true,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Button(onClick = { openAppNotificationSettings(context) }) {
                            Text("Open Android Notification Settings")
                        }
                    }
                }
            } else {
                item {
                    SettingsListItem(
                        title = "Android notification channels",
                        subtitle = "Manage system sound, vibration, and lock-screen behavior",
                        icon = Icons.Filled.Notifications,
                        onClick = { openAppNotificationSettings(context) },
                    )
                }
            }

            item {
                SettingsSwitchItem(
                    title = "Play sound for chat notifications",
                    subtitle = "Play Caelum’s custom dm.mp3 chime for incoming direct messages.",
                    icon = Icons.Filled.VolumeUp,
                    checked = settings.playSoundChat,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setPlaySoundChat(enabled) }
                    },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Notification Categories")
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!errorMsg.isNullOrBlank()) {
                item {
                    SettingsInfoCard(
                        body = errorMsg.orEmpty(),
                        isError = true,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else if (prefs != null) {
                val activePrefs = prefs!!
                itemsIndexed(NOTIFICATION_CATEGORIES, key = { _, item -> item.key }) { _, spec ->
                    val entry = entryForKey(activePrefs, spec.key)
                    SettingsListItem(
                        title = spec.title,
                        subtitle = describeNotificationSummary(spec, entry),
                        icon = spec.icon,
                        onClick = {
                            if (spec.key == "subscribedPost") {
                                onNavigate(AppRoute.ActivityNotificationSettings)
                            } else {
                                editingSpec = spec
                            }
                        },
                    )
                }
            }
        }
    }

    editingSpec?.let { spec ->
        val activePrefs = prefs ?: return@let
        val initial = entryForKey(activePrefs, spec.key)
        var includeMode by remember(spec.key) {
            mutableStateOf(
                when {
                    !initial.push && (spec.isChat || !initial.list) -> "none"
                    initial.include == "follows" -> "follows"
                    initial.include == "accepted" -> "accepted"
                    else -> "all"
                }
            )
        }
        var pushEnabled by remember(spec.key) { mutableStateOf(initial.push) }
        var listEnabled by remember(spec.key) { mutableStateOf(initial.list) }
        var saving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!saving) editingSpec = null },
            title = { Text(spec.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (spec.supportsIncludeFilter) {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.contrast700.toComposeColor(),
                        )
                        val filterOptions = if (spec.isChat) {
                            listOf("all" to "All", "accepted" to "Accepted", "none" to "None")
                        } else {
                            listOf("all" to "All", "follows" to "Follows", "none" to "None")
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            filterOptions.forEachIndexed { idx, (value, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(idx, filterOptions.size),
                                    selected = includeMode == value,
                                    onClick = {
                                        includeMode = value
                                        if (value == "none") {
                                            pushEnabled = false
                                            listEnabled = false
                                        } else if (spec.isChat) {
                                            if (!pushEnabled) pushEnabled = true
                                        } else if (!pushEnabled && !listEnabled) {
                                            pushEnabled = true
                                            listEnabled = true
                                        }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Push notifications", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = pushEnabled,
                            onCheckedChange = {
                                pushEnabled = it
                                if (it && includeMode == "none") includeMode = "all"
                            },
                        )
                    }

                    if (!spec.isChat) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("In-app notifications", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = listEnabled,
                                onCheckedChange = {
                                    listEnabled = it
                                    if (it && includeMode == "none") includeMode = "all"
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            val nextInclude = if (includeMode == "none") initial.include ?: "all" else includeMode
                            val nextEntry = NotificationPrefEntry(
                                include = if (spec.supportsIncludeFilter) nextInclude else initial.include,
                                list = if (includeMode == "none") false else listEnabled,
                                push = if (includeMode == "none") false else pushEnabled,
                            )
                            runCatching { saveUpdatedPrefs(spec.key, nextEntry) }
                                .onSuccess {
                                    haptics.confirm()
                                    editingSpec = null
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Failed to save", Toast.LENGTH_LONG).show()
                                }
                            saving = false
                        }
                    },
                ) {
                    Text(if (saving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { editingSpec = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun ActivityNotificationSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    var prefs by remember { mutableStateOf<NotificationPreferencesResponse?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionState.currentDid) {
        loading = true
        runCatching { container.api.getNotificationPreferences() }
            .onSuccess { prefs = it }
        loading = false
    }

    fun updateSubscribed(push: Boolean, list: Boolean) {
        val current = prefs ?: return
        val did = sessionState.currentDid ?: return
        val updated = current.copy(subscribedPost = current.subscribedPost.copy(push = push, list = list))
        prefs = updated
        scope.launch {
            runCatching {
                val body = PushGatewayManager.buildGatewayPreferenceBody(updated)
                container.api.putNotificationPreferencesV2(body)
                container.preferences.setDisabledPushReasons(did, PushGatewayManager.getDisabledPushReasons(updated))
                container.pushGatewayManager.syncPreferences(did, updated, force = true)
            }.onFailure { err ->
                prefs = current
                Toast.makeText(context, err.message ?: "Failed to update", Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsScaffold(
        title = "Activity Notifications",
        onBack = onBack,
        testTag = "activity_notification_settings_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
        ) {
            SettingsInfoCard(
                title = "Subscribed accounts",
                body = "Choose how you receive alerts when accounts you subscribe to publish new posts or replies.",
                icon = Icons.Filled.NotificationsActive,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (loading) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val sub = prefs?.subscribedPost ?: NotificationPrefEntry()
                SettingsSwitchItem(
                    title = "Push notifications",
                    subtitle = "Receive Android push notifications for subscribed posts",
                    checked = sub.push,
                    haptics = haptics,
                    onCheckedChange = { updateSubscribed(push = it, list = sub.list) },
                )
                SettingsSwitchItem(
                    title = "In-app notifications",
                    subtitle = "Show subscribed post updates in your Notifications tab",
                    checked = sub.list,
                    haptics = haptics,
                    onCheckedChange = { updateSubscribed(push = sub.push, list = it) },
                )
            }
        }
    }
}

@Composable
fun ContentAndMediaSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = "Content & Media",
        onBack = onBack,
        testTag = "content_media_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSectionHeader("Feeds & Threads")
                SettingsListItem(
                    title = "Saved feeds",
                    subtitle = "Pin, reorder, and manage your custom feeds",
                    icon = Icons.Filled.RssFeed,
                    onClick = { onNavigate(AppRoute.SavedFeeds) },
                )
                SettingsListItem(
                    title = "Following feed preferences",
                    subtitle = "Filter replies, reposts, and quote posts in Following",
                    icon = Icons.Filled.DynamicFeed,
                    onClick = { onNavigate(AppRoute.PreferencesFollowingFeed) },
                )
                SettingsListItem(
                    title = "Thread preferences",
                    subtitle = "Choose how replies are sorted in post threads",
                    icon = Icons.Filled.Forum,
                    onClick = { onNavigate(AppRoute.PreferencesThreads) },
                )
                SettingsListItem(
                    title = "Your interests",
                    subtitle = "Topics used to personalize recommendations",
                    icon = Icons.Filled.Interests,
                    onClick = { onNavigate(AppRoute.InterestsSettings) },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Media & Embeds")
                SettingsListItem(
                    title = "External media",
                    subtitle = "Control embedded players like YouTube, Spotify, and SoundCloud",
                    icon = Icons.Filled.MusicNote,
                    onClick = { onNavigate(AppRoute.PreferencesExternalEmbeds) },
                )
                SettingsSwitchItem(
                    title = "Disable autoplay for videos and GIFs",
                    subtitle = "Wait for manual tap before playing video or GIF media",
                    icon = Icons.Filled.PlayCircleOutline,
                    checked = settings.disableAutoplay,
                    haptics = haptics,
                    onCheckedChange = { disable ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(disableAutoplay = disable) }
                    },
                )
                SettingsSwitchItem(
                    title = tr("Enable trending topics"),
                    icon = Icons.Filled.TrendingUp,
                    checked = !settings.hideTrendingTopics,
                    haptics = haptics,
                    onCheckedChange = { enable ->
                        scope.launch { container.preferences.setHideTrendingTopics(!enable) }
                    },
                )
            }
        }
    }
}

@Composable
fun SavedFeedsSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val sessionState by container.sessionManager.state.collectAsState()
    val scope = rememberAccountCoroutineScope()

    var feeds by remember(sessionState.pinnedFeeds) { mutableStateOf(sessionState.pinnedFeeds) }
    var generators by remember(sessionState.feedGenerators) { mutableStateOf(sessionState.feedGenerators) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(feeds) {
        val uris = feeds.filter { it.type == "feed" && !generators.containsKey(it.value) }.map { it.value }
        if (uris.isNotEmpty()) {
            runCatching { container.api.getFeedGenerators(uris) }.getOrNull()?.let { fetched ->
                generators = generators + fetched.associateBy { it.uri }
            }
        }
    }

    fun persistFeeds(next: List<SavedFeedPref>) {
        val did = sessionState.currentDid ?: return
        val prev = feeds
        feeds = next
        scope.launch {
            saving = true
            runCatching {
                container.preferences.setSavedFeeds(did, next)
                upsertActorPreference(
                    api = container.api,
                    type = "app.bsky.actor.defs#savedFeedsPrefV2",
                ) {
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#savedFeedsPrefV2"))
                        put(
                            "items",
                            buildJsonArray {
                                next.forEach { item ->
                                    add(
                                        buildJsonObject {
                                            put("id", JsonPrimitive(item.id))
                                            put("type", JsonPrimitive(item.type))
                                            put("value", JsonPrimitive(item.value))
                                            put("pinned", JsonPrimitive(item.pinned))
                                        }
                                    )
                                }
                            },
                        )
                    }
                }
                container.sessionManager.syncPreferencesFromServer(did)
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) throw err
                feeds = prev
                runCatching { container.preferences.setSavedFeeds(did, prev) }
                Toast.makeText(context, err.message ?: "Failed to save feeds", Toast.LENGTH_LONG).show()
            }
            saving = false
        }
    }

    SettingsScaffold(
        title = "Saved Feeds",
        onBack = onBack,
        testTag = "saved_feeds_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (feeds.isEmpty()) {
                item {
                    SettingsInfoCard(
                        title = "No saved feeds",
                        body = "Pin feeds from the Feeds directory to customize your Home tabs.",
                    )
                }
            } else {
                itemsIndexed(feeds, key = { _, f -> f.id }) { index, feed ->
                    val gen = generators[feed.value]
                    val title = when {
                        feed.value == "following" || feed.type == "timeline" -> "Following"
                        gen != null -> gen.displayName
                        else -> feed.value.substringAfterLast('/')
                    }
                    val subtitle = when {
                        feed.type == "timeline" -> "Chronological home timeline"
                        gen != null -> "by @${gen.creator.handle}"
                        else -> feed.type
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.contrast25.toComposeColor())
                            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CaelumAvatar(
                            url = gen?.avatar,
                            contentDescription = title,
                            size = 38.dp,
                            shapeType = AvatarShape.RoundedSquare,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.contrast1000.toComposeColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            enabled = !saving && index > 0,
                            onClick = {
                                val mutable = feeds.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index - 1, item)
                                persistFeeds(mutable)
                            },
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = !saving && index < feeds.lastIndex,
                            onClick = {
                                val mutable = feeds.toMutableList()
                                val item = mutable.removeAt(index)
                                mutable.add(index + 1, item)
                                persistFeeds(mutable)
                            },
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                        }
                        IconButton(
                            enabled = !saving,
                            onClick = {
                                persistFeeds(
                                    feeds.map { if (it.id == feed.id) it.copy(pinned = !it.pinned) else it }
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = if (feed.pinned) "Unpin" else "Pin",
                                tint = if (feed.pinned) palette.primary500.toComposeColor() else palette.contrast400.toComposeColor(),
                            )
                        }
                        if (feed.type != "timeline") {
                            IconButton(
                                enabled = !saving,
                                onClick = {
                                    persistFeeds(feeds.filterNot { it.id == feed.id })
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove feed",
                                    tint = palette.negative500.toComposeColor(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreferencesFollowingFeedScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val did = sessionState.currentDid
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    val modSettings by (if (did != null) container.preferences.observeModerationSettings(did) else kotlinx.coroutines.flow.flowOf(ModerationUserSettings()))
        .collectAsState(initial = ModerationUserSettings())

    fun updateFeedView(
        hideReplies: Boolean = modSettings.followFeedDisableReplies,
        hideReposts: Boolean = modSettings.followFeedDisableReposts,
        hideQuotes: Boolean = modSettings.followFeedDisableQuotePosts,
    ) {
        val currentDid = did ?: return
        scope.launch {
            container.preferences.updateModerationSettings(currentDid) {
                it.copy(
                    followFeedDisableReplies = hideReplies,
                    followFeedDisableReposts = hideReposts,
                    followFeedDisableQuotePosts = hideQuotes,
                )
            }
            runCatching {
                upsertActorPreference(
                    api = container.api,
                    type = "app.bsky.actor.defs#feedViewPref",
                    match = {
                        (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#feedViewPref" &&
                            (it["feed"] as? JsonPrimitive)?.content == "home"
                    },
                ) { existing ->
                    buildJsonObject {
                        existing?.forEach { (k, v) -> put(k, v) }
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#feedViewPref"))
                        put("feed", JsonPrimitive("home"))
                        put("hideReplies", JsonPrimitive(hideReplies))
                        put("hideReposts", JsonPrimitive(hideReposts))
                        put("hideQuotePosts", JsonPrimitive(hideQuotes))
                    }
                }
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Failed to sync feed preference", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SettingsScaffold(
        title = "Following Feed Preferences",
        onBack = onBack,
        testTag = "following_feed_preferences_screen",
    ) { paddingModifier ->
        Column(modifier = paddingModifier.fillMaxSize()) {
            SettingsSwitchItem(
                title = "Show replies",
                subtitle = "Display replies in your Following feed",
                checked = !modSettings.followFeedDisableReplies,
                haptics = haptics,
                onCheckedChange = { show -> updateFeedView(hideReplies = !show) },
            )
            SettingsSwitchItem(
                title = "Show reposts",
                subtitle = "Display reposts from people you follow",
                checked = !modSettings.followFeedDisableReposts,
                haptics = haptics,
                onCheckedChange = { show -> updateFeedView(hideReposts = !show) },
            )
            SettingsSwitchItem(
                title = "Show quote posts",
                subtitle = "Display quote posts in your Following feed",
                checked = !modSettings.followFeedDisableQuotePosts,
                haptics = haptics,
                onCheckedChange = { show -> updateFeedView(hideQuotes = !show) },
            )
        }
    }
}

@Composable
fun PreferencesThreadsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val did = sessionState.currentDid
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    val modSettings by (if (did != null) container.preferences.observeModerationSettings(did) else kotlinx.coroutines.flow.flowOf(ModerationUserSettings()))
        .collectAsState(initial = ModerationUserSettings())

    val sortOptions = listOf(
        Triple("hotness", "Hot", "Prioritize active, engaging replies"),
        Triple("oldest", "Oldest", "Show earliest replies first"),
        Triple("newest", "Newest", "Show most recent replies first"),
        Triple("most-likes", "Most liked", "Sort replies by like count"),
        Triple("random", "Random", "Randomize reply ordering"),
    )

    SettingsScaffold(
        title = "Thread Preferences",
        onBack = onBack,
        testTag = "thread_preferences_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsSectionHeader("Sort Replies")
            sortOptions.forEach { (value, label, sub) ->
                SettingsRadioRow(
                    title = label,
                    subtitle = sub,
                    selected = modSettings.threadReplyPrioritization == value,
                    onClick = {
                        val currentDid = did ?: return@SettingsRadioRow
                        haptics.selection()
                        scope.launch {
                            container.preferences.updateModerationSettings(currentDid) {
                                it.copy(threadReplyPrioritization = value)
                            }
                            runCatching {
                                upsertActorPreference(
                                    api = container.api,
                                    type = "app.bsky.actor.defs#threadViewPref",
                                ) { existing ->
                                    buildJsonObject {
                                        existing?.forEach { (k, v) -> put(k, v) }
                                        put("\$type", JsonPrimitive("app.bsky.actor.defs#threadViewPref"))
                                        put("sort", JsonPrimitive(value))
                                    }
                                }
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Failed to sync thread preference", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

private val EXTERNAL_EMBED_PROVIDERS = listOf(
    "youtube" to "YouTube",
    "youtu.be" to "YouTube Short Links",
    "spotify" to "Spotify",
    "soundcloud" to "SoundCloud",
    "appleMusic" to "Apple Music",
    "twitch" to "Twitch",
    "vimeo" to "Vimeo",
    "bandcamp" to "Bandcamp",
    "giphy" to "Giphy",
    "tenor" to "Tenor",
    "flickr" to "Flickr",
)

@Composable
fun PreferencesExternalEmbedsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = "External Media",
        onBack = onBack,
        testTag = "external_embeds_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsInfoCard(
                    title = "Embedded media players",
                    body = "Choose which external media providers may load embedded previews inline in posts.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(EXTERNAL_EMBED_PROVIDERS.size) { index ->
                val (key, label) = EXTERNAL_EMBED_PROVIDERS[index]
                val allowed = settings.externalEmbedsAllowList[key] ?: true
                SettingsSwitchItem(
                    title = label,
                    checked = allowed,
                    haptics = haptics,
                    onCheckedChange = { next ->
                        scope.launch { container.preferences.setExternalEmbedConsent(key, next) }
                    },
                )
            }
        }
    }
}
