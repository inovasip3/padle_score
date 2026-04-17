package com.padelboard

import android.content.Context
import android.content.SharedPreferences

/**
 * V2.0 - Master ConfigManager.
 * Manages all persistent configuration via SharedPreferences.
 * Stores: team names, server settings, colors, fonts, sound, effects,
 * scoring presets, BLE key bindings, and advanced JSON theme.
 */
class ConfigManager(val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("padel_config_v2", Context.MODE_PRIVATE)

    companion object {
        // --- Team ---
        const val KEY_TEAM_A_NAME = "team_a_name"
        const val KEY_TEAM_B_NAME = "team_b_name"
        // --- Server ---
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_PIN = "pin"
        const val KEY_ENABLE_HTTP = "enable_http_server"
        const val KEY_ENABLE_BLE_HID = "enable_ble_hid"
        const val KEY_ENABLE_SHUTTER_REMOTE = "enable_shutter_remote"
        const val KEY_SHOW_DEBUG_MSG = "show_debug_msg"

        // --- Bluetooth Remote Mode (V2.3) ---
        const val KEY_REMOTE_MODE = "remote_mode" // 1: Single, 2: Dual
        const val KEY_REMOTE_SWP_MOD2 = "remote_swp_mod2"
        const val KEY_REMOTE_A_DESC = "remote_a_desc"
        const val KEY_REMOTE_B_DESC = "remote_b_desc"
        const val KEY_REMOTE_LONG_PRESS_MS = "remote_long_press_ms"
        const val KEY_REMOTE_DUAL_PRESS_MS = "remote_dual_press_ms"
        const val KEY_REMOTE_RESET_ENABLED = "remote_reset_enabled"
        // --- Appearance ---
        const val KEY_COLOR_A = "color_a"
        const val KEY_COLOR_B = "color_b"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_FONT_TYPEFACE = "font_typeface"
        const val KEY_ENABLE_WIN_EFFECT = "enable_win_effect"
        const val KEY_CUSTOM_THEME_JSON = "custom_theme_json"
        // --- Audio ---
        const val KEY_ENABLE_VOICE_REF = "enable_voice_ref"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_USE_LOVE_FOR_ZERO = "use_love_for_zero"
        const val KEY_ENABLE_APPLAUSE = "enable_applause"
        
        // --- Photos ---
        const val KEY_ENABLE_PHOTOS = "enable_photos"
        const val KEY_PHOTO_SIZE = "photo_size"
        const val KEY_PHOTO_Y_POS = "photo_y_pos"
        const val KEY_PHOTO_X_POS_A = "photo_x_pos_a" // Percentage from centerAX
        const val KEY_PHOTO_X_POS_B = "photo_x_pos_b" // Percentage from centerBX
        const val KEY_ALLOW_USER_UPLOAD_PHOTO = "allow_user_upload_photo"
        // --- Scoring Preset ---
        const val KEY_SCORING_PRESET = "scoring_preset" // "standard", "golden_point", "fast_short", "custom_1", "custom_2", "custom_3"
        
        // --- Point Rule ---
        const val KEY_USE_GOLDEN_POINT = "use_golden_point"
        
        // --- Game Rule ---
        const val KEY_GAMES_TO_WIN_SET = "games_to_win_set"
        const val KEY_WIN_BY_2_GAMES = "win_by_2_games"
        
        // --- Tie Break ---
        const val KEY_USE_TIE_BREAK = "use_tie_break"
        const val KEY_TIE_BREAK_AT = "tie_break_at"
        const val KEY_TIE_BREAK_TARGET = "tie_break_target"
        const val KEY_TIE_BREAK_WIN_BY_2 = "tie_break_win_by_2"
        
        // --- Set Rule ---
        const val KEY_SETS_TO_WIN_MATCH = "sets_to_win_match"
        const val KEY_FINAL_SET_SUPER_TB = "final_set_super_tb"
        const val KEY_SUPER_TB_TARGET = "super_tb_target"

        // --- Custom Slots JSON ---
        const val KEY_CUSTOM_1_JSON = "custom_1_json"
        const val KEY_CUSTOM_2_JSON = "custom_2_json"
        const val KEY_CUSTOM_3_JSON = "custom_3_json"
        // --- BLE Key Bindings ---
        const val KEY_KB_TEAM_A_PLUS = "kb_team_a_plus"
        const val KEY_KB_TEAM_A_MINUS = "kb_team_a_minus"
        const val KEY_KB_TEAM_B_PLUS = "kb_team_b_plus"
        const val KEY_KB_TEAM_B_MINUS = "kb_team_b_minus"
        const val KEY_KB_RESET = "kb_reset"

        // --- Defaults ---
        const val DEFAULT_TEAM_A = "TEAM A"
        const val DEFAULT_TEAM_B = "TEAM B"
        const val DEFAULT_PORT = 8888
        const val DEFAULT_PIN = "1234"
        const val DEFAULT_COLOR_A = 0xFF00FF66.toInt()
        const val DEFAULT_COLOR_B = 0xFFFFA500.toInt()
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_USE_LOVE_FOR_ZERO = false
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_FONT_TYPEFACE = "monospace"
        const val DEFAULT_ENABLE_WIN_EFFECT = true
        const val DEFAULT_ENABLE_PHOTOS = false
        const val DEFAULT_PHOTO_SIZE = 25 // Percentage size
        const val DEFAULT_PHOTO_Y_POS = 35 // Percentage Y-position
        const val DEFAULT_PHOTO_X_POS_A = 0 // Relative to centerAX
        const val DEFAULT_PHOTO_X_POS_B = 0 // Relative to centerBX
        const val DEFAULT_ENABLE_VOICE_REF = true
        const val DEFAULT_ENABLE_HTTP = true
        const val DEFAULT_ENABLE_BLE_HID = true
        const val DEFAULT_ENABLE_SHUTTER_REMOTE = true
        const val DEFAULT_SCORING_PRESET = "standard"
        const val DEFAULT_ALLOW_USER_UPLOAD_PHOTO = false
        const val DEFAULT_SHOW_DEBUG_MSG = false

        const val DEFAULT_REMOTE_MODE = 1
        const val DEFAULT_REMOTE_SWP_MOD2 = false
        const val DEFAULT_REMOTE_A_DESC = ""
        const val DEFAULT_REMOTE_B_DESC = ""
        const val DEFAULT_REMOTE_LONG_PRESS_MS = 600
        const val DEFAULT_REMOTE_DUAL_PRESS_MS = 2000
        const val DEFAULT_REMOTE_RESET_ENABLED = true
        
        const val DEFAULT_USE_GOLDEN_POINT = false
        const val DEFAULT_GAMES_TO_WIN_SET = 6
        const val DEFAULT_WIN_BY_2_GAMES = true
        const val DEFAULT_USE_TIE_BREAK = true
        const val DEFAULT_TIE_BREAK_AT = 6
        const val DEFAULT_TIE_BREAK_TARGET = 7
        const val DEFAULT_TIE_BREAK_WIN_BY_2 = true
        const val DEFAULT_SETS_TO_WIN_MATCH = 2 // Best of 3 requires 2 sets to win
        const val DEFAULT_FINAL_SET_SUPER_TB = false
        const val DEFAULT_SUPER_TB_TARGET = 10
        const val DEFAULT_ENABLE_APPLAUSE = true
        const val DEFAULT_KB_A_PLUS = "a"
        const val DEFAULT_KB_A_MINUS = "s"
        const val DEFAULT_KB_B_PLUS = "b"
        const val DEFAULT_KB_B_MINUS = "d"
        const val DEFAULT_KB_RESET = "r"

    }

    /**
     * Perform multiple SharedPreferences writes atomically in a single disk operation.
     * Prevents 20 separate disk writes when saving all settings at once.
     * Usage: config.saveBatch { putString(KEY_X, val1).putInt(KEY_Y, val2) }
     */
    fun saveBatch(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
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

    var enableShutterRemote: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SHUTTER_REMOTE, DEFAULT_ENABLE_SHUTTER_REMOTE)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SHUTTER_REMOTE, value).apply()

    var showDebugMsg: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DEBUG_MSG, DEFAULT_SHOW_DEBUG_MSG)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DEBUG_MSG, value).apply()

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
    var enableVoiceRef: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_VOICE_REF, DEFAULT_ENABLE_VOICE_REF)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_VOICE_REF, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var useLoveForZero: Boolean
        get() = prefs.getBoolean(KEY_USE_LOVE_FOR_ZERO, DEFAULT_USE_LOVE_FOR_ZERO)
        set(value) = prefs.edit().putBoolean(KEY_USE_LOVE_FOR_ZERO, value).apply()

    var enableApplause: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_APPLAUSE, DEFAULT_ENABLE_APPLAUSE)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_APPLAUSE, value).apply()
        
    // --- Photos ---
    var enablePhotos: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_PHOTOS, DEFAULT_ENABLE_PHOTOS)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_PHOTOS, value).apply()
        
    var photoSize: Int
        get() = prefs.getInt(KEY_PHOTO_SIZE, DEFAULT_PHOTO_SIZE)
        set(value) = prefs.edit().putInt(KEY_PHOTO_SIZE, value).apply()
        
    var photoYPos: Int
        get() = prefs.getInt(KEY_PHOTO_Y_POS, DEFAULT_PHOTO_Y_POS)
        set(value) = prefs.edit().putInt(KEY_PHOTO_Y_POS, value).apply()

    var photoXPosA: Int
        get() = prefs.getInt(KEY_PHOTO_X_POS_A, DEFAULT_PHOTO_X_POS_A)
        set(value) = prefs.edit().putInt(KEY_PHOTO_X_POS_A, value).apply()

    var photoXPosB: Int
        get() = prefs.getInt(KEY_PHOTO_X_POS_B, DEFAULT_PHOTO_X_POS_B)
        set(value) = prefs.edit().putInt(KEY_PHOTO_X_POS_B, value).apply()

    var allowUserUploadPhoto: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_USER_UPLOAD_PHOTO, DEFAULT_ALLOW_USER_UPLOAD_PHOTO)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_USER_UPLOAD_PHOTO, value).apply()

    // --- Scoring Rules ---
    var scoringPreset: String
        get() = prefs.getString(KEY_SCORING_PRESET, DEFAULT_SCORING_PRESET) ?: DEFAULT_SCORING_PRESET
        set(value) = prefs.edit().putString(KEY_SCORING_PRESET, value).apply()

    var useGoldenPoint: Boolean
        get() = prefs.getBoolean(KEY_USE_GOLDEN_POINT, DEFAULT_USE_GOLDEN_POINT)
        set(value) = prefs.edit().putBoolean(KEY_USE_GOLDEN_POINT, value).apply()

    var gamesToWinSet: Int
        get() = prefs.getInt(KEY_GAMES_TO_WIN_SET, DEFAULT_GAMES_TO_WIN_SET)
        set(value) = prefs.edit().putInt(KEY_GAMES_TO_WIN_SET, value).apply()

    var winBy2Games: Boolean
        get() = prefs.getBoolean(KEY_WIN_BY_2_GAMES, DEFAULT_WIN_BY_2_GAMES)
        set(value) = prefs.edit().putBoolean(KEY_WIN_BY_2_GAMES, value).apply()

    var useTieBreak: Boolean
        get() = prefs.getBoolean(KEY_USE_TIE_BREAK, DEFAULT_USE_TIE_BREAK)
        set(value) = prefs.edit().putBoolean(KEY_USE_TIE_BREAK, value).apply()

    var tieBreakAt: Int
        get() = prefs.getInt(KEY_TIE_BREAK_AT, DEFAULT_TIE_BREAK_AT)
        set(value) = prefs.edit().putInt(KEY_TIE_BREAK_AT, value).apply()

    var tieBreakTarget: Int
        get() = prefs.getInt(KEY_TIE_BREAK_TARGET, DEFAULT_TIE_BREAK_TARGET)
        set(value) = prefs.edit().putInt(KEY_TIE_BREAK_TARGET, value).apply()

    var tieBreakWinBy2: Boolean
        get() = prefs.getBoolean(KEY_TIE_BREAK_WIN_BY_2, DEFAULT_TIE_BREAK_WIN_BY_2)
        set(value) = prefs.edit().putBoolean(KEY_TIE_BREAK_WIN_BY_2, value).apply()

    var setsToWinMatch: Int
        get() = prefs.getInt(KEY_SETS_TO_WIN_MATCH, DEFAULT_SETS_TO_WIN_MATCH)
        set(value) = prefs.edit().putInt(KEY_SETS_TO_WIN_MATCH, value).apply()

    var finalSetSuperTieBreak: Boolean
        get() = prefs.getBoolean(KEY_FINAL_SET_SUPER_TB, DEFAULT_FINAL_SET_SUPER_TB)
        set(value) = prefs.edit().putBoolean(KEY_FINAL_SET_SUPER_TB, value).apply()

    var superTieBreakTarget: Int
        get() = prefs.getInt(KEY_SUPER_TB_TARGET, DEFAULT_SUPER_TB_TARGET)
        set(value) = prefs.edit().putInt(KEY_SUPER_TB_TARGET, value).apply()

    fun getCustomSlotJson(slotNumber: Int): String {
        return when (slotNumber) {
            1 -> prefs.getString(KEY_CUSTOM_1_JSON, "") ?: ""
            2 -> prefs.getString(KEY_CUSTOM_2_JSON, "") ?: ""
            3 -> prefs.getString(KEY_CUSTOM_3_JSON, "") ?: ""
            else -> ""
        }
    }

    fun saveCustomSlotJson(slotNumber: Int, jsonString: String) {
        when (slotNumber) {
            1 -> prefs.edit().putString(KEY_CUSTOM_1_JSON, jsonString).apply()
            2 -> prefs.edit().putString(KEY_CUSTOM_2_JSON, jsonString).apply()
            3 -> prefs.edit().putString(KEY_CUSTOM_3_JSON, jsonString).apply()
        }
    }

    /** Applies a preset template quickly. */
    fun applyPreset(preset: String) {
        scoringPreset = preset
        when (preset) {
            "standard" -> {
                useGoldenPoint = false
                gamesToWinSet = 6
                winBy2Games = true
                useTieBreak = true
                tieBreakAt = 6
                tieBreakTarget = 7
                tieBreakWinBy2 = true
                setsToWinMatch = 2
                finalSetSuperTieBreak = false
            }
            "golden_point" -> {
                useGoldenPoint = true
                gamesToWinSet = 6
                winBy2Games = true
                useTieBreak = true
                tieBreakAt = 6
                tieBreakTarget = 7
                tieBreakWinBy2 = true
                setsToWinMatch = 2
                finalSetSuperTieBreak = false
            }
            "fast_short" -> {
                useGoldenPoint = true
                gamesToWinSet = 4
                winBy2Games = false
                useTieBreak = true
                tieBreakAt = 4
                tieBreakTarget = 5
                tieBreakWinBy2 = true // Standard tiebreaks usually win by 2, but the prompt didn't specify. Assuming true unless otherwise noted, but prompt says "winBy2 = false" for games.
                setsToWinMatch = 1 // Best of 1
                finalSetSuperTieBreak = false
            }
            "custom_1" -> loadCustomSlot(1)
            "custom_2" -> loadCustomSlot(2)
            "custom_3" -> loadCustomSlot(3)
        }
    }

    private fun loadCustomSlot(slotNumber: Int) {
        val jsonStr = getCustomSlotJson(slotNumber)
        if (jsonStr.isEmpty()) return // do nothing if empty
        try {
            val json = org.json.JSONObject(jsonStr)
            useGoldenPoint = json.optBoolean("useGoldenPoint", useGoldenPoint)
            gamesToWinSet = json.optInt("gamesToWinSet", gamesToWinSet)
            winBy2Games = json.optBoolean("winBy2Games", winBy2Games)
            useTieBreak = json.optBoolean("useTieBreak", useTieBreak)
            tieBreakAt = json.optInt("tieBreakAt", tieBreakAt)
            tieBreakTarget = json.optInt("tieBreakTarget", tieBreakTarget)
            tieBreakWinBy2 = json.optBoolean("tieBreakWinBy2", tieBreakWinBy2)
            setsToWinMatch = json.optInt("setsToWinMatch", setsToWinMatch)
            finalSetSuperTieBreak = json.optBoolean("finalSetSuperTieBreak", finalSetSuperTieBreak)
            superTieBreakTarget = json.optInt("superTieBreakTarget", superTieBreakTarget)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentSettingsAsJson(): String {
        return org.json.JSONObject().apply {
            put("useGoldenPoint", useGoldenPoint)
            put("gamesToWinSet", gamesToWinSet)
            put("winBy2Games", winBy2Games)
            put("useTieBreak", useTieBreak)
            put("tieBreakAt", tieBreakAt)
            put("tieBreakTarget", tieBreakTarget)
            put("tieBreakWinBy2", tieBreakWinBy2)
            put("setsToWinMatch", setsToWinMatch)
            put("finalSetSuperTieBreak", finalSetSuperTieBreak)
            put("superTieBreakTarget", superTieBreakTarget)
        }.toString()
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

    // --- Bluetooth Remote Mode Properties ---
    var remoteMode: Int
        get() = prefs.getInt(KEY_REMOTE_MODE, DEFAULT_REMOTE_MODE)
        set(value) = prefs.edit().putInt(KEY_REMOTE_MODE, value).apply()

    var remoteSwapMod2: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_SWP_MOD2, DEFAULT_REMOTE_SWP_MOD2)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_SWP_MOD2, value).apply()

    var remoteADesc: String
        get() = prefs.getString(KEY_REMOTE_A_DESC, DEFAULT_REMOTE_A_DESC) ?: DEFAULT_REMOTE_A_DESC
        set(value) = prefs.edit().putString(KEY_REMOTE_A_DESC, value).apply()

    var remoteBDesc: String
        get() = prefs.getString(KEY_REMOTE_B_DESC, DEFAULT_REMOTE_B_DESC) ?: DEFAULT_REMOTE_B_DESC
        set(value) = prefs.edit().putString(KEY_REMOTE_B_DESC, value).apply()

    var remoteLongPressMs: Int
        get() = prefs.getInt(KEY_REMOTE_LONG_PRESS_MS, DEFAULT_REMOTE_LONG_PRESS_MS)
        set(value) = prefs.edit().putInt(KEY_REMOTE_LONG_PRESS_MS, value).apply()

    var remoteDualPressMs: Int
        get() = prefs.getInt(KEY_REMOTE_DUAL_PRESS_MS, DEFAULT_REMOTE_DUAL_PRESS_MS)
        set(value) = prefs.edit().putInt(KEY_REMOTE_DUAL_PRESS_MS, value).apply()

    var remoteResetEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_RESET_ENABLED, DEFAULT_REMOTE_RESET_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_REMOTE_RESET_ENABLED, value).apply()
}
