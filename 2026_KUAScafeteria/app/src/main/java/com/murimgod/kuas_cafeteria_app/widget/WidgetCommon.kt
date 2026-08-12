package com.murimgod.kuas_cafeteria_app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.murimgod.kuas_cafeteria_app.MainActivity
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.api.RetrofitClient
import com.murimgod.kuas_cafeteria_app.data.model.DailyMenu
import com.murimgod.kuas_cafeteria_app.data.model.MenuItem
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import kotlinx.coroutines.flow.first

/** Shared fetch + formatting helpers for all home-screen widgets. */
object WidgetCommon {

    data class Today(val campus: String, val menu: DailyMenu?, val failed: Boolean)

    suspend fun fetchToday(context: Context, widgetId: Int): Today {
        val prefs = UserPreferences(context.applicationContext)
        // Per-widget override (set at placement) wins over the app default.
        val campus = WidgetConfigStore.getCampus(context, widgetId) ?: prefs.campusFlow.first()
        return try {
            val lang = prefs.langFlow.first()
            Today(campus, RetrofitClient.api.getTodayMenu(campus, lang, null), failed = false)
        } catch (_: Exception) {
            Today(campus, null, failed = true)
        }
    }

    fun campusLabel(context: Context, campus: String): String =
        if (campus == "uzumasa") context.getString(R.string.campus_uzumasa)
        else context.getString(R.string.campus_kameoka)

    fun allItems(menu: DailyMenu): List<MenuItem> = menu.sections.flatMap { it.items }

    fun sectionEmoji(kind: String): String = when (kind) {
        "campus_lunch", "set" -> "🍴"
        "curry" -> "🍛"
        "ramen", "udon_soba" -> "🍜"
        "rice_bowl" -> "🍚"
        "salad" -> "🥗"
        "side", "a_la_carte" -> "🍽️"
        "live_kitchen" -> "👨‍🍳"
        else -> "🍴"
    }

    /** "¥350–¥520" across all priced dishes, or null. */
    fun priceRange(menu: DailyMenu): String? {
        val prices = allItems(menu).mapNotNull { it.basePrice }
        if (prices.isEmpty()) return null
        val lo = prices.min(); val hi = prices.max()
        return if (lo == hi) "¥$lo" else "¥$lo–¥$hi"
    }

    fun hoursLabel(menu: DailyMenu): String? {
        val h = menu.hours ?: return null
        return if (h.open != null && h.close != null) "${h.open}–${h.close}" else null
    }

    fun cheapest(menu: DailyMenu): MenuItem? =
        allItems(menu).filter { it.basePrice != null }.minByOrNull { it.basePrice!! }

    fun lowestKcal(menu: DailyMenu): MenuItem? =
        allItems(menu).filter { it.nutrition?.kcal != null }.minByOrNull { it.nutrition!!.kcal!! }

    fun highestProtein(menu: DailyMenu): MenuItem? =
        allItems(menu).filter { it.nutrition?.protein != null }.maxByOrNull { it.nutrition!!.protein!! }

    fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Broadcast an update to every placed widget of [provider]. */
    fun refresh(context: Context, provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isNotEmpty()) {
            context.sendBroadcast(
                Intent(context, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
