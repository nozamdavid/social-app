package am.noz.caelum.core.post

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BookmarkView
import am.noz.caelum.core.model.PostShadowStore
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.model.ToggleMutationQueue
import am.noz.caelum.core.model.threadRootUri
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.BoundXrpcSession
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.core.session.SessionManager
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.core.storage.SearchableBookmarkEngine
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * Shared optimistic mutation controller for PostCard, PostThread, and Search/Profile feeds.
 */
class PostInteractionController(
    private val api: AtprotoApi,
    private val sessionManager: SessionManager,
    private val preferences: PreferencesRepository,
    private val shadowStore: PostShadowStore,
    private val mutationQueue: ToggleMutationQueue,
    private val bookmarkEngine: SearchableBookmarkEngine,
    private val tldsProvider: () -> Set<String>,
) {
    // Authoritative server record URIs per "$did:$postUri" ("" means confirmed absent).
    private val confirmedLikeUris = ConcurrentHashMap<String, String>()
    private val confirmedRepostUris = ConcurrentHashMap<String, String>()

    suspend fun toggleLike(post: PostView, via: StrongRef? = null) {
        val did = sessionManager.state.value.currentDid ?: return
        sessionManager.runForCurrentAccount(did) {
            val session = coroutineContext[BoundXrpcSession] ?: return@runForCurrentAccount
            val effective = shadowStore.applyToPost(post) ?: return@runForCurrentAccount
            val currentlyLiked = !effective.viewer?.like.isNullOrBlank()
            val targetLikeCount = ((effective.likeCount ?: 0) + if (currentlyLiked) -1 else 1).coerceAtLeast(0)
            val cacheKey = "$did:${post.uri}"

            shadowStore.update(post.uri) {
                it.copy(
                    likeUri = if (currentlyLiked) null else "pending://like",
                    likeUriSet = true,
                    likeCount = targetLikeCount,
                )
            }

            mutationQueue.enqueue("like:$cacheKey") {
                if (session.auth() == null) return@enqueue
                try {
                    if (!currentlyLiked) {
                        val created = api.likePost(did, StrongRef(post.uri, post.cid), via)
                        confirmedLikeUris[cacheKey] = created.uri
                        if (session.auth() != null) {
                            shadowStore.update(post.uri) { current ->
                                if (current.likeUri == "pending://like") {
                                    current.copy(likeUri = created.uri, likeUriSet = true)
                                } else {
                                    current
                                }
                            }
                        }
                    } else {
                        val existingUri = confirmedLikeUris[cacheKey]?.ifEmpty { null }
                            ?: effective.viewer.like.takeUnless { it.startsWith("pending://") }
                            ?: post.viewer?.like
                        if (!existingUri.isNullOrBlank() && !existingUri.startsWith("pending://")) {
                            val rkey = existingUri.substringAfterLast('/')
                            api.deleteRecord(did, "app.bsky.feed.like", rkey)
                        }
                        confirmedLikeUris[cacheKey] = ""
                        if (session.auth() != null) {
                            shadowStore.update(post.uri) { current ->
                                if (current.likeUri == null) {
                                    current.copy(likeUri = null, likeUriSet = true)
                                } else {
                                    current
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (session.auth() != null) {
                        val fallbackUri = confirmedLikeUris[cacheKey]?.ifEmpty { null } ?: post.viewer?.like
                        shadowStore.update(post.uri) {
                            it.copy(
                                likeUri = fallbackUri,
                                likeUriSet = true,
                                likeCount = post.likeCount,
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun toggleRepost(post: PostView, via: StrongRef? = null) {
        val did = sessionManager.state.value.currentDid ?: return
        sessionManager.runForCurrentAccount(did) {
            val session = coroutineContext[BoundXrpcSession] ?: return@runForCurrentAccount
            val effective = shadowStore.applyToPost(post) ?: return@runForCurrentAccount
            val currentlyReposted = !effective.viewer?.repost.isNullOrBlank()
            val targetRepostCount = ((effective.repostCount ?: 0) + if (currentlyReposted) -1 else 1).coerceAtLeast(0)
            val cacheKey = "$did:${post.uri}"

            shadowStore.update(post.uri) {
                it.copy(
                    repostUri = if (currentlyReposted) null else "pending://repost",
                    repostUriSet = true,
                    repostCount = targetRepostCount,
                )
            }

            mutationQueue.enqueue("repost:$cacheKey") {
                if (session.auth() == null) return@enqueue
                try {
                    if (!currentlyReposted) {
                        val created = api.repostPost(did, StrongRef(post.uri, post.cid), via)
                        confirmedRepostUris[cacheKey] = created.uri
                        if (session.auth() != null) {
                            shadowStore.update(post.uri) { current ->
                                if (current.repostUri == "pending://repost") {
                                    current.copy(repostUri = created.uri, repostUriSet = true)
                                } else {
                                    current
                                }
                            }
                            // Caelum customization: automatically like reposted posts when enabled
                            val autoLike = preferences.currentSnapshot().autoLikeReposts
                            val latest = shadowStore.applyToPost(post) ?: post
                            if (autoLike && latest.viewer?.like.isNullOrBlank()) {
                                toggleLike(post, via)
                            }
                        }
                    } else {
                        val existingUri = confirmedRepostUris[cacheKey]?.ifEmpty { null }
                            ?: effective.viewer.repost.takeUnless { it.startsWith("pending://") }
                            ?: post.viewer?.repost
                        if (!existingUri.isNullOrBlank() && !existingUri.startsWith("pending://")) {
                            val rkey = existingUri.substringAfterLast('/')
                            api.deleteRecord(did, "app.bsky.feed.repost", rkey)
                        }
                        confirmedRepostUris[cacheKey] = ""
                        if (session.auth() != null) {
                            shadowStore.update(post.uri) { current ->
                                if (current.repostUri == null) {
                                    current.copy(repostUri = null, repostUriSet = true)
                                } else {
                                    current
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (session.auth() != null) {
                        val fallbackUri = confirmedRepostUris[cacheKey]?.ifEmpty { null } ?: post.viewer?.repost
                        shadowStore.update(post.uri) {
                            it.copy(
                                repostUri = fallbackUri,
                                repostUriSet = true,
                                repostCount = post.repostCount,
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun toggleBookmark(post: PostView) {
        val did = sessionManager.state.value.currentDid ?: return
        sessionManager.runForCurrentAccount(did) {
            val session = coroutineContext[BoundXrpcSession] ?: return@runForCurrentAccount
            val effective = shadowStore.applyToPost(post) ?: return@runForCurrentAccount
            val currentlySaved = effective.viewer?.bookmarked == true
            val targetCount = ((effective.bookmarkCount ?: 0) + if (currentlySaved) -1 else 1).coerceAtLeast(0)

            shadowStore.update(post.uri) {
                it.copy(bookmarked = !currentlySaved, bookmarkCount = targetCount)
            }

            mutationQueue.enqueue("bookmark:$did:${post.uri}") {
                if (session.auth() == null) return@enqueue
                try {
                    val snap = preferences.currentSnapshot()
                    val scope = SearchableBookmarkEngine.scopeKeyFor(did, snap.infrastructure.appviewHostname)
                    if (!currentlySaved) {
                        api.createBookmark(post.uri, post.cid)
                        if (snap.searchableBookmarks) {
                            val postJson = AtProtoJson.encodeToJsonElement(PostView.serializer(), post) as JsonObject
                            bookmarkEngine.upsertBookmarks(
                                scope,
                                listOf(
                                    BookmarkView(
                                        subject = StrongRef(post.uri, post.cid),
                                        item = postJson,
                                    )
                                ),
                            )
                        }
                    } else {
                        api.deleteBookmark(post.uri)
                        if (snap.searchableBookmarks) {
                            bookmarkEngine.removeBookmark(scope, post.uri)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (session.auth() != null) {
                        shadowStore.update(post.uri) {
                            it.copy(bookmarked = post.viewer?.bookmarked, bookmarkCount = post.bookmarkCount)
                        }
                    }
                }
            }
        }
    }

    suspend fun deletePost(post: PostView): Result<Unit> {
        val did = sessionManager.state.value.currentDid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            sessionManager.runForCurrentAccount(did) {
                val rkey = post.uri.substringAfterLast('/')
                api.deleteRecord(did, "app.bsky.feed.post", rkey)
                if (coroutineContext[BoundXrpcSession]?.auth() != null) {
                    shadowStore.update(post.uri) { it.copy(isDeleted = true) }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleThreadMute(post: PostView) {
        val did = sessionManager.state.value.currentDid ?: return
        val rootUri = post.threadRootUri()
        sessionManager.runForCurrentAccount(did) {
            val session = coroutineContext[BoundXrpcSession] ?: return@runForCurrentAccount
            mutationQueue.enqueue("thread-mute:$did:$rootUri") {
                if (session.auth() == null) return@enqueue
                val previousOverride = shadowStore.get(rootUri)?.threadMuted
                val shouldMute = !(previousOverride ?: post.viewer?.threadMuted ?: false)
                shadowStore.update(rootUri) { it.copy(threadMuted = shouldMute) }
                var saved = false
                try {
                    api.setThreadMuted(rootUri, shouldMute)
                    saved = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Restore the previous viewer state when AppView rejects the change.
                } finally {
                    if (!saved && session.auth() != null) {
                        shadowStore.update(rootUri) { it.copy(threadMuted = previousOverride) }
                    }
                }
            }
        }
    }

    suspend fun migrateLegacyThreadMutes(did: String) = sessionManager.runForCurrentAccount(did) {
        val session = coroutineContext[BoundXrpcSession] ?: return@runForCurrentAccount
        val legacyUris = preferences.getModerationSettings(did).mutedThreads
        for (batch in legacyUris.chunked(25)) {
            val posts = api.getPosts(batch)
            for ((rootUri, threadPosts) in posts.groupBy(PostView::threadRootUri)) {
                mutationQueue.enqueue("thread-mute:$did:$rootUri") {
                    if (session.auth() == null) return@enqueue
                    if (shadowStore.get(rootUri)?.threadMuted == null) {
                        api.setThreadMuted(rootUri, true)
                        if (session.auth() != null) {
                            shadowStore.update(rootUri) { it.copy(threadMuted = true) }
                        }
                    }
                    val migrated = threadPosts.map(PostView::uri).toSet()
                    preferences.updateModerationSettings(did) {
                        it.copy(mutedThreads = it.mutedThreads - migrated)
                    }
                }
            }
        }
    }

    suspend fun hidePost(postUri: String) {
        val did = sessionManager.state.value.currentDid ?: return
        sessionManager.runForCurrentAccount(did) {
            shadowStore.update(postUri) { it.copy(isDeleted = true) }
            preferences.updateModerationSettings(did) {
                it.copy(hiddenPosts = it.hiddenPosts + postUri)
            }
            runCatching {
                val raw = api.getActorPreferences().toMutableList()
                val hiddenItems = preferences.getModerationSettings(did).hiddenPosts.map { JsonPrimitive(it) }
                val filtered = raw.filterNot {
                    (it as? JsonObject)?.get("\$type")?.let { t -> (t as? JsonPrimitive)?.content == "app.bsky.actor.defs#hiddenPostsPref" } == true
                }.toMutableList()
                filtered.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#hiddenPostsPref"))
                        put("items", JsonArray(hiddenItems))
                    }
                )
                api.putActorPreferences(JsonArray(filtered))
            }
        }
    }

    suspend fun editPostWithinWindow(post: PostView, newText: String): Result<JsonObject> {
        val did = sessionManager.state.value.currentDid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            var resultRecord: JsonObject? = null
            sessionManager.runForCurrentAccount(did) {
                val resolver = sessionManager.identityResolver
                val pdsOrigin = coroutineContext[BoundXrpcSession]?.auth()?.pdsUrl ?: "https://bsky.social"
                val facets = RichTextEngine.detectFacets(newText, tldsProvider()) { handle ->
                    resolver.resolveHandle(handle, pdsOrigin)
                }
                val (shortText, shortFacets) = RichTextEngine.shortenLinks(newText, facets)
                val updatedRecord = api.editPostWithinWindow(
                    repo = did,
                    postUri = post.uri,
                    existingRecord = post.record,
                    newText = shortText,
                    newFacets = shortFacets,
                )
                if (coroutineContext[BoundXrpcSession]?.auth() != null) {
                    shadowStore.update(post.uri) { it.copy(editedRecord = updatedRecord) }
                }
                resultRecord = updatedRecord
            }
            val updated = resultRecord ?: return Result.failure(IllegalStateException("Session changed"))
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
