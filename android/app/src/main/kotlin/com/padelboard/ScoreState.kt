package com.padelboard

/**
 * Padel scoring state machine.
 * Implements official padel scoring: 0, 15, 30, 40, Deuce, Advantage.
 * Thread-safe via synchronized blocks.
 */
class ScoreState {

    // Points index: 0=0, 1=15, 2=30, 3=40
    private val pointLabels = arrayOf("0", "15", "30", "40")

    // Game points (index into pointLabels, or special states)
    private var pointsA: Int = 0
    private var pointsB: Int = 0

    // Set scores
    var setsA: Int = 0
        private set
    var setsB: Int = 0
        private set

    // Special states
    var isDeuce: Boolean = false
        private set
    var advantageTeam: Char? = null // 'A' or 'B' or null
        private set

    // Track whether a game was just won for UI animation
    var lastGameWinner: Char? = null
        private set

    // Listener for score changes
    var onScoreChanged: (() -> Unit)? = null

    /**
     * Get display text for team A's game score
     */
    @Synchronized
    fun getScoreDisplayA(): String {
        if (isDeuce) return if (advantageTeam == 'A') "AD" else if (advantageTeam == 'B') "" else "40"
        return if (pointsA in 0..3) pointLabels[pointsA] else "0"
    }

    /**
     * Get display text for team B's game score
     */
    @Synchronized
    fun getScoreDisplayB(): String {
        if (isDeuce) return if (advantageTeam == 'B') "AD" else if (advantageTeam == 'A') "" else "40"
        return if (pointsB in 0..3) pointLabels[pointsB] else "0"
    }

    /**
     * Get the deuce/advantage status text (shown centrally)
     */
    @Synchronized
    fun getStatusText(): String {
        return when {
            isDeuce && advantageTeam == null -> "DEUCE"
            isDeuce && advantageTeam == 'A' -> "ADVANTAGE"
            isDeuce && advantageTeam == 'B' -> "ADVANTAGE"
            else -> ""
        }
    }

    /**
     * Add a point to the specified team
     */
    @Synchronized
    fun addPoint(team: Char) {
        lastGameWinner = null

        if (isDeuce) {
            handleDeucePoint(team)
        } else {
            handleNormalPoint(team)
        }

        onScoreChanged?.invoke()
    }

    private fun handleNormalPoint(team: Char) {
        if (team == 'A') {
            if (pointsA < 3) {
                pointsA++
                // Check for deuce: both at 40 (index 3)
                if (pointsA == 3 && pointsB == 3) {
                    isDeuce = true
                }
            } else {
                // pointsA is already at 40 and pointsB < 40
                winGame('A')
            }
        } else {
            if (pointsB < 3) {
                pointsB++
                if (pointsA == 3 && pointsB == 3) {
                    isDeuce = true
                }
            } else {
                winGame('B')
            }
        }
    }

    private fun handleDeucePoint(team: Char) {
        when {
            advantageTeam == null -> {
                // Deuce, someone gets advantage
                advantageTeam = team
            }
            advantageTeam == team -> {
                // Team with advantage wins the game
                winGame(team)
            }
            else -> {
                // Other team scored, back to deuce
                advantageTeam = null
            }
        }
    }

    private fun winGame(team: Char) {
        lastGameWinner = team
        if (team == 'A') setsA++ else setsB++
        resetGame()
    }

    private fun resetGame() {
        pointsA = 0
        pointsB = 0
        isDeuce = false
        advantageTeam = null
    }

    /**
     * Remove a point from the specified team (undo).
     * Prevents negative values.
     */
    @Synchronized
    fun removePoint(team: Char) {
        lastGameWinner = null

        if (isDeuce) {
            // In deuce state, undo means:
            if (advantageTeam != null) {
                // Remove advantage -> back to deuce
                advantageTeam = null
            } else {
                // At deuce, go back: the undone team goes to 30 (index 2), other stays at 40
                isDeuce = false
                if (team == 'A') {
                    pointsA = 2 // 30
                    pointsB = 3 // 40
                } else {
                    pointsA = 3 // 40
                    pointsB = 2 // 30
                }
            }
        } else {
            if (team == 'A') {
                if (pointsA > 0) pointsA--
            } else {
                if (pointsB > 0) pointsB--
            }
        }

        onScoreChanged?.invoke()
    }

    /**
     * Reset all scores to zero
     */
    @Synchronized
    fun reset() {
        pointsA = 0
        pointsB = 0
        setsA = 0
        setsB = 0
        isDeuce = false
        advantageTeam = null
        lastGameWinner = null
        onScoreChanged?.invoke()
    }

    /**
     * Get full state snapshot for JSON API response
     */
    @Synchronized
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "scoreA" to getScoreDisplayA(),
            "scoreB" to getScoreDisplayB(),
            "setsA" to setsA,
            "setsB" to setsB,
            "isDeuce" to isDeuce,
            "advantage" to advantageTeam?.toString(),
            "status" to getStatusText()
        )
    }
}
