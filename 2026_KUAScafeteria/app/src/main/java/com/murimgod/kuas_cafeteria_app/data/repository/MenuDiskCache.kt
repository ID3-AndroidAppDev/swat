package com.murimgod.kuas_cafeteria_app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.murimgod.kuas_cafeteria_app.data.model.WeekMenuResponse
import java.io.File
import java.security.MessageDigest

/**
 * Last-good week menus persisted to disk so the app works offline and survives
 * process death (the in-memory caches don't). Plain JSON files under
 * `filesDir/menu_cache`, keyed by a hash of the request key. Best-effort: any
 * IO error is swallowed and treated as a cache miss.
 */
class MenuDiskCache(context: Context) {

    private val dir: File = File(context.applicationContext.filesDir, "menu_cache").apply { mkdirs() }
    private val gson = Gson()

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.json")
    }

    fun put(key: String, value: WeekMenuResponse) {
        runCatching { fileFor(key).writeText(gson.toJson(value)) }
    }

    fun get(key: String): WeekMenuResponse? = runCatching {
        val f = fileFor(key)
        if (!f.exists()) return null
        gson.fromJson(f.readText(), WeekMenuResponse::class.java)
    }.getOrNull()

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
