package com.simbridge.host.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("simbridge_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var deviceId: Int
        get() = prefs.getInt(KEY_DEVICE_ID, -1)
        set(value) = prefs.edit().putInt(KEY_DEVICE_ID, value).apply()

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    val isLoggedIn: Boolean
        get() = token.isNotBlank() && serverUrl.isNotBlank()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
