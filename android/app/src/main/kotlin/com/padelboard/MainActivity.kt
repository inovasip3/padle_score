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
        scoreState = ScoreState()
        scoreboardView = findViewById(R.id.scoreboardView)
        ipOverlay = findViewById(R.id.ipOverlay)
        qrCodeView = findViewById(R.id.qrCodeView)
        versionText = findViewById(R.id.versionText)
        bottomInfoLayout = findViewById(R.id.bottomInfoLayout)
        
        // Set version text
        try {
            val versionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
            versionText.text = "Padle Score v.$versionName."
        } catch (e: Exception) {
            versionText.text = "Padle Score v.1.0.0."
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
        scoreboardView.teamAName = config.teamAName
        scoreboardView.teamBName = config.teamBName
        scoreboardView.colorA = config.colorA
        scoreboardView.colorB = config.colorB
        scoreboardView.invalidate()
    }

    private fun refreshScoreboard(changedTeam: Char? = null) {
        scoreboardView.updateScore(
            newScoreA = scoreState.getScoreDisplayA(),
            newScoreB = scoreState.getScoreDisplayB(),
            newSetsA = scoreState.setsA,
            newSetsB = scoreState.setsB,
            newStatus = scoreState.getStatusText(),
            changedTeam = changedTeam
        )
        updateBottomInfoVisibility()
    }
    
    private fun updateBottomInfoVisibility() {
        // Show QR code and IP only when the score is 0-0 in points and sets
        val isStarted = scoreState.setsA > 0 || scoreState.setsB > 0 || 
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
            config.teamAName = ConfigManager.DEFAULT_TEAM_A
            config.teamBName = ConfigManager.DEFAULT_TEAM_B
            applyConfig()
            refreshScoreboard()
        }
    }

    // --- HTTP Server ---

    private fun startServer() {
        try {
            httpServer?.stop()
            httpServer = HttpCommandServer(config.serverPort, scoreState, config) { cmd ->
                if (cmd == "CONFIG_UPDATE") {
                    handler.post { applyConfig(); refreshScoreboard() }
                    return@HttpCommandServer
                }
                // Determine which team changed for animation
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
            // Retry after 2 seconds
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

            // Method 2: Enumerate network interfaces
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
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
    }
}
