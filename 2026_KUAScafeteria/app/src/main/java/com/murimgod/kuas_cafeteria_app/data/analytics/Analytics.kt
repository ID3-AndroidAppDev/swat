package com.murimgod.kuas_cafeteria_app.data.analytics

import android.content.Context
import com.murimgod.kuas_cafeteria_app.BuildConfig
import com.murimgod.kuas_cafeteria_app.data.api.RetrofitClient
import com.murimgod.kuas_cafeteria_app.data.model.CrashEvent
import com.murimgod.kuas_cafeteria_app.data.model.OnboardingEvent
import com.murimgod.kuas_cafeteria_app.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Self-hosted, fire-and-forget telemetry — no Firebase/Google deps.
 *
 *  - [sendOnboarding]: one row after the first launch survey (or Skip defaults).
 *  - [installCrashHandler]: posts uncaught exceptions to the backend before the
 *    process dies, then defers to the previous handler so the normal crash flow
 *    (and the OS dialog) still happens.
 *
 * Every call swallows its own failure: telemetry must never affect the user.
 */
object Analytics {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendOnboarding(context: Context, campus: String, lang: String, skipped: Boolean) {
        val prefs = UserPreferences(context.applicationContext)
        scope.launch {
            runCatching {
                RetrofitClient.api.postOnboarding(
                    OnboardingEvent(
                        campus = campus,
                        lang = lang,
                        skipped = skipped,
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceId = prefs.getOrCreateDeviceId()
                    )
                )
            }
        }
    }

    fun installCrashHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val deviceId = runBlocking {
                    UserPreferences(appContext).getOrCreateDeviceId()
                }
                // Synchronous on the dying thread — best-effort, short timeout
                // is enforced by OkHttp; we're inside the crash path already.
                runBlocking {
                    runCatching {
                        RetrofitClient.api.postCrash(
                            CrashEvent(
                                message = throwable.message ?: throwable.javaClass.name,
                                stacktrace = sw.toString().take(20000),
                                appVersion = BuildConfig.VERSION_NAME,
                                deviceId = deviceId
                            )
                        )
                    }
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
