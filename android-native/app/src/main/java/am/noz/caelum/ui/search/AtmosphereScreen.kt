package am.noz.caelum.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.R
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage

private enum class AtmosphereCategory(val label: String) {
    ALL("All"),
    DATA_IDENTITY("Data & Identity"),
    MEDIA_LIVE("Media & Live"),
    WRITING_FORUMS("Writing & Events"),
    CLIENTS_THEMES("Clients & Tools"),
}

private data class AtmosphereAppEntry(
    val id: String,
    val name: String,
    val domain: String,
    val url: String,
    val description: String,
    val category: AtmosphereCategory,
    val icon: ImageVector,
    @DrawableRes val iconRes: Int? = null,
)

private val ATMOSPHERE_APPS = listOf(
    AtmosphereAppEntry(
        id = "pdsls",
        name = "pds.ls",
        domain = "pds.ls",
        url = "https://pds.ls",
        description = "Inspect raw ATProto repositories, records, blobs, and lexicons across any PDS.",
        category = AtmosphereCategory.DATA_IDENTITY,
        icon = Icons.Filled.Storage,
        iconRes = R.drawable.pdsls_favicon,
    ),
    AtmosphereAppEntry(
        id = "tangled",
        name = "Tangled",
        domain = "tangled.org",
        url = "https://tangled.org",
        description = "Social Git hosting and collaborative code repositories built natively on ATProto.",
        category = AtmosphereCategory.CLIENTS_THEMES,
        icon = Icons.Filled.Code,
        iconRes = R.drawable.tangled_favicon,
    ),
    AtmosphereAppEntry(
        id = "grain",
        name = "Grain",
        domain = "grain.social",
        url = "https://grain.social",
        description = "Curated photography galleries, albums, and visual storytelling on ATProto.",
        category = AtmosphereCategory.MEDIA_LIVE,
        icon = Icons.Filled.PhotoLibrary,
        iconRes = R.drawable.grain_favicon,
    ),
    AtmosphereAppEntry(
        id = "sifa",
        name = "sifa.id",
        domain = "sifa.id",
        url = "https://sifa.id",
        description = "Decentralized professional identity, work history, and verifiable credentials.",
        category = AtmosphereCategory.DATA_IDENTITY,
        icon = Icons.Filled.AlternateEmail,
        iconRes = R.drawable.sifa_favicon,
    ),
    AtmosphereAppEntry(
        id = "streamplace",
        name = "Streamplace",
        domain = "stream.place",
        url = "https://stream.place",
        description = "Open live video streaming integrated directly with your ATProto handle and social graph.",
        category = AtmosphereCategory.MEDIA_LIVE,
        icon = Icons.Filled.LiveTv,
        iconRes = R.drawable.streamplace_favicon,
    ),
    AtmosphereAppEntry(
        id = "smokesignal",
        name = "Smoke Signal",
        domain = "smokesignal.events",
        url = "https://smokesignal.events",
        description = "Decentralized events, RSVPs, and community meetups powered by ATProto records.",
        category = AtmosphereCategory.WRITING_FORUMS,
        icon = Icons.Filled.Event,
    ),
    AtmosphereAppEntry(
        id = "frontpage",
        name = "Frontpage",
        domain = "frontpage.fyi",
        url = "https://frontpage.fyi",
        description = "Link aggregator and community discussions where every submission and vote lives in your repo.",
        category = AtmosphereCategory.WRITING_FORUMS,
        icon = Icons.Filled.Forum,
    ),
    AtmosphereAppEntry(
        id = "whitewind",
        name = "WhiteWind",
        domain = "whtwnd.com",
        url = "https://whtwnd.com",
        description = "Long-form Markdown blogging platform publishing directly to your Personal Data Server.",
        category = AtmosphereCategory.WRITING_FORUMS,
        icon = Icons.Filled.Article,
    ),
    AtmosphereAppEntry(
        id = "bluecast",
        name = "Bluecast",
        domain = "bluecast.app",
        url = "https://bluecast.app",
        description = "Live audio rooms and social podcasting spaces for the Bluesky and ATProto community.",
        category = AtmosphereCategory.MEDIA_LIVE,
        icon = Icons.Filled.Podcasts,
    ),
    AtmosphereAppEntry(
        id = "witchsky",
        name = "Witchsky",
        domain = "witchsky.app",
        url = "https://witchsky.app",
        description = "Custom ATProto social client and portable color theme ecosystem compatible with Caelum.",
        category = AtmosphereCategory.CLIENTS_THEMES,
        icon = Icons.Filled.Palette,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtmosphereScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val context = LocalContext.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())

    var selectedCategory by rememberSaveable { mutableStateOf(AtmosphereCategory.ALL) }
    val filteredApps = remember(selectedCategory) {
        if (selectedCategory == AtmosphereCategory.ALL) {
            ATMOSPHERE_APPS
        } else {
            ATMOSPHERE_APPS.filter { it.category == selectedCategory }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.contrast0.toComposeColor(),
                    ),
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = tr("Open drawer menu"),
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = palette.primary500.toComposeColor(),
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = tr("Atmosphere"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    },
                )
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 84.dp),
        ) {
            item(key = "atmosphere_intro") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.primary50.toComposeColor())
                        .border(1.dp, palette.primary100.toComposeColor(), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = palette.primary500.toComposeColor(),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = tr("Explore the ATProto Ecosystem"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.contrast1000.toComposeColor(),
                        )
                    }
                    Text(
                        text = tr("Apps across the Atmosphere share your decentralized identity, handle, and social graph."),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast700.toComposeColor(),
                    )
                }
            }

            item(key = "atmosphere_categories") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AtmosphereCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(tr(category.label)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.primary500.toComposeColor(),
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            items(
                items = filteredApps,
                key = { it.id },
            ) { app ->
                AtmosphereAppCard(
                    app = app,
                    onClick = {
                        LinkNavigationHelper.openUrl(
                            context = context,
                            rawUrl = app.url,
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onInternalRoute = onNavigate,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AtmosphereAppCard(
    app: AtmosphereAppEntry,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.contrast25.toComposeColor())
            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.contrast50.toComposeColor())
                    .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (app.iconRes != null) {
                    AsyncImage(
                        model = app.iconRes,
                        fallback = painterResource(app.iconRes),
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Icon(
                        imageVector = app.icon,
                        contentDescription = app.name,
                        tint = palette.primary500.toComposeColor(),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = app.domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary500.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = tr(app.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast700.toComposeColor(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = tr("Open ${app.name}"),
                tint = palette.contrast500.toComposeColor(),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
