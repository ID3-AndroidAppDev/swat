package com.example.kotobadrop.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MetWordDao {
    @Query("SELECT * FROM met_words WHERE wordId = :wordId")
    suspend fun get(wordId: Int): MetWordEntity?

    @Query("SELECT * FROM met_words")
    suspend fun getAll(): List<MetWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MetWordEntity)

    @Query("DELETE FROM met_words")
    suspend fun deleteAll()
}
