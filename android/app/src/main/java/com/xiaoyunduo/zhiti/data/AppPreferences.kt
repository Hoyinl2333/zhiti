package com.xiaoyunduo.zhiti.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("zhiti", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }

    var questionsPerSet: Int
        get() = prefs.getInt("questions_per_set", 5).takeIf { it in setOf(5, 10, 15, 20) } ?: 5
        set(value) {
            require(value in setOf(5, 10, 15, 20))
            prefs.edit().putInt("questions_per_set", value).apply()
        }

    var currentSession: SessionSnapshot?
        get() = prefs.getString("session", null)?.let { runCatching { json.decodeFromString<SessionSnapshot>(it) }.getOrNull() }
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("session") else editor.putString("session", json.encodeToString(value))
            editor.apply()
        }

    fun setInstalled(packId: String, version: String) {
        prefs.edit().putString("installed_$packId", version).apply()
    }

    fun installedVersion(packId: String): String? = prefs.getString("installed_$packId", null)
}

