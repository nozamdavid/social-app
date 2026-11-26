package am.noz.caelum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.VerificationState
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor

internal val LocalHideVerificationBadges = compositionLocalOf { false }

/**
 * Author name, verification badge, optional handle/pronouns, and timestamp header row.
 */
@Composable
fun DisplayNameRow(
    profile: ProfileViewBasic,
    timestamp: String? = null,
    showHandle: Boolean = true,
    showPronouns: Boolean = false,
    modifier: Modifier = Modifier,
    nameStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    nameMaxLines: Int = 1,
    badgeSize: Dp = 14.dp,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: "@${profile.handle}"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Reserve the timestamp first, then let the handle use what the name and badges leave.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = nameStyle,
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = nameMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ProfileBadges(profile, badgeSize)
            }

            if (showPronouns && !profile.pronouns.isNullOrBlank()) {
                Text(
                    text = profile.pronouns,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier
                        .background(palette.contrast50.toComposeColor(), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showHandle && profile.handle != "handle.invalid") {
                Text(
                    text = "@${profile.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        if (!timestamp.isNullOrBlank()) {
            Text(
                text = "· $timestamp",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun ProfileBadges(profile: ProfileViewBasic, size: Dp) {
    VerificationBadge(profile.verification, size = size)
    if (profile.labels.any { it.src == profile.did && it.`val` == "bot" && it.neg != true }) {
        Icon(
            imageVector = BotBadgeIcon,
            contentDescription = tr("Automated account"),
            tint = LocalAlfPalette.current.contrast700.toComposeColor(),
            modifier = Modifier.size(size + 1.dp),
        )
    }
}

@Composable
fun VerificationBadge(
    verification: VerificationState?,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    if (verification == null || LocalHideVerificationBadges.current) return
    val verifierStatus = verification.trustedVerifierStatus
    if (verifierStatus != "valid" && verification.verifiedStatus != "valid") return

    val isVerifier = verifierStatus == "valid" || verifierStatus == "invalid"
    Box(modifier = modifier.size(size)) {
        Icon(
            imageVector = if (isVerifier) VerifierSeal else VerifiedSeal,
            contentDescription = if (isVerifier) tr("Trusted verifier") else tr("Verified account"),
            tint = LocalAlfPalette.current.primary500.toComposeColor(),
            modifier = Modifier.matchParentSize(),
        )
        Icon(
            imageVector = if (isVerifier) VerifierTick else VerifiedTick,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private val VerifiedSeal = bskyVector(
    "Verified account seal",
    "M23.5 12a11.5 11.5 0 1 1-23 0 11.5 11.5 0 1 1 23 0Z",
)
private val VerifiedTick = bskyVector(
    "Verified account check",
    "M17.659 8.175a1.36 1.36 0 0 1 0 1.925l-6.224 6.223a1.36 1.36 0 0 1-1.925 0L6.4 13.212a1.361 1.361 0 0 1 1.925-1.925l2.149 2.148 5.26-5.26a1.36 1.36 0 0 1 1.925 0Z",
    pathFillType = PathFillType.EvenOdd,
)
private val VerifierSeal = bskyVector(
    "Trusted verifier seal",
    "M8.792 1.615a4.154 4.154 0 0 1 6.416 0 4.15 4.15 0 0 0 3.146 1.515 4.154 4.154 0 0 1 4 5.017 4.15 4.15 0 0 0 .777 3.404 4.154 4.154 0 0 1-1.427 6.255 4.15 4.15 0 0 0-2.177 2.73 4.154 4.154 0 0 1-5.781 2.784 4.15 4.15 0 0 0-3.492 0 4.154 4.154 0 0 1-5.78-2.784 4.15 4.15 0 0 0-2.178-2.73A4.154 4.154 0 0 1 .87 11.551a4.15 4.15 0 0 0 .776-3.404 4.154 4.154 0 0 1 4-5.017 4.15 4.15 0 0 0 3.146-1.515Z",
    pathFillType = PathFillType.EvenOdd,
)
private val VerifierTick = bskyVector(
    "Trusted verifier check",
    "M17.861 8.26a1.44 1.44 0 0 1 0 2.033l-6.571 6.571a1.437 1.437 0 0 1-2.033 0L5.97 13.58a1.438 1.438 0 0 1 2.033-2.033l2.27 2.269 5.554-5.555a1.437 1.437 0 0 1 2.033 0Z",
    pathFillType = PathFillType.EvenOdd,
)
private val BotBadgeIcon = bskyVector(
    "Automated account",
    "M12 0a2 2 0 0 1 1 3.73V5h4.2c1.68 0 2.52 0 3.162.327a3 3 0 0 1 1.31 1.31C22 7.28 22 8.12 22 9.8v.25a2.501 2.501 0 0 1 0 4.9V15c0 2.8 0 4.2-.545 5.27a5 5 0 0 1-2.185 2.185C18.2 23 16.8 23 14 23h-4c-2.8 0-4.2 0-5.27-.545a5 5 0 0 1-2.185-2.185C2 19.2 2 17.8 2 15v-.05a2.5 2.5 0 0 1 0-4.9V9.8c0-1.68 0-2.52.327-3.162a3 3 0 0 1 1.31-1.31C4.28 5 5.12 5 6.8 5H11V3.73A2 2 0 0 1 12 0M8 10a2 2 0 0 0-2 2v2a2 2 0 1 0 4 0v-2a2 2 0 0 0-2-2m8 0a2 2 0 0 0-2 2v2a2 2 0 1 0 4 0v-2a2 2 0 0 0-2-2",
)
