package am.noz.caelum.core.network

import am.noz.caelum.core.model.ActivitySubscription
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BlobRef
import am.noz.caelum.core.model.BookmarkView
import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.CreateSessionResponse
import am.noz.caelum.core.model.DraftView
import am.noz.caelum.core.model.FeedResponse
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.NotificationItem
import am.noz.caelum.core.model.NotificationPreferencesResponse
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StarterPackViewBasic
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.model.ThreadV2Response
import am.noz.caelum.core.model.TrendTopicView
import am.noz.caelum.core.richtext.RichTextFacet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Serializable
data class CreateRecordResponse(
    val uri: String,
    val cid: String,
)

@Serializable
data class SearchActorsResponse(
    val cursor: String? = null,
    val actors: List<ProfileView> = emptyList(),
)

@Serializable
data class SearchActorsTypeaheadResponse(
    val actors: List<ProfileViewBasic> = emptyList(),
)

@Serializable
data class SearchPostsResponse(
    val cursor: String? = null,
    val posts: List<PostView> = emptyList(),
)

@Serializable
data class BookmarksResponse(
    val cursor: String? = null,
    val bookmarks: List<BookmarkView> = emptyList(),
)

@Serializable
data class NotificationsResponse(
    val cursor: String? = null,
    val notifications: List<NotificationItem> = emptyList(),
    val seenAt: String? = null,
)

@Serializable
data class ListConvosResponse(
    val cursor: String? = null,
    val convos: List<ConvoView> = emptyList(),
)

@Serializable
data class ConvoAvailability(
    val canChat: Boolean,
    val convo: ConvoView? = null,
)

@Serializable
data class ConvoMessagesResponse(
    val cursor: String? = null,
    val messages: List<JsonObject> = emptyList(),
    val relatedProfiles: List<ProfileViewBasic> = emptyList(),
)

@Serializable
data class ConvoMembersResponse(
    val cursor: String? = null,
    val members: List<ProfileViewBasic> = emptyList(),
)

@Serializable
data class PdsListRecordsItem(
    val uri: String,
    val cid: String,
    val value: JsonObject,
)

@Serializable
data class PdsListRecordsResponse(
    val cursor: String? = null,
    val records: List<PdsListRecordsItem> = emptyList(),
)

@Serializable
data class VideoJobStatus(
    val jobId: String,
    val did: String,
    val state: String,
    val progress: Int? = null,
    val blob: BlobRef? = null,
    val error: String? = null,
    val message: String? = null,
)

/**
 * Full ATProto + Bluesky + Caelum AppView/PDS/Chat/Push/Discovery typed client.
 */
class AtprotoApi(
    private val client: XrpcClient,
) {
    // -------------------------------------------------------------------------
    // Auth & Session
    // -------------------------------------------------------------------------

    suspend fun createSession(
        serviceOrigin: String,
        identifier: String,
        password: String,
        authFactorToken: String? = null,
    ): CreateSessionResponse {
        val body = buildJsonObject {
            put("identifier", JsonPrimitive(identifier))
            put("password", JsonPrimitive(password))
            if (!authFactorToken.isNullOrBlank()) {
                put("authFactorToken", JsonPrimitive(authFactorToken))
            }
        }
        val res = client.postJson(
            nsid = "com.atproto.server.createSession",
            body = body,
            baseUrlOverride = serviceOrigin,
            authenticated = false,
        )
        return AtProtoJson.decodeFromJsonElement(res)
    }

    suspend fun getSession(): JsonObject {
        return client.getJson("com.atproto.server.getSession").jsonObject
    }

    suspend fun describeServer(serviceOrigin: String): JsonObject {
        return client.getJson(
            nsid = "com.atproto.server.describeServer",
            baseUrlOverride = serviceOrigin,
            authenticated = false,
        ).jsonObject
    }

    suspend fun createAccount(
        serviceOrigin: String,
        email: String,
        handle: String,
        password: String,
        inviteCode: String? = null,
        verificationCode: String? = null,
    ): CreateSessionResponse {
        val body = buildJsonObject {
            put("email", JsonPrimitive(email))
            put("handle", JsonPrimitive(handle))
            put("password", JsonPrimitive(password))
            inviteCode?.takeIf { it.isNotBlank() }?.let { put("inviteCode", JsonPrimitive(it)) }
            verificationCode?.takeIf { it.isNotBlank() }?.let { put("verificationCode", JsonPrimitive(it)) }
        }
        val res = client.postJson(
            nsid = "com.atproto.server.createAccount",
            body = body,
            baseUrlOverride = serviceOrigin,
            authenticated = false,
        )
        return AtProtoJson.decodeFromJsonElement(res)
    }

    suspend fun requestPasswordReset(serviceOrigin: String, email: String) {
        client.postJson(
            nsid = "com.atproto.server.requestPasswordReset",
            body = buildJsonObject { put("email", JsonPrimitive(email)) },
            baseUrlOverride = serviceOrigin,
            authenticated = false,
        )
    }

    suspend fun resetPassword(serviceOrigin: String, token: String, password: String) {
        client.postJson(
            nsid = "com.atproto.server.resetPassword",
            body = buildJsonObject {
                put("token", JsonPrimitive(token))
                put("password", JsonPrimitive(password))
            },
            baseUrlOverride = serviceOrigin,
            authenticated = false,
        )
    }

    suspend fun requestEmailConfirmation() {
        client.postJson("com.atproto.server.requestEmailConfirmation")
    }

    suspend fun confirmEmail(email: String, token: String) {
        client.postJson(
            nsid = "com.atproto.server.confirmEmail",
            body = buildJsonObject {
                put("email", JsonPrimitive(email))
                put("token", JsonPrimitive(token))
            },
        )
    }

    suspend fun listAppPasswords(): JsonObject =
        client.getJson("com.atproto.server.listAppPasswords").jsonObject

    suspend fun createAppPassword(name: String, privileged: Boolean = false): JsonObject =
        client.postJson(
            nsid = "com.atproto.server.createAppPassword",
            body = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("privileged", JsonPrimitive(privileged))
            },
        ).jsonObject

    suspend fun revokeAppPassword(name: String) {
        client.postJson(
            nsid = "com.atproto.server.revokeAppPassword",
            body = buildJsonObject { put("name", JsonPrimitive(name)) },
        )
    }

    // -------------------------------------------------------------------------
    // Username Typeahead (Caelum login customization: typeahead.waow.tech)
    // -------------------------------------------------------------------------

    suspend fun searchLoginUsernameSuggestions(query: String, limit: Int = 5): List<ProfileViewBasic> {
        val res = client.getJson(
            nsid = "app.bsky.actor.searchActorsTypeahead",
            params = listOf("q" to query, "limit" to limit.toString()),
            baseUrlOverride = LOGIN_TYPEAHEAD_ORIGIN,
            authenticated = false,
            extraHeaders = mapOf("X-Client" to "bsky.app"),
        )
        return AtProtoJson.decodeFromJsonElement<SearchActorsTypeaheadResponse>(res).actors
    }

    // -------------------------------------------------------------------------
    // Feeds & Threads
    // -------------------------------------------------------------------------

    suspend fun getTimeline(cursor: String? = null, limit: Int = 30): FeedResponse {
        val params = buildList {
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.feed.getTimeline", params))
    }

    suspend fun getFeed(feedUri: String, cursor: String? = null, limit: Int = 30): FeedResponse {
        val params = buildList {
            add("feed" to feedUri)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.feed.getFeed", params))
    }

    suspend fun getAuthorFeed(
        actor: String,
        filter: String = "posts_with_replies",
        cursor: String? = null,
        limit: Int = 30,
        includePins: Boolean = false,
    ): FeedResponse {
        val params = buildList {
            add("actor" to actor)
            add("filter" to filter)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val nsid = "app.bsky.feed.getAuthorFeed"
        val response = try {
            client.getJson(nsid, if (includePins) params + ("includePins" to "true") else params)
        } catch (error: XrpcException) {
            if (!includePins || error.statusCode != 400 ||
                !error.message.contains("includePins", ignoreCase = true)
            ) throw error
            client.getJson(nsid, params)
        }
        return AtProtoJson.decodeFromJsonElement(response)
    }

    suspend fun getActorLikes(actor: String, cursor: String? = null, limit: Int = 30): FeedResponse {
        val params = buildList {
            add("actor" to actor)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.feed.getActorLikes", params))
    }

    suspend fun getListFeed(listUri: String, cursor: String? = null, limit: Int = 30): FeedResponse {
        val params = buildList {
            add("list" to listUri)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.feed.getListFeed", params))
    }

    suspend fun getPosts(uris: List<String>): List<PostView> {
        if (uris.isEmpty()) return emptyList()
        val params = uris.take(25).map { "uris" to it }
        val res = client.getJson("app.bsky.feed.getPosts", params).jsonObject
        val arr = res["posts"]?.jsonArray ?: return emptyList()
        return arr.map { AtProtoJson.decodeFromJsonElement<PostView>(it) }
    }

    suspend fun getPostThreadV2(
        anchorUri: String,
        above: Boolean = true,
        below: Int = 6,
        branchingFactor: Int? = null,
        sort: String = "newest",
    ): ThreadV2Response {
        val params = buildList {
            add("anchor" to anchorUri)
            add("above" to above.toString())
            add("below" to below.toString())
            branchingFactor?.let { add("branchingFactor" to it.toString()) }
            add("sort" to sort)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.unspecced.getPostThreadV2", params))
    }

    suspend fun getPostThreadOtherV2(anchorUri: String): ThreadV2Response {
        val response = client.getJson("app.bsky.unspecced.getPostThreadOtherV2", listOf("anchor" to anchorUri))
        return AtProtoJson.decodeFromJsonElement(response)
    }

    suspend fun getPostThread(uri: String, depth: Int = 6, parentHeight: Int = 80): JsonObject {
        return client.getJson(
            nsid = "app.bsky.feed.getPostThread",
            params = listOf("uri" to uri, "depth" to depth.toString(), "parentHeight" to parentHeight.toString()),
        ).jsonObject
    }

    suspend fun getLikedBy(uri: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("uri" to uri)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.feed.getLikes", params).jsonObject
    }

    suspend fun getRepostedBy(uri: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("uri" to uri)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.feed.getRepostedBy", params).jsonObject
    }

    suspend fun getQuotes(uri: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("uri" to uri)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.feed.getQuotes", params).jsonObject
    }

    // -------------------------------------------------------------------------
    // Repo Records, Interactions & Caelum 10-Min Post Edit
    // -------------------------------------------------------------------------

    suspend fun createRecord(repo: String, collection: String, record: JsonObject, rkey: String? = null): CreateRecordResponse {
        val body = buildJsonObject {
            put("repo", JsonPrimitive(repo))
            put("collection", JsonPrimitive(collection))
            rkey?.let { put("rkey", JsonPrimitive(it)) }
            put("record", record)
        }
        return AtProtoJson.decodeFromJsonElement(client.postJson("com.atproto.repo.createRecord", body))
    }

    suspend fun putRecord(repo: String, collection: String, rkey: String, record: JsonObject): CreateRecordResponse {
        val body = buildJsonObject {
            put("repo", JsonPrimitive(repo))
            put("collection", JsonPrimitive(collection))
            put("rkey", JsonPrimitive(rkey))
            put("record", record)
        }
        return AtProtoJson.decodeFromJsonElement(client.postJson("com.atproto.repo.putRecord", body))
    }

    suspend fun deleteRecord(repo: String, collection: String, rkey: String) {
        client.postJson(
            nsid = "com.atproto.repo.deleteRecord",
            body = buildJsonObject {
                put("repo", JsonPrimitive(repo))
                put("collection", JsonPrimitive(collection))
                put("rkey", JsonPrimitive(rkey))
            },
        )
    }

    suspend fun likePost(repo: String, subject: StrongRef, via: StrongRef? = null): CreateRecordResponse {
        val record = buildJsonObject {
            put("\$type", JsonPrimitive("app.bsky.feed.like"))
            put("subject", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), subject))
            via?.let { put("via", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), it)) }
            put("createdAt", JsonPrimitive(Instant.now().toString()))
        }
        return createRecord(repo, "app.bsky.feed.like", record)
    }

    suspend fun repostPost(repo: String, subject: StrongRef, via: StrongRef? = null): CreateRecordResponse {
        val record = buildJsonObject {
            put("\$type", JsonPrimitive("app.bsky.feed.repost"))
            put("subject", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), subject))
            via?.let { put("via", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), it)) }
            put("createdAt", JsonPrimitive(Instant.now().toString()))
        }
        return createRecord(repo, "app.bsky.feed.repost", record)
    }

    suspend fun setThreadMuted(rootUri: String, muted: Boolean) {
        client.postJson(
            nsid = if (muted) "app.bsky.graph.muteThread" else "app.bsky.graph.unmuteThread",
            body = buildJsonObject { put("root", JsonPrimitive(rootUri)) },
        )
    }

    suspend fun createBookmark(uri: String, cid: String) {
        client.postJson(
            nsid = "app.bsky.bookmark.createBookmark",
            body = buildJsonObject {
                put("uri", JsonPrimitive(uri))
                put("cid", JsonPrimitive(cid))
            },
        )
    }

    suspend fun deleteBookmark(uri: String) {
        client.postJson(
            nsid = "app.bsky.bookmark.deleteBookmark",
            body = buildJsonObject { put("uri", JsonPrimitive(uri)) },
        )
    }

    suspend fun getBookmarks(cursor: String? = null, limit: Int = 50): BookmarksResponse {
        val params = buildList {
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.bookmark.getBookmarks", params))
    }

    suspend fun applyWrites(repo: String, writes: JsonArray) {
        client.postJson(
            nsid = "com.atproto.repo.applyWrites",
            body = buildJsonObject {
                put("repo", JsonPrimitive(repo))
                put("validate", JsonPrimitive(true))
                put("writes", writes)
            },
        )
    }

    /**
     * Port of `src/state/queries/post-edit.ts`:
     * Recreates the post under the SAME `rkey` via `com.atproto.repo.applyWrites` (delete + create),
     * preserving `createdAt`, `reply`, `embed`, `langs`, `labels`, `tags`, and recording `originalText` & `updatedAt`.
     */
    suspend fun editPostWithinWindow(
        repo: String,
        postUri: String,
        existingRecord: JsonObject,
        newText: String,
        newFacets: List<RichTextFacet>,
    ): JsonObject {
        val rkey = postUri.substringAfterLast('/')
        val previousText = (existingRecord["text"] as? JsonPrimitive)?.content.orEmpty()
        val originalText = (existingRecord["originalText"] as? JsonPrimitive)?.content ?: previousText
        val nowIso = Instant.now().toString()

        val updatedRecord = buildJsonObject {
            for ((k, v) in existingRecord) {
                if (k != "text" && k != "facets" && k != "originalText" && k != "updatedAt") {
                    put(k, v)
                }
            }
            put("\$type", JsonPrimitive("app.bsky.feed.post"))
            put("text", JsonPrimitive(newText))
            if (newFacets.isNotEmpty()) {
                put("facets", AtProtoJson.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(RichTextFacet.serializer()), newFacets))
            }
            put("originalText", JsonPrimitive(originalText))
            put("updatedAt", JsonPrimitive(nowIso))
        }

        val writes = buildJsonArray {
            add(
                buildJsonObject {
                    put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#delete"))
                    put("collection", JsonPrimitive("app.bsky.feed.post"))
                    put("rkey", JsonPrimitive(rkey))
                }
            )
            add(
                buildJsonObject {
                    put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#create"))
                    put("collection", JsonPrimitive("app.bsky.feed.post"))
                    put("rkey", JsonPrimitive(rkey))
                    put("value", updatedRecord)
                }
            )
        }

        client.postJson(
            nsid = "com.atproto.repo.applyWrites",
            body = buildJsonObject {
                put("repo", JsonPrimitive(repo))
                put("writes", writes)
            },
        )
        return updatedRecord
    }

    suspend fun uploadBlob(bytes: ByteArray, mimeType: String): BlobRef {
        val res = client.uploadBinary("com.atproto.repo.uploadBlob", bytes, mimeType).jsonObject
        return AtProtoJson.decodeFromJsonElement(res["blob"]!!)
    }

    // -------------------------------------------------------------------------
    // Profile, Graph, Search, Explore & Feeds
    // -------------------------------------------------------------------------

    suspend fun getProfile(actor: String): ProfileViewDetailed =
        AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.actor.getProfile", listOf("actor" to actor)))

    suspend fun getProfiles(actors: List<String>): List<ProfileViewDetailed> {
        if (actors.isEmpty()) return emptyList()
        val res = client.getJson("app.bsky.actor.getProfiles", actors.take(25).map { "actors" to it }).jsonObject
        return res["profiles"]?.jsonArray?.map { AtProtoJson.decodeFromJsonElement<ProfileViewDetailed>(it) }.orEmpty()
    }

    suspend fun getSuggestions(cursor: String? = null, limit: Int = 25): SearchActorsResponse {
        val params = buildList {
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.actor.getSuggestions", params))
    }

    suspend fun searchActors(query: String, cursor: String? = null, limit: Int = 25): SearchActorsResponse {
        val params = buildList {
            add("q" to query)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.actor.searchActors", params))
    }

    suspend fun searchActorsTypeahead(query: String, limit: Int = 8): List<ProfileViewBasic> {
        val res = client.getJson(
            nsid = "app.bsky.actor.searchActorsTypeahead",
            params = listOf("q" to query, "limit" to limit.toString()),
        )
        return AtProtoJson.decodeFromJsonElement<SearchActorsTypeaheadResponse>(res).actors
    }

    suspend fun searchPosts(
        query: String,
        sort: String = "top",
        author: String? = null,
        since: String? = null,
        until: String? = null,
        lang: String? = null,
        cursor: String? = null,
        limit: Int = 25,
        mentions: String? = null,
        domain: String? = null,
        url: String? = null,
        tags: List<String> = emptyList(),
    ): SearchPostsResponse {
        val params = buildList {
            add("q" to query)
            add("sort" to sort)
            add("limit" to limit.toString())
            author?.takeIf { it.isNotBlank() }?.let { add("author" to it) }
            since?.takeIf { it.isNotBlank() }?.let { add("since" to it) }
            until?.takeIf { it.isNotBlank() }?.let { add("until" to it) }
            lang?.takeIf { it.isNotBlank() }?.let { add("lang" to it) }
            cursor?.takeIf { it.isNotBlank() }?.let { add("cursor" to it) }
            mentions?.takeIf { it.isNotBlank() }?.let { add("mentions" to it) }
            domain?.takeIf { it.isNotBlank() }?.let { add("domain" to it) }
            url?.takeIf { it.isNotBlank() }?.let { add("url" to it) }
            tags.forEach { add("tag" to it) }
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.feed.searchPosts", params))
    }

    suspend fun getTrends(limit: Int = 10, contentLanguages: List<String> = emptyList()): List<TrendTopicView> {
        suspend fun fetch(authenticated: Boolean): List<TrendTopicView> {
            val response = client.getJson(
                nsid = "app.bsky.unspecced.getTrends",
                params = listOf("limit" to limit.toString()),
                authenticated = authenticated,
                extraHeaders = if (contentLanguages.isEmpty()) emptyMap() else {
                    mapOf("Accept-Language" to contentLanguages.joinToString(","))
                },
            ).jsonObject
            return response["trends"]?.jsonArray.orEmpty().mapNotNull { item ->
                runCatching { AtProtoJson.decodeFromJsonElement<TrendTopicView>(item) }.getOrNull()
            }.distinctBy { it.link }
        }

        val personalized = try {
            fetch(authenticated = true)
        } catch (_: XrpcException) {
            emptyList()
        }
        return personalized.ifEmpty { fetch(authenticated = false) }
    }

    suspend fun getPopularFeedGenerators(query: String? = null, cursor: String? = null): List<GeneratorView> {
        val params = buildList {
            add("limit" to "30")
            if (!query.isNullOrBlank()) add("query" to query)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = client.getJson("app.bsky.unspecced.getPopularFeedGenerators", params).jsonObject
        return res["feeds"]?.jsonArray?.mapNotNull { el ->
            runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(el) }.getOrNull()
        }.orEmpty()
    }

    suspend fun getFeedGenerators(uris: List<String>): List<GeneratorView> {
        val distinct = uris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyList()
        val result = mutableListOf<GeneratorView>()
        for (chunk in distinct.chunked(25)) {
            val res = client.getJson("app.bsky.feed.getFeedGenerators", chunk.map { "feeds" to it }).jsonObject
            res["feeds"]?.jsonArray?.mapNotNull { el ->
                runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(el) }.getOrNull()
            }?.let { result.addAll(it) }
        }
        return result
    }

    suspend fun getFeedGenerator(uri: String): JsonObject =
        client.getJson("app.bsky.feed.getFeedGenerator", listOf("feed" to uri)).jsonObject

    suspend fun getActorFeeds(actor: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("actor" to actor)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.feed.getActorFeeds", params).jsonObject
    }

    suspend fun getFollowers(actor: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("actor" to actor)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.graph.getFollowers", params).jsonObject
    }

    suspend fun getFollows(actor: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("actor" to actor)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.graph.getFollows", params).jsonObject
    }

    suspend fun getKnownFollowers(actor: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("actor" to actor)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.graph.getKnownFollowers", params).jsonObject
    }

    suspend fun getLists(actor: String, cursor: String? = null): List<ListView> {
        val params = buildList {
            add("actor" to actor)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = client.getJson("app.bsky.graph.getLists", params).jsonObject
        return res["lists"]?.jsonArray?.map { AtProtoJson.decodeFromJsonElement<ListView>(it) }.orEmpty()
    }

    suspend fun getList(uri: String, cursor: String? = null): JsonObject {
        val params = buildList {
            add("list" to uri)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        return client.getJson("app.bsky.graph.getList", params).jsonObject
    }

    suspend fun getStarterPack(uri: String): JsonObject =
        client.getJson("app.bsky.graph.getStarterPack", listOf("starterPack" to uri)).jsonObject

    suspend fun getActorStarterPacks(actor: String): List<StarterPackViewBasic> {
        val res = client.getJson("app.bsky.graph.getActorStarterPacks", listOf("actor" to actor)).jsonObject
        return res["starterPacks"]?.jsonArray?.map { AtProtoJson.decodeFromJsonElement<StarterPackViewBasic>(it) }.orEmpty()
    }

    suspend fun followActor(repo: String, did: String): CreateRecordResponse = createRecord(
        repo = repo,
        collection = "app.bsky.graph.follow",
        record = buildJsonObject {
            put("\$type", JsonPrimitive("app.bsky.graph.follow"))
            put("subject", JsonPrimitive(did))
            put("createdAt", JsonPrimitive(Instant.now().toString()))
        },
    )

    suspend fun blockActor(repo: String, did: String): CreateRecordResponse = createRecord(
        repo = repo,
        collection = "app.bsky.graph.block",
        record = buildJsonObject {
            put("\$type", JsonPrimitive("app.bsky.graph.block"))
            put("subject", JsonPrimitive(did))
            put("createdAt", JsonPrimitive(Instant.now().toString()))
        },
    )

    suspend fun muteActor(actor: String) {
        client.postJson("app.bsky.graph.muteActor", buildJsonObject { put("actor", JsonPrimitive(actor)) })
    }

    suspend fun unmuteActor(actor: String) {
        client.postJson("app.bsky.graph.unmuteActor", buildJsonObject { put("actor", JsonPrimitive(actor)) })
    }

    suspend fun getMutes(cursor: String? = null): JsonObject {
        val params = if (!cursor.isNullOrBlank()) listOf("cursor" to cursor) else emptyList()
        return client.getJson("app.bsky.graph.getMutes", params).jsonObject
    }

    suspend fun getBlocks(cursor: String? = null): JsonObject {
        val params = if (!cursor.isNullOrBlank()) listOf("cursor" to cursor) else emptyList()
        return client.getJson("app.bsky.graph.getBlocks", params).jsonObject
    }

    // -------------------------------------------------------------------------
    // Preferences & Labelers
    // -------------------------------------------------------------------------

    suspend fun getActorPreferences(): JsonArray {
        val res = client.getJson("app.bsky.actor.getPreferences").jsonObject
        return res["preferences"]?.jsonArray ?: JsonArray(emptyList())
    }

    suspend fun putActorPreferences(preferences: JsonArray) {
        client.postJson(
            nsid = "app.bsky.actor.putPreferences",
            body = buildJsonObject { put("preferences", preferences) },
        )
    }

    suspend fun setSignupBirthdate(pdsUrl: String, accessJwt: String, birthdate: LocalDate) {
        val response = client.getJson(
            nsid = "app.bsky.actor.getPreferences",
            baseUrlOverride = pdsUrl,
            bearerOverride = accessJwt,
        ).jsonObject
        val preferences = response.getValue("preferences").jsonArray
        val detailsType = JsonPrimitive("app.bsky.actor.defs#personalDetailsPref")
        val birthDate = JsonPrimitive(birthdate.atStartOfDay().toInstant(ZoneOffset.UTC).toString())
        val updated = preferences.map { pref ->
            if (pref is JsonObject && pref["\$type"] == detailsType) {
                JsonObject(pref + ("birthDate" to birthDate))
            } else {
                pref
            }
        }
        val updatedPreferences = if (preferences.any { it is JsonObject && it["\$type"] == detailsType }) {
            updated
        } else {
            updated + buildJsonObject {
                put("\$type", detailsType)
                put("birthDate", birthDate)
            }
        }
        client.postJson(
            nsid = "app.bsky.actor.putPreferences",
            body = buildJsonObject { put("preferences", JsonArray(updatedPreferences)) },
            baseUrlOverride = pdsUrl,
            bearerOverride = accessJwt,
        )
    }

    suspend fun getLabelerServices(dids: List<String>): List<LabelerViewDetailed> {
        if (dids.isEmpty()) return emptyList()
        val params = dids.map { "dids" to it } + listOf("detailed" to "true")
        val res = client.getJson("app.bsky.labeler.getServices", params).jsonObject
        return res["views"]?.jsonArray?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<LabelerViewDetailed>(it) }.getOrNull()
        }.orEmpty()
    }

    // -------------------------------------------------------------------------
    // Notifications & Push Gateway
    // -------------------------------------------------------------------------

    suspend fun listNotifications(
        reasons: List<String> = emptyList(),
        cursor: String? = null,
        limit: Int = 30,
    ): NotificationsResponse {
        val params = buildList {
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
            reasons.forEach { add("reasons" to it) }
        }
        return AtProtoJson.decodeFromJsonElement(client.getJson("app.bsky.notification.listNotifications", params))
    }

    suspend fun getUnreadNotificationsCount(): Int {
        val res = client.getJson("app.bsky.notification.getUnreadCount").jsonObject
        return res["count"]?.jsonPrimitive?.intOrNull ?: 0
    }

    suspend fun updateNotificationsSeen(seenAt: String = Instant.now().toString()) {
        client.postJson(
            nsid = "app.bsky.notification.updateSeen",
            body = buildJsonObject { put("seenAt", JsonPrimitive(seenAt)) },
        )
    }

    suspend fun putActivitySubscription(subject: String, subscription: ActivitySubscription) {
        client.postJson(
            nsid = "app.bsky.notification.putActivitySubscription",
            body = buildJsonObject {
                put("subject", JsonPrimitive(subject))
                put("activitySubscription", buildJsonObject {
                    put("post", JsonPrimitive(subscription.post))
                    put("reply", JsonPrimitive(subscription.reply))
                })
            },
        )
    }

    suspend fun getNotificationPreferences(): NotificationPreferencesResponse {
        val res = client.getJson("app.bsky.notification.getPreferences").jsonObject
        return AtProtoJson.decodeFromJsonElement(res["preferences"]!!)
    }

    suspend fun putNotificationPreferencesV2(
        body: JsonObject,
        proxyServiceDid: String? = null,
    ): JsonObject {
        return client.postJson(
            nsid = "app.bsky.notification.putPreferencesV2",
            body = body,
            proxyServiceDid = proxyServiceDid,
        ).jsonObject
    }

    suspend fun registerPush(
        serviceDid: String,
        token: String,
        appId: String,
        ageRestricted: Boolean = false,
        proxyServiceDid: String? = null,
    ) {
        client.postJson(
            nsid = "app.bsky.notification.registerPush",
            body = buildJsonObject {
                put("serviceDid", JsonPrimitive(serviceDid))
                put("token", JsonPrimitive(token))
                put("platform", JsonPrimitive("android"))
                put("appId", JsonPrimitive(appId))
                put("ageRestricted", JsonPrimitive(ageRestricted))
            },
            proxyServiceDid = proxyServiceDid,
        )
    }

    suspend fun unregisterPush(
        serviceDid: String,
        token: String,
        appId: String,
        proxyServiceDid: String? = null,
    ) {
        client.postJson(
            nsid = "app.bsky.notification.unregisterPush",
            body = buildJsonObject {
                put("serviceDid", JsonPrimitive(serviceDid))
                put("token", JsonPrimitive(token))
                put("platform", JsonPrimitive("android"))
                put("appId", JsonPrimitive(appId))
            },
            proxyServiceDid = proxyServiceDid,
        )
    }

    // -------------------------------------------------------------------------
    // Direct Messages (chat.bsky.*)
    // -------------------------------------------------------------------------

    private fun decodeConvoViewSafely(el: JsonElement): ConvoView? {
        val primary = runCatching { AtProtoJson.decodeFromJsonElement<ConvoView>(el) }
        if (primary.isSuccess) {
            return primary.getOrNull()?.takeIf { it.id.isNotBlank() }
        }
        val ex = primary.exceptionOrNull()
        runCatching {
            android.util.Log.w("AtprotoApi", "ConvoView decode failed; using fallback parser: ${ex?.message}")
        }
        val obj = el as? JsonObject ?: return null
        val id = (obj["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (id.isBlank()) return null
        val members = (obj["members"] as? JsonArray)?.mapNotNull { memberEl ->
            runCatching { AtProtoJson.decodeFromJsonElement<ProfileViewBasic>(memberEl) }.getOrNull()
        }.orEmpty()
        return ConvoView(
            id = id,
            rev = (obj["rev"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            members = members,
            muted = (obj["muted"] as? JsonPrimitive)?.booleanOrNull ?: false,
            opened = (obj["opened"] as? JsonPrimitive)?.booleanOrNull,
            unreadCount = (obj["unreadCount"] as? JsonPrimitive)?.intOrNull ?: 0,
            status = (obj["status"] as? JsonPrimitive)?.contentOrNull,
            lastMessage = obj["lastMessage"] as? JsonObject,
            lastReaction = obj["lastReaction"] as? JsonObject,
            kind = obj["kind"],
            details = obj["details"],
        )
    }

    suspend fun listConvos(status: String? = null, cursor: String? = null): ListConvosResponse {
        val params = buildList {
            if (!status.isNullOrBlank()) add("status" to status)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = client.getJson("chat.bsky.convo.listConvos", params).jsonObject
        val nextCursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val convos = res["convos"]?.jsonArray?.mapNotNull { el ->
            decodeConvoViewSafely(el)
        }.orEmpty()
        return ListConvosResponse(cursor = nextCursor, convos = convos)
    }

    suspend fun getConvo(convoId: String): ConvoView {
        val res = client.getJson("chat.bsky.convo.getConvo", listOf("convoId" to convoId)).jsonObject
        val convoEl = res["convo"] ?: throw IllegalStateException("Missing conversation in response")
        return decodeConvoViewSafely(convoEl)
            ?: throw IllegalStateException("Unable to decode conversation")
    }

    suspend fun getConvoForMembers(members: List<String>): ConvoView {
        val res = client.getJson("chat.bsky.convo.getConvoForMembers", members.map { "members" to it }).jsonObject
        val convoEl = res["convo"] ?: throw IllegalStateException("Missing conversation in response")
        return decodeConvoViewSafely(convoEl)
            ?: throw IllegalStateException("Unable to decode conversation")
    }

    suspend fun getConvoMembers(convoId: String, cursor: String? = null): ConvoMembersResponse {
        val params = buildList {
            add("convoId" to convoId)
            cursor?.let { add("cursor" to it) }
        }
        val response = client.getJson("chat.bsky.convo.getConvoMembers", params)
        return AtProtoJson.decodeFromJsonElement<ConvoMembersResponse>(response)
    }

    suspend fun getConvoAvailability(member: String): ConvoAvailability =
        AtProtoJson.decodeFromJsonElement(
            client.getJson("chat.bsky.convo.getConvoAvailability", listOf("members" to member)),
        )

    suspend fun getMessages(convoId: String, cursor: String? = null): ConvoMessagesResponse {
        val params = buildList {
            add("convoId" to convoId)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = client.getJson("chat.bsky.convo.getMessages", params).jsonObject
        val nextCursor = (res["cursor"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val messages = res["messages"]?.jsonArray?.mapNotNull { el ->
            (el as? JsonObject)?.takeIf { msgObj ->
                val id = (msgObj["id"] as? JsonPrimitive)?.contentOrNull
                val type = (msgObj["\$type"] as? JsonPrimitive)?.contentOrNull
                !id.isNullOrBlank() && (
                    type == null ||
                        type == "chat.bsky.convo.defs#messageView" ||
                        type == "chat.bsky.convo.defs#deletedMessageView" ||
                        msgObj.containsKey("text") ||
                        msgObj.containsKey("sender")
                    )
            }
        }.orEmpty()
        val relatedProfiles = (res["relatedProfiles"] as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<ProfileViewBasic>(it) }.getOrNull()
        }.orEmpty()
        return ConvoMessagesResponse(cursor = nextCursor, messages = messages, relatedProfiles = relatedProfiles)
    }

    suspend fun sendMessage(convoId: String, message: JsonObject): JsonObject =
        client.postJson(
            nsid = "chat.bsky.convo.sendMessage",
            body = buildJsonObject {
                put("convoId", JsonPrimitive(convoId))
                put("message", message)
            },
        ).jsonObject

    suspend fun addMessageReaction(convoId: String, messageId: String, value: String): JsonObject =
        client.postJson(
            nsid = "chat.bsky.convo.addReaction",
            body = buildJsonObject {
                put("convoId", JsonPrimitive(convoId))
                put("messageId", JsonPrimitive(messageId))
                put("value", JsonPrimitive(value))
            },
        ).jsonObject

    suspend fun removeMessageReaction(convoId: String, messageId: String, value: String): JsonObject =
        client.postJson(
            nsid = "chat.bsky.convo.removeReaction",
            body = buildJsonObject {
                put("convoId", JsonPrimitive(convoId))
                put("messageId", JsonPrimitive(messageId))
                put("value", JsonPrimitive(value))
            },
        ).jsonObject

    suspend fun muteConvo(convoId: String) {
        client.postJson("chat.bsky.convo.muteConvo", buildJsonObject { put("convoId", JsonPrimitive(convoId)) })
    }

    suspend fun unmuteConvo(convoId: String) {
        client.postJson("chat.bsky.convo.unmuteConvo", buildJsonObject { put("convoId", JsonPrimitive(convoId)) })
    }

    suspend fun markConvoRead(convoId: String, messageId: String?) {
        client.postJson("chat.bsky.convo.updateRead", buildJsonObject {
            put("convoId", JsonPrimitive(convoId))
            messageId?.takeIf { it.isNotBlank() }?.let { put("messageId", JsonPrimitive(it)) }
        })
    }

    suspend fun acceptConvo(convoId: String) {
        client.postJson("chat.bsky.convo.acceptConvo", buildJsonObject { put("convoId", JsonPrimitive(convoId)) })
    }

    suspend fun leaveConvo(convoId: String) {
        client.postJson("chat.bsky.convo.leaveConvo", buildJsonObject { put("convoId", JsonPrimitive(convoId)) })
    }

    suspend fun updateAllReadConvos(status: String? = null) {
        val body = buildJsonObject {
            status?.let { put("status", JsonPrimitive(it)) }
        }
        client.postJson("chat.bsky.convo.updateAllRead", body)
    }

    // -------------------------------------------------------------------------
    // Moderation, PDS App Discovery (`Open in...`), Witchsky Themes, GIFs & Video
    // -------------------------------------------------------------------------

    suspend fun createModerationReport(
        reasonType: String,
        subject: JsonObject,
        reason: String? = null,
        labelerDid: String? = null,
    ): JsonObject {
        return client.postJson(
            nsid = "com.atproto.moderation.createReport",
            body = buildJsonObject {
                put("reasonType", JsonPrimitive(reasonType))
                put("subject", subject)
                if (!reason.isNullOrBlank()) put("reason", JsonPrimitive(reason))
            },
            proxyServiceDid = labelerDid?.let { "$it#atproto_labeler" },
        ).jsonObject
    }

    suspend fun listPdsRecords(
        pdsOrigin: String,
        repo: String,
        collection: String,
        limit: Int = 50,
        cursor: String? = null,
    ): PdsListRecordsResponse {
        val params = buildList {
            add("repo" to repo)
            add("collection" to collection)
            add("limit" to limit.toString())
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = client.getJson(
            nsid = "com.atproto.repo.listRecords",
            params = params,
            baseUrlOverride = pdsOrigin,
            authenticated = false,
        )
        return AtProtoJson.decodeFromJsonElement(res)
    }

    suspend fun listDrafts(cursor: String? = null): List<DraftView> {
        val params = if (!cursor.isNullOrBlank()) listOf("cursor" to cursor) else emptyList()
        val res = client.getJson("app.bsky.draft.getDrafts", params).jsonObject
        return res["drafts"]?.jsonArray?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<DraftView>(it) }.getOrNull()
        }.orEmpty()
    }

    suspend fun getServiceAuth(
        audience: String,
        method: String,
        expiresInSeconds: Long? = 60 * 30L,
    ): String? {
        val res = client.getJson(
            nsid = "com.atproto.server.getServiceAuth",
            params = buildList {
                add("aud" to audience)
                add("lxm" to method)
                expiresInSeconds?.let { add("exp" to (Instant.now().epochSecond + it).toString()) }
            },
        ).jsonObject
        return (res["token"] as? JsonPrimitive)?.contentOrNull
    }

    suspend fun getVideoJobStatus(jobId: String): VideoJobStatus {
        val res = client.getJson(
            nsid = "app.bsky.video.getJobStatus",
            params = listOf("jobId" to jobId),
            baseUrlOverride = VIDEO_SERVICE_ORIGIN,
            authenticated = false,
        ).jsonObject
        return AtProtoJson.decodeFromJsonElement(res["jobStatus"]!!)
    }

    companion object {
        const val LOGIN_TYPEAHEAD_ORIGIN = "https://typeahead.waow.tech"
        const val VIDEO_SERVICE_ORIGIN = "https://video.bsky.app"
    }
}
