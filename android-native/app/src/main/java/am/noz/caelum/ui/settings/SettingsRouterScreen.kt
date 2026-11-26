package am.noz.caelum.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsRouterScreen(
    route: AppRoute,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onAddAccount: () -> Unit,
) {
    when (route) {
        AppRoute.Settings -> SettingsMainScreen(
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onAddAccount = onAddAccount,
        )
        AppRoute.AppearanceSettings -> AppearanceSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.AppIconSettings -> AppIconSettingsScreen(onBack = onBack)
        AppRoute.InfrastructureSettings -> InfrastructureSettingsScreen(onBack = onBack)
        AppRoute.CustomizationSettings -> CustomizationSettingsScreen(onBack = onBack)
        AppRoute.EmojiCredits -> EmojiCreditsScreen(onBack = onBack)
        AppRoute.AccountSettings -> AccountSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.PrivacyAndSecuritySettings -> PrivacyAndSecuritySettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.ActivityPrivacySettings -> ActivityPrivacySettingsScreen(onBack = onBack)
        AppRoute.AppPasswords -> AppPasswordsScreen(onBack = onBack)
        AppRoute.NotificationSettings -> NotificationSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.ActivityNotificationSettings -> ActivityNotificationSettingsScreen(onBack = onBack)
        AppRoute.ContentAndMediaSettings -> ContentAndMediaSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.SavedFeeds -> SavedFeedsSettingsScreen(onBack = onBack)
        AppRoute.PreferencesFollowingFeed -> PreferencesFollowingFeedScreen(onBack = onBack)
        AppRoute.PreferencesThreads -> PreferencesThreadsScreen(onBack = onBack)
        AppRoute.PreferencesExternalEmbeds -> PreferencesExternalEmbedsScreen(onBack = onBack)
        AppRoute.AccessibilitySettings -> AccessibilitySettingsScreen(onBack = onBack)
        AppRoute.LanguageSettings -> LanguageSettingsScreen(onBack = onBack)
        AppRoute.InterestsSettings -> InterestsSettingsScreen(onBack = onBack)
        AppRoute.AutomationLabelSettings -> AutomationLabelSettingsScreen(onBack = onBack)
        AppRoute.BetaFeaturesSettings -> BetaFeaturesSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.FindContactsSettings -> FindContactsSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.FindContactsFlow -> FindContactsFlowScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.AboutSettings -> AboutSettingsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.Log -> LogSettingsScreen(onBack = onBack)
        AppRoute.DebugMod -> DebugModSettingsScreen(onBack = onBack)
        AppRoute.Support -> SupportScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.PrivacyPolicy -> LegalPolicyScreen(
            title = "Privacy Policy",
            summary = "Review how Bluesky and Caelum handle account data, AT Protocol public records, and push notification tokens.",
            url = "https://bsky.social/about/support/privacy-policy",
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.TermsOfService -> LegalPolicyScreen(
            title = "Terms of Service",
            summary = "Review the Bluesky Social Terms of Service governing accounts hosted on bsky.social.",
            url = "https://bsky.social/about/support/tos",
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.CommunityGuidelines -> LegalPolicyScreen(
            title = "Community Guidelines",
            summary = "Review the Bluesky Community Guidelines for respectful participation across the Atmosphere.",
            url = "https://bsky.social/about/support/community-guidelines",
            onBack = onBack,
            onNavigate = onNavigate,
        )
        AppRoute.CopyrightPolicy -> LegalPolicyScreen(
            title = "Copyright Policy",
            summary = "Review copyright reporting procedures and DMCA policies.",
            url = "https://bsky.social/about/support/copyright",
            onBack = onBack,
            onNavigate = onNavigate,
        )
        else -> SettingsMainScreen(
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onAddAccount = onAddAccount,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val currentAccount = sessionState.currentAccount

    var showSignOutDialog by remember { mutableStateOf(false) }

    val bg = palette.contrast0.toComposeColor()
    val textColor = palette.contrast1000.toComposeColor()
    val dividerColor = palette.contrast100.toComposeColor()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textColor,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open navigation drawer",
                                tint = textColor,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bg,
                        titleContentColor = textColor,
                    ),
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            // Account Card + Account Switcher Trigger
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.contrast25.toComposeColor())
                        .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(16.dp))
                        .clickable {
                            if (currentAccount != null) onOpenAccountSwitcher() else onAddAccount()
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentAccount != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CaelumAvatar(
                                url = currentAccount.avatar,
                                contentDescription = currentAccount.handle,
                                size = 56.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentAccount.displayName?.takeIf { it.isNotBlank() }
                                        ?: currentAccount.handle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.contrast1000.toComposeColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${currentAccount.handle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val pdsHost = LinkNavigationHelper.extractHost(
                                    currentAccount.pdsUrl ?: currentAccount.serviceUrl
                                )
                                if (!pdsHost.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = palette.primary50.toComposeColor(),
                                    ) {
                                        Text(
                                            text = pdsHost,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.primary600.toComposeColor(),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onOpenAccountSwitcher,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (sessionState.accounts.size > 1) {
                                        "Switch (${sessionState.accounts.size})"
                                    } else {
                                        "Switch account"
                                    },
                                    maxLines = 1,
                                )
                            }
                            OutlinedButton(
                                onClick = onAddAccount,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add account", maxLines = 1)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CaelumAvatar(url = null, contentDescription = "Guest", size = 48.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Not signed in",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.contrast1000.toComposeColor(),
                                )
                                Text(
                                    text = "Sign in or create an account to customize your experience.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onAddAccount,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sign in or add account")
                        }
                    }
                }
            }

            // Invite & Saved Feeds
            item {
                SettingsListItem(
                    title = "Invite a friend",
                    subtitle = "Share Caelum & Bluesky or scan an invite code",
                    icon = Icons.Filled.CardGiftcard,
                    onClick = {
                        val handle = currentAccount?.handle
                        val inviteUrl = if (!handle.isNullOrBlank()) {
                            "https://bsky.app/profile/$handle"
                        } else {
                            "https://bsky.app"
                        }
                        LinkNavigationHelper.shareText(
                            context = context,
                            text = "Join me on Bluesky with Caelum: $inviteUrl",
                            subject = "Join me on Caelum",
                        )
                    },
                )
                SettingsListItem(
                    title = "Saved feeds",
                    subtitle = "Manage and reorder your pinned feeds",
                    icon = Icons.Filled.RssFeed,
                    onClick = { onNavigate(AppRoute.SavedFeeds) },
                )
            }

            item { SettingsDivider() }

            // Core Account & Social Settings
            item {
                SettingsListItem(
                    title = "Account",
                    icon = Icons.Filled.Person,
                    onClick = { onNavigate(AppRoute.AccountSettings) },
                )
                SettingsListItem(
                    title = "Privacy and security",
                    icon = Icons.Filled.Lock,
                    onClick = { onNavigate(AppRoute.PrivacyAndSecuritySettings) },
                )
                SettingsListItem(
                    title = "Moderation",
                    icon = Icons.Filled.Shield,
                    onClick = { onNavigate(AppRoute.Moderation) },
                )
                SettingsListItem(
                    title = "Notifications",
                    icon = Icons.Filled.Notifications,
                    onClick = { onNavigate(AppRoute.NotificationSettings) },
                )
                SettingsListItem(
                    title = "Content and media",
                    icon = Icons.Filled.PermMedia,
                    onClick = { onNavigate(AppRoute.ContentAndMediaSettings) },
                )
                SettingsListItem(
                    title = "Find and invite friends",
                    icon = Icons.Filled.Contacts,
                    onClick = { onNavigate(AppRoute.FindContactsSettings) },
                )
            }

            item { SettingsDivider() }

            // Appearance & Caelum Features
            item {
                SettingsSectionHeader("Appearance & Caelum")
                SettingsListItem(
                    title = "Appearance",
                    subtitle = settings.witchskySelectedTheme?.let { "${it.name} (${it.variantName})" }
                        ?: "Bluesky Default",
                    icon = Icons.Filled.Palette,
                    onClick = { onNavigate(AppRoute.AppearanceSettings) },
                )
                SettingsListItem(
                    title = "App icon",
                    subtitle = "Choose from 12 custom launcher icons",
                    icon = Icons.Filled.Apps,
                    onClick = { onNavigate(AppRoute.AppIconSettings) },
                )
                SettingsListItem(
                    title = "Caelum customization",
                    subtitle = "Auto-like reposts, searchable saved posts, in-app links",
                    icon = Icons.Filled.Tune,
                    onClick = { onNavigate(AppRoute.CustomizationSettings) },
                )
                SettingsListItem(
                    title = "Caelum infrastructure",
                    subtitle = settings.infrastructure.appviewHostname.ifBlank { "api.bsky.app (default)" },
                    icon = Icons.Filled.Cloud,
                    onClick = { onNavigate(AppRoute.InfrastructureSettings) },
                )
            }

            item { SettingsDivider() }

            // Accessibility, Languages, Support & About
            item {
                SettingsListItem(
                    title = "Accessibility",
                    icon = Icons.Filled.AccessibilityNew,
                    onClick = { onNavigate(AppRoute.AccessibilitySettings) },
                )
                SettingsListItem(
                    title = "Languages",
                    icon = Icons.Filled.Language,
                    onClick = { onNavigate(AppRoute.LanguageSettings) },
                )
                SettingsListItem(
                    title = "Interests",
                    icon = Icons.Filled.Interests,
                    onClick = { onNavigate(AppRoute.InterestsSettings) },
                )
                SettingsListItem(
                    title = "Beta features",
                    icon = Icons.Filled.Science,
                    onClick = { onNavigate(AppRoute.BetaFeaturesSettings) },
                )
                SettingsListItem(
                    title = "Help & support",
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    onClick = { onNavigate(AppRoute.Support) },
                )
                SettingsListItem(
                    title = "About",
                    icon = Icons.Filled.Info,
                    onClick = { onNavigate(AppRoute.AboutSettings) },
                )
                SettingsListItem(
                    title = "Emoji credits",
                    icon = Icons.Filled.EmojiEmotions,
                    onClick = { onNavigate(AppRoute.EmojiCredits) },
                )
            }

            if (currentAccount != null) {
                item { SettingsDivider() }
                item {
                    SettingsListItem(
                        title = "Sign out",
                        subtitle = if (sessionState.accounts.size > 1) {
                            "Signed in to ${sessionState.accounts.size} accounts"
                        } else {
                            "@${currentAccount.handle}"
                        },
                        icon = Icons.AutoMirrored.Filled.Logout,
                        destructive = true,
                        showChevron = false,
                        onClick = { showSignOutDialog = true },
                    )
                }
            }
        }
    }

    if (showSignOutDialog && currentAccount != null) {
        val multiAccount = sessionState.accounts.size > 1
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(if (multiAccount) "Sign out?" else "Sign out of @${currentAccount.handle}?") },
            text = {
                Text(
                    if (multiAccount) {
                        "Sign out of only @${currentAccount.handle}, or sign out of all ${sessionState.accounts.size} accounts on this device?"
                    } else {
                        "You will be signed out of your account on this device."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        container.applicationScope.launch(Dispatchers.Main.immediate) {
                            container.sessionManager.signOutAll()
                            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(
                        text = if (multiAccount) "Sign out all" else "Sign out",
                        color = palette.negative500.toComposeColor(),
                    )
                }
            },
            dismissButton = {
                Row {
                    if (multiAccount) {
                        TextButton(
                            onClick = {
                                showSignOutDialog = false
                                container.applicationScope.launch(Dispatchers.Main.immediate) {
                                    container.sessionManager.removeAccount(currentAccount.did)
                                    Toast.makeText(context, "Signed out @${currentAccount.handle}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            Text("Only @${currentAccount.handle}")
                        }
                    }
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}
