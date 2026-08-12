package com.example.kotobadrop.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Japanese pastel identity per CLAUDE.md §10. The light kinari theme IS the identity; the
// dark ("yoru") scheme keeps every pastel accent identical and only swaps the ground —
// charcoal background, kinari-register ink. No dynamic color in either: the palette is
// the identity, not Material defaults.
private val PastelColorScheme = lightColorScheme(
    primary = Sakura,
    onPrimary = Sumi,
    primaryContainer = Sakura,
    onPrimaryContainer = Sumi,
    secondary = Matcha,
    onSecondary = Sumi,
    secondaryContainer = Matcha,
    onSecondaryContainer = Sumi,
    tertiary = Sora,
    onTertiary = Sumi,
    tertiaryContainer = Sora,
    onTertiaryContainer = Sumi,
    background = Kinari,
    onBackground = Sumi,
    surface = Kinari,
    onSurface = Sumi,
    error = Akabeni,
    onError = Sumi,
)

private val YoruColorScheme = darkColorScheme(
    primary = Sakura,
    onPrimary = Sumi,
    primaryContainer = Sakura,
    onPrimaryContainer = Sumi,
    secondary = Matcha,
    onSecondary = Sumi,
    secondaryContainer = Matcha,
    onSecondaryContainer = Sumi,
    tertiary = Sora,
    onTertiary = Sumi,
    tertiaryContainer = Sora,
    onTertiaryContainer = Sumi,
    background = YoruBg,
    onBackground = KinariInk,
    surface = YoruBg,
    onSurface = KinariInk,
    surfaceVariant = YoruSurface,
    onSurfaceVariant = KinariInk,
    error = Akabeni,
    onError = Sumi,
    errorContainer = YoruAkabeniContainer,
    onErrorContainer = KinariInk,
)

/**
 * Theme-dependent semantic colors for everything the Material scheme has no slot for.
 * Screens must use these (via [KotobaTheme]) instead of the raw palette tokens whenever
 * the color sits on the *background/surface* — on-pastel content (button labels on
 * Sakura/Matcha chips) stays literal [Sumi] in both themes, since the pastels themselves
 * never change.
 */
@Immutable
data class KotobaPalette(
    /** Primary text/icon ink on the background: Sumi on kinari, kinari-ink on yoru. */
    val ink: Color,
    /** Furigana + reading text: Fuji deepened for light, original fuji pastel for dark. */
    val furigana: Color,
    /** Readable success/won text on the background. */
    val success: Color,
    /** Readable fail/danger text on the background. */
    val danger: Color,
    /** The game input field's fill. */
    val fieldBackground: Color,
    /** Opaque card background for the redesign's section/stat cards: white on kinari, elevated surface on yoru. */
    val cardBackground: Color,
    /**
     * Opacity of the seigaiha background pattern. Sora strokes differ from kinari by only
     * ~25% luminance but from yoru charcoal by ~70%, so equal alpha renders wildly unequal
     * visual weight — light needs several times dark's alpha to read at all (0.07 was still
     * invisible on a real screen; 0.20 chosen by the user 2026-07-19). Dark keeps the
     * original near-subliminal value.
     */
    val seigahaAlpha: Float,
)

private val LightKotobaPalette = KotobaPalette(
    ink = Sumi,
    furigana = Fuji,
    success = MatchaDeep,
    danger = AkabeniDeep,
    fieldBackground = Color.White.copy(alpha = 0.55f),
    cardBackground = Color.White,
    seigahaAlpha = 0.20f,
)

private val DarkKotobaPalette = KotobaPalette(
    ink = KinariInk,
    furigana = FujiPastel,
    success = Matcha,
    danger = Akabeni,
    fieldBackground = YoruField,
    cardBackground = YoruSurface,
    seigahaAlpha = 0.035f,
)

private val LocalKotobaPalette = staticCompositionLocalOf { LightKotobaPalette }

object KotobaTheme {
    val palette: KotobaPalette
        @Composable get() = LocalKotobaPalette.current
}

@Composable
fun KotobaDropTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalKotobaPalette provides if (darkTheme) DarkKotobaPalette else LightKotobaPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) YoruColorScheme else PastelColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
