package am.noz.caelum.ui.openin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import am.noz.caelum.R
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExternalAtprotoTarget(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val iconRes: Int? = null,
    val iconUrl: String? = null,
)

sealed interface OpenInSubject {
    data class Profile(val did: String, val handle: String) : OpenInSubject
    data class Post(val atUri: String, val authorDid: String, val handle: String) : OpenInSubject
    data class Feed(val atUri: String) : OpenInSubject
    data class ListRecord(val atUri: String) : OpenInSubject
}

/**
 * Caelum ATProto ecosystem "Open in..." discovery sheet (`src/features/atprotoApps/`):
 * Supports `pds.ls`, Tangled, Grain, sifa.id, Streamplace, and live PDS discovery of
 * `site.standard.publication` publications (with optional PDS icon blob).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenInBottomSheet(
    subject: OpenInSubject,
    onDismiss: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())

    var discoveredTargets by remember(subject) { mutableStateOf<List<ExternalAtprotoTarget>>(emptyList()) }
    var isDiscovering by remember(subject) { mutableStateOf(false) }

    val baseTargets = remember(subject) { buildStaticTargets(subject) }

    LaunchedEffect(subject) {
        val targetDid = when (subject) {
            is OpenInSubject.Profile -> subject.did
            is OpenInSubject.Post -> subject.authorDid
            else -> null
        } ?: return@LaunchedEffect

        isDiscovering = true
        runCatching {
            val pds = container.identityResolver.resolvePdsUrl(targetDid) ?: return@runCatching
            val handle = when (subject) {
                is OpenInSubject.Profile -> subject.handle
                is OpenInSubject.Post -> subject.handle
                else -> ""
            }

            suspend fun checkCollection(collection: String): Boolean = runCatching {
                container.api.listPdsRecords(pds, targetDid, collection, limit = 1).records.isNotEmpty()
            }.getOrDefault(false)

            val found = coroutineScope {
                val pubsDeferred = async {
                    runCatching {
                        val pubs = container.api.listPdsRecords(pds, targetDid, "site.standard.publication", limit = 10)
                        pubs.records.mapNotNull { rec ->
                            val v = rec.value
                            val url = (v["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                            val name = (v["name"] as? JsonPrimitive)?.content ?: url
                            val desc = (v["description"] as? JsonPrimitive)?.content ?: "Standard.site publication"
                            val iconCid = (v["icon"] as? JsonObject)?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content
                            val iconUrl = iconCid?.let { "$pds/xrpc/com.atproto.sync.getBlob?did=$targetDid&cid=$it" }
                            ExternalAtprotoTarget(
                                id = "std_${rec.uri}",
                                name = name,
                                description = desc,
                                url = url,
                                iconRes = R.drawable.standard_site_favicon,
                                iconUrl = iconUrl,
                            )
                        }
                    }.getOrDefault(emptyList())
                }
                val hasTangled = async { if (handle.isNotBlank()) checkCollection("sh.tangled.repo") else false }
                val hasGrain = async { if (handle.isNotBlank()) checkCollection("social.grain.gallery") else false }
                val hasStreamplace = async { if (handle.isNotBlank()) checkCollection("place.stream.key") else false }
                val hasSifa = async {
                    if (handle.isNotBlank()) {
                        targetDid.startsWith("did:") || checkCollection("id.sifa.profile.self")
                    } else {
                        false
                    }
                }

                buildList {
                    addAll(pubsDeferred.await())
                    if (hasTangled.await()) {
                        add(ExternalAtprotoTarget("tangled", "Tangled", "Git hosting on ATProto", "https://tangled.org/@$handle", iconRes = R.drawable.tangled_favicon))
                    }
                    if (hasGrain.await()) {
                        add(ExternalAtprotoTarget("grain", "Grain", "ATProto photo galleries", "https://grain.social/profile/$handle", iconRes = R.drawable.grain_favicon))
                    }
                    if (hasStreamplace.await()) {
                        add(ExternalAtprotoTarget("streamplace", "Streamplace", "ATProto livestreaming", "https://stream.place/$handle", iconRes = R.drawable.streamplace_favicon))
                    }
                    if (hasSifa.await()) {
                        add(ExternalAtprotoTarget("sifa", "sifa.id", "ATProto professional profile", "https://sifa.id/p/$handle", iconRes = R.drawable.sifa_favicon))
                    }
                }
            }
            discoveredTargets = found
        }
        isDiscovering = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Open in ATProto app",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.contrast1000.toComposeColor(),
                )
                if (isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            for (item in discoveredTargets + baseTargets) {
                ListItem(
                    leadingContent = {
                        when {
                            !item.iconUrl.isNullOrBlank() -> AsyncImage(
                                model = item.iconUrl,
                                contentDescription = item.name,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            item.iconRes != null -> AsyncImage(
                                model = item.iconRes,
                                fallback = painterResource(item.iconRes),
                                contentDescription = item.name,
                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            else -> Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    },
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(item.description) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        LinkNavigationHelper.openUrl(
                            context = context,
                            rawUrl = item.url,
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = false,
                            onInternalRoute = onNavigate,
                        )
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun buildStaticTargets(subject: OpenInSubject): List<ExternalAtprotoTarget> = when (subject) {
    is OpenInSubject.Profile -> listOf(
        ExternalAtprotoTarget(
            id = "pdsls",
            name = "PDSls",
            description = "Browse raw ATProto repository records",
            url = "https://pdsls.dev/at://${subject.did}",
            iconRes = R.drawable.pdsls_favicon,
        ),
        ExternalAtprotoTarget(
            id = "bskyweb",
            name = "Bluesky Web",
            description = "https://bsky.app/profile/${subject.handle}",
            url = "https://bsky.app/profile/${subject.handle}",
        ),
    )
    is OpenInSubject.Post -> {
        val rkey = subject.atUri.substringAfterLast('/')
        listOf(
            ExternalAtprotoTarget(
                id = "pdsls_post",
                name = "PDSls",
                description = "Inspect raw post record & AT URI",
                url = "https://pdsls.dev/${subject.atUri}",
                iconRes = R.drawable.pdsls_favicon,
            ),
            ExternalAtprotoTarget(
                id = "bskyweb_post",
                name = "Bluesky Web",
                description = "https://bsky.app/profile/${subject.handle}/post/$rkey",
                url = "https://bsky.app/profile/${subject.handle}/post/$rkey",
            ),
        )
    }
    is OpenInSubject.Feed -> listOf(
        ExternalAtprotoTarget(
            id = "pdsls_feed",
            name = "PDSls",
            description = "Inspect feed generator record",
            url = "https://pdsls.dev/${subject.atUri}",
            iconRes = R.drawable.pdsls_favicon,
        )
    )
    is OpenInSubject.ListRecord -> listOf(
        ExternalAtprotoTarget(
            id = "pdsls_list",
            name = "PDSls",
            description = "Inspect list record",
            url = "https://pdsls.dev/${subject.atUri}",
            iconRes = R.drawable.pdsls_favicon,
        )
    )
}
