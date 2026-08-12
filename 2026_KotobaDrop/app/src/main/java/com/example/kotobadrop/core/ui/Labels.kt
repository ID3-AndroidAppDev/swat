package com.example.kotobadrop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.kotobadrop.R
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.core.model.ThemePreference

/**
 * Display-name lookups for enums/tiers, kept out of core/model so those stay plain data
 * (stringResource() needs a @Composable context) — shared across every screen that shows
 * a SpeedDifficulty, InputMode, or campaign section tier, so the label stays consistent.
 */
@Composable
fun speedDifficultyLabel(speed: SpeedDifficulty): String = when (speed) {
    SpeedDifficulty.EASY -> stringResource(R.string.speed_easy)
    SpeedDifficulty.NORMAL -> stringResource(R.string.speed_normal)
    SpeedDifficulty.HARD -> stringResource(R.string.speed_hard)
    SpeedDifficulty.EXPERT -> stringResource(R.string.speed_expert)
}

@Composable
fun inputModeLabel(mode: InputMode): String = when (mode) {
    InputMode.ROMAJI -> stringResource(R.string.input_mode_romaji)
    InputMode.IME -> stringResource(R.string.input_mode_ime)
}

@Composable
fun themePreferenceLabel(pref: ThemePreference): String = when (pref) {
    ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
    ThemePreference.LIGHT -> stringResource(R.string.theme_light)
    ThemePreference.DARK -> stringResource(R.string.theme_dark)
}

@Composable
fun campaignSectionDisplayName(tier: Int): String = when (tier) {
    0 -> stringResource(R.string.campaign_section_0)
    1 -> stringResource(R.string.campaign_section_1)
    2 -> stringResource(R.string.campaign_section_2)
    3 -> stringResource(R.string.campaign_section_3)
    else -> stringResource(R.string.campaign_section_4)
}
