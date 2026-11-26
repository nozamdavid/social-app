package am.noz.caelum.ui.theme

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

const val WITCHSKY_THEME_COLLECTION = "app.witchsky.theme.colors"
const val BUNDLED_CAELUM_THEME_URI = "builtin://caelum"

enum class ThemeMode {
    light,
    dim,
    dark,
}

@Immutable
@Serializable
data class SemanticColors(
    val canvas: String,
    val surface: String,
    val surfaceRaised: String,
    val text: String,
    val textMuted: String,
    val border: String,
    val accent: String,
    val accentSoft: String,
    val onAccent: String,
    val positive: String,
    val warning: String,
    val critical: String,
    val favorite: String,
)

@Immutable
@Serializable
data class ThemeOption(
    val id: String,
    val uri: String,
    val name: String,
    val variantName: String,
    val mode: String, // "light" or "dark"
    val colors: SemanticColors,
)

@Immutable
data class AlfPalette(
    val name: ThemeMode,
    val isDark: Boolean,
    val white: String = "#FFFFFF",
    val black: String = "#000000",
    val pink: String,
    val yellow: String,
    val like: String,
    val contrast0: String,
    val contrast25: String,
    val contrast50: String,
    val contrast100: String,
    val contrast200: String,
    val contrast300: String,
    val contrast400: String,
    val contrast500: String,
    val contrast600: String,
    val contrast700: String,
    val contrast800: String,
    val contrast900: String,
    val contrast950: String,
    val contrast975: String,
    val contrast1000: String,
    val primary25: String,
    val primary50: String,
    val primary100: String,
    val primary200: String,
    val primary300: String,
    val primary400: String,
    val primary500: String,
    val primary600: String,
    val primary700: String,
    val primary800: String,
    val primary900: String,
    val primary950: String,
    val primary975: String,
    val positive25: String,
    val positive50: String,
    val positive100: String,
    val positive200: String,
    val positive300: String,
    val positive400: String,
    val positive500: String,
    val positive600: String,
    val positive700: String,
    val positive800: String,
    val positive900: String,
    val positive950: String,
    val positive975: String,
    val negative25: String,
    val negative50: String,
    val negative100: String,
    val negative200: String,
    val negative300: String,
    val negative400: String,
    val negative500: String,
    val negative600: String,
    val negative700: String,
    val negative800: String,
    val negative900: String,
    val negative950: String,
    val negative975: String,
    val onAccent: String? = null,
    val surfaceRaised: String? = null,
)

object ThemeEngine {

    val STOCK_LIGHT = AlfPalette(
        name = ThemeMode.light,
        isDark = false,
        pink = "#EC4899",
        yellow = "#FFC404",
        like = "#EC4899",
        contrast0 = "#FFFFFF",
        contrast25 = "#F9FAFB",
        contrast50 = "#EFF2F6",
        contrast100 = "#DCE2EA",
        contrast200 = "#C0CAD8",
        contrast300 = "#A5B2C5",
        contrast400 = "#8798B0",
        contrast500 = "#667B99",
        contrast600 = "#526580",
        contrast700 = "#405168",
        contrast800 = "#313F54",
        contrast900 = "#232E3E",
        contrast950 = "#19222E",
        contrast975 = "#111822",
        contrast1000 = "#000000",
        primary25 = "#F5F9FF",
        primary50 = "#E5F0FF",
        primary100 = "#CCE1FF",
        primary200 = "#A8CCFF",
        primary300 = "#75AFFF",
        primary400 = "#4291FF",
        primary500 = "#006AFF",
        primary600 = "#0059D6",
        primary700 = "#0048AD",
        primary800 = "#00398A",
        primary900 = "#002861",
        primary950 = "#001E47",
        primary975 = "#001533",
        positive25 = "#ECFEF5",
        positive50 = "#D3FDE8",
        positive100 = "#A3FACF",
        positive200 = "#6AF6B0",
        positive300 = "#2CF28F",
        positive400 = "#0DD370",
        positive500 = "#09B35E",
        positive600 = "#04904A",
        positive700 = "#036D38",
        positive800 = "#04522B",
        positive900 = "#033F21",
        positive950 = "#032A17",
        positive975 = "#021D0F",
        negative25 = "#FFF5F7",
        negative50 = "#FEE7EC",
        negative100 = "#FDD3DD",
        negative200 = "#FBBBCA",
        negative300 = "#F891A9",
        negative400 = "#F65A7F",
        negative500 = "#E91646",
        negative600 = "#CA123D",
        negative700 = "#A71134",
        negative800 = "#7F0B26",
        negative900 = "#5F071C",
        negative950 = "#430413",
        negative975 = "#30030D",
    )

    private val STOCK_SUBDUED_LIGHT = AlfPalette(
        name = ThemeMode.light,
        isDark = false,
        pink = "#EC4899",
        yellow = "#FFC404",
        like = "#EC4899",
        contrast0 = "#FFFFFF",
        contrast25 = "#F9FAFB",
        contrast50 = "#F2F4F8",
        contrast100 = "#E2E7EE",
        contrast200 = "#C3CDDA",
        contrast300 = "#ABB8C9",
        contrast400 = "#8D9DB4",
        contrast500 = "#6F839F",
        contrast600 = "#586C89",
        contrast700 = "#485B75",
        contrast800 = "#394960",
        contrast900 = "#2C3A4E",
        contrast950 = "#222E3F",
        contrast975 = "#1C2736",
        contrast1000 = "#151D28",
        primary25 = "#F5F9FF",
        primary50 = "#EBF3FF",
        primary100 = "#D6E7FF",
        primary200 = "#ADCFFF",
        primary300 = "#80B5FF",
        primary400 = "#4D97FF",
        primary500 = "#0F73FF",
        primary600 = "#0661E0",
        primary700 = "#0A52B8",
        primary800 = "#0E4490",
        primary900 = "#123464",
        primary950 = "#122949",
        primary975 = "#122136",
        positive25 = "#ECFEF5",
        positive50 = "#D8FDEB",
        positive100 = "#A8FAD1",
        positive200 = "#6FF6B3",
        positive300 = "#31F291",
        positive400 = "#0EDD75",
        positive500 = "#0AC266",
        positive600 = "#049F52",
        positive700 = "#038142",
        positive800 = "#056636",
        positive900 = "#04522B",
        positive950 = "#053D21",
        positive975 = "#052917",
        negative25 = "#FFF5F7",
        negative50 = "#FEEBEF",
        negative100 = "#FDD8E1",
        negative200 = "#FCC0CE",
        negative300 = "#F99AB0",
        negative400 = "#F76486",
        negative500 = "#EB2452",
        negative600 = "#D81341",
        negative700 = "#BA1239",
        negative800 = "#910D2C",
        negative900 = "#6F0B22",
        negative950 = "#500B1C",
        negative975 = "#3E0915",
    )

    val STOCK_DARK = invertPalette(STOCK_LIGHT, ThemeMode.dark)
    val STOCK_DIM = invertPalette(STOCK_SUBDUED_LIGHT, ThemeMode.dim)

    val CAELUM_LIGHT = STOCK_LIGHT.copy(
        name = ThemeMode.light,
        isDark = false,
        contrast0 = "#FAF9FE",
        contrast25 = "#F6F5FA",
        contrast50 = "#F0EFF4",
        contrast100 = "#E0DEE4",
        contrast200 = "#C5C4CB",
        contrast300 = "#AFAEB6",
        contrast400 = "#97959E",
        contrast500 = "#7D7A85",
        contrast600 = "#67646F",
        contrast700 = "#55535E",
        contrast800 = "#46434F",
        contrast900 = "#34313E",
        contrast950 = "#292634",
        contrast975 = "#24212E",
        contrast1000 = "#1E1B29",
        primary25 = "#FAFAFE",
        primary50 = "#F3F1FD",
        primary100 = "#E6E3FB",
        primary200 = "#CEC7F7",
        primary300 = "#ACA1F1",
        primary400 = "#8A7AEB",
        primary500 = "#6550E5",
        primary600 = "#5B49CB",
        primary700 = "#5141B0",
        primary800 = "#473A96",
        primary900 = "#3C3178",
        primary950 = "#322A5E",
        primary975 = "#2B254B",
    )

    private val CAELUM_SUBDUED_LIGHT = STOCK_SUBDUED_LIGHT.copy(
        name = ThemeMode.light,
        isDark = false,
        contrast0 = "#FAF9FE",
        contrast25 = "#F6F5FA",
        contrast50 = "#F0EFF4",
        contrast100 = "#DFDEE4",
        contrast200 = "#C5C3CB",
        contrast300 = "#AFADB5",
        contrast400 = "#97959E",
        contrast500 = "#7C7A84",
        contrast600 = "#66646F",
        contrast700 = "#54525E",
        contrast800 = "#45424F",
        contrast900 = "#33303D",
        contrast950 = "#282533",
        contrast975 = "#23202D",
        contrast1000 = "#1D1A28",
        primary25 = "#FAFAFE",
        primary50 = "#F3F1FD",
        primary100 = "#E6E3FB",
        primary200 = "#CEC7F7",
        primary300 = "#ACA1F1",
        primary400 = "#8A7AEB",
        primary500 = "#6550E5",
        primary600 = "#5B49CB",
        primary700 = "#5141B0",
        primary800 = "#473A96",
        primary900 = "#3C3178",
        primary950 = "#322A5E",
        primary975 = "#2B254B",
    )

    val CAELUM_DIM = invertPalette(CAELUM_SUBDUED_LIGHT, ThemeMode.dim)
    val CAELUM_DARK = invertPalette(CAELUM_LIGHT, ThemeMode.dark).copy(
        contrast0 = "#09080f",
        contrast25 = "#100e19",
        contrast50 = "#171421",
        contrast100 = "#292437",
    )

    val bundledThemes: List<ThemeOption> = listOf(
        Triple("light", "Light", CAELUM_LIGHT),
        Triple("dim", "Dim", CAELUM_DIM),
        Triple("dark", "Dark", CAELUM_DARK),
    ).map { (key, variantName, p) ->
        ThemeOption(
            id = "$BUNDLED_CAELUM_THEME_URI#$key",
            uri = BUNDLED_CAELUM_THEME_URI,
            name = "Caelum",
            variantName = variantName,
            mode = if (key == "light") "light" else "dark",
            colors = SemanticColors(
                canvas = p.contrast0,
                surface = p.contrast25,
                surfaceRaised = p.contrast50,
                text = p.contrast1000,
                textMuted = p.contrast700,
                border = p.contrast100,
                accent = p.primary500,
                accentSoft = p.primary50,
                onAccent = "#FFFFFF",
                positive = p.positive500,
                warning = p.yellow,
                critical = p.negative500,
                favorite = p.like,
            ),
        )
    }

    fun getBundledTheme(id: String): ThemeOption? = bundledThemes.firstOrNull { it.id == id }

    fun getBundledPalette(id: String): AlfPalette? {
        val option = getBundledTheme(id) ?: return null
        val p = when (id.substringAfter('#')) {
            "light" -> CAELUM_LIGHT
            "dim" -> CAELUM_DIM
            "dark" -> CAELUM_DARK
            else -> return null
        }
        return p.copy(onAccent = option.colors.onAccent, surfaceRaised = option.colors.surfaceRaised)
    }

    fun invertPalette(palette: AlfPalette, targetMode: ThemeMode): AlfPalette = palette.copy(
        name = targetMode,
        isDark = true,
        contrast0 = palette.contrast1000,
        contrast25 = palette.contrast975,
        contrast50 = palette.contrast950,
        contrast100 = palette.contrast900,
        contrast200 = palette.contrast800,
        contrast300 = palette.contrast700,
        contrast400 = palette.contrast600,
        contrast500 = palette.contrast500,
        contrast600 = palette.contrast400,
        contrast700 = palette.contrast300,
        contrast800 = palette.contrast200,
        contrast900 = palette.contrast100,
        contrast950 = palette.contrast50,
        contrast975 = palette.contrast25,
        contrast1000 = palette.contrast0,
        primary25 = palette.primary975,
        primary50 = palette.primary950,
        primary100 = palette.primary900,
        primary200 = palette.primary800,
        primary300 = palette.primary700,
        primary400 = palette.primary600,
        primary500 = palette.primary500,
        primary600 = palette.primary400,
        primary700 = palette.primary300,
        primary800 = palette.primary200,
        primary900 = palette.primary100,
        primary950 = palette.primary50,
        primary975 = palette.primary25,
        positive25 = palette.positive975,
        positive50 = palette.positive950,
        positive100 = palette.positive900,
        positive200 = palette.positive800,
        positive300 = palette.positive700,
        positive400 = palette.positive600,
        positive500 = palette.positive500,
        positive600 = palette.positive400,
        positive700 = palette.positive300,
        positive800 = palette.positive200,
        positive900 = palette.positive100,
        positive950 = palette.positive50,
        positive975 = palette.positive25,
        negative25 = palette.negative975,
        negative50 = palette.negative950,
        negative100 = palette.negative900,
        negative200 = palette.negative800,
        negative300 = palette.negative700,
        negative400 = palette.negative600,
        negative500 = palette.negative500,
        negative600 = palette.negative400,
        negative700 = palette.negative300,
        negative800 = palette.negative200,
        negative900 = palette.negative100,
        negative950 = palette.negative50,
        negative975 = palette.negative25,
    )

    fun normalizeHexColor(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        if (trimmed.length > 128) return null
        if (trimmed.startsWith("#")) {
            val hex = trimmed.substring(1)
            return when (hex.length) {
                3 -> {
                    if (!hex.all { it.isHexDigit() }) return null
                    val r = "${hex[0]}${hex[0]}"
                    val g = "${hex[1]}${hex[1]}"
                    val b = "${hex[2]}${hex[2]}"
                    "#${r}${g}${b}".lowercase(Locale.ROOT)
                }
                6 -> {
                    if (!hex.all { it.isHexDigit() }) return null
                    "#${hex.lowercase(Locale.ROOT)}"
                }
                8 -> {
                    if (!hex.all { it.isHexDigit() }) return null
                    val rgb = hex.substring(0, 6).lowercase(Locale.ROOT)
                    val a = hex.substring(6, 8).lowercase(Locale.ROOT)
                    if (a == "ff") "#$rgb" else "#$rgb$a"
                }
                else -> null
            }
        }
        return null
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun mixHex(from: String, to: String, amount: Double): String {
        val a = parseRgba(from)
        val b = parseRgba(to)
        val res = IntArray(4) { i ->
            (a[i] + (b[i] - a[i]) * amount).roundToInt().coerceIn(0, 255)
        }
        val base = "#%02x%02x%02x".format(Locale.ROOT, res[0], res[1], res[2])
        return if (res[3] == 255) base else base + "%02x".format(Locale.ROOT, res[3])
    }

    private fun parseRgba(color: String): IntArray {
        val norm = normalizeHexColor(color) ?: "#000000"
        val r = norm.substring(1, 3).toInt(16)
        val g = norm.substring(3, 5).toInt(16)
        val b = norm.substring(5, 7).toInt(16)
        val a = if (norm.length == 9) norm.substring(7, 9).toInt(16) else 255
        return intArrayOf(r, g, b, a)
    }

    fun resolveCanvasMode(canvas: String, fallback: String): String {
        val norm = normalizeHexColor(canvas) ?: return fallback
        val alpha = if (norm.length == 9) norm.substring(7, 9).toInt(16) / 255.0 else 1.0
        val ground = if (fallback == "light") 255.0 else 0.0
        val linear = doubleArrayOf(
            srgbToLinear((norm.substring(1, 3).toInt(16) * alpha + ground * (1.0 - alpha)) / 255.0),
            srgbToLinear((norm.substring(3, 5).toInt(16) * alpha + ground * (1.0 - alpha)) / 255.0),
            srgbToLinear((norm.substring(5, 7).toInt(16) * alpha + ground * (1.0 - alpha)) / 255.0),
        )
        val luminance = linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
        return if (luminance > 0.179) "light" else "dark"
    }

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun createWitchskyPalette(option: ThemeOption): AlfPalette {
        getBundledPalette(option.id)?.let { return it }
        val c = option.colors
        val isLight = option.mode == "light"
        val base = if (isLight) STOCK_LIGHT else STOCK_DARK
        val mode = if (isLight) ThemeMode.light else ThemeMode.dark

        val primarySoft = c.accentSoft
        val primaryMain = c.accent
        val positiveSoft = mixHex(c.canvas, c.positive, 0.15)
        val negativeSoft = mixHex(c.canvas, c.critical, 0.15)

        return base.copy(
            name = mode,
            isDark = !isLight,
            pink = c.favorite,
            like = c.favorite,
            yellow = c.warning,
            contrast0 = c.canvas,
            contrast25 = c.surface,
            contrast50 = c.surfaceRaised,
            contrast100 = c.border,
            contrast200 = mixHex(c.border, c.textMuted, 0.25),
            contrast300 = mixHex(c.border, c.textMuted, 0.5),
            contrast400 = c.textMuted,
            contrast500 = mixHex(c.textMuted, c.text, 0.25),
            contrast600 = mixHex(c.textMuted, c.text, 0.45),
            contrast700 = c.textMuted,
            contrast800 = mixHex(c.textMuted, c.text, 0.65),
            contrast900 = mixHex(c.textMuted, c.text, 0.85),
            contrast950 = mixHex(c.textMuted, c.text, 0.93),
            contrast975 = mixHex(c.textMuted, c.text, 0.97),
            contrast1000 = c.text,
            primary25 = primarySoft,
            primary50 = primarySoft,
            primary100 = primarySoft,
            primary200 = mixHex(primarySoft, primaryMain, 0.3),
            primary300 = mixHex(primarySoft, primaryMain, 0.55),
            primary400 = mixHex(primarySoft, primaryMain, 0.8),
            primary500 = primaryMain,
            primary600 = primaryMain,
            primary700 = mixHex(primaryMain, c.text, 0.15),
            primary800 = mixHex(primaryMain, c.text, 0.3),
            primary900 = mixHex(primaryMain, c.text, 0.45),
            primary950 = mixHex(primaryMain, c.text, 0.6),
            primary975 = mixHex(primaryMain, c.text, 0.7),
            positive25 = positiveSoft,
            positive50 = positiveSoft,
            positive100 = positiveSoft,
            positive200 = mixHex(positiveSoft, c.positive, 0.3),
            positive300 = mixHex(positiveSoft, c.positive, 0.55),
            positive400 = mixHex(positiveSoft, c.positive, 0.8),
            positive500 = c.positive,
            positive600 = c.positive,
            positive700 = mixHex(c.positive, c.text, 0.15),
            positive800 = mixHex(c.positive, c.text, 0.3),
            positive900 = mixHex(c.positive, c.text, 0.45),
            positive950 = mixHex(c.positive, c.text, 0.6),
            positive975 = mixHex(c.positive, c.text, 0.7),
            negative25 = negativeSoft,
            negative50 = negativeSoft,
            negative100 = negativeSoft,
            negative200 = mixHex(negativeSoft, c.critical, 0.3),
            negative300 = mixHex(negativeSoft, c.critical, 0.55),
            negative400 = mixHex(negativeSoft, c.critical, 0.8),
            negative500 = c.critical,
            negative600 = c.critical,
            negative700 = mixHex(c.critical, c.text, 0.15),
            negative800 = mixHex(c.critical, c.text, 0.3),
            negative900 = mixHex(c.critical, c.text, 0.45),
            negative950 = mixHex(c.critical, c.text, 0.6),
            negative975 = mixHex(c.critical, c.text, 0.7),
            onAccent = c.onAccent,
            surfaceRaised = c.surfaceRaised,
        )
    }

    fun parseThemeRecord(uri: String, value: JsonObject): List<ThemeOption> {
        if (value["\$type"]?.jsonPrimitive?.contentOrNull != WITCHSKY_THEME_COLLECTION) return emptyList()
        val name = value["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= 64 }
            ?: return emptyList()
        val fallbackMode = value["mode"]?.jsonPrimitive?.contentOrNull?.takeIf { it == "light" || it == "dark" }
            ?: return emptyList()
        val baseObj = value["base"]?.jsonObject ?: return emptyList()
        val baseName = baseObj["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it.length <= 48 }
            ?: return emptyList()
        val baseColorsObj = baseObj["colors"]?.jsonObject ?: return emptyList()
        val baseColors = parseSemanticColors(baseColorsObj) ?: return emptyList()

        val baseOption = ThemeOption(
            id = uri,
            uri = uri,
            name = name,
            variantName = baseName,
            mode = resolveCanvasMode(baseColors.canvas, fallbackMode),
            colors = baseColors,
        )
        val result = mutableListOf(baseOption)
        val variants = value["variants"] as? JsonArray ?: return result
        if (variants.size > 11) return result

        variants.forEachIndexed { index, elem ->
            val vObj = elem as? JsonObject ?: return@forEachIndexed
            val vName = vObj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() && it.length <= 48 } ?: return@forEachIndexed
            val partial = vObj["colors"] as? JsonObject
            val merged = if (partial == null) baseColors else mergeSemanticColors(baseColors, partial) ?: return@forEachIndexed
            result.add(
                baseOption.copy(
                    id = "$uri#variant-$index",
                    variantName = vName,
                    colors = merged,
                    mode = resolveCanvasMode(merged.canvas, fallbackMode),
                )
            )
        }
        return result
    }

    private fun parseSemanticColors(obj: JsonObject): SemanticColors? {
        fun col(k: String) = normalizeHexColor(obj[k]?.jsonPrimitive?.contentOrNull)
        return SemanticColors(
            canvas = col("canvas") ?: return null,
            surface = col("surface") ?: return null,
            surfaceRaised = col("surfaceRaised") ?: return null,
            text = col("text") ?: return null,
            textMuted = col("textMuted") ?: return null,
            border = col("border") ?: return null,
            accent = col("accent") ?: return null,
            accentSoft = col("accentSoft") ?: return null,
            onAccent = col("onAccent") ?: return null,
            positive = col("positive") ?: return null,
            warning = col("warning") ?: return null,
            critical = col("critical") ?: return null,
            favorite = col("favorite") ?: return null,
        )
    }

    private fun mergeSemanticColors(base: SemanticColors, patch: JsonObject): SemanticColors? {
        fun col(k: String, fallback: String): String? {
            if (!patch.containsKey(k)) return fallback
            return normalizeHexColor(patch[k]?.jsonPrimitive?.contentOrNull)
        }
        return SemanticColors(
            canvas = col("canvas", base.canvas) ?: return null,
            surface = col("surface", base.surface) ?: return null,
            surfaceRaised = col("surfaceRaised", base.surfaceRaised) ?: return null,
            text = col("text", base.text) ?: return null,
            textMuted = col("textMuted", base.textMuted) ?: return null,
            border = col("border", base.border) ?: return null,
            accent = col("accent", base.accent) ?: return null,
            accentSoft = col("accentSoft", base.accentSoft) ?: return null,
            onAccent = col("onAccent", base.onAccent) ?: return null,
            positive = col("positive", base.positive) ?: return null,
            warning = col("warning", base.warning) ?: return null,
            critical = col("critical", base.critical) ?: return null,
            favorite = col("favorite", base.favorite) ?: return null,
        )
    }

    private val themeJson = Json { ignoreUnknownKeys = true }

    fun readThemeSelection(raw: String?, did: String): ThemeOption? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = Json.parseToJsonElement(raw).jsonObject
            val dataObj = obj["data"]?.jsonObject ?: obj
            val option = themeJson.decodeFromJsonElement(ThemeOption.serializer(), dataObj)
            if (option.uri == BUNDLED_CAELUM_THEME_URI) {
                getBundledTheme(option.id)
            } else if (option.uri.startsWith("at://$did/$WITCHSKY_THEME_COLLECTION/")) {
                option
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hexToArgbLong(hex: String): Long {
        val norm = normalizeHexColor(hex) ?: "#000000"
        val r = norm.substring(1, 3).toLong(16)
        val g = norm.substring(3, 5).toLong(16)
        val b = norm.substring(5, 7).toLong(16)
        val a = if (norm.length == 9) norm.substring(7, 9).toLong(16) else 255L
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun getDrawerOverlayArgb(palette: AlfPalette): Long {
        if (palette.surfaceRaised != null) {
            val base = hexToArgbLong(palette.contrast0) and 0x00FFFFFFL
            val alpha = if (palette.name == ThemeMode.dim) 0xCC000000L else 0x1A000000L
            return alpha or base
        }
        return when (palette.name) {
            ThemeMode.light -> 0x1A003975L
            ThemeMode.dark -> 0x1A1085FEL
            ThemeMode.dim -> 0xCC0A0D10L
        }
    }
}
