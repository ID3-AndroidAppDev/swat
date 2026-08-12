package com.example.kotobadrop.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Deliberately a SEPARATE physical database from AppDatabase (words.db), not additional
// tables bolted onto it. AppDatabase is createFromAsset + fallbackToDestructiveMigration,
// and its own comment documents that its version gets bumped on every word-data pipeline
// re-run — Room implements that destructive fallback for a prepackaged database by deleting
// the on-disk file and re-copying the asset, which would silently wipe ANY other tables
// sharing that file. Met-words and scores are the player's actual progress, so they live
// here instead, in a normal (non-prepackaged) Room database we fully control the schema of.
@Database(entities = [MetWordEntity::class, ScoreEntity::class], version = 1, exportSchema = false)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun metWordDao(): MetWordDao
    abstract fun scoreDao(): ScoreDao

    companion object {
        fun build(context: Context): UserDataDatabase =
            Room.databaseBuilder(context, UserDataDatabase::class.java, "user_data.db").build()
    }
}
