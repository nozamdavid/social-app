package am.noz.caelum.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import am.noz.caelum.R

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_semi_bold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

fun String.toComposeColor(): Color = Color(ThemeEngine.hexToArgbLong(this))

val LocalAlfPalette = staticCompositionLocalOf { ThemeEngine.STOCK_LIGHT }
val LocalThemeFontScale = staticCompositionLocalOf { 1f }
val LocalReducedMotion = staticCompositionLocalOf { false }

val CaelumShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

fun computeFontScaleMultiplier(fontScaleKey: String): Float = when (fontScaleKey) {
    "-2", "-1" -> 0.9375f
    "1", "2" -> 1.0625f
    else -> 1f
}

fun alfPaletteToMaterialColorScheme(palette: AlfPalette): ColorScheme {
    val bg = palette.contrast0.toComposeColor()
    val surface = palette.contrast25.toComposeColor()
    val surfaceRaised = (palette.surfaceRaised ?: palette.contrast50).toComposeColor()
    val text = palette.contrast1000.toComposeColor()
    val textMuted = palette.contrast700.toComposeColor()
    val border = palette.contrast100.toComposeColor()
    val primary = palette.primary500.toComposeColor()
    val primarySoft = palette.primary50.toComposeColor()
    val onPrimary = (palette.onAccent ?: "#FFFFFF").toComposeColor()
    val error = palette.negative500.toComposeColor()
    val errorContainer = palette.negative100.toComposeColor()

    return if (palette.isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primarySoft,
            onPrimaryContainer = text,
            secondary = palette.primary400.toComposeColor(),
            onSecondary = onPrimary,
            secondaryContainer = surfaceRaised,
            onSecondaryContainer = text,
            background = bg,
            onBackground = text,
            surface = bg,
            onSurface = text,
            surfaceVariant = surface,
            onSurfaceVariant = textMuted,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surfaceRaised,
            surfaceContainerHigh = palette.contrast100.toComposeColor(),
            surfaceContainerHighest = palette.contrast200.toComposeColor(),
            outline = border,
            outlineVariant = palette.contrast50.toComposeColor(),
            error = error,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = text,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primarySoft,
            onPrimaryContainer = text,
            secondary = palette.primary400.toComposeColor(),
            onSecondary = onPrimary,
            secondaryContainer = surfaceRaised,
            onSecondaryContainer = text,
            background = bg,
            onBackground = text,
            surface = bg,
            onSurface = text,
            surfaceVariant = surface,
            onSurfaceVariant = textMuted,
            surfaceContainerLowest = bg,
            surfaceContainerLow = surface,
            surfaceContainer = surfaceRaised,
            surfaceContainerHigh = palette.contrast100.toComposeColor(),
            surfaceContainerHighest = palette.contrast200.toComposeColor(),
            outline = border,
            outlineVariant = palette.contrast50.toComposeColor(),
            error = error,
            onError = Color.White,
            errorContainer = errorContainer,
            onErrorContainer = text,
        )
    }
}

fun buildCaelumTypography(
    useInterFont: Boolean,
    scaleMultiplier: Float,
): Typography {
    val family = if (useInterFont) InterFontFamily else FontFamily.Default
    val s = scaleMultiplier
    return Typography(
        displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = (30 * s).sp, lineHeight = (35 * s).sp),
        headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = (24.3 * s).sp, lineHeight = (28 * s).sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = (20.6 * s).sp, lineHeight = (25 * s).sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (18.8 * s).sp, lineHeight = (23 * s).sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (18.8 * s).sp, lineHeight = (24 * s).sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (16.9 * s).sp, lineHeight = (22 * s).sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (15 * s).sp, lineHeight = (20 * s).sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = (15 * s).sp, lineHeight = (20 * s).sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = (14 * s).sp, lineHeight = (18.5 * s).sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = (13.1 * s).sp, lineHeight = (17 * s).sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = (14 * s).sp, lineHeight = (18 * s).sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = (13.1 * s).sp, lineHeight = (17 * s).sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = (11.3 * s).sp, lineHeight = (14 * s).sp),
    )
}

@Composable
fun CaelumTheme(
    colorMode: String = "system",
    darkTheme: String = "dim",
    witchskyTheme: ThemeOption? = null,
    dynamicColorEnabled: Boolean = false,
    fontFamilySetting: String = "theme",
    fontScaleSetting: String = "0",
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val palette: AlfPalette = if (witchskyTheme != null) {
        ThemeEngine.createWitchskyPalette(witchskyTheme)
    } else {
        val useDark = when (colorMode) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        if (!useDark) {
            ThemeEngine.STOCK_LIGHT
        } else if (darkTheme == "dark") {
            ThemeEngine.STOCK_DARK
        } else {
            ThemeEngine.STOCK_DIM
        }
    }

    // Optional Dynamic Color ONLY applies when user has not selected a custom Witchsky/Caelum theme.
    val colorScheme = if (
        dynamicColorEnabled &&
        witchskyTheme == null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        val ctx = LocalContext.current
        if (palette.isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        alfPaletteToMaterialColorScheme(palette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            val lightBars = !palette.isDark
            insetsController.isAppearanceLightStatusBars = lightBars
            insetsController.isAppearanceLightNavigationBars = lightBars
        }
    }

    val scaleMultiplier = computeFontScaleMultiplier(fontScaleSetting)
    val typography = buildCaelumTypography(
        useInterFont = fontFamilySetting == "theme",
        scaleMultiplier = scaleMultiplier,
    )

    CompositionLocalProvider(
        LocalAlfPalette provides palette,
        LocalThemeFontScale provides scaleMultiplier,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = CaelumShapes,
            typography = typography,
            content = content,
        )
    }
}
