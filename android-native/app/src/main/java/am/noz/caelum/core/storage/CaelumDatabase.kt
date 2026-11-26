package am.noz.caelum.core.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BookmarkView
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val accountDid: String,
    val payloadJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "feed_cache",
    primaryKeys = ["scopeKey", "feedUri"],
)
data class FeedCacheEntity(
    val scopeKey: String,
    val feedUri: String,
    val payloadJson: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "search_history",
    primaryKeys = ["accountDid", "kind", "query"],
)
data class SearchHistoryEntity(
    val accountDid: String,
    val kind: String,
    val query: String,
    val updatedAtMs: Long,
)

@Entity(
    tableName = "bookmark_index",
    primaryKeys = ["scopeKey", "postUri"],
)
data class BookmarkIndexEntity(
    val scopeKey: String,
    val postUri: String,
    val cid: String,
    val authorDid: String,
    val authorHandle: String,
    val authorDisplayName: String,
    val normalizedText: String,
    val rawJson: String,
    val indexedAtMs: Long,
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE accountDid = :did ORDER BY updatedAtMs DESC")
    fun observeForAccount(did: String): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE accountDid = :did ORDER BY updatedAtMs DESC")
    suspend fun getForAccount(did: String): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM drafts WHERE accountDid = :did")
    suspend fun clearForAccount(did: String)
}

@Dao
interface FeedCacheDao {
    @Query("SELECT * FROM feed_cache WHERE scopeKey = :scopeKey AND feedUri = :feedUri LIMIT 1")
    suspend fun get(scopeKey: String, feedUri: String): FeedCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FeedCacheEntity)

    @Query("DELETE FROM feed_cache WHERE scopeKey = :scopeKey")
    suspend fun clearScope(scopeKey: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE accountDid = :did ORDER BY updatedAtMs DESC LIMIT 30")
    fun observe(did: String): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE accountDid = :did AND query = :query")
    suspend fun delete(did: String, query: String)

    @Query("DELETE FROM search_history WHERE accountDid = :did")
    suspend fun clear(did: String)
}

@Dao
interface BookmarkIndexDao {
    @Query("SELECT * FROM bookmark_index WHERE scopeKey = :scopeKey ORDER BY indexedAtMs DESC")
    suspend fun getAllForScope(scopeKey: String): List<BookmarkIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BookmarkIndexEntity>)

    @Query("DELETE FROM bookmark_index WHERE scopeKey = :scopeKey AND postUri = :postUri")
    suspend fun deleteOne(scopeKey: String, postUri: String)

    @Query("DELETE FROM bookmark_index WHERE scopeKey = :scopeKey")
    suspend fun clearScope(scopeKey: String)

    @Query("SELECT COUNT(*) FROM bookmark_index WHERE scopeKey = :scopeKey")
    suspend fun countForScope(scopeKey: String): Int
}

@Database(
    entities = [
        DraftEntity::class,
        FeedCacheEntity::class,
        SearchHistoryEntity::class,
        BookmarkIndexEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CaelumDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun bookmarkIndexDao(): BookmarkIndexDao

    companion object {
        fun create(context: Context): CaelumDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CaelumDatabase::class.java,
                "caelum.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        fun createInMemory(context: Context): CaelumDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                CaelumDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

@Serializable
data class SearchableBookmarkSnapshot(
    val version: Int = 1,
    val scopeKey: String,
    val syncedAtMs: Long,
    val items: List<BookmarkView>,
)

/**
 * Searchable local bookmark index engine matching `src/features/searchableBookmarks/{database.ts,store.ts}`.
 * Performs NFKD accent-stripped multi-term and `@handle` matching plus gzipped snapshot round-trips.
 */
class SearchableBookmarkEngine(
    private val dao: BookmarkIndexDao,
) {
    suspend fun upsertBookmarks(scopeKey: String, bookmarks: List<BookmarkView>, nowMs: Long = System.currentTimeMillis()) {
        val entities = bookmarks.mapNotNull { bm ->
            val postObj = bm.item
            val authorObj = postObj["author"] as? kotlinx.serialization.json.JsonObject
            val authorDid = authorObj?.get("did")?.jsonPrimitive?.content.orEmpty()
            val authorHandle = authorObj?.get("handle")?.jsonPrimitive?.content.orEmpty()
            val authorDisplayName = (authorObj?.get("displayName") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val recordObj = postObj["record"] as? kotlinx.serialization.json.JsonObject
            val text = (recordObj?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val indexedAtMs = bm.createdAt?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: nowMs

            val searchableComposite = normalizeForSearch(
                "$text $authorDisplayName $authorHandle @$authorHandle"
            )
            BookmarkIndexEntity(
                scopeKey = scopeKey,
                postUri = bm.subject.uri,
                cid = bm.subject.cid,
                authorDid = authorDid,
                authorHandle = normalizeForSearch(authorHandle),
                authorDisplayName = authorDisplayName,
                normalizedText = searchableComposite,
                rawJson = AtProtoJson.encodeToString(bm),
                indexedAtMs = indexedAtMs,
            )
        }
        if (entities.isNotEmpty()) {
            dao.upsertAll(entities)
        }
    }

    suspend fun removeBookmark(scopeKey: String, postUri: String) {
        dao.deleteOne(scopeKey, postUri)
    }

    suspend fun search(scopeKey: String, query: String): List<BookmarkView> {
        val all = dao.getAllForScope(scopeKey)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return all.mapNotNull { runCatching { AtProtoJson.decodeFromString<BookmarkView>(it.rawJson) }.getOrNull() }
        }
        val terms = normalizeForSearch(trimmed)
            .split(' ')
            .filter { it.isNotEmpty() }

        return all.filter { entity ->
            terms.all { term ->
                if (term.startsWith("@")) {
                    val handleTerm = term.removePrefix("@")
                    normalizeForSearch(entity.authorHandle).contains(handleTerm)
                } else {
                    entity.normalizedText.contains(term)
                }
            }
        }.mapNotNull { entity ->
            runCatching { AtProtoJson.decodeFromString<BookmarkView>(entity.rawJson) }.getOrNull()
        }
    }

    suspend fun exportGzipSnapshot(scopeKey: String, nowMs: Long = System.currentTimeMillis()): ByteArray {
        val items = search(scopeKey, "")
        val payload = AtProtoJson.encodeToString(
            SearchableBookmarkSnapshot(scopeKey = scopeKey, syncedAtMs = nowMs, items = items)
        )
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gz ->
            gz.write(payload.toByteArray(Charsets.UTF_8))
        }
        return baos.toByteArray()
    }

    suspend fun importGzipSnapshot(gzipped: ByteArray): Int {
        val jsonText = GZIPInputStream(ByteArrayInputStream(gzipped)).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
        val snapshot = AtProtoJson.decodeFromString<SearchableBookmarkSnapshot>(jsonText)
        upsertBookmarks(snapshot.scopeKey, snapshot.items, snapshot.syncedAtMs)
        return snapshot.items.size
    }

    companion object {
        fun snapshotFile(filesDir: java.io.File, scopeKey: String): java.io.File {
            val safeKey = scopeKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            return java.io.File(filesDir, "bookmarks-snapshot-$safeKey.json.gz")
        }

        fun scopeKeyFor(did: String, appviewHostname: String): String {
            val host = appviewHostname.ifBlank { "api.bsky.app" }
            return "$did|$host"
        }

        fun normalizeRnScopeKey(rawKey: String): String? {
            if (rawKey.startsWith("[")) {
                val arr = runCatching { AtProtoJson.decodeFromString<List<String>>(rawKey) }.getOrNull()
                if (arr != null && arr.size >= 2) {
                    return scopeKeyFor(arr[0], arr[1])
                }
            }
            if ('|' in rawKey) return rawKey
            return null
        }

        fun normalizeForSearch(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFKD)
            return COMBINING_MARKS.replace(decomposed, "")
                .replace(MULTI_SPACE, " ")
                .trim()
        }

        private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val MULTI_SPACE = Regex("\\s+")
    }
}
