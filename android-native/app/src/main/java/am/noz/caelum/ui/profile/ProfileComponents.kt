package am.noz.caelum.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StarterPackViewBasic
import am.noz.caelum.core.moderation.LabelPreference
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.richtext.FacetFeature
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.serialization.json.JsonPrimitive

internal val GITHUB_SPONSORS_GREEN_DARK = Color(0xFF238636)
internal val GITHUB_SPONSORS_GREEN_BRIGHT = Color(0xFF2EA043)

private val GITHUB_SPONSORS_REGEX = Regex(
    """(?:https?://)?(?:www\.)?github\.com/sponsors/([A-Za-z0-9_-]+)(?:[/?#][^\s)]*)?""",
    RegexOption.IGNORE_CASE,
)

internal fun ProfileViewDetailed.toBasicProfile(): ProfileViewBasic = ProfileViewBasic(
    did = did,
    handle = handle,
    displayName = displayName,
    pronouns = pronouns,
    avatar = avatar,
    associated = associated,
    viewer = viewer,
    labels = labels,
    verification = verification,
    status = status,
)

internal fun extractGitHubSponsorUrls(
    description: String?,
    facets: List<RichTextFacet>,
    website: String?,
): List<String> {
    val found = linkedMapOf<String, String>()
    fun collectFrom(raw: String?) {
        if (raw.isNullOrBlank()) return
        for (match in GITHUB_SPONSORS_REGEX.findAll(raw)) {
            val slug = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (slug.isNotEmpty()) {
                found.putIfAbsent(slug.lowercase(), "https://github.com/sponsors/$slug")
            }
        }
    }
    collectFrom(website)
    for (facet in facets) {
        for (feature in facet.features) {
            if (feature is FacetFeature.Link) {
                collectFrom(feature.uri)
            }
        }
    }
    collectFrom(description)
    return found.values.toList()
}

@Composable
internal fun GitHubSponsorPill(
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slug = url.substringAfter("/sponsors/", "").substringBefore('/').substringBefore('?')
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(GITHUB_SPONSORS_GREEN_DARK)
            .border(1.dp, GITHUB_SPONSORS_GREEN_BRIGHT, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (slug.isNotBlank()) "Sponsor @$slug on GitHub" else "GitHub Sponsors",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
internal fun FeedGeneratorRow(
    feed: GeneratorView,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = feed.avatar,
                contentDescription = feed.displayName,
                size = 44.dp,
                shapeType = AvatarShape.RoundedSquare,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = feed.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tr("Feed by @${feed.creator.handle}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!feed.description.isNullOrBlank()) {
                    Text(
                        text = feed.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val likes = feed.likeCount ?: 0
                if (likes > 0) {
                    Text(
                        text = tr("Liked by {count} users", mapOf("count" to formatCompactCount(likes))),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
internal fun ProfileListRow(
    list: ListView,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    val purposeLabel = when (list.purpose) {
        "app.bsky.graph.defs#modlist" -> tr("Moderation list")
        "app.bsky.graph.defs#curatelist" -> tr("User list")
        "app.bsky.graph.defs#referencelist" -> tr("Starter pack list")
        else -> tr("List")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = list.avatar,
                contentDescription = list.name,
                size = 44.dp,
                shapeType = AvatarShape.RoundedSquare,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$purposeLabel · @${list.creator.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!list.description.isNullOrBlank()) {
                    Text(
                        text = list.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
internal fun StarterPackRow(
    pack: StarterPackViewBasic,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    val name = (pack.record["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: tr("Starter Pack")
    val desc = (pack.record["description"] as? JsonPrimitive)?.content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.primary50.toComposeColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    tint = palette.primary500.toComposeColor(),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tr("Starter pack by @${pack.creator.handle}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val joinedAll = pack.joinedAllTimeCount ?: 0
                if (joinedAll > 0) {
                    Text(
                        text = "$joinedAll users joined",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
internal fun LabelerLabelsList(
    labeler: LabelerViewDetailed?,
    isLoading: Boolean,
    isSubscribed: Boolean,
    isBlocked: Boolean,
    modSettings: ModerationUserSettings,
    hasSession: Boolean,
    onSelectVisibility: (labelId: String, visibility: LabelPreference) -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = tr("Labels are annotations on users and content. They can be used to hide, warn, and categorize the network."),
            style = MaterialTheme.typography.bodySmall,
            color = palette.contrast700.toComposeColor(),
        )

        if (isBlocked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.contrast25.toComposeColor())
                    .padding(10.dp),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                Text(
                    text = tr("Blocking does not prevent this labeler from placing labels on your account."),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast800.toComposeColor(),
                )
            }
        }

        if (isLoading && labeler == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        if (labeler == null) {
            Text(
                text = tr("Unable to load labeler configuration."),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            return
        }

        val interpretedDefs = remember(labeler) {
            val customById = labeler.policies.labelValueDefinitions.associateBy { it.identifier }
            labeler.policies.labelValues.distinct().mapNotNull { id ->
                customById[id]?.let { def ->
                    ModerationEngine.interpretLabelValueDefinition(def, labeler.creator.did)
                }
            }
        }

        if (interpretedDefs.isEmpty()) {
            Text(
                text = tr("This labeler hasn't declared what labels it publishes, and may not be active."),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            return
        }

        if (!isSubscribed) {
            Text(
                text = tr("Subscribe to @${labeler.creator.handle} to use these labels:"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.contrast800.toComposeColor(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.contrast25.toComposeColor())
                .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
        ) {
            interpretedDefs.forEachIndexed { index, def ->
                if (index > 0) {
                    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                }
                val savedVis = modSettings.labelerContentLabels
                    .firstOrNull { it.labelerDid == labeler.creator.did && it.label == def.identifier }
                    ?.visibility
                    ?.let { LabelPreference.fromWire(it) }
                    ?: def.defaultSetting

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = def.title?.takeIf { it.isNotBlank() } ?: def.identifier,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.contrast1000.toComposeColor(),
                            modifier = Modifier.weight(1f),
                        )
                        if ("adult" in def.flags) {
                            Text(
                                text = "18+",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.negative500.toComposeColor(),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.negative50.toComposeColor())
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    if (!def.description.isNullOrBlank()) {
                        Text(
                            text = def.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }

                    val enabled = isSubscribed && hasSession
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.contrast50.toComposeColor())
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf(
                            LabelPreference.ignore to tr("Off"),
                            LabelPreference.warn to tr("Warn"),
                            LabelPreference.hide to tr("Hide"),
                        ).forEach { (pref, labelText) ->
                            val selected = savedVis == pref
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            selected -> palette.primary500.toComposeColor()
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = enabled) {
                                        onSelectVisibility(def.identifier, pref)
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    ),
                                    color = when {
                                        selected -> Color.White
                                        enabled -> palette.contrast800.toComposeColor()
                                        else -> palette.contrast400.toComposeColor()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportAccountDialog(
    handle: String,
    isSubmitting: Boolean,
    onSubmit: (reasonType: String, details: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val reasons = remember {
        listOf(
            "com.atproto.moderation.defs#reasonSpam" to "Spam",
            "com.atproto.moderation.defs#reasonViolation" to "Community guidelines violation",
            "com.atproto.moderation.defs#reasonMisleading" to "Misleading or impersonation",
            "com.atproto.moderation.defs#reasonSexual" to "Unwanted sexual content",
            "com.atproto.moderation.defs#reasonRude" to "Harassment or abuse",
            "com.atproto.moderation.defs#reasonOther" to "Other",
        )
    }
    var selectedReason by remember { mutableStateOf(reasons.first().first) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(tr("Report account")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "@$handle",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
                reasons.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitting) { selectedReason = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedReason == type,
                            onClick = { if (!isSubmitting) selectedReason = type },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 300) details = it },
                    label = { Text(tr("Additional details (optional)")) },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details.trim()) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.negative500.toComposeColor(),
                    contentColor = Color.White,
                ),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(tr("Send report"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(tr("Cancel"))
            }
        },
    )
}
