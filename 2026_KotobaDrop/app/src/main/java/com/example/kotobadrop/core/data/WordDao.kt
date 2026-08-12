package com.example.kotobadrop.core.data

import androidx.room.Dao
import androidx.room.Query

data class TierCount(val tier: Int, val count: Int)

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    suspend fun getAll(): List<WordEntity>

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<WordEntity>

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Query("SELECT tier, COUNT(*) AS count FROM words GROUP BY tier")
    suspend fun countByTier(): List<TierCount>
}
