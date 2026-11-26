package am.noz.caelum.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BookmarkView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.model.StrongRef
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LegacyRnMigrationTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestPrefsRepo(): PreferencesRepository {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dsFile = File(tempFolder.newFolder(), "test_prefs.preferences_pb")
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { dsFile })
        return PreferencesRepository(ctx, ds)
    }

    @Test
    fun `migrates BSKY_STORAGE and MMKV envelopes into native storage and TokenVault`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = createTestPrefsRepo()
        val vault = InMemoryTokenVault()
        val manager = LegacyRnMigrationManager(ctx, repo, vault)

        val fixtureRaw = LegacyRnMigrationTest::class.java.classLoader!!
            .getResource("fixtures/notifications_and_migration.json")!!
            .readText()
        val root = AtProtoJson.parseToJsonElement(fixtureRaw).jsonObject
        val bskyStorageJson = root["legacyBskyStorage"]!!.toString()
        val mmkvMap = root["legacyDeviceMmkv"]!!.jsonObject

        val report = manager.migrateFromPayloads(
            bskyStorageJson = bskyStorageJson,
            deviceMmkv = mmkvMap,
            accountMmkv = emptyMap(),
        )

        assertEquals(1, report.migratedAccountCount)
        assertEquals("did:plc:legacyuser1", report.currentDid)
        assertTrue(report.migratedAppearance)
        assertTrue(report.migratedInfrastructure)

        val tokens = vault.getTokens("did:plc:legacyuser1")
        assertNotNull(tokens)
        assertEquals("legacy-access-jwt-token", tokens!!.accessJwt)
        assertEquals("legacy-refresh-jwt-token", tokens.refreshJwt)

        val snap = repo.currentSnapshot()
        assertEquals("dark", snap.colorMode)
        assertEquals("dim", snap.darkTheme)
        assertEquals("did:plc:legacyuser1", snap.currentDid)
        assertEquals(1, snap.accounts.size)
        assertEquals("https://morel.us-east.host.bsky.network", snap.accounts.first().pdsUrl)
        assertEquals(true, snap.searchableBookmarks)
        assertEquals(true, snap.autoLikeReposts)
        assertEquals(false, snap.infrastructure.useLinkRedirects)
        assertEquals("api.eurosky.network", snap.infrastructure.appviewHostname)
        assertEquals("luna.danielmorrisey.com", snap.infrastructure.imageCdnHostname)
        assertEquals("plc.eurosky.network", snap.infrastructure.plcDirectoryHostname)
        assertEquals("1", snap.fontScale)
        assertEquals(listOf("en", "es"), snap.contentLanguages)
        assertEquals(true, snap.requireAltTextEnabled)

        val mod = repo.getModerationSettings("did:plc:legacyuser1")
        assertTrue("at://did:plc:abc/app.bsky.feed.post/123" in mod.mutedThreads)
    }

    @Test
    fun `migrates preferences from files written by the bundled MMKV Core`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        installMmkvFixture(ctx)
        val repo = createTestPrefsRepo()
        restoreNativeAccount(repo)
        val manager = LegacyRnMigrationManager(ctx, repo, InMemoryTokenVault())
        val report = manager.migrateIfNeeded()

        assertTrue(report.migratedInfrastructure)
        assertTrue(report.migratedAppearance)
        val settings = repo.currentSnapshot()
        assertEquals("2", settings.fontScale)
        assertTrue(settings.searchableBookmarks)
        assertFalse(settings.autoLikeReposts)
        assertEquals("api.eurosky.network", settings.infrastructure.appviewHostname)
        assertFalse(settings.infrastructure.useLinkRedirects)
        assertEquals("Café", settings.witchskySelectedTheme?.name)
        assertEquals(setOf("at://did:plc:a/app.bsky.feed.post/1"), repo.getModerationSettings(LEGACY_DID).hiddenPosts)
        assertTrue(manager.migrateIfNeeded().alreadyMigrated)
    }

    @Test
    fun `recovers missed MMKV data while preserving explicit native settings`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val legacyFiles = installMmkvFixture(ctx)
        val repo = createTestPrefsRepo()
        repo.markLegacyRnMigrated()
        restoreNativeAccount(repo)
        repo.setFontScale("0")
        // An old native reset removed the theme key, leaving the same state as an unset theme.
        val nativePost = "at://did:plc:b/app.bsky.feed.post/2"
        repo.updateModerationSettings(LEGACY_DID) { it.copy(hiddenPosts = setOf(nativePost)) }
        val manager = LegacyRnMigrationManager(ctx, repo, InMemoryTokenVault())

        val report = manager.migrateIfNeeded()
        assertTrue(report.alreadyMigrated)
        assertTrue(report.migratedInfrastructure)
        val settings = repo.currentSnapshot()
        assertEquals("native.bsky.social", settings.accounts.single().handle)
        assertEquals("0", settings.fontScale)
        assertNull(settings.witchskySelectedTheme)
        assertTrue(settings.searchableBookmarks)
        assertEquals(setOf(nativePost), repo.getModerationSettings(LEGACY_DID).hiddenPosts)

        repo.setSearchableBookmarks(false)
        assertFalse(manager.migrateIfNeeded().migratedInfrastructure)
        assertFalse(repo.currentSnapshot().searchableBookmarks)
        for ((file, bytes) in legacyFiles) {
            assertTrue(bytes.contentEquals(file.readBytes()))
        }
    }

    private suspend fun restoreNativeAccount(repo: PreferencesRepository) {
        repo.updateAccounts(
            listOf(StoredAccount(LEGACY_DID, "native.bsky.social", "https://bsky.social")),
            LEGACY_DID,
        )
    }

    /**
     * These files come from the MMKV Core bundled with react-native-mmkv 3.3.3.
     * The device store includes an overwritten value and a deleted key.
     */
    private fun installMmkvFixture(context: Context): Map<File, ByteArray> {
        val directory = File(context.filesDir, "mmkv").apply { mkdirs() }
        return listOf("bsky_device", "bsky_device.crc", "bsky_account", "bsky_account.crc").associate { name ->
            val bytes = javaClass.getResourceAsStream("/fixtures/legacy-mmkv/$name")!!.use { it.readBytes() }
            File(directory, name).apply { writeBytes(bytes) } to bytes
        }
    }

    private companion object {
        const val LEGACY_DID = "did:plc:legacyuser1"
    }

    @Test
    fun `searchable bookmarks Room index performs NFKD search and gzip roundtrip`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = CaelumDatabase.createInMemory(ctx)
        try {
            val engine = SearchableBookmarkEngine(db.bookmarkIndexDao())
            val scope = SearchableBookmarkEngine.scopeKeyFor("did:plc:me", "api.bsky.app")

            val postObj: JsonObject = buildJsonObject {
                put("uri", JsonPrimitive("at://did:plc:a/app.bsky.feed.post/1"))
                put("cid", JsonPrimitive("bafy1"))
                put(
                    "author",
                    buildJsonObject {
                        put("did", JsonPrimitive("did:plc:a"))
                        put("handle", JsonPrimitive("café.bsky.social"))
                        put("displayName", JsonPrimitive("Café Owner"))
                    },
                )
                put(
                    "record",
                    buildJsonObject {
                        put("text", JsonPrimitive("Fresh crème brûlée recipe with espresso"))
                    },
                )
            }
            val bm = BookmarkView(
                subject = StrongRef("at://did:plc:a/app.bsky.feed.post/1", "bafy1"),
                createdAt = "2026-04-01T12:00:00Z",
                item = postObj,
            )

            engine.upsertBookmarks(scope, listOf(bm))
            assertEquals(1, engine.search(scope, "creme brulee").size)
            assertEquals(1, engine.search(scope, "@cafe").size)
            assertEquals(0, engine.search(scope, "nonexistent").size)

            val gzipped = engine.exportGzipSnapshot(scope)
            db.bookmarkIndexDao().clearScope(scope)
            assertEquals(0, db.bookmarkIndexDao().countForScope(scope))

            val restored = engine.importGzipSnapshot(gzipped)
            assertEquals(1, restored)
            assertEquals(1, engine.search(scope, "espresso").size)

            assertEquals(
                "did:plc:abc|api.bsky.app",
                SearchableBookmarkEngine.normalizeRnScopeKey("""["did:plc:abc","api.bsky.app"]"""),
            )
        } finally {
            db.close()
        }
    }
}
