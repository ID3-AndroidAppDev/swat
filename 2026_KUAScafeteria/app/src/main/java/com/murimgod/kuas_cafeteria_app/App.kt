package com.murimgod.kuas_cafeteria_app

import android.app.Application
import com.murimgod.kuas_cafeteria_app.data.analytics.Analytics
import com.murimgod.kuas_cafeteria_app.data.api.RetrofitClient
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import com.murimgod.kuas_cafeteria_app.ui.ThemeManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply the saved light/dark/system theme before any Activity inflates.
        ThemeManager.apply(UserPreferences(this).themeBlocking())
        // Give the HTTP client a disk cache so repeated GETs (menus, weeks,
        // allergens) are served locally for 5 min instead of hitting the server.
        RetrofitClient.init(this)
        // Report uncaught exceptions to our own backend (no Firebase).
        Analytics.installCrashHandler(this)
    }
}
