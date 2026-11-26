package am.noz.caelum.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ContentLabelPref
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.network.InfrastructureConfig
import am.noz.caelum.core.network.InfrastructureSettings
import am.noz.caelum.ui.theme.ThemeOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ModerationUserSettings(
    val adultContentEnabled: Boolean = false,
    val contentLabels: Map<String, String> = emptyMap(),
    val labelers: List<LabelerPrefItem> = emptyList(),
    val labelerContentLabels: List<ContentLabelPref> = emptyList(),
    val mutedWords: List<MutedWord> = emptyList(),
    val hiddenPosts: Set<String> = emptySet(),
    val mutedThreads: Set<String> = emptySet(),
    val threadReplyPrioritization: String = "hotness",
    val threadedReplies: Boolean = false,
    val followFeedDisableReplies: Boolean = false,
    val followFeedDisableReposts: Boolean = false,
    val followFeedDisableQuotePosts: Boolean = false,
    val feedLanguages: List<String> = emptyList(),
    val hideVerificationBadges: Boolean = false,
)

@Serializable
data class AppSettingsSnapshot(
    val accounts: List<StoredAccount> = emptyList(),
    val currentDid: String? = null,
    val colorMode: String = "system",
    val darkTheme: String = "dim",
    val dynamicColorEnabled: Boolean = false,
    val fontScale: String = "0",
    val fontFamily: String = "theme",
    val witchskySelectedTheme: ThemeOption? = null,
    val witchskySavedThemes: List<ThemeOption> = emptyList(),
    val appIconId: String = "default_light",
    val openBskyLinksInCaelum: Boolean = true,
    val autoLikeReposts: Boolean = false,
    val searchableBookmarks: Boolean = false,
    val infrastructure: InfrastructureSettings = InfrastructureSettings(),
    val primaryLanguage: String = "en",
    val contentLanguages: List<String> = listOf("en"),
    val postLanguage: String = "en",
    val postLanguageHistory: List<String> = listOf("en"),
    val appLanguage: String = "",
    val requireAltTextEnabled: Boolean = false,
    val largeAltBadgeEnabled: Boolean = false,
    val disableAutoplay: Boolean = false,
    val hideTrendingTopics: Boolean = false,
    val disableHaptics: Boolean = false,
    val reducedMotion: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val playSoundChat: Boolean = true,
    val externalEmbedsAllowList: Map<String, Boolean> = emptyMap(),
    val interests: List<String> = emptyList(),
    val legacyRnMigrated: Boolean = false,
)

internal data class LegacyMmkvImportResult(
    val appearance: Boolean = false,
    val infrastructure: Boolean = false,
)

/**
 * Single source of truth for persisted client preferences and account metadata.
 * Accepts an injected DataStore instance so Robolectric tests do not collide on static delegates.
 */
class PreferencesRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val rnNotificationPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(RN_NOTIFICATION_PREFS_NAME, Context.MODE_PRIVATE)

    val settingsFlow: Flow<AppSettingsSnapshot> = dataStore.data.map { prefs ->
        decodeSnapshot(prefs)
    }

    suspend fun currentSnapshot(): AppSettingsSnapshot = settingsFlow.first()

    suspend fun updateAccounts(accounts: List<StoredAccount>, currentDid: String?) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS_JSON] = AtProtoJson.encodeToString(accounts)
            if (currentDid != null) {
                prefs[KEY_CURRENT_DID] = currentDid
            } else {
                prefs.remove(KEY_CURRENT_DID)
            }
        }
    }

    suspend fun setColorMode(mode: String) {
        dataStore.edit { it[KEY_COLOR_MODE] = mode }
    }

    suspend fun setDarkTheme(mode: String) {
        dataStore.edit { it[KEY_DARK_THEME] = mode }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setFontScale(scale: String) {
        dataStore.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun setFontFamily(family: String) {
        dataStore.edit { it[KEY_FONT_FAMILY] = family }
    }

    suspend fun setWitchskySelectedTheme(theme: ThemeOption?) {
        dataStore.edit { prefs ->
            if (theme == null) {
                prefs.remove(KEY_WITCHSKY_SELECTED)
            } else {
                prefs[KEY_WITCHSKY_SELECTED] = AtProtoJson.encodeToString(theme)
            }
        }
    }

    suspend fun setWitchskySavedThemes(themes: List<ThemeOption>) {
        dataStore.edit { it[KEY_WITCHSKY_SAVED] = AtProtoJson.encodeToString(themes) }
    }

    suspend fun setAppIconId(iconId: String) {
        dataStore.edit { it[KEY_APP_ICON] = iconId }
    }

    suspend fun setOpenBskyLinksInCaelum(enabled: Boolean) {
        dataStore.edit { it[KEY_OPEN_BSKY_LINKS] = enabled }
    }

    suspend fun setAutoLikeReposts(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LIKE_REPOSTS] = enabled }
    }

    suspend fun setSearchableBookmarks(enabled: Boolean) {
        dataStore.edit { it[KEY_SEARCHABLE_BOOKMARKS] = enabled }
    }

    suspend fun setInfrastructure(state: InfrastructureSettings) {
        val normalized = InfrastructureSettings(
            appviewHostname = InfrastructureConfig.normalizeAppview(state.appviewHostname),
            imageCdnHostname = InfrastructureConfig.normalizeImageCdn(state.imageCdnHostname),
            plcDirectoryHostname = InfrastructureConfig.normalizePlcDirectory(state.plcDirectoryHostname),
            useLinkRedirects = state.useLinkRedirects,
        )
        dataStore.edit { it[KEY_INFRASTRUCTURE_JSON] = AtProtoJson.encodeToString(normalized) }
    }

    suspend fun setLanguages(
        primaryLanguage: String? = null,
        contentLanguages: List<String>? = null,
        postLanguage: String? = null,
        postLanguageHistory: List<String>? = null,
        appLanguage: String? = null,
    ) {
        dataStore.edit { prefs ->
            primaryLanguage?.let { prefs[KEY_PRIMARY_LANGUAGE] = it }
            contentLanguages?.let { prefs[KEY_CONTENT_LANGUAGES] = AtProtoJson.encodeToString(it) }
            postLanguage?.let { prefs[KEY_POST_LANGUAGE] = it }
            postLanguageHistory?.let { prefs[KEY_POST_LANGUAGE_HISTORY] = AtProtoJson.encodeToString(it) }
            appLanguage?.let { prefs[KEY_APP_LANGUAGE] = it }
        }
    }

    suspend fun setAccessibilityAndMedia(
        requireAltText: Boolean? = null,
        largeAltBadge: Boolean? = null,
        disableAutoplay: Boolean? = null,
        disableHaptics: Boolean? = null,
        reducedMotion: Boolean? = null,
        subtitlesEnabled: Boolean? = null,
    ) {
        dataStore.edit { prefs ->
            requireAltText?.let { prefs[KEY_REQUIRE_ALT_TEXT] = it }
            largeAltBadge?.let { prefs[KEY_LARGE_ALT_BADGE] = it }
            disableAutoplay?.let { prefs[KEY_DISABLE_AUTOPLAY] = it }
            disableHaptics?.let { prefs[KEY_DISABLE_HAPTICS] = it }
            reducedMotion?.let { prefs[KEY_REDUCED_MOTION] = it }
            subtitlesEnabled?.let { prefs[KEY_SUBTITLES_ENABLED] = it }
        }
    }

    suspend fun setHideTrendingTopics(hidden: Boolean) {
        dataStore.edit { it[KEY_HIDE_TRENDING_TOPICS] = hidden }
    }

    suspend fun setPlaySoundChat(enabled: Boolean) {
        dataStore.edit { it[KEY_PLAY_SOUND_CHAT] = enabled }
        rnNotificationPrefs.edit().putBoolean("playSoundChat", enabled).commit()
    }

    fun getDisabledPushReasons(did: String): Set<String> {
        val csv = rnNotificationPrefs.getString("disabledPushReasons:$did", "").orEmpty()
        return csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setDisabledPushReasons(did: String, reasons: Set<String>) {
        rnNotificationPrefs.edit()
            .putString("disabledPushReasons:$did", reasons.sorted().joinToString(","))
            .commit()
    }

    fun getPlaySoundChatImmediate(): Boolean = rnNotificationPrefs.getBoolean("playSoundChat", true)

    suspend fun getSavedFeeds(did: String): List<SavedFeedPref> {
        val raw = dataStore.data.first()[stringPreferencesKey("saved_feeds_$did")] ?: return emptyList()
        return runCatching { AtProtoJson.decodeFromString<List<SavedFeedPref>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun setSavedFeeds(did: String, feeds: List<SavedFeedPref>) {
        dataStore.edit { it[stringPreferencesKey("saved_feeds_$did")] = AtProtoJson.encodeToString(feeds) }
    }

    fun observeSavedFeeds(did: String): Flow<List<SavedFeedPref>> =
        dataStore.data.map { prefs ->
            val raw = prefs[stringPreferencesKey("saved_feeds_$did")] ?: return@map emptyList()
            runCatching { AtProtoJson.decodeFromString<List<SavedFeedPref>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun getModerationSettings(did: String): ModerationUserSettings {
        val raw = dataStore.data.first()[stringPreferencesKey("mod_settings_$did")] ?: return ModerationUserSettings()
        return runCatching { AtProtoJson.decodeFromString<ModerationUserSettings>(raw) }
            .getOrDefault(ModerationUserSettings())
    }

    suspend fun updateModerationSettings(did: String, transform: (ModerationUserSettings) -> ModerationUserSettings) {
        dataStore.edit { prefs ->
            updateModerationSettings(prefs, did, transform)
        }
    }

    internal suspend fun applyServerActorPreferences(
        did: String,
        feeds: List<SavedFeedPref>,
        interests: List<String>?,
        transform: (ModerationUserSettings) -> ModerationUserSettings,
    ): ModerationUserSettings {
        val updated = dataStore.edit { prefs ->
            prefs[stringPreferencesKey("saved_feeds_$did")] = AtProtoJson.encodeToString(feeds)
            if (interests != null) prefs[interestsKey(did)] = AtProtoJson.encodeToString(interests)
            updateModerationSettings(prefs, did, transform)
        }
        return AtProtoJson.decodeFromString(checkNotNull(updated[stringPreferencesKey("mod_settings_$did")]))
    }

    private fun updateModerationSettings(
        prefs: MutablePreferences,
        did: String,
        transform: (ModerationUserSettings) -> ModerationUserSettings,
    ) {
        val key = stringPreferencesKey("mod_settings_$did")
        val previous = prefs[key]?.let {
            runCatching { AtProtoJson.decodeFromString<ModerationUserSettings>(it) }.getOrNull()
        } ?: ModerationUserSettings()
        val updated = transform(previous)
        if (previous.hiddenPosts != updated.hiddenPosts) {
            prefs[booleanPreferencesKey("hidden_posts_changed_$did")] = true
        }
        prefs[key] = AtProtoJson.encodeToString(updated)
    }

    fun observeModerationSettings(did: String): Flow<ModerationUserSettings> =
        dataStore.data.map { prefs ->
            val raw = prefs[stringPreferencesKey("mod_settings_$did")] ?: return@map ModerationUserSettings()
            runCatching { AtProtoJson.decodeFromString<ModerationUserSettings>(raw) }
                .getOrDefault(ModerationUserSettings())
        }

    suspend fun setExternalEmbedConsent(provider: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_EXTERNAL_EMBEDS]?.let {
                runCatching { AtProtoJson.decodeFromString<Map<String, Boolean>>(it) }.getOrNull()
            }.orEmpty()
            prefs[KEY_EXTERNAL_EMBEDS] = AtProtoJson.encodeToString(existing + (provider to allowed))
        }
    }

    suspend fun setInterests(did: String, interests: List<String>) {
        dataStore.edit { it[interestsKey(did)] = AtProtoJson.encodeToString(interests) }
    }

    internal suspend fun migrateLegacyInterests() {
        dataStore.edit { prefs ->
            val legacyInterests = prefs.remove(LEGACY_INTERESTS_JSON) ?: return@edit
            val accounts = prefs[KEY_ACCOUNTS_JSON]?.let {
                runCatching { AtProtoJson.decodeFromString<List<StoredAccount>>(it) }.getOrNull()
            }
            // An unscoped preference cannot be safely attributed when multiple accounts existed.
            val owner = accounts?.singleOrNull()?.did ?: return@edit
            prefs.importIfAbsent(interestsKey(owner), legacyInterests)
        }
    }

    suspend fun markLegacyRnMigrated() {
        dataStore.edit { it[KEY_LEGACY_RN_MIGRATED] = true }
    }

    internal suspend fun isLegacyMmkvMigrated(): Boolean =
        dataStore.data.first()[KEY_LEGACY_MMKV_MIGRATED] == true

    internal suspend fun importLegacyMmkv(
        searchableBookmarks: Boolean?,
        autoLikeReposts: Boolean?,
        openBskyLinksInCaelum: Boolean?,
        fontScale: String?,
        fontFamily: String?,
        selectedTheme: ThemeOption?,
        infrastructure: InfrastructureSettings?,
        hiddenPosts: Map<String, Set<String>>,
        recovery: Boolean,
        complete: Boolean,
    ): LegacyMmkvImportResult {
        var result = LegacyMmkvImportResult()
        dataStore.edit { prefs ->
            if (prefs[KEY_LEGACY_MMKV_MIGRATED] == true) return@edit
            prefs.importIfAbsent(KEY_SEARCHABLE_BOOKMARKS, searchableBookmarks)
            prefs.importIfAbsent(KEY_AUTO_LIKE_REPOSTS, autoLikeReposts)
            prefs.importIfAbsent(KEY_OPEN_BSKY_LINKS, openBskyLinksInCaelum)

            val importedScale = prefs.importIfAbsent(KEY_FONT_SCALE, fontScale)
            val importedFamily = prefs.importIfAbsent(KEY_FONT_FAMILY, fontFamily)
            val importedTheme = prefs.importIfAbsent(
                KEY_WITCHSKY_SELECTED,
                selectedTheme?.let { AtProtoJson.encodeToString(it) },
            )
            val importedInfra = prefs.importIfAbsent(
                KEY_INFRASTRUCTURE_JSON,
                infrastructure?.let { AtProtoJson.encodeToString(it) },
            )

            for ((did, posts) in hiddenPosts) {
                if (posts.isEmpty()) continue
                val key = stringPreferencesKey("mod_settings_$did")
                val current = prefs[key]?.let {
                    runCatching { AtProtoJson.decodeFromString<ModerationUserSettings>(it) }.getOrNull()
                } ?: ModerationUserSettings()
                if (recovery && (current.hiddenPosts.isNotEmpty() ||
                        prefs[booleanPreferencesKey("hidden_posts_changed_$did")] == true)
                ) continue
                prefs[key] = AtProtoJson.encodeToString(current.copy(hiddenPosts = current.hiddenPosts + posts))
            }

            if (complete) prefs[KEY_LEGACY_MMKV_MIGRATED] = true
            result = LegacyMmkvImportResult(importedScale || importedFamily || importedTheme, importedInfra)
        }
        return result
    }

    private fun <T> MutablePreferences.importIfAbsent(key: Preferences.Key<T>, value: T?): Boolean {
        if (value == null || this[key] != null) return false
        this[key] = value
        return true
    }

    private fun decodeSnapshot(prefs: Preferences): AppSettingsSnapshot {
        return AppSettingsSnapshot(
            accounts = prefs[KEY_ACCOUNTS_JSON]?.let {
                runCatching { AtProtoJson.decodeFromString<List<StoredAccount>>(it) }.getOrNull()
            }.orEmpty(),
            currentDid = prefs[KEY_CURRENT_DID],
            colorMode = prefs[KEY_COLOR_MODE] ?: "system",
            darkTheme = prefs[KEY_DARK_THEME] ?: "dim",
            dynamicColorEnabled = prefs[KEY_DYNAMIC_COLOR] ?: false,
            fontScale = prefs[KEY_FONT_SCALE] ?: "0",
            fontFamily = prefs[KEY_FONT_FAMILY] ?: "theme",
            witchskySelectedTheme = prefs[KEY_WITCHSKY_SELECTED]?.let {
                runCatching { AtProtoJson.decodeFromString<ThemeOption>(it) }.getOrNull()
            },
            witchskySavedThemes = prefs[KEY_WITCHSKY_SAVED]?.let {
                runCatching { AtProtoJson.decodeFromString<List<ThemeOption>>(it) }.getOrNull()
            }.orEmpty(),
            appIconId = prefs[KEY_APP_ICON] ?: "default_light",
            openBskyLinksInCaelum = prefs[KEY_OPEN_BSKY_LINKS] ?: true,
            autoLikeReposts = prefs[KEY_AUTO_LIKE_REPOSTS] ?: false,
            searchableBookmarks = prefs[KEY_SEARCHABLE_BOOKMARKS] ?: false,
            infrastructure = prefs[KEY_INFRASTRUCTURE_JSON]?.let {
                runCatching { AtProtoJson.decodeFromString<InfrastructureSettings>(it) }.getOrNull()
            } ?: InfrastructureSettings(),
            primaryLanguage = prefs[KEY_PRIMARY_LANGUAGE] ?: "en",
            contentLanguages = prefs[KEY_CONTENT_LANGUAGES]?.let {
                runCatching { AtProtoJson.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: listOf("en"),
            postLanguage = prefs[KEY_POST_LANGUAGE] ?: "en",
            postLanguageHistory = prefs[KEY_POST_LANGUAGE_HISTORY]?.let {
                runCatching { AtProtoJson.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: listOf("en"),
            appLanguage = prefs[KEY_APP_LANGUAGE] ?: "",
            requireAltTextEnabled = prefs[KEY_REQUIRE_ALT_TEXT] ?: false,
            largeAltBadgeEnabled = prefs[KEY_LARGE_ALT_BADGE] ?: false,
            disableAutoplay = prefs[KEY_DISABLE_AUTOPLAY] ?: false,
            hideTrendingTopics = prefs[KEY_HIDE_TRENDING_TOPICS] ?: false,
            disableHaptics = prefs[KEY_DISABLE_HAPTICS] ?: false,
            reducedMotion = prefs[KEY_REDUCED_MOTION] ?: false,
            subtitlesEnabled = prefs[KEY_SUBTITLES_ENABLED] ?: true,
            playSoundChat = prefs[KEY_PLAY_SOUND_CHAT] ?: true,
            externalEmbedsAllowList = prefs[KEY_EXTERNAL_EMBEDS]?.let {
                runCatching { AtProtoJson.decodeFromString<Map<String, Boolean>>(it) }.getOrNull()
            }.orEmpty(),
            interests = prefs[KEY_CURRENT_DID]?.let { prefs[interestsKey(it)] }?.let {
                runCatching { AtProtoJson.decodeFromString<List<String>>(it) }.getOrNull()
            }.orEmpty(),
            legacyRnMigrated = prefs[KEY_LEGACY_RN_MIGRATED] ?: false,
        )
    }

    companion object {
        const val RN_NOTIFICATION_PREFS_NAME = "xyz.blueskyweb.app"

        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_CURRENT_DID = stringPreferencesKey("current_did")
        private val KEY_COLOR_MODE = stringPreferencesKey("color_mode")
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        private val KEY_FONT_SCALE = stringPreferencesKey("font_scale")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_WITCHSKY_SELECTED = stringPreferencesKey("witchsky_selected_theme")
        private val KEY_WITCHSKY_SAVED = stringPreferencesKey("witchsky_saved_themes")
        private val KEY_APP_ICON = stringPreferencesKey("app_icon_id")
        private val KEY_OPEN_BSKY_LINKS = booleanPreferencesKey("open_bsky_links_in_caelum")
        private val KEY_AUTO_LIKE_REPOSTS = booleanPreferencesKey("auto_like_reposts")
        private val KEY_SEARCHABLE_BOOKMARKS = booleanPreferencesKey("searchable_bookmarks")
        private val KEY_INFRASTRUCTURE_JSON = stringPreferencesKey("infrastructure_json")
        private val KEY_PRIMARY_LANGUAGE = stringPreferencesKey("primary_language")
        private val KEY_CONTENT_LANGUAGES = stringPreferencesKey("content_languages_json")
        private val KEY_POST_LANGUAGE = stringPreferencesKey("post_language")
        private val KEY_POST_LANGUAGE_HISTORY = stringPreferencesKey("post_language_history_json")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_REQUIRE_ALT_TEXT = booleanPreferencesKey("require_alt_text")
        private val KEY_LARGE_ALT_BADGE = booleanPreferencesKey("large_alt_badge")
        private val KEY_DISABLE_AUTOPLAY = booleanPreferencesKey("disable_autoplay")
        private val KEY_HIDE_TRENDING_TOPICS = booleanPreferencesKey("hide_trending_topics")
        private val KEY_DISABLE_HAPTICS = booleanPreferencesKey("disable_haptics")
        private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val KEY_SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled")
        private val KEY_PLAY_SOUND_CHAT = booleanPreferencesKey("play_sound_chat")
        private val KEY_EXTERNAL_EMBEDS = stringPreferencesKey("external_embeds_json")
        private val LEGACY_INTERESTS_JSON = stringPreferencesKey("interests_json")
        private fun interestsKey(did: String) = stringPreferencesKey("interests_json_$did")
        private val KEY_LEGACY_MMKV_MIGRATED = booleanPreferencesKey("legacy_mmkv_migrated")
        private val KEY_LEGACY_RN_MIGRATED = booleanPreferencesKey("legacy_rn_migrated")

        fun create(
            context: Context,
            fileName: String = "caelum_prefs",
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): PreferencesRepository {
            val appCtx = context.applicationContext
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { appCtx.preferencesDataStoreFile(fileName) },
            )
            return PreferencesRepository(appCtx, store)
        }
    }
}
