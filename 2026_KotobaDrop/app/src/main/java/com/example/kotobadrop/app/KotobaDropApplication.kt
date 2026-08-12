package com.example.kotobadrop.app

import android.app.Application
import com.example.kotobadrop.core.data.AppDatabase
import com.example.kotobadrop.core.data.CampaignRepository
import com.example.kotobadrop.core.data.MetWordRepository
import com.example.kotobadrop.core.data.ScoreRepository
import com.example.kotobadrop.core.data.SettingsRepository
import com.example.kotobadrop.core.data.UserDataDatabase
import com.example.kotobadrop.core.data.WordEntity
import com.example.kotobadrop.core.data.WordRepository

class KotobaDropApplication : Application() {
    private val database by lazy { AppDatabase.build(this) }
    private val userDataDatabase by lazy { UserDataDatabase.build(this) }

    val wordRepository by lazy { WordRepository(database.wordDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val campaignRepository by lazy { CampaignRepository(this) }
    val metWordRepository by lazy { MetWordRepository(userDataDatabase.metWordDao()) }
    val scoreRepository by lazy { ScoreRepository(userDataDatabase.scoreDao()) }

    /**
     * The most recent run's missed words, written by GameScreen at run end and read by the
     * Results/LevelResult screens so a learner can review what they missed. In-memory only
     * on purpose: it's a hand-off between two screens in one session, not persistence —
     * met-word history already records misses durably. Lost on process death, in which case
     * the results screens simply omit the list.
     */
    var lastRunMissedWords: List<WordEntity> = emptyList()
}
