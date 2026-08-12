package com.example.kotobadrop.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.kotobadrop.R

// Bundled offline (no downloadable-fonts provider) per CLAUDE.md §10. Both fonts keep the
// child-friendly, playful feel; split by role because Hachi Maru Pop's glyph widths are wide
// and uneven enough to cause real layout bugs (chip labels wrapping, falling words drifting
// past the screen edge) on text that has to lay out predictably.
// Hachi Maru Pop: display/headline/title — static, short strings where that's harmless.
// Yusei Magic: body/label — falling words, buttons, HUD, dictionary. Designed by Google's
// Japanese type team for on-screen UI legibility; more consistent character widths. Verified
// (fontTools cmap check) to cover all 1,796 unique characters across every word in words.db.
private val HachiMaruPop = FontFamily(
    Font(R.font.hachi_maru_pop_regular, FontWeight.Normal),
)

private val YuseiMagic = FontFamily(
    Font(R.font.yusei_magic_regular, FontWeight.Normal),
)

private val defaults = Typography()

val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = HachiMaruPop),
    displayMedium = defaults.displayMedium.copy(fontFamily = HachiMaruPop),
    displaySmall = defaults.displaySmall.copy(fontFamily = HachiMaruPop),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = HachiMaruPop),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = HachiMaruPop),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = HachiMaruPop),
    titleLarge = defaults.titleLarge.copy(fontFamily = HachiMaruPop),
    titleMedium = defaults.titleMedium.copy(fontFamily = YuseiMagic),
    titleSmall = defaults.titleSmall.copy(fontFamily = YuseiMagic),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = YuseiMagic),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = YuseiMagic),
    bodySmall = defaults.bodySmall.copy(fontFamily = YuseiMagic),
    labelLarge = defaults.labelLarge.copy(fontFamily = YuseiMagic),
    labelMedium = defaults.labelMedium.copy(fontFamily = YuseiMagic),
    labelSmall = defaults.labelSmall.copy(fontFamily = YuseiMagic),
)
