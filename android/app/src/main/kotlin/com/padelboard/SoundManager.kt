package com.padelboard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.*

/**
 * Manages audio feedback for the app.
 * Uses ToneGenerator for beeps and TextToSpeech for umpire announcements.
 */
class SoundManager(private val context: Context, private val config: ConfigManager) : TextToSpeech.OnInitListener {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var ttsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.9f) // Slightly deeper, professional voice
            tts?.setSpeechRate(1.0f)
            ttsReady = true
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
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    fun playUndo() {
        if (!config.soundEnabled) return
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    }

    fun announceGameWin(winnerName: String, gamesA: Int, gamesB: Int) {
        if (!config.soundEnabled || !config.enableVoiceRef || !ttsReady) return
        speak("Game $winnerName. Score is $gamesA games to $gamesB.")
    }

    fun announceSetWin(winnerName: String) {
        if (!config.soundEnabled || !config.enableVoiceRef || !ttsReady) return
        speak("Set won by $winnerName.")
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "padel_ref")
    }

    fun release() {
        toneGenerator.release()
        tts?.stop()
        tts?.shutdown()
    }
}
