package am.noz.caelum.core.session

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ContentLabelPref
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.PostShadowStore
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.model.ThreadSort
import am.noz.caelum.core.moderation.DEFAULT_LABEL_SETTINGS
import am.noz.caelum.core.moderation.InterpretedLabelDef
import am.noz.caelum.core.moderation.LabelPreference
import am.noz.caelum.core.moderation.LabelerPref
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.moderation.ModerationPrefs
import am.noz.caelum.core.network.ActiveXrpcAuth
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.BoundXrpcSession
import am.noz.caelum.core.network.IdentityResolver
import am.noz.caelum.core.network.PushGatewayManager
import am.noz.caelum.core.network.XrpcClient
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.core.network.XrpcSessionDelegate
import am.noz.caelum.core.storage.LegacyRnMigrationManager
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.core.storage.TokenVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap

data class SessionUiState(
    val isInitialized: Boolean = false,
    val accounts: List<StoredAccount> = emptyList(),
    val currentDid: String? = null,
    val expiredDid: String? = null,
    val activation: Long = 0,
    internal val credentialGeneration: Long? = null,
    val pinnedFeeds: List<SavedFeedPref> = emptyList(),
    val feedsHydrated: Boolean = false,
    val feedGenerators: Map<String, GeneratorView> = emptyMap(),
    val listViews: Map<String, ListView> = emptyMap(),
    val subscribedLabelers: List<LabelerViewDetailed> = emptyList(),
    val moderationOpts: ModerationOpts = ModerationOpts(),
    val unreadNotificationsCount: Int = 0,
    val unreadChatCount: Int = 0,
    val fcmToken: String? = null,
) {
    val currentAccount: StoredAccount?
        get() = accounts.firstOrNull { it.did == currentDid }

    val hasSession: Boolean
        get() = currentAccount != null
}

/**
 * Multi-account ATProto session manager with per-account Keystore JWT isolation,
 * custom PDS discovery, server preference sync, and atomic account switching.
 */
class SessionManager(
    private val preferences: PreferencesRepository,
    private val tokenVault: TokenVault,
    private val postShadowStore: PostShadowStore,
    private val migrationManager: LegacyRnMigrationManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : XrpcSessionDelegate {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private data class Credentials(val generation: Long, val tokens: SessionTokens)
    private data class ActiveSession(val did: String, val activation: Long)

    private val sessionMutex = Mutex()
    private var nextCredentialGeneration = 0L
    private val activeTokenCache = ConcurrentHashMap<String, Credentials>()

    private fun SessionUiState.activeSession() = currentDid?.let { ActiveSession(it, activation) }

    private fun boundSession(session: ActiveSession) = BoundXrpcSession {
        val snapshot = _state.value
        if (snapshot.activeSession() == session) authForAccount(snapshot, session.did) else null
    }

    internal fun bindAccount(did: String, activation: Long) =
        boundSession(ActiveSession(did, activation))

    private suspend fun withCurrentSession(
        did: String? = null,
        block: suspend (ActiveSession) -> Unit,
    ) {
        val session = _state.value.activeSession()?.takeIf { did == null || it.did == did } ?: return
        val existing = coroutineContext[BoundXrpcSession]
        if (existing != null) {
            if (existing.auth()?.did == session.did) block(session)
        } else {
            withContext(boundSession(session)) { block(session) }
        }
    }

    private suspend fun commitIfActive(session: ActiveSession, block: suspend () -> Unit) {
        sessionMutex.withLock {
            if (_state.value.activeSession() == session) block()
        }
    }

    suspend fun runForCurrentAccount(did: String, block: suspend () -> Unit) {
        withCurrentSession(did) { block() }
    }


    lateinit var identityResolver: IdentityResolver
    lateinit var api: AtprotoApi
    var pushGatewayManager: PushGatewayManager? = null

    fun attachNetwork(
        identityResolver: IdentityResolver,
        api: AtprotoApi,
        pushGatewayManager: PushGatewayManager? = null,
    ) {
        this.identityResolver = identityResolver
        this.api = api
        this.pushGatewayManager = pushGatewayManager
    }

    suspend fun initialize() = sessionMutex.withLock {
        if (_state.value.isInitialized) return@withLock
        runCatching { migrationManager?.migrateIfNeeded() }
        preferences.migrateLegacyInterests()

        val snap = preferences.currentSnapshot()
        for (acc in snap.accounts) {
            tokenVault.getTokens(acc.did)?.let { tok ->
                activeTokenCache[acc.did] = Credentials(++nextCredentialGeneration, tok)
            }
        }

        val currentDid = snap.currentDid?.takeIf { did ->
            snap.accounts.any { it.did == did } && activeTokenCache.containsKey(did)
        }
        val savedFeeds = if (currentDid != null) {
            preferences.getSavedFeeds(currentDid).let { if (it.isEmpty()) emptyList() else normalizeSavedFeeds(it) }
        } else {
            emptyList()
        }
        val modSettings = if (currentDid != null) preferences.getModerationSettings(currentDid) else ModerationUserSettings()

        _state.update {
            SessionUiState(
                isInitialized = true,
                accounts = snap.accounts,
                currentDid = currentDid,
                pinnedFeeds = savedFeeds,
                moderationOpts = buildModerationOpts(currentDid, modSettings, emptyList()),
                activation = it.activation + 1,
                credentialGeneration = currentDid?.let { did -> activeTokenCache[did]?.generation },
                fcmToken = it.fcmToken,
            )
        }

        launchActiveRefresh()
    }

    override fun currentAuth(): ActiveXrpcAuth? {
        val snapshot = _state.value
        return snapshot.currentDid?.let { authForAccount(snapshot, it) }
    }

    private fun authForAccount(snapshot: SessionUiState, did: String): ActiveXrpcAuth? {
        val account = snapshot.accounts.firstOrNull { it.did == did } ?: return null
        val credentials = activeTokenCache[did] ?: return null
        if (snapshot.currentDid == did && credentials.generation != snapshot.credentialGeneration) return null
        val origin = (account.pdsUrl?.takeIf { it.isNotBlank() } ?: account.serviceUrl).trimEnd('/')
        return ActiveXrpcAuth(
            did = did,
            pdsUrl = origin,
            accessJwt = credentials.tokens.accessJwt,
            refreshJwt = credentials.tokens.refreshJwt,
            labelers = if (snapshot.currentDid == did) snapshot.moderationOpts.prefs.labelers.map { it.did } else emptyList(),
            accountGeneration = credentials.generation,
        )
    }

    override fun authForRefresh(failed: ActiveXrpcAuth): ActiveXrpcAuth? =
        authForAccount(_state.value, failed.did)?.takeIf { it.accountGeneration == failed.accountGeneration }

    override suspend fun updateRefreshedTokens(failed: ActiveXrpcAuth, tokens: SessionTokens): Boolean =
        sessionMutex.withLock {
            if (authForRefresh(failed)?.copy(labelers = failed.labelers) != failed) return@withLock false
            tokenVault.saveTokens(failed.did, tokens)
            activeTokenCache[failed.did] = Credentials(failed.accountGeneration, tokens)
            _state.update { if (it.expiredDid == failed.did) it.copy(expiredDid = null) else it }
            true
        }

    override suspend fun expireFailedSession(failed: ActiveXrpcAuth) {
        sessionMutex.withLock {
            if (authForRefresh(failed)?.copy(labelers = failed.labelers) == failed) {
                _state.update { if (it.currentDid == failed.did) it.copy(expiredDid = failed.did) else it }
            }
        }
    }

    suspend fun signIn(
        serviceOrigin: String,
        identifier: String,
        password: String,
        authFactorToken: String? = null,
    ): StoredAccount {
        val cleanService = serviceOrigin.trim().ifEmpty { "https://bsky.social" }.let {
            if (it.startsWith("http")) it else "https://$it"
        }
        val cleanId = identifier.trim().removePrefix("@")

        val discoveredPds = runCatching {
            identityResolver.discoverPdsForLogin(cleanId, cleanService)
        }.getOrDefault(cleanService)

        val response = try {
            api.createSession(discoveredPds, cleanId, password, authFactorToken)
        } catch (ex: Exception) {
            if (discoveredPds != cleanService) {
                api.createSession(cleanService, cleanId, password, authFactorToken)
            } else {
                throw ex
            }
        }

        val docPds = extractPdsFromDidDoc(response.didDoc)
        val resolvedFallbackPds = if (docPds == null && discoveredPds.contains("bsky.social", ignoreCase = true)) {
            runCatching { identityResolver.resolvePdsUrl(response.did) }.getOrNull()
        } else {
            null
        }
        val pdsUrl = (docPds ?: resolvedFallbackPds ?: discoveredPds).trimEnd('/')

        val tokens = SessionTokens(accessJwt = response.accessJwt, refreshJwt = response.refreshJwt)
        var newAccount = StoredAccount(
            did = response.did,
            handle = response.handle,
            serviceUrl = cleanService,
            pdsUrl = pdsUrl,
            email = response.email,
            emailConfirmed = response.emailConfirmed ?: true,
            emailAuthFactor = response.emailAuthFactor ?: false,
            active = response.active ?: true,
            status = response.status,
            isSelfHosted = !cleanService.contains("bsky.social") && !pdsUrl.contains("bsky.network"),
        )

        val session = storeAccount(newAccount, tokens)
        withContext(boundSession(session)) {
            runCatching { hydrateProfile(session)?.let { newAccount = it } }
            runCatching { syncPreferencesFromServer(response.did) }
            runCatching { _state.value.fcmToken?.let { pushGatewayManager?.registerToken(it) } }
        }
        return newAccount
    }

    suspend fun registerNewAccount(account: StoredAccount, tokens: SessionTokens) {
        storeAccount(account, tokens)
    }

    private suspend fun storeAccount(account: StoredAccount, tokens: SessionTokens): ActiveSession =
        sessionMutex.withLock {
            val updated = listOf(account) + preferences.currentSnapshot().accounts.filterNot { it.did == account.did }
            val savedFeeds = preferences.getSavedFeeds(account.did)
            val settings = preferences.getModerationSettings(account.did)
            preferences.updateAccounts(updated, account.did)
            tokenVault.saveTokens(account.did, tokens)
            activeTokenCache[account.did] = Credentials(++nextCredentialGeneration, tokens)
            activate(updated, account.did, savedFeeds, settings)
            checkNotNull(_state.value.activeSession())
        }

    suspend fun switchAccount(did: String): Boolean = sessionMutex.withLock {
        val snap = preferences.currentSnapshot()
        if (snap.accounts.none { it.did == did }) return@withLock false
        if (!activeTokenCache.containsKey(did)) {
            _state.update { it.copy(expiredDid = did) }
            return@withLock false
        }

        val savedFeeds = preferences.getSavedFeeds(did)
        val settings = preferences.getModerationSettings(did)
        preferences.updateAccounts(snap.accounts, did)
        activate(snap.accounts, did, savedFeeds, settings)
        launchActiveRefresh()
        true
    }

    suspend fun removeAccount(did: String) {
        val session = _state.value.activeSession()?.takeIf { it.did == did }
        unregisterActiveToken(session)
        sessionMutex.withLock {
            val snap = preferences.currentSnapshot()
            val remaining = snap.accounts.filterNot { it.did == did }
            if (remaining.size == snap.accounts.size) return@withLock
            val nextCurrent = if (snap.currentDid == did) {
                remaining.firstOrNull { activeTokenCache.containsKey(it.did) }?.did
            } else snap.currentDid
            val savedFeeds = nextCurrent?.let { preferences.getSavedFeeds(it) }.orEmpty()
            val settings = nextCurrent?.let { preferences.getModerationSettings(it) } ?: ModerationUserSettings()
            preferences.updateAccounts(remaining, nextCurrent)
            activeTokenCache.remove(did)
            tokenVault.removeTokens(did)
            pushGatewayManager?.clearSyncCache()
            if (snap.currentDid == did) {
                activate(remaining, nextCurrent, savedFeeds, settings)
                launchActiveRefresh()
            } else {
                _state.update { it.copy(accounts = remaining, expiredDid = it.expiredDid?.takeUnless { expired -> expired == did }) }
            }
        }
    }

    suspend fun signOutAll() {
        unregisterActiveToken(_state.value.activeSession())
        sessionMutex.withLock {
            preferences.updateAccounts(emptyList(), null)
            activeTokenCache.clear()
            tokenVault.clearAll()
            pushGatewayManager?.clearSyncCache()
            activate(emptyList(), null, emptyList(), ModerationUserSettings())
        }
    }

    private suspend fun unregisterActiveToken(session: ActiveSession?) {
        val fcm = _state.value.fcmToken?.takeIf { it.isNotBlank() } ?: return
        if (session == null) return
        withContext(boundSession(session)) {
            runCatching { pushGatewayManager?.unregisterToken(fcm) }
        }
    }

    private fun activate(
        accounts: List<StoredAccount>,
        did: String?,
        savedFeeds: List<SavedFeedPref>,
        settings: ModerationUserSettings,
    ) {
        postShadowStore.clear()
        _state.update {
            SessionUiState(
                isInitialized = true,
                accounts = accounts,
                currentDid = did,
                activation = it.activation + 1,
                credentialGeneration = did?.let { activeTokenCache[it]?.generation },
                pinnedFeeds = savedFeeds,
                moderationOpts = buildModerationOpts(did, settings, emptyList()),
                fcmToken = it.fcmToken,
            )
        }
    }

    suspend fun refreshUnreadCounts() = withCurrentSession { session ->
        if (!::api.isInitialized) return@withCurrentSession
        val notifUnread = runCatching { api.getUnreadNotificationsCount() }.getOrDefault(0)
        val chatUnread = runCatching {
            api.listConvos(status = "accepted").convos.sumOf { it.unreadCount }
        }.getOrDefault(0)
        commitIfActive(session) {
            _state.update {
                it.copy(unreadNotificationsCount = notifUnread, unreadChatCount = chatUnread)
            }
        }
    }

    suspend fun ensurePdsResolvedForCurrentAccount() = withCurrentSession { ensureResolvedPdsUrl(it) }

    private suspend fun ensureResolvedPdsUrl(session: ActiveSession) {
        if (!::identityResolver.isInitialized) return
        val did = session.did
        val snapshot = _state.value
        if (snapshot.activeSession() != session) return
        val account = snapshot.currentAccount ?: return
        val currentPds = account.pdsUrl?.trim().orEmpty()
        if (currentPds.isNotEmpty() && !currentPds.contains("bsky.social", ignoreCase = true)) return
        val resolved = runCatching { identityResolver.resolvePdsUrl(did) }
            .getOrNull()
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (resolved == currentPds.trimEnd('/')) return

        commitIfActive(session) {
            val updated = _state.value.accounts.map { acc ->
                if (acc.did == did) {
                    acc.copy(
                        pdsUrl = resolved,
                        isSelfHosted = !acc.serviceUrl.contains("bsky.social", ignoreCase = true) &&
                            !resolved.contains("bsky.network", ignoreCase = true),
                    )
                } else acc
            }
            preferences.updateAccounts(updated, did)
            _state.update { it.copy(accounts = updated) }
        }
    }

    fun updateSavedFeedsOptimistically(
        did: String,
        updated: List<SavedFeedPref>,
        extraGenerator: GeneratorView? = null,
        extraList: ListView? = null,
    ) {
        val normalized = normalizeSavedFeeds(updated)
        _state.update { st ->
            if (st.currentDid != did) st
            else st.copy(
                pinnedFeeds = normalized,
                feedGenerators = if (extraGenerator != null) {
                    st.feedGenerators + (extraGenerator.uri to extraGenerator)
                } else {
                    st.feedGenerators
                },
                listViews = if (extraList != null) {
                    st.listViews + (extraList.uri to extraList)
                } else {
                    st.listViews
                },
            )
        }
    }

    suspend fun syncPreferencesFromServer(did: String) = withCurrentSession(did) { session ->
        if (!::api.isInitialized) return@withCurrentSession
        val rawPrefs = api.getActorPreferences()
        val hasSavedFeedsV2 = rawPrefs.any { el ->
            ((el as? JsonObject)?.get("\$type") as? JsonPrimitive)?.contentOrNull == "app.bsky.actor.defs#savedFeedsPrefV2"
        }
        val savedFeeds = mutableListOf<SavedFeedPref>()
        val mutedWords = mutableListOf<MutedWord>()
        val labelers = mutableListOf<LabelerPrefItem>()
        val labelerPrefs = mutableListOf<ContentLabelPref>()
        val contentLabels = mutableMapOf<String, String>()
        val hiddenPosts = mutableSetOf<String>()
        var interests: List<String>? = null
        var adultEnabled = false
        var replySort: String? = null
        var threadedReplies: Boolean? = null
        var disableReplies = false
        var disableReposts = false
        var disableQuotes = false
        var hideVerificationBadges = false

        for (el in rawPrefs) {
            val obj = el as? JsonObject ?: continue
            when ((obj["\$type"] as? JsonPrimitive)?.content) {
                "app.bsky.actor.defs#savedFeedsPrefV2" -> {
                    val items = obj["items"] as? JsonArray ?: continue
                    for (itemEl in items) {
                        runCatching { AtProtoJson.decodeFromJsonElement(SavedFeedPref.serializer(), itemEl) }.getOrNull()
                            ?.let { savedFeeds.add(it) }
                    }
                }
                "app.bsky.actor.defs#savedFeedsPref" -> {
                    // Only fall back to legacy v1 preferences when no savedFeedsPrefV2 object exists.
                    if (!hasSavedFeedsV2) {
                        val pinned = (obj["pinned"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            .orEmpty()
                            .toSet()
                        val saved = (obj["saved"] as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                            .orEmpty()
                        for (uri in (pinned + saved).distinct()) {
                            val feedType = when {
                                uri == "following" -> "timeline"
                                uri.contains("/app.bsky.graph.list/") -> "list"
                                else -> "feed"
                            }
                            savedFeeds.add(
                                SavedFeedPref(
                                    id = generateFeedPrefId(),
                                    type = feedType,
                                    value = uri,
                                    pinned = uri in pinned,
                                )
                            )
                        }
                    }
                }
                "app.bsky.actor.defs#adultContentPref" -> {
                    adultEnabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false
                }
                "app.bsky.actor.defs#contentLabelPref" -> {
                    val label = (obj["label"] as? JsonPrimitive)?.content ?: continue
                    val vis = (obj["visibility"] as? JsonPrimitive)?.content ?: continue
                    val labelerDid = (obj["labelerDid"] as? JsonPrimitive)?.content
                    if (labelerDid != null) {
                        labelerPrefs.add(ContentLabelPref(labelerDid = labelerDid, label = label, visibility = vis))
                    } else {
                        contentLabels[label] = vis
                    }
                }
                "app.bsky.actor.defs#labelersPref" -> {
                    val items = obj["labelers"] as? JsonArray ?: continue
                    for (l in items) {
                        (l as? JsonObject)?.get("did")?.jsonPrimitive?.content?.let {
                            labelers.add(LabelerPrefItem(did = it))
                        }
                    }
                }
                "app.bsky.actor.defs#mutedWordsPref" -> {
                    val items = obj["items"] as? JsonArray ?: continue
                    for (w in items) {
                        runCatching { AtProtoJson.decodeFromJsonElement(MutedWord.serializer(), w) }.getOrNull()
                            ?.let { mutedWords.add(it) }
                    }
                }
                "app.bsky.actor.defs#hiddenPostsPref" -> {
                    val items = obj["items"] as? JsonArray ?: continue
                    for (u in items) {
                        hiddenPosts.add(u.jsonPrimitive.content)
                    }
                }
                "app.bsky.actor.defs#interestsPref" -> {
                    val tags = obj["tags"] as? JsonArray ?: continue
                    interests = tags.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                }
                "app.bsky.actor.defs#threadViewPref" -> {
                    (obj["sort"] as? JsonPrimitive)?.contentOrNull?.let {
                        replySort = ThreadSort.fromPreference(it).preference
                    }
                    threadedReplies = (obj["lab_treeViewEnabled"] as? JsonPrimitive)?.booleanOrNull
                }
                "app.bsky.actor.defs#verificationPrefs" -> {
                    hideVerificationBadges = (obj["hideBadges"] as? JsonPrimitive)?.booleanOrNull ?: false
                }
                "app.bsky.actor.defs#feedViewPref" -> {
                    if ((obj["feed"] as? JsonPrimitive)?.content == "home") {
                        disableReplies = (obj["hideReplies"] as? JsonPrimitive)?.booleanOrNull ?: false
                        disableReposts = (obj["hideReposts"] as? JsonPrimitive)?.booleanOrNull ?: false
                        disableQuotes = (obj["hideQuotePosts"] as? JsonPrimitive)?.booleanOrNull ?: false
                    }
                }
            }
        }

        val normalizedFeeds = normalizeSavedFeeds(savedFeeds)
        val feedUris = normalizedFeeds
            .filter { it.type == "feed" || it.value.contains("/app.bsky.feed.generator/") }
            .map { it.value }
            .distinct()
        val feedGensResult = runCatching {
            api.getFeedGenerators(feedUris).associateBy { it.uri }
        }
        val feedsFetchSucceeded = feedGensResult.isSuccess
        val feedGens = feedGensResult.getOrDefault(emptyMap())

        val listUris = normalizedFeeds
            .filter { it.type == "list" || it.value.contains("/app.bsky.graph.list/") }
            .map { it.value }
            .distinct()
        val deadListUris = HashSet<String>()
        val resolvedLists = if (listUris.isEmpty()) {
            emptyMap()
        } else {
            runCatching {
                coroutineScope {
                    listUris.map { uri ->
                        async {
                            val listRes = runCatching {
                                api.getList(uri)["list"]?.let {
                                    AtProtoJson.decodeFromJsonElement<ListView>(it)
                                }
                            }
                            listRes.onFailure { ex ->
                                if (ex is XrpcException && (ex.statusCode == 400 || ex.statusCode == 404)) {
                                    synchronized(deadListUris) { deadListUris.add(uri) }
                                }
                            }
                            listRes.getOrNull()?.let { uri to it }
                        }
                    }.awaitAll().filterNotNull().toMap()
                }
            }.getOrDefault(emptyMap())
        }

        // Filter out dead/deleted feeds omitted by getFeedGenerators or lists returning 400/404,
        // matching RN usePinnedFeedsInfos / useSavedFeeds behavior.
        val activeFeeds = normalizedFeeds.filter { pref ->
            when {
                pref.type == "timeline" -> true
                pref.type == "list" || pref.value.contains("/app.bsky.graph.list/") ->
                    pref.value !in deadListUris
                else ->
                    !feedsFetchSucceeded || feedGens.containsKey(pref.value)
            }
        }

        val allLabelerDids = (listOf(BLUESKY_LABELER_DID) + labelers.map { it.did }).distinct()
        val labelerViews = runCatching {
            api.getLabelerServices(allLabelerDids)
        }.getOrDefault(emptyList())

        commitIfActive(session) {
            val latestMod = preferences.applyServerActorPreferences(did, normalizedFeeds, interests) { prev ->
                prev.copy(
                    adultContentEnabled = adultEnabled,
                    contentLabels = contentLabels,
                    labelers = labelers,
                    labelerContentLabels = labelerPrefs,
                    mutedWords = mutedWords,
                    hiddenPosts = prev.hiddenPosts + hiddenPosts,
                    threadReplyPrioritization = replySort ?: prev.threadReplyPrioritization,
                    threadedReplies = threadedReplies ?: prev.threadedReplies,
                    followFeedDisableReplies = disableReplies,
                    followFeedDisableReposts = disableReposts,
                    followFeedDisableQuotePosts = disableQuotes,
                    hideVerificationBadges = hideVerificationBadges,
                )
            }

            val modOpts = buildModerationOpts(did, latestMod, labelerViews)
            _state.update {
                it.copy(
                    pinnedFeeds = activeFeeds,
                    feedsHydrated = feedsFetchSucceeded || it.feedsHydrated,
                    feedGenerators = it.feedGenerators + feedGens,
                    listViews = it.listViews + resolvedLists,
                    subscribedLabelers = labelerViews,
                    moderationOpts = modOpts,
                )
            }
        }
    }

    fun onFcmTokenUpdated(token: String) {
        _state.update { it.copy(fcmToken = token) }
        val session = _state.value.activeSession() ?: return
        if (!::api.isInitialized) return
        scope.launch(boundSession(session)) {
            runCatching { pushGatewayManager?.registerToken(token) }
            runCatching { syncPushPreferences(session) }
        }
    }

    private fun launchActiveRefresh() {
        val session = _state.value.activeSession() ?: return
        if (!::api.isInitialized) return
        scope.launch(boundSession(session)) {
            runCatching { ensureResolvedPdsUrl(session) }
            runCatching { hydrateProfile(session) }
            runCatching { syncPreferencesFromServer(session.did) }
            runCatching { refreshUnreadCounts() }
            runCatching { syncPushPreferences(session) }
        }
    }

    private suspend fun hydrateProfile(session: ActiveSession): StoredAccount? {
        val profile = api.getProfile(session.did)
        var hydrated: StoredAccount? = null
        commitIfActive(session) {
            val updated = _state.value.accounts.map {
                if (it.did == session.did) {
                    it.copy(displayName = profile.displayName, avatar = profile.avatar, handle = profile.handle)
                        .also { account -> hydrated = account }
                } else it
            }
            preferences.updateAccounts(updated, session.did)
            _state.update { it.copy(accounts = updated) }
        }
        return hydrated
    }

    private suspend fun syncPushPreferences(session: ActiveSession) {
        val manager = pushGatewayManager ?: return
        val prefs = api.getNotificationPreferences()
        manager.syncPreferences(session.did, prefs, persistDisabledReasons = {
            commitIfActive(session) {
                preferences.setDisabledPushReasons(session.did, it)
            }
        })
    }

    internal fun extractPdsFromDidDoc(didDoc: JsonObject?): String? {
        val services = didDoc?.get("service") as? JsonArray ?: return null
        for (el in services) {
            val obj = el as? JsonObject ?: continue
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (id == "#atproto_pds" || id?.endsWith("#atproto_pds") == true || type == "AtprotoPersonalDataServer") {
                return (obj["serviceEndpoint"] as? JsonPrimitive)?.contentOrNull
            }
        }
        return null
    }

    companion object {
        const val BLUESKY_LABELER_DID = "did:plc:ar7c4by46qjdydhdevvrndac"

        fun generateFeedPrefId(): String =
            UUID.randomUUID().toString().replace("-", "").take(13)

        fun normalizeSavedFeeds(feeds: List<SavedFeedPref>): List<SavedFeedPref> {
            val valueIndex = HashMap<String, Int>()
            val seenIds = HashSet<String>()
            val result = ArrayList<SavedFeedPref>(feeds.size + 1)

            for (pref in feeds) {
                val rawValue = pref.value.trim()
                val normType = when {
                    pref.type == "timeline" || rawValue == "following" -> "timeline"
                    pref.type == "list" || rawValue.contains("/app.bsky.graph.list/") -> "list"
                    else -> "feed"
                }
                val normValue = if (normType == "timeline" && rawValue.isEmpty()) "following" else rawValue
                if (normValue.isEmpty()) continue

                val existingIdx = valueIndex[normValue]
                if (existingIdx != null) {
                    // Prefer a real non-URI ID over a legacy corrupted `at://` ID for the same feed.
                    val existing = result[existingIdx]
                    val candidateId = pref.id.trim()
                    val existingCorrupted = existing.id.startsWith("at://")
                    val candidateValid = candidateId.isNotEmpty() && !candidateId.startsWith("at://")
                    if (existingCorrupted && candidateValid && seenIds.add(candidateId)) {
                        seenIds.remove(existing.id)
                        result[existingIdx] = existing.copy(
                            id = candidateId,
                            pinned = existing.pinned || pref.pinned,
                        )
                    }
                    continue
                }

                var normId = pref.id.trim()
                if (normId.isEmpty() || !seenIds.add(normId)) {
                    do {
                        normId = generateFeedPrefId()
                    } while (!seenIds.add(normId))
                }
                valueIndex[normValue] = result.size
                result.add(
                    SavedFeedPref(
                        id = normId,
                        type = normType,
                        value = normValue,
                        pinned = pref.pinned,
                    )
                )
            }

            if (result.none { it.type == "timeline" }) {
                result.add(
                    0,
                    SavedFeedPref(
                        id = if (seenIds.add("timeline-default")) "timeline-default" else generateFeedPrefId(),
                        type = "timeline",
                        value = "following",
                        pinned = true,
                    ),
                )
            }
            return result
        }

        fun buildModerationOpts(
            userDid: String?,
            settings: ModerationUserSettings,
            labelerViews: List<LabelerViewDetailed>,
        ): ModerationOpts {
            val globalLabels = LinkedHashMap<String, LabelPreference>(DEFAULT_LABEL_SETTINGS)
            for ((k, v) in settings.contentLabels) {
                LabelPreference.fromWire(v)?.let { globalLabels[k] = it }
            }
            val byLabeler = LinkedHashMap<String, MutableMap<String, LabelPreference>>()
            for (pref in settings.labelerContentLabels) {
                val lDid = pref.labelerDid ?: continue
                val vis = LabelPreference.fromWire(pref.visibility) ?: continue
                byLabeler.getOrPut(lDid) { LinkedHashMap() }[pref.label] = vis
            }

            val labelerDids = (listOf(BLUESKY_LABELER_DID) + settings.labelers.map { it.did }).distinct()
            val labelerPrefs = labelerDids.map { did ->
                LabelerPref(did = did, labels = byLabeler[did].orEmpty())
            }

            val labelDefs = LinkedHashMap<String, List<InterpretedLabelDef>>()
            for (view in labelerViews) {
                val defs = view.policies.labelValueDefinitions.map { def ->
                    ModerationEngine.interpretLabelValueDefinition(def, view.creator.did)
                }
                if (defs.isNotEmpty()) {
                    labelDefs[view.creator.did] = defs
                }
            }

            return ModerationOpts(
                userDid = userDid,
                prefs = ModerationPrefs(
                    adultContentEnabled = settings.adultContentEnabled,
                    labels = globalLabels,
                    labelers = labelerPrefs,
                    mutedWords = settings.mutedWords,
                    hiddenPosts = settings.hiddenPosts.toList(),
                ),
                labelDefs = labelDefs,
                labelerProfiles = labelerViews.associate { it.creator.did to it.creator },
            )
        }
    }
}
