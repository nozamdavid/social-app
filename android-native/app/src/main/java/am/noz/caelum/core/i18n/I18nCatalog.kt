package am.noz.caelum.core.i18n

import am.noz.caelum.core.model.TimeFormatter
import java.time.temporal.ChronoUnit
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class SupportedLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
)

/**
 * Native Lingui catalog loader & evaluator backed by the pre-compiled
 * `assets/locales/en_index.json` and 44 `<locale>.json` files exported from TS.
 */
class I18nCatalog(
    val localeTag: String,
    private val enIndex: Map<String, String>,
    private val messages: Map<String, JsonArray>,
    private val fallbackEn: Map<String, JsonArray>,
) {
    val locale: Locale = Locale.forLanguageTag(localeTag)

    fun translate(
        source: String,
        args: Map<String, Any?> = emptyMap(),
        context: String? = null,
        stripMarkupTags: Boolean = true,
    ): String {
        val lookupKey = if (!context.isNullOrEmpty()) "$context\u0004$source" else source
        val messageId = enIndex[lookupKey] ?: enIndex[source]
        val tokens = messageId?.let { messages[it] ?: fallbackEn[it] }
        if (tokens == null) {
            return formatFallback(source, args, stripMarkupTags)
        }
        return evalTokens(tokens, args, activeCount = null, stripMarkupTags = stripMarkupTags)
    }

    fun formatRelativeShort(iso: String?, now: Instant = Instant.now()): String {
        val then = TimeFormatter.parseInstantOrNull(iso) ?: return ""
        val seconds = ChronoUnit.SECONDS.between(then, now)
        if (seconds < 5) return translate("now")
        if (seconds < 60) return translate("{0}s", mapOf("0" to seconds))
        if (seconds < 3600) return translate("{0}m", mapOf("0" to seconds / 60))
        if (seconds < 86400) return translate("{0}h", mapOf("0" to seconds / 3600))
        val days = seconds / 86400
        if (days < 30) return translate("{0}d", mapOf("0" to days))

        val zone = ZoneId.systemDefault()
        val sameYear = then.atZone(zone).year == now.atZone(zone).year
        val skeleton = if (sameYear) "MMMd" else "yMMMd"
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(then)
    }

    fun formatDate(isoOrInstant: Any?, dateStyle: FormatStyle = FormatStyle.MEDIUM, timeStyle: FormatStyle? = FormatStyle.SHORT): String {
        val instant = when (isoOrInstant) {
            is Instant -> isoOrInstant
            is Long -> Instant.ofEpochMilli(isoOrInstant)
            is String -> runCatching { Instant.parse(isoOrInstant) }.getOrNull() ?: return isoOrInstant
            else -> return ""
        }
        val formatter = if (timeStyle != null) {
            DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)
        } else {
            DateTimeFormatter.ofLocalizedDate(dateStyle)
        }.withLocale(locale).withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    private fun evalTokens(
        tokens: JsonArray,
        args: Map<String, Any?>,
        activeCount: Number?,
        stripMarkupTags: Boolean,
    ): String {
        val sb = StringBuilder()
        for (el in tokens) {
            when (el) {
                is JsonPrimitive -> {
                    val str = el.content
                    if (str == "#" && activeCount != null) {
                        sb.append(NumberFormat.getInstance(locale).format(activeCount))
                    } else {
                        sb.append(if (stripMarkupTags) TAG_REGEX.replace(str, "") else str)
                    }
                }
                is JsonArray -> {
                    if (el.size == 1) {
                        val varName = el[0].jsonPrimitive.content
                        val v = args[varName]
                        sb.append(formatArg(v))
                    } else if (el.size >= 3) {
                        val varName = el[0].jsonPrimitive.content
                        val kind = el[1].jsonPrimitive.content
                        val branches = el[2].jsonObject
                        val rawVal = args[varName]
                        val selectedBranch: JsonArray? = when (kind) {
                            "plural", "selectordinal" -> {
                                val num = (rawVal as? Number) ?: rawVal?.toString()?.toDoubleOrNull() ?: 0
                                val exactKey = if (num.toDouble() == num.toLong().toDouble()) {
                                    num.toLong().toString()
                                } else {
                                    num.toString()
                                }
                                val cat = pluralCategory(locale, num.toDouble())
                                (branches[exactKey]
                                    ?: branches["=$exactKey"]
                                    ?: branches[cat]
                                    ?: branches["other"])?.jsonArray
                            }
                            "select" -> {
                                val key = rawVal?.toString().orEmpty()
                                (branches[key] ?: branches["other"])?.jsonArray
                            }
                            else -> branches["other"]?.jsonArray
                        }
                        if (selectedBranch != null) {
                            val countArg = if (kind == "plural" || kind == "selectordinal") {
                                (rawVal as? Number) ?: rawVal?.toString()?.toDoubleOrNull()
                            } else {
                                activeCount
                            }
                            sb.append(evalTokens(selectedBranch, args, countArg, stripMarkupTags))
                        }
                    }
                }
                else -> Unit
            }
        }
        return sb.toString()
    }

    private fun formatArg(value: Any?): String = when (value) {
        null -> ""
        is Int, is Long -> NumberFormat.getIntegerInstance(locale).format(value)
        is Float, is Double -> NumberFormat.getNumberInstance(locale).format(value)
        else -> value.toString()
    }

    private fun formatFallback(source: String, args: Map<String, Any?>, stripMarkupTags: Boolean): String {
        var out = if (stripMarkupTags) TAG_REGEX.replace(source, "") else source
        for ((k, v) in args) {
            out = out.replace("{$k}", formatArg(v))
        }
        return out
    }

    companion object {
        private val TAG_REGEX = Regex("</?\\d+/?>")
        private val json = Json { ignoreUnknownKeys = true }

        val SUPPORTED_LANGUAGES: List<SupportedLanguage> = listOf(
            SupportedLanguage("en", "English", "English"),
            SupportedLanguage("en-GB", "English (UK)", "English (UK)"),
            SupportedLanguage("en-CA", "English (Canada)", "English (Canada)"),
            SupportedLanguage("an", "Aragonese", "Aragonés"),
            SupportedLanguage("ast", "Asturian", "Asturianu"),
            SupportedLanguage("ca", "Catalan", "Català"),
            SupportedLanguage("cs", "Czech", "Čeština"),
            SupportedLanguage("cy", "Welsh", "Cymraeg"),
            SupportedLanguage("da", "Danish", "Dansk"),
            SupportedLanguage("de", "German", "Deutsch"),
            SupportedLanguage("el", "Greek", "Ελληνικά"),
            SupportedLanguage("eo", "Esperanto", "Esperanto"),
            SupportedLanguage("es", "Spanish", "Español"),
            SupportedLanguage("eu", "Basque", "Euskara"),
            SupportedLanguage("fi", "Finnish", "Suomi"),
            SupportedLanguage("fr", "French", "Français"),
            SupportedLanguage("fr-CA", "French (Canada)", "Français (Canada)"),
            SupportedLanguage("fy", "Western Frisian", "Frysk"),
            SupportedLanguage("ga", "Irish", "Gaeilge"),
            SupportedLanguage("gd", "Scottish Gaelic", "Gàidhlig"),
            SupportedLanguage("gl", "Galician", "Galego"),
            SupportedLanguage("hi", "Hindi", "हिन्दी"),
            SupportedLanguage("hu", "Hungarian", "Magyar"),
            SupportedLanguage("ia", "Interlingua", "Interlingua"),
            SupportedLanguage("id", "Indonesian", "Bahasa Indonesia"),
            SupportedLanguage("it", "Italian", "Italiano"),
            SupportedLanguage("ja", "Japanese", "日本語"),
            SupportedLanguage("km", "Khmer", "ខ្មែរ"),
            SupportedLanguage("ko", "Korean", "한국어"),
            SupportedLanguage("ne", "Nepali", "नेपाली"),
            SupportedLanguage("nl", "Dutch", "Nederlands"),
            SupportedLanguage("pl", "Polish", "Polski"),
            SupportedLanguage("pt-BR", "Portuguese (Brazil)", "Português (Brasil)"),
            SupportedLanguage("pt-PT", "Portuguese (Portugal)", "Português (Portugal)"),
            SupportedLanguage("ro", "Romanian", "Română"),
            SupportedLanguage("ru", "Russian", "Русский"),
            SupportedLanguage("sv", "Swedish", "Svenska"),
            SupportedLanguage("th", "Thai", "ไทย"),
            SupportedLanguage("tr", "Turkish", "Türkçe"),
            SupportedLanguage("uk", "Ukrainian", "Українська"),
            SupportedLanguage("vi", "Vietnamese", "Tiếng Việt"),
            SupportedLanguage("zh-CN", "Chinese (Simplified)", "简体中文"),
            SupportedLanguage("zh-HK", "Chinese (Hong Kong)", "繁體中文 (香港)"),
            SupportedLanguage("zh-TW", "Chinese (Traditional)", "繁體中文"),
        )

        fun normalizeLocaleCode(requested: String?, available: Set<String> = SUPPORTED_LANGUAGES.map { it.code }.toSet()): String {
            if (requested.isNullOrBlank()) return "en"
            val cleaned = requested.replace('_', '-')
            available.firstOrNull { it.equals(cleaned, ignoreCase = true) }?.let { return it }
            val parts = cleaned.split('-')
            if (parts.isNotEmpty()) {
                val base = parts[0].lowercase()
                if (base == "zh") {
                    val region = parts.getOrNull(1)?.uppercase()
                    return when {
                        region == "HK" || region == "MO" -> "zh-HK"
                        region == "TW" || region == "HANT" -> "zh-TW"
                        else -> "zh-CN"
                    }
                }
                if (base == "yue") return "zh-HK"
                if (base == "pt") {
                    return if (parts.getOrNull(1).equals("PT", ignoreCase = true)) "pt-PT" else "pt-BR"
                }
                available.firstOrNull { it.equals(base, ignoreCase = true) }?.let { return it }
            }
            return "en"
        }

        fun loadFromAssets(context: Context, localeCode: String?): I18nCatalog {
            val resolved = normalizeLocaleCode(localeCode)
            val enIndex = context.assets.open("locales/en_index.json").use { parseIndex(it) }
            val enMsgs = context.assets.open("locales/en.json").use { parseMessages(it) }
            val activeMsgs = if (resolved == "en") {
                enMsgs
            } else {
                runCatching {
                    context.assets.open("locales/$resolved.json").use { parseMessages(it) }
                }.getOrDefault(enMsgs)
            }
            return I18nCatalog(resolved, enIndex, activeMsgs, enMsgs)
        }

        fun fromStreams(
            localeTag: String,
            enIndexStream: InputStream,
            localeMessagesStream: InputStream,
            fallbackEnStream: InputStream,
        ): I18nCatalog {
            return I18nCatalog(
                localeTag = localeTag,
                enIndex = parseIndex(enIndexStream),
                messages = parseMessages(localeMessagesStream),
                fallbackEn = parseMessages(fallbackEnStream),
            )
        }

        fun parseIndex(stream: InputStream): Map<String, String> {
            val raw = stream.bufferedReader().use { it.readText() }
            val obj = json.parseToJsonElement(raw).jsonObject
            return obj.mapValues { it.value.jsonPrimitive.content }
        }

        fun parseMessages(stream: InputStream): Map<String, JsonArray> {
            val raw = stream.bufferedReader().use { it.readText() }
            val obj = json.parseToJsonElement(raw).jsonObject
            return obj.mapValues { it.value.jsonArray }
        }

        private fun pluralCategory(locale: Locale, value: Double): String {
            // Safe across Android API 24+ and JVM unit tests (where android.icu may be stubbed).
            val icuResult = runCatching {
                val rules = android.icu.text.PluralRules.forLocale(locale)
                rules.select(value)
            }.getOrNull()
            if (!icuResult.isNullOrBlank()) return icuResult

            val n = value.toLong()
            val isInt = value == n.toDouble()
            val lang = locale.language.lowercase()
            return when (lang) {
                "ja", "ko", "zh", "yue", "vi", "th", "id", "km" -> "other"
                "fr" -> if (isInt && (n == 0L || n == 1L)) "one" else "other"
                "ru", "uk" -> {
                    if (!isInt) return "other"
                    val mod10 = n % 10
                    val mod100 = n % 100
                    when {
                        mod10 == 1L && mod100 != 11L -> "one"
                        mod10 in 2..4 && mod100 !in 12..14 -> "few"
                        else -> "many"
                    }
                }
                else -> if (value == 1.0) "one" else "other"
            }
        }
    }
}

val LocalI18n = staticCompositionLocalOf {
    I18nCatalog(
        localeTag = "en",
        enIndex = emptyMap(),
        messages = emptyMap(),
        fallbackEn = emptyMap(),
    )
}

@Composable
fun tr(
    source: String,
    args: Map<String, Any?> = emptyMap(),
    context: String? = null,
): String = LocalI18n.current.translate(source = source, args = args, context = context)
