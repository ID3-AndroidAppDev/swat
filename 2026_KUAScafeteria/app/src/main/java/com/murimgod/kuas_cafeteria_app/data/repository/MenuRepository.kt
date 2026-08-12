package com.murimgod.kuas_cafeteria_app.data.repository

import android.content.Context
import com.murimgod.kuas_cafeteria_app.data.api.RetrofitClient
import com.murimgod.kuas_cafeteria_app.data.model.*
import java.util.Collections

/** A week menu plus whether it was served from the offline fallback. */
data class WeekResult(val response: WeekMenuResponse, val offline: Boolean)

class MenuRepository(context: Context) {

    private val api = RetrofitClient.api
    private val disk = MenuDiskCache(context)

    private data class Entry(val value: WeekMenuResponse, val ts: Long)

    // Bounded, TTL'd, access-ordered (LRU) in-memory cache. Previously this was
    // an unbounded Map with no expiry — it could grow forever and serve stale
    // current-week data until process death.
    private val weekCache: MutableMap<String, Entry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) =
                size > MAX_ENTRIES
        }
    )

    suspend fun getCampuses(lang: String = "en"): List<Campus> =
        api.getCampuses(lang)

    suspend fun getTodayMenu(campus: String, lang: String, excludeAllergens: String?): DailyMenu =
        api.getTodayMenu(campus, lang, excludeAllergens)

    suspend fun getMenuByDate(campus: String, date: String, lang: String, excludeAllergens: String?): DailyMenu =
        api.getMenuByDate(campus, date, lang, excludeAllergens)

    suspend fun getMenuByWeek(
        campus: String, week: String, lang: String, excludeAllergens: String?
    ): WeekResult {
        val key = "$campus-$week-$lang-${excludeAllergens ?: ""}"
        val now = System.currentTimeMillis()

        weekCache[key]?.let { if (now - it.ts < TTL_MS) return WeekResult(it.value, offline = false) }

        return try {
            val result = api.getMenuByWeek(campus, week, lang, excludeAllergens)
            weekCache[key] = Entry(result, now)
            disk.put(key, result)
            WeekResult(result, offline = false)
        } catch (e: Exception) {
            // Network failed — fall back to the last-good copy if we have one.
            val cached = weekCache[key]?.value ?: disk.get(key)
            if (cached != null) WeekResult(cached, offline = true) else throw e
        }
    }

    suspend fun getWeeks(campus: String): List<WeekSummary> =
        api.getWeeks(campus, 12).weeks.map { w ->
            WeekSummary(weekId = w.weekId, isoWeek = w.isoWeek, coverage = w.days)
        }

    suspend fun getAllergens(lang: String = "en"): List<AllergenInfo> =
        api.getAllergens(lang)

    suspend fun getItem(itemId: String, lang: String = "en"): MenuItem =
        api.getItem(itemId, lang)

    fun clearCache() {
        weekCache.clear()
        disk.clear()
    }

    companion object {
        private const val TTL_MS = 5 * 60 * 1000L  // 5 min, matches HTTP cache
        private const val MAX_ENTRIES = 32
    }
}
