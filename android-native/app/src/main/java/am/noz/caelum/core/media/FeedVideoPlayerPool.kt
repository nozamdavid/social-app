package am.noz.caelum.core.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.File

/**
 * Bounded Media3 player pool supporting a single active inline/fullscreen player
 * with automatic lifecycle pause/release and shared disk cache.
 */
@OptIn(UnstableApi::class)
class FeedVideoPlayerPool(
    context: Context,
    okHttpClient: OkHttpClient,
) : DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private val cache by lazy {
        val cacheDir = File(appContext.cacheDir, "media3_cache").apply { mkdirs() }
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L),
            StandaloneDatabaseProvider(appContext),
        )
    }
    private val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)
    private val cacheDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private var player: ExoPlayer? = null
    private var activeUrl: String? = null

    private val _muted = MutableStateFlow(true)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _activeUri = MutableStateFlow<String?>(null)
    val activeUri: StateFlow<String?> = _activeUri.asStateFlow()

    fun acquirePlayer(url: String, loop: Boolean = true, autoPlay: Boolean = true): ExoPlayer {
        val current = player ?: ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build().also {
                player = it
            }

        current.volume = if (_muted.value) 0f else 1f
        current.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        if (activeUrl != url) {
            activeUrl = url
            _activeUri.value = url
            current.setMediaItem(MediaItem.fromUri(url))
            current.prepare()
        }
        current.playWhenReady = autoPlay
        return current
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        player?.volume = if (muted) 0f else 1f
    }

    fun toggleMuted() {
        setMuted(!_muted.value)
    }

    fun pauseActive() {
        player?.playWhenReady = false
    }

    fun releasePlayerIfUrl(url: String) {
        if (activeUrl == url) {
            pauseActive()
            activeUrl = null
            _activeUri.value = null
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        pauseActive()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseAll()
    }

    fun releaseAll() {
        player?.release()
        player = null
        activeUrl = null
        _activeUri.value = null
    }
}
