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
    var enablePhotos: Boolean = false
    var photoSize: Int = 25
    var photoYPos: Int = 35
    var photoXPosA: Int = 0 
    var photoXPosB: Int = 0

    // --- Photos ---
    private var photoA: Bitmap? = null
    private var photoB: Bitmap? = null
    private var photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private var photoBorderPaintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private var photoBorderPaintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }

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
    private var celebratingTeam: String? = null

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
     * Decode photos from internal storage
     */
    fun reloadPhotos() {
        photoA?.recycle()
        photoB?.recycle()
        photoA = null
        photoB = null

        if (!enablePhotos) {
            postInvalidate()
            return
        }

        try {
            val fileA = java.io.File(context.filesDir, "team_a_photo.jpg")
            if (fileA.exists()) photoA = BitmapFactory.decodeFile(fileA.absolutePath)
        } catch (e: Exception) {}

        try {
            val fileB = java.io.File(context.filesDir, "team_b_photo.jpg")
            if (fileB.exists()) photoB = BitmapFactory.decodeFile(fileB.absolutePath)
        } catch (e: Exception) {}
        
        postInvalidate()
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
     * Celebrate a set win with a flash and professional banner overlay
     */
    fun celebrateSetWin(team: String) {
        if (!enableWinEffect) return
        celebratingTeam = team
        ValueAnimator.ofInt(0, 180, 0).apply {
            duration = 3500 // 3.5 seconds duration for readability
            addUpdateListener { anim ->
                flashAlpha = anim.animatedValue as Int
                if (flashAlpha == 0 && anim.animatedFraction > 0.9f) {
                    celebratingTeam = null
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
        
        // --- Photos ---
        if (enablePhotos && flashAlpha == 0) { // Hide normal photos during flash overlay
            val drawPhoto = { bmp: Bitmap?, centerX: Float, teamColor: Int, xOffset: Int ->
                if (bmp != null) {
                    val sizePx = h * (photoSize / 100f)
                    val topY = h * (photoYPos / 100f) - (sizePx / 2f)
                    val leftX = centerX + (w * (xOffset / 100f)) - (sizePx / 2f)
                    val srcRect = Rect(0, 0, bmp.width, bmp.height)
                    val dstRect = RectF(leftX, topY, leftX + sizePx, topY + sizePx)
                    
                    canvas.drawBitmap(bmp, srcRect, dstRect, photoPaint)
                    
                    val borderP = if (teamColor == colorA) photoBorderPaintA else photoBorderPaintB
                    borderP.color = teamColor
                    canvas.drawRect(dstRect, borderP)
                }
            }
            drawPhoto(photoA, centerAX, colorA, photoXPosA)
            drawPhoto(photoB, centerBX, colorB, photoXPosB)
        }

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

        // --- Game Label ---
        paintSetLabel.textSize = setLabelSize
        canvas.drawText("GAMES", centerAX, setLabelY, paintSetLabel)
        canvas.drawText("GAMES", centerBX, setLabelY, paintSetLabel)

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

        // --- Professional Celebration Banner Overlay ---
        celebratingTeam?.let { winningTeamName ->
            val isTeamA = winningTeamName == teamAName
            val teamColor = if (isTeamA) colorA else colorB
            
            // Draw a full-width elegant dark translucent banner
            val bannerHeight = h * 0.28f
            val bannerTop = (h - bannerHeight) / 2f
            val bannerBottom = bannerTop + bannerHeight
            
            val bgPaint = Paint().apply {
                color = Color.argb(240, 15, 15, 20) // Very dark, sleek background
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, bannerTop, w, bannerBottom, bgPaint)
            
            // Draw glowing accent borders top/bottom in the winning team's color
            val borderPaint = Paint().apply {
                color = teamColor
                style = Paint.Style.STROKE
                strokeWidth = h * 0.01f
                setShadowLayer(20f, 0f, 0f, teamColor)
            }
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            canvas.drawLine(0f, bannerTop, w, bannerTop, borderPaint)
            canvas.drawLine(0f, bannerBottom, w, bannerBottom, borderPaint)
            
            // "SET WINNER" Subheading
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.LTGRAY // elegant silver
                textSize = h * 0.05f * fontScale
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.2f
            }
            canvas.drawText("🏆 SET WINNER", halfW, bannerTop + (h * 0.09f), titlePaint)
            
            // Winning Team Name Display
            val teamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = h * 0.12f * fontScale
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(fontTypeface, Typeface.BOLD)
                setShadowLayer(30f, 0f, 0f, teamColor) // strong colored glow
            }
            canvas.drawText(winningTeamName, halfW, bannerBottom - (h * 0.06f), teamPaint)
            
            // Winning Team Photo Animation Overlay
            if (enablePhotos) {
                val winnerBmp = if (isTeamA) photoA else photoB
                if (winnerBmp != null) {
                    // Start small, grow large during the animation
                    val animPerc = 1.0f - (flashAlpha / 180f) // goes from 0f to 1f over time
                    val scale = 1.0f + (animPerc * 1.5f) // from 1.0x to 2.5x size
                    
                    val sizePx = h * (photoSize / 100f) * scale
                    val topY = bannerTop - sizePx - 40f
                    val leftX = halfW - (sizePx / 2f)
                    
                    if (topY > 20f) {
                        val srcRect = Rect(0, 0, winnerBmp.width, winnerBmp.height)
                        val dstRect = RectF(leftX, topY, leftX + sizePx, topY + sizePx)
                        
                        // Add glow shadow border
                        val photoGlowBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                            style = Paint.Style.STROKE
                            strokeWidth = 12f
                            color = teamColor
                            setShadowLayer(40f, 0f, 0f, teamColor)
                        }
                        canvas.drawBitmap(winnerBmp, srcRect, dstRect, photoPaint)
                        canvas.drawRect(dstRect, photoGlowBorder)
                    }
                }
            }
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
