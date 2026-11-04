package com.tolt

import android.content.SharedPreferences
import java.io.File
import java.io.IOException

internal interface Store {
    fun getLong(key: String): Long?

    fun putLong(key: String, value: Long)
}

internal class SharedPreferencesStore(private val preferences: SharedPreferences) : Store {
    override fun getLong(key: String): Long? = if (preferences.contains(key)) preferences.getLong(key, 0) else null

    override fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }
}

internal class FileStore(private val directory: File) : Store {
    override fun getLong(key: String): Long? =
        try {
            File(directory, key).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
        } catch (_: IOException) {
            null
        }

    override fun putLong(key: String, value: Long) {
        try {
            directory.mkdirs()
            File(directory, key).writeText(value.toString())
        } catch (_: IOException) {
        }
    }
}

internal class FirstSeenStore(private val store: Store) {
    fun firstSeenAt(now: Long): Long = store.getLong(KEY) ?: now.also { store.putLong(KEY, it) }

    companion object {
        const val KEY = "com.tolt.first_seen_at"
    }
}
