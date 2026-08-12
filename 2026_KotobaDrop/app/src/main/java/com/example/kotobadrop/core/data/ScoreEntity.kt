package com.example.kotobadrop.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per completed run (Endless or Campaign), per CLAUDE.md §7. */
@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val score: Int,
    val playedAt: Long,
    val maxCombo: Int,
    val wordsCleared: Int,
    val wordsMissed: Int,
    val speedDifficulty: String,
    val knowledgeDifficulty: Int,
)
