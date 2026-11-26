package am.noz.caelum

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ProcessLifecycleOwner
import am.noz.caelum.di.AppContainer
import coil3.ImageLoader
import coil3.SingletonImageLoader

class CaelumApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.videoPlayerPool)
        createNotificationChannels()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return container.imageLoader
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return

        val socialChannels = listOf(
            "like" to "Likes",
            "repost" to "Reposts",
            "follow" to "Followers",
            "mention" to "Mentions",
            "reply" to "Replies",
            "quote" to "Quotes",
            "subscribed-post" to "Subscribed accounts",
            "starterpack-joined" to "Starter packs",
            "default" to "General notifications",
        ).map { (id, title) ->
            NotificationChannel(id, title, NotificationManager.IMPORTANCE_DEFAULT)
        }

        val chatSoundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.dm}")
        val chatAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val chatChannel = NotificationChannel(
            "chat-messages",
            "Direct messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(chatSoundUri, chatAttrs)
        }

        nm.createNotificationChannels(socialChannels + chatChannel)
    }
}
