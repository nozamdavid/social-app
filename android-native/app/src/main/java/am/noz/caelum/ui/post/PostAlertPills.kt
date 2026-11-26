package am.noz.caelum.ui.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.moderation.ModerationCause
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.moderation.ModerationUI
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor

@Composable
internal fun PostAlertPills(
    moderation: ModerationUI,
    moderationOpts: ModerationOpts,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    if (!moderation.alert && !moderation.inform) return
    val palette = LocalAlfPalette.current
    val color = palette.contrast700.toComposeColor()
    val shape = CircleShape
    val iconSize = if (expanded) 16.dp else 12.dp
    val padding = if (expanded) 5.dp else 3.dp
    val causes = (moderation.alerts + moderation.informs).distinctBy { cause ->
        if (cause is ModerationCause.LabelCause) cause.label.src to cause.label.`val` else cause.type to ""
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(padding),
        verticalArrangement = Arrangement.spacedBy(padding),
    ) {
        for (cause in causes) {
            val label = cause as? ModerationCause.LabelCause
            val creator = label?.labelDef?.definedBy?.let(moderationOpts.labelerProfiles::get)
            val title = postModerationCauseTitle(cause)
            val description = label?.labelDef?.description
            var showDetails by remember(cause) { mutableStateOf(false) }

            val surface = if (expanded) Modifier.clip(shape).background(palette.contrast25.toComposeColor()) else Modifier
            val interaction = if (description.isNullOrBlank() && creator == null) Modifier else {
                Modifier.clickable(role = Role.Button) { showDetails = true }
            }
            Row(
                modifier = surface.then(interaction).padding(start = padding, end = padding + 3.dp, top = padding, bottom = padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(padding),
            ) {
                if (creator?.avatar != null) {
                    CaelumAvatar(url = creator.avatar, contentDescription = null, size = iconSize)
                } else {
                    Icon(
                        imageVector = if (label?.labelDef?.severity == "alert") Icons.Filled.Warning else Icons.Filled.Info,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(iconSize),
                    )
                }
                Text(
                    title,
                    color = color,
                    style = if (expanded) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (showDetails) {
                AlertDialog(
                    onDismissRequest = { showDetails = false },
                    title = { Text(title) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            if (creator != null) {
                                val source = creator.displayName?.takeIf { it.isNotBlank() } ?: "@${creator.handle}"
                                Text(tr("Labeled by {0}.", mapOf("0" to source)))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDetails = false }) { Text(tr("Done")) }
                    },
                )
            }
        }
    }
}

@Composable
internal fun postModerationCauseTitle(cause: ModerationCause?): String = when (cause) {
    is ModerationCause.Blocking -> tr("Blocked account")
    is ModerationCause.BlockedBy -> tr("Author blocked you")
    is ModerationCause.BlockOther -> tr("Blocked thread interaction")
    is ModerationCause.Muted -> tr("Muted account")
    is ModerationCause.MuteWord -> tr("Muted word: {word}", mapOf("word" to cause.matches.joinToString(", ")))
    is ModerationCause.Hidden -> tr("Post Hidden by You")
    is ModerationCause.LabelCause -> cause.labelDef.title ?: cause.label.`val`
    null -> tr("Content hidden")
}
