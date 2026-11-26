package am.noz.caelum.ui.post

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.PostView
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun PostAuthorFollowButton(post: PostView, currentUserDid: String?, disableHaptics: Boolean) {
    val blocked = post.isAuthorBlocked
    val authorDid = post.author.did
    val rules = post.threadgate?.record?.get("allow") as? JsonArray
    val hasFollowerRule = rules?.any {
        ((it as? JsonObject)?.get("\$type") as? JsonPrimitive)?.content == "app.bsky.feed.threadgate#followerRule"
    } == true
    if (currentUserDid == null || authorDid.isBlank() || currentUserDid == authorDid || blocked || hasFollowerRule) return

    val originalFollow = post.author.viewer?.following?.takeIf(String::isNotBlank)
    var followUri by remember(currentUserDid, authorDid, originalFollow) { mutableStateOf(originalFollow) }
    var isPending by remember(currentUserDid, authorDid) { mutableStateOf(false) }
    if (!originalFollow.isNullOrBlank()) return

    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val context = LocalContext.current
    val haptics = rememberHaptics(disableHaptics)
    val scope = rememberAccountCoroutineScope()
    val failed = tr("Something went wrong. Please try again.")
    val following = !followUri.isNullOrBlank()
    val label = when {
        following -> tr("Following")
        !post.author.viewer?.followedBy.isNullOrBlank() -> tr("Follow back")
        else -> tr("Follow")
    }
    val actionLabel = if (following) tr("Unfollow account") else label

    Button(
        onClick = {
            if (!isPending) {
                val previousFollow = followUri
                isPending = true
                scope.launch {
                    try {
                        val updatedFollow = if (previousFollow == null) {
                            container.api.followActor(currentUserDid, authorDid).uri
                        } else {
                            container.api.deleteRecord(currentUserDid, "app.bsky.graph.follow", previousFollow.substringAfterLast('/'))
                            null
                        }
                        if (container.sessionManager.state.value.currentDid == currentUserDid) {
                            followUri = updatedFollow
                            haptics.confirm()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                    } finally {
                        isPending = false
                    }
                }
            }
        },
        enabled = !isPending,
        modifier = Modifier.testTag("followBtn").semantics { contentDescription = actionLabel },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (following) palette.contrast100.toComposeColor() else palette.contrast1000.toComposeColor(),
            contentColor = if (following) palette.contrast900.toComposeColor() else palette.contrast0.toComposeColor(),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, modifier = Modifier.clearAndSetSemantics {})
    }
}
