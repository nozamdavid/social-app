package am.noz.caelum.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayDisabled
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import am.noz.caelum.BuildConfig
import am.noz.caelum.core.i18n.I18nCatalog
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.SearchableBookmarkEngine
import am.noz.caelum.ui.components.CaelumLogoMark
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun AccessibilitySettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = "Accessibility",
        onBack = onBack,
        testTag = "accessibility_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsSectionHeader("Alt Text")
                SettingsSwitchItem(
                    title = "Require alt text before posting",
                    subtitle = "Prevent posting images or GIFs without descriptive alt text.",
                    icon = Icons.Filled.Image,
                    checked = settings.requireAltTextEnabled,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(requireAltText = enabled) }
                    },
                )
                SettingsSwitchItem(
                    title = "Display larger alt text badges",
                    subtitle = "Show an enlarged ALT badge on media attachments for easier legibility.",
                    icon = Icons.Filled.AccessibilityNew,
                    checked = settings.largeAltBadgeEnabled,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(largeAltBadge = enabled) }
                    },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Motion, Haptics & Captions")
                SettingsSwitchItem(
                    title = "Disable haptic feedback",
                    subtitle = "Turn off tactile vibration feedback across buttons, toggles, and gestures.",
                    icon = Icons.Filled.Vibration,
                    checked = settings.disableHaptics,
                    haptics = haptics,
                    onCheckedChange = { disabled ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(disableHaptics = disabled) }
                    },
                )
                SettingsSwitchItem(
                    title = "Reduce motion",
                    subtitle = "Minimize screen transitions, theme animations, and motion effects.",
                    icon = Icons.Filled.Animation,
                    checked = settings.reducedMotion,
                    haptics = haptics,
                    onCheckedChange = { reduced ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(reducedMotion = reduced) }
                    },
                )
                SettingsSwitchItem(
                    title = "Enable video captions / subtitles",
                    subtitle = "Display embedded captions and subtitles automatically on videos when available.",
                    icon = Icons.Filled.ClosedCaption,
                    checked = settings.subtitlesEnabled,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(subtitlesEnabled = enabled) }
                    },
                )
                SettingsSwitchItem(
                    title = "Disable autoplay for videos and GIFs",
                    subtitle = "Require manual tap to start video or GIF playback.",
                    icon = Icons.Filled.PlayDisabled,
                    checked = settings.disableAutoplay,
                    haptics = haptics,
                    onCheckedChange = { disabled ->
                        scope.launch { container.preferences.setAccessibilityAndMedia(disableAutoplay = disabled) }
                    },
                )
            }
        }
    }
}

@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var activeSection by remember { mutableStateOf("app") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            I18nCatalog.SUPPORTED_LANGUAGES
        } else {
            I18nCatalog.SUPPORTED_LANGUAGES.filter {
                it.name.lowercase().contains(q) ||
                    it.nativeName.lowercase().contains(q) ||
                    it.code.lowercase().contains(q)
            }
        }
    }

    SettingsScaffold(
        title = "Languages",
        onBack = onBack,
        testTag = "language_settings_screen",
    ) { paddingModifier ->
        Column(modifier = paddingModifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val sections = listOf(
                    "app" to "App (${I18nCatalog.SUPPORTED_LANGUAGES.size})",
                    "primary" to "Primary",
                    "content" to "Content (${settings.contentLanguages.size})",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sections.forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(idx, sections.size),
                            selected = activeSection == key,
                            onClick = {
                                haptics.selection()
                                activeSection = key
                            },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 44 languages...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (activeSection) {
                    "app" -> {
                        if (searchQuery.isBlank()) {
                            item {
                                SettingsRadioRow(
                                    title = "System Default",
                                    subtitle = "Match your Android device locale",
                                    selected = settings.appLanguage.isBlank(),
                                    onClick = {
                                        haptics.selection()
                                        scope.launch { container.preferences.setLanguages(appLanguage = "") }
                                    },
                                )
                            }
                        }
                        items(filteredLanguages, key = { "app_${it.code}" }) { lang ->
                            SettingsRadioRow(
                                title = lang.nativeName,
                                subtitle = "${lang.name} (${lang.code})",
                                selected = settings.appLanguage == lang.code,
                                onClick = {
                                    haptics.selection()
                                    scope.launch { container.preferences.setLanguages(appLanguage = lang.code) }
                                },
                            )
                        }
                    }

                    "primary" -> {
                        items(filteredLanguages, key = { "primary_${it.code}" }) { lang ->
                            SettingsRadioRow(
                                title = lang.nativeName,
                                subtitle = "${lang.name} (${lang.code})",
                                selected = settings.primaryLanguage == lang.code,
                                onClick = {
                                    haptics.selection()
                                    scope.launch {
                                        container.preferences.setLanguages(
                                            primaryLanguage = lang.code,
                                            postLanguage = lang.code,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    "content" -> {
                        item {
                            Text(
                                text = "Select languages you want to see in feeds and search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        items(filteredLanguages, key = { "content_${it.code}" }) { lang ->
                            val checked = settings.contentLanguages.contains(lang.code)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val next = if (checked) {
                                            (settings.contentLanguages - lang.code).ifEmpty { listOf(lang.code) }
                                        } else {
                                            (settings.contentLanguages + lang.code).distinct()
                                        }
                                        haptics.toggle(!checked)
                                        scope.launch { container.preferences.setLanguages(contentLanguages = next) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lang.nativeName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                        color = palette.contrast1000.toComposeColor(),
                                    )
                                    Text(
                                        text = "${lang.name} (${lang.code})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.contrast600.toComposeColor(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SUGGESTED_INTERESTS = listOf(
    "Art", "Technology", "Science", "Gaming", "Music", "Movies", "Books",
    "Photography", "Nature", "Sports", "News", "Politics", "Food", "Comics",
    "Anime", "History", "Space", "Design", "Open Source", "ATProto",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val session by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    var customTag by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val selected = settings.interests.takeIf { settings.currentDid == session.currentDid }.orEmpty()
    val allTags = remember(selected) { (SUGGESTED_INTERESTS + selected).distinct() }

    fun saveInterests(next: List<String>) {
        val did = session.currentDid ?: return
        scope.launch {
            saving = true
            runCatching {
                container.sessionManager.runForCurrentAccount(did) {
                    container.preferences.setInterests(did, next)
                    upsertActorPreference(
                        api = container.api,
                        type = "app.bsky.actor.defs#interestsPref",
                    ) {
                        buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.actor.defs#interestsPref"))
                            put("tags", JsonArray(next.map { JsonPrimitive(it) }))
                        }
                    }
                }
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Saved locally; server sync failed", Toast.LENGTH_SHORT).show()
            }
            saving = false
        }
    }

    SettingsScaffold(
        title = "Your Interests",
        onBack = onBack,
        testTag = "interests_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "Choose topics you care about to help tune discovery feeds and starter pack suggestions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast700.toComposeColor(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = customTag,
                        onValueChange = { customTag = it },
                        label = { Text("Add custom interest") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = !saving && customTag.isNotBlank(),
                        onClick = {
                            val clean = customTag.trim()
                            customTag = ""
                            haptics.confirm()
                            saveInterests((selected + clean).distinct())
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allTags.forEach { tag ->
                        val isSelected = selected.any { it.equals(tag, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.selection()
                                val next = if (isSelected) {
                                    selected.filterNot { it.equals(tag, ignoreCase = true) }
                                } else {
                                    selected + tag
                                }
                                saveInterests(next)
                            },
                            label = { Text(tag) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutomationLabelSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var isBot by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionState.currentDid) {
        val did = sessionState.currentDid
        if (did != null) {
            runCatching { container.api.getProfile(did) }.getOrNull()?.let { prof ->
                isBot = prof.labels.any { it.`val` == "bot" }
            }
        }
        loading = false
    }

    SettingsScaffold(
        title = "Automation Label",
        onBack = onBack,
        testTag = "automation_label_settings_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsInfoCard(
                title = "Bot / Automated Account Declaration",
                body = "Adds the self-applied `bot` label to your profile record so others know this account posts automated content.",
                icon = Icons.Filled.SmartToy,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (loading) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                SettingsSwitchItem(
                    title = "Mark account as automated",
                    subtitle = "Displays a Bot badge on your profile across Bluesky and Caelum.",
                    icon = Icons.Filled.SmartToy,
                    checked = isBot,
                    enabled = sessionState.currentDid != null,
                    haptics = haptics,
                    onCheckedChange = { enable ->
                        val did = sessionState.currentDid ?: return@SettingsSwitchItem
                        isBot = enable
                        scope.launch {
                            runCatching {
                                toggleProfileSelfLabel(
                                    xrpc = container.xrpcClient,
                                    api = container.api,
                                    did = did,
                                    labelValue = "bot",
                                    enabled = enable,
                                )
                                Toast.makeText(context, "Automation label updated", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                isBot = !enable
                                Toast.makeText(context, err.message ?: "Failed to update label", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun BetaFeaturesSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    SettingsScaffold(
        title = "Beta Features",
        onBack = onBack,
        testTag = "beta_features_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsInfoCard(
                    title = "Caelum Native Android Preview",
                    body = "You are running the native Jetpack Compose edition of Caelum (${BuildConfig.APP_VERSION_NAME}). Experimental capabilities like Witchsky ATProto themes, custom AppView/CDN/PLC routing, and local searchable bookmarks are active.",
                    icon = Icons.Filled.Science,
                    isAccent = true,
                )
            }
            item {
                SettingsSwitchItem(
                    title = "Searchable saved posts (Beta)",
                    subtitle = "Local Room + gzipped snapshot bookmark search index.",
                    checked = settings.searchableBookmarks,
                    haptics = haptics,
                    onCheckedChange = { enabled ->
                        scope.launch { container.preferences.setSearchableBookmarks(enabled) }
                    },
                )
                SettingsListItem(
                    title = "Configure Caelum Customization",
                    subtitle = "Open full Customization settings",
                    onClick = { onNavigate(AppRoute.CustomizationSettings) },
                )
                SettingsListItem(
                    title = "Configure Caelum Infrastructure",
                    subtitle = "Open AppView, CDN, and PLC Directory overrides",
                    onClick = { onNavigate(AppRoute.InfrastructureSettings) },
                )
            }
        }
    }
}

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val scope = rememberCoroutineScope()
    var clearingCache by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "About",
        onBack = onBack,
        testTag = "about_settings_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CaelumLogoMark(size = 56.dp, useDarkVariant = palette.isDark)
                    Text(
                        text = "Caelum",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.contrast1000.toComposeColor(),
                    )
                    Text(
                        text = "Version ${BuildConfig.APP_VERSION_NAME} (${BuildConfig.COMMIT_SHA})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.contrast600.toComposeColor(),
                    )
                    Text(
                        text = "Native Android client for Bluesky & the AT Protocol.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast700.toComposeColor(),
                    )
                }
            }

            item { SettingsDivider() }

            item {
                SettingsListItem(
                    title = "Emoji suggestion credits",
                    subtitle = "Open-source licenses for Emoji Mart & Fuse.js",
                    icon = Icons.Filled.EmojiEmotions,
                    onClick = { onNavigate(AppRoute.EmojiCredits) },
                )
                SettingsListItem(
                    title = "Clear image cache",
                    subtitle = if (clearingCache) "Clearing cached images..." else "Free disk and memory cache used by Coil image loader",
                    icon = Icons.Filled.CleaningServices,
                    onClick = {
                        if (!clearingCache) {
                            scope.launch {
                                clearingCache = true
                                withContext(Dispatchers.IO) {
                                    runCatching { container.imageLoader.diskCache?.clear() }
                                }
                                runCatching { container.imageLoader.memoryCache?.clear() }
                                clearingCache = false
                                Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                SettingsListItem(
                    title = "System log & diagnostics",
                    subtitle = "Inspect infrastructure routing, database counts, and recent logs",
                    icon = Icons.Filled.BugReport,
                    onClick = { onNavigate(AppRoute.Log) },
                )
                SettingsListItem(
                    title = "Moderation state inspector",
                    subtitle = "Inspect active labelers, muted words, and label preferences",
                    icon = Icons.Filled.Shield,
                    onClick = { onNavigate(AppRoute.DebugMod) },
                )
            }

            item { SettingsDivider() }

            item {
                SettingsSectionHeader("Legal & Policies")
                SettingsListItem(
                    title = "Terms of Service",
                    icon = Icons.Filled.Description,
                    onClick = { onNavigate(AppRoute.TermsOfService) },
                )
                SettingsListItem(
                    title = "Privacy Policy",
                    icon = Icons.Filled.Policy,
                    onClick = { onNavigate(AppRoute.PrivacyPolicy) },
                )
                SettingsListItem(
                    title = "Community Guidelines",
                    icon = Icons.Filled.Gavel,
                    onClick = { onNavigate(AppRoute.CommunityGuidelines) },
                )
                SettingsListItem(
                    title = "Copyright Policy",
                    icon = Icons.Filled.Description,
                    onClick = { onNavigate(AppRoute.CopyrightPolicy) },
                )
                SettingsListItem(
                    title = "Status Page",
                    subtitle = "status.bsky.app",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        LinkNavigationHelper.openUrl(
                            context = context,
                            rawUrl = "https://status.bsky.app",
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onInternalRoute = onNavigate,
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun LogSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var logText by remember { mutableStateOf("Loading diagnostics...") }
    var bookmarkCount by remember { mutableStateOf(0) }

    suspend fun collectDiagnostics() {
        val did = sessionState.currentDid
        if (did != null) {
            val scopeKey = SearchableBookmarkEngine.scopeKeyFor(did, settings.infrastructure.appviewHostname)
            bookmarkCount = runCatching { container.database.bookmarkIndexDao().countForScope(scopeKey) }.getOrDefault(0)
        }
        val logcat = withContext(Dispatchers.IO) {
            runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "100", "am.noz.caelum:V", "*:S"))
                BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            }.getOrDefault("")
        }
        val infra = settings.infrastructure
        logText = buildString {
            appendLine("=== Caelum Diagnostics ===")
            appendLine("Version: ${BuildConfig.APP_VERSION_NAME} (${BuildConfig.COMMIT_SHA})")
            appendLine("Package: ${context.packageName}")
            appendLine("Active DID: ${sessionState.currentDid ?: "signed out"}")
            appendLine("Handle: ${sessionState.currentAccount?.handle ?: "-"}")
            appendLine("PDS: ${sessionState.currentAccount?.pdsUrl ?: sessionState.currentAccount?.serviceUrl ?: "-"}")
            appendLine("AppView: ${infra.appviewHostname.ifBlank { "api.bsky.app (default)" }}")
            appendLine("Image CDN: ${infra.imageCdnHostname.ifBlank { "cdn.bsky.app (default)" }}")
            appendLine("PLC Directory: ${infra.plcDirectoryHostname.ifBlank { "plc.directory (default)" }}")
            appendLine("Link Redirects: ${infra.useLinkRedirects}")
            appendLine("Active Theme: ${settings.witchskySelectedTheme?.let { "${it.name} (${it.variantName})" } ?: "Bluesky Default"}")
            appendLine("App Icon: ${settings.appIconId}")
            appendLine("Indexed Bookmarks: $bookmarkCount")
            appendLine("FCM Token Present: ${!sessionState.fcmToken.isNullOrBlank()}")
            if (logcat.isNotBlank()) {
                appendLine()
                appendLine("=== Recent Logcat ===")
                appendLine(logcat.trim())
            }
        }
    }

    LaunchedEffect(sessionState.currentDid, settings) {
        collectDiagnostics()
    }

    SettingsScaffold(
        title = "System Log",
        onBack = onBack,
        testTag = "log_screen",
        actions = {
            IconButton(onClick = { scope.launch { collectDiagnostics() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = {
                LinkNavigationHelper.copyToClipboard(context, "Caelum Log", logText, "Copied diagnostics")
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy log")
            }
        },
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                SelectionContainer {
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.contrast900.toComposeColor(),
                    )
                }
            }
        }
    }
}

@Composable
fun DebugModSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val sessionState by container.sessionManager.state.collectAsState()
    val modOpts = sessionState.moderationOpts
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Moderation Inspector",
        onBack = onBack,
        testTag = "debug_mod_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(
                    enabled = !syncing && sessionState.currentDid != null,
                    onClick = {
                        val did = sessionState.currentDid ?: return@Button
                        scope.launch {
                            syncing = true
                            runCatching { container.sessionManager.syncPreferencesFromServer(did) }
                                .onSuccess { Toast.makeText(context, "Synced moderation preferences", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, it.message ?: "Sync failed", Toast.LENGTH_SHORT).show() }
                            syncing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (syncing) "Syncing..." else "Sync Preferences from Server")
                }
            }
            item {
                SettingsInfoCard(
                    title = "Summary",
                    body = buildString {
                        appendLine("User DID: ${modOpts.userDid ?: "signed out"}")
                        appendLine("Adult content enabled: ${modOpts.prefs.adultContentEnabled}")
                        appendLine("Subscribed labelers: ${modOpts.prefs.labelers.size}")
                        appendLine("Muted words: ${modOpts.prefs.mutedWords.size}")
                        appendLine("Hidden posts: ${modOpts.prefs.hiddenPosts.size}")
                        appendLine("Label definitions loaded: ${modOpts.labelDefs.values.sumOf { it.size }}")
                    },
                )
            }
            item {
                SettingsInfoCard(
                    title = "Global Label Preferences",
                    body = modOpts.prefs.labels.entries.joinToString("\n") { (k, v) -> "$k -> $v" }
                        .ifBlank { "No custom label preferences." },
                )
            }
            item {
                SettingsInfoCard(
                    title = "Subscribed Labelers",
                    body = sessionState.subscribedLabelers.joinToString("\n") {
                        "${it.creator.displayName ?: it.creator.handle} (${it.creator.did})"
                    }.ifBlank { "Default Bluesky moderation labeler" },
                )
            }
        }
    }
}

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())

    SettingsScaffold(
        title = "Support & Help",
        onBack = onBack,
        testTag = "support_screen",
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsInfoCard(
                    title = "Caelum Help & Documentation",
                    body = "Find answers to common questions, check Bluesky network status, or review legal policies.",
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                SettingsListItem(
                    title = "Bluesky Help Center",
                    subtitle = "blueskyweb.zendesk.com",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        LinkNavigationHelper.openUrl(
                            context = context,
                            rawUrl = "https://blueskyweb.zendesk.com/hc/en-us",
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onInternalRoute = onNavigate,
                        )
                    },
                )
                SettingsListItem(
                    title = "Network Status Page",
                    subtitle = "status.bsky.app",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        LinkNavigationHelper.openUrl(
                            context = context,
                            rawUrl = "https://status.bsky.app",
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onInternalRoute = onNavigate,
                        )
                    },
                )
                SettingsListItem(
                    title = "System Log & Diagnostics",
                    icon = Icons.Filled.BugReport,
                    onClick = { onNavigate(AppRoute.Log) },
                )
                SettingsDivider()
                SettingsListItem(
                    title = "Privacy Policy",
                    icon = Icons.Filled.Policy,
                    onClick = { onNavigate(AppRoute.PrivacyPolicy) },
                )
                SettingsListItem(
                    title = "Terms of Service",
                    icon = Icons.Filled.Description,
                    onClick = { onNavigate(AppRoute.TermsOfService) },
                )
                SettingsListItem(
                    title = "Community Guidelines",
                    icon = Icons.Filled.Gavel,
                    onClick = { onNavigate(AppRoute.CommunityGuidelines) },
                )
                SettingsListItem(
                    title = "Copyright Policy",
                    icon = Icons.Filled.Description,
                    onClick = { onNavigate(AppRoute.CopyrightPolicy) },
                )
            }
        }
    }
}

@Composable
fun LegalPolicyScreen(
    title: String,
    summary: String,
    url: String,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())

    SettingsScaffold(
        title = title,
        onBack = onBack,
        testTag = "legal_policy_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsInfoCard(
                title = title,
                body = summary,
                icon = Icons.Filled.Policy,
            )
            OutlinedButton(
                onClick = {
                    LinkNavigationHelper.openUrl(
                        context = context,
                        rawUrl = url,
                        useLinkRedirects = settings.infrastructure.useLinkRedirects,
                        openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                        onInternalRoute = onNavigate,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open full document ($url)")
            }
        }
    }
}
