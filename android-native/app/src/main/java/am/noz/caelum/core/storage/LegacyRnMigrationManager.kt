package am.noz.caelum.core.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.network.InfrastructureConfig
import am.noz.caelum.core.network.InfrastructureSettings
import am.noz.caelum.ui.theme.ThemeEngine
import am.noz.caelum.ui.theme.ThemeOption
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class LegacyMigrationReport(
    val alreadyMigrated: Boolean = false,
    val migratedAccountCount: Int = 0,
    val currentDid: String? = null,
    val migratedAppearance: Boolean = false,
    val migratedInfrastructure: Boolean = false,
    val migratedBookmarksCount: Int = 0,
)

/**
 * Idempotent one-time importer from existing RN/Expo app storage:
 * - AsyncStorage SQLite `RKStorage` (`catalystLocalStorage`, key `BSKY_STORAGE`)
 * - MMKV key-value envelopes (`{"data": ...}`) from `bsky_device`, `bsky_account`
 * - `xyz.blueskyweb.app` SharedPreferences (`playSoundChat`, `disabledPushReasons:*`)
 *
 * Ensures upgrade compatibility from RN `am.noz.caelum` without requiring users to sign in again.
 */
class LegacyRnMigrationManager(
    private val context: Context,
    private val preferences: PreferencesRepository,
    private val tokenVault: TokenVault,
) {
    suspend fun migrateIfNeeded(): LegacyMigrationReport {
        val alreadyMigrated = preferences.currentSnapshot().legacyRnMigrated
        val report = if (alreadyMigrated) {
            LegacyMigrationReport(alreadyMigrated = true)
        } else {
            migrateFromPayloads(bskyStorageJson = readBskyStorageFromSqlite())
        }

        val imported = if (preferences.isLegacyMmkvMigrated()) {
            LegacyMmkvImportResult()
        } else {
            val mmkvDir = File(context.applicationContext.filesDir, "mmkv")
            val device = LegacyMmkvReader.read(File(mmkvDir, "bsky_device"))
            val account = LegacyMmkvReader.read(File(mmkvDir, "bsky_account"))
            importMmkv(
                device.orEmpty(),
                account.orEmpty(),
                recovery = alreadyMigrated,
                complete = device != null && account != null,
            )
        }
        if (!alreadyMigrated) preferences.markLegacyRnMigrated()
        return report.copy(
            migratedAppearance = report.migratedAppearance || imported.appearance,
            migratedInfrastructure = imported.infrastructure,
        )
    }

    suspend fun migrateFromPayloads(
        bskyStorageJson: String?,
        deviceMmkv: Map<String, JsonElement> = emptyMap(),
        accountMmkv: Map<String, JsonElement> = emptyMap(),
    ): LegacyMigrationReport {
        var accountCount = 0
        var activeDid: String? = null
        var migratedAppearance = false

        // 1. AsyncStorage BSKY_STORAGE
        if (!bskyStorageJson.isNullOrBlank()) {
            val root = runCatching { AtProtoJson.parseToJsonElement(bskyStorageJson).jsonObject }.getOrNull()
            if (root != null) {
                root["colorMode"]?.jsonPrimitive?.content?.let {
                    preferences.setColorMode(it)
                    migratedAppearance = true
                }
                root["darkTheme"]?.jsonPrimitive?.content?.let {
                    preferences.setDarkTheme(it)
                    migratedAppearance = true
                }

                val sessionObj = root["session"] as? JsonObject
                val currentObj = sessionObj?.get("currentAccount") as? JsonObject
                activeDid = (currentObj?.get("did") as? JsonPrimitive)?.content

                val rawAccounts = sessionObj?.get("accounts") as? JsonArray
                if (rawAccounts != null) {
                    val migratedAccounts = ArrayList<StoredAccount>(rawAccounts.size)
                    for (el in rawAccounts) {
                        val acc = el as? JsonObject ?: continue
                        val did = acc.str("did") ?: continue
                        val handle = acc.str("handle") ?: continue
                        val serviceUrl = acc.str("service") ?: acc.str("serviceUrl") ?: "https://bsky.social"
                        val pdsUrl = acc.str("pdsUrl")
                        val accessJwt = acc.str("accessJwt")
                        val refreshJwt = acc.str("refreshJwt")
                        if (!accessJwt.isNullOrBlank() && !refreshJwt.isNullOrBlank()) {
                            tokenVault.saveTokens(did, SessionTokens(accessJwt = accessJwt, refreshJwt = refreshJwt))
                        }
                        migratedAccounts.add(
                            StoredAccount(
                                did = did,
                                handle = handle,
                                serviceUrl = serviceUrl,
                                pdsUrl = pdsUrl,
                                email = acc.str("email"),
                                emailConfirmed = acc.bool("emailConfirmed") ?: true,
                                emailAuthFactor = acc.bool("emailAuthFactor") ?: false,
                                signupQueued = acc.bool("signupQueued") ?: false,
                                active = acc.bool("active") ?: true,
                                status = acc.str("status"),
                                isSelfHosted = acc.bool("isSelfHosted") ?: false,
                            )
                        )
                    }
                    if (migratedAccounts.isNotEmpty()) {
                        preferences.updateAccounts(migratedAccounts, activeDid ?: migratedAccounts.first().did)
                        accountCount = migratedAccounts.size
                    }
                }

                val langObj = root["languagePrefs"] as? JsonObject
                if (langObj != null) {
                    preferences.setLanguages(
                        primaryLanguage = langObj.str("primaryLanguage"),
                        contentLanguages = langObj.strList("contentLanguages"),
                        postLanguage = langObj.str("postLanguage"),
                        postLanguageHistory = langObj.strList("postLanguageHistory"),
                        appLanguage = langObj.str("appLanguage"),
                    )
                }

                preferences.setAccessibilityAndMedia(
                    requireAltText = root.bool("requireAltTextEnabled"),
                    largeAltBadge = root.bool("largeAltBadgeEnabled"),
                    disableAutoplay = root.bool("disableAutoplay"),
                    disableHaptics = root.bool("disableHaptics"),
                )

                val mutedThreads = root.strList("mutedThreads")?.toSet()
                val targetDid = activeDid
                if (!targetDid.isNullOrBlank() && mutedThreads != null) {
                    preferences.updateModerationSettings(targetDid) {
                        it.copy(mutedThreads = it.mutedThreads + mutedThreads)
                    }
                }
            }
        }

        val imported = if (deviceMmkv.isEmpty() && accountMmkv.isEmpty()) {
            LegacyMmkvImportResult()
        } else {
            importMmkv(deviceMmkv, accountMmkv, recovery = false, complete = false)
        }

        // 4. SharedPreferences `"xyz.blueskyweb.app"` notification sound sync
        val rnPrefs = context.applicationContext.getSharedPreferences(
            PreferencesRepository.RN_NOTIFICATION_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        if (rnPrefs.contains("playSoundChat")) {
            preferences.setPlaySoundChat(rnPrefs.getBoolean("playSoundChat", true))
        }

        return LegacyMigrationReport(
            alreadyMigrated = false,
            migratedAccountCount = accountCount,
            currentDid = activeDid,
            migratedAppearance = migratedAppearance || imported.appearance,
            migratedInfrastructure = imported.infrastructure,
        )
    }

    private fun readBskyStorageFromSqlite(): String? {
        val dbFile = context.applicationContext.getDatabasePath("RKStorage")
        if (!dbFile.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT value FROM catalystLocalStorage WHERE key = ? LIMIT 1",
                    arrayOf("BSKY_STORAGE"),
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
        }.getOrNull()
    }

    private suspend fun importMmkv(
        device: Map<String, JsonElement>,
        account: Map<String, JsonElement>,
        recovery: Boolean,
        complete: Boolean,
    ): LegacyMmkvImportResult {
        val hiddenPosts = account.mapNotNull { (key, wrapped) ->
            val did = key.removeSuffix(":hiddenPosts")
            if (did == key || !did.startsWith("did:")) return@mapNotNull null
            val posts = unwrapMmkvData(wrapped) as? JsonArray ?: return@mapNotNull null
            did to posts.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }.toSet()
        }.toMap()
        // Earlier native versions removed the key when a user selected the default theme.
        val selectedTheme = if (recovery) {
            null
        } else {
            val currentDid = preferences.currentSnapshot().currentDid
            currentDid?.let { account["$it:witchskyTheme"] } ?: device["witchskySelectedTheme"]
        }
        val theme = (unwrapMmkvData(selectedTheme) as? JsonObject)?.let { obj ->
            runCatching { AtProtoJson.decodeFromJsonElement<ThemeOption>(obj) }.getOrNull()
                ?: runCatching {
                    ThemeEngine.parseThemeRecord(obj.str("uri") ?: "builtin://caelum", obj).firstOrNull()
                }.getOrNull()
        }
        val infrastructure = (unwrapMmkvData(device["infrastructure"]) as? JsonObject)?.let { obj ->
            InfrastructureSettings(
                appviewHostname = runCatching { InfrastructureConfig.normalizeAppview(obj.str("appviewHostname").orEmpty()) }.getOrDefault(""),
                imageCdnHostname = runCatching { InfrastructureConfig.normalizeImageCdn(obj.str("imageCdnHostname").orEmpty()) }.getOrDefault(""),
                plcDirectoryHostname = runCatching { InfrastructureConfig.normalizePlcDirectory(obj.str("plcDirectoryHostname").orEmpty()) }.getOrDefault(""),
                useLinkRedirects = obj.bool("useLinkRedirects") ?: true,
            )
        }
        return preferences.importLegacyMmkv(
            searchableBookmarks = unwrapMmkvData(device["searchableBookmarks"])?.boolVal(),
            autoLikeReposts = unwrapMmkvData(device["autoLikeReposts"])?.boolVal(),
            openBskyLinksInCaelum = unwrapMmkvData(device["openBskyLinksInCaelum"])?.boolVal(),
            fontScale = unwrapMmkvData(device["fontScale"])?.strVal(),
            fontFamily = unwrapMmkvData(device["fontFamily"])?.strVal(),
            selectedTheme = theme,
            infrastructure = infrastructure,
            hiddenPosts = hiddenPosts,
            recovery = recovery,
            complete = complete,
        )
    }

    private fun unwrapMmkvData(el: JsonElement?): JsonElement? {
        if (el == null) return null
        return (el as? JsonObject)?.get("data") ?: el
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.strList(key: String): List<String>? =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    private fun JsonElement.boolVal(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
    private fun JsonElement.strVal(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
