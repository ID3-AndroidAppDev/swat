package com.example.kotobadrop.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kotobadrop.core.model.InputMode
import com.example.kotobadrop.core.model.Settings
import com.example.kotobadrop.core.model.SpeedDifficulty
import com.example.kotobadrop.core.model.ThemePreference
import com.example.kotobadrop.core.model.UiLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val UI_LANGUAGE = stringPreferencesKey("uiLanguage")
        val INPUT_MODE = stringPreferencesKey("inputMode")
        val FURIGANA = booleanPreferencesKey("furigana")
        val SPEED_DIFFICULTY = stringPreferencesKey("speedDifficulty")
        val KNOWLEDGE_DIFFICULTY = intPreferencesKey("knowledgeDifficulty")
        val SOUND_ENABLED = booleanPreferencesKey("soundEnabled")
        val THEME_PREFERENCE = stringPreferencesKey("themePreference")
        val DO_NOT_TOUCH = booleanPreferencesKey("doNotTouch")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            uiLanguage = prefs[Keys.UI_LANGUAGE]?.let { UiLanguage.valueOf(it) } ?: defaults.uiLanguage,
            inputMode = prefs[Keys.INPUT_MODE]?.let { InputMode.valueOf(it) } ?: defaults.inputMode,
            furigana = prefs[Keys.FURIGANA] ?: defaults.furigana,
            speedDifficulty = prefs[Keys.SPEED_DIFFICULTY]?.let { SpeedDifficulty.valueOf(it) } ?: defaults.speedDifficulty,
            knowledgeDifficulty = prefs[Keys.KNOWLEDGE_DIFFICULTY] ?: defaults.knowledgeDifficulty,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: defaults.soundEnabled,
            themePreference = prefs[Keys.THEME_PREFERENCE]?.let { ThemePreference.valueOf(it) } ?: defaults.themePreference,
            doNotTouch = prefs[Keys.DO_NOT_TOUCH] ?: defaults.doNotTouch,
        )
    }

    suspend fun setUiLanguage(value: UiLanguage) {
        context.dataStore.edit { it[Keys.UI_LANGUAGE] = value.name }
    }

    suspend fun setInputMode(value: InputMode) {
        context.dataStore.edit { it[Keys.INPUT_MODE] = value.name }
    }

    suspend fun setFurigana(value: Boolean) {
        context.dataStore.edit { it[Keys.FURIGANA] = value }
    }

    suspend fun setSpeedDifficulty(value: SpeedDifficulty) {
        context.dataStore.edit { it[Keys.SPEED_DIFFICULTY] = value.name }
    }

    suspend fun setKnowledgeDifficulty(value: Int) {
        context.dataStore.edit { it[Keys.KNOWLEDGE_DIFFICULTY] = value }
    }

    suspend fun setThemePreference(value: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREFERENCE] = value.name }
    }

    suspend fun setDoNotTouch(value: Boolean) {
        context.dataStore.edit { it[Keys.DO_NOT_TOUCH] = value }
    }

    suspend fun setSoundEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = value }
    }
}
