package am.noz.caelum.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import am.noz.caelum.CaelumApplication
import am.noz.caelum.MainActivity
import am.noz.caelum.R
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.TrendTopicView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TRENDS_ENDPOINT = "https://public.api.bsky.app/xrpc/app.bsky.unspecced.getTrends?limit=5"
private val KEY_TRENDS_JSON = stringPreferencesKey("trends_json")
private val KEY_TRENDS_ERROR = stringPreferencesKey("trends_error")
private val KEY_UPDATED_AT = longPreferencesKey("trends_updated_at")

class TrendingTopicsGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fetched = runCatching { fetchTopTrends(context) }
        runCatching {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().apply {
                    fetched.onSuccess { list ->
                        this[KEY_TRENDS_JSON] = AtProtoJson.encodeToString(list)
                        remove(KEY_TRENDS_ERROR)
                        this[KEY_UPDATED_AT] = System.currentTimeMillis()
                    }.onFailure { err ->
                        if (!contains(KEY_TRENDS_JSON)) {
                            this[KEY_TRENDS_ERROR] = err.message ?: "Unable to load trends"
                        }
                    }
                }
            }
        }

        val fallbackList = fetched.getOrDefault(emptyList())
        val fallbackError = fetched.exceptionOrNull()?.message

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val rawJson = prefs[KEY_TRENDS_JSON]
                val trends = remember(rawJson, fallbackList) {
                    if (!rawJson.isNullOrBlank()) {
                        runCatching { AtProtoJson.decodeFromString<List<TrendTopicView>>(rawJson) }
                            .getOrDefault(fallbackList)
                    } else {
                        fallbackList
                    }
                }
                val errorText = if (trends.isEmpty()) prefs[KEY_TRENDS_ERROR] ?: fallbackError else null

                TrendingWidgetContent(
                    context = context,
                    trends = trends.take(5),
                    errorText = errorText,
                )
            }
        }
    }

    suspend fun refreshAndUpdate(context: Context, glanceId: GlanceId) {
        val fetched = runCatching { fetchTopTrends(context) }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                fetched.onSuccess { list ->
                    this[KEY_TRENDS_JSON] = AtProtoJson.encodeToString(list)
                    remove(KEY_TRENDS_ERROR)
                    this[KEY_UPDATED_AT] = System.currentTimeMillis()
                }.onFailure { err ->
                    this[KEY_TRENDS_ERROR] = err.message ?: "Unable to load trends"
                }
            }
        }
        update(context, glanceId)
    }

    private suspend fun fetchTopTrends(context: Context): List<TrendTopicView> = withContext(Dispatchers.IO) {
        val client = (context.applicationContext as? CaelumApplication)?.container?.okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

        val request = Request.Builder()
            .url(TRENDS_ENDPOINT)
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = AtProtoJson.parseToJsonElement(body).jsonObject
            root["trends"]?.jsonArray?.mapNotNull { el ->
                runCatching { AtProtoJson.decodeFromJsonElement(TrendTopicView.serializer(), el) }.getOrNull()
            }.orEmpty().take(5)
        }
    }
}

@Composable
private fun TrendingWidgetContent(
    context: Context,
    trends: List<TrendTopicView>,
    errorText: String?,
) {
    val exploreIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("https://bsky.app/search")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(exploreIntent))
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.trending_topics_widget_display_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    maxLines = 1,
                )
            }

            Box(
                modifier = GlanceModifier
                    .cornerRadius(12.dp)
                    .clickable(actionRunCallback<RefreshTrendingTopicsAction>())
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_menu_rotate),
                    contentDescription = "Refresh trending topics",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        when {
            trends.isNotEmpty() -> {
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    trends.forEachIndexed { index, trend ->
                        TrendingTopicRow(
                            context = context,
                            rank = index + 1,
                            trend = trend,
                        )
                    }
                }
            }
            !errorText.isNullOrBlank() -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionRunCallback<RefreshTrendingTopicsAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$errorText\nTap to retry",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
            else -> {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionRunCallback<RefreshTrendingTopicsAction>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No trending topics right now. Tap to refresh.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingTopicRow(
    context: Context,
    rank: Int,
    trend: TrendTopicView,
) {
    val searchUrl = "https://bsky.app/search?q=${Uri.encode(trend.topic)}"
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(searchUrl)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val title = trend.displayName.ifBlank { trend.topic }
    val subtitle = buildList {
        if (trend.status == "hot") add("Hot")
        trend.category?.takeIf { it.isNotBlank() }?.let { add(it) }
        trend.postCount?.takeIf { it > 0 }?.let { add("$it posts") }
    }.joinToString(" · ")

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(8.dp)
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank.",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            modifier = GlanceModifier.width(20.dp),
        )
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

class RefreshTrendingTopicsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TrendingTopicsGlanceWidget().refreshAndUpdate(context, glanceId)
    }
}

class TrendingTopicsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrendingTopicsGlanceWidget()
}
