package com.example.kotobadrop.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per word the player has ever encountered in a run. Created on FIRST APPEARANCE
 * (not on clear), per CLAUDE.md §7 — counters then serve both the dictionary (§8) and
 * adaptive spawn weighting (§5).
 */
@Entity(tableName = "met_words")
data class MetWordEntity(
    @PrimaryKey val wordId: Int,
    val firstSeen: Long,
    val timesSeen: Int,
    val timesCleared: Int,
    val timesMissed: Int,
)
