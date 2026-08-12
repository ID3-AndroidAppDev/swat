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
 * "Today's picks" widget: surfaces the cheapest, lightest (fewest kcal) and
 * highest-protein dish of the day — a quick decision helper without opening
 * the app.
 */
class HighlightsWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE")

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val pending = goAsync()
        scope.launch {
            val views = RemoteViews(context.packageName, R.layout.widget_highlights)
            views.setOnClickPendingIntent(R.id.widget_root, WidgetCommon.openAppIntent(context, 3))
            try {
                val today = WidgetCommon.fetchToday(context, widgetId)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "${WidgetCommon.campusLabel(context, today.campus)} · ${LocalDate.now().format(dateFmt)}"
                )
                bind(context, views, today.menu, today.failed)
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_pick_cheap, context.getString(R.string.error_load_menu))
                views.setTextViewText(R.id.widget_pick_light, "")
                views.setTextViewText(R.id.widget_pick_protein, "")
            } finally {
                manager.updateAppWidget(widgetId, views)
                pending.finish()
            }
        }
    }

    private fun bind(context: Context, views: RemoteViews, menu: DailyMenu?, failed: Boolean) {
        if (failed || menu == null || !menu.isOpen) {
            val msg = if (menu != null && !menu.isOpen)
                context.getString(R.string.restaurant_closed)
            else context.getString(R.string.error_load_menu)
            views.setTextViewText(R.id.widget_pick_cheap, msg)
            views.setTextViewText(R.id.widget_pick_light, "")
            views.setTextViewText(R.id.widget_pick_protein, "")
            return
        }
        val cheap = WidgetCommon.cheapest(menu)
        val light = WidgetCommon.lowestKcal(menu)
        val protein = WidgetCommon.highestProtein(menu)

        views.setTextViewText(
            R.id.widget_pick_cheap,
            cheap?.let { "💰 ${it.name}  ¥${it.basePrice}" }
                ?: context.getString(R.string.widget_pick_none)
        )
        views.setTextViewText(
            R.id.widget_pick_light,
            light?.let { "🥗 ${it.name}  ${it.nutrition?.kcal?.toInt()} kcal" }
                ?: context.getString(R.string.widget_pick_none)
        )
        views.setTextViewText(
            R.id.widget_pick_protein,
            protein?.let { "💪 ${it.name}  ${"%.1f".format(it.nutrition?.protein)} g" }
                ?: context.getString(R.string.widget_pick_none)
        )
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetConfigStore.remove(context, it) }
    }

    companion object {
        fun refreshAll(context: Context) =
            WidgetCommon.refresh(context, HighlightsWidgetProvider::class.java)
    }
}
