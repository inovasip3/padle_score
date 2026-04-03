package com.padelboard

import android.content.Context
import android.content.SharedPreferences

/**
 * V2.0 - Master ConfigManager.
 * Manages all persistent configuration via SharedPreferences.
 * Stores: team names, server settings, colors, fonts, sound, effects,
 * scoring presets, BLE key bindings, and advanced JSON theme.
 */
class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("padel_config_v2", Context.MODE_PRIVATE)

    companion object {
        // --- Team ---
        private const val KEY_TEAM_A_NAME = "team_a_name"
        private const val KEY_TEAM_B_NAME = "team_b_name"
        // --- Server ---
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_PIN = "pin"
        private const val KEY_ENABLE_HTTP = "enable_http_server"
        private const val KEY_ENABLE_BLE_HID = "enable_ble_hid"
        // --- Appearance ---
        private const val KEY_COLOR_A = "color_a"
        private const val KEY_COLOR_B = "color_b"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_FONT_TYPEFACE = "font_typeface"
        private const val KEY_ENABLE_WIN_EFFECT = "enable_win_effect"
        private const val KEY_CUSTOM_THEME_JSON = "custom_theme_json"
        // --- Audio ---
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        // --- Scoring Preset ---
        private const val KEY_SCORING_PRESET = "scoring_preset"
        private const val KEY_SCORING_MODE = "scoring_mode"         // "standard" | "custom"
        private const val KEY_CUSTOM_INCREMENT = "custom_increment"
        private const val KEY_MAX_POINTS_WIN = "max_points_win"
        private const val KEY_WIN_BY_TWO = "win_by_two"
        // --- BLE Key Bindings ---
        private const val KEY_KB_TEAM_A_PLUS = "kb_team_a_plus"
        private const val KEY_KB_TEAM_A_MINUS = "kb_team_a_minus"
        private const val KEY_KB_TEAM_B_PLUS = "kb_team_b_plus"
        private const val KEY_KB_TEAM_B_MINUS = "kb_team_b_minus"
        private const val KEY_KB_RESET = "kb_reset"

        // --- Defaults ---
        const val DEFAULT_TEAM_A = "TEAM A"
        const val DEFAULT_TEAM_B = "TEAM B"
        const val DEFAULT_PORT = 8888
        const val DEFAULT_PIN = "1234"
        const val DEFAULT_COLOR_A = 0xFF00FF66.toInt()
        const val DEFAULT_COLOR_B = 0xFFFFA500.toInt()
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_FONT_TYPEFACE = "monospace"
        const val DEFAULT_ENABLE_WIN_EFFECT = true
        const val DEFAULT_ENABLE_HTTP = true
        const val DEFAULT_ENABLE_BLE_HID = true
        const val DEFAULT_SCORING_PRESET = "standard"
        const val DEFAULT_SCORING_MODE = "standard"
        const val DEFAULT_CUSTOM_INCREMENT = 1
        const val DEFAULT_MAX_POINTS = 11
        const val DEFAULT_WIN_BY_TWO = true
        const val DEFAULT_KB_A_PLUS = "a"
        const val DEFAULT_KB_A_MINUS = "s"
        const val DEFAULT_KB_B_PLUS = "b"
        const val DEFAULT_KB_B_MINUS = "d"
        const val DEFAULT_KB_RESET = "r"

        // Preset values table — [increment, maxPoints, winByTwo]
        val PRESET_STANDARD = Triple(15, 40, false)
        val PRESET_AMERICAN_11 = Triple(1, 11, true)
        val PRESET_AMERICAN_21 = Triple(1, 21, true)
    }

    // --- Team ---
    var teamAName: String
        get() = prefs.getString(KEY_TEAM_A_NAME, DEFAULT_TEAM_A) ?: DEFAULT_TEAM_A
        set(value) = prefs.edit().putString(KEY_TEAM_A_NAME, value).apply()

    var teamBName: String
        get() = prefs.getString(KEY_TEAM_B_NAME, DEFAULT_TEAM_B) ?: DEFAULT_TEAM_B
        set(value) = prefs.edit().putString(KEY_TEAM_B_NAME, value).apply()

    // --- Server ---
    var serverPort: Int
        get() = prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_SERVER_PORT, value).apply()

    var pin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var enableHttpServer: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_HTTP, DEFAULT_ENABLE_HTTP)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_HTTP, value).apply()

    var enableBleHid: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_BLE_HID, DEFAULT_ENABLE_BLE_HID)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_BLE_HID, value).apply()

    // --- Appearance ---
    var colorA: Int
        get() = prefs.getInt(KEY_COLOR_A, DEFAULT_COLOR_A)
        set(value) = prefs.edit().putInt(KEY_COLOR_A, value).apply()

    var colorB: Int
        get() = prefs.getInt(KEY_COLOR_B, DEFAULT_COLOR_B)
        set(value) = prefs.edit().putInt(KEY_COLOR_B, value).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    var fontTypeface: String
        get() = prefs.getString(KEY_FONT_TYPEFACE, DEFAULT_FONT_TYPEFACE) ?: DEFAULT_FONT_TYPEFACE
        set(value) = prefs.edit().putString(KEY_FONT_TYPEFACE, value).apply()

    var enableWinEffect: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_WIN_EFFECT, DEFAULT_ENABLE_WIN_EFFECT)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_WIN_EFFECT, value).apply()

    /**
     * JSON string for advanced custom theming.
     * Supports keys: bgColor, colorA, colorB, fontScale, fontTypeface, etc.
     * If empty or invalid, defaults are used.
     */
    var customThemeJson: String
        get() = prefs.getString(KEY_CUSTOM_THEME_JSON, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_THEME_JSON, value).apply()

    // --- Audio ---
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    // --- Scoring ---
    /** "standard", "american11", "american21", "custom" */
    var scoringPreset: String
        get() = prefs.getString(KEY_SCORING_PRESET, DEFAULT_SCORING_PRESET) ?: DEFAULT_SCORING_PRESET
        set(value) = prefs.edit().putString(KEY_SCORING_PRESET, value).apply()

    /** "standard" or "custom" */
    var scoringMode: String
        get() = prefs.getString(KEY_SCORING_MODE, DEFAULT_SCORING_MODE) ?: DEFAULT_SCORING_MODE
        set(value) = prefs.edit().putString(KEY_SCORING_MODE, value).apply()

    var customIncrement: Int
        get() = prefs.getInt(KEY_CUSTOM_INCREMENT, DEFAULT_CUSTOM_INCREMENT)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_INCREMENT, value).apply()

    var maxPointsToWin: Int
        get() = prefs.getInt(KEY_MAX_POINTS_WIN, DEFAULT_MAX_POINTS)
        set(value) = prefs.edit().putInt(KEY_MAX_POINTS_WIN, value).apply()

    var winByTwo: Boolean
        get() = prefs.getBoolean(KEY_WIN_BY_TWO, DEFAULT_WIN_BY_TWO)
        set(value) = prefs.edit().putBoolean(KEY_WIN_BY_TWO, value).apply()

    /** Applies a preset template quickly, overwriting increment/max/winByTwo values. */
    fun applyPreset(preset: String) {
        scoringPreset = preset
        when (preset) {
            "standard" -> {
                scoringMode = "standard"
                customIncrement = 15
                maxPointsToWin = 40
                winByTwo = false
            }
            "american11" -> {
                scoringMode = "custom"
                customIncrement = 1
                maxPointsToWin = 11
                winByTwo = true
            }
            "american21" -> {
                scoringMode = "custom"
                customIncrement = 1
                maxPointsToWin = 21
                winByTwo = true
            }
            "custom" -> {
                scoringMode = "custom"
                // Keep existing custom values untouched
            }
        }
    }

    // --- BLE Key Bindings (single-char lowercase keys) ---
    var keyTeamAPlus: String
        get() = prefs.getString(KEY_KB_TEAM_A_PLUS, DEFAULT_KB_A_PLUS) ?: DEFAULT_KB_A_PLUS
        set(value) = prefs.edit().putString(KEY_KB_TEAM_A_PLUS, value.take(1).lowercase()).apply()

    var keyTeamAMinus: String
        get() = prefs.getString(KEY_KB_TEAM_A_MINUS, DEFAULT_KB_A_MINUS) ?: DEFAULT_KB_A_MINUS
        set(value) = prefs.edit().putString(KEY_KB_TEAM_A_MINUS, value.take(1).lowercase()).apply()

    var keyTeamBPlus: String
        get() = prefs.getString(KEY_KB_TEAM_B_PLUS, DEFAULT_KB_B_PLUS) ?: DEFAULT_KB_B_PLUS
        set(value) = prefs.edit().putString(KEY_KB_TEAM_B_PLUS, value.take(1).lowercase()).apply()

    var keyTeamBMinus: String
        get() = prefs.getString(KEY_KB_TEAM_B_MINUS, DEFAULT_KB_B_MINUS) ?: DEFAULT_KB_B_MINUS
        set(value) = prefs.edit().putString(KEY_KB_TEAM_B_MINUS, value.take(1).lowercase()).apply()

    var keyReset: String
        get() = prefs.getString(KEY_KB_RESET, DEFAULT_KB_RESET) ?: DEFAULT_KB_RESET
        set(value) = prefs.edit().putString(KEY_KB_RESET, value.take(1).lowercase()).apply()
}
