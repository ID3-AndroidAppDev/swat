package com.example.kotobadrop.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class ScoreAggregates(val runs: Int, val totalCleared: Int, val totalMissed: Int, val bestScore: Int)

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(entity: ScoreEntity)

    @Query("SELECT * FROM scores ORDER BY score DESC LIMIT :limit")
    suspend fun getHighScores(limit: Int): List<ScoreEntity>

    @Query("SELECT COUNT(*) AS runs, COALESCE(SUM(wordsCleared), 0) AS totalCleared, COALESCE(SUM(wordsMissed), 0) AS totalMissed, COALESCE(MAX(score), 0) AS bestScore FROM scores")
    suspend fun aggregates(): ScoreAggregates
}
