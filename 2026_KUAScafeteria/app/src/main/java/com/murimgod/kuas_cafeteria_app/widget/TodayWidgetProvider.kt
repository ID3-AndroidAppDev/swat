package com.murimgod.kuas_cafeteria_app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.model.DailyMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Full today's-menu widget: campus + date, a list of dishes (section emoji +
 * price + kcal per line) and a footer with dish count, price range and hours.
 * Refreshes hourly + on broadcast; tap opens the app. Fetch runs off-main
 * via [goAsync].
 */
class TodayWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val loading = RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_subtitle, context.getString(R.string.loading))
            setOnClickPendingIntent(R.id.widget_root, WidgetCommon.openAppIntent(context, 1))
        }
        manager.updateAppWidget(widgetId, loading)

        val pending = goAsync()
        scope.launch {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setOnClickPendingIntent(R.id.widget_root, WidgetCommon.openAppIntent(context, 1))
            try {
                val today = WidgetCommon.fetchToday(context, widgetId)
                views.setTextViewText(R.id.widget_title, WidgetCommon.campusLabel(context, today.campus))
                views.setTextViewText(R.id.widget_subtitle, LocalDate.now().format(dateFmt))
                bindBody(context, views, today.menu, today.failed)
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_body, context.getString(R.string.error_load_menu))
                views.setTextViewText(R.id.widget_footer, "")
            } finally {
                manager.updateAppWidget(widgetId, views)
                pending.finish()
            }
        }
    }

    private fun bindBody(context: Context, views: RemoteViews, menu: DailyMenu?, failed: Boolean) {
        if (failed || menu == null) {
            views.setTextViewText(R.id.widget_body, context.getString(R.string.error_load_menu))
            views.setTextViewText(R.id.widget_footer, "")
            return
        }
        if (!menu.isOpen) {
            views.setTextViewText(R.id.widget_body, context.getString(R.string.restaurant_closed))
            views.setTextViewText(R.id.widget_footer, "")
            return
        }
        val lines = menu.sections.flatMap { sec ->
            sec.items.map { item ->
                val emoji = WidgetCommon.sectionEmoji(sec.kind)
                val price = item.basePrice?.let { "  ¥$it" } ?: ""
                val kcal = item.nutrition?.kcal?.let { " · ${it.toInt()} kcal" } ?: ""
                "$emoji ${item.name}$price$kcal"
            }
        }.take(6)

        views.setTextViewText(
            R.id.widget_body,
            if (lines.isEmpty()) context.getString(R.string.no_menu_for_day)
            else lines.joinToString("\n")
        )

        val count = WidgetCommon.allItems(menu).size
        val parts = mutableListOf("🍽️ $count")
        WidgetCommon.priceRange(menu)?.let { parts.add("💰 $it") }
        WidgetCommon.hoursLabel(menu)?.let { parts.add("🕒 $it") }
        views.setTextViewText(R.id.widget_footer, parts.joinToString("   "))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetConfigStore.remove(context, it) }
    }

    companion object {
        fun refreshAll(context: Context) =
            WidgetCommon.refresh(context, TodayWidgetProvider::class.java)
    }
}
