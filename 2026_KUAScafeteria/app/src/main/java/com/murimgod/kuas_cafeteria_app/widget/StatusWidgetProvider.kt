package com.murimgod.kuas_cafeteria_app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.CafeteriaStatus
import com.murimgod.kuas_cafeteria_app.data.model.DailyMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Open/closed status widget with a small live countdown. When the cafeteria is
 * within 10 minutes of opening or closing, a self-ticking [android.widget.Chronometer]
 * shows the remaining MM:SS — no per-second widget updates needed. An inexact
 * alarm wakes the widget at each boundary (open−10m / open / close−10m / close)
 * so the countdown appears/flips on time.
 */
class StatusWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFmt = DateTimeFormatter.ofPattern("EEE")

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> render(context, manager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val pending = goAsync()
        scope.launch {
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setOnClickPendingIntent(R.id.widget_root, WidgetCommon.openAppIntent(context, 4))
            try {
                val today = WidgetCommon.fetchToday(context, widgetId)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    "${WidgetCommon.campusLabel(context, today.campus)} · ${LocalDate.now().format(dateFmt)}"
                )
                bind(context, views, today.menu)
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_status_big, context.getString(R.string.error_load_menu))
                views.setViewVisibility(R.id.widget_cd_row, android.view.View.GONE)
                views.setTextViewText(R.id.widget_status_sub, "")
            } finally {
                manager.updateAppWidget(widgetId, views)
                pending.finish()
            }
        }
    }

    private fun bind(context: Context, views: RemoteViews, menu: DailyMenu?) {
        views.setViewVisibility(R.id.widget_cd_row, android.view.View.GONE)
        val hours = menu?.hours
        if (menu == null || !menu.isOpen || hours?.open == null || hours.close == null) {
            views.setTextViewText(R.id.widget_status_big, context.getString(R.string.status_closed))
            views.setTextViewText(R.id.widget_status_sub, "")
            return
        }

        val status = CafeteriaStatus.compute(hours)
        val now = System.currentTimeMillis()
        val ms = status.msUntilNext(now)
        val soon = ms != null && ms in 1..CafeteriaStatus.COUNTDOWN_WINDOW_MS

        when (status.phase) {
            CafeteriaStatus.Phase.BEFORE_OPEN -> {
                views.setTextViewText(
                    R.id.widget_status_big,
                    context.getString(if (soon) R.string.status_opening_soon else R.string.status_closed)
                )
                views.setTextViewText(R.id.widget_status_sub, context.getString(R.string.status_opens_at, hours.open))
                if (soon) startCountdown(context, views, ms!!, R.string.cd_opens_label)
            }
            CafeteriaStatus.Phase.OPEN -> {
                views.setTextViewText(R.id.widget_status_big, context.getString(R.string.status_open))
                views.setTextViewText(R.id.widget_status_sub, context.getString(R.string.status_until, hours.close))
                if (soon) startCountdown(context, views, ms!!, R.string.cd_closes_label)
            }
            else -> {
                views.setTextViewText(R.id.widget_status_big, context.getString(R.string.status_closed))
                views.setTextViewText(R.id.widget_status_sub, "")
            }
        }
        scheduleBoundaryRefresh(context, hours)
    }

    private fun startCountdown(context: Context, views: RemoteViews, ms: Long, labelRes: Int) {
        views.setViewVisibility(R.id.widget_cd_row, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_cd_label, context.getString(labelRes))
        views.setChronometerCountDown(R.id.widget_chrono, true)
        views.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime() + ms, null, true)
    }

    /**
     * Wake the widget at the next boundary: 10 min before the next event (to show
     * the countdown) and at the event itself (to flip phase). After firing, the
     * re-render schedules the following boundary.
     */
    private fun scheduleBoundaryRefresh(context: Context, hours: com.murimgod.kuas_cafeteria_app.data.model.Hours) {
        val now = System.currentTimeMillis()
        val nextEvent = CafeteriaStatus.compute(hours).nextEventMs ?: return
        val next = listOf(nextEvent - CafeteriaStatus.COUNTDOWN_WINDOW_MS, nextEvent)
            .filter { it > now }
            .minOrNull() ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC, next, updatePendingIntent(context))
    }

    private fun updatePendingIntent(context: Context): PendingIntent {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, StatusWidgetProvider::class.java))
        val intent = Intent(context, StatusWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        return PendingIntent.getBroadcast(
            context, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetConfigStore.remove(context, it) }
    }

    companion object {
        fun refreshAll(context: Context) =
            WidgetCommon.refresh(context, StatusWidgetProvider::class.java)
    }
}
