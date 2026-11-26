package am.noz.caelum.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.network.HostPreset
import am.noz.caelum.core.network.InfrastructureConfig
import am.noz.caelum.core.network.InfrastructureSettings
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.SearchableBookmarkEngine
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

private val EXTRA_APPVIEW_PRESETS = listOf(
    HostPreset("Bluesky (Default)", ""),
    HostPreset("bsky.network", "bsky.network"),
    HostPreset("zeppelin.social", "zeppelin.social"),
) + InfrastructureConfig.APPVIEW_PRESETS.filter { it.hostname.isNotEmpty() }

@Composable
fun InfrastructureSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    val stored = settings.infrastructure
    var useLinkRedirects by remember(stored.useLinkRedirects) { mutableStateOf(stored.useLinkRedirects) }
    var appviewInput by remember(stored.appviewHostname) { mutableStateOf(stored.appviewHostname) }
    var imageCdnInput by remember(stored.imageCdnHostname) { mutableStateOf(stored.imageCdnHostname) }
    var plcInput by remember(stored.plcDirectoryHostname) { mutableStateOf(stored.plcDirectoryHostname) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val normalizedAppviewPreview = remember(appviewInput) {
        runCatching { InfrastructureConfig.normalizeAppview(appviewInput) }
    }
    val normalizedCdnPreview = remember(imageCdnInput) {
        runCatching { InfrastructureConfig.normalizeImageCdn(imageCdnInput) }
    }
    val normalizedPlcPreview = remember(plcInput) {
        runCatching { InfrastructureConfig.normalizePlcDirectory(plcInput) }
    }

    fun saveSettings(target: InfrastructureSettings) {
        scope.launch {
            runCatching {
                container.preferences.setInfrastructure(target)
                container.identityResolver.clearCaches()
                validationError = null
                haptics.confirm()
                Toast.makeText(context, "Infrastructure settings saved", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                validationError = err.message ?: "Invalid hostname configuration"
            }
        }
    }

    SettingsScaffold(
        title = "Infrastructure",
        onBack = onBack,
        testTag = "infrastructure_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                SettingsInfoCard(
                    title = "Network & Federation",
                    body = "Configure link redirects, AppView proxy routing, Image CDN, and PLC Directory. Applies to all accounts on this device.",
                    icon = Icons.Filled.Cloud,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // 1. Link Redirects
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader("Link Redirects")
                SettingsSwitchItem(
                    title = "Use Bluesky link redirects (go.bsky.app)",
                    subtitle = "Route external links through go.bsky.app for safety previews. Turn off to open URLs directly.",
                    icon = Icons.Filled.Link,
                    checked = useLinkRedirects,
                    haptics = haptics,
                    onCheckedChange = { useLinkRedirects = it },
                )
            }

            item { SettingsDivider() }

            // 2. AppView Server
            item {
                SettingsSectionHeader("AppView Server")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Provides feeds, profiles, and search. Authenticated requests proxy through your PDS via atproto-proxy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                    EXTRA_APPVIEW_PRESETS.forEach { preset ->
                        val selected = appviewInput.trim().equals(preset.hostname, ignoreCase = true) ||
                            (preset.hostname.isEmpty() && appviewInput.isBlank())
                        SettingsRadioRow(
                            title = preset.name,
                            subtitle = preset.hostname.ifEmpty { "api.bsky.app (default)" },
                            selected = selected,
                            onClick = {
                                haptics.selection()
                                appviewInput = preset.hostname
                            },
                        )
                    }
                    OutlinedTextField(
                        value = appviewInput,
                        onValueChange = { appviewInput = it; validationError = null },
                        label = { Text("Custom AppView hostname") },
                        placeholder = { Text("api.bsky.app or custom.appview.host") },
                        singleLine = true,
                        isError = appviewInput.isNotBlank() && normalizedAppviewPreview.isFailure,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("appview_hostname_input"),
                    )
                    val resolvedHost = normalizedAppviewPreview.getOrNull()
                    if (resolvedHost != null) {
                        val effectiveHost = resolvedHost.ifEmpty { "api.bsky.app" }
                        val proxyDid = "did:web:$effectiveHost#bsky_appview"
                        Text(
                            text = "Normalized: ${resolvedHost.ifEmpty { "(default api.bsky.app)" }}\nProxy header: $proxyDid",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = palette.primary600.toComposeColor(),
                        )
                    } else if (appviewInput.isNotBlank()) {
                        Text(
                            text = "Enter a valid hostname without scheme, port, or path.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.negative500.toComposeColor(),
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)); SettingsDivider() }

            // 3. Image CDN
            item {
                SettingsSectionHeader("Image CDN")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfrastructureConfig.IMAGE_CDN_PRESETS.forEach { preset ->
                        val selected = imageCdnInput.trim().equals(preset.hostname, ignoreCase = true) ||
                            (preset.hostname.isEmpty() && imageCdnInput.isBlank())
                        SettingsRadioRow(
                            title = preset.name,
                            subtitle = preset.hostname.ifEmpty { "cdn.bsky.app (default)" },
                            selected = selected,
                            onClick = {
                                haptics.selection()
                                imageCdnInput = preset.hostname
                            },
                        )
                    }
                    OutlinedTextField(
                        value = imageCdnInput,
                        onValueChange = { imageCdnInput = it; validationError = null },
                        label = { Text("Custom Image CDN hostname") },
                        placeholder = { Text("cdn.bsky.app") },
                        singleLine = true,
                        isError = imageCdnInput.isNotBlank() && normalizedCdnPreview.isFailure,
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)); SettingsDivider() }

            // 4. PLC Directory
            item {
                SettingsSectionHeader("PLC Directory")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfrastructureConfig.PLC_DIRECTORY_PRESETS.forEach { preset ->
                        val selected = plcInput.trim().equals(preset.hostname, ignoreCase = true) ||
                            (preset.hostname.isEmpty() && plcInput.isBlank())
                        SettingsRadioRow(
                            title = preset.name,
                            subtitle = preset.hostname.ifEmpty { "plc.directory (default)" },
                            selected = selected,
                            onClick = {
                                haptics.selection()
                                plcInput = preset.hostname
                            },
                        )
                    }
                    OutlinedTextField(
                        value = plcInput,
                        onValueChange = { plcInput = it; validationError = null },
                        label = { Text("Custom PLC Directory hostname") },
                        placeholder = { Text("plc.directory") },
                        singleLine = true,
                        isError = plcInput.isNotBlank() && normalizedPlcPreview.isFailure,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    normalizedPlcPreview.getOrNull()?.let { host ->
                        Text(
                            text = "Origin: ${InfrastructureConfig.plcDirectoryOrigin(host)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = palette.contrast600.toComposeColor(),
                        )
                    }
                }
            }

            if (!validationError.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    SettingsInfoCard(
                        body = validationError.orEmpty(),
                        isError = true,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // 5. Actions: Reset to Defaults + Save
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            useLinkRedirects = InfrastructureConfig.DEFAULT.useLinkRedirects
                            appviewInput = InfrastructureConfig.DEFAULT.appviewHostname
                            imageCdnInput = InfrastructureConfig.DEFAULT.imageCdnHostname
                            plcInput = InfrastructureConfig.DEFAULT.plcDirectoryHostname
                            saveSettings(InfrastructureConfig.DEFAULT)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset Defaults")
                    }
                    Button(
                        onClick = {
                            val appview = normalizedAppviewPreview.getOrElse {
                                validationError = "Invalid AppView hostname."
                                return@Button
                            }
                            val cdn = normalizedCdnPreview.getOrElse {
                                validationError = "Invalid Image CDN hostname."
                                return@Button
                            }
                            val plc = normalizedPlcPreview.getOrElse {
                                validationError = "Invalid PLC Directory hostname."
                                return@Button
                            }
                            saveSettings(
                                InfrastructureSettings(
                                    useLinkRedirects = useLinkRedirects,
                                    appviewHostname = appview,
                                    imageCdnHostname = cdn,
                                    plcDirectoryHostname = plc,
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizationSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    val currentDid = sessionState.currentDid
    val scopeKey = remember(currentDid, settings.infrastructure.appviewHostname) {
        currentDid?.let { SearchableBookmarkEngine.scopeKeyFor(it, settings.infrastructure.appviewHostname) }
    }

    var indexedCount by remember { mutableIntStateOf(0) }
    var snapshotBytes by remember { mutableIntStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshIndexStats() {
        val key = scopeKey ?: return
        val dao = container.database.bookmarkIndexDao()
        indexedCount = dao.countForScope(key)
        val snapshotFile = SearchableBookmarkEngine.snapshotFile(context.filesDir, key)
        snapshotBytes = if (snapshotFile.exists()) snapshotFile.length().toInt() else 0
    }

    LaunchedEffect(scopeKey, settings.searchableBookmarks) {
        refreshIndexStats()
    }

    fun rebuildBookmarkIndex() {
        val key = scopeKey
        if (key == null) {
            syncError = "Sign in to index your saved posts."
            return
        }
        scope.launch {
            syncing = true
            syncError = null
            runCatching {
                var cursor: String? = null
                var totalFetched = 0
                val seenCursors = mutableSetOf<String>()
                do {
                    val page = container.api.getBookmarks(cursor = cursor, limit = 50)
                    if (page.bookmarks.isNotEmpty()) {
                        container.bookmarkEngine.upsertBookmarks(key, page.bookmarks)
                        totalFetched += page.bookmarks.size
                        indexedCount = container.database.bookmarkIndexDao().countForScope(key)
                    }
                    val nextCursor = page.cursor
                    if (nextCursor.isNullOrBlank() || !seenCursors.add(nextCursor) || page.bookmarks.isEmpty()) {
                        break
                    }
                    cursor = nextCursor
                } while (true)

                val gzipped = container.bookmarkEngine.exportGzipSnapshot(key)
                withContext(Dispatchers.IO) {
                    SearchableBookmarkEngine.snapshotFile(context.filesDir, key).writeBytes(gzipped)
                }
                snapshotBytes = gzipped.size
                haptics.confirm()
                Toast.makeText(context, "Indexed $totalFetched saved posts", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                syncError = err.message ?: "Failed to sync saved posts"
            }
            refreshIndexStats()
            syncing = false
        }
    }

    fun clearBookmarkIndex() {
        val key = scopeKey ?: return
        scope.launch {
            container.database.bookmarkIndexDao().clearScope(key)
            withContext(Dispatchers.IO) {
                SearchableBookmarkEngine.snapshotFile(context.filesDir, key).delete()
            }
            indexedCount = 0
            snapshotBytes = 0
            haptics.tap()
            Toast.makeText(context, "Cleared local bookmark index", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsScaffold(
        title = "Customization",
        onBack = onBack,
        testTag = "customization_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSectionHeader("Caelum Features")
                SettingsSwitchItem(
                    title = "Open Bluesky links in Caelum",
                    subtitle = "Open bsky.app links directly inside Caelum instead of an external browser.",
                    icon = Icons.Filled.OpenInNew,
                    checked = settings.openBskyLinksInCaelum,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setOpenBskyLinksInCaelum(enabled) }
                    },
                )
                SettingsSwitchItem(
                    title = "Automatically like reposts",
                    subtitle = "Automatically like posts when you repost them. Undoing a repost keeps the like.",
                    icon = Icons.Filled.Favorite,
                    checked = settings.autoLikeReposts,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setAutoLikeReposts(enabled) }
                    },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Searchable Saved Posts")
                SettingsSwitchItem(
                    title = "Searchable saved posts",
                    subtitle = "Download and index your saved posts locally in Room with compressed gzip snapshots for instant full-text & @handle search.",
                    icon = Icons.Filled.Bookmark,
                    checked = settings.searchableBookmarks,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.preferences.setSearchableBookmarks(enabled)
                            if (enabled && indexedCount == 0 && scopeKey != null) {
                                rebuildBookmarkIndex()
                            }
                        }
                    },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val sizeLabel = if (snapshotBytes >= 1024 * 1024) {
                        String.format(Locale.US, "%.2f MB", snapshotBytes / 1048576.0)
                    } else {
                        String.format(Locale.US, "%.1f KB", snapshotBytes / 1024.0)
                    }
                    SettingsInfoCard(
                        title = if (syncing) "Syncing saved posts..." else "$indexedCount indexed saved posts",
                        body = if (scopeKey == null) {
                            "Sign in to build a local searchable bookmark index."
                        } else {
                            "Compressed snapshot size: $sizeLabel"
                        },
                        icon = Icons.Filled.Storage,
                        isAccent = settings.searchableBookmarks,
                    ) {
                        if (!syncError.isNullOrBlank()) {
                            Text(
                                text = syncError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.negative500.toComposeColor(),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { rebuildBookmarkIndex() },
                                enabled = !syncing && scopeKey != null,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(if (syncing) "Indexing..." else "Rebuild index")
                            }
                            OutlinedButton(
                                onClick = { clearBookmarkIndex() },
                                enabled = !syncing && indexedCount > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear index")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
private data class EmojiNoticeItem(
    val name: String,
    val packages: List<String> = emptyList(),
    val license: String,
    val text: String,
)

@Composable
fun EmojiCreditsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current

    var notices by remember { mutableStateOf<List<EmojiNoticeItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = container.context.assets.open("emoji/notices.json")
                    .bufferedReader()
                    .use { it.readText() }
                AtProtoJson.decodeFromString<List<EmojiNoticeItem>>(raw)
            }.onSuccess { parsed ->
                notices = parsed
            }.onFailure { err ->
                errorMsg = err.message ?: "Unable to read emoji license notices."
            }
        }
        loading = false
    }

    SettingsScaffold(
        title = "Emoji Suggestion Credits",
        onBack = onBack,
        testTag = "emoji_credits_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Caelum’s on-device emoji suggestions and fuzzy search use the following open-source libraries. Their full license notices are included below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast700.toComposeColor(),
                )
            }

            if (loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (!errorMsg.isNullOrBlank()) {
                item {
                    SettingsInfoCard(body = errorMsg.orEmpty(), isError = true)
                }
            } else {
                items(notices, key = { it.name }) { notice ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.contrast25.toComposeColor())
                            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notice.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.contrast1000.toComposeColor(),
                                )
                                Text(
                                    text = notice.packages.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.primary50.toComposeColor(),
                            ) {
                                Text(
                                    text = notice.license,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primary600.toComposeColor(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    LinkNavigationHelper.copyToClipboard(
                                        context,
                                        notice.name,
                                        "${notice.name} (${notice.license})\n\n${notice.text}",
                                        "Copied ${notice.name} license",
                                    )
                                },
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy notice")
                            }
                        }

                        SelectionContainer {
                            Text(
                                text = notice.text.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.contrast800.toComposeColor(),
                            )
                        }
                    }
                }
            }
        }
    }
}
