package com.example.kotobadrop.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the schema baked into assets/words.db by tools/preprocess/build_words_db.py.
 * Column names must match that script's schema exactly (see tools/preprocess/README.md) —
 * Room's createFromAsset() validates the copied database's actual structure against this
 * entity on first open.
 */
@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey val id: Int,
    val surface: String,
    val reading: String,
    val meaning: String,
    val tier: Int,
    val hardestKanjiGrade: Int?,
    val frequencyRank: Int?,
    val kanaOnly: Boolean,
)
