package am.noz.caelum

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import am.noz.caelum.ui.CaelumRootApp
import am.noz.caelum.ui.navigation.DeepLinkParser
import am.noz.caelum.ui.navigation.DeepLinkTarget
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private data class ParsedIntent(
        val deepLink: DeepLinkTarget? = null,
        val sharedText: String? = null,
        val sharedMedia: List<Uri> = emptyList(),
    )

    private val liveComposeEvents = MutableSharedFlow<DeepLinkTarget.ComposeIntentTarget>(
        extraBufferCapacity = 8,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CaelumApplication).container
        val initial = if (savedInstanceState == null) parseIntent(intent) else ParsedIntent()

        setContent {
            CaelumRootApp(
                container = container,
                initialDeepLink = initial.deepLink,
                initialSharedText = initial.sharedText,
                initialSharedMedia = initial.sharedMedia,
                liveComposeEvents = liveComposeEvents,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val container = (application as CaelumApplication).container
        val parsed = parseIntent(intent)

        when (val target = parsed.deepLink) {
            is DeepLinkTarget.RouteTarget -> container.emitDeepLinkRoute(target.route)
            is DeepLinkTarget.ComposeIntentTarget -> liveComposeEvents.tryEmit(target)
            null -> {
                if (!parsed.sharedText.isNullOrBlank() || parsed.sharedMedia.isNotEmpty()) {
                    liveComposeEvents.tryEmit(
                        DeepLinkTarget.ComposeIntentTarget(
                            text = parsed.sharedText,
                            imageUris = parsed.sharedMedia.map { it.toString() },
                        )
                    )
                }
            }
        }
    }

    private fun parseIntent(intent: Intent?): ParsedIntent {
        if (intent == null) return ParsedIntent()

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrBlank()) {
                    val target = DeepLinkParser.parseUri(dataUri)
                    if (target != null) {
                        return ParsedIntent(deepLink = target)
                    }
                }
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val media = listOfNotNull(streamUri)
                if (!text.isNullOrBlank() || media.isNotEmpty()) {
                    return ParsedIntent(sharedText = text, sharedMedia = media)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val media = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                ).orEmpty()
                if (!text.isNullOrBlank() || media.isNotEmpty()) {
                    return ParsedIntent(sharedText = text, sharedMedia = media)
                }
            }
        }

        // Notification extras (e.g. FCM/system notification carrying "uri")
        val extraUri = intent.getStringExtra("uri")?.trim()
        if (!extraUri.isNullOrEmpty()) {
            val target = if (extraUri.startsWith("at://")) {
                DeepLinkParser.atUriToRoute(extraUri)?.let { DeepLinkTarget.RouteTarget(it) }
            } else {
                DeepLinkParser.parseUri(extraUri)
            }
            if (target != null) {
                return ParsedIntent(deepLink = target)
            }
        }

        return ParsedIntent()
    }
}
