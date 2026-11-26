package am.noz.caelum.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import am.noz.caelum.CaelumApplication
import am.noz.caelum.MainActivity
import am.noz.caelum.R
import am.noz.caelum.core.network.PushGatewayManager
import am.noz.caelum.ui.navigation.AppRoute
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Caelum push-gateway FCM data-only payloads, suppresses locally disabled
 * notification reasons, and routes notification taps to native Compose screens.
 */
class CaelumFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext as? CaelumApplication ?: return
        app.container.sessionManager.onFcmTokenUpdated(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = applicationContext as? CaelumApplication ?: return
        val container = app.container
        val parsed = PushGatewayManager.parseFcmData(message.data) ?: return

        if (!container.pushGatewayManager.shouldDisplayNotification(parsed.recipientDid, parsed.reason)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val targetUrl = routeToDeepLinkUrl(parsed.targetRoute)
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(targetUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            parsed.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, parsed.channelId)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(parsed.title)
            .setContentText(parsed.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(parsed.message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSilent(parsed.reason == "chat-message" && !container.preferences.getPlaySoundChatImmediate())
            .build()

        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() and 0x7fffffffL).toInt(), notification)
    }

    private fun routeToDeepLinkUrl(route: AppRoute): String = when (route) {
        is AppRoute.PostThread -> "blueskyplus://profile/${route.name}/post/${route.rkey}"
        is AppRoute.Profile -> "blueskyplus://profile/${route.name}"
        is AppRoute.CustomFeed -> "blueskyplus://profile/${route.name}/feed/${route.rkey}"
        is AppRoute.ProfileList -> "blueskyplus://profile/${route.name}/lists/${route.rkey}"
        is AppRoute.StarterPack -> "blueskyplus://starter-pack/${route.name}/${route.rkey}"
        is AppRoute.MessagesConversation -> "blueskyplus://messages/${route.conversation}"
        AppRoute.Messages -> "blueskyplus://messages"
        else -> "blueskyplus://notifications"
    }
}
