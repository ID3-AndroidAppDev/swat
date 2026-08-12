package com.example.kotobadrop.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// `version` must be bumped whenever assets/words.db's *content* changes (a word data
// pipeline re-run), not just its schema — createFromAsset() only ever copies the bundled
// file into place once, on first install; without a version bump + destructive fallback,
// an app update ships a corrected words.db that existing installs never actually pick up.
// Safe to wipe-and-recopy on mismatch: this table is entirely prepopulated reference data.
// Deliberately holds ONLY WordEntity — met-words/scores (Step 6) live in the separate
// UserDataDatabase instead, specifically so this file's destructive-migration-on-version-
// bump (which re-copies the whole physical file from assets) can never wipe player progress
// by sharing a file with it. See UserDataDatabase's doc comment for the full reasoning.
@Database(entities = [WordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "words.db")
                .createFromAsset("words.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
