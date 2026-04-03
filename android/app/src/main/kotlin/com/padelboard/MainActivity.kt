package com.padelboard

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
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
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

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

    private var httpServer: HttpCommandServer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Tap detection for hidden config
    private var tapCount = 0
    private var lastTapTime = 0L
    private val TAP_WINDOW_MS = 2000L
    private val TAPS_REQUIRED = 5

    // Watchdog: auto-restart server if it dies
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            ensureServerRunning()
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
        
        // Set version text
        try {
            val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
            versionText.text = "Padle Score v.$versionName"
        } catch (e: Exception) {
            versionText.text = "Padle Score v.2.0.0"
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

    // Track sets to detect set win
    private var lastSetsA = 0
    private var lastSetsB = 0

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
            newStatus = scoreState.getStatusText(),
            changedTeam = changedTeam
        )

        if (changedTeam != null) {
            scoreboardView.shake()
            if (setWon) {
                val winnerName = if (currentSetsA > lastSetsA) config.teamAName else config.teamBName
                scoreboardView.celebrateSetWin(winnerName)
                soundManager.playWinSet()
            } else if (scoreState.isMatchPoint(changedTeam)) {
                soundManager.playMatchPoint()
            } else {
                soundManager.playPoint()
            }
        }
        
        lastSetsA = currentSetsA
        lastSetsB = currentSetsB
        
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
            // Generate QR once
            if (qrCodeView.drawable == null) {
                generateQrCode()
            }
        } else {
            bottomInfoLayout.visibility = View.GONE
        }
    }
    
    private fun generateQrCode() {
        val ip = getLocalIpAddress()
        if (ip != null) {
            try {
                val url = "http://$ip:${config.serverPort}/"
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
    
    private fun setupTouchControls() {
        scoreboardView.onNameTap = { team ->
            openConfig()
        }
        
        scoreboardView.onScoreTap = { team ->
            if (team == 'A') scoreState.addPoint('A') else scoreState.addPoint('B')
            refreshScoreboard(team)
        }
        
        scoreboardView.onScoreLongPress = { team ->
            if (team == 'A') scoreState.removePoint('A') else scoreState.removePoint('B')
            refreshScoreboard(team)
        }
        
        scoreboardView.onResetLongPress = {
            scoreState.reset()
            refreshScoreboard()
            soundManager.playUndo() // Use undo sound for reset
        }
    }

    // --- HTTP Server ---

    private fun startServer() {
        httpServer?.stop()
        httpServer = null
        if (!config.enableHttpServer) return   // HTTP remote disabled in settings
        try {
            httpServer = HttpCommandServer(config.serverPort, scoreState, config) { cmd ->
                if (cmd == "CONFIG_UPDATE") {
                    handler.post { applyConfig(); refreshScoreboard() }
                    return@HttpCommandServer
                }
                val team = when (cmd) {
                    "A_PLUS", "A_MINUS" -> 'A'
                    "B_PLUS", "B_MINUS" -> 'B'
                    else -> null
                }
                handler.post { refreshScoreboard(team) }
            }
            httpServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            handler.postDelayed({ startServer() }, 2000)
        }
    }

    private fun ensureServerRunning() {
        if (httpServer?.isAlive != true) {
            startServer()
        }
    }

    // --- IP Display ---

    private fun showIpOverlay() {
        val ip = getLocalIpAddress()
        val port = config.serverPort
        ipOverlay.text = if (ip != null) {
            "📱 Remote: http://$ip:$port"
        } else {
            "⚠️ No WiFi connection"
        }
        updateBottomInfoVisibility()
    }

    private fun getLocalIpAddress(): String? {
        try {
            // Method 1: Try WifiManager
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            // Method 2: Enumerate network interfaces (Prioritize Tethering/Hotspot)
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // First pass: look for Hotspot/Tethering interfaces (usually ap0, wlan1, etc.)
            for (intf in interfaces) {
                if (intf.name.contains("ap") || intf.name.contains("wlan1")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }

            // Second pass: any other non-loopback IPv4 (standard WiFi/Ethernet)
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
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
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
     * V2.0: BLE HID keyboard dispatch.
     * Routes single-character keystrokes from the ESP32-C3 remote
     * based on the user-configured key bindings in ConfigManager.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!config.enableBleHid) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val ch = event.unicodeChar.toChar().lowercaseChar().toString()
        if (ch.isBlank()) return super.dispatchKeyEvent(event)

        when (ch) {
            config.keyTeamAPlus  -> { scoreState.addPoint('A');    handler.post { refreshScoreboard('A') }; return true }
            config.keyTeamAMinus -> { scoreState.removePoint('A'); handler.post { refreshScoreboard() };   return true }
            config.keyTeamBPlus  -> { scoreState.addPoint('B');    handler.post { refreshScoreboard('B') }; return true }
            config.keyTeamBMinus -> { scoreState.removePoint('B'); handler.post { refreshScoreboard() };   return true }
            config.keyReset      -> { scoreState.reset(); applyConfig(); handler.post { refreshScoreboard() }; return true }
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

    private fun openConfig() {
        ConfigDialog(this, config) {
            applyConfig()
            refreshScoreboard()
            // Restart server if port changed
            startServer()
            // Re-show IP
            showIpOverlay()
        }.showPinDialog()
    }

    // --- Lifecycle ---

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(watchdogRunnable)
        httpServer?.stop()
        soundManager.release()
    }
}
