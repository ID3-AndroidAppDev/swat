package com.murimgod.kuas_cafeteria_app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.murimgod.kuas_cafeteria_app.databinding.ActivityWidgetConfigBinding

/**
 * Shown when a widget is dropped on the home screen (#20). Lets the user pin the
 * widget to a specific campus, or follow the app's default. Result must be
 * RESULT_OK with the widget id or the launcher discards the widget.
 */
class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUzumasa.setOnClickListener { save("uzumasa") }
        binding.btnKameoka.setOnClickListener { save("kameoka") }
        binding.btnFollow.setOnClickListener { save(null) }
    }

    private fun save(campus: String?) {
        WidgetConfigStore.setCampus(this, widgetId, campus)
        // The newly placed widget belongs to exactly one provider; refresh all so
        // whichever it is re-renders with the chosen campus.
        TodayWidgetProvider.refreshAll(this)
        HighlightsWidgetProvider.refreshAll(this)
        StatusWidgetProvider.refreshAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
