package com.padelboard

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.content.pm.PackageManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.Bitmap
import android.graphics.Color
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.view.Gravity
import android.widget.EditText
import java.util.concurrent.atomic.AtomicReference

/**
 * Main kiosk activity.
 * - Full immersive mode (no system bars)
 * - Blocks back/home/recent buttons
 * - Starts embedded HTTP server
 * - Manages scoreboard view
 * - Hidden config via 5 rapid taps
 */
class MainActivity : Activity() {

    private lateinit var scoreboardView: ScoreboardView
    private lateinit var ipOverlay: TextView
    private lateinit var qrCodeView: ImageView
    private lateinit var versionText: TextView
    private lateinit var bottomInfoLayout: LinearLayout
    private lateinit var config: ConfigManager
    private lateinit var scoreState: ScoreState
    private lateinit var soundManager: SoundManager
    private lateinit var historyManager: HistoryManager

    private val httpServer = AtomicReference<HttpCommandServer?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private var lastIp: String? = null

    // Random 4-char token generated each launch for secure remote access
    private val remoteToken: String = generateRemoteToken()
    
    // AB Shutter 3 / Bluetooth Remote tracking (V2.3)
    private val deviceDepressTimestamps = mutableMapOf<String, Long>()
    private var lastScoredTeam: Char? = null
    private var bleRemoteClickCount = 0

    // Tap detection for hidden config
    private var tapCount = 0
    private var lastTapTime = 0L
    private val TAP_WINDOW_MS = 2000L
    private val TAPS_REQUIRED = 5

    // V2.3: Learning Mode for Remote Assignment
    private var learningRemoteTeam: Char? = null

    // Watchdog: auto-restart server if it dies
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            ensureServerRunning()
            handler.post { showIpOverlay() } // Refresh network/IP periodically
            handler.postDelayed(this, 30_000) // Check every 30 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_main)

        // Initialize components
        config = ConfigManager(this)
        scoreState = ScoreState(config)
        scoreboardView = findViewById(R.id.scoreboardView)
        ipOverlay = findViewById(R.id.ipOverlay)
        qrCodeView = findViewById(R.id.qrCodeView)
        versionText = findViewById(R.id.versionText)
        bottomInfoLayout = findViewById(R.id.bottomInfoLayout)
        soundManager = SoundManager(this, config)
        historyManager = HistoryManager(this)
        
        // Set version text with "X9" prefix in golden yellow
        try {
            val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
            setX9Title(versionText, "Padle Score v.$versionName")
        } catch (e: Exception) {
            setX9Title(versionText, "Padle Score v.2.3.1")
        }

        // Click version text to open settings immediately
        versionText.setOnClickListener {
            openConfig()
        }

        // Apply config
        applyConfig()

        // Setup score change listener
        scoreState.onScoreChanged = {
            handler.post { refreshScoreboard() }
        }

        // Initial scoreboard render
        refreshScoreboard()
        
        // Wire up touch events for local controls
        setupTouchControls()

        // Enter immersive mode
        enterImmersiveMode()

        // Start HTTP server
        startServer()

        // Show IP overlay (fades out after 10s)
        showIpOverlay()

        // Start watchdog
        handler.postDelayed(watchdogRunnable, 30_000)

        // Setup tap detection on root view
        findViewById<View>(R.id.rootLayout).setOnClickListener {
            handleTap()
        }
    }

    private fun applyConfig() {
        // ScoreState now reads directly from config internally upon methods like isGamePoint()

        // Apply visual config to ScoreboardView
        scoreboardView.teamAName   = config.teamAName
        scoreboardView.teamBName   = config.teamBName
        scoreboardView.fontTypeface = config.fontTypeface
        scoreboardView.enableWinEffect = config.enableWinEffect
        scoreboardView.enablePhotos = config.enablePhotos
        scoreboardView.photoSize = config.photoSize
        scoreboardView.photoYPos = config.photoYPos
        scoreboardView.photoXPosA = config.photoXPosA
        scoreboardView.photoXPosB = config.photoXPosB
        scoreboardView.reloadPhotos()

        // JSON theme overrides individual fields if valid
        applyJsonTheme()

        scoreboardView.invalidate()
    }

    private fun applyJsonTheme() {
        val json = config.customThemeJson
        try {
            if (json.isNotBlank()) {
                val obj = org.json.JSONObject(json)
                scoreboardView.colorA    = parseColor(obj.optString("colorA"),    config.colorA)
                scoreboardView.colorB    = parseColor(obj.optString("colorB"),    config.colorB)
                scoreboardView.fontScale = obj.optDouble("fontScale", config.fontScale.toDouble()).toFloat()
            } else {
                scoreboardView.colorA    = config.colorA
                scoreboardView.colorB    = config.colorB
                scoreboardView.fontScale = config.fontScale
            }
        } catch (e: Exception) {
            scoreboardView.colorA    = config.colorA
            scoreboardView.colorB    = config.colorB
            scoreboardView.fontScale = config.fontScale
        }
    }

    private fun parseColor(value: String, default: Int): Int {
        return try { android.graphics.Color.parseColor(value) } catch (e: Exception) { default }
    }

    // Track sets/games to detect wins
    private var lastSetsA = 0
    private var lastSetsB = 0
    private var lastGamesA = 0
    private var lastGamesB = 0

    private fun refreshScoreboard(changedTeam: Char? = null) {
        val currentSetsA = scoreState.setsA
        val currentSetsB = scoreState.setsB
        val currentGamesA = scoreState.gamesA
        val currentGamesB = scoreState.gamesB
        
        val setWon = currentSetsA > lastSetsA || currentSetsB > lastSetsB
        
        // Padel display format: If there's 1 set played, showing "1-4" (Set-Game). 
        // Or if simple, just show games. We will pass games to the scoreboard's 'sets' field for now,
        // Since traditional scoreboards show games in the secondary digits.
        // Wait, ScoreboardView expects Int. We'll give it games.
        
        scoreboardView.updateScore(
            newScoreA = scoreState.getScoreDisplayA(),
            newScoreB = scoreState.getScoreDisplayB(),
            newSetsA = currentGamesA,
            newSetsB = currentGamesB,
            newMatchSetsA = currentSetsA,
            newMatchSetsB = currentSetsB,
            newStatus = scoreState.getStatusText(),
            changedTeam = changedTeam
        )

        val gameWon = currentGamesA != lastGamesA || currentGamesB != lastGamesB

        if (changedTeam != null) {
            scoreboardView.shake()
            
            val winnerName = if (changedTeam == 'A') config.teamAName else config.teamBName
            
            if (setWon) {
                scoreboardView.celebrateSetWin(winnerName)
                soundManager.announceSetWin(winnerName)
                // Save to history on every set win
                historyManager.saveMatch(
                    config.teamAName, config.teamBName,
                    currentSetsA, currentSetsB,
                    currentGamesA, currentGamesB,
                    scoreState.getScoreDisplayA(), scoreState.getScoreDisplayB()
                )
            } else if (gameWon) {
                soundManager.announceGameWin(winnerName, currentGamesA, currentGamesB)
            } else {
                soundManager.announceScore(scoreState.getScoreDisplayA(), scoreState.getScoreDisplayB(), scoreState.getStatusText())
            }
        }
        
        lastSetsA = currentSetsA
        lastSetsB = currentSetsB
        lastGamesA = currentGamesA
        lastGamesB = currentGamesB
        
        updateBottomInfoVisibility()
    }
    
    private fun updateBottomInfoVisibility() {
        // Show QR code and IP only when the score is 0-0 in points, games, and sets
        val isStarted = scoreState.setsA > 0 || scoreState.setsB > 0 || 
                        scoreState.gamesA > 0 || scoreState.gamesB > 0 ||
                        scoreState.getScoreDisplayA() != "0" || scoreState.getScoreDisplayB() != "0"
        
        if (!isStarted) {
            bottomInfoLayout.visibility = View.VISIBLE
            bottomInfoLayout.alpha = 1f
            // QR generation is now handled centrally in showIpOverlay
        } else {
            bottomInfoLayout.visibility = View.GONE
        }
    }
    
    private fun generateQrCode() {
        val ip = getLocalIpAddress()
        if (ip != null) {
            try {
                val url = "http://$ip:${config.serverPort}/$remoteToken"
                val barcodeEncoder = BarcodeEncoder()
                val bitmap: Bitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 400, 400)
                qrCodeView.setImageBitmap(bitmap)
                qrCodeView.visibility = View.VISIBLE
            } catch (e: Exception) {
                e.printStackTrace()
                qrCodeView.visibility = View.GONE
            }
        }
    }

    /** Generates a random 4-character alphanumeric token (lowercase letters + digits). */
    private fun generateRemoteToken(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..4).map { chars.random() }.joinToString("")
    }

    /** Sets versionText with "X9 " prefix in golden yellow and the rest in default white. */
    private fun setX9Title(view: TextView, suffix: String) {
        val full = "X9 $suffix"
        val spannable = SpannableString(full)
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#FFD700")),
            0, 2,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        view.text = spannable
    }
    
    private fun setupTouchControls() {
        scoreboardView.onNameTap = { team ->
            showQuickRenameDialog(team)
        }
        
        scoreboardView.onScoreTap = { team ->
            if (team == 'A') scoreState.addPoint('A') else scoreState.addPoint('B')
            refreshScoreboard(team)
        }
        
        scoreboardView.onScoreLongPress = { _ ->
            // Use history-based undo for accurate state restoration across game/set boundaries
            val didUndo = scoreState.undo()
            if (didUndo) {
                refreshScoreboard()
                soundManager.playUndo()
            }
        }
        
        scoreboardView.onResetLongPress = {
            scoreState.reset()
            refreshScoreboard()
            soundManager.playUndo() // Use undo sound for reset
        }
    }

    private fun startServer() {
        val ip = getLocalIpAddress()
        Log.d("PadleScore", "Starting server on port ${config.serverPort} (Binding: ${ip ?: "0.0.0.0"})...")
        Log.d("PadleScore", "Remote token: $remoteToken")
        httpServer.get()?.stop()
        httpServer.set(null)
        if (!config.enableHttpServer) {
            Log.d("PadleScore", "Server disabled in settings.")
            return
        }
        try {
            // Revert to null (0.0.0.0) binding for maximum compatibility with Hotspot/NAT
            val server = HttpCommandServer(null, config.serverPort, scoreState, config, historyManager, remoteToken) { cmd ->
                Log.d("PadleScore", "Command received: $cmd")
                handler.post { 
                    if (config.showDebugMsg) {
                        Toast.makeText(this, "Remote Cmd: $cmd", Toast.LENGTH_SHORT).show()
                    }
                }
                if (cmd == "CONFIG_UPDATE") {
                    handler.post { applyConfig(); refreshScoreboard() }
                    return@HttpCommandServer
                }
                if (cmd.startsWith("ASSIGN_REMOTE_")) {
                    val team = if (cmd.endsWith("_B")) 'B' else 'A'
                    handler.post {
                        learningRemoteTeam = team
                        Toast.makeText(this, "📡 Waiting for Remote $team\nPress any key on the remote now...", Toast.LENGTH_LONG).show()
                        soundManager.playPoint()
                    }
                    return@HttpCommandServer
                }
                val team = when (cmd) {
                    "A_PLUS", "A_MINUS" -> 'A'
                    "B_PLUS", "B_MINUS" -> 'B'
                    else -> null
                }
                handler.post { refreshScoreboard(team) }
            }
            httpServer.set(server)
            server.start()
            Log.d("PadleScore", "Server started successfully.")
            handler.post {
                Toast.makeText(this, "Server started on port ${config.serverPort}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PadleScore", "Failed to start server: ${e.message}")
            e.printStackTrace()
            handler.postDelayed({ startServer() }, 2000)
        }
    }

    private fun ensureServerRunning() {
        val server = httpServer.get()
        if (server == null || !server.isAlive) {
            Log.w("PadleScore", "Server health check failed. Restarting...")
            startServer()
        }
    }

    // --- IP Display ---

    private fun showIpOverlay() {
        val ip = getLocalIpAddress()
        val port = config.serverPort
        ipOverlay.text = if (ip != null) {
            "📱 http://$ip:$port/$remoteToken"
        } else {
            "⚠️ No WiFi connection"
        }
        
        // Update QR Code if IP has changed or is missing
        if (ip != null && (ip != lastIp || qrCodeView.drawable == null)) {
            lastIp = ip
            generateQrCode()
        }
        
        updateBottomInfoVisibility()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // Priority 1: Specifically look for the Hotspot interface IP (usually 192.168.43.1)
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("ap") || name.contains("softap")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            
            // Priority 2: Traditional interfaces (WiFi Client as guest)
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("eth") || name.contains("rndis") || name.contains("p2p")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }

            // Priority 3: Try WifiManager (Legacy/Fallback)
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format("%d.%d.%d.%d", (ipInt and 0xff), (ipInt shr 8 and 0xff), (ipInt shr 16 and 0xff), (ipInt shr 24 and 0xff))
            }

            // Final fallback: any non-loopback IPv4
            for (intf in interfaces) {
                if (intf.isUp && !intf.isLoopback) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // --- Immersive Mode ---

    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        ensureServerRunning()
    }

    // --- Block system keys ---

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Blocked - do nothing
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true // Consume/block
            else -> super.onKeyDown(keyCode, event)
        }
    }

     /**
     * V2.3: Advanced BLE / Bluetooth button logic.
     * Mode 1: 1-click for Team A, 2-click for Team B.
     */
    private val bleRemoteActionRunnable = Runnable {
        if (config.remoteMode != 1) return@Runnable // Only for Mode 1
        when (bleRemoteClickCount) {
            1 -> { 
                scoreState.addPoint('A')
                refreshScoreboard('A')
                lastScoredTeam = 'A'
            }
            2 -> { 
                scoreState.addPoint('B')
                refreshScoreboard('B')
                lastScoredTeam = 'B'
            }
        }
        bleRemoteClickCount = 0
    }

    private var dualResetTriggered = false
    private val dualResetRunnable = Runnable {
        if (config.remoteMode == 2 && config.remoteResetEnabled) {
            val isADown = deviceDepressTimestamps.containsKey(config.remoteADesc)
            val isBDown = deviceDepressTimestamps.containsKey(config.remoteBDesc)
            if (isADown && isBDown && !dualResetTriggered) {
                dualResetTriggered = true
                scoreState.reset()
                applyConfig()
                refreshScoreboard()
                soundManager.playUndo() 
                Toast.makeText(this, "♻️ Reset via Remotes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!config.enableBleHid) return super.dispatchKeyEvent(event)
        
        val keyCode = event.keyCode
        val action = event.action
        val device = event.device
        val descriptor = device?.descriptor ?: ""

        // Learning Mode: Capture the first key press to assign this remote to a team
        if (learningRemoteTeam != null && action == KeyEvent.ACTION_DOWN && descriptor.isNotEmpty()) {
            val team = learningRemoteTeam!!
            if (team == 'A') config.remoteADesc = descriptor else config.remoteBDesc = descriptor

            learningRemoteTeam = null

            // Notify web remote polling: learning done, report success
            val server = httpServer.get()
            if (server != null) {
                server.pendingAssignTeam = null
                server.lastAssignSuccess = team.toString()
                // Clear success flag after 10s so UI can reset
                handler.postDelayed({ server.lastAssignSuccess = "" }, 10_000)
            }

            handler.post {
                Toast.makeText(this, "✅ Remote $team Linked!", Toast.LENGTH_SHORT).show()
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK)
                applyConfig() // Refresh IDs
            }
            return true
        }

        // Filter: We only care about buttons typically found on remotes
        val isRemoteKey = (keyCode == KeyEvent.KEYCODE_VOLUME_UP || 
                          keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || 
                          keyCode == KeyEvent.KEYCODE_ENTER || 
                          keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                          keyCode == KeyEvent.KEYCODE_SPACE ||
                          keyCode == KeyEvent.KEYCODE_BUTTON_A)

        // If it's the specific key Team A Plus binding, treat it as remote key
        val chRaw = event.unicodeChar.toChar().lowercaseChar().toString()
        val isBindingKey = chRaw == config.keyTeamAPlus || chRaw == config.keyTeamBPlus

        if (config.enableShutterRemote && (isRemoteKey || isBindingKey)) {
            
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount > 0) return true // Ignore repeats, we handle hold via start time or runnable

                if (!deviceDepressTimestamps.containsKey(descriptor)) {
                    val now = System.currentTimeMillis()
                    deviceDepressTimestamps[descriptor] = now
                    
                    // Mode 2 Dual Reset Check
                    if (config.remoteMode == 2 && config.remoteResetEnabled) {
                        val otherDesc = if (descriptor == config.remoteADesc) config.remoteBDesc else config.remoteADesc
                        if (deviceDepressTimestamps.containsKey(otherDesc)) {
                            // Both are now down!
                            handler.removeCallbacks(dualResetRunnable)
                            handler.postDelayed(dualResetRunnable, config.remoteDualPressMs.toLong())
                        }
                    }
                }
                return true 
            } else if (action == KeyEvent.ACTION_UP) {
                val downTime = deviceDepressTimestamps.remove(descriptor) ?: return true
                handler.removeCallbacks(dualResetRunnable) // Stop reset timer if any button is released
                
                if (dualResetTriggered) {
                    // Check if all designated remotes are released to clear the flag
                    val anyStillDown = deviceDepressTimestamps.containsKey(config.remoteADesc) || 
                                       deviceDepressTimestamps.containsKey(config.remoteBDesc)
                    if (!anyStillDown) dualResetTriggered = false
                    return true
                }

                val duration = System.currentTimeMillis() - downTime
                
                if (duration > config.remoteLongPressMs) {
                    // Long press -> Undo/Minus
                    if (config.remoteMode == 1) {
                        val teamToUndo = lastScoredTeam
                        if (teamToUndo != null) {
                            scoreState.removePoint(teamToUndo)
                            refreshScoreboard()
                            soundManager.playUndo()
                            lastScoredTeam = null 
                        }
                    } else if (config.remoteMode == 2) {
                        val team = when (descriptor) {
                            config.remoteADesc -> if (config.remoteSwapMod2) 'B' else 'A'
                            config.remoteBDesc -> if (config.remoteSwapMod2) 'A' else 'B'
                            else -> null
                        }
                        if (team != null) {
                            scoreState.removePoint(team)
                            refreshScoreboard()
                            soundManager.playUndo()
                        }
                    }
                } else {
                    // Short press
                    if (config.remoteMode == 1) {
                        handler.removeCallbacks(bleRemoteActionRunnable)
                        bleRemoteClickCount++
                        handler.postDelayed(bleRemoteActionRunnable, 500)
                    } else if (config.remoteMode == 2) {
                        val team = when (descriptor) {
                            config.remoteADesc -> if (config.remoteSwapMod2) 'B' else 'A'
                            config.remoteBDesc -> if (config.remoteSwapMod2) 'A' else 'B'
                            else -> null
                        }
                        if (team != null) {
                            scoreState.addPoint(team)
                            refreshScoreboard(team)
                            lastScoredTeam = team
                        }
                    }
                }
                return true
            }
        }

        if (action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val ch = event.unicodeChar.toChar().lowercaseChar().toString()
        if (ch.isBlank()) return super.dispatchKeyEvent(event)

        // If the key is one of the specialized keys (like A+), treat it as a generic trigger for multi-click logic
        // or prioritize its dedicated function if it's NOT a standard cheap remote button.
        // We will target the config.keyTeamAPlus as the main "Action Button" for cheap remotes.
        
        if (ch == config.keyTeamAPlus) {
            handler.removeCallbacks(bleRemoteActionRunnable)
            bleRemoteClickCount++
            handler.postDelayed(bleRemoteActionRunnable, 500)
            return true
        }

        // Keep fallback for other keys
        when (ch) {
            config.keyTeamAMinus -> { scoreState.removePoint('A'); refreshScoreboard(); return true }
            config.keyTeamBPlus  -> { scoreState.addPoint('B');    refreshScoreboard('B'); return true }
            config.keyTeamBMinus -> { scoreState.removePoint('B'); refreshScoreboard(); return true }
            config.keyReset      -> { scoreState.reset(); applyConfig(); refreshScoreboard(); return true }
        }

        return super.dispatchKeyEvent(event)
    }

    // --- Hidden config (5 rapid taps) ---

    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > TAP_WINDOW_MS) {
            tapCount = 0
        }
        lastTapTime = now
        tapCount++

        if (tapCount >= TAPS_REQUIRED) {
            tapCount = 0
            openConfig()
        }
    }

    private fun showQuickRenameDialog(team: Char) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val input = EditText(this).apply {
            setText(if (team == 'A') config.teamAName else config.teamBName)
            isSingleLine = true
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setSelectAllOnFocus(true)
            // Request focus and show keyboard
            requestFocus()
        }
        container.addView(input)

        val dialog = android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Ganti Nama Tim ${if (team == 'A') "A" else "B"}")
            .setView(container)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (team == 'A') config.teamAName = newName else config.teamBName = newName
                    applyConfig()
                }
            }
            .setNegativeButton("Batal", null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun openConfig() {
        ConfigDialog(this, config) {
            applyConfig()
            refreshScoreboard()
            // Restart server if port changed
            startServer()
            // Re-show IP
            showIpOverlay()
        }.showConfigDialog()
    }

    // --- Lifecycle ---

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
        httpServer.get()?.stop()
        soundManager.release()
    }
}
