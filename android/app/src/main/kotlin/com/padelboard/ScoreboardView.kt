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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, paintBackground)

        // Update paint colors (in case they changed from config)
        paintScoreA.color = colorA
        paintScoreB.color = colorB
        paintTeamNameA.color = colorA
        paintTeamNameB.color = colorB
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

        // --- Font sizes scaled to screen ---
        val teamNameSize = h * 0.07f
        val scoreSize = h * 0.38f
        val setScoreSize = h * 0.09f
        val setLabelSize = h * 0.04f
        val statusSize = h * 0.05f
        val colonSize = h * 0.30f

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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimatorA?.cancel()
        glowAnimatorB?.cancel()
    }
}
