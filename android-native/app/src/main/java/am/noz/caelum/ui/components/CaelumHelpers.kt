package am.noz.caelum.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.network.InfrastructureConfig
import am.noz.caelum.di.AppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkParser
import am.noz.caelum.ui.navigation.DeepLinkTarget
import coil3.compose.AsyncImage
import java.net.URI

val LocalScreenStateCache = staticCompositionLocalOf<am.noz.caelum.core.session.ScreenStateCache> {
    error("LocalScreenStateCache not provided")
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided")
}

/** Keeps every request in a multi-step action on its originating account and PDS. */
@Composable
internal fun rememberAccountCoroutineScope(): CoroutineScope {
    val manager = LocalAppContainer.current.sessionManager
    val session = manager.state.collectAsState().value
    return key(manager, session.activation) {
        rememberCoroutineScope {
            session.currentDid?.let { manager.bindAccount(it, session.activation) } ?: EmptyCoroutineContext
        }
    }
}

val LocalNavigate = staticCompositionLocalOf<(AppRoute) -> Unit> {
    {}
}

val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> {
    {}
}

class CaelumHaptics(
    private val view: View,
    private val disabled: Boolean,
) {
    fun tap() {
        if (disabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun confirm() {
        if (disabled) return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun longPress() {
        if (disabled) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun toggle(on: Boolean) {
        if (disabled) return
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            view.performHapticFeedback(
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            )
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun selection() {
        if (disabled) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberHaptics(disableHaptics: Boolean): CaelumHaptics {
    val view = LocalView.current
    return remember(view, disableHaptics) { CaelumHaptics(view, disableHaptics) }
}

@Composable
fun CaelumLogoMark(
    size: Dp = 28.dp,
    tint: Color? = null,
    useDarkVariant: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val asset = if (useDarkVariant) {
        "file:///android_asset/branding/mark-dark.svg"
    } else {
        "file:///android_asset/branding/mark.svg"
    }
    AsyncImage(
        model = asset,
        contentDescription = "Caelum",
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(size),
    )
}

object LinkNavigationHelper {
    private val BARE_DOMAIN_REGEX = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+([/:?#].*)?$", RegexOption.IGNORE_CASE)

    fun extractHost(rawUrl: String): String? = runCatching {
        val normalized = if (rawUrl.contains("://")) rawUrl else "https://$rawUrl"
        URI(normalized).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    fun isPotentiallyMisleadingLink(displayText: String, targetUri: String): Boolean {
        val cleanText = displayText.trim().removeSuffix("...")
        if (!BARE_DOMAIN_REGEX.matches(cleanText) &&
            !cleanText.startsWith("http://", ignoreCase = true) &&
            !cleanText.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val textHost = extractHost(cleanText) ?: return false
        val targetHost = extractHost(targetUri) ?: return false
        return textHost != targetHost && !targetHost.endsWith(".$textHost")
    }

    fun openUrl(
        context: Context,
        rawUrl: String,
        useLinkRedirects: Boolean,
        openBskyLinksInCaelum: Boolean,
        onInternalRoute: (AppRoute) -> Unit,
    ) {
        if (openBskyLinksInCaelum) {
            val target = DeepLinkParser.parseUri(rawUrl)
            if (target is DeepLinkTarget.RouteTarget && target.route !is AppRoute.NotFound) {
                onInternalRoute(target.route)
                return
            }
        }
        val finalUrl = InfrastructureConfig.createProxiedUrl(rawUrl, useLinkRedirects)
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(finalUrl))
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String = "Copied to clipboard") {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun postCanonicalWebUrl(handleOrDid: String, postUri: String): String {
        val rkey = postUri.substringAfterLast('/')
        return "https://bsky.app/profile/$handleOrDid/post/$rkey"
    }
}

@Composable
fun LinkMismatchWarningDialog(
    displayText: String,
    targetUri: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Confirm destination link")) },
        text = {
            Text(tr("The link text reads “{displayText}”, but points to:\n\n{targetUri}\n\nOpen this URL?", mapOf("displayText" to displayText, "targetUri" to targetUri)))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(tr("Open link"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("Cancel"))
            }
        },
    )
}
