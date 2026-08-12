package com.example.kotobadrop.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.kotobadrop.game.campaign.CAMPAIGN_LEVELS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.campaignDataStore by preferencesDataStore(name = "campaign")

/** levelId -> best score. A level's presence as a key means it's completed. */
class CampaignRepository(private val context: Context) {
    private fun scoreKey(levelId: String) = intPreferencesKey("score_$levelId")

    val progressFlow: Flow<Map<String, Int>> = context.campaignDataStore.data.map { prefs ->
        CAMPAIGN_LEVELS.mapNotNull { level -> prefs[scoreKey(level.id)]?.let { level.id to it } }.toMap()
    }

    suspend fun markCompleted(levelId: String, score: Int) {
        context.campaignDataStore.edit { prefs ->
            val key = scoreKey(levelId)
            prefs[key] = maxOf(prefs[key] ?: 0, score)
        }
    }

    /** Wipes all level completions/best scores — the Settings "Reset progress" action. */
    suspend fun resetAll() {
        context.campaignDataStore.edit { it.clear() }
    }
}
