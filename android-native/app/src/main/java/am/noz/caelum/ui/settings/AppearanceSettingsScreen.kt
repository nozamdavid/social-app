package am.noz.caelum.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import am.noz.caelum.R
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.AlfPalette
import am.noz.caelum.ui.theme.BUNDLED_CAELUM_THEME_URI
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.ThemeEngine
import am.noz.caelum.ui.theme.ThemeOption
import am.noz.caelum.ui.theme.WITCHSKY_THEME_COLLECTION
import am.noz.caelum.ui.theme.alfPaletteToMaterialColorScheme
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val sessionState by container.sessionManager.state.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var showAddThemesDialog by remember { mutableStateOf(false) }
    var previewGroup by remember { mutableStateOf<List<ThemeOption>?>(null) }
    var applyingThemeLabel by remember { mutableStateOf<String?>(null) }
    var pdsLoading by remember { mutableStateOf(false) }
    var pdsError by remember { mutableStateOf<String?>(null) }

    val selectedTheme = settings.witchskySelectedTheme

    fun applyThemeWithOverlay(target: ThemeOption?, label: String) {
        scope.launch {
            haptics.confirm()
            applyingThemeLabel = label
            delay(180)
            container.preferences.setWitchskySelectedTheme(target)
            delay(220)
            applyingThemeLabel = null
        }
    }

    suspend fun syncAccountThemesFromPds() {
        val account = sessionState.currentAccount ?: return
        pdsLoading = true
        pdsError = null
        runCatching {
            val pds = account.pdsUrl?.takeIf { it.isNotBlank() }
                ?: container.identityResolver.resolvePdsUrl(account.did)
                ?: account.serviceUrl
            val fetched = mutableListOf<ThemeOption>()
            var cursor: String? = null
            do {
                val page = container.api.listPdsRecords(
                    pdsOrigin = pds,
                    repo = account.did,
                    collection = WITCHSKY_THEME_COLLECTION,
                    limit = 50,
                    cursor = cursor,
                )
                for (rec in page.records) {
                    fetched.addAll(ThemeEngine.parseThemeRecord(rec.uri, rec.value))
                }
                cursor = page.cursor
            } while (!cursor.isNullOrBlank())

            val merged = (settings.witchskySavedThemes.filter { old ->
                !old.uri.startsWith("at://${account.did}/")
            } + fetched).distinctBy { it.id }
            container.preferences.setWitchskySavedThemes(merged)
        }.onFailure { err ->
            pdsError = err.message ?: "Failed to load saved themes from PDS"
        }
        pdsLoading = false
    }

    LaunchedEffect(sessionState.currentDid) {
        if (sessionState.currentDid != null) {
            syncAccountThemesFromPds()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScaffold(
            title = "Appearance",
            onBack = onBack,
            testTag = "appearance_settings_screen",
        ) { paddingModifier ->
            LazyColumn(
                modifier = paddingModifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // 1. Color mode & Dark variant (for Bluesky Default)
                item {
                    SettingsSectionHeader("Color Mode")
                    val modeOptions = listOf(
                        "system" to "System",
                        "light" to "Light",
                        "dark" to "Dark",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (selectedTheme != null) {
                            SettingsInfoCard(
                                body = "Active custom theme (${selectedTheme.name} - ${selectedTheme.variantName}) controls the light/dark palette. Switch to Bluesky Default to use Color Mode & Material You.",
                                icon = Icons.Filled.Palette,
                            )
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            modeOptions.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, modeOptions.size),
                                    selected = settings.colorMode == value,
                                    enabled = selectedTheme == null,
                                    onClick = {
                                        haptics.selection()
                                        scope.launch { container.preferences.setColorMode(value) }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (settings.colorMode != "light" && selectedTheme == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Dark Theme Variant",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.contrast700.toComposeColor(),
                            )
                            val darkOptions = listOf("dim" to "Dim", "dark" to "Dark")
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                darkOptions.forEachIndexed { index, (value, label) ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index, darkOptions.size),
                                        selected = settings.darkTheme == value,
                                        onClick = {
                                            haptics.selection()
                                            scope.launch { container.preferences.setDarkTheme(value) }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)); SettingsDivider() }

                // 2. Subordinate Material You Dynamic Color toggle
                item {
                    SettingsSectionHeader("Dynamic Color")
                    val android12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val subtitle = when {
                        !android12Plus -> "Requires Android 12 or newer."
                        selectedTheme != null ->
                            "Subordinate to active Witchsky/Caelum theme. Switch to Bluesky Default above for wallpaper-based Material You colors."
                        else ->
                            "Use Android 12+ Material You wallpaper colors when Bluesky Default is active. Custom Witchsky/Caelum themes always take precedence."
                    }
                    SettingsSwitchItem(
                        title = "Material You dynamic color",
                        subtitle = subtitle,
                        icon = Icons.Filled.AutoAwesome,
                        checked = settings.dynamicColorEnabled,
                        enabled = android12Plus,
                        haptics = haptics,
                        onCheckedChange = { enabled ->
                            scope.launch { container.preferences.setDynamicColorEnabled(enabled) }
                        },
                    )
                }

                item { SettingsDivider() }

                // 3. Theme Library (Bluesky Default + Caelum Built-in + Saved Witchsky Themes)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theme Library",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary500.toComposeColor(),
                            )
                            Text(
                                text = "Caelum themes & Witchsky ATProto color palettes",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (sessionState.hasSession) {
                                IconButton(
                                    onClick = { scope.launch { syncAccountThemesFromPds() } },
                                    enabled = !pdsLoading,
                                ) {
                                    if (pdsLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh saved themes")
                                    }
                                }
                            }
                            Button(
                                onClick = { showAddThemesDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("add_themes_button"),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add themes")
                            }
                        }
                    }
                }

                if (!pdsError.isNullOrBlank()) {
                    item {
                        SettingsInfoCard(
                            body = pdsError.orEmpty(),
                            isError = true,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }

                // Bluesky Default card
                item {
                    val defaultPalette = when {
                        settings.colorMode == "light" -> ThemeEngine.STOCK_LIGHT
                        settings.darkTheme == "dark" -> ThemeEngine.STOCK_DARK
                        else -> ThemeEngine.STOCK_DIM
                    }
                    ThemeLibraryCard(
                        name = "Bluesky Default",
                        variant = when (settings.colorMode) {
                            "light" -> "Light"
                            "dark" -> if (settings.darkTheme == "dark") "Dark" else "Dim"
                            else -> "Follows System (${if (settings.darkTheme == "dark") "Dark" else "Dim"})"
                        },
                        authorLabel = "By Bluesky",
                        swatches = listOf(
                            defaultPalette.contrast0,
                            defaultPalette.contrast25,
                            defaultPalette.primary500,
                            defaultPalette.contrast1000,
                        ),
                        isSelected = selectedTheme == null,
                        canDelete = false,
                        onApply = { applyThemeWithOverlay(null, "Bluesky Default") },
                        onPreview = null,
                        onShare = null,
                        onDelete = null,
                    )
                }

                // Caelum Built-in Themes (Caelum Light / Dim / Dark)
                items(
                    count = ThemeEngine.bundledThemes.size,
                    key = { idx -> ThemeEngine.bundledThemes[idx].id },
                ) { idx ->
                    val option = ThemeEngine.bundledThemes[idx]
                    val isSelected = selectedTheme?.id == option.id
                    ThemeLibraryCard(
                        name = "${option.name} ${option.variantName}",
                        variant = if (option.mode == "light") "Light mode" else "Dark mode",
                        authorLabel = "By Caelum (Built-in)",
                        swatches = listOf(
                            option.colors.canvas,
                            option.colors.surface,
                            option.colors.accent,
                            option.colors.text,
                        ),
                        isSelected = isSelected,
                        canDelete = false,
                        onApply = { applyThemeWithOverlay(option, "${option.name} ${option.variantName}") },
                        onPreview = { previewGroup = listOf(option) },
                        onShare = {
                            val json = AtProtoJson.encodeToString(option)
                            LinkNavigationHelper.shareText(context, json, "${option.name} ${option.variantName} Theme")
                        },
                        onDelete = null,
                    )
                }

                // Saved / Imported Witchsky Themes
                val savedThemes = settings.witchskySavedThemes
                if (savedThemes.isEmpty()) {
                    item {
                        Text(
                            text = "No imported Witchsky themes yet. Tap “Add themes” to browse palettes from any handle or DID (e.g. witchsky.app).",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast600.toComposeColor(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    items(
                        count = savedThemes.size,
                        key = { idx -> savedThemes[idx].id },
                    ) { idx ->
                        val option = savedThemes[idx]
                        val ownerDid = option.uri.removePrefix("at://").substringBefore('/')
                        val authorLabel = if (ownerDid == sessionState.currentDid) {
                            "Saved to your PDS"
                        } else {
                            "Author: ${ownerDid.take(22)}${if (ownerDid.length > 22) "..." else ""}"
                        }
                        val groupVariants = savedThemes.filter { it.uri == option.uri }
                        ThemeLibraryCard(
                            name = option.name,
                            variant = "${option.variantName} (${option.mode})",
                            authorLabel = authorLabel,
                            swatches = listOf(
                                option.colors.canvas,
                                option.colors.surface,
                                option.colors.accent,
                                option.colors.text,
                            ),
                            isSelected = selectedTheme?.id == option.id,
                            canDelete = true,
                            onApply = { applyThemeWithOverlay(option, option.name) },
                            onPreview = {
                                previewGroup = if (groupVariants.isNotEmpty()) groupVariants else listOf(option)
                            },
                            onShare = {
                                val json = AtProtoJson.encodeToString(option)
                                LinkNavigationHelper.shareText(context, json, "${option.name} Theme")
                            },
                            onDelete = {
                                scope.launch {
                                    val updated = settings.witchskySavedThemes.filterNot { it.id == option.id }
                                    container.preferences.setWitchskySavedThemes(updated)
                                    if (selectedTheme?.id == option.id) {
                                        container.preferences.setWitchskySelectedTheme(null)
                                    }
                                    Toast.makeText(context, "Removed ${option.name}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)); SettingsDivider() }

                // 4. Typography (Font Family + Font Size)
                item {
                    SettingsSectionHeader("Typography")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FontDownload,
                                contentDescription = null,
                                tint = palette.contrast700.toComposeColor(),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Font Family",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.contrast1000.toComposeColor(),
                            )
                        }
                        val fontOptions = listOf("theme" to "Theme (Inter)", "system" to "System")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            fontOptions.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, fontOptions.size),
                                    selected = settings.fontFamily == value,
                                    onClick = {
                                        haptics.selection()
                                        scope.launch { container.preferences.setFontFamily(value) }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatSize,
                                contentDescription = null,
                                tint = palette.contrast700.toComposeColor(),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = palette.contrast1000.toComposeColor(),
                            )
                        }
                        val sizeOptions = listOf("-1" to "Smaller", "0" to "Default", "1" to "Larger")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            sizeOptions.forEachIndexed { index, (value, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index, sizeOptions.size),
                                    selected = settings.fontScale == value,
                                    onClick = {
                                        haptics.selection()
                                        scope.launch { container.preferences.setFontScale(value) }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)); SettingsDivider() }

                // 5. App Icon entry
                item {
                    SettingsListItem(
                        title = "App Icon",
                        subtitle = "Choose from 12 custom Caelum launcher icons",
                        icon = Icons.Filled.Apps,
                        badge = settings.appIconId,
                        onClick = { onNavigate(AppRoute.AppIconSettings) },
                    )
                }
            }
        }

        // Add Themes PDS Importer Dialog
        if (showAddThemesDialog) {
            AddThemesPdsDialog(
                savedThemes = settings.witchskySavedThemes,
                onPreview = { options -> previewGroup = options },
                onSaveAndApply = { option, allInGroup, applyNow ->
                    scope.launch {
                        val merged = (settings.witchskySavedThemes + allInGroup).distinctBy { it.id }
                        container.preferences.setWitchskySavedThemes(merged)
                        if (applyNow) {
                            showAddThemesDialog = false
                            applyThemeWithOverlay(option, option.name)
                        } else {
                            Toast.makeText(context, "Added ${option.name} to Theme Library", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showAddThemesDialog = false },
            )
        }

        // Live Sample Post Preview Dialog
        previewGroup?.let { options ->
            ThemePreviewDialog(
                options = options,
                onApply = { chosen ->
                    previewGroup = null
                    val merged = if (chosen.uri != BUNDLED_CAELUM_THEME_URI) {
                        (settings.witchskySavedThemes + options).distinctBy { it.id }
                    } else {
                        null
                    }
                    scope.launch {
                        if (merged != null) {
                            container.preferences.setWitchskySavedThemes(merged)
                        }
                        applyThemeWithOverlay(chosen, "${chosen.name} ${chosen.variantName}")
                    }
                },
                onDismiss = { previewGroup = null },
            )
        }

        // Smooth ApplyingThemeOverlay
        ApplyingThemeOverlay(
            visible = applyingThemeLabel != null,
            themeLabel = applyingThemeLabel.orEmpty(),
        )
    }
}

@Composable
private fun ThemeLibraryCard(
    name: String,
    variant: String,
    authorLabel: String,
    swatches: List<String>,
    isSelected: Boolean,
    canDelete: Boolean,
    onApply: () -> Unit,
    onPreview: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val palette = LocalAlfPalette.current
    val borderColor = if (isSelected) palette.primary500.toComposeColor() else palette.contrast100.toComposeColor()
    val cardBg = if (isSelected) palette.primary50.toComposeColor().copy(alpha = 0.35f) else palette.contrast25.toComposeColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onApply)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                ThemeSwatchCluster(swatches = swatches)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.contrast1000.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Active",
                                tint = palette.primary500.toComposeColor(),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = "$variant · $authorLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelected) {
                OutlinedButton(
                    onClick = onApply,
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Applied")
                }
            } else {
                Button(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Apply")
                }
            }
            if (onPreview != null) {
                OutlinedButton(
                    onClick = onPreview,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview")
                }
            }
            Spacer(Modifier.weight(1f))
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share theme JSON",
                        tint = palette.contrast700.toComposeColor(),
                    )
                }
            }
            if (canDelete && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete theme",
                        tint = palette.negative500.toComposeColor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchCluster(swatches: List<String>) {
    val palette = LocalAlfPalette.current
    val border = palette.contrast200.toComposeColor()
    Row(
        horizontalArrangement = Arrangement.spacedBy((-6).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatches.take(4).forEach { hex ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(hex.toComposeColor())
                    .border(1.dp, border, CircleShape),
            )
        }
    }
}

private data class DiscoveredThemeBundle(
    val uri: String,
    val name: String,
    val options: List<ThemeOption>,
)

@Composable
private fun AddThemesPdsDialog(
    savedThemes: List<ThemeOption>,
    onPreview: (List<ThemeOption>) -> Unit,
    onSaveAndApply: (ThemeOption, List<ThemeOption>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val scope = rememberCoroutineScope()

    var handleOrDid by remember { mutableStateOf("witchsky.app") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resolvedLabel by remember { mutableStateOf<String?>(null) }
    var bundles by remember { mutableStateOf<List<DiscoveredThemeBundle>>(emptyList()) }

    fun fetchFromPds() {
        val raw = handleOrDid.trim().removePrefix("@")
        if (raw.isEmpty()) {
            errorMsg = "Enter a handle or DID."
            return
        }
        scope.launch {
            loading = true
            errorMsg = null
            bundles = emptyList()
            runCatching {
                val did = container.identityResolver.resolveHandle(raw)
                    ?: throw IllegalStateException("Could not resolve handle or DID “$raw”.")
                val pds = container.identityResolver.resolvePdsUrl(did)
                    ?: throw IllegalStateException("No ATProto PDS endpoint found for $did.")
                resolvedLabel = "$raw ($pds)"

                val found = mutableListOf<DiscoveredThemeBundle>()
                var cursor: String? = null
                do {
                    val page = container.api.listPdsRecords(
                        pdsOrigin = pds,
                        repo = did,
                        collection = WITCHSKY_THEME_COLLECTION,
                        limit = 50,
                        cursor = cursor,
                    )
                    for (rec in page.records) {
                        val parsed = ThemeEngine.parseThemeRecord(rec.uri, rec.value)
                        if (parsed.isNotEmpty()) {
                            found.add(
                                DiscoveredThemeBundle(
                                    uri = rec.uri,
                                    name = parsed.first().name,
                                    options = parsed,
                                )
                            )
                        }
                    }
                    cursor = page.cursor
                } while (!cursor.isNullOrBlank())
                found
            }.onSuccess { found ->
                bundles = found
                if (found.isEmpty()) {
                    errorMsg = "No $WITCHSKY_THEME_COLLECTION records found on this account's PDS."
                }
            }.onFailure { err ->
                errorMsg = err.message ?: "Failed to load themes from PDS."
            }
            loading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = palette.contrast0.toComposeColor(),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add Witchsky Themes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.contrast1000.toComposeColor(),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                Text(
                    text = "Enter an account handle or DID (such as witchsky.app) to fetch their public ATProto theme records directly from their PDS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
                OutlinedTextField(
                    value = handleOrDid,
                    onValueChange = { handleOrDid = it },
                    label = { Text("Handle or DID") },
                    placeholder = { Text("witchsky.app") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pds_theme_handle_input"),
                )
                Button(
                    onClick = { fetchFromPds() },
                    enabled = !loading && handleOrDid.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loading) "Fetching from PDS..." else "Find themes")
                }

                if (!resolvedLabel.isNullOrBlank()) {
                    Text(
                        text = "Source: $resolvedLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                }

                if (!errorMsg.isNullOrBlank()) {
                    SettingsInfoCard(body = errorMsg.orEmpty(), isError = true)
                }

                bundles.forEach { bundle ->
                    val primaryOption = bundle.options.first()
                    val alreadySaved = savedThemes.any { it.uri == bundle.uri }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.contrast25.toComposeColor())
                            .border(1.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ThemeSwatchCluster(
                                swatches = listOf(
                                    primaryOption.colors.canvas,
                                    primaryOption.colors.surface,
                                    primaryOption.colors.accent,
                                    primaryOption.colors.text,
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bundle.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.contrast1000.toComposeColor(),
                                )
                                Text(
                                    text = "${bundle.options.size} variant(s): ${bundle.options.joinToString { it.variantName }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = { onPreview(bundle.options) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Preview")
                            }
                            Button(
                                onClick = { onSaveAndApply(primaryOption, bundle.options, true) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (alreadySaved) "Apply" else "Import & Apply")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePreviewDialog(
    options: List<ThemeOption>,
    onApply: (ThemeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember(options) { mutableStateOf(options.first().id) }
    val activeOption = options.firstOrNull { it.id == selectedId } ?: options.first()
    val previewPalette: AlfPalette = remember(activeOption) {
        ThemeEngine.createWitchskyPalette(activeOption)
    }
    val previewScheme = remember(previewPalette) {
        alfPaletteToMaterialColorScheme(previewPalette)
    }
    val hostPalette = LocalAlfPalette.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = hostPalette.contrast0.toComposeColor(),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Theme Preview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = hostPalette.contrast1000.toComposeColor(),
                        )
                        Text(
                            text = "${activeOption.name} · ${activeOption.variantName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = hostPalette.contrast600.toComposeColor(),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                }

                if (options.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { opt ->
                            FilterChip(
                                selected = opt.id == activeOption.id,
                                onClick = { selectedId = opt.id },
                                label = { Text(opt.variantName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = hostPalette.primary50.toComposeColor(),
                                ),
                            )
                        }
                    }
                }

                // Live sample post card rendered inside the target palette & ColorScheme
                MaterialTheme(colorScheme = previewScheme) {
                    CompositionLocalProvider(LocalAlfPalette provides previewPalette) {
                        val canvas = previewPalette.contrast0.toComposeColor()
                        val surface = previewPalette.contrast25.toComposeColor()
                        val surfaceRaised = (previewPalette.surfaceRaised ?: previewPalette.contrast50).toComposeColor()
                        val border = previewPalette.contrast100.toComposeColor()
                        val textMain = previewPalette.contrast1000.toComposeColor()
                        val textMuted = previewPalette.contrast700.toComposeColor()
                        val accent = previewPalette.primary500.toComposeColor()
                        val accentSoft = previewPalette.primary50.toComposeColor()
                        val onAccent = (previewPalette.onAccent ?: "#FFFFFF").toComposeColor()
                        val positive = previewPalette.positive500.toComposeColor()
                        val favorite = previewPalette.like.toComposeColor()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(canvas)
                                .border(1.dp, border, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CaelumAvatar(
                                        url = null,
                                        contentDescription = "Caelum preview avatar",
                                        size = 40.dp,
                                    )
                                    Column {
                                        Text(
                                            text = "Caelum Sky",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = textMain,
                                        )
                                        Text(
                                            text = "@caelum.noz.am · 2m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textMuted,
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = accent,
                                ) {
                                    Text(
                                        text = "Follow",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = onAccent,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            Text(
                                text = "Sampling ${activeOption.name} (${activeOption.variantName}) across surfaces, typography, hashtags #ATProto, and interactive controls.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textMain,
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surface)
                                    .border(1.dp, border, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Quoted Post Surface",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textMain,
                                )
                                Text(
                                    text = "Elevated cards and quoted embeds render on surface / surfaceRaised tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textMuted,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceRaised)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                                    Text("12", style = MaterialTheme.typography.labelSmall, color = textMuted)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Repeat, contentDescription = null, tint = positive, modifier = Modifier.size(16.dp))
                                    Text("34", style = MaterialTheme.typography.labelSmall, color = positive)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = favorite, modifier = Modifier.size(16.dp))
                                    Text("128", style = MaterialTheme.typography.labelSmall, color = favorite)
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = accentSoft,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(Icons.Filled.BookmarkBorder, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                        Text("Saved", style = MaterialTheme.typography.labelSmall, color = accent)
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Sample content. Your active theme stays unchanged until you tap Apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = hostPalette.contrast600.toComposeColor(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = { onApply(activeOption) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply Theme")
                    }
                }
            }
        }
    }
}

@Composable
fun ApplyingThemeOverlay(
    visible: Boolean,
    themeLabel: String,
) {
    val palette = LocalAlfPalette.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = (palette.surfaceRaised ?: palette.contrast25).toComposeColor(),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(24.dp)
                    .testTag("applying_theme_overlay"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = palette.primary500.toComposeColor(),
                    )
                    Column {
                        Text(
                            text = "Applying theme...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.contrast1000.toComposeColor(),
                        )
                        if (themeLabel.isNotBlank()) {
                            Text(
                                text = themeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AppIconSpec(
    val id: String,
    val label: String,
    val mipmapResId: Int,
)

private val APP_ICON_SPECS = listOf(
    AppIconSpec("default", "Caelum Default", R.mipmap.ic_launcher),
    AppIconSpec("default_light", "Default Light", R.mipmap.default_light),
    AppIconSpec("default_dark", "Default Dark", R.mipmap.default_dark),
    AppIconSpec("core_aurora", "Aurora", R.mipmap.core_aurora),
    AppIconSpec("core_bonfire", "Bonfire", R.mipmap.core_bonfire),
    AppIconSpec("core_sunrise", "Sunrise", R.mipmap.core_sunrise),
    AppIconSpec("core_sunset", "Sunset", R.mipmap.core_sunset),
    AppIconSpec("core_midnight", "Midnight", R.mipmap.core_midnight),
    AppIconSpec("core_flat_blue", "Flat Blue", R.mipmap.core_flat_blue),
    AppIconSpec("core_flat_white", "Flat White", R.mipmap.core_flat_white),
    AppIconSpec("core_flat_black", "Flat Black", R.mipmap.core_flat_black),
    AppIconSpec("core_classic", "Classic", R.mipmap.core_classic),
)

private fun componentNameForIcon(context: Context, spec: AppIconSpec): ComponentName {
    val pkg = context.packageName
    return ComponentName(pkg, "$pkg.MainActivity${spec.id}")
}

private fun detectActiveAppIconId(context: Context, fallbackId: String): String {
    val pm = context.packageManager
    for (spec in APP_ICON_SPECS) {
        val state = runCatching {
            pm.getComponentEnabledSetting(componentNameForIcon(context, spec))
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return spec.id
        }
    }
    val defaultAliasState = runCatching {
        pm.getComponentEnabledSetting(
            ComponentName(context.packageName, "${context.packageName}.MainActivitydefault")
        )
    }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    return if (defaultAliasState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) "default" else fallbackId
}

private fun switchAppIcon(context: Context, target: AppIconSpec) {
    val pm = context.packageManager
    val targetComponent = componentNameForIcon(context, target)
    pm.setComponentEnabledSetting(
        targetComponent,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
    )
    for (spec in APP_ICON_SPECS) {
        if (spec.id != target.id) {
            pm.setComponentEnabledSetting(
                componentNameForIcon(context, spec),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

@Composable
fun AppIconSettingsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var activeIconId by remember(settings.appIconId) {
        mutableStateOf(detectActiveAppIconId(context, settings.appIconId))
    }

    SettingsScaffold(
        title = "App Icon",
        onBack = onBack,
        testTag = "app_icon_settings_screen",
    ) { paddingModifier ->
        Column(
            modifier = paddingModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            SettingsInfoCard(
                body = "Select the Caelum launcher icon for your home screen. Your launcher may take a moment to refresh the icon.",
                icon = Icons.Filled.Apps,
            )
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(APP_ICON_SPECS, key = { it.id }) { spec ->
                    val isSelected = activeIconId == spec.id
                    val iconBitmap = remember(spec.mipmapResId) {
                        runCatching {
                            ContextCompat.getDrawable(context, spec.mipmapResId)
                                ?.toBitmap(144, 144)
                                ?.asImageBitmap()
                        }.getOrNull()
                    }
                    val borderColor = if (isSelected) {
                        palette.primary500.toComposeColor()
                    } else {
                        palette.contrast100.toComposeColor()
                    }
                    val cardBg = if (isSelected) {
                        palette.primary50.toComposeColor().copy(alpha = 0.45f)
                    } else {
                        palette.contrast25.toComposeColor()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable {
                                haptics.confirm()
                                runCatching {
                                    switchAppIcon(context, spec)
                                    activeIconId = spec.id
                                    scope.launch { container.preferences.setAppIconId(spec.id) }
                                    Toast.makeText(context, "App icon set to ${spec.label}", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.message ?: "Unable to switch app icon",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = spec.label,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.ColorLens,
                                    contentDescription = spec.label,
                                    tint = palette.primary500.toComposeColor(),
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) palette.primary500.toComposeColor() else palette.contrast900.toComposeColor(),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
