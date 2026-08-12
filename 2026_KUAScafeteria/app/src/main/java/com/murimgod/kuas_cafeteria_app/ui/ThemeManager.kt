package com.murimgod.kuas_cafeteria_app.ui

import androidx.appcompat.app.AppCompatDelegate

/** Maps the persisted theme preference to an AppCompat night mode and applies it. */
object ThemeManager {

    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    val options = listOf(SYSTEM, LIGHT, DARK)

    fun apply(theme: String) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
