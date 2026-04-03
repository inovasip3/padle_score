package com.padelboard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Manages audio feedback for the app.
 * Uses ToneGenerator for system reach and low-latency beeps.
 * Respects sound settings from ConfigManager.
 */
class SoundManager(private val context: Context, private val config: ConfigManager) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private val handler = Handler(Looper.getMainLooper())

    fun playPoint() {
        if (!config.soundEnabled) return
        // Short, mid-high pitch beep
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
    }

    fun playUndo() {
        if (!config.soundEnabled) return
        // Lower pitch beep
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
    }

    fun playWinGame() {
        if (!config.soundEnabled) return
        // Success-like pattern
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
    }

    fun playWinSet() {
        if (!config.soundEnabled) return
        // More celebratory pattern
        Thread {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
            Thread.sleep(300)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
            Thread.sleep(300)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 400)
        }.start()
    }

    fun playMatchPoint() {
        if (!config.soundEnabled) return
        // Warning-like pattern to build tension
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 300)
    }

    fun release() {
        toneGenerator.release()
    }
}
