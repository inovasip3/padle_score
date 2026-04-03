package com.padelboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Custom Canvas-based LED-style scoreboard view.
 * Renders large, high-contrast scores visible from 20+ meters.
 * Black background with green (Team A) and amber (Team B) colors.
 */
class ScoreboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Configuration (mutable from ConfigManager) ---
    var teamAName: String = "TEAM A"
    var teamBName: String = "TEAM B"
    var colorA: Int = 0xFF00FF66.toInt()
    var colorB: Int = 0xFFFFA500.toInt()
    var fontScale: Float = 1.0f
    var fontTypeface: String = "monospace"
    var enableWinEffect: Boolean = true

    // --- Score data ---
    var scoreA: String = "0"
    var scoreB: String = "0"
    var setsA: Int = 0
    var setsB: Int = 0
    var statusText: String = ""

    // --- Animation ---
    private var glowAlphaA: Float = 0f
    private var glowAlphaB: Float = 0f
    private var glowAnimatorA: ValueAnimator? = null
    private var glowAnimatorB: ValueAnimator? = null
    
    // --- Screen Shake & Flash ---
    private var shakeX: Float = 0f
    private var shakeY: Float = 0f
    private var flashAlpha: Int = 0
    private var celebrationText: String? = null

    // --- Paints ---
    private val paintBackground = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val paintScoreA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorA
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintScoreB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorB
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintTeamNameA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorA
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintTeamNameB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorB
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintSetScore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        strokeWidth = 3f
    }

    private val paintStatus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    private val paintGlowA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorA
        style = Paint.Style.FILL
    }

    private val paintGlowB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorB
        style = Paint.Style.FILL
    }

    private val paintSetLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.NORMAL)
    }

    private val paintColon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCCCCC.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("monospace", Typeface.BOLD)
    }

    /**
     * Update score data and trigger redraw with glow animation
     */
    fun updateScore(
        newScoreA: String, newScoreB: String,
        newSetsA: Int, newSetsB: Int,
        newStatus: String,
        changedTeam: Char? = null
    ) {
        val aChanged = newScoreA != scoreA || newSetsA != setsA
        val bChanged = newScoreB != scoreB || newSetsB != setsB

        scoreA = newScoreA
        scoreB = newScoreB
        setsA = newSetsA
        setsB = newSetsB
        statusText = newStatus

        if (aChanged || changedTeam == 'A') triggerGlow('A')
        if (bChanged || changedTeam == 'B') triggerGlow('B')

        invalidate()
    }

    private fun triggerGlow(team: Char) {
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val value = anim.animatedValue as Float
                if (team == 'A') glowAlphaA = value else glowAlphaB = value
                postInvalidateOnAnimation()
            }
        }

        if (team == 'A') {
            glowAnimatorA?.cancel()
            glowAnimatorA = animator
        } else {
            glowAnimatorB?.cancel()
            glowAnimatorB = animator
        }
        animator.start()
    }
    
    /**
     * Trigger a screen-shake effect (e.g. on point scored)
     */
    fun shake() {
        ValueAnimator.ofFloat(20f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                shakeX = (Math.random() * v - v / 2).toFloat()
                shakeY = (Math.random() * v - v / 2).toFloat()
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    /**
     * Celebrate a set win with a flash and text overlay
     */
    fun celebrateSetWin(team: String) {
        if (!enableWinEffect) return
        celebrationText = "SET WINNER: $team"
        ValueAnimator.ofInt(0, 200, 0).apply {
            duration = 2000
            addUpdateListener { anim ->
                flashAlpha = anim.animatedValue as Int
                if (flashAlpha == 0 && anim.animatedFraction > 0.9f) {
                    celebrationText = null
                }
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, paintBackground)

        // Apply shake translation
        canvas.save()
        canvas.translate(shakeX, shakeY)

        // Update paint colors and typeface in case they changed from config
        val tf = android.graphics.Typeface.create(fontTypeface, android.graphics.Typeface.BOLD)
        paintScoreA.color = colorA;    paintScoreA.typeface = tf
        paintScoreB.color = colorB;    paintScoreB.typeface = tf
        paintTeamNameA.color = colorA; paintTeamNameA.typeface = tf
        paintTeamNameB.color = colorB; paintTeamNameB.typeface = tf
        paintGlowA.color = colorA
        paintGlowB.color = colorB

        val halfW = w / 2f
        val centerAX = w * 0.25f
        val centerBX = w * 0.75f

        // --- Layout zones ---
        val teamNameY = h * 0.12f
        val scoreY = h * 0.60f
        val setLabelY = h * 0.78f
        val setScoreY = h * 0.90f
        val statusY = h * 0.97f

        // --- Font sizes scaled to screen and user config ---
        val teamNameSize = h * 0.07f * fontScale
        val scoreSize = h * 0.38f * fontScale
        val setScoreSize = h * 0.09f * fontScale
        val setLabelSize = h * 0.04f * fontScale
        val statusSize = h * 0.05f * fontScale
        val colonSize = h * 0.30f * fontScale

        // --- Draw glow effects ---
        if (glowAlphaA > 0) {
            paintGlowA.alpha = (glowAlphaA * 40).toInt()
            canvas.drawRect(0f, 0f, halfW, h, paintGlowA)
        }
        if (glowAlphaB > 0) {
            paintGlowB.alpha = (glowAlphaB * 40).toInt()
            canvas.drawRect(halfW, 0f, w, h, paintGlowB)
        }

        // --- Divider line ---
        canvas.drawLine(halfW, h * 0.05f, halfW, h * 0.95f, paintDivider)

        // --- Team Names ---
        paintTeamNameA.textSize = teamNameSize
        paintTeamNameB.textSize = teamNameSize
        canvas.drawText(teamAName, centerAX, teamNameY, paintTeamNameA)
        canvas.drawText(teamBName, centerBX, teamNameY, paintTeamNameB)

        // --- Game Scores (large LED digits) ---
        paintScoreA.textSize = scoreSize
        paintScoreB.textSize = scoreSize

        // Apply shadow for LED glow effect
        paintScoreA.setShadowLayer(scoreSize * 0.15f, 0f, 0f, colorA)
        paintScoreB.setShadowLayer(scoreSize * 0.15f, 0f, 0f, colorB)

        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for shadow layer

        canvas.drawText(scoreA, centerAX, scoreY, paintScoreA)
        canvas.drawText(scoreB, centerBX, scoreY, paintScoreB)

        // --- Colon separator ---
        paintColon.textSize = colonSize
        canvas.drawText(":", halfW, scoreY - scoreSize * 0.15f, paintColon)

        // --- Set Label ---
        paintSetLabel.textSize = setLabelSize
        canvas.drawText("SETS", centerAX, setLabelY, paintSetLabel)
        canvas.drawText("SETS", centerBX, setLabelY, paintSetLabel)

        // --- Set Scores ---
        paintSetScore.textSize = setScoreSize
        paintSetScore.color = colorA
        paintSetScore.setShadowLayer(setScoreSize * 0.1f, 0f, 0f, colorA)
        canvas.drawText(setsA.toString(), centerAX, setScoreY, paintSetScore)

        paintSetScore.color = colorB
        paintSetScore.setShadowLayer(setScoreSize * 0.1f, 0f, 0f, colorB)
        canvas.drawText(setsB.toString(), centerBX, setScoreY, paintSetScore)

        // --- Status text (Deuce/Advantage) ---
        if (statusText.isNotEmpty()) {
            paintStatus.textSize = statusSize
            paintStatus.setShadowLayer(statusSize * 0.2f, 0f, 0f, Color.WHITE)
            canvas.drawText(statusText, halfW, statusY, paintStatus)
        }
        
        canvas.restore() // End shake translation

        // --- Flash Overlay ---
        if (flashAlpha > 0) {
            val flashPaint = Paint().apply {
                color = Color.WHITE
                alpha = flashAlpha
            }
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }

        // --- Celebration Text Overlay ---
        celebrationText?.let { text ->
            val celebPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = h * 0.12f * fontScale
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setShadowLayer(20f, 0f, 0f, Color.WHITE)
            }
            // Draw background for text
            val textRect = Rect()
            celebPaint.getTextBounds(text, 0, text.length, textRect)
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(
                halfW - textRect.width() / 2f - 40,
                h / 2f - textRect.height() / 2f - 40,
                halfW + textRect.width() / 2f + 40,
                h / 2f + textRect.height() / 2f + 40,
                bgPaint
            )
            canvas.drawText(text, halfW, h / 2f + textRect.height() / 2f, celebPaint)
        }
    }

    // --- Touch callbcks ---
    var onNameTap: ((Char) -> Unit)? = null
    var onScoreTap: ((Char) -> Unit)? = null
    var onScoreLongPress: ((Char) -> Unit)? = null
    var onResetLongPress: (() -> Unit)? = null

    // Touch handling state
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var lastDownTime = 0L
    private val LONG_PRESS_TIMEOUT = 1000L
    private var lastTapTimeMillis = 0L
    private val TAP_DEBOUNCE_MS = 300L
    
    // Multi-touch reset state
    private var pointersDown = 0
    private var pointer1X = 0f
    private var pointer2X = 0f
    private var resetRunnableRegistered = false

    private val longPressRunnable = Runnable {
        // Triggered if single finger held
        if (pointersDown == 1) {
            val h = height.toFloat()
            val w = width.toFloat()
            val scoreYBase = h * 0.60f
            val scoreSize = h * 0.38f
            val scoreTop = scoreYBase - scoreSize
            val scoreBottom = scoreYBase + (scoreSize * 0.2f)
            
            if (lastDownY in scoreTop..scoreBottom) {
                if (lastDownX < w / 2f) {
                    onScoreLongPress?.invoke('A')
                } else {
                    onScoreLongPress?.invoke('B')
                }
            }
        }
    }

    private val resetLongPressRunnable = Runnable {
        if (pointersDown >= 2) {
            // Check if one pointer is left side and one is right side
            val w = width.toFloat()
            val halfW = w / 2f
            if ((pointer1X < halfW && pointer2X > halfW) || (pointer1X > halfW && pointer2X < halfW)) {
                onResetLongPress?.invoke()
            }
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        pointersDown = event.pointerCount
        val action = event.actionMasked

        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                lastDownX = event.x
                lastDownY = event.y
                lastDownTime = System.currentTimeMillis()
                pointer1X = event.x
                postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
            }
            android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPressRunnable) // Cancel single touch long press
                if (pointersDown == 2) {
                    pointer2X = event.getX(1)
                    postDelayed(resetLongPressRunnable, LONG_PRESS_TIMEOUT)
                    resetRunnableRegistered = true
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (resetRunnableRegistered) {
                    removeCallbacks(resetLongPressRunnable)
                    resetRunnableRegistered = false
                }
                
                // If it was a quick tap, process it
                if (pointersDown == 1 && System.currentTimeMillis() - lastDownTime < LONG_PRESS_TIMEOUT) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTimeMillis >= TAP_DEBOUNCE_MS) {
                        lastTapTimeMillis = now
                        val x = event.x
                        val y = event.y
                        val w = width.toFloat()
                        val h = height.toFloat()
                        val halfW = w / 2f

                        // Hit zones
                        val teamNameYBase = h * 0.12f
                        val teamNameSize = h * 0.07f
                        val nameTop = teamNameYBase - teamNameSize
                        val nameBottom = teamNameYBase + (teamNameSize * 0.2f)

                        val scoreYBase = h * 0.60f
                        val scoreSize = h * 0.38f
                        val scoreTop = scoreYBase - scoreSize
                        val scoreBottom = scoreYBase + (scoreSize * 0.2f)

                        if (y in nameTop..nameBottom) {
                            onNameTap?.invoke(if (x < halfW) 'A' else 'B')
                        } else if (y in scoreTop..scoreBottom) {
                            onScoreTap?.invoke(if (x < halfW) 'A' else 'B')
                        }
                    }
                }
            }
            android.view.MotionEvent.ACTION_POINTER_UP -> {
                if (resetRunnableRegistered) {
                    removeCallbacks(resetLongPressRunnable)
                    resetRunnableRegistered = false
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (resetRunnableRegistered) {
                    removeCallbacks(resetLongPressRunnable)
                    resetRunnableRegistered = false
                }
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimatorA?.cancel()
        glowAnimatorB?.cancel()
        removeCallbacks(longPressRunnable)
        removeCallbacks(resetLongPressRunnable)
    }
}
