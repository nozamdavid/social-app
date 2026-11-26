package am.noz.caelum.core.media

import android.content.Context
import am.noz.caelum.core.network.InfrastructureConfig
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

object CaelumImageLoader {

    fun create(
        context: Context,
        okHttpClient: OkHttpClient,
        getImageCdnHostname: () -> String,
    ): ImageLoader {
        val cdnRewriter = Interceptor { chain ->
            val data = chain.request.data
            if (data is String) {
                val rewritten = InfrastructureConfig.rewriteImageUrl(data, getImageCdnHostname())
                if (rewritten != data) {
                    val updated = chain.request.newBuilder()
                        .data(rewritten)
                        .build()
                    return@Interceptor chain.withRequest(updated).proceed()
                }
            }
            chain.proceed()
        }

        return ImageLoader.Builder(context)
            .components {
                add(cdnRewriter)
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .crossfade(120)
            .build()
    }

    suspend fun clearDiskCache(imageLoader: ImageLoader) {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }
}
