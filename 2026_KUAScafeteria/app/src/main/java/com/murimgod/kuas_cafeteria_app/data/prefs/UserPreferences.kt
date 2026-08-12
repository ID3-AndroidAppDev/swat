package com.murimgod.kuas_cafeteria_app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kuas_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        val CAMPUS = stringPreferencesKey("campus")
        val LANG = stringPreferencesKey("lang")
        val EXCLUDED_ALLERGENS = stringPreferencesKey("excluded_allergens")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val FILTER_ACTIVE = booleanPreferencesKey("filter_active")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val THEME = stringPreferencesKey("theme")  // system | light | dark
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val themeFlow: Flow<String> = context.dataStore.data
        .map { it[THEME] ?: "light" }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data
        .map { it[DYNAMIC_COLOR] ?: false }

    val campusFlow: Flow<String> = context.dataStore.data
        .map { it[CAMPUS] ?: "uzumasa" }

    val langFlow: Flow<String> = context.dataStore.data
        .map { it[LANG] ?: "en" }

    val excludedAllergensFlow: Flow<Set<String>> = context.dataStore.data
        .map { prefs ->
            prefs[EXCLUDED_ALLERGENS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
        }

    val filterActiveFlow: Flow<Boolean> = context.dataStore.data
        .map { it[FILTER_ACTIVE] ?: false }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { it[ONBOARDING_DONE] ?: false }

    suspend fun setCampus(campus: String) {
        context.dataStore.edit { it[CAMPUS] = campus }
    }

    suspend fun setLang(lang: String) {
        context.dataStore.edit { it[LANG] = lang }
    }

    suspend fun setExcludedAllergens(allergens: Set<String>) {
        context.dataStore.edit { it[EXCLUDED_ALLERGENS] = allergens.joinToString(",") }
    }

    suspend fun setFilterActive(active: Boolean) {
        context.dataStore.edit { it[FILTER_ACTIVE] = active }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME] = theme }
    }

    /** Blocking read of the saved theme, for [android.app.Application.onCreate]
     *  where applying the night mode must happen before any Activity inflates. */
    fun themeBlocking(): String = runBlocking { themeFlow.first() }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    fun dynamicColorBlocking(): Boolean = runBlocking { dynamicColorFlow.first() }

    /** Anonymous install id (random UUID). Generated once, persisted; never a
     *  hardware identifier. Used only to dedupe telemetry rows. */
    suspend fun getOrCreateDeviceId(): String {
        context.dataStore.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[DEVICE_ID] = id }
        return id
    }
}
