package com.padelboard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * Manages audio feedback for the app.
 * Uses ToneGenerator for beeps and TextToSpeech for umpire announcements.
 *
 * V2.1: Added crash protection around ToneGenerator and TTS init which can
 * throw RuntimeException on some devices with restricted audio subsystems.
 */
class SoundManager(private val context: Context, private val config: ConfigManager) : TextToSpeech.OnInitListener {

    private var toneGenerator: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private var soundPool: android.media.SoundPool? = null
    private var applauseSoundId: Int = -1

    init {
        // Init SoundPool for sound effects
        try {
            soundPool = android.media.SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build()
            
            // Try to load applause sound if it exists
            val resId = context.resources.getIdentifier("applause", "raw", context.packageName)
            if (resId != 0) {
                applauseSoundId = soundPool?.load(context, resId, 1) ?: -1
            }
        } catch (e: Exception) {
            Log.w("SoundManager", "SoundPool init failed: ${e.message}")
        }

        // ToneGenerator can throw RuntimeException if the audio service is unavailable
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: RuntimeException) {
            Log.w("SoundManager", "ToneGenerator init failed (audio service unavailable): ${e.message}")
        }
        // TTS init is async; result is reported in onInit()
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.w("SoundManager", "TextToSpeech init failed: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts?.language = Locale.US
                tts?.setPitch(0.9f)
                tts?.setSpeechRate(1.0f)
                ttsReady = true
            } catch (e: Exception) {
                Log.w("SoundManager", "TTS language setup failed: ${e.message}")
            }
        } else {
            Log.w("SoundManager", "TTS init failed with status: $status")
        }
    }

    fun announceScore(scoreA: String, scoreB: String, status: String) {
        if (!config.soundEnabled || !config.enableVoiceRef || !ttsReady) return

        var scoreAText = scoreA
        var scoreBText = scoreB

        if (config.useLoveForZero) {
            if (scoreAText == "0") scoreAText = "Love"
            if (scoreBText == "0") scoreBText = "Love"
            if (scoreAText == "Love" && scoreBText == "Love") {
                speak("Love all")
                return
            }
        }

        val text = when {
            status.contains("Advantage", true) -> status
            status.contains("Deuce", true) -> "Deuce"
            status.contains("Tie-break", true) -> "Tie-break: $scoreAText, $scoreBText"
            else -> "$scoreAText, $scoreBText"
        }
        speak(text)
    }

    fun playPoint() {
        if (!config.soundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (e: Exception) {
            Log.w("SoundManager", "playPoint tone failed: ${e.message}")
        }
    }

    fun playUndo() {
        if (!config.soundEnabled) return
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
        } catch (e: Exception) {
            Log.w("SoundManager", "playUndo tone failed: ${e.message}")
        }
    }

    fun announceGameWin(winnerName: String, gamesA: Int, gamesB: Int) {
        if (!config.soundEnabled || !config.enableVoiceRef || !ttsReady) return
        speak("Game $winnerName. Score is $gamesA games to $gamesB.")
    }

    fun announceSetWin(winnerName: String) {
        if (!config.soundEnabled || !config.enableVoiceRef || !ttsReady) return
        speak("Set won by $winnerName.")
    }

    fun playApplause() {
        if (!config.soundEnabled || !config.enableApplause) return
        if (applauseSoundId != -1) {
            soundPool?.play(applauseSoundId, 1f, 1f, 1, 0, 1f)
        } else {
            // Fallback: Triple beep if no applause file found
            playPoint()
            handler.postDelayed({ playPoint() }, 200)
            handler.postDelayed({ playPoint() }, 400)
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "padel_ref")
        } catch (e: Exception) {
            Log.w("SoundManager", "TTS speak failed: ${e.message}")
        }
    }

    fun release() {
        try { soundPool?.release() } catch (e: Exception) {}
        try { toneGenerator?.release() } catch (e: Exception) { /* ignore */ }
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
        soundPool = null
        toneGenerator = null
        tts = null
        ttsReady = false
    }
}
