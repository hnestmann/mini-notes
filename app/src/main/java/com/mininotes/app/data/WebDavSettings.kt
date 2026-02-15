package com.mininotes.app.data

import android.content.Context
import android.content.SharedPreferences

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

class WebDavSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "webdav_settings",
        Context.MODE_PRIVATE
    )

    fun getConfig(): WebDavConfig? {
        val url = prefs.getString(KEY_URL, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        return if (url.isNotBlank()) WebDavConfig(url, username, password) else null
    }

    fun saveConfig(config: WebDavConfig) {
        prefs.edit()
            .putString(KEY_URL, config.url.trim().trimEnd('/'))
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    fun clearConfig() {
        prefs.edit().remove(KEY_URL).remove(KEY_USERNAME).remove(KEY_PASSWORD).apply()
    }

    companion object {
        private const val KEY_URL = "webdav_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"
    }
}
