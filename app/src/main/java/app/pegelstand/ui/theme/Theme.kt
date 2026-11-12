package app.pegelstand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.pegelstand.domain.WaterColor
import app.pegelstand.ui.bottle.PegelColors

/**
 * Colours the Material scheme has no role for: gold for "goal reached", the history tracks, and
 * whether the dark bottle variant applies.
 */
@Immutable
data class PegelExtras(
    val dark: Boolean,
    /** Gold fill of reached bars and drops (same in both themes, like the bottle's accent). */
    val gold: Color,
    /** Lighter end of the gold gradient. */
    val goldLight: Color,
    /** Gold that is readable as text on the background. */
    val goldText: Color,
    /** Empty bar track / empty drop of a past day. */
    val track: Color,
    /** Outline of a future day. */
    val futureOutline: Color,
)

private val LightExtras = PegelExtras(
    dark = false,
    gold = Color(PegelColors.GOLD),
    goldLight = Color(0xFFFFD66B),
    goldText = Color(PegelColors.Light.goldText),
    track = Color(0xFFDCEBF5),
    futureOutline = Color(0xFFB4C9D8),
)

private val DarkExtras = PegelExtras(
    dark = true,
    gold = Color(PegelColors.GOLD),
    goldLight = Color(0xFFFFD66B),
    goldText = Color(PegelColors.Dark.goldText),
    track = Color(0xFF223442),
    futureOutline = Color(0xFF3C5263),
)

val LocalPegelExtras = staticCompositionLocalOf { LightExtras }

// Every role is set explicitly so no Material baseline (purple) colour can leak in.
private val Navy = Color(PegelColors.Light.outline)
private val Ice = Color(PegelColors.Dark.outline)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8F7),
    onPrimaryContainer = Color(0xFF082F6B),
    inversePrimary = Ice,
    secondary = Color(0xFF3F6683),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEDF8),
    onSecondaryContainer = Color(0xFF12324A),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC7),
    onTertiaryContainer = Color(0xFF5A3A00),
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF122436),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF122436),
    surfaceVariant = Color(PegelColors.Light.glassTint),
    onSurfaceVariant = Color(0xFF51677A),
    surfaceTint = Navy,
    inverseSurface = Color(0xFF223443),
    inverseOnSurface = Color(0xFFEAF3F9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE2DF),
    onErrorContainer = Color(0xFF5C1210),
    outline = Color(0xFF8DA6B8),
    outlineVariant = Color(0xFFD2E2EC),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF6FAFD),
    surfaceDim = Color(0xFFD6E2EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF6FB),
    surfaceContainer = Color(PegelColors.Light.glassTint),
    surfaceContainerHigh = Color(0xFFE0EEF7),
    surfaceContainerHighest = Color(0xFFD7E8F3),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Ice,
    onPrimary = Color(0xFF07263F),
    primaryContainer = Color(0xFF1D3A52),
    onPrimaryContainer = Color(0xFFD6EFFC),
    inversePrimary = Navy,
    secondary = Color(0xFFA7C8E0),
    onSecondary = Color(0xFF0F2C40),
    secondaryContainer = Color(0xFF223747),
    onSecondaryContainer = Color(0xFFD2E6F4),
    tertiary = Color(PegelColors.Dark.goldText),
    onTertiary = Color(0xFF3A2600),
    tertiaryContainer = Color(PegelColors.Dark.chip),
    onTertiaryContainer = Color(PegelColors.Dark.onChip),
    background = Color(0xFF0C141A),
    onBackground = Color(0xFFE2EEF5),
    surface = Color(0xFF0C141A),
    onSurface = Color(0xFFE2EEF5),
    surfaceVariant = Color(PegelColors.Dark.glassTint),
    onSurfaceVariant = Color(0xFFA3B8C7),
    surfaceTint = Ice,
    inverseSurface = Color(0xFFE2EEF5),
    inverseOnSurface = Color(0xFF1B2A35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6A8293),
    outlineVariant = Color(0xFF2A3C49),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3A46),
    surfaceDim = Color(0xFF0C141A),
    surfaceContainerLowest = Color(0xFF081016),
    surfaceContainerLow = Color(0xFF111C24),
    surfaceContainer = Color(0xFF15222C),
    surfaceContainerHigh = Color(PegelColors.Dark.glassTint),
    surfaceContainerHighest = Color(0xFF253744),
)

private val BaseTypography = Typography()

private val PegelTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    displayMedium = BaseTypography.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Material 3 theme built from [PegelColors]; light and dark, no dynamic colour. */
@Composable
fun PegelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPegelExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = PegelTypography,
            content = content,
        )
    }
}

/** The light (top) end of the water gradient. */
val WaterColor.lightColor: Color get() = Color(light)

/** The deep (bottom) end of the water gradient. */
val WaterColor.deepColor: Color get() = Color(deep)

/**
 * The water colour as a button fill under white text: the deep end, darkened a little so the
 * label stays readable on the lighter presets (mint, coral, turquoise).
 */
val WaterColor.buttonColor: Color get() = lerp(Color(deep), Color(0xFF0A1F33), 0.22f)
