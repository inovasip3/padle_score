package com.padelboard

import fi.iki.elonen.NanoHTTPD

/**
 * Lightweight HTTP server embedded in the app.
 * Listens on the local WiFi IP and handles scoring commands.
 * Designed for <50ms response time.
 */
class HttpCommandServer(
    hostname: String?,
    port: Int,
    private val scoreState: ScoreState,
    private val config: ConfigManager,
    private val historyManager: HistoryManager,
    private val remoteToken: String,   // Random per-session token for secure access
    private val onCommand: (String) -> Unit
) : NanoHTTPD(hostname, port) {

    // Shared learning state: set by web remote, consumed by MainActivity.dispatchKeyEvent
    // null = not learning; 'A' or 'B' = waiting for next BLE key press
    @Volatile var pendingAssignTeam: Char? = null
    @Volatile var lastAssignSuccess: String = ""  // "A", "B", or "" for none

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val params = session.parms

        return when {
            // Secure token path – only this URL serves the Web UI
            uri == "/$remoteToken" -> serveWebUI()
            // API endpoints – accessible to the already-loaded page
            uri == "/cmd" -> handleCommand(params)
            uri == "/status" -> handleStatus()
            uri == "/config" -> handleConfig(params)
            uri == "/upload" && session.method == Method.POST -> handleUpload(session)
            uri == "/assign_remote" -> handleAssignRemote(params)
            uri == "/assign_status" -> handleAssignStatus()
            uri == "/ping" -> newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"ok":true,"msg":"pong"}"""
            )
            uri == "/clear_history" -> {
                historyManager.clearHistory()
                newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
            // Bare root and anything else -> Forbidden / Not Found
            uri == "/" || uri == "/index.html" -> newFixedLengthResponse(
                Response.Status.FORBIDDEN, "text/plain",
                "Access Denied. Scan the QR code on the scoreboard to connect."
            )
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, "application/json",
                """{"ok":false,"error":"not found"}"""
            )
        }.also { response ->
            // Revert to stable headers for legacy Android Hotspot
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Cache-Control", "no-cache")
        }
    }

    private fun handleCommand(params: Map<String, String>): Response {
        val cmd = params["c"] ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "application/json",
            """{"ok":false,"error":"missing parameter 'c'"}"""
        )

        val validCommands = setOf("A_PLUS", "B_PLUS", "A_MINUS", "B_MINUS", "RESET", "RESET_WITH_PHOTOS")
        if (cmd !in validCommands) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "application/json",
                """{"ok":false,"error":"unknown command: $cmd"}"""
            )
        }

        // Execute command
        when (cmd) {
            "A_PLUS" -> scoreState.addPoint('A')
            "B_PLUS" -> scoreState.addPoint('B')
            "A_MINUS" -> scoreState.removePoint('A')
            "B_MINUS" -> scoreState.removePoint('B')
            "RESET" -> {
                scoreState.reset()
                onCommand("CONFIG_UPDATE")
            }
            "RESET_WITH_PHOTOS" -> {
                scoreState.reset()
                config.teamAName = ConfigManager.DEFAULT_TEAM_A
                config.teamBName = ConfigManager.DEFAULT_TEAM_B
                val fileA = java.io.File(config.context.filesDir, "team_a_photo.jpg")
                val fileB = java.io.File(config.context.filesDir, "team_b_photo.jpg")
                if(fileA.exists()) fileA.delete()
                if(fileB.exists()) fileB.delete()
                onCommand("CONFIG_UPDATE")
            }
        }

        // Notify UI thread
        onCommand(cmd)

        // Return current state
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", buildJsonResponse(getFullState())
        )
    }

    private fun handleStatus(): Response {
        return newFixedLengthResponse(
            Response.Status.OK, "application/json", buildJsonResponse(getFullState())
        )
    }

    private fun handleAssignRemote(params: Map<String, String>): Response {
        val team = params["team"]?.uppercase() ?: "A"
        if (team != "A" && team != "B") {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "application/json",
                """{"ok":false,"error":"team must be A or B"}"""
            )
        }
        // Signal MainActivity to enter learning mode
        lastAssignSuccess = ""
        pendingAssignTeam = team[0] // 'A' or 'B'
        onCommand("ASSIGN_REMOTE_${team}")
        return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            """{"ok":true,"learning":true,"team":"$team"}"""
        )
    }

    private fun handleAssignStatus(): Response {
        val learning = pendingAssignTeam != null
        val success = lastAssignSuccess
        // Describe assigned remote descriptors (shortened)
        val aDesc = if (config.remoteADesc.isEmpty()) "" else config.remoteADesc.take(12)
        val bDesc = if (config.remoteBDesc.isEmpty()) "" else config.remoteBDesc.take(12)
        return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            """{"ok":true,"learning":$learning,"success":"$success","remoteADesc":"$aDesc","remoteBDesc":"$bDesc"}"""
        )
    }

    private fun handleUpload(session: IHTTPSession): Response {
        try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            
            val params = session.parms
            val team = params["team"] // "A" or "B"
            
            val tempPath = files["file"]
            if (team != null && tempPath != null) {
                val tempFile = java.io.File(tempPath)
                val targetFile = java.io.File(config.context.filesDir, "team_${team.lowercase()}_photo.jpg")
                tempFile.copyTo(targetFile, overwrite = true)
                
                // Crop to square (center crop) and keep file within reasonable size
                try {
                    val bmp = android.graphics.BitmapFactory.decodeFile(targetFile.absolutePath)
                    if (bmp != null) {
                        val dim = Math.min(bmp.width, bmp.height)
                        val xOffset = (bmp.width - dim) / 2
                        val yOffset = (bmp.height - dim) / 2
                        
                        // Center crop to 1:1
                        val croppedBmp = android.graphics.Bitmap.createBitmap(bmp, xOffset, yOffset, dim, dim)
                        
                        // Scale to target max size
                        val maxDim = 600
                        val finalBmp = if (dim > maxDim) {
                             android.graphics.Bitmap.createScaledBitmap(croppedBmp, maxDim, maxDim, true)
                        } else {
                             croppedBmp
                        }

                        val out = java.io.FileOutputStream(targetFile)
                        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        out.close()
                        
                        if (finalBmp != croppedBmp) finalBmp.recycle()
                        if (croppedBmp != bmp) croppedBmp.recycle()
                        bmp.recycle()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                
                // Notify UI to reload images
                onCommand("CONFIG_UPDATE")
                
                return newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false,"error":"Upload failed"}""")
    }

    private fun handleConfig(params: Map<String, String>): Response {
        val pinAttempt = params["pin"]
        if (pinAttempt != null) {
            val ok = (pinAttempt == config.pin)
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":$ok}")
        }
        
        config.saveBatch {
            params["teamA"]?.let { putString(ConfigManager.KEY_TEAM_A_NAME, it) }
            params["teamB"]?.let { putString(ConfigManager.KEY_TEAM_B_NAME, it) }
            params["photoSize"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_PHOTO_SIZE, it) }
            params["photoYPos"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_PHOTO_Y_POS, it) }
            params["photoXPosA"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_PHOTO_X_POS_A, it) }
            params["photoXPosB"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_PHOTO_X_POS_B, it) }
            params["voiceRef"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_VOICE_REF, it) }
            params["scoringPreset"]?.let { putString(ConfigManager.KEY_SCORING_PRESET, it) }
            params["soundEnabled"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_SOUND_ENABLED, it) }
            params["enablePhotos"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_PHOTOS, it) }
            params["enableApplause"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_APPLAUSE, it) }
            
            // Rules
            params["useGoldenPoint"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_USE_GOLDEN_POINT, it) }
            params["gamesToWinSet"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_GAMES_TO_WIN_SET, it) }
            params["winBy2Games"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_WIN_BY_2_GAMES, it) }
            params["useTieBreak"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_USE_TIE_BREAK, it) }
            params["tieBreakAt"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_TIE_BREAK_AT, it) }
            params["tieBreakTarget"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_TIE_BREAK_TARGET, it) }
            params["tieBreakWinBy2"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_TIE_BREAK_WIN_BY_2, it) }
            params["setsToWinMatch"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_SETS_TO_WIN_MATCH, it) }
            params["finalSetSuperTieBreak"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_FINAL_SET_SUPER_TB, it) }
            params["superTieBreakTarget"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_SUPER_TB_TARGET, it) }
            
            // Visuals
            params["colorA"]?.let { try { putInt(ConfigManager.KEY_COLOR_A, android.graphics.Color.parseColor(it)) } catch(e:Exception){} }
            params["colorB"]?.let { try { putInt(ConfigManager.KEY_COLOR_B, android.graphics.Color.parseColor(it)) } catch(e:Exception){} }
            params["fontScale"]?.toFloatOrNull()?.let { putFloat(ConfigManager.KEY_FONT_SCALE, it) }
            params["fontTypeface"]?.let { putString(ConfigManager.KEY_FONT_TYPEFACE, it) }
            params["enableWinEffect"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_WIN_EFFECT, it) }
            
            // Audio Additions
            params["useLoveForZero"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_USE_LOVE_FOR_ZERO, it) }
            
            // Remote Control
            params["enableHttpServer"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_HTTP, it) }
            params["enableBleHid"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_BLE_HID, it) }
            params["enableShutterRemote"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ENABLE_SHUTTER_REMOTE, it) }
            params["keyTeamAPlus"]?.let { putString(ConfigManager.KEY_KB_TEAM_A_PLUS, it.take(1)) }
            params["keyTeamAMinus"]?.let { putString(ConfigManager.KEY_KB_TEAM_A_MINUS, it.take(1)) }
            params["keyTeamBPlus"]?.let { putString(ConfigManager.KEY_KB_TEAM_B_PLUS, it.take(1)) }
            params["keyTeamBMinus"]?.let { putString(ConfigManager.KEY_KB_TEAM_B_MINUS, it.take(1)) }
            params["keyReset"]?.let { putString(ConfigManager.KEY_KB_RESET, it.take(1)) }
            
            // V2.3 Remote Actions
            params["remoteMode"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_REMOTE_MODE, it) }
            params["remoteSwapMod2"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_REMOTE_SWP_MOD2, it) }
            params["remoteResetEnabled"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_REMOTE_RESET_ENABLED, it) }
            params["remoteLongPressMs"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_REMOTE_LONG_PRESS_MS, it) }
            params["remoteDualPressMs"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_REMOTE_DUAL_PRESS_MS, it) }
            
            // System
            params["serverPort"]?.toIntOrNull()?.let { putInt(ConfigManager.KEY_SERVER_PORT, it) }
            params["showDebugMsg"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_SHOW_DEBUG_MSG, it) }
            params["newPin"]?.let { if (it.isNotBlank()) putString(ConfigManager.KEY_PIN, it) }
            params["customThemeJson"]?.let { putString(ConfigManager.KEY_CUSTOM_THEME_JSON, it) }
            params["allowUserUploadPhoto"]?.toBooleanStrictOrNull()?.let { putBoolean(ConfigManager.KEY_ALLOW_USER_UPLOAD_PHOTO, it) }
        }

        val nonConfigKeys = listOf("pin")
        val hasChanges = params.keys.any { it !in nonConfigKeys }
        
        if (hasChanges) {
            onCommand("CONFIG_UPDATE")
        }
        
        val json = buildJsonResponse(getFullState())
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun getFullState(): Map<String, Any?> {
        val state = scoreState.toMap().toMutableMap()
        state["teamA"] = config.teamAName
        state["teamB"] = config.teamBName
        state["photoSize"] = config.photoSize
        state["photoYPos"] = config.photoYPos
        state["photoXPosA"] = config.photoXPosA
        state["photoXPosB"] = config.photoXPosB
        state["voiceRef"] = config.enableVoiceRef
        state["scoringPreset"] = config.scoringPreset
        state["soundEnabled"] = config.soundEnabled
        state["enablePhotos"] = config.enablePhotos
        
        // Rules
        state["useGoldenPoint"] = config.useGoldenPoint
        state["gamesToWinSet"] = config.gamesToWinSet
        state["winBy2Games"] = config.winBy2Games
        state["useTieBreak"] = config.useTieBreak
        state["tieBreakAt"] = config.tieBreakAt
        state["tieBreakTarget"] = config.tieBreakTarget
        state["tieBreakWinBy2"] = config.tieBreakWinBy2
        state["setsToWinMatch"] = config.setsToWinMatch
        state["finalSetSuperTieBreak"] = config.finalSetSuperTieBreak
        state["superTieBreakTarget"] = config.superTieBreakTarget
        
        // Visuals
        state["colorA"] = String.format("#%06X", 0xFFFFFF and config.colorA)
        state["colorB"] = String.format("#%06X", 0xFFFFFF and config.colorB)
        state["fontScale"] = config.fontScale
        state["fontTypeface"] = config.fontTypeface
        state["enableWinEffect"] = config.enableWinEffect
        
        // Audio Additions
        state["useLoveForZero"] = config.useLoveForZero
        
        // Remote Control
        state["enableHttpServer"] = config.enableHttpServer
        state["enableBleHid"] = config.enableBleHid
        state["enableShutterRemote"] = config.enableShutterRemote
        state["keyTeamAPlus"] = config.keyTeamAPlus
        state["keyTeamAMinus"] = config.keyTeamAMinus
        state["keyTeamBPlus"] = config.keyTeamBPlus
        state["keyTeamBMinus"] = config.keyTeamBMinus
        state["keyReset"] = config.keyReset
        
        // V2.3 Remote Control Additions
        state["remoteMode"] = config.remoteMode
        state["remoteSwapMod2"] = config.remoteSwapMod2
        state["remoteResetEnabled"] = config.remoteResetEnabled
        state["remoteLongPressMs"] = config.remoteLongPressMs
        state["remoteDualPressMs"] = config.remoteDualPressMs
        state["remoteADesc"] = config.remoteADesc
        state["remoteBDesc"] = config.remoteBDesc
        
        // Audio Additions
        state["enableApplause"] = config.enableApplause
        
        // System
        state["serverPort"] = config.serverPort
        state["customThemeJson"] = config.customThemeJson
        state["allowUserUploadPhoto"] = config.allowUserUploadPhoto

        state["history"] = historyManager.getHistory()
        return state
    }

    private fun buildJsonResponse(state: Map<String, Any?>): String {
        // Deterministic JSON building for simple IoT clients (like ESP8266)
        val sb = StringBuilder()
        sb.append("""{"ok":true,"state":{""")
        val entries = state.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
            sb.append("\"$key\":")
            when (value) {
                is String -> {
                    // History is already a JSON array string, don't quote it
                    if (key == "history") {
                        sb.append(value)
                    } else {
                        // Very basic escaping for internal quotes just in case
                        val escaped = value.replace("\"", "\\\"")
                        sb.append("\"$escaped\"")
                    }
                }
                is Int -> sb.append(value)
                is Float -> sb.append(value)
                is Double -> sb.append(value)
                is Boolean -> sb.append(value)
                null -> sb.append("null")
                else -> sb.append("\"$value\"")
            }
            if (index < entries.size - 1) sb.append(",")
        }
        sb.append("}}")
        return sb.toString()
    }
    
    private fun serveWebUI(): Response {
        var html = ""
        try {
            html = config.context.assets.open("remote.html").bufferedReader().use { it.readText() }
            
            // Inject App Version
            val versionName = try {
                @Suppress("DEPRECATION")
                config.context.packageManager.getPackageInfo(config.context.packageName, 0).versionName
            } catch (e: Exception) {
                "2.3.0"
            }
            html = html.replace("{{APP_VERSION}}", versionName)
            
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to load remote.html")
        }
        
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
