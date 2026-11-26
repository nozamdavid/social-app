package am.noz.caelum.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.XrpcClient
import am.noz.caelum.core.network.XrpcException
import am.noz.caelum.ui.components.CaelumHaptics
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val palette = LocalAlfPalette.current
    val bg = palette.contrast0.toComposeColor()
    val textColor = palette.contrast1000.toComposeColor()
    val dividerColor = palette.contrast100.toComposeColor()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .let { if (testTag != null) it.testTag(testTag) else it },
        containerColor = bg,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = bg,
                        titleContentColor = textColor,
                        navigationIconContentColor = textColor,
                    ),
                )
                HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
            }
        },
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = palette.primary500.toComposeColor(),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    val palette = LocalAlfPalette.current
    HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = palette.contrast100.toComposeColor(),
    )
}

@Composable
fun SettingsListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badge: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalAlfPalette.current
    val fg = when {
        !enabled -> palette.contrast400.toComposeColor()
        destructive -> palette.negative500.toComposeColor()
        else -> palette.contrast1000.toComposeColor()
    }
    val subColor = palette.contrast600.toComposeColor()
    val iconTint = when {
        !enabled -> palette.contrast400.toComposeColor()
        destructive -> palette.negative500.toComposeColor()
        else -> palette.contrast700.toComposeColor()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                )
            }
        }
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = palette.contrast50.toComposeColor(),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.contrast700.toComposeColor(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.contrast400.toComposeColor(),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    haptics: CaelumHaptics? = null,
) {
    val palette = LocalAlfPalette.current
    val fg = if (enabled) palette.contrast1000.toComposeColor() else palette.contrast400.toComposeColor()
    val subColor = palette.contrast600.toComposeColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                val next = !checked
                haptics?.toggle(next)
                onCheckedChange(next)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) palette.contrast700.toComposeColor() else palette.contrast400.toComposeColor(),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = fg,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { next ->
                haptics?.toggle(next)
                onCheckedChange(next)
            },
            enabled = enabled,
        )
    }
}

@Composable
fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalAlfPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) palette.contrast1000.toComposeColor() else palette.contrast400.toComposeColor(),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsInfoCard(
    title: String? = null,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isError: Boolean = false,
    isAccent: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = LocalAlfPalette.current
    val bgColor: Color
    val borderColor: Color
    val titleColor: Color
    when {
        isError -> {
            bgColor = palette.negative50.toComposeColor()
            borderColor = palette.negative200.toComposeColor()
            titleColor = palette.negative600.toComposeColor()
        }
        isAccent -> {
            bgColor = palette.primary50.toComposeColor()
            borderColor = palette.primary200.toComposeColor()
            titleColor = palette.primary600.toComposeColor()
        }
        else -> {
            bgColor = palette.contrast25.toComposeColor()
            borderColor = palette.contrast100.toComposeColor()
            titleColor = palette.contrast1000.toComposeColor()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!title.isNullOrBlank() || icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                }
            }
        }
        if (body.isNotBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = palette.contrast800.toComposeColor(),
            )
        }
        content?.invoke(this)
    }
}

fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}

/**
 * Updates or inserts a single actor preference entry in `app.bsky.actor.putPreferences`.
 */
suspend fun upsertActorPreference(
    api: AtprotoApi,
    type: String,
    match: (JsonObject) -> Boolean = { (it["\$type"] as? JsonPrimitive)?.content == type },
    builder: (existing: JsonObject?) -> JsonObject,
) {
    val existing = api.getActorPreferences()
    var matched: JsonObject? = null
    val kept = buildList {
        for (el in existing) {
            val obj = el as? JsonObject
            if (obj != null && match(obj)) {
                matched = obj
            } else {
                add(el)
            }
        }
    }
    val nextEntry = builder(matched)
    val updated = buildJsonArray {
        kept.forEach { add(it) }
        add(nextEntry)
    }
    api.putActorPreferences(updated)
}

/**
 * Reads and updates the user's `app.bsky.actor.profile` self-labels (`!no-unauthenticated`, `bot`, etc.).
 */
suspend fun toggleProfileSelfLabel(
    xrpc: XrpcClient,
    api: AtprotoApi,
    did: String,
    labelValue: String,
    enabled: Boolean,
) {
    val recordResp = try {
        xrpc.getJson(
            nsid = "com.atproto.repo.getRecord",
            params = listOf(
                "repo" to did,
                "collection" to "app.bsky.actor.profile",
                "rkey" to "self",
            ),
        ).jsonObject
    } catch (ex: XrpcException) {
        val isNotFound = ex.statusCode == 404 ||
            ex.error == "RecordNotFound" ||
            (ex.error == "InvalidRequest" && ex.message.contains("Could not locate record", ignoreCase = true))
        if (isNotFound) null else throw ex
    }

    val existingValue = recordResp?.get("value")?.jsonObject ?: buildJsonObject {
        put("\$type", JsonPrimitive("app.bsky.actor.profile"))
    }
    val currentLabels = existingValue["labels"]?.jsonObject
        ?.get("values")?.jsonArray
        ?.mapNotNull { (it as? JsonObject)?.get("val")?.jsonPrimitive?.content }
        .orEmpty()
        .filter { it != labelValue }

    val nextLabels = if (enabled) currentLabels + labelValue else currentLabels

    val updatedRecord = buildJsonObject {
        for ((k, v) in existingValue) {
            if (k != "labels") put(k, v)
        }
        if (!existingValue.containsKey("\$type")) {
            put("\$type", JsonPrimitive("app.bsky.actor.profile"))
        }
        if (nextLabels.isNotEmpty()) {
            put(
                "labels",
                buildJsonObject {
                    put("\$type", JsonPrimitive("com.atproto.label.defs#selfLabels"))
                    put(
                        "values",
                        JsonArray(
                            nextLabels.distinct().map { v ->
                                buildJsonObject { put("val", JsonPrimitive(v)) }
                            }
                        ),
                    )
                },
            )
        }
    }

    api.putRecord(
        repo = did,
        collection = "app.bsky.actor.profile",
        rkey = "self",
        record = updatedRecord,
    )
}
