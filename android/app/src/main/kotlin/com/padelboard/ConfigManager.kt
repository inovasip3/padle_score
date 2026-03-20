package com.padelboard

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent configuration via SharedPreferences.
 * Stores team names, server port, PIN, and color preferences.
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("padel_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TEAM_A_NAME = "team_a_name"
        private const val KEY_TEAM_B_NAME = "team_b_name"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_PIN = "pin"
        private const val KEY_COLOR_A = "color_a"
        private const val KEY_COLOR_B = "color_b"

        const val DEFAULT_TEAM_A = "TEAM A"
        const val DEFAULT_TEAM_B = "TEAM B"
        const val DEFAULT_PORT = 8888
        const val DEFAULT_PIN = "1234"
        const val DEFAULT_COLOR_A = 0xFF00FF66.toInt() // Green
        const val DEFAULT_COLOR_B = 0xFFFFA500.toInt() // Amber
    }

    var teamAName: String
        get() = prefs.getString(KEY_TEAM_A_NAME, DEFAULT_TEAM_A) ?: DEFAULT_TEAM_A
        set(value) = prefs.edit().putString(KEY_TEAM_A_NAME, value).apply()

    var teamBName: String
        get() = prefs.getString(KEY_TEAM_B_NAME, DEFAULT_TEAM_B) ?: DEFAULT_TEAM_B
        set(value) = prefs.edit().putString(KEY_TEAM_B_NAME, value).apply()

    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var colorA: Int
        get() = prefs.getInt(KEY_COLOR_A, DEFAULT_COLOR_A)
        set(value) = prefs.edit().putInt(KEY_COLOR_A, value).apply()

    var colorB: Int
        get() = prefs.getInt(KEY_COLOR_B, DEFAULT_COLOR_B)
        set(value) = prefs.edit().putInt(KEY_COLOR_B, value).apply()
}
