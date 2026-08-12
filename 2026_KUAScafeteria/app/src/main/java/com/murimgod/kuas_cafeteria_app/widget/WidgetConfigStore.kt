package com.murimgod.kuas_cafeteria_app.widget

import android.content.Context

/**
 * Per-widget campus override chosen in the placement config screen. A null/absent
 * value means "follow the app's default campus".
 */
object WidgetConfigStore {

    private const val PREFS = "widget_prefs"
    private fun key(id: Int) = "campus_$id"

    fun setCampus(context: Context, widgetId: Int, campus: String?) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (campus == null) sp.remove(key(widgetId)) else sp.putString(key(widgetId), campus)
        sp.apply()
    }

    /** Returns the override campus, or null to follow the app default. */
    fun getCampus(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)

    fun remove(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(widgetId)).apply()
    }
}
