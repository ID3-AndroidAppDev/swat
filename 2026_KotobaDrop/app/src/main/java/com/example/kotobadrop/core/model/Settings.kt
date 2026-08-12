package com.example.kotobadrop.core.model

enum class UiLanguage { EN, JA }

enum class InputMode { ROMAJI, IME }

enum class SpeedDifficulty { EASY, NORMAL, HARD, EXPERT }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class Settings(
    val uiLanguage: UiLanguage = UiLanguage.EN,
    val inputMode: InputMode = InputMode.ROMAJI,
    val furigana: Boolean = true,
    val speedDifficulty: SpeedDifficulty = SpeedDifficulty.NORMAL,
    val knowledgeDifficulty: Int = 4,
    val soundEnabled: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val doNotTouch: Boolean = false,
)
