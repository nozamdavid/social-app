package am.noz.caelum.di

import android.content.Context
import am.noz.caelum.core.alttext.OnDeviceAltTextEngine
import am.noz.caelum.core.emoji.EmojiSearchEngine
import am.noz.caelum.core.i18n.I18nCatalog
import am.noz.caelum.core.media.CaelumImageLoader
import am.noz.caelum.core.media.FeedVideoPlayerPool
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostShadowStore
import am.noz.caelum.core.model.ToggleMutationQueue
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.IdentityResolver
import am.noz.caelum.core.network.InfrastructureConfig
import am.noz.caelum.core.network.InfrastructureSettings
import am.noz.caelum.core.network.PushGatewayManager
import am.noz.caelum.core.network.XrpcClient
import am.noz.caelum.core.post.PostInteractionController
import am.noz.caelum.core.session.ScreenStateCacheStore
import am.noz.caelum.core.session.SessionManager
import am.noz.caelum.core.storage.AndroidKeystoreTokenVault
import am.noz.caelum.core.storage.CaelumDatabase
import am.noz.caelum.core.storage.LegacyRnMigrationManager
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.core.storage.SearchableBookmarkEngine
import am.noz.caelum.core.storage.TokenVault
import am.noz.caelum.ui.navigation.AppRoute
import coil3.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

/**
 * Application dependency graph and shared singletons.
 * Loads heavy JSON assets (TLDs, Emoji index, Lingui catalogs) off the main thread
 * so cold start remains minimal and responsive.
 */
class AppContainer(
    val context: Context,
) {
    val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val okHttpClient: OkHttpClient = XrpcClient.defaultOkHttpClient()

    val preferences: PreferencesRepository = PreferencesRepository.create(context, scope = CoroutineScope(Dispatchers.IO + SupervisorJob()))
    val tokenVault: TokenVault = AndroidKeystoreTokenVault(context)
    val database: CaelumDatabase = CaelumDatabase.create(context)
    val bookmarkEngine = SearchableBookmarkEngine(database.bookmarkIndexDao())
    val migrationManager = LegacyRnMigrationManager(context, preferences, tokenVault)

    val postShadowStore = PostShadowStore()
    val toggleMutationQueue = ToggleMutationQueue()
    private val screenCaches = ScreenStateCacheStore()

    fun screenCacheFor(activation: Long) = screenCaches.forActivation(activation)

    @Volatile
    var latestInfra: InfrastructureSettings = InfrastructureConfig.DEFAULT
        private set

    val sessionManager = SessionManager(
        preferences = preferences,
        tokenVault = tokenVault,
        postShadowStore = postShadowStore,
        migrationManager = migrationManager,
        scope = applicationScope,
    )

    val xrpcClient = XrpcClient(
        okHttpClient = okHttpClient,
        sessionDelegate = sessionManager,
        infraProvider = { latestInfra },
    )

    val identityResolver = IdentityResolver(
        okHttpClient = okHttpClient,
        infraProvider = { latestInfra },
    )

    val api = AtprotoApi(xrpcClient)
    val pushGatewayManager = PushGatewayManager(
        api = api,
        preferences = preferences,
        pushGatewayDid = am.noz.caelum.BuildConfig.PUSH_GATEWAY_DID
            .ifBlank { PushGatewayManager.DEFAULT_PUSH_GATEWAY_DID },
    )

    val postController = PostInteractionController(
        api = api,
        sessionManager = sessionManager,
        preferences = preferences,
        shadowStore = postShadowStore,
        mutationQueue = toggleMutationQueue,
        bookmarkEngine = bookmarkEngine,
        tldsProvider = { tlds },
    )

    val imageLoader: ImageLoader = CaelumImageLoader.create(
        context = context,
        okHttpClient = okHttpClient,
        getImageCdnHostname = { latestInfra.imageCdnHostname },
    )

    val videoPlayerPool = FeedVideoPlayerPool(context, okHttpClient)
    val altTextEngine = OnDeviceAltTextEngine(context)

    private val _emojiEngine = MutableStateFlow<EmojiSearchEngine?>(null)
    val emojiEngine: StateFlow<EmojiSearchEngine?> = _emojiEngine.asStateFlow()

    private val _i18nCatalog = MutableStateFlow(
        I18nCatalog("en", emptyMap(), emptyMap(), emptyMap())
    )
    val i18nCatalog: StateFlow<I18nCatalog> = _i18nCatalog.asStateFlow()

    @Volatile
    var tlds: Set<String> = DEFAULT_TLDS_FALLBACK
        private set

    private val _deepLinkEvents = MutableSharedFlow<AppRoute>(extraBufferCapacity = 16)
    val deepLinkEvents = _deepLinkEvents.asSharedFlow()

    init {
        sessionManager.attachNetwork(identityResolver, api, pushGatewayManager)

        applicationScope.launch(Dispatchers.IO) {
            sessionManager.state
                .map { it.currentDid }
                .distinctUntilChanged()
                .collectLatest { did ->
                    if (did != null) {
                        try {
                            postController.migrateLegacyThreadMutes(did)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Keep failed legacy mutes for the next sign-in.
                        }
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            preferences.settingsFlow.collect { snap ->
                latestInfra = snap.infrastructure
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            preferences.settingsFlow
                .map { it.appLanguage }
                .distinctUntilChanged()
                .collect { lang ->
                    val effective = lang.ifBlank { java.util.Locale.getDefault().toLanguageTag() }
                    _i18nCatalog.value = I18nCatalog.loadFromAssets(context, effective)
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            sessionManager.initialize()
        }

        runCatching {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        sessionManager.onFcmTokenUpdated(token)
                    }
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                context.assets.open("tlds.json").bufferedReader().use { r ->
                    AtProtoJson.parseToJsonElement(r.readText()).jsonArray
                        .map { it.jsonPrimitive.content.lowercase() }
                        .toSet()
                }
            }.getOrNull()?.let { loaded ->
                tlds = loaded
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            runCatching { EmojiSearchEngine.fromAssets(context) }.getOrNull()?.let {
                _emojiEngine.value = it
            }
        }
    }

    fun emitDeepLinkRoute(route: AppRoute) {
        _deepLinkEvents.tryEmit(route)
    }

    companion object {
        private val DEFAULT_TLDS_FALLBACK = setOf(
            "com", "org", "net", "edu", "gov", "io", "dev", "app", "social",
            "bsky", "uk", "de", "fr", "jp", "ca", "au", "network", "am", "community",
        )
    }
}
