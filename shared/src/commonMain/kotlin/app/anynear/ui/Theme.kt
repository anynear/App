package app.anynear.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.FontResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anynear.AppConfig
import app.anynear.resources.Res
import app.anynear.resources.inter
import org.jetbrains.compose.resources.Font

// Brand colors of anynear (draft 03.11.2026, Docs/marke/assets/anynear-*). The names of the colors stay until the
// renaming per ADR 0061; Nachtblau is now the petrol of the draft.
// Use only via AppTheme / MaterialTheme.colorScheme / AppTheme.colors, never directly in screens.
// Orange carries dark text, never white, and marks only the one main action or the selection.
object BrandPalette {
    val Signalorange = Color(0xFFFF8A00)
    val Bernstein = Color(0xFFFFB347)
    val Nachtblau = Color(0xFF082C35)
    /** Own bubble, one step lighter than Nachtblau. */
    val Abendblau = Color(0xFF134552)
    val Schiefergrau = Color(0xFF687983)
    /** Slate gray for secondary text on a light background (6.6 : 1 instead of 4.5 : 1). */
    val SchiefergrauDunkel = Color(0xFF52626B)
    val Stein = Color(0xFFE6E4DE)
    val Alpinweiss = Color(0xFFFAFAF7)
    val OrangeHell = Color(0xFFFFF0DE)
    val OrangeText = Color(0xFF6A3A00)
    val Nacht = Color(0xFF041A20)
    val Sicher = Color(0xFF2F9E6E)
    val SicherHell = Color(0xFFE1F3EA)
    val SicherText = Color(0xFF1D6B49)
    val Warnrot = Color(0xFFC2412D)
    /** Surface and text of an active alarm (ADR 0062 §9). */
    val WarnrotHell = Color(0xFFFBE3DE)
    val WarnrotText = Color(0xFF7A2214)
    /** Light surface for input fields and incoming bubbles. */
    val Feld = Color(0xFFF1F1EC)
}

/** Brand colors that Material 3 does not cover (header area, state „sicher“). */
@Immutable
data class AppColors(
    val header: Color,
    val onHeader: Color,
    val headerAccent: Color,
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val incomingBubble: Color,
    val bubbleOutline: Color,
    /** Two check marks for „gelesen“ (ADR 0019) on your own bubble; blue would get lost on Nachtblau. */
    val readTick: Color,
    /** Active alarm (ADR 0062 §9): strong surface for the sign, light card with dark text. */
    val alarm: Color,
    val onAlarm: Color,
    val alarmContainer: Color,
    val onAlarmContainer: Color,
    /** Active warning or important alert: orange, one step calmer than an alarm. */
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** Color pairs (surface, pattern) for generated pictures of circles and people (ADR 0012). */
    val avatarTones: List<AvatarTone> = AvatarTones,
)

/** Surface and pattern of a generated picture. */
@Immutable
data class AvatarTone(val background: Color, val pattern: Color)

// Only brand colors, without green and red (state colors). Same in light and dark: a picture looks the same everywhere.
private val AvatarTones = listOf(
    AvatarTone(BrandPalette.Nachtblau, BrandPalette.Signalorange),
    AvatarTone(BrandPalette.Schiefergrau, BrandPalette.Alpinweiss),
    AvatarTone(BrandPalette.Abendblau, BrandPalette.Bernstein),
    AvatarTone(BrandPalette.OrangeHell, BrandPalette.Signalorange),
    AvatarTone(BrandPalette.Stein, BrandPalette.Schiefergrau),
    AvatarTone(BrandPalette.Nachtblau, BrandPalette.Alpinweiss),
)

private val LightScheme = lightColorScheme(
    primary = BrandPalette.Nachtblau,
    onPrimary = BrandPalette.Alpinweiss,
    primaryContainer = BrandPalette.Abendblau,
    onPrimaryContainer = Color.White,
    secondary = BrandPalette.Signalorange,
    onSecondary = BrandPalette.Nachtblau,
    secondaryContainer = BrandPalette.OrangeHell,
    onSecondaryContainer = BrandPalette.OrangeText,
    tertiary = BrandPalette.Sicher,
    onTertiary = Color.White,
    tertiaryContainer = BrandPalette.SicherHell,
    onTertiaryContainer = BrandPalette.SicherText,
    error = BrandPalette.Warnrot,
    background = BrandPalette.Alpinweiss,
    onBackground = BrandPalette.Nachtblau,
    surface = BrandPalette.Alpinweiss,
    onSurface = BrandPalette.Nachtblau,
    surfaceVariant = BrandPalette.Stein,
    onSurfaceVariant = BrandPalette.SchiefergrauDunkel,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFA7B2B8),
    outlineVariant = BrandPalette.Stein,
)

// Dark: Signalorange takes over the main action, Nachtblau becomes the surface.
private val DarkScheme = darkColorScheme(
    primary = BrandPalette.Signalorange,
    onPrimary = BrandPalette.Nachtblau,
    primaryContainer = BrandPalette.Abendblau,
    onPrimaryContainer = Color.White,
    secondary = BrandPalette.Bernstein,
    onSecondary = BrandPalette.Nachtblau,
    secondaryContainer = BrandPalette.OrangeText,
    onSecondaryContainer = BrandPalette.OrangeHell,
    tertiary = Color(0xFF6FD3A4),
    onTertiary = Color(0xFF00391F),
    tertiaryContainer = Color(0xFF14432F),
    onTertiaryContainer = Color(0xFFB9EED3),
    error = Color(0xFFFF8A75),
    background = BrandPalette.Nacht,
    onBackground = BrandPalette.Alpinweiss,
    surface = BrandPalette.Nacht,
    onSurface = BrandPalette.Alpinweiss,
    surfaceVariant = Color(0xFF0F343D),
    onSurfaceVariant = Color(0xFF9FB0B7),
    surfaceContainerLowest = BrandPalette.Nacht,
    surfaceContainerLow = BrandPalette.Nachtblau,
    surfaceContainer = BrandPalette.Nachtblau,
    surfaceContainerHigh = BrandPalette.Nachtblau,
    surfaceContainerHighest = Color(0xFF123A44),
    outline = BrandPalette.Schiefergrau,
    outlineVariant = Color(0xFF17404A),
)

// Light header area as in the draft; petrol carries text and signs, orange stays reserved for the selection.
private val LightAppColors = AppColors(
    header = BrandPalette.Alpinweiss,
    onHeader = BrandPalette.Nachtblau,
    headerAccent = BrandPalette.SchiefergrauDunkel,
    safe = BrandPalette.Sicher,
    safeContainer = BrandPalette.SicherHell,
    onSafeContainer = BrandPalette.SicherText,
    outgoingBubble = BrandPalette.Nachtblau,
    onOutgoingBubble = BrandPalette.Alpinweiss,
    incomingBubble = BrandPalette.Feld,
    bubbleOutline = BrandPalette.Feld,
    readTick = BrandPalette.Signalorange,
    alarm = BrandPalette.Warnrot,
    onAlarm = Color.White,
    alarmContainer = BrandPalette.WarnrotHell,
    onAlarmContainer = BrandPalette.WarnrotText,
    warningContainer = BrandPalette.OrangeHell,
    onWarningContainer = BrandPalette.OrangeText,
)

private val DarkAppColors = AppColors(
    header = BrandPalette.Nacht,
    onHeader = BrandPalette.Alpinweiss,
    headerAccent = Color(0xFF9FB0B7),
    safe = Color(0xFF6FD3A4),
    safeContainer = Color(0xFF14432F),
    onSafeContainer = Color(0xFFB9EED3),
    outgoingBubble = Color(0xFF1B5260),
    onOutgoingBubble = Color.White,
    incomingBubble = Color(0xFF0F343D),
    bubbleOutline = Color(0xFF0F343D),
    readTick = BrandPalette.Signalorange,
    alarm = Color(0xFFFF8A75),
    onAlarm = BrandPalette.Nacht,
    alarmContainer = Color(0xFF5A1D14),
    onAlarmContainer = Color(0xFFFFDAD3),
    warningContainer = BrandPalette.OrangeText,
    onWarningContainer = BrandPalette.OrangeHell,
)

private val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/** Name of the app as it appears in UI texts. */
internal const val appName: String = AppConfig.APP_NAME

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

// Inter as a variable font: one file, weights via the wght axis.
@Composable
private fun brandFamily(file: FontResource): FontFamily {
    @Composable
    fun weight(w: FontWeight) = Font(
        file,
        weight = w,
        variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
    )
    return FontFamily(
        weight(FontWeight.Light),
        weight(FontWeight.Normal),
        weight(FontWeight.Medium),
        weight(FontWeight.SemiBold),
    )
}

// Slightly larger font than the Material default: the app must be well readable under stress and for older people too.
@Composable
private fun appTypography(): Typography {
    val family = brandFamily(Res.font.inter)
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall = style(34, 42, FontWeight.SemiBold, -0.5),
        headlineLarge = style(30, 38, FontWeight.SemiBold, -0.5),
        headlineMedium = style(26, 34, FontWeight.SemiBold, -0.25),
        headlineSmall = style(22, 30, FontWeight.SemiBold),
        titleLarge = style(21, 28, FontWeight.SemiBold),
        titleMedium = style(18, 24, FontWeight.Medium),
        titleSmall = style(15, 20, FontWeight.Medium),
        bodyLarge = style(18, 26, FontWeight.Normal),
        bodyMedium = style(16, 22, FontWeight.Normal),
        bodySmall = style(14, 20, FontWeight.Normal),
        labelLarge = style(15, 20, FontWeight.Medium),
        labelMedium = style(13, 18, FontWeight.Medium),
        labelSmall = style(12, 16, FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    val colors = if (dark) DarkAppColors else LightAppColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(), shapes = AppShapes, content = content)
    }
}
